package com.skyframe.data.nws

import com.skyframe.data.alerts.AlertClassifier
import com.skyframe.domain.Alert
import com.skyframe.domain.AlertSeverity
import kotlinx.datetime.Instant

object AlertNormalizer {

    fun normalize(dto: AlertsDto): List<Alert> {
        return dto.features
            .map { feature ->
                val props = feature.properties
                Alert(
                    id = props.id,
                    event = props.event,
                    tier = AlertClassifier.classify(props.event, props.parameters),
                    severity = parseSeverity(props.severity),
                    headline = props.headline ?: props.event,
                    description = props.description,
                    // NWS spec allows `sent` to be omitted; fall back to `effective`.
                    issuedAt = Instant.parse(props.sent ?: props.effective),
                    effective = Instant.parse(props.effective),
                    expires = Instant.parse(props.expires),
                    areaDesc = props.areaDesc,
                )
            }
            .sortedWith(compareBy({ it.tier.rank }, { -it.issuedAt.epochSeconds }))
    }

    private fun parseSeverity(s: String): AlertSeverity = when (s) {
        "Extreme" -> AlertSeverity.EXTREME
        "Severe" -> AlertSeverity.SEVERE
        "Moderate" -> AlertSeverity.MODERATE
        "Minor" -> AlertSeverity.MINOR
        else -> AlertSeverity.UNKNOWN
    }
}
