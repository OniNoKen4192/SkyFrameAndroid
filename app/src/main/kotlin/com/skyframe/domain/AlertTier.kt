package com.skyframe.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Severity tier for NWS alerts. Rank 1 = most severe. The 13 tiers match
 * the web project's shared/alert-tiers.ts type union one-to-one; IDs are
 * the wire-format strings used in serialized payloads.
 *
 * Adding a tier is a coordinated change: update this enum, ALERT_TIERS.md,
 * and any Composable that switches on tier explicitly.
 */
@Serializable
enum class AlertTier(
    val id: String,
    val rank: Int,
    val baseColor: Long,
    val darkColor: Long,
) {
    @SerialName("tornado-emergency")
    TORNADO_EMERGENCY("tornado-emergency", 1, 0xFFB052E4, 0xFF6F3490),

    @SerialName("tornado-pds")
    TORNADO_PDS("tornado-pds", 2, 0xFFFF55C8, 0xFFA1367E),

    @SerialName("tornado-warning")
    TORNADO_WARNING("tornado-warning", 3, 0xFFFF4444, 0xFFA02828),

    @SerialName("tstorm-destructive")
    TSTORM_DESTRUCTIVE("tstorm-destructive", 4, 0xFFFF4466, 0xFFA12B40),

    @SerialName("severe-warning")
    SEVERE_WARNING("severe-warning", 5, 0xFFFF8800, 0xFFA05500),

    @SerialName("blizzard")
    BLIZZARD("blizzard", 6, 0xFFFFFFFF, 0xFFBBBBBB),

    @SerialName("winter-storm")
    WINTER_STORM("winter-storm", 7, 0xFF4488FF, 0xFF2A55A0),

    @SerialName("flood")
    FLOOD("flood", 8, 0xFF22CC66, 0xFF147A3D),

    @SerialName("heat")
    HEAT("heat", 9, 0xFFFF5533, 0xFFA0331C),

    @SerialName("special-weather-statement")
    SPECIAL_WEATHER_STATEMENT("special-weather-statement", 10, 0xFFEE82EE, 0xFF9D539D),

    @SerialName("watch")
    WATCH("watch", 11, 0xFFFFDD33, 0xFFA08820),

    @SerialName("advisory-high")
    ADVISORY_HIGH("advisory-high", 12, 0xFFFFAA22, 0xFFA06D15),

    @SerialName("advisory")
    ADVISORY("advisory", 13, 0xFF00E5D1, 0xFF008E82);

    companion object {
        /** Lookup by wire-format ID; returns null on unknown. */
        fun fromId(id: String): AlertTier? = entries.firstOrNull { it.id == id }
    }
}
