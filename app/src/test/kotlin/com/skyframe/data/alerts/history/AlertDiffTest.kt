package com.skyframe.data.alerts.history

import com.skyframe.domain.Alert
import com.skyframe.domain.AlertSeverity
import com.skyframe.domain.AlertTier
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AlertDiffTest {

    private fun alert(id: String, tier: AlertTier = AlertTier.SEVERE_WARNING) = Alert(
        id = id,
        event = "Severe Thunderstorm Warning",
        tier = tier,
        severity = AlertSeverity.SEVERE,
        headline = "",
        description = "",
        issuedAt = Instant.parse("2026-05-19T19:00:00Z"),
        effective = Instant.parse("2026-05-19T19:00:00Z"),
        expires = Instant.parse("2026-05-19T20:00:00Z"),
        areaDesc = "Milwaukee County",
    )

    @Test
    fun `empty current and empty lastSeen returns empty`() {
        val result = AlertDiff.diff(current = emptyList(), lastSeen = emptySet(), acknowledged = emptySet())
        assertEquals(emptyList<Alert>(), result)
    }

    @Test
    fun `all current and empty lastSeen returns all current`() {
        val a = alert("alert-1"); val b = alert("alert-2")
        val result = AlertDiff.diff(current = listOf(a, b), lastSeen = emptySet(), acknowledged = emptySet())
        assertEquals(listOf(a, b), result)
    }

    @Test
    fun `partial overlap returns only new alerts`() {
        val a = alert("alert-1"); val b = alert("alert-2"); val c = alert("alert-3")
        val result = AlertDiff.diff(
            current = listOf(a, b, c),
            lastSeen = setOf("alert-1"),
            acknowledged = emptySet(),
        )
        assertEquals(listOf(b, c), result)
    }

    @Test
    fun `acknowledged-but-new alerts are filtered out`() {
        val a = alert("alert-1"); val b = alert("alert-2")
        val result = AlertDiff.diff(
            current = listOf(a, b),
            lastSeen = emptySet(),
            acknowledged = setOf("alert-2"),
        )
        assertEquals(listOf(a), result)
    }

    @Test
    fun `acknowledged-and-old alerts are filtered out`() {
        val a = alert("alert-1")
        val result = AlertDiff.diff(
            current = listOf(a),
            lastSeen = setOf("alert-1"),
            acknowledged = setOf("alert-1"),
        )
        assertEquals(emptyList<Alert>(), result)
    }

    @Test
    fun `same alert reissued (id in lastSeen) does not re-emit`() {
        // NWS sometimes re-sends an alert with extended expires but the
        // same identifier. We rely on id-based dedup.
        val a = alert("alert-1")
        val result = AlertDiff.diff(
            current = listOf(a),
            lastSeen = setOf("alert-1"),
            acknowledged = emptySet(),
        )
        assertEquals(emptyList<Alert>(), result)
    }
}
