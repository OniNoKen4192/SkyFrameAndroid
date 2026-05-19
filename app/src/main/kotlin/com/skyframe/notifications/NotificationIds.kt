package com.skyframe.notifications

/**
 * Stable Int hash from an alert ID string. Used as the notification ID so that
 * a re-fired alert (same NWS id, extended expires) replaces the existing
 * shade entry instead of stacking a new one.
 *
 * String.hashCode is deterministic per JVM run and stable across runs within
 * the same Kotlin/JVM version; that's sufficient for the lifecycle of a single
 * device install. Collisions are vanishingly unlikely (2^32 space, ~10 alerts
 * a year for a single location).
 */
object NotificationIds {
    fun forAlertId(id: String): Int = id.hashCode()
}
