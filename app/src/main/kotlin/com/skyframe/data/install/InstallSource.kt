package com.skyframe.data.install

import android.content.Context
import android.os.Build

/**
 * Detects whether this app was installed from the Google Play Store.
 *
 * Used to gate the "Check GitHub for SkyFrame updates" checkbox: Play-installed
 * users get updates from Google Play, so the in-app GitHub poll would be
 * redundant and confusing. Sideload users (APK from GitHub release) genuinely
 * need the polling.
 *
 * API 30+ uses PackageManager.getInstallSourceInfo (modern, recommended).
 * API 26-29 falls back to the deprecated getInstallerPackageName. Both wrapped
 * in runCatching since some manufacturers throw IllegalArgumentException for
 * package names that resolve oddly.
 */
object InstallSource {
    private const val PLAY_PACKAGE = "com.android.vending"

    fun isFromPlayStore(context: Context): Boolean {
        val pm = context.packageManager
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { pm.getInstallSourceInfo(context.packageName).installingPackageName }
                .getOrNull()
        } else {
            @Suppress("DEPRECATION")
            runCatching { pm.getInstallerPackageName(context.packageName) }.getOrNull()
        }
        return installer == PLAY_PACKAGE
    }
}
