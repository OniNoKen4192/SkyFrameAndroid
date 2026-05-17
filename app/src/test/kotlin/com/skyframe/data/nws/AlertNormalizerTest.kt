package com.skyframe.data.nws

import com.skyframe.domain.AlertTier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AlertNormalizerTest {

    @Test
    fun `normalize maps event and parameters to tier`() {
        val dto = AlertsDto(features = listOf(
            AlertFeatureDto(
                id = "feature-id-1",
                properties = AlertProperties(
                    id = "urn:oid:test-tornado",
                    event = "Tornado Warning",
                    severity = "Extreme",
                    headline = "Tornado Warning issued",
                    description = "TAKE COVER.",
                    sent = "2026-05-16T14:00:00Z",
                    effective = "2026-05-16T14:00:00Z",
                    expires = "2026-05-16T14:30:00Z",
                    areaDesc = "Milwaukee County",
                    parameters = mapOf("tornadoDamageThreat" to listOf("CATASTROPHIC")),
                )
            )
        ))
        val result = AlertNormalizer.normalize(dto)
        assertEquals(1, result.size)
        assertEquals(AlertTier.TORNADO_EMERGENCY, result[0].tier)
    }

    @Test
    fun `normalize sorts by tier rank ascending then by issuedAt descending`() {
        val dto = AlertsDto(features = listOf(
            simpleAlert("a", "Wind Advisory", "2026-05-16T10:00:00Z"),
            simpleAlert("b", "Tornado Warning", "2026-05-16T11:00:00Z"),
            simpleAlert("c", "Severe Thunderstorm Warning", "2026-05-16T12:00:00Z"),
        ))
        val result = AlertNormalizer.normalize(dto)
        // Tornado (rank 3) first, then Severe (rank 5), then Wind Advisory (rank 12)
        assertEquals("b", result[0].id.substringAfterLast(':'))
        assertEquals("c", result[1].id.substringAfterLast(':'))
        assertEquals("a", result[2].id.substringAfterLast(':'))
    }

    @Test
    fun `unknown event falls through to advisory`() {
        val dto = AlertsDto(features = listOf(
            simpleAlert("a", "Some Made-Up Event", "2026-05-16T10:00:00Z")
        ))
        assertEquals(AlertTier.ADVISORY, AlertNormalizer.normalize(dto)[0].tier)
    }

    private fun simpleAlert(idSuffix: String, event: String, sent: String): AlertFeatureDto =
        AlertFeatureDto(
            id = "feature-$idSuffix",
            properties = AlertProperties(
                id = "urn:oid:$idSuffix",
                event = event, severity = "Moderate",
                headline = event, description = "",
                sent = sent, effective = sent, expires = sent,
                areaDesc = "Test County", parameters = emptyMap(),
            )
        )
}
