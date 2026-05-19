package com.skyframe.data.alerts.history

import com.skyframe.domain.Alert

/**
 * Pure "what's new since last poll" predicate.
 *
 * An alert is "new" iff its id is absent from BOTH the lastSeen set
 * (previous poll's full ID list) AND the acknowledged set (user-dismissed
 * IDs from AlertAcknowledgmentRepository).
 *
 * - lastSeen prevents re-notifying on every poll while an alert is still active.
 * - acknowledged prevents re-notifying after the user dismissed in-app or
 *   via the system DISMISS action.
 *
 * Pure function, no I/O, no side effects. Trivially testable.
 */
object AlertDiff {
    fun diff(
        current: List<Alert>,
        lastSeen: Set<String>,
        acknowledged: Set<String>,
    ): List<Alert> = current.filter { c ->
        c.id !in lastSeen && c.id !in acknowledged
    }
}
