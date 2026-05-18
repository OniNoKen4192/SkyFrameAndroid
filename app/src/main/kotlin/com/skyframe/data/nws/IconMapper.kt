package com.skyframe.data.nws

import com.skyframe.domain.IconCode

/**
 * Maps NWS icon URLs to the project's IconCode enum.
 *
 * NWS icon URLs look like:
 *   https://api.weather.gov/icons/land/day/skc?size=medium
 *   https://api.weather.gov/icons/land/night/rain,80?size=medium
 *
 * The path segment after /day/ or /night/ encodes the condition + optional
 * probability suffix (e.g. "rain,80" means rain with 80% probability — but
 * we ignore the suffix value, the caller passes probability separately).
 *
 * Ported from _reference/server/nws/icon-mapping.ts.
 */
object IconMapper {

    private val PATH_REGEX = Regex("""/icons/land/(day|night)/([^?,/]+)""")

    private val CODE_MAP_DAY = mapOf(
        "skc" to IconCode.SUN,
        "few" to IconCode.PARTLY_DAY,
        "sct" to IconCode.PARTLY_DAY,
        "bkn" to IconCode.PARTLY_DAY,
        "ovc" to IconCode.CLOUD,
        "rain" to IconCode.RAIN,
        "rain_showers" to IconCode.RAIN,
        "rain_showers_hi" to IconCode.RAIN,
        "snow" to IconCode.SNOW,
        "rain_snow" to IconCode.SNOW,
        "rain_sleet" to IconCode.SNOW,
        "snow_sleet" to IconCode.SNOW,
        "fzra" to IconCode.SNOW,
        "rain_fzra" to IconCode.SNOW,
        "snow_fzra" to IconCode.SNOW,
        "sleet" to IconCode.SNOW,
        "tsra" to IconCode.THUNDER,
        "tsra_sct" to IconCode.THUNDER,
        "tsra_hi" to IconCode.THUNDER,
        "scttsra" to IconCode.THUNDER,
        "hi_shwrs" to IconCode.RAIN,
        "fzra_sct" to IconCode.SNOW,
        "ra_fzra" to IconCode.SNOW,
        "ra_sn" to IconCode.SNOW,
        "sn" to IconCode.SNOW,
        "blizzard" to IconCode.SNOW,
        "cold" to IconCode.CLOUD,
        "fog" to IconCode.FOG,
        "haze" to IconCode.FOG,
        "smoke" to IconCode.FOG,
        "dust" to IconCode.FOG,
    )

    private val CODE_MAP_NIGHT = CODE_MAP_DAY + mapOf(
        "skc" to IconCode.MOON,
        "few" to IconCode.PARTLY_NIGHT,
        "sct" to IconCode.PARTLY_NIGHT,
        "bkn" to IconCode.PARTLY_NIGHT,
    )

    /** Pure URL to IconCode mapping with no probability-aware logic. */
    fun fromUrl(url: String?): IconCode {
        if (url == null) return IconCode.CLOUD
        val match = PATH_REGEX.find(url) ?: return IconCode.CLOUD
        val (period, code) = match.destructured
        val map = if (period == "night") CODE_MAP_NIGHT else CODE_MAP_DAY
        return map[code] ?: IconCode.CLOUD
    }

    /**
     * Hourly icon with probability-aware downgrade: when NWS picked a precip
     * icon but probability < 30%, downgrade to partly-* so the hourly chart
     * doesn't lie about an unlikely event.
     */
    fun forHourly(rawUrl: String?, isDay: Boolean, precipProbPct: Int): IconCode {
        val base = fromUrl(rawUrl)
        if (precipProbPct < 30 && base in setOf(IconCode.RAIN, IconCode.SNOW, IconCode.THUNDER)) {
            return if (isDay) IconCode.PARTLY_DAY else IconCode.PARTLY_NIGHT
        }
        return base
    }

    /**
     * Daily icon with probability-aware upgrade: when NWS picked a non-precip
     * icon but probability >= 50%, upgrade to rain/snow/thunder. Target is
     * picked by shortForecast keyword match: thunder beats snow beats rain.
     */
    fun forDaily(rawUrl: String?, shortForecast: String, precipProbPct: Int): IconCode {
        val base = fromUrl(rawUrl)
        if (precipProbPct < 50) return base
        if (base in setOf(IconCode.RAIN, IconCode.SNOW, IconCode.THUNDER)) return base

        val text = shortForecast.lowercase()
        return when {
            "thunder" in text || "t-storm" in text -> IconCode.THUNDER
            "snow" in text || "sleet" in text || "ice" in text -> IconCode.SNOW
            "rain" in text || "shower" in text || "drizzle" in text -> IconCode.RAIN
            else -> base
        }
    }
}
