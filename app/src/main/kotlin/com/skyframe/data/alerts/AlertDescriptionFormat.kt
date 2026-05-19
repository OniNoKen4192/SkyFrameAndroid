package com.skyframe.data.alerts

import com.skyframe.domain.Alert
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.Locale

/**
 * Parsed paragraph of an NWS alert description. NWS alerts use ALL CAPS
 * "HAZARD...", "SOURCE...", "IMPACT..." section prefixes that we strip
 * and tag so the UI can render them tier-colored. Other paragraphs render
 * plain in body color.
 */
sealed class AlertDescriptionParagraph {
    abstract val text: String

    data class Tagged(val prefix: Prefix, override val text: String) : AlertDescriptionParagraph()
    data class Plain(override val text: String) : AlertDescriptionParagraph()

    enum class Prefix { HAZARD, SOURCE, IMPACT }
}

/**
 * Port of _reference/client/alert-detail-format.ts. Pure functions - no
 * Android dependencies beyond kotlinx.datetime.
 */
object AlertDescriptionFormat {

    private val PREFIX_RE = Regex("""^(HAZARD|SOURCE|IMPACT)\.\.\.\s*""")

    fun parseDescription(raw: String): List<AlertDescriptionParagraph> {
        if (raw.isEmpty()) return emptyList()
        return raw.replace("\r\n", "\n")
            .split(Regex("""\n{2,}"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { chunk ->
                val match = PREFIX_RE.find(chunk)
                if (match != null) {
                    val prefix = AlertDescriptionParagraph.Prefix.valueOf(match.groupValues[1])
                    AlertDescriptionParagraph.Tagged(prefix, chunk.substring(match.range.last + 1))
                } else {
                    AlertDescriptionParagraph.Plain(chunk)
                }
            }
    }

    /**
     * Renders an Instant as "2:30 PM CDT" in the supplied timezone. Uppercased.
     * Uses Locale.US to keep AM/PM and month/weekday names in English regardless
     * of device locale — NWS responses are English-only anyway.
     */
    fun formatTime(instant: Instant, tz: TimeZone): String {
        val ldt = instant.toLocalDateTime(tz)
        val hour12 = ((ldt.hour + 11) % 12) + 1
        val ampm = if (ldt.hour < 12) "AM" else "PM"
        val minute = ldt.minute.toString().padStart(2, '0')
        // TZ abbreviation: use the Java zone's short display name in standard or daylight time.
        val zone = java.util.TimeZone.getTimeZone(tz.id)
        val isDst = zone.inDaylightTime(java.util.Date(instant.toEpochMilliseconds()))
        val tzAbbr = zone.getDisplayName(isDst, java.util.TimeZone.SHORT, Locale.US)
        return "$hour12:$minute $ampm $tzAbbr"
    }

    /**
     * True when the alert was synthesized by UpdateCheckRepository (id starts
     * with "update-") rather than coming from NWS. Used to suppress meta
     * fields that don't apply (the far-future expires + empty areaDesc).
     */
    fun isUpdateAlert(alert: Alert): Boolean = alert.id.startsWith("update-")

    fun formatAlertMeta(alert: Alert, tz: TimeZone): String {
        val issued = formatTime(alert.issuedAt, tz)
        return if (isUpdateAlert(alert)) {
            // Synthetic update alerts have far-future expires and empty area;
            // showing them would mislead users about a real "until" deadline.
            "ISSUED $issued"
        } else {
            val expires = formatTime(alert.expires, tz)
            "ISSUED $issued · EXPIRES $expires · ${alert.areaDesc.uppercase()}"
        }
    }
}
