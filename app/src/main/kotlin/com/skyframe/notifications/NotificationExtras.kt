package com.skyframe.notifications

/**
 * Intent extra keys shared across NotificationDispatcher (publisher),
 * MainActivity (tap-target), DismissReceiver (dismiss-action target),
 * and FullScreenAlertActivity (full-screen-intent target).
 *
 * Single source of truth so a key rename doesn't quietly desynchronize
 * publishers from consumers.
 */
object NotificationExtras {
    const val ALERT_ID        = "com.skyframe.notifications.ALERT_ID"
    const val NOTIFICATION_ID = "com.skyframe.notifications.NOTIFICATION_ID"
}
