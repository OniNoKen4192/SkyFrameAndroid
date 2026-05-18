package com.skyframe.data.nws

import com.skyframe.data.cache.WeatherCache
import com.skyframe.data.settings.SettingsRepository
import com.skyframe.domain.StationOverride
import com.skyframe.domain.WeatherError
import com.skyframe.domain.WeatherMeta
import com.skyframe.domain.WeatherResponse
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.time.Duration.Companion.seconds

/**
 * Top-level orchestrator. Calls /points + /forecast + /forecast/hourly +
 * /alerts in parallel, then /stations/.../observations/latest (primary or
 * fallback). Caches the assembled WeatherResponse for 90s.
 *
 * Port of _reference/server/nws/normalizer.ts orchestration.
 */
@Singleton
class WeatherNormalizer @Inject constructor(
    private val nws: NwsClient,
    private val settings: SettingsRepository,
    private val cache: WeatherCache<WeatherResponse>,
) {
    private val CACHE_KEY = "weather-response"
    private val CACHE_TTL = 90.seconds

    suspend fun load(forceRefresh: Boolean = false): WeatherResponse {
        if (!forceRefresh) {
            cache.get(CACHE_KEY)?.let { return it.copy(meta = it.meta.copy(cacheHit = true)) }
        }
        val cfg = settings.snapshot()
        require(cfg.forecastOffice.isNotBlank()) { "Setup incomplete — run SettingsScreen first" }

        val now = Clock.System.now()
        val response = coroutineScope {
            val pointsAsync = async { nws.points(cfg.lat, cfg.lon) }
            val forecastAsync = async { nws.forecast(cfg.forecastOffice, cfg.gridX, cfg.gridY) }
            val hourlyAsync = async { nws.hourlyForecast(cfg.forecastOffice, cfg.gridX, cfg.gridY) }
            // Alerts is wrapped in a safe try so a flaky /alerts/active endpoint
            // can't take down the entire dashboard. If it fails we emit zero
            // alerts and surface meta.error = PARTIAL.
            val alertsAsync = async { runCatching { nws.activeAlerts(cfg.lat, cfg.lon) }.getOrNull() }

            val points = pointsAsync.await()
            val forecast = forecastAsync.await()
            val hourly = hourlyAsync.await()
            val alertsDto = alertsAsync.await()
            val alertsFailed = alertsDto == null

            // Station fallback: try primary first; if stale/null, try secondary.
            val (observation, activeStationId, fellBack) = fetchObservationWithFallback(cfg, now)

            // History fetch is sequential after fallback - we don't know which
            // station ID won until fallback decides. Wrapped in runCatching for
            // partial-failure semantics (matches alerts pattern): on failure the
            // trends fall back to MISSING confidence and HudMetricBar hides the
            // arrows silently. ~200-400ms additional latency on cold loads;
            // cache hits skip it entirely.
            val history: List<ObservationDto> = runCatching {
                nws.recentObservations(activeStationId).features
                    .map { ObservationDto(properties = it.properties) }
            }.getOrDefault(emptyList())

            val sunrise = points.properties.astronomicalData?.sunrise?.let { Instant.parse(it) }
            val sunset = points.properties.astronomicalData?.sunset?.let { Instant.parse(it) }
            val precipOutlook = "" // computed from hourly periods; deferred to enhancement

            WeatherResponse(
                current = ObservationNormalizer.normalize(
                    latest = observation,
                    recentObservations = history,      // Plan 2: populates ConditionTrends
                    stationDistanceKm = 0.0,           // not exposed by /observations/latest
                    sunrise = sunrise,
                    sunset = sunset,
                    precipOutlook = precipOutlook,
                    isDay = sunrise != null && sunset != null && now in sunrise..sunset,
                ),
                hourly = ForecastNormalizer.normalizeHourly(hourly, now, locationTz(cfg)),
                daily = ForecastNormalizer.normalizeDaily(forecast, locationTz(cfg)),
                alerts = alertsDto?.let { AlertNormalizer.normalize(it) } ?: emptyList(),
                meta = WeatherMeta(
                    fetchedAt = now,
                    nextRefreshAt = now.plus(CACHE_TTL),
                    cacheHit = false,
                    stationId = activeStationId,
                    locationName = cfg.locationName,
                    stationOverride = cfg.stationOverride,
                    forecastGeneratedAt = Instant.parse(forecast.properties.generatedAt),
                    forecastOffice = cfg.forecastOffice,
                    gridX = cfg.gridX,
                    gridY = cfg.gridY,
                    forecastZone = cfg.forecastZone,
                    // PARTIAL takes precedence over STATION_FALLBACK in priority since
                    // alerts going dark is more important to surface to the user.
                    error = when {
                        alertsFailed -> WeatherError.PARTIAL
                        fellBack -> WeatherError.STATION_FALLBACK
                        else -> null
                    },
                ),
            )
        }
        cache.put(CACHE_KEY, response, CACHE_TTL)
        return response
    }

    /**
     * Resolve the configured IANA timezone (e.g. "America/Chicago") to a
     * TimeZone, falling back to system default if the string is malformed.
     * Critical for forecast bucketing: the device may be in a different TZ
     * than the configured location (traveling, work-from-anywhere, etc.).
     */
    private fun locationTz(cfg: SettingsRepository.Snapshot): TimeZone =
        runCatching { TimeZone.of(cfg.timezone) }.getOrDefault(TimeZone.currentSystemDefault())

    private suspend fun fetchObservationWithFallback(
        cfg: SettingsRepository.Snapshot,
        now: Instant,
    ): Triple<ObservationDto, String, Boolean> {
        if (cfg.stationOverride == StationOverride.FORCE_SECONDARY) {
            val obs = nws.latestObservation(cfg.stationFallback)
            return Triple(obs, cfg.stationFallback, true)
        }
        return try {
            val primary = nws.latestObservation(cfg.stationPrimary)
            val timestamp = Instant.parse(primary.properties.timestamp)
            val tempF = NormalizerHelpers.toFahrenheit(primary.properties.temperature)
            if (NormalizerHelpers.isObservationStale(timestamp.toEpochMilliseconds(), now.toEpochMilliseconds(), tempF)) {
                val fallback = nws.latestObservation(cfg.stationFallback)
                Triple(fallback, cfg.stationFallback, true)
            } else {
                Triple(primary, cfg.stationPrimary, false)
            }
        } catch (e: Exception) {
            val fallback = nws.latestObservation(cfg.stationFallback)
            Triple(fallback, cfg.stationFallback, true)
        }
    }
}
