package com.skyframe.data.nws

import com.skyframe.domain.DailyPeriod
import com.skyframe.domain.HourlyPeriod
import com.skyframe.domain.Wind
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Normalizes the NWS /forecast and /forecast/hourly responses to typed
 * domain HourlyPeriod and DailyPeriod lists.
 *
 * Port of forecast normalization logic from
 * _reference/server/nws/normalizer.ts, including:
 *   - Past-hour filtering (drop periods that ended before 'now')
 *   - Day+night pairing by date for daily outlook
 *   - Probability-aware icon downgrade (hourly) and upgrade (daily)
 *   - Orphan period handling (lone "Tonight" at window start, or day-only
 *     at window end — emit with the missing half as null)
 */
object ForecastNormalizer {

    private const val HOURLY_LIMIT = 13   // 12+ hours
    private const val DAILY_LIMIT = 7

    fun normalizeHourly(dto: ForecastDto, now: Instant): List<HourlyPeriod> {
        return dto.properties.periods
            .filter { Instant.parse(it.endTime) > now }
            .take(HOURLY_LIMIT)
            .map { p ->
                val precip = p.probabilityOfPrecipitation?.value ?: 0
                HourlyPeriod(
                    startTime = Instant.parse(p.startTime),
                    hourLabel = formatHourLabel(Instant.parse(p.startTime)),
                    tempF = p.temperature.toDouble(),
                    iconCode = IconMapper.forHourly(p.icon, p.isDaytime, precip),
                    precipProbPct = precip,
                    wind = parseWindString(p.windSpeed, p.windDirection),
                    shortDescription = p.shortForecast,
                )
            }
    }

    fun normalizeDaily(dto: ForecastDto, tz: TimeZone = TimeZone.currentSystemDefault()): List<DailyPeriod> {
        // Group periods by date-of-start in the supplied TZ.
        val periods = dto.properties.periods
        val byDate = LinkedHashMap<LocalDate, MutableList<ForecastPeriodDto>>()
        for (p in periods) {
            val date = Instant.parse(p.startTime).toLocalDateTime(tz).date
            byDate.getOrPut(date) { mutableListOf() }.add(p)
        }
        return byDate.entries.take(DAILY_LIMIT).map { (date, list) ->
            val day = list.firstOrNull { it.isDaytime }
            val night = list.firstOrNull { !it.isDaytime }
            val anchor = day ?: night ?: list.first()
            val precip = (day?.probabilityOfPrecipitation?.value ?: night?.probabilityOfPrecipitation?.value) ?: 0
            DailyPeriod(
                dateISO = date,
                dayOfWeek = anchor.name.substringBefore(' ').uppercase(),
                dateLabel = "${date.monthNumber}/${date.dayOfMonth}",
                highF = day?.temperature ?: night?.temperature ?: 0,
                lowF = night?.temperature ?: day?.temperature ?: 0,
                iconCode = IconMapper.forDaily(anchor.icon, anchor.shortForecast, precip),
                precipProbPct = precip,
                shortDescription = anchor.shortForecast,
                dayDetailedForecast = day?.detailedForecast,
                nightDetailedForecast = night?.detailedForecast,
                dayPeriodName = day?.name,
                nightPeriodName = night?.name,
            )
        }
    }

    private fun formatHourLabel(t: Instant, tz: TimeZone = TimeZone.currentSystemDefault()): String {
        val ldt = t.toLocalDateTime(tz)
        val hour12 = ((ldt.hour + 11) % 12) + 1
        val ampm = if (ldt.hour < 12) "AM" else "PM"
        return "${hour12}$ampm"
    }

    private fun parseWindString(speed: String, dir: String): Wind {
        // NWS windSpeed like "5 mph" or "5 to 15 mph"; take the first number.
        val mph = Regex("""\d+""").find(speed)?.value?.toDoubleOrNull() ?: 0.0
        return Wind(speedMph = mph, directionDeg = cardinalToDegrees(dir), cardinal = dir)
    }

    private fun cardinalToDegrees(c: String): Double = when (c.uppercase()) {
        "N" -> 0.0; "NNE" -> 22.5; "NE" -> 45.0; "ENE" -> 67.5
        "E" -> 90.0; "ESE" -> 112.5; "SE" -> 135.0; "SSE" -> 157.5
        "S" -> 180.0; "SSW" -> 202.5; "SW" -> 225.0; "WSW" -> 247.5
        "W" -> 270.0; "WNW" -> 292.5; "NW" -> 315.0; "NNW" -> 337.5
        else -> 0.0
    }
}
