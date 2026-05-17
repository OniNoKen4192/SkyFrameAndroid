package com.skyframe.data.alerts

import com.skyframe.domain.AlertTier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AlertClassifierTest {

    @Test
    fun `unknown event with no parameters falls through to advisory catch-all`() {
        assertEquals(AlertTier.ADVISORY, AlertClassifier.classify("Some Bizarre Event", emptyMap()))
    }

    @Test
    fun `known mapped event resolves directly`() {
        assertEquals(AlertTier.BLIZZARD, AlertClassifier.classify("Blizzard Warning", emptyMap()))
        assertEquals(AlertTier.WINTER_STORM, AlertClassifier.classify("Winter Storm Warning", emptyMap()))
        assertEquals(AlertTier.FLOOD, AlertClassifier.classify("Flood Warning", emptyMap()))
        assertEquals(AlertTier.FLOOD, AlertClassifier.classify("Flash Flood Warning", emptyMap()))
        assertEquals(AlertTier.HEAT, AlertClassifier.classify("Heat Advisory", emptyMap()))
        assertEquals(AlertTier.WATCH, AlertClassifier.classify("Tornado Watch", emptyMap()))
        assertEquals(AlertTier.WATCH, AlertClassifier.classify("Severe Thunderstorm Watch", emptyMap()))
        assertEquals(AlertTier.ADVISORY_HIGH, AlertClassifier.classify("Wind Advisory", emptyMap()))
        assertEquals(AlertTier.ADVISORY_HIGH, AlertClassifier.classify("Frost Advisory", emptyMap()))
    }

    @Test
    fun `tornado warning with no damage threat is plain tornado-warning`() {
        assertEquals(
            AlertTier.TORNADO_WARNING,
            AlertClassifier.classify("Tornado Warning", emptyMap())
        )
    }

    @Test
    fun `tornado warning with CONSIDERABLE damage threat upgrades to PDS`() {
        assertEquals(
            AlertTier.TORNADO_PDS,
            AlertClassifier.classify(
                event = "Tornado Warning",
                parameters = mapOf("tornadoDamageThreat" to listOf("CONSIDERABLE"))
            )
        )
    }

    @Test
    fun `tornado warning with CATASTROPHIC damage threat escalates to emergency`() {
        assertEquals(
            AlertTier.TORNADO_EMERGENCY,
            AlertClassifier.classify(
                event = "Tornado Warning",
                parameters = mapOf("tornadoDamageThreat" to listOf("CATASTROPHIC"))
            )
        )
    }

    @Test
    fun `tornado emergency event resolves to emergency tier without explicit threat`() {
        assertEquals(
            AlertTier.TORNADO_EMERGENCY,
            AlertClassifier.classify("Tornado Emergency", emptyMap())
        )
    }

    @Test
    fun `severe thunderstorm warning is severe-warning by default`() {
        assertEquals(
            AlertTier.SEVERE_WARNING,
            AlertClassifier.classify("Severe Thunderstorm Warning", emptyMap())
        )
    }

    @Test
    fun `severe thunderstorm with DESTRUCTIVE threat upgrades to tstorm-destructive`() {
        assertEquals(
            AlertTier.TSTORM_DESTRUCTIVE,
            AlertClassifier.classify(
                event = "Severe Thunderstorm Warning",
                parameters = mapOf("thunderstormDamageThreat" to listOf("DESTRUCTIVE"))
            )
        )
    }

    @Test
    fun `damage threat lookups are case-insensitive against the value`() {
        assertEquals(
            AlertTier.TSTORM_DESTRUCTIVE,
            AlertClassifier.classify(
                event = "Severe Thunderstorm Warning",
                parameters = mapOf("thunderstormDamageThreat" to listOf("destructive"))
            )
        )
    }

    @Test
    fun `empty parameter list is treated as absent`() {
        assertEquals(
            AlertTier.TORNADO_WARNING,
            AlertClassifier.classify(
                event = "Tornado Warning",
                parameters = mapOf("tornadoDamageThreat" to emptyList())
            )
        )
    }
}
