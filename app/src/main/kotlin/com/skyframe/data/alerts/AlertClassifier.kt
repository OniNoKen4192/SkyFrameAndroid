package com.skyframe.data.alerts

import com.skyframe.domain.AlertTier

/**
 * Pure port of _reference/shared/alert-tiers.ts classifyAlert.
 *
 * NWS alerts have an event name (e.g. "Tornado Warning") and a parameters
 * map (Map<String, List<String>>). For most events, the event name alone
 * determines the tier. Two events use parameter-driven escalation:
 *   - Tornado Warning + tornadoDamageThreat=CATASTROPHIC -> tornado-emergency
 *   - Tornado Warning + tornadoDamageThreat=CONSIDERABLE -> tornado-pds
 *   - Severe Thunderstorm Warning + thunderstormDamageThreat=DESTRUCTIVE -> tstorm-destructive
 *
 * Unknown events fall through to the ADVISORY catch-all rather than being
 * silently dropped (matches web behavior post-PR #8).
 */
object AlertClassifier {

    private val DIRECT_MAP: Map<String, AlertTier> = mapOf(
        "Blizzard Warning"          to AlertTier.BLIZZARD,
        "Winter Storm Warning"      to AlertTier.WINTER_STORM,
        "Flood Warning"             to AlertTier.FLOOD,
        "Flash Flood Warning"       to AlertTier.FLOOD,
        "Heat Advisory"             to AlertTier.HEAT,
        "Excessive Heat Warning"    to AlertTier.HEAT,
        "Excessive Heat Watch"      to AlertTier.HEAT,
        "Special Weather Statement" to AlertTier.SPECIAL_WEATHER_STATEMENT,
        "Tornado Watch"             to AlertTier.WATCH,
        "Severe Thunderstorm Watch" to AlertTier.WATCH,
        "Wind Advisory"             to AlertTier.ADVISORY_HIGH,
        "Winter Weather Advisory"   to AlertTier.ADVISORY_HIGH,
        "Dense Fog Advisory"        to AlertTier.ADVISORY_HIGH,
        "Wind Chill Advisory"       to AlertTier.ADVISORY_HIGH,
        "Freeze Warning"            to AlertTier.ADVISORY_HIGH,
        "Freeze Watch"              to AlertTier.ADVISORY_HIGH,
        "Frost Advisory"            to AlertTier.ADVISORY_HIGH,
    )

    fun classify(event: String, parameters: Map<String, List<String>>): AlertTier {
        // Parameter-driven escalations first
        if (event == "Tornado Warning" || event == "Tornado Emergency") {
            val threat = parameters["tornadoDamageThreat"]?.firstOrNull()?.uppercase()
            return when {
                threat == "CATASTROPHIC" -> AlertTier.TORNADO_EMERGENCY
                threat == "CONSIDERABLE" -> AlertTier.TORNADO_PDS
                event == "Tornado Emergency" -> AlertTier.TORNADO_EMERGENCY
                else -> AlertTier.TORNADO_WARNING
            }
        }
        if (event == "Severe Thunderstorm Warning") {
            val threat = parameters["thunderstormDamageThreat"]?.firstOrNull()?.uppercase()
            return if (threat == "DESTRUCTIVE") AlertTier.TSTORM_DESTRUCTIVE else AlertTier.SEVERE_WARNING
        }
        return DIRECT_MAP[event] ?: AlertTier.ADVISORY
    }
}
