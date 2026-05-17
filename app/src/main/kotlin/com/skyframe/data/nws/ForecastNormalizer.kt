package com.skyframe.data.nws

import com.skyframe.domain.DailyPeriod
import com.skyframe.domain.HourlyPeriod
import com.skyframe.domain.Wind
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.max

/**
 * Normalizes the NWS /forecast and /forecast/hourly responses to typed
 * domain HourlyPeriod and DailyPeriod lists.
 *
 * Port of forecast normalization logic from
 * _reference/server/nws/normalizer.ts, including:
 *   - Past-hour filtering (drop periods that ended before 'now')
 *   - Day+night pairing via order-walk (NOT date-bucketing, which mis-pairs
 *     "Overnight" with next-day "Day")
 *   - Probability-aware icon downgrade (hourly) and upgrade (daily)
 *   - Orphan period handling (lone "Tonight" at window start, day-only at
 *     window end, "Overnight" skipped when it shares its date with next day)
 */
object ForecastNormalizer {

    private const val HOURLY_LIMIT = 13   // 12+ hours
    private const val DAILY_LIMIT = 7

    fun normalizeHourly(
        dto: ForecastDto,
        now: Instant,
        tz: TimeZone = TimeZone.currentSystemDefault(),
    ): List<HourlyPeriod> {
        return dto.properties.periods
            .filter { Instant.parse(it.endTime) > now }
            .take(HOURLY_LIMIT)
            .map { p ->
                val precip = p.probabilityOfPrecipitation?.value ?: 0
                HourlyPeriod(
                    startTime = Instant.parse(p.startTime),
                    hourLabel = formatHourLabel(Instant.parse(p.startTime), tz),
                    tempF = p.temperature.toDouble(),
                    iconCode = IconMapper.forHourly(p.icon, p.isDaytime, precip),
                    precipProbPct = precip,
                    wind = parseWindString(p.windSpeed, p.windDirection),
                    shortDescription = p.shortForecast,
                )
            }
    }

    /**
     * Order-walk pairing matching _reference/server/nws/normalizer.ts
     * `collapseDailyPeriods`. NWS returns periods in chronological order; we
     * pair consecutive (day, night) periods into one DailyPeriod each. The
     * three special cases:
     *   1. Standalone "Tonight"/"Overnight" at the window start (no preceding
     *      day period) → emit with night detail only.
     *   2. Standalone day at the window end (no following night) → emit with
     *      day detail only.
     *   3. "Overnight" whose local date matches the *next* day's "Day" period
     *      → skip it. NWS sends an early-morning Overnight period whose start
     *      time is technically the previous date's late evening but conceptually
     *      belongs to today; date-bucketing would create a duplicate row.
     */
    fun normalizeDaily(
        dto: ForecastDto,
        tz: TimeZone = TimeZone.currentSystemDefault(),
    ): List<DailyPeriod> {
        val periods = dto.properties.periods
        val daily = mutableListOf<DailyPeriod>()
        var i = 0

        while (i < periods.size && daily.size < DAILY_LIMIT) {
            val a = periods[i]
            val b = periods.getOrNull(i + 1)

            if (a.isDaytime && b != null && !b.isDaytime) {
                // Day + night pair (the common case).
                val pairProb = max(
                    a.probabilityOfPrecipitation?.value ?: 0,
                    b.probabilityOfPrecipitation?.value ?: 0,
                )
                val startInstant = Instant.parse(a.startTime)
                val startDate = startInstant.toLocalDateTime(tz).date
                daily += DailyPeriod(
                    dateISO = startDate,
                    dayOfWeek = formatDayOfWeek(startDate),
                    dateLabel = formatDateLabel(startDate),
                    highF = a.temperature,
                    lowF = b.temperature,
                    iconCode = IconMapper.forDaily(a.icon, a.shortForecast, pairProb),
                    precipProbPct = pairProb,
                    shortDescription = a.shortForecast,
                    dayDetailedForecast = a.detailedForecast,
                    nightDetailedForecast = b.detailedForecast,
                    dayPeriodName = a.name,
                    nightPeriodName = b.name,
                )
                i += 2
            } else if (!a.isDaytime) {
                // Night period before any day period was paired with it.
                // Special case 3: skip "Overnight" that shares its local date
                // with the next day's "Day" period.
                val aDate = Instant.parse(a.startTime).toLocalDateTime(tz).date
                if (b != null && b.isDaytime &&
                    Instant.parse(b.startTime).toLocalDateTime(tz).date == aDate
                ) {
                    i += 1
                    continue
                }
                // Otherwise (special case 1): standalone Tonight at window start.
                val nightProb = a.probabilityOfPrecipitation?.value ?: 0
                daily += DailyPeriod(
                    dateISO = aDate,
                    dayOfWeek = formatDayOfWeek(aDate),
                    dateLabel = formatDateLabel(aDate),
                    highF = a.temperature,
                    lowF = a.temperature,
                    iconCode = IconMapper.forDaily(a.icon, a.shortForecast, nightProb),
                    precipProbPct = nightProb,
                    shortDescription = a.shortForecast,
                    dayDetailedForecast = null,
                    nightDetailedForecast = a.detailedForecast,
                    dayPeriodName = null,
                    nightPeriodName = a.name,
                )
                i += 1
            } else {
                // Special case 2: orphan day period at window end.
                val dayProb = a.probabilityOfPrecipitation?.value ?: 0
                val aDate = Instant.parse(a.startTime).toLocalDateTime(tz).date
                daily += DailyPeriod(
                    dateISO = aDate,
                    dayOfWeek = formatDayOfWeek(aDate),
                    dateLabel = formatDateLabel(aDate),
                    highF = a.temperature,
                    lowF = a.temperature,
                    iconCode = IconMapper.forDaily(a.icon, a.shortForecast, dayProb),
                    precipProbPct = dayProb,
                    shortDescription = a.shortForecast,
                    dayDetailedForecast = a.detailedForecast,
                    nightDetailedForecast = null,
                    dayPeriodName = a.name,
                    nightPeriodName = null,
                )
                i += 1
            }
        }

        return daily
    }

    private fun formatHourLabel(t: Instant, tz: TimeZone): String {
        val ldt = t.toLocalDateTime(tz)
        val hour12 = ((ldt.hour + 11) % 12) + 1
        val ampm = if (ldt.hour < 12) "AM" else "PM"
        return "${hour12}$ampm"
    }

    /** Reference: 3-letter uppercase day name, e.g. "MON". */
    private fun formatDayOfWeek(date: LocalDate): String = when (date.dayOfWeek) {
        DayOfWeek.MONDAY -> "MON"
        DayOfWeek.TUESDAY -> "TUE"
        DayOfWeek.WEDNESDAY -> "WED"
        DayOfWeek.THURSDAY -> "THU"
        DayOfWeek.FRIDAY -> "FRI"
        DayOfWeek.SATURDAY -> "SAT"
        DayOfWeek.SUNDAY -> "SUN"
    }

    /** Reference: "MAY 17" — short month name + 2-digit day, uppercased. */
    private fun formatDateLabel(date: LocalDate): String {
        val month = when (date.month) {
            Month.JANUARY -> "JAN"; Month.FEBRUARY -> "FEB"; Month.MARCH -> "MAR"
            Month.APRIL -> "APR"; Month.MAY -> "MAY"; Month.JUNE -> "JUN"
            Month.JULY -> "JUL"; Month.AUGUST -> "AUG"; Month.SEPTEMBER -> "SEP"
            Month.OCTOBER -> "OCT"; Month.NOVEMBER -> "NOV"; Month.DECEMBER -> "DEC"
            else -> date.month.name.take(3).uppercase()
        }
        return "$month ${date.dayOfMonth.toString().padStart(2, '0')}"
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
