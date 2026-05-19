# SkyFrame Android — Plan 4: Background Alerts + Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the headline native feature — severe weather alerts arrive on the user's lock screen even when SkyFrame is closed. WorkManager 15-min periodic poll + ExpeditedWorkRequest escalation for top-tier alerts. Five notification channels routed by tier rank. Full-screen intent for life-safety alerts. Permission cascade appended to first-run onboarding.

**Architecture:** `AlertCheckWorker` (CoroutineWorker + @HiltWorker) registered via WorkManager `PeriodicWorkRequest` (15-min, NetworkType.CONNECTED). Worker fetches `/alerts/active`, classifies via existing `AlertNormalizer`, diffs against `LastSeenAlertRepository` and `AlertAcknowledgmentRepository` via pure `AlertDiff` function, fires notifications via `NotificationDispatcher`. Top-tier active alerts chain an `EscalationWorker` ExpeditedWorkRequest at ~2-min cadence. Life-safety channel uses `setFullScreenIntent` targeting a dedicated `FullScreenAlertActivity` with `setShowWhenLocked` + `setTurnScreenOn`.

**Tech Stack:**
- WorkManager 2.10.x + `androidx.hilt:hilt-work` (already wired in version catalog)
- `NotificationCompat.Builder` + `NotificationManagerCompat` (5 channels, 2 groups)
- `ActivityResultContracts.RequestPermission` for POST_NOTIFICATIONS
- `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` system intent (Android 14+)
- `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` system intent
- Python 3 + numpy + stdlib `wave` + ffmpeg for audio generator
- JUnit5 + MockK + `WorkManagerTestInitHelper`

**Reference spec:** [docs/superpowers/specs/2026-05-19-skyframe-android-plan-4-background-alerts-design.md](../specs/2026-05-19-skyframe-android-plan-4-background-alerts-design.md)

**Prior plans:** Plan 3 ([v0.3.0](https://github.com/OniNoKen4192/SkyFrameAndroid/releases/tag/v0.3.0)) shipped the SettingsScreen and permission cascade pattern this plan extends.

---

## File Structure (new + modified)

```
app/src/main/kotlin/com/skyframe/
  SkyFrameApp.kt                                   MODIFIED — Configuration.Provider
                                                   + NotificationChannels.createAll()
                                                   + AlertCheckScheduler.schedulePeriodic()
  MainActivity.kt                                  MODIFIED — handle EXTRA_ALERT_ID intent;
                                                   updated start-destination logic
  background/
    AlertCheckWorker.kt                            NEW — @HiltWorker CoroutineWorker
    EscalationWorker.kt                            NEW — one-shot ExpeditedWorkRequest
    AlertCheckScheduler.kt                         NEW — PeriodicWorkRequest registration
    SkyFrameWorkerFactory.kt                       NEW — HiltWorkerFactory injector
  data/alerts/history/
    LastSeenAlertRepository.kt                     NEW — DataStore<Set<String>>
    AlertDiff.kt                                   NEW — pure diff(current, lastSeen, ack)
  notifications/
    NotificationChannels.kt                        NEW — createAll(context); 5 channels + 2 groups
    NotificationIds.kt                             NEW — forAlertId(id): Int stable hash
    NotificationDispatcher.kt                      NEW — notify(alert)
    DismissReceiver.kt                             NEW — BroadcastReceiver for DISMISS
    NotificationExtras.kt                          NEW — Intent extra keys
  ui/alert/
    FullScreenAlertActivity.kt                     NEW — separate Activity, life_safety only
  ui/screens/
    PermissionScreen.kt                            NEW — onboarding permission cascade
    SettingsScreen.kt                              MODIFIED — POST_NOTIFICATIONS denied banner
  ui/nav/
    NavRoutes.kt                                   MODIFIED — add PERMISSIONS
    SkyFrameNavHost.kt                             MODIFIED — wire PERMISSIONS destination
  viewmodel/
    DashboardViewModel.kt                          MODIFIED — dismissAlert cancels notification
    SettingsViewModel.kt                           MODIFIED — POST_NOTIFICATIONS-granted flag
                                                   in uiState for banner rendering
  data/settings/
    SettingsRepository.kt                          MODIFIED — permissionsPromptedAt: Long
    SettingsKeys.kt                                MODIFIED — PERMISSIONS_PROMPTED_AT key

app/src/main/AndroidManifest.xml                   MODIFIED — 3 new permissions,
                                                   FullScreenAlertActivity registration,
                                                   DismissReceiver registration

app/src/main/res/raw/
  notification_life_safety.ogg                     NEW (generated, committed)
  notification_severe.ogg                          NEW (generated, committed)

tools/
  generate-notification-audio.py                   NEW — 47 CFR § 11.45 compliant generator

app/src/test/kotlin/com/skyframe/
  data/alerts/history/
    AlertDiffTest.kt                               NEW — 6 tests
    LastSeenAlertRepositoryTest.kt                 NEW — 3 tests
  notifications/
    NotificationIdsTest.kt                         NEW — 2 tests
  background/
    AlertCheckSchedulerTest.kt                     NEW — 2 tests
    AlertCheckWorkerTest.kt                        NEW — 8 tests
    EscalationWorkerTest.kt                        NEW — 3 tests

docs/
  PROJECT_STATUS.md                                MODIFIED — Plan 4 phase list
  ROADMAP.md                                       MODIFIED — flip Plan 4 to ✅ Shipped
  SMOKE_TEST.md                                    MODIFIED — Plan 4 verification
  CHANGELOG.md                                     MODIFIED — v0.4.0 release notes
README.md                                          MODIFIED — tag badge + status-by-area
```

---

## Phase A — Foundation: Manifest, Channels, IDs, WorkManager init

Wire up the infrastructure pieces nothing else depends on. Manifest permissions + receiver/activity declarations, the channels-and-groups creator, the stable notification-ID helper, and the Hilt-aware WorkManager factory.

### Task A.1: Add manifest permissions + activity + receiver

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Read current manifest**

```powershell
Get-Content "app/src/main/AndroidManifest.xml"
```

Expected: Plan 3's manifest with INTERNET + ACCESS_FINE_LOCATION + MainActivity registration.

- [ ] **Step 2: Add permissions + FullScreenAlertActivity + DismissReceiver**

Replace the entire contents of `app/src/main/AndroidManifest.xml` with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.USE_FULL_SCREEN_INTENT" />
    <uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />

    <application
        android:name=".SkyFrameApp"
        android:allowBackup="false"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="false"
        android:theme="@style/Theme.SkyFrame"
        tools:targetApi="34">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.SkyFrame">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <activity
            android:name=".ui.alert.FullScreenAlertActivity"
            android:exported="false"
            android:showWhenLocked="true"
            android:turnScreenOn="true"
            android:launchMode="singleTop"
            android:excludeFromRecents="true"
            android:theme="@style/Theme.SkyFrame" />

        <receiver
            android:name=".notifications.DismissReceiver"
            android:exported="false" />
    </application>
</manifest>
```

- [ ] **Step 3: Verify build still passes**

```powershell
./gradlew.bat :app:assembleDebug --no-daemon 2>&1 | Select-Object -Last 4
```

Expected: BUILD SUCCESSFUL. (FullScreenAlertActivity and DismissReceiver classes don't exist yet; AAPT verifies XML syntax, not class resolution. Final compile happens later phases.)

If the manifest merger complains that the activity/receiver classes are unresolved, it's a hint that AAPT2 strict resolution is on — in that case, defer staging this change to Task G.1 (FullScreenAlertActivity) and Task E.2 (DismissReceiver). The manifest-only commit can stand if AAPT accepts it.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/AndroidManifest.xml
git commit -m "$(@'
feat(manifest): declare POST_NOTIFICATIONS + USE_FULL_SCREEN_INTENT
+ REQUEST_IGNORE_BATTERY_OPTIMIZATIONS

Pre-registers FullScreenAlertActivity (showWhenLocked + turnScreenOn,
singleTop, excludeFromRecents) and DismissReceiver. The Kotlin classes
land in later Plan 4 phases; manifest-first lets each subsequent task
focus on one piece.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task A.2: NotificationChannels (5 channels + 2 groups)

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/notifications/NotificationChannels.kt`

No tests — channel-creation is idempotent API plumbing; manual smoke test verifies.

- [ ] **Step 1: Create the package directory**

```powershell
New-Item -ItemType Directory -Force -Path "app/src/main/kotlin/com/skyframe/notifications" | Out-Null
"ok"
```

- [ ] **Step 2: Write NotificationChannels.kt**

Create `app/src/main/kotlin/com/skyframe/notifications/NotificationChannels.kt`:

```kotlin
package com.skyframe.notifications

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.net.Uri
import com.skyframe.R

/**
 * Creates the five notification channels and two groups SkyFrame uses.
 * Idempotent — Android no-ops on re-creation with the same channel/group ID.
 * Called from SkyFrameApp.onCreate.
 *
 * Channel → tier mapping (see ALERT_TIERS.md):
 *   life_safety     ranks 1-4    (tornado-*, tstorm-destructive)
 *   severe_weather  rank 5       (severe-warning)
 *   watches         ranks 6-8    (blizzard, winter-storm, flood)
 *   advisories      ranks 9-13   (heat, special-weather-statement, watch,
 *                                  advisory-high, advisory)
 *   app_updates     synthetic update alerts only
 */
object NotificationChannels {

    // Channel IDs - public so NotificationDispatcher can reference them.
    const val LIFE_SAFETY     = "life_safety"
    const val SEVERE_WEATHER  = "severe_weather"
    const val WATCHES         = "watches"
    const val ADVISORIES      = "advisories"
    const val APP_UPDATES     = "app_updates"

    // Group IDs.
    private const val GROUP_WEATHER = "weather_alerts"
    private const val GROUP_SYSTEM  = "system"

    fun createAll(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        nm.createNotificationChannelGroup(
            NotificationChannelGroup(GROUP_WEATHER, "Weather alerts"),
        )
        nm.createNotificationChannelGroup(
            NotificationChannelGroup(GROUP_SYSTEM, "App updates"),
        )

        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val lifeSafetySound = soundUri(context, R.raw.notification_life_safety)
        val severeSound     = soundUri(context, R.raw.notification_severe)

        nm.createNotificationChannel(
            NotificationChannel(LIFE_SAFETY, "Life-safety alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Tornado warnings, destructive thunderstorm warnings. Bypasses Do Not Disturb."
                group = GROUP_WEATHER
                setSound(lifeSafetySound, audioAttrs)
                setBypassDnd(true)
                enableLights(true)
                lightColor = 0xFFFF4444.toInt()
                enableVibration(true)
            },
        )

        nm.createNotificationChannel(
            NotificationChannel(SEVERE_WEATHER, "Severe weather", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Severe thunderstorm warnings."
                group = GROUP_WEATHER
                setSound(severeSound, audioAttrs)
                enableVibration(true)
            },
        )

        nm.createNotificationChannel(
            NotificationChannel(WATCHES, "Watches", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Blizzard, winter storm, flood warnings."
                group = GROUP_WEATHER
            },
        )

        nm.createNotificationChannel(
            NotificationChannel(ADVISORIES, "Advisories", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Heat advisories, special weather statements, lower-tier alerts."
                group = GROUP_WEATHER
                setSound(null, null)
            },
        )

        nm.createNotificationChannel(
            NotificationChannel(APP_UPDATES, "App updates", NotificationManager.IMPORTANCE_MIN).apply {
                description = "New SkyFrame release available on GitHub."
                group = GROUP_SYSTEM
                setSound(null, null)
            },
        )
    }

    private fun soundUri(context: Context, @androidx.annotation.RawRes resId: Int): Uri =
        Uri.parse("android.resource://${context.packageName}/$resId")
}
```

- [ ] **Step 3: Verify compile fails (raw resources don't exist yet)**

```powershell
./gradlew.bat :app:compileDebugKotlin --no-daemon 2>&1 | Select-String -Pattern "error|unresolved" | Select-Object -First 5
```

Expected: errors about `R.raw.notification_life_safety` and `R.raw.notification_severe` being unresolved. That's correct — they ship in Phase B.

- [ ] **Step 4: Temporarily stub the R references so the file can be staged**

In `NotificationChannels.kt`, change the `lifeSafetySound` and `severeSound` initializations to use null:

```kotlin
val lifeSafetySound: Uri? = null  // R.raw.notification_life_safety lands in Phase B
val severeSound:     Uri? = null  // R.raw.notification_severe       lands in Phase B
```

And drop the `import com.skyframe.R` line until Phase B reinstates it.

This is a one-task scope; Phase B's audio task reverts both lines to the real `soundUri(...)` calls.

- [ ] **Step 5: Verify compile**

```powershell
./gradlew.bat :app:compileDebugKotlin --no-daemon 2>&1 | Select-Object -Last 4
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/notifications/NotificationChannels.kt
git commit -m "$(@'
feat(notifications): NotificationChannels with 5 channels + 2 groups

life_safety (HIGH + DND bypass + lights) for ranks 1-4.
severe_weather (HIGH) for rank 5.
watches (DEFAULT) for ranks 6-8.
advisories (LOW, silent) for ranks 9-13.
app_updates (MIN, silent) for synthetic update alerts.

Groups: weather_alerts + system. Channel groups make these individually
editable in Android system settings (API 26+).

Sound URIs temporarily nulled - real .ogg references land in Phase B
once the audio generator emits the files.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task A.3: NotificationIds.forAlertId + tests

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/notifications/NotificationIds.kt`
- Create: `app/src/test/kotlin/com/skyframe/notifications/NotificationIdsTest.kt`

- [ ] **Step 1: Create the test package directory**

```powershell
New-Item -ItemType Directory -Force -Path "app/src/test/kotlin/com/skyframe/notifications" | Out-Null
"ok"
```

- [ ] **Step 2: Write the failing test**

Create `app/src/test/kotlin/com/skyframe/notifications/NotificationIdsTest.kt`:

```kotlin
package com.skyframe.notifications

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class NotificationIdsTest {

    @Test
    fun `same alert id produces the same Int`() {
        val a = NotificationIds.forAlertId("urn:oid:2.49.0.1.840.0.test-tornado-1")
        val b = NotificationIds.forAlertId("urn:oid:2.49.0.1.840.0.test-tornado-1")
        assertEquals(a, b)
    }

    @Test
    fun `different alert ids produce different Ints`() {
        val a = NotificationIds.forAlertId("urn:oid:2.49.0.1.840.0.test-tornado-1")
        val b = NotificationIds.forAlertId("urn:oid:2.49.0.1.840.0.test-tornado-2")
        assertNotEquals(a, b)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "com.skyframe.notifications.NotificationIdsTest" --no-daemon
```

Expected: compile error (NotificationIds unresolved).

- [ ] **Step 4: Implement NotificationIds.kt**

Create `app/src/main/kotlin/com/skyframe/notifications/NotificationIds.kt`:

```kotlin
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
```

- [ ] **Step 5: Run tests to verify pass**

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "com.skyframe.notifications.NotificationIdsTest" --no-daemon
```

Expected: 2 tests pass.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/notifications/NotificationIds.kt app/src/test/kotlin/com/skyframe/notifications/NotificationIdsTest.kt
git commit -m "$(@'
feat(notifications): NotificationIds.forAlertId stable hash helper

Single function. Same NWS alert id -> same Int -> notification re-fire
replaces existing shade entry instead of stacking. String.hashCode is
sufficient for collision-free use at this scale (~10 alerts/year per
location, 2^32 namespace).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task A.4: NotificationExtras constants

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/notifications/NotificationExtras.kt`

- [ ] **Step 1: Write NotificationExtras.kt**

Create `app/src/main/kotlin/com/skyframe/notifications/NotificationExtras.kt`:

```kotlin
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
```

- [ ] **Step 2: Verify compile**

```powershell
./gradlew.bat :app:compileDebugKotlin --no-daemon 2>&1 | Select-Object -Last 4
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/notifications/NotificationExtras.kt
git commit -m "$(@'
feat(notifications): NotificationExtras intent extra key constants

Single source of truth for ALERT_ID + NOTIFICATION_ID across
NotificationDispatcher publisher and MainActivity / DismissReceiver /
FullScreenAlertActivity consumers.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task A.5: SkyFrameWorkerFactory + SkyFrameApp Configuration.Provider

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/background/SkyFrameWorkerFactory.kt`
- Modify: `app/src/main/kotlin/com/skyframe/SkyFrameApp.kt`

- [ ] **Step 1: Create the background package directory**

```powershell
New-Item -ItemType Directory -Force -Path "app/src/main/kotlin/com/skyframe/background" | Out-Null
New-Item -ItemType Directory -Force -Path "app/src/test/kotlin/com/skyframe/background" | Out-Null
"ok"
```

- [ ] **Step 2: Write SkyFrameWorkerFactory.kt**

Create `app/src/main/kotlin/com/skyframe/background/SkyFrameWorkerFactory.kt`:

```kotlin
package com.skyframe.background

import androidx.hilt.work.HiltWorkerFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin re-export for clarity. HiltWorkerFactory is already @Singleton-eligible
 * via androidx.hilt:hilt-work; injecting it into SkyFrameApp lets WorkManager
 * resolve @HiltWorker-annotated workers (AlertCheckWorker, EscalationWorker).
 */
@Singleton
class SkyFrameWorkerFactory @Inject constructor(
    val hiltFactory: HiltWorkerFactory,
)
```

- [ ] **Step 3: Update SkyFrameApp.kt**

Replace the entire contents of `app/src/main/kotlin/com/skyframe/SkyFrameApp.kt` with:

```kotlin
package com.skyframe

import android.app.Application
import androidx.work.Configuration
import com.skyframe.background.SkyFrameWorkerFactory
import com.skyframe.notifications.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SkyFrameApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactoryHolder: SkyFrameWorkerFactory

    // WorkManager's on-demand initializer reads this; required for @HiltWorker
    // resolution. AlertCheckScheduler.schedulePeriodic() (Phase D) enqueues
    // the actual periodic work.
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactoryHolder.hiltFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        // AlertCheckScheduler.schedulePeriodic(this) wires in Phase D
    }
}
```

- [ ] **Step 4: Verify compile**

```powershell
./gradlew.bat :app:compileDebugKotlin --no-daemon 2>&1 | Select-Object -Last 4
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Disable WorkManager's auto-initializer in manifest**

WorkManager 2.10 auto-initializes via a `<provider>` declared in its own manifest. When you implement `Configuration.Provider`, the auto-initializer must be disabled or it races our on-demand init.

In `app/src/main/AndroidManifest.xml`, add this inside the `<application>` element (after the receiver):

```xml
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>
```

- [ ] **Step 6: Verify build**

```powershell
./gradlew.bat :app:assembleDebug --no-daemon 2>&1 | Select-Object -Last 6
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/background/SkyFrameWorkerFactory.kt app/src/main/kotlin/com/skyframe/SkyFrameApp.kt app/src/main/AndroidManifest.xml
git commit -m "$(@'
feat(background): SkyFrameApp implements Configuration.Provider

Hilt-aware WorkManager initialization. SkyFrameWorkerFactory holds the
HiltWorkerFactory injected from the @HiltAndroidApp graph; SkyFrameApp
hands it to WorkManager via Configuration.Provider so @HiltWorker
workers (AlertCheckWorker, EscalationWorker) can resolve their
constructor dependencies.

Manifest disables WorkManager's startup auto-initializer per the
androidx.work docs - mandatory when implementing Configuration.Provider
manually.

NotificationChannels.createAll() called in onCreate; idempotent.
AlertCheckScheduler.schedulePeriodic() wires in Phase D.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

## Phase B — Audio Generator + .ogg Outputs

47 CFR § 11.45 compliant 1050 Hz tone generator + the two `.ogg` files it emits.

### Task B.1: Python generator script

**Files:**
- Create: `tools/generate-notification-audio.py`

- [ ] **Step 1: Create the tools directory**

```powershell
New-Item -ItemType Directory -Force -Path "tools" | Out-Null
"ok"
```

- [ ] **Step 2: Write the generator script**

Create `tools/generate-notification-audio.py`:

```python
#!/usr/bin/env python3
"""
SkyFrame notification audio generator.

Emits two .ogg files for the life_safety and severe_weather notification
channels. Both are 1050 Hz sine waves, inspired by NOAA Weather Radio's
Warning Alarm Tone (WAT) character.

LEGAL CONSTRAINT (47 CFR section 11.45):
The EAS Attention Signal (853 Hz + 960 Hz dual tones) and SAME header bursts
are reserved for actual Emergency Alert System broadcasts. Reproducing them
in non-EAS contexts violates federal law. This generator emits ONLY a single
1050 Hz tone and does NOT combine frequencies that would approximate either
EAS signal. Any future edits MUST preserve this constraint.

Usage:
    python3 tools/generate-notification-audio.py

Requires:
    - Python 3.8+
    - numpy
    - ffmpeg in PATH (for libvorbis .ogg encoding)

Outputs:
    app/src/main/res/raw/notification_life_safety.ogg   (looping channel sound)
    app/src/main/res/raw/notification_severe.ogg        (single-play channel sound)
"""

from __future__ import annotations

import math
import struct
import subprocess
import sys
import wave
from pathlib import Path

import numpy as np

SAMPLE_RATE = 44_100
FREQ_HZ = 1050.0  # NWR WAT character; explicitly NOT EAS Attention Signal


def gen_tone(duration_s: float, fade_ms: float = 8.0) -> np.ndarray:
    """Generate a mono 16-bit PCM 1050 Hz sine wave with short cosine fades
    on each end to avoid click artifacts."""
    n = int(SAMPLE_RATE * duration_s)
    t = np.arange(n) / SAMPLE_RATE
    sine = np.sin(2.0 * math.pi * FREQ_HZ * t)

    fade_n = int(SAMPLE_RATE * (fade_ms / 1000.0))
    if fade_n > 0 and n > 2 * fade_n:
        ramp = 0.5 - 0.5 * np.cos(np.linspace(0.0, math.pi, fade_n))
        sine[:fade_n] *= ramp
        sine[-fade_n:] *= ramp[::-1]

    # 80% peak amplitude to leave headroom; system mixes with other audio.
    return (sine * 0.8 * 32_767).astype(np.int16)


def gen_silence(duration_s: float) -> np.ndarray:
    return np.zeros(int(SAMPLE_RATE * duration_s), dtype=np.int16)


def write_wav(path: Path, samples: np.ndarray) -> None:
    with wave.open(str(path), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        w.writeframes(samples.tobytes())


def wav_to_ogg(wav_path: Path, ogg_path: Path) -> None:
    """Encode WAV -> Ogg Vorbis via ffmpeg. -q:a 5 is ~160 kbps quality;
    overkill for a sine wave but tiny on disk (<10 KB each)."""
    result = subprocess.run(
        ["ffmpeg", "-y", "-i", str(wav_path), "-c:a", "libvorbis", "-q:a", "5", str(ogg_path)],
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        sys.stderr.write(result.stderr)
        raise RuntimeError(f"ffmpeg failed for {ogg_path.name}")


def main() -> int:
    repo_root = Path(__file__).resolve().parent.parent
    out_dir = repo_root / "app" / "src" / "main" / "res" / "raw"
    out_dir.mkdir(parents=True, exist_ok=True)

    tmp_dir = repo_root / "build" / "audio-gen"
    tmp_dir.mkdir(parents=True, exist_ok=True)

    # life_safety: 3 cycles of (500ms tone + 1000ms silence). System channel
    # config sets the channel as looping, so a finite-length file with 3
    # cycles gives natural fade in case looping is preempted.
    cycle = np.concatenate([gen_tone(0.5), gen_silence(1.0)])
    life_safety = np.tile(cycle, 3)
    life_safety_wav = tmp_dir / "life_safety.wav"
    write_wav(life_safety_wav, life_safety)
    wav_to_ogg(life_safety_wav, out_dir / "notification_life_safety.ogg")

    # severe: single ~800ms tone with fades.
    severe = gen_tone(0.8, fade_ms = 20.0)
    severe_wav = tmp_dir / "severe.wav"
    write_wav(severe_wav, severe)
    wav_to_ogg(severe_wav, out_dir / "notification_severe.ogg")

    print(f"Wrote {out_dir / 'notification_life_safety.ogg'}")
    print(f"Wrote {out_dir / 'notification_severe.ogg'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
```

- [ ] **Step 3: Verify Python + ffmpeg are available**

```powershell
python --version
ffmpeg -version 2>&1 | Select-Object -First 1
```

Expected: Python 3.8+ and ffmpeg both report a version. If either is missing on this machine, install before continuing.

- [ ] **Step 4: Commit the generator (.ogg outputs land in B.2)**

```powershell
git add tools/generate-notification-audio.py
git commit -m "$(@'
feat(audio): 1050 Hz NWR-style notification tone generator

Python + numpy + stdlib wave -> 16-bit PCM, then ffmpeg libvorbis to
.ogg. Two outputs:
  - notification_life_safety.ogg: 3 cycles of 500ms tone / 1000ms silence
  - notification_severe.ogg: single 800ms tone, longer fades

Module docstring documents the 47 CFR section 11.45 constraint: NO EAS
Attention Signal (853+960 Hz dual tones) and NO SAME header bursts -
only a single 1050 Hz NWR Warning Alarm Tone character. Federal law
reserves the EAS signals for actual EAS broadcasts.

Outputs land in res/raw/ via the next task; this commit is the script
only so the source-of-truth for the audio is reviewable.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task B.2: Run generator, commit .ogg outputs, re-link channels

**Files:**
- Create: `app/src/main/res/raw/notification_life_safety.ogg`
- Create: `app/src/main/res/raw/notification_severe.ogg`
- Modify: `app/src/main/kotlin/com/skyframe/notifications/NotificationChannels.kt` (restore real soundUri references)

- [ ] **Step 1: Ensure pip deps are installed**

```powershell
python -m pip install --quiet numpy
```

- [ ] **Step 2: Run the generator**

```powershell
python tools/generate-notification-audio.py
```

Expected: prints `Wrote app/src/main/res/raw/notification_life_safety.ogg` + `Wrote app/src/main/res/raw/notification_severe.ogg`.

- [ ] **Step 3: Verify the files exist and are sized reasonably**

```powershell
Get-ChildItem app/src/main/res/raw/notification_*.ogg | Select-Object Name, Length
```

Expected: both `~3-10 KB`. If either is `0 bytes`, the ffmpeg pipe failed — re-run and inspect stderr.

- [ ] **Step 4: Restore real R.raw references in NotificationChannels.kt**

In `app/src/main/kotlin/com/skyframe/notifications/NotificationChannels.kt`, locate:

```kotlin
        val lifeSafetySound: Uri? = null  // R.raw.notification_life_safety lands in Phase B
        val severeSound:     Uri? = null  // R.raw.notification_severe       lands in Phase B
```

Replace with:

```kotlin
        val lifeSafetySound = soundUri(context, R.raw.notification_life_safety)
        val severeSound     = soundUri(context, R.raw.notification_severe)
```

And add back the import at the top:

```kotlin
import com.skyframe.R
```

- [ ] **Step 5: Verify compile + APK build**

```powershell
./gradlew.bat :app:assembleDebug --no-daemon 2>&1 | Select-Object -Last 4
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit .ogg files + NotificationChannels fix**

```powershell
git add app/src/main/res/raw/notification_life_safety.ogg app/src/main/res/raw/notification_severe.ogg app/src/main/kotlin/com/skyframe/notifications/NotificationChannels.kt
git commit -m "$(@'
feat(audio): notification_*.ogg outputs + relink channel sounds

Generated via tools/generate-notification-audio.py. Both files ~3-10 KB.
NotificationChannels now references R.raw.notification_life_safety
and R.raw.notification_severe through soundUri(), reverting the
temporary null placeholders from Task A.2.

Manual ear-test recommended before tagging: each channel should play
a clear 1050 Hz tone with no perceptible clicks at the ends.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

## Phase C — AlertDiff + LastSeenAlertRepository

Two small modules with pure-logic / DataStore-roundtrip tests. Foundations for the worker in Phase D.

### Task C.1: AlertDiff (pure function) + tests

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/data/alerts/history/AlertDiff.kt`
- Create: `app/src/test/kotlin/com/skyframe/data/alerts/history/AlertDiffTest.kt`

- [ ] **Step 1: Create the package directories**

```powershell
New-Item -ItemType Directory -Force -Path "app/src/main/kotlin/com/skyframe/data/alerts/history" | Out-Null
New-Item -ItemType Directory -Force -Path "app/src/test/kotlin/com/skyframe/data/alerts/history" | Out-Null
"ok"
```

- [ ] **Step 2: Write the failing test**

Create `app/src/test/kotlin/com/skyframe/data/alerts/history/AlertDiffTest.kt`:

```kotlin
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
```

- [ ] **Step 3: Run test to verify it fails**

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "com.skyframe.data.alerts.history.AlertDiffTest" --no-daemon
```

Expected: compile error (AlertDiff unresolved).

- [ ] **Step 4: Implement AlertDiff.kt**

Create `app/src/main/kotlin/com/skyframe/data/alerts/history/AlertDiff.kt`:

```kotlin
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
```

- [ ] **Step 5: Run tests to verify pass**

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "com.skyframe.data.alerts.history.AlertDiffTest" --no-daemon
```

Expected: 6 tests pass.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/data/alerts/history/AlertDiff.kt app/src/test/kotlin/com/skyframe/data/alerts/history/AlertDiffTest.kt
git commit -m "$(@'
feat(alerts): AlertDiff pure new-since-last-poll predicate

Single function: diff(current, lastSeen, acknowledged) -> List<Alert>.
Filters current to alerts whose id is in neither lastSeen (prevents
re-notify while still active) nor acknowledged (respects user-dismissed
state from AlertAcknowledgmentRepository).

Pure - no I/O, no side effects. 6 tests cover empty inputs, full new,
partial overlap, acknowledged-but-new, acknowledged-and-old, reissued
same id.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task C.2: LastSeenAlertRepository + tests

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/data/alerts/history/LastSeenAlertRepository.kt`
- Create: `app/src/test/kotlin/com/skyframe/data/alerts/history/LastSeenAlertRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/skyframe/data/alerts/history/LastSeenAlertRepositoryTest.kt`:

```kotlin
package com.skyframe.data.alerts.history

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LastSeenAlertRepositoryTest {

    @TempDir
    lateinit var tempDir: File

    private fun newRepo(): LastSeenAlertRepository {
        val ds = PreferenceDataStoreFactory.create(produceFile = { File(tempDir, "ls.preferences_pb") })
        return LastSeenAlertRepository(ds)
    }

    @Test
    fun `initial read returns empty set`() = runTest {
        assertEquals(emptySet<String>(), newRepo().read())
    }

    @Test
    fun `write then read returns the same set`() = runTest {
        val repo = newRepo()
        repo.write(setOf("alert-1", "alert-2"))
        assertEquals(setOf("alert-1", "alert-2"), repo.read())
    }

    @Test
    fun `overwrite replaces previous set entirely`() = runTest {
        val repo = newRepo()
        repo.write(setOf("alert-1", "alert-2"))
        repo.write(setOf("alert-3"))
        // alert-1 and alert-2 are gone after overwrite
        assertEquals(setOf("alert-3"), repo.read())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "com.skyframe.data.alerts.history.LastSeenAlertRepositoryTest" --no-daemon
```

Expected: compile error (LastSeenAlertRepository unresolved).

- [ ] **Step 3: Implement LastSeenAlertRepository.kt**

Create `app/src/main/kotlin/com/skyframe/data/alerts/history/LastSeenAlertRepository.kt`:

```kotlin
package com.skyframe.data.alerts.history

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Persists the set of NWS alert IDs from the most recent successful background
 * poll. Read by AlertDiff to compute "new since last poll" alerts; overwritten
 * after each successful poll so the set is always exactly one poll's worth.
 *
 * Naturally bounded: NWS rarely returns more than ~5 active alerts for a single
 * point. No pruning logic needed - each write fully replaces the prior set.
 */
@Singleton
class LastSeenAlertRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val key = stringSetPreferencesKey("last_seen_alert_ids")

    suspend fun read(): Set<String> = dataStore.data.first()[key].orEmpty()

    suspend fun write(ids: Set<String>) {
        dataStore.edit { prefs ->
            if (ids.isEmpty()) prefs.remove(key) else prefs[key] = ids
        }
    }
}
```

- [ ] **Step 4: Run tests to verify pass**

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "com.skyframe.data.alerts.history.LastSeenAlertRepositoryTest" --no-daemon
```

Expected: 3 tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/data/alerts/history/LastSeenAlertRepository.kt app/src/test/kotlin/com/skyframe/data/alerts/history/LastSeenAlertRepositoryTest.kt
git commit -m "$(@'
feat(alerts): LastSeenAlertRepository - DataStore-backed Set<String>

Single key (last_seen_alert_ids) holding the IDs from the most recent
background poll. read() returns the set; write(ids) overwrites it
atomically. Empty set is stored as key removal so an empty read is
indistinguishable from a never-written state - both correctly produce
the empty set.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

## Phase D — AlertCheckWorker (baseline) + Scheduler

`AlertCheckWorker` does everything EXCEPT fire notifications — Phase E adds that. We can exercise the worker's fetch + classify + diff + persist behavior in tests via `WorkManagerTestInitHelper` without any notification side-effects yet. Cleaner failure isolation.

### Task D.1: AlertCheckScheduler with KEEP policy + tests

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/background/AlertCheckScheduler.kt`
- Create: `app/src/test/kotlin/com/skyframe/background/AlertCheckSchedulerTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/skyframe/background/AlertCheckSchedulerTest.kt`:

```kotlin
package com.skyframe.background

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.junit.jupiter.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
class AlertCheckSchedulerTest {

    private lateinit var context: Context

    @BeforeEach
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
    }

    @Test
    fun `schedulePeriodic enqueues a unique periodic work request`() {
        AlertCheckScheduler.schedulePeriodic(context)

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(AlertCheckScheduler.UNIQUE_WORK_NAME)
            .get()
        assertEquals(1, infos.size)
        assertTrue(infos[0].state == WorkInfo.State.ENQUEUED || infos[0].state == WorkInfo.State.RUNNING)
    }

    @Test
    fun `schedulePeriodic called twice keeps the original work (idempotent)`() {
        AlertCheckScheduler.schedulePeriodic(context)
        val first = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(AlertCheckScheduler.UNIQUE_WORK_NAME)
            .get()
            .first()
            .id

        AlertCheckScheduler.schedulePeriodic(context)
        val second = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(AlertCheckScheduler.UNIQUE_WORK_NAME)
            .get()
            .first()
            .id

        // KEEP policy: same work ID, not a new request.
        assertEquals(first, second)
    }
}
```

- [ ] **Step 2: Add Robolectric to testImplementation if not already present**

Check `app/build.gradle.kts` `dependencies { ... }` block for `robolectric`. If absent, add to the version catalog:

In `gradle/libs.versions.toml` `[versions]` add:

```toml
robolectric = "4.13"
```

In `[libraries]` add:

```toml
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
robolectric-junit-jupiter = { group = "org.robolectric", name = "junit-jupiter", version.ref = "robolectric" }
```

In `app/build.gradle.kts`, in the `dependencies { ... }` block, after the existing `testImplementation(libs.work.testing)` line, add:

```kotlin
    testImplementation(libs.robolectric)
    testImplementation(libs.robolectric.junit.jupiter)
```

Also in the `android { ... }` block, add (if not present):

```kotlin
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
```

- [ ] **Step 3: Run test to verify it fails**

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "com.skyframe.background.AlertCheckSchedulerTest" --no-daemon
```

Expected: compile error (AlertCheckScheduler unresolved).

- [ ] **Step 4: Implement AlertCheckScheduler.kt**

Create `app/src/main/kotlin/com/skyframe/background/AlertCheckScheduler.kt`:

```kotlin
package com.skyframe.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Registers AlertCheckWorker as a PeriodicWorkRequest. Called from
 * SkyFrameApp.onCreate; KEEP policy ensures re-calls don't replace
 * the in-flight schedule.
 *
 * 15 minutes is Android's minimum periodic interval - lower cadences
 * require ExpeditedWorkRequest (see EscalationWorker).
 *
 * Constraints:
 *   - NetworkType.CONNECTED (no point polling NWS offline)
 *   - setRequiresBatteryNotLow(false): severe weather doesn't pause for
 *     low battery. Explicit because the default is also false but
 *     reading the code shouldn't require remembering defaults.
 */
object AlertCheckScheduler {

    const val UNIQUE_WORK_NAME = "alert_check_periodic"

    fun schedulePeriodic(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(false)
            .build()

        val request = PeriodicWorkRequestBuilder<AlertCheckWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
```

Note: this references `AlertCheckWorker` which doesn't exist yet — Task D.2 creates it. The test would still fail to compile until then. Continue to D.2 first, then come back and run D.1's test.

- [ ] **Step 5: Commit the scheduler stub (waits for D.2 to compile-test)**

```powershell
git add app/src/main/kotlin/com/skyframe/background/AlertCheckScheduler.kt app/src/test/kotlin/com/skyframe/background/AlertCheckSchedulerTest.kt gradle/libs.versions.toml app/build.gradle.kts
git commit -m "$(@'
feat(background): AlertCheckScheduler + Robolectric test scaffold

PeriodicWorkRequest at 15-min cadence (Android minimum), NetworkType
CONNECTED, KEEP policy so re-calls are idempotent. Companion cancelAll
for explicit teardown.

Robolectric + JUnit5 extension wired into testImplementation so we can
exercise WorkManagerTestInitHelper without Espresso.

AlertCheckWorker referenced in the request builder lands in the next
task; this commit's tests will fail to compile until then.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task D.2: AlertCheckWorker (baseline, no notifications yet) + tests

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/background/AlertCheckWorker.kt`
- Create: `app/src/test/kotlin/com/skyframe/background/AlertCheckWorkerTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/skyframe/background/AlertCheckWorkerTest.kt`:

```kotlin
package com.skyframe.background

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.skyframe.data.acknowledgments.AlertAcknowledgmentRepository
import com.skyframe.data.alerts.history.LastSeenAlertRepository
import com.skyframe.data.nws.AlertsDto
import com.skyframe.data.nws.AlertFeatureDto
import com.skyframe.data.nws.AlertProperties
import com.skyframe.data.nws.NwsClient
import com.skyframe.data.settings.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.junit.jupiter.RobolectricExtension
import java.io.IOException

@ExtendWith(RobolectricExtension::class)
class AlertCheckWorkerTest {

    private lateinit var context: Context
    private lateinit var nws: NwsClient
    private lateinit var settings: SettingsRepository
    private lateinit var lastSeen: LastSeenAlertRepository
    private lateinit var ack: AlertAcknowledgmentRepository

    @BeforeEach
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        nws = mockk()
        settings = mockk()
        lastSeen = mockk(relaxed = true)
        ack = mockk()
        coEvery { ack.snapshot() } returns emptySet()
        coEvery { lastSeen.read() } returns emptySet()
    }

    private fun buildWorker() = TestListenableWorkerBuilder<AlertCheckWorker>(context)
        .setWorkerFactory(
            object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: androidx.work.WorkerParameters,
                ) = AlertCheckWorker(appContext, workerParameters, nws, settings, lastSeen, ack)
            }
        )
        .build()

    private fun configuredSnapshot() = SettingsRepository.Snapshot(
        email = "test@example.com",
        lat = 42.8744, lon = -87.8633,
        locationName = "OAK CREEK WI",
        forecastOffice = "MKX",
        gridX = 88, gridY = 58,
        stationPrimary = "KMKE",
    )

    private fun unconfiguredSnapshot() = SettingsRepository.Snapshot()

    private fun fakeAlertsDto(vararg ids: String) = AlertsDto(
        features = ids.map { id ->
            AlertFeatureDto(
                id = id,
                properties = AlertProperties(
                    id = id,
                    event = "Severe Thunderstorm Warning",
                    severity = "Severe",
                    headline = "TEST",
                    description = "test alert",
                    instruction = null,
                    sent = "2026-05-19T19:00:00Z",
                    effective = "2026-05-19T19:00:00Z",
                    expires = "2026-05-19T20:00:00Z",
                    areaDesc = "Milwaukee County",
                    parameters = null,
                ),
            )
        }
    )

    @Test
    fun `skips cleanly when isConfigured is false`() = runBlocking {
        coEvery { settings.snapshot() } returns unconfiguredSnapshot()

        val result = buildWorker().doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { nws.activeAlerts(any(), any()) }
    }

    @Test
    fun `retries on IOException from activeAlerts`() = runBlocking {
        coEvery { settings.snapshot() } returns configuredSnapshot()
        coEvery { nws.activeAlerts(any(), any()) } throws IOException("network down")

        val result = buildWorker().doWork()
        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test
    fun `persists current alert ids to LastSeenAlertRepository`() = runBlocking {
        coEvery { settings.snapshot() } returns configuredSnapshot()
        coEvery { nws.activeAlerts(any(), any()) } returns fakeAlertsDto("alert-1", "alert-2")

        buildWorker().doWork()

        coVerify { lastSeen.write(setOf("alert-1", "alert-2")) }
    }

    @Test
    fun `same alert reissued does not appear in newAlerts`() = runBlocking {
        coEvery { settings.snapshot() } returns configuredSnapshot()
        coEvery { lastSeen.read() } returns setOf("alert-1")
        coEvery { nws.activeAlerts(any(), any()) } returns fakeAlertsDto("alert-1")

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        // No-notify is the contract; downstream Phase E test verifies dispatcher not called.
    }

    @Test
    fun `acknowledged alerts are not in newAlerts`() = runBlocking {
        coEvery { settings.snapshot() } returns configuredSnapshot()
        coEvery { ack.snapshot() } returns setOf("alert-1")
        coEvery { nws.activeAlerts(any(), any()) } returns fakeAlertsDto("alert-1")

        val result = buildWorker().doWork()
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun `empty alerts list still writes empty set to LastSeenAlertRepository`() = runBlocking {
        coEvery { settings.snapshot() } returns configuredSnapshot()
        coEvery { nws.activeAlerts(any(), any()) } returns fakeAlertsDto()

        buildWorker().doWork()

        coVerify { lastSeen.write(emptySet()) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "com.skyframe.background.AlertCheckWorkerTest" --no-daemon
```

Expected: compile error (AlertCheckWorker unresolved).

- [ ] **Step 3: Implement AlertCheckWorker.kt (baseline — no notifications)**

Create `app/src/main/kotlin/com/skyframe/background/AlertCheckWorker.kt`:

```kotlin
package com.skyframe.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.skyframe.data.acknowledgments.AlertAcknowledgmentRepository
import com.skyframe.data.alerts.history.AlertDiff
import com.skyframe.data.alerts.history.LastSeenAlertRepository
import com.skyframe.data.nws.AlertNormalizer
import com.skyframe.data.nws.NwsClient
import com.skyframe.data.settings.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException

/**
 * Background poll worker. Runs on the periodic schedule registered by
 * AlertCheckScheduler. Steps:
 *   1. Read config; skip cleanly if !isConfigured (user still in onboarding).
 *   2. Fetch /alerts/active. IOException -> Result.retry() (WorkManager
 *      applies exponential backoff).
 *   3. Classify via AlertNormalizer.
 *   4. Diff against LastSeenAlertRepository + AlertAcknowledgmentRepository.
 *   5. (Phase E) Fire notifications for new alerts.
 *   6. Overwrite LastSeenAlertRepository with current IDs.
 *   7. (Phase F) Schedule EscalationWorker if top-tier present.
 *
 * Phase D ships steps 1-4 + 6 only - notifications and escalation land in
 * later phases. Worker is fully testable end-to-end before any
 * notification side-effects are added.
 */
@HiltWorker
class AlertCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val nws: NwsClient,
    private val settings: SettingsRepository,
    private val lastSeen: LastSeenAlertRepository,
    private val acknowledgments: AlertAcknowledgmentRepository,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val cfg = settings.snapshot()
        if (!cfg.isConfigured) return Result.success()

        val alertsDto = try {
            nws.activeAlerts(cfg.lat, cfg.lon)
        } catch (e: IOException) {
            return Result.retry()
        } catch (e: Exception) {
            // Non-network exceptions are programming/parse errors - retrying
            // won't help. Log and succeed so we don't loop.
            return Result.success()
        }

        val classified = AlertNormalizer.normalize(alertsDto)
        val seen = lastSeen.read()
        val ack = acknowledgments.snapshot()

        @Suppress("UNUSED_VARIABLE") // Phase E consumes this
        val newAlerts = AlertDiff.diff(classified, seen, ack)
        // notificationDispatcher.notify(...) lands in Phase E

        lastSeen.write(classified.map { it.id }.toSet())
        // EscalationWorker.enqueue(...) lands in Phase F
        return Result.success()
    }
}
```

- [ ] **Step 4: Run tests to verify pass**

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "com.skyframe.background.AlertCheckWorkerTest" --tests "com.skyframe.background.AlertCheckSchedulerTest" --no-daemon
```

Expected: 6 AlertCheckWorker tests pass + 2 AlertCheckScheduler tests pass = 8 total.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/background/AlertCheckWorker.kt app/src/test/kotlin/com/skyframe/background/AlertCheckWorkerTest.kt
git commit -m "$(@'
feat(background): AlertCheckWorker baseline (no notifications yet)

@HiltWorker CoroutineWorker. Steps: read config, skip if not
configured, fetch /alerts/active (IOException -> retry, other
exceptions -> success to avoid infinite loop), classify via
AlertNormalizer, diff against LastSeenAlertRepository +
AlertAcknowledgmentRepository, persist current IDs.

Notification firing lands in Phase E.
EscalationWorker scheduling lands in Phase F.

6 unit tests via TestListenableWorkerBuilder + Robolectric: skip when
not configured, retry on IOException, persist IDs, dedup reissued
alerts, skip acknowledged, persist empty set when no alerts.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task D.3: Wire schedulePeriodic into SkyFrameApp.onCreate

**Files:**
- Modify: `app/src/main/kotlin/com/skyframe/SkyFrameApp.kt`

- [ ] **Step 1: Add the scheduler call**

In `app/src/main/kotlin/com/skyframe/SkyFrameApp.kt`, change:

```kotlin
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        // AlertCheckScheduler.schedulePeriodic(this) wires in Phase D
    }
```

to:

```kotlin
    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)
        AlertCheckScheduler.schedulePeriodic(this)
    }
```

And add the import:

```kotlin
import com.skyframe.background.AlertCheckScheduler
```

- [ ] **Step 2: Verify build**

```powershell
./gradlew.bat :app:assembleDebug --no-daemon 2>&1 | Select-Object -Last 4
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/SkyFrameApp.kt
git commit -m "$(@'
feat(background): SkyFrameApp.onCreate schedules AlertCheckWorker

Idempotent registration (KEEP policy inside AlertCheckScheduler) -
safe to call on every app process start.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

## Phase E — NotificationDispatcher + DismissReceiver + MainActivity Deep-Link

This phase makes the worker actually fire notifications. Three pieces: the builder/dispatcher, the dismiss broadcast receiver, and MainActivity's handling of the tap deep-link extra. No new tests — these are thin wrappers around Android's notification + intent APIs; manual smoke test verifies.

### Task E.1: NotificationDispatcher (channel routing + builder)

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/notifications/NotificationDispatcher.kt`

- [ ] **Step 1: Write NotificationDispatcher.kt**

Create `app/src/main/kotlin/com/skyframe/notifications/NotificationDispatcher.kt`:

```kotlin
package com.skyframe.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.skyframe.MainActivity
import com.skyframe.R
import com.skyframe.domain.Alert
import com.skyframe.domain.AlertTier
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Builds + posts a single Alert as a system notification, routed to the
 * channel implied by alert.tier.rank. Tap PendingIntent launches MainActivity
 * with EXTRA_ALERT_ID; DISMISS action PendingIntent broadcasts to
 * DismissReceiver.
 *
 * No unit test - thin wrapper around NotificationCompat.Builder +
 * NotificationManagerCompat. Manual smoke test verifies tier->channel
 * routing, tap behavior, and DISMISS action.
 *
 * Life-safety notifications (ranks 1-4) set setFullScreenIntent targeting
 * FullScreenAlertActivity - that part wires in Phase G.
 */
@Singleton
class NotificationDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun notify(alert: Alert) {
        val channelId = channelFor(alert.tier)
        val notificationId = NotificationIds.forAlertId(alert.id)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)  // TODO Phase J: dedicated monochrome icon
            .setColor(alert.tier.baseColor.toInt())
            .setContentTitle("⚠ ${alert.event.uppercase()}")
            .setContentText(formatBody(alert))
            .setStyle(NotificationCompat.BigTextStyle().bigText(longBody(alert)))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(tapIntent(alert.id, notificationId))
            .addAction(
                NotificationCompat.Action.Builder(
                    /* icon = */ 0,
                    /* title = */ "DISMISS",
                    /* intent = */ dismissIntent(alert.id, notificationId),
                ).build(),
            )

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    private fun channelFor(tier: AlertTier): String = when (tier.rank) {
        in 1..4    -> NotificationChannels.LIFE_SAFETY
        5          -> NotificationChannels.SEVERE_WEATHER
        in 6..8    -> NotificationChannels.WATCHES
        in 9..13   -> NotificationChannels.ADVISORIES
        else       -> NotificationChannels.ADVISORIES  // unreachable; defensive
    }

    private fun formatBody(alert: Alert): String {
        val tz = TimeZone.currentSystemDefault()
        val expires = alert.expires.toLocalDateTime(tz)
        val hh = expires.hour.toString().padStart(2, '0')
        val mm = expires.minute.toString().padStart(2, '0')
        val area = if (alert.areaDesc.isNotBlank()) " · ${alert.areaDesc}" else ""
        return "Until $hh:$mm$area"
    }

    private fun longBody(alert: Alert): String {
        val short = formatBody(alert)
        val firstLine = alert.description.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            ?: alert.headline
        return "$short\n$firstLine"
    }

    private fun tapIntent(alertId: String, notificationId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(NotificationExtras.ALERT_ID, alertId)
            putExtra(NotificationExtras.NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun dismissIntent(alertId: String, notificationId: Int): PendingIntent {
        val intent = Intent(context, DismissReceiver::class.java).apply {
            putExtra(NotificationExtras.ALERT_ID, alertId)
            putExtra(NotificationExtras.NOTIFICATION_ID, notificationId)
        }
        return PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
```

- [ ] **Step 2: Verify compile fails (DismissReceiver doesn't exist)**

```powershell
./gradlew.bat :app:compileDebugKotlin --no-daemon 2>&1 | Select-String -Pattern "unresolved" | Select-Object -First 3
```

Expected: error on `DismissReceiver::class.java`. Continue to E.2.

---

### Task E.2: DismissReceiver

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/notifications/DismissReceiver.kt`

- [ ] **Step 1: Write DismissReceiver.kt**

Create `app/src/main/kotlin/com/skyframe/notifications/DismissReceiver.kt`:

```kotlin
package com.skyframe.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.skyframe.data.acknowledgments.AlertAcknowledgmentRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Fires when the user taps DISMISS on a SkyFrame notification. Marks the
 * alert as acknowledged in AlertAcknowledgmentRepository (prevents
 * re-notification on subsequent polls) and clears the system notification.
 *
 * Bidirectional sync: in-app dismissal via DashboardViewModel.dismissAlert
 * does the equivalent in reverse (see Phase H).
 */
@AndroidEntryPoint
class DismissReceiver : BroadcastReceiver() {

    @Inject lateinit var acknowledgments: AlertAcknowledgmentRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val alertId = intent.getStringExtra(NotificationExtras.ALERT_ID) ?: return
        val notificationId = intent.getIntExtra(NotificationExtras.NOTIFICATION_ID, -1)

        scope.launch {
            acknowledgments.dismiss(alertId)
        }
        if (notificationId != -1) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
    }
}
```

- [ ] **Step 2: Verify compile**

```powershell
./gradlew.bat :app:compileDebugKotlin --no-daemon 2>&1 | Select-Object -Last 4
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit dispatcher + receiver together**

```powershell
git add app/src/main/kotlin/com/skyframe/notifications/NotificationDispatcher.kt app/src/main/kotlin/com/skyframe/notifications/DismissReceiver.kt
git commit -m "$(@'
feat(notifications): NotificationDispatcher + DismissReceiver

Dispatcher: builds NotificationCompat.Builder for an Alert, routes to
channel by tier rank (1-4 life_safety, 5 severe_weather, 6-8 watches,
9-13 advisories), tier-colored accent via setColor, BigTextStyle for
expanded body, tap PendingIntent launches MainActivity with ALERT_ID
+ NOTIFICATION_ID extras, DISMISS action PendingIntent broadcasts to
DismissReceiver.

Receiver: marks alert as acknowledged + cancels the system notification.
@AndroidEntryPoint for Hilt injection of AlertAcknowledgmentRepository.

setFullScreenIntent for life_safety wires in Phase G.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task E.3: MainActivity handles EXTRA_ALERT_ID deep-link

**Files:**
- Modify: `app/src/main/kotlin/com/skyframe/MainActivity.kt`
- Modify: `app/src/main/kotlin/com/skyframe/viewmodel/DashboardViewModel.kt` (add a hook for triggering the sheet)

- [ ] **Step 1: Read DashboardViewModel current state**

```powershell
Get-Content "app/src/main/kotlin/com/skyframe/viewmodel/DashboardViewModel.kt"
```

Note the existing sheet-state mechanism (`SheetState` sealed class). The deep-link needs to open `AlertDetailSheet` for a specific alert by ID.

- [ ] **Step 2: Add an openAlertDetail(id) helper to DashboardViewModel**

In `app/src/main/kotlin/com/skyframe/viewmodel/DashboardViewModel.kt`, in the body of the class (next to `dismissAlert`), add:

```kotlin
    /**
     * Called by MainActivity when launched via a notification tap with
     * EXTRA_ALERT_ID. Finds the alert in the current WeatherResponse and
     * opens AlertDetailSheet for it. No-op if the alert isn't in the current
     * snapshot (notification stale; alert already expired).
     */
    fun openAlertDetail(alertId: String) {
        val current = (weatherState.value as? WeatherState.Success)?.response ?: return
        val alert = current.alerts.firstOrNull { it.id == alertId } ?: return
        _sheetState.value = SheetState.AlertDetail(alert)
    }
```

If `_sheetState` is named differently in the file (e.g. `_sheet`), use that name.

- [ ] **Step 3: Update MainActivity to dispatch the intent extra**

In `app/src/main/kotlin/com/skyframe/MainActivity.kt`, add this override after `onCreate`:

```kotlin
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAlertDeepLink(intent)
    }
```

And add this helper at the bottom of the class:

```kotlin
    private fun handleAlertDeepLink(intent: Intent?) {
        val alertId = intent?.getStringExtra(NotificationExtras.ALERT_ID) ?: return
        dashboardViewModel.openAlertDetail(alertId)
    }
```

In `onCreate`, after the existing `enableEdgeToEdge()` line, call:

```kotlin
        handleAlertDeepLink(intent)
```

Add imports at the top of the file:

```kotlin
import android.content.Intent
import com.skyframe.notifications.NotificationExtras
```

- [ ] **Step 4: Wire notify() into AlertCheckWorker**

Now that NotificationDispatcher exists, fire from AlertCheckWorker. In `app/src/main/kotlin/com/skyframe/background/AlertCheckWorker.kt`, change the constructor to also accept `NotificationDispatcher`:

```kotlin
@HiltWorker
class AlertCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val nws: NwsClient,
    private val settings: SettingsRepository,
    private val lastSeen: LastSeenAlertRepository,
    private val acknowledgments: AlertAcknowledgmentRepository,
    private val notificationDispatcher: com.skyframe.notifications.NotificationDispatcher,
) : CoroutineWorker(appContext, workerParams) {
```

And in `doWork()`, replace:

```kotlin
        @Suppress("UNUSED_VARIABLE") // Phase E consumes this
        val newAlerts = AlertDiff.diff(classified, seen, ack)
        // notificationDispatcher.notify(...) lands in Phase E
```

with:

```kotlin
        val newAlerts = AlertDiff.diff(classified, seen, ack)
        newAlerts.forEach { notificationDispatcher.notify(it) }
```

- [ ] **Step 5: Update AlertCheckWorkerTest to inject the dispatcher mock**

In `app/src/test/kotlin/com/skyframe/background/AlertCheckWorkerTest.kt`, add `notificationDispatcher` to the test fixtures.

Near the top of the class, add:

```kotlin
    private lateinit var notificationDispatcher: com.skyframe.notifications.NotificationDispatcher
```

In `setUp()`, after `ack = mockk(...)`, add:

```kotlin
        notificationDispatcher = mockk(relaxed = true)
```

Update `buildWorker()` to pass it:

```kotlin
                ) = AlertCheckWorker(appContext, workerParameters, nws, settings, lastSeen, ack, notificationDispatcher)
```

Add two new tests at the end of the class (before the closing brace):

```kotlin
    @Test
    fun `fires notification only for new alerts (not in lastSeen, not ack)`() = runBlocking {
        coEvery { settings.snapshot() } returns configuredSnapshot()
        coEvery { lastSeen.read() } returns setOf("alert-1")  // alert-1 already seen
        coEvery { nws.activeAlerts(any(), any()) } returns fakeAlertsDto("alert-1", "alert-2")

        buildWorker().doWork()

        // Only alert-2 fires; alert-1 was already in lastSeen
        coVerify(exactly = 1) {
            notificationDispatcher.notify(io.mockk.match { it.id == "alert-2" })
        }
        coVerify(exactly = 0) {
            notificationDispatcher.notify(io.mockk.match { it.id == "alert-1" })
        }
    }

    @Test
    fun `no notifications fire when all alerts are acknowledged`() = runBlocking {
        coEvery { settings.snapshot() } returns configuredSnapshot()
        coEvery { ack.snapshot() } returns setOf("alert-1", "alert-2")
        coEvery { nws.activeAlerts(any(), any()) } returns fakeAlertsDto("alert-1", "alert-2")

        buildWorker().doWork()

        coVerify(exactly = 0) { notificationDispatcher.notify(any()) }
    }
```

- [ ] **Step 6: Run tests**

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "com.skyframe.background.AlertCheckWorkerTest" --no-daemon 2>&1 | Select-Object -Last 8
```

Expected: 8 tests pass (6 original + 2 new).

- [ ] **Step 7: Verify APK build**

```powershell
./gradlew.bat :app:assembleDebug --no-daemon 2>&1 | Select-Object -Last 4
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/MainActivity.kt app/src/main/kotlin/com/skyframe/viewmodel/DashboardViewModel.kt app/src/main/kotlin/com/skyframe/background/AlertCheckWorker.kt app/src/test/kotlin/com/skyframe/background/AlertCheckWorkerTest.kt
git commit -m "$(@'
feat(notifications): MainActivity deep-link + wire worker to dispatcher

MainActivity.onNewIntent + onCreate both call handleAlertDeepLink which
reads EXTRA_ALERT_ID and calls dashboardViewModel.openAlertDetail(id).
openAlertDetail finds the alert in the current WeatherResponse and
opens AlertDetailSheet via SheetState - no-op if alert is stale.

AlertCheckWorker now injects NotificationDispatcher and fires
notify(alert) for each new alert (not in lastSeen, not in ack).

2 new worker tests confirm dispatcher invocation only for genuinely
new alerts and never for acknowledged alerts.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

## Phase F — EscalationWorker (ExpeditedWorkRequest chain for top-tier alerts)

When the periodic worker sees a top-tier active alert (rank 1-4), it enqueues a one-shot `ExpeditedWorkRequest` that re-runs the same logic in ~2 minutes and chains itself again if top-tier is still active. Stops naturally when the top-tier alert clears.

### Task F.1: EscalationWorker + tests

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/background/EscalationWorker.kt`
- Create: `app/src/test/kotlin/com/skyframe/background/EscalationWorkerTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/skyframe/background/EscalationWorkerTest.kt`:

```kotlin
package com.skyframe.background

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.skyframe.data.acknowledgments.AlertAcknowledgmentRepository
import com.skyframe.data.alerts.history.LastSeenAlertRepository
import com.skyframe.data.nws.AlertsDto
import com.skyframe.data.nws.AlertFeatureDto
import com.skyframe.data.nws.AlertProperties
import com.skyframe.data.nws.NwsClient
import com.skyframe.data.settings.SettingsRepository
import com.skyframe.notifications.NotificationDispatcher
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.junit.jupiter.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
class EscalationWorkerTest {

    private lateinit var context: Context
    private lateinit var nws: NwsClient
    private lateinit var settings: SettingsRepository
    private lateinit var lastSeen: LastSeenAlertRepository
    private lateinit var ack: AlertAcknowledgmentRepository
    private lateinit var notificationDispatcher: NotificationDispatcher

    @BeforeEach
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        nws = mockk()
        settings = mockk()
        lastSeen = mockk(relaxed = true)
        ack = mockk()
        notificationDispatcher = mockk(relaxed = true)
        coEvery { ack.snapshot() } returns emptySet()
        coEvery { lastSeen.read() } returns emptySet()
    }

    private fun buildWorker() = TestListenableWorkerBuilder<EscalationWorker>(context)
        .setWorkerFactory(
            object : androidx.work.WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ) = EscalationWorker(appContext, workerParameters, nws, settings, lastSeen, ack, notificationDispatcher)
            }
        )
        .build()

    private fun configuredSnapshot() = SettingsRepository.Snapshot(
        email = "test@example.com",
        lat = 42.8744, lon = -87.8633,
        locationName = "OAK CREEK WI",
        forecastOffice = "MKX",
        gridX = 88, gridY = 58,
        stationPrimary = "KMKE",
    )

    private fun tornadoAlertsDto() = AlertsDto(
        features = listOf(
            AlertFeatureDto(
                id = "tornado-1",
                properties = AlertProperties(
                    id = "tornado-1",
                    event = "Tornado Warning",
                    severity = "Extreme",
                    headline = "TEST",
                    description = "test tornado",
                    instruction = null,
                    sent = "2026-05-19T19:00:00Z",
                    effective = "2026-05-19T19:00:00Z",
                    expires = "2026-05-19T20:00:00Z",
                    areaDesc = "Milwaukee County",
                    parameters = null,
                ),
            )
        )
    )

    private fun lowTierAlertsDto() = AlertsDto(
        features = listOf(
            AlertFeatureDto(
                id = "advisory-1",
                properties = AlertProperties(
                    id = "advisory-1",
                    event = "Wind Advisory",
                    severity = "Minor",
                    headline = "TEST",
                    description = "test advisory",
                    instruction = null,
                    sent = "2026-05-19T19:00:00Z",
                    effective = "2026-05-19T19:00:00Z",
                    expires = "2026-05-19T20:00:00Z",
                    areaDesc = "Milwaukee County",
                    parameters = null,
                ),
            )
        )
    )

    @Test
    fun `chains another escalation when top-tier still active`() = runBlocking {
        coEvery { settings.snapshot() } returns configuredSnapshot()
        coEvery { nws.activeAlerts(any(), any()) } returns tornadoAlertsDto()

        val result = buildWorker().doWork()
        assertEquals(ListenableWorker.Result.success(), result)

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(EscalationWorker.UNIQUE_WORK_NAME)
            .get()
        assertEquals(1, infos.size, "expected exactly one chained EscalationWorker enqueued")
        assertTrue(infos[0].state == WorkInfo.State.ENQUEUED || infos[0].state == WorkInfo.State.RUNNING)
    }

    @Test
    fun `does not chain when top-tier clears`() = runBlocking {
        coEvery { settings.snapshot() } returns configuredSnapshot()
        coEvery { nws.activeAlerts(any(), any()) } returns lowTierAlertsDto()

        buildWorker().doWork()

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(EscalationWorker.UNIQUE_WORK_NAME)
            .get()
        // REPLACE policy + no enqueue path = empty info list
        assertEquals(0, infos.size)
    }

    @Test
    fun `skips cleanly when not configured`() = runBlocking {
        coEvery { settings.snapshot() } returns SettingsRepository.Snapshot()

        val result = buildWorker().doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        io.mockk.coVerify(exactly = 0) { nws.activeAlerts(any(), any()) }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "com.skyframe.background.EscalationWorkerTest" --no-daemon
```

Expected: compile error (EscalationWorker unresolved).

- [ ] **Step 3: Implement EscalationWorker.kt**

Create `app/src/main/kotlin/com/skyframe/background/EscalationWorker.kt`:

```kotlin
package com.skyframe.background

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.skyframe.data.acknowledgments.AlertAcknowledgmentRepository
import com.skyframe.data.alerts.history.AlertDiff
import com.skyframe.data.alerts.history.LastSeenAlertRepository
import com.skyframe.data.nws.AlertNormalizer
import com.skyframe.data.nws.NwsClient
import com.skyframe.data.settings.SettingsRepository
import com.skyframe.notifications.NotificationDispatcher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * One-shot ExpeditedWorkRequest chained from AlertCheckWorker (or itself)
 * when a top-tier active alert (rank 1-4) is present.
 *
 * Re-runs the same fetch/diff/notify logic as AlertCheckWorker, then
 * either:
 *   - top-tier still active -> chain another EscalationWorker via
 *     OneTimeWorkRequestBuilder with ~2-min initial delay
 *   - top-tier cleared -> stop (next 15-min periodic resumes baseline)
 *
 * Uses OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST so quota
 * exhaustion degrades gracefully to next periodic - it doesn't lose work.
 */
@HiltWorker
class EscalationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val nws: NwsClient,
    private val settings: SettingsRepository,
    private val lastSeen: LastSeenAlertRepository,
    private val acknowledgments: AlertAcknowledgmentRepository,
    private val notificationDispatcher: NotificationDispatcher,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val cfg = settings.snapshot()
        if (!cfg.isConfigured) return Result.success()

        val alertsDto = try {
            nws.activeAlerts(cfg.lat, cfg.lon)
        } catch (e: IOException) {
            return Result.retry()
        } catch (e: Exception) {
            return Result.success()
        }

        val classified = AlertNormalizer.normalize(alertsDto)
        val seen = lastSeen.read()
        val ack = acknowledgments.snapshot()
        val newAlerts = AlertDiff.diff(classified, seen, ack)
        newAlerts.forEach { notificationDispatcher.notify(it) }
        lastSeen.write(classified.map { it.id }.toSet())

        val hasTopTier = classified.any { it.tier.rank in 1..4 }
        if (hasTopTier) {
            enqueueNext(applicationContext)
        }
        return Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "alert_check_escalation"

        fun enqueueNext(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<EscalationWorker>()
                .setInitialDelay(2, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
```

- [ ] **Step 4: Wire enqueueNext into AlertCheckWorker**

In `app/src/main/kotlin/com/skyframe/background/AlertCheckWorker.kt`, replace the comment near the end of `doWork`:

```kotlin
        lastSeen.write(classified.map { it.id }.toSet())
        // EscalationWorker.enqueue(...) lands in Phase F
        return Result.success()
```

with:

```kotlin
        lastSeen.write(classified.map { it.id }.toSet())
        if (classified.any { it.tier.rank in 1..4 }) {
            EscalationWorker.enqueueNext(applicationContext)
        }
        return Result.success()
```

- [ ] **Step 5: Run tests**

```powershell
./gradlew.bat :app:testDebugUnitTest --tests "com.skyframe.background.EscalationWorkerTest" --no-daemon 2>&1 | Select-Object -Last 8
```

Expected: 3 tests pass.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/background/EscalationWorker.kt app/src/main/kotlin/com/skyframe/background/AlertCheckWorker.kt app/src/test/kotlin/com/skyframe/background/EscalationWorkerTest.kt
git commit -m "$(@'
feat(background): EscalationWorker ExpeditedWorkRequest chain

One-shot OneTimeWorkRequest with 2-min initial delay, marked expedited.
When AlertCheckWorker (or EscalationWorker itself) sees a top-tier
active alert (rank 1-4), it enqueues a chained EscalationWorker. The
chain stops naturally when top-tier clears - next 15-min periodic
resumes baseline cadence.

OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST degrades gracefully
when Android's expedited quota is exhausted (mostly Android 12+
foreground constraint).

ExistingWorkPolicy.REPLACE so back-to-back escalations don't pile up -
only one outstanding at a time.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

## Phase G — FullScreenAlertActivity (life-safety only)

Separate `Activity` with `setShowWhenLocked` + `setTurnScreenOn`. Wired into NotificationDispatcher via `setFullScreenIntent` for the life_safety channel.

### Task G.1: FullScreenAlertActivity stub + Compose UI

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/ui/alert/FullScreenAlertActivity.kt`

No tests — hand-verified per SMOKE_TEST.md.

- [ ] **Step 1: Create the package directory**

```powershell
New-Item -ItemType Directory -Force -Path "app/src/main/kotlin/com/skyframe/ui/alert" | Out-Null
"ok"
```

- [ ] **Step 2: Write FullScreenAlertActivity.kt**

Create `app/src/main/kotlin/com/skyframe/ui/alert/FullScreenAlertActivity.kt`:

```kotlin
package com.skyframe.ui.alert

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import com.skyframe.MainActivity
import com.skyframe.data.acknowledgments.AlertAcknowledgmentRepository
import com.skyframe.notifications.NotificationExtras
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Launched by NotificationDispatcher's setFullScreenIntent for life_safety
 * channel notifications. Shows the alert prominently on the lock screen.
 * setShowWhenLocked + setTurnScreenOn are declared in AndroidManifest so
 * the system wakes the screen even when locked.
 *
 * Renders edge-to-edge with tier-color background. Two CTAs: VIEW DETAILS
 * launches MainActivity routed to AlertDetailSheet; DISMISS acknowledges
 * the alert + cancels the notification + finishes the activity.
 *
 * Reads minimal data from intent extras to avoid touching the WeatherResponse
 * (which may be stale by the time the user sees the lock-screen takeover).
 * VIEW DETAILS hands off to MainActivity which resolves the full alert.
 */
@AndroidEntryPoint
class FullScreenAlertActivity : ComponentActivity() {

    @Inject lateinit var acknowledgments: AlertAcknowledgmentRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Force-screen-on for Android 8.1+ when manifest attributes aren't enough.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            km?.requestDismissKeyguard(this, null)
        }

        val alertId = intent.getStringExtra(NotificationExtras.ALERT_ID).orEmpty()
        val notificationId = intent.getIntExtra(NotificationExtras.NOTIFICATION_ID, -1)
        val event = intent.getStringExtra(EXTRA_EVENT).orEmpty()
        val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
        val tierColor = intent.getLongExtra(EXTRA_TIER_COLOR, 0xFFFF4444L)

        setContent {
            FullScreenAlertContent(
                event = event,
                body = body,
                tierColor = Color(tierColor.toULong()),
                onViewDetails = {
                    startActivity(
                        Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra(NotificationExtras.ALERT_ID, alertId)
                        },
                    )
                    finish()
                },
                onDismiss = {
                    lifecycleScope.launch {
                        acknowledgments.dismiss(alertId)
                    }
                    if (notificationId != -1) {
                        NotificationManagerCompat.from(this).cancel(notificationId)
                    }
                    finish()
                },
            )
        }
    }

    companion object {
        const val EXTRA_EVENT      = "com.skyframe.alert.EVENT"
        const val EXTRA_BODY       = "com.skyframe.alert.BODY"
        const val EXTRA_TIER_COLOR = "com.skyframe.alert.TIER_COLOR"
    }
}

@Composable
private fun FullScreenAlertContent(
    event: String,
    body: String,
    tierColor: Color,
    onViewDetails: () -> Unit,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HudColors.BackgroundBase),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(tierColor),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "⚠",
                color = tierColor,
                style = HudType.titleBar.copy(fontSize = androidx.compose.ui.unit.TextUnit.Unspecified),
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = event.uppercase(),
                color = tierColor,
                style = HudType.titleBar,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = body,
                color = HudColors.Foreground,
                style = HudType.bodyMono,
            )
            Spacer(Modifier.height(48.dp))
            Text(
                text = "[ VIEW DETAILS ]",
                color = tierColor,
                style = HudType.titleBar,
                modifier = Modifier
                    .border(BorderStroke(1.dp, tierColor))
                    .clickable(onClick = onViewDetails)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "[ DISMISS ]",
                color = HudColors.ForegroundDim,
                style = HudType.titleBar,
                modifier = Modifier
                    .border(BorderStroke(1.dp, HudColors.ForegroundDim))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
                    .align(Alignment.CenterHorizontally),
            )
        }
    }
}
```

- [ ] **Step 3: Wire setFullScreenIntent into NotificationDispatcher**

In `app/src/main/kotlin/com/skyframe/notifications/NotificationDispatcher.kt`, add this helper to the class:

```kotlin
    private fun fullScreenIntent(alert: Alert, notificationId: Int): PendingIntent {
        val intent = Intent(context, com.skyframe.ui.alert.FullScreenAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(NotificationExtras.ALERT_ID, alert.id)
            putExtra(NotificationExtras.NOTIFICATION_ID, notificationId)
            putExtra(com.skyframe.ui.alert.FullScreenAlertActivity.EXTRA_EVENT, alert.event)
            putExtra(com.skyframe.ui.alert.FullScreenAlertActivity.EXTRA_BODY, longBody(alert))
            putExtra(com.skyframe.ui.alert.FullScreenAlertActivity.EXTRA_TIER_COLOR, alert.tier.baseColor)
        }
        return PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
```

In the same file, in the `notify(alert)` method, after the `addAction(...)` call on the builder, add:

```kotlin
        if (channelId == NotificationChannels.LIFE_SAFETY) {
            builder.setFullScreenIntent(fullScreenIntent(alert, notificationId), /* highPriority = */ true)
        }
```

- [ ] **Step 4: Verify APK build**

```powershell
./gradlew.bat :app:assembleDebug --no-daemon 2>&1 | Select-Object -Last 4
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/ui/alert/FullScreenAlertActivity.kt app/src/main/kotlin/com/skyframe/notifications/NotificationDispatcher.kt
git commit -m "$(@'
feat(notifications): FullScreenAlertActivity + setFullScreenIntent wiring

Separate ComponentActivity launched via the life_safety channel's
fullScreenIntent. setShowWhenLocked(true) + setTurnScreenOn(true) +
requestDismissKeyguard wake the screen even when locked.

Compose UI: tier-color top bar, large warning glyph + event name in
tier color, body text, two CTAs (VIEW DETAILS hands off to
MainActivity routed to AlertDetailSheet; DISMISS records ack + cancels
notification). Reads minimal data from intent extras - doesn't depend
on WeatherResponse being current.

NotificationDispatcher conditionally sets setFullScreenIntent only for
LIFE_SAFETY channel notifications; other tiers use heads-up only.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

## Phase H — Bidirectional Acknowledgment Sync

In-app banner [×] dismissal should ALSO cancel the system notification, not just record the ack. Symmetric to the DISMISS-receiver flow (which cancels the system notification + records the ack).

### Task H.1: DashboardViewModel.dismissAlert cancels system notification

**Files:**
- Modify: `app/src/main/kotlin/com/skyframe/viewmodel/DashboardViewModel.kt`

No unit tests — the wrapped operations are already tested individually; this is the one-line composition. Manual smoke test verifies.

- [ ] **Step 1: Inject application Context (or NotificationManagerCompat) into DashboardViewModel**

Check the current DashboardViewModel constructor:

```powershell
Get-Content "app/src/main/kotlin/com/skyframe/viewmodel/DashboardViewModel.kt" | Select-String -Pattern "class DashboardViewModel|@Inject constructor" -Context 0,5
```

The cleanest path: inject `@ApplicationContext Context` directly so we can call `NotificationManagerCompat.from(context).cancel(id)`. In `app/src/main/kotlin/com/skyframe/viewmodel/DashboardViewModel.kt`, add to the constructor parameter list:

```kotlin
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
```

- [ ] **Step 2: Update dismissAlert to also cancel the system notification**

Replace the existing `dismissAlert` function with:

```kotlin
    fun dismissAlert(id: String) {
        viewModelScope.launch { acknowledgments.dismiss(id) }
        androidx.core.app.NotificationManagerCompat.from(appContext)
            .cancel(com.skyframe.notifications.NotificationIds.forAlertId(id))
    }
```

- [ ] **Step 3: Verify build**

```powershell
./gradlew.bat :app:assembleDebug --no-daemon 2>&1 | Select-Object -Last 4
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Verify no existing tests break (DashboardViewModelTest, if present, may need the new ctor arg)**

```powershell
./gradlew.bat :app:testDebugUnitTest --no-daemon 2>&1 | Select-Object -Last 6
```

Expected: BUILD SUCCESSFUL. If DashboardViewModelTest fails to compile due to the new constructor param, add `appContext = mockk(relaxed = true)` to the test's VM construction.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/viewmodel/DashboardViewModel.kt
git commit -m "$(@'
feat(viewmodel): in-app banner dismissal cancels system notification

DashboardViewModel.dismissAlert now does both halves of the
bidirectional sync: ackRepo.dismiss(id) (existing) AND
NotificationManagerCompat.cancel(NotificationIds.forAlertId(id)).

Symmetric to DismissReceiver, which already did both directions for
system-side dismissal. Together they ensure the in-app banner and
the system notification shade are never out of sync.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

## Phase I — Permission Cascade Onboarding + Settings Banner

`PermissionScreen` Compose route inserted between SETTINGS and DASHBOARD in the NavHost on first run. SettingsScreen status banner when POST_NOTIFICATIONS is denied after onboarding.

### Task I.1: Add permissionsPromptedAt to SettingsRepository.Snapshot

**Files:**
- Modify: `app/src/main/kotlin/com/skyframe/data/settings/SettingsKeys.kt`
- Modify: `app/src/main/kotlin/com/skyframe/data/settings/SettingsRepository.kt`

- [ ] **Step 1: Add the key**

In `app/src/main/kotlin/com/skyframe/data/settings/SettingsKeys.kt`, add:

```kotlin
import androidx.datastore.preferences.core.longPreferencesKey
```

near the other key imports, then add inside the `object SettingsKeys`:

```kotlin
    val PERMISSIONS_PROMPTED_AT = longPreferencesKey("permissions_prompted_at")
```

- [ ] **Step 2: Add to Snapshot data class**

In `app/src/main/kotlin/com/skyframe/data/settings/SettingsRepository.kt`, in the `data class Snapshot(...)`, add after the existing `updateCheckEnabled` field:

```kotlin
        val permissionsPromptedAt: Long = 0L,
```

In both `flow` and `snapshotInternal` (the two read sites that populate Snapshot from prefs), add:

```kotlin
            permissionsPromptedAt = prefs[SettingsKeys.PERMISSIONS_PROMPTED_AT] ?: 0L,
```

In the `update { ... }` write block, add (next to `prefs[SettingsKeys.UPDATE_CHECK] = ...`):

```kotlin
            prefs[SettingsKeys.PERMISSIONS_PROMPTED_AT] = next.permissionsPromptedAt
```

- [ ] **Step 3: Verify build + tests**

```powershell
./gradlew.bat :app:testDebugUnitTest --no-daemon 2>&1 | Select-Object -Last 4
```

Expected: BUILD SUCCESSFUL. Existing tests that construct Snapshot() without naming all params still work — Kotlin default values cover it.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/data/settings/SettingsKeys.kt app/src/main/kotlin/com/skyframe/data/settings/SettingsRepository.kt
git commit -m "$(@'
feat(settings): persist permissionsPromptedAt flag

Long timestamp written once after the user completes the post-onboarding
permission cascade. Drives MainActivity's start-destination decision:
configured + permissionsPromptedAt == 0L -> PERMISSIONS route; else
DASHBOARD.

Default 0L means existing v0.3.0 installs will see PermissionScreen
once on first v0.4.0 launch, which is the desired behavior.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task I.2: PermissionScreen Compose route

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/ui/screens/PermissionScreen.kt`

- [ ] **Step 1: Write PermissionScreen.kt**

Create `app/src/main/kotlin/com/skyframe/ui/screens/PermissionScreen.kt`:

```kotlin
package com.skyframe.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType
import com.skyframe.theme.LocalHudAccent

/**
 * Post-onboarding permission cascade. Reached only on first run, after
 * SettingsScreen SAVE succeeds. Three rows, each independently tappable:
 *
 *   POST_NOTIFICATIONS (Android 13+) - permission dialog
 *   USE_FULL_SCREEN_INTENT (Android 14+) - system intent
 *   Battery optimization whitelist - system intent
 *
 * Each row shows current status + rationale. The CONTINUE button is
 * always enabled - permissions are optional from the app's perspective;
 * the user can revisit via SettingsScreen banner if they decline now.
 */
@Composable
fun PermissionScreen(onContinue: () -> Unit) {
    val accent = LocalHudAccent.current.accent
    val context = LocalContext.current
    var refreshTick by remember { mutableStateOf(0) }
    // refreshTick reruns the @Composable to re-read permission state after
    // the system dialogs return.

    BackHandler { /* swallow - force-completion */ }

    val notificationGranted = remember(refreshTick) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) true
        else ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    val fullScreenGranted = remember(refreshTick) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) true
        else (context.getSystemService(android.app.NotificationManager::class.java)
            ?.canUseFullScreenIntent() == true)
    }

    val batteryWhitelisted = remember(refreshTick) {
        val pm = context.getSystemService(PowerManager::class.java)
        pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    val notificationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { refreshTick++ }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HudColors.BackgroundBase),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(HudColors.BackgroundDeep)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "TERMINAL // PERMISSIONS",
                color = accent,
                style = HudType.titleBar,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = "Severe weather alerts require these permissions. " +
                    "You can change them later in Settings.",
                color = HudColors.ForegroundDim,
                style = HudType.bodyMono,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            PermissionRow(
                title = "NOTIFICATIONS",
                rationale = "Required for severe weather alerts when the app is closed.",
                granted = notificationGranted,
                accent = accent,
                onTap = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
            )
            Spacer(Modifier.height(12.dp))

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                PermissionRow(
                    title = "FULL-SCREEN INTENT",
                    rationale = "Lets life-threatening alerts show on your lock screen.",
                    granted = fullScreenGranted,
                    accent = accent,
                    onTap = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        ).apply { data = Uri.fromParts("package", context.packageName, null) }
                        context.startActivity(intent)
                    },
                )
                Spacer(Modifier.height(12.dp))
            }

            PermissionRow(
                title = "BATTERY OPTIMIZATION",
                rationale = "Improves background reliability on aggressive OEMs (Samsung, Xiaomi).",
                granted = batteryWhitelisted,
                accent = accent,
                onTap = {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                },
            )

            Spacer(Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "[ CONTINUE ]",
                    color = accent,
                    style = HudType.titleBar,
                    modifier = Modifier
                        .border(BorderStroke(1.dp, accent))
                        .clickable(onClick = onContinue)
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    rationale: String,
    granted: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onTap: () -> Unit,
) {
    val borderColor = if (granted) accent else HudColors.ForegroundDim
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, borderColor))
            .clickable(enabled = !granted, onClick = onTap)
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (granted) "[✓] $title" else "[ ] $title",
                color = if (granted) accent else HudColors.Foreground,
                style = HudType.titleBar,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = rationale,
            color = HudColors.ForegroundDim,
            style = HudType.metaLabel,
        )
    }
}
```

- [ ] **Step 2: Verify compile**

```powershell
./gradlew.bat :app:compileDebugKotlin --no-daemon 2>&1 | Select-Object -Last 4
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/ui/screens/PermissionScreen.kt
git commit -m "$(@'
feat(onboarding): PermissionScreen Compose route

Three permission rows: POST_NOTIFICATIONS (Android 13+),
USE_FULL_SCREEN_INTENT (Android 14+, hidden on older), battery
optimization whitelist. Each row shows current status + rationale +
tap-to-request behavior.

CONTINUE button always enabled - permissions optional from app's
view; system enforces functional consequences (silent notifications,
deferred work during Doze, etc).

BackHandler swallows system back to enforce force-completion mode -
matches Plan 3's SettingsScreen onboarding posture.

NavHost wiring + start-destination integration land in I.3.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task I.3: NavHost wiring + MainActivity start-destination logic

**Files:**
- Modify: `app/src/main/kotlin/com/skyframe/ui/nav/NavRoutes.kt`
- Modify: `app/src/main/kotlin/com/skyframe/ui/nav/SkyFrameNavHost.kt`
- Modify: `app/src/main/kotlin/com/skyframe/MainActivity.kt`

- [ ] **Step 1: Add PERMISSIONS route**

In `app/src/main/kotlin/com/skyframe/ui/nav/NavRoutes.kt`, add:

```kotlin
    const val PERMISSIONS = "permissions"
```

inside the `object NavRoutes`.

- [ ] **Step 2: Add PERMISSIONS destination to SkyFrameNavHost**

In `app/src/main/kotlin/com/skyframe/ui/nav/SkyFrameNavHost.kt`, change the `SettingsScreen.onSaved` lambda from:

```kotlin
                onSaved = {
                    if (!navController.popBackStack(NavRoutes.DASHBOARD, inclusive = false)) {
                        navController.navigate(NavRoutes.DASHBOARD) {
                            popUpTo(NavRoutes.SETTINGS) { inclusive = true }
                        }
                    }
                },
```

to:

```kotlin
                onSaved = {
                    if (!navController.popBackStack(NavRoutes.DASHBOARD, inclusive = false)) {
                        // First-run: settings just saved, now go to PermissionScreen
                        // (not directly to dashboard).
                        navController.navigate(NavRoutes.PERMISSIONS) {
                            popUpTo(NavRoutes.SETTINGS) { inclusive = true }
                        }
                    }
                },
```

Then add a new composable inside the NavHost block:

```kotlin
        composable(NavRoutes.PERMISSIONS) {
            com.skyframe.ui.screens.PermissionScreen(
                onContinue = {
                    onPermissionsCompleted()
                    navController.navigate(NavRoutes.DASHBOARD) {
                        popUpTo(NavRoutes.PERMISSIONS) { inclusive = true }
                    }
                },
            )
        }
```

Add `onPermissionsCompleted: () -> Unit` to the `SkyFrameNavHost` parameter list:

```kotlin
fun SkyFrameNavHost(
    startDestination: String,
    dashboardViewModel: DashboardViewModel,
    settingsViewModel: SettingsViewModel,
    nwsClient: NwsClient,
    onPermissionsCompleted: () -> Unit,
    navController: NavHostController = rememberNavController(),
)
```

- [ ] **Step 3: Update MainActivity start-destination + pass onPermissionsCompleted callback**

In `app/src/main/kotlin/com/skyframe/MainActivity.kt`, replace the start-destination computation:

```kotlin
        val startDestination = if (runBlocking { settingsRepository.snapshot().isConfigured }) {
            NavRoutes.DASHBOARD
        } else {
            NavRoutes.SETTINGS
        }
```

with:

```kotlin
        val startDestination = runBlocking {
            val snap = settingsRepository.snapshot()
            when {
                !snap.isConfigured              -> NavRoutes.SETTINGS
                snap.permissionsPromptedAt == 0L -> NavRoutes.PERMISSIONS
                else                            -> NavRoutes.DASHBOARD
            }
        }
```

In the `setContent { HudTheme { SkyFrameNavHost(... ) } }` block, pass the new callback:

```kotlin
                SkyFrameNavHost(
                    startDestination = startDestination,
                    dashboardViewModel = dashboardViewModel,
                    settingsViewModel = settingsViewModel,
                    nwsClient = nwsClient,
                    onPermissionsCompleted = {
                        lifecycleScope.launch {
                            settingsRepository.update { it.copy(permissionsPromptedAt = System.currentTimeMillis()) }
                        }
                    },
                )
```

- [ ] **Step 4: Verify build**

```powershell
./gradlew.bat :app:assembleDebug --no-daemon 2>&1 | Select-Object -Last 4
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/ui/nav/NavRoutes.kt app/src/main/kotlin/com/skyframe/ui/nav/SkyFrameNavHost.kt app/src/main/kotlin/com/skyframe/MainActivity.kt
git commit -m "$(@'
feat(onboarding): wire PermissionScreen into NavHost + start-destination

NavRoutes adds PERMISSIONS. SkyFrameNavHost composes PermissionScreen
for that destination; SettingsScreen.onSaved now navigates to
PERMISSIONS on first run (not directly to DASHBOARD). PermissionScreen
.onContinue records permissionsPromptedAt and navigates to DASHBOARD.

MainActivity.onCreate computes start destination from a three-way
when: !isConfigured -> SETTINGS, permissionsPromptedAt == 0L ->
PERMISSIONS, else -> DASHBOARD. v0.3.0 users upgrading to v0.4.0 will
see PermissionScreen once on first launch (their permissionsPromptedAt
defaults to 0L).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task I.4: SettingsScreen denied-permission banner

**Files:**
- Modify: `app/src/main/kotlin/com/skyframe/ui/screens/SettingsScreen.kt`

- [ ] **Step 1: Add banner Composable + state hook**

In `app/src/main/kotlin/com/skyframe/ui/screens/SettingsScreen.kt`, at the top of the file, add the imports:

```kotlin
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
```

In the `SettingsScreen` Composable, after the existing `val state by viewModel.uiState.collectAsState()` line, add:

```kotlin
    var notificationsGranted by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        notificationsGranted = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) true
        else ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
```

In the title-bar Row's parent Column (the outer Column with `.background(HudColors.BackgroundBase)`), immediately after the title-bar Row closing brace, add:

```kotlin
        if (!notificationsGranted) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(androidx.compose.ui.graphics.Color(0xFF665522))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "SEVERE WEATHER ALERTS DISABLED",
                    color = androidx.compose.ui.graphics.Color(0xFFFFDD33),
                    style = HudType.titleBar,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "[ GRANT ]",
                    color = androidx.compose.ui.graphics.Color(0xFFFFDD33),
                    style = HudType.titleBar,
                    modifier = Modifier
                        .border(
                            BorderStroke(1.dp, androidx.compose.ui.graphics.Color(0xFFFFDD33)),
                        )
                        .clickable {
                            val intent = android.content.Intent(
                                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            ).apply {
                                data = android.net.Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
```

- [ ] **Step 2: Verify build**

```powershell
./gradlew.bat :app:assembleDebug --no-daemon 2>&1 | Select-Object -Last 4
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/ui/screens/SettingsScreen.kt
git commit -m "$(@'
feat(settings): banner when POST_NOTIFICATIONS denied

Yellow banner at the top of SettingsScreen, only when running on
Android 13+ AND the user has denied/revoked POST_NOTIFICATIONS.
SEVERE WEATHER ALERTS DISABLED label + [GRANT] button deep-linking
to ACTION_APPLICATION_DETAILS_SETTINGS.

State checked via ContextCompat.checkSelfPermission inside a
LaunchedEffect so re-entering SettingsScreen after granting in
system settings will hide the banner.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

## Phase J — Documentation + v0.4.0 Tag

### Task J.1: SMOKE_TEST additions for background polling + permissions + full-screen intent

**Files:**
- Modify: `docs/SMOKE_TEST.md`

- [ ] **Step 1: Find the Regression heading**

```powershell
Get-Content "docs/SMOKE_TEST.md" | Select-String -Pattern "^## Regression" -Context 0,4
```

- [ ] **Step 2: Insert Plan 4 verification section before Regression**

In `docs/SMOKE_TEST.md`, locate `## Regression` and insert immediately before it:

```markdown

## Background alerts + notifications (v0.4.0 / Plan 4)

### Notification channels created

- [ ] Install + launch the app once.
- [ ] In device settings → Apps → SkyFrame → Notifications, confirm 5 channels appear in 2 groups:
  - Weather alerts: Life-safety alerts, Severe weather, Watches, Advisories
  - App updates: App updates

### Permission cascade onboarding

- [ ] Uninstall, then `./gradlew :app:installDebug` (no DEBUG_SEED).
- [ ] First launch routes to SettingsScreen (Plan 3 onboarding).
- [ ] Complete the SAVE → app routes to PermissionScreen (not directly to dashboard).
- [ ] PermissionScreen shows 3 rows on Android 14+, 2 rows on Android 13, 1 row on Android 8-12 (battery whitelist only).
- [ ] Tap each row → system dialog or system intent. After granting, the row's [✓] check appears.
- [ ] Tap [CONTINUE] → routes to dashboard. Subsequent launches go directly to dashboard.

### Settings denied-permission banner

- [ ] In device settings, revoke POST_NOTIFICATIONS for SkyFrame.
- [ ] Open SettingsScreen → yellow banner appears: "SEVERE WEATHER ALERTS DISABLED" + [GRANT] button.
- [ ] Tap [GRANT] → app's system settings page opens. Re-grant the permission.
- [ ] Re-open SettingsScreen → banner is gone.

### AlertCheckWorker baseline poll

- [ ] After a fresh install + onboarding, give the app ~16 minutes (first PeriodicWorkRequest run + 1-min slack).
- [ ] Confirm via `adb shell dumpsys jobscheduler | grep -i skyframe` that a periodic job is scheduled.
- [ ] If you can synthesize a fresh alert via your local NWS area, confirm a notification fires within 15 minutes of issuance.

### Full-screen intent (life-safety only)

This is hard to test without a real tornado warning. Synthetic verification:

- [ ] Temporarily inject a synthetic top-tier alert (rank 1-4) into the worker's fetch path, or hand-fire `notificationDispatcher.notify(syntheticAlert)` once.
- [ ] Lock the phone, fire the notification.
- [ ] Expected: screen wakes, FullScreenAlertActivity renders with tier-color top stripe, "TORNADO WARNING" in tier color, body text, [VIEW DETAILS] + [DISMISS] buttons.
- [ ] [VIEW DETAILS] → unlocks (or prompts keyguard) → MainActivity opens AlertDetailSheet.
- [ ] [DISMISS] → activity finishes, notification clears, ack persists across re-poll.
- [ ] REVERT the synthetic injection before tagging.

### Bidirectional dismissal

- [ ] Fire a (real or synthetic) alert. Confirm both the in-app AlertBanner AND a system shade notification exist.
- [ ] Tap [×] in the in-app banner. Expected: notification disappears from shade.
- [ ] Fire again (different alert id). Tap [DISMISS] in the system notification action. Expected: in-app banner refreshes and the alert is gone.

### EscalationWorker chain

- [ ] When a top-tier alert is active, a OneTimeWorkRequest with unique work name `alert_check_escalation` should appear in WorkManager.
- [ ] Verify with: `adb shell dumpsys jobscheduler | grep -i escalation` or via `WorkManager.getWorkInfosForUniqueWork`.
- [ ] When the top-tier alert clears, the next escalation worker run does NOT enqueue a new chain.

### Notification audio

- [ ] Trigger a `severe_weather` channel notification - audible 1050 Hz tone (~800 ms).
- [ ] Trigger a `life_safety` channel notification - audible 1050 Hz tone, ~3 cycles of 500ms-on / 1000ms-off.
- [ ] Audio explicitly does NOT sound like the EAS Attention Signal (a dual-tone "boop") - it's a clean single-frequency NWR-style WAT.
```

- [ ] **Step 3: Update the test count line in the Regression section**

Find:

```markdown
- [ ] `./gradlew :app:testDebugUnitTest` → ~153 tests pass, 0 failures (as of v0.3.0)
```

Change to:

```markdown
- [ ] `./gradlew :app:testDebugUnitTest` → ~177 tests pass, 0 failures (as of v0.4.0)
```

- [ ] **Step 4: Update the "What this does NOT verify" section**

Replace the Plan 4 line in `## What this does NOT verify`:

```markdown
These come in Plans 4–5:
- Background WorkManager alert polling + notifications (Plan 4)
- 1050 Hz NWR-style notification audio (Plan 4)
- Release signing, Play Store distribution (Plan 5)
```

with:

```markdown
These come in Plan 5:
- Release signing, Play Store distribution
- GitHub Actions tag→APK pipeline
```

- [ ] **Step 5: Commit**

```powershell
git add docs/SMOKE_TEST.md
git commit -m "$(@'
docs: SMOKE_TEST sections for Plan 4

Channels-created verification (5 channels, 2 groups visible in system
settings), permission cascade onboarding, denied-permission banner,
baseline poll verification, full-screen intent synthetic test
(life-safety only), bidirectional dismissal, EscalationWorker chain
visibility, audio character check.

Regression test count bumped to ~177 / v0.4.0. NOT verified section
trimmed to Plan 5 items only.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task J.2: PROJECT_STATUS + ROADMAP + CHANGELOG + README

**Files:**
- Modify: `docs/PROJECT_STATUS.md`
- Modify: `docs/ROADMAP.md`
- Modify: `CHANGELOG.md`
- Modify: `README.md`

- [ ] **Step 1: Update PROJECT_STATUS header date + tag**

In `docs/PROJECT_STATUS.md`, change:

```markdown
**Last updated:** 2026-05-19 (v0.3.0)
**Current tag:** [v0.3.0](https://github.com/OniNoKen4192/SkyFrameAndroid/releases/tag/v0.3.0)
```

to:

```markdown
**Last updated:** 2026-05-19 (v0.4.0)
**Current tag:** [v0.4.0](https://github.com/OniNoKen4192/SkyFrameAndroid/releases/tag/v0.4.0)
```

- [ ] **Step 2: Insert Plan 4 section in PROJECT_STATUS**

After the Plan 3 phase content (just before `## What's pending`), insert:

```markdown

### Plan 4 — Background alerts + notifications (v0.4.0 / 2026-05-19)

#### Phase A: Foundation

- `AndroidManifest.xml` adds `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + `FullScreenAlertActivity` (showWhenLocked + turnScreenOn, singleTop, excludeFromRecents) + `DismissReceiver` registration + WorkManager startup auto-init disabled
- `NotificationChannels.createAll(context)` — 5 channels (life_safety, severe_weather, watches, advisories, app_updates) in 2 groups (weather_alerts, system); idempotent
- `NotificationIds.forAlertId(id): Int` — stable hash for re-fire-replaces-not-stacks
- `NotificationExtras` — Intent extra key constants
- `SkyFrameWorkerFactory` + `SkyFrameApp implements Configuration.Provider` — Hilt-aware WorkManager initialization

#### Phase B: Audio generator + .ogg outputs

- `tools/generate-notification-audio.py` — numpy + stdlib wave + ffmpeg libvorbis emits both .ogg files. Module docstring documents 47 CFR § 11.45 constraint
- `app/src/main/res/raw/notification_life_safety.ogg` — 1050 Hz, 3 cycles of 500ms-on / 1000ms-off
- `app/src/main/res/raw/notification_severe.ogg` — single 800ms 1050 Hz tone

#### Phase C: Diff layer

- `AlertDiff.diff(current, lastSeen, acknowledged)` — pure new-since-last-poll predicate; 6 tests
- `LastSeenAlertRepository` — DataStore-backed `Set<String>` overwritten each successful poll; 3 tests

#### Phase D: AlertCheckWorker baseline + scheduler

- `AlertCheckWorker` — `@HiltWorker` CoroutineWorker. Fetch + classify + diff + persist. `Result.retry()` on IOException; defensive `Result.success()` on other exceptions to prevent infinite loops
- `AlertCheckScheduler.schedulePeriodic(context)` — 15-min PeriodicWorkRequest with KEEP policy + CONNECTED constraint
- 8 worker tests via `WorkManagerTestInitHelper` + Robolectric + JUnit5 extension
- 2 scheduler tests
- Wired into `SkyFrameApp.onCreate` (idempotent)

#### Phase E: NotificationDispatcher + DismissReceiver + deep-link

- `NotificationDispatcher.notify(alert)` — tier→channel routing (ranks 1-4 life_safety, 5 severe_weather, 6-8 watches, 9-13 advisories), tier-colored accent via `setColor`, tap PendingIntent → MainActivity with `EXTRA_ALERT_ID`, DISMISS action PendingIntent → `DismissReceiver`
- `DismissReceiver` — `@AndroidEntryPoint` BroadcastReceiver records ack + cancels notification
- `MainActivity.onCreate/onNewIntent` route `EXTRA_ALERT_ID` to `DashboardViewModel.openAlertDetail(id)` which opens `AlertDetailSheet`

#### Phase F: EscalationWorker

- `EscalationWorker` — one-shot `ExpeditedWorkRequest` (2-min initial delay) chained from `AlertCheckWorker` when top-tier (rank 1-4) active. Self-chains while top-tier active; clears when top-tier expires
- `OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST` graceful degradation
- 3 tests

#### Phase G: FullScreenAlertActivity

- Separate `ComponentActivity` with `setShowWhenLocked` + `setTurnScreenOn` + `requestDismissKeyguard`. Compose UI: tier-color top stripe, ⚠ + tier-colored uppercase event, body, [VIEW DETAILS] + [DISMISS] CTAs
- `NotificationDispatcher.setFullScreenIntent` gated to life_safety channel only

#### Phase H: Bidirectional acknowledgment sync

- `DashboardViewModel.dismissAlert(id)` cancels the system notification in addition to recording ack
- Symmetric to `DismissReceiver` which records ack + cancels notification

#### Phase I: Permission cascade onboarding

- `SettingsRepository.Snapshot.permissionsPromptedAt: Long` — drives start-destination decision
- `PermissionScreen` Compose route inserted between SETTINGS and DASHBOARD on first run; three permission rows (POST_NOTIFICATIONS, USE_FULL_SCREEN_INTENT, battery whitelist), CONTINUE always enabled
- `MainActivity.onCreate` 3-way start-destination: !isConfigured → SETTINGS; configured + permissionsPromptedAt == 0L → PERMISSIONS; else → DASHBOARD
- `SettingsScreen` shows yellow banner when POST_NOTIFICATIONS denied + [GRANT] deep-link

Test count: 153 → ~177 (+~24 new tests).
```

- [ ] **Step 3: Trim "What's pending" to Plan 5 only**

In `docs/PROJECT_STATUS.md`, find:

```markdown
## What's pending

See [docs/ROADMAP.md](ROADMAP.md) for the full Plans 4–5 outline. Headline pending items:

- **Plan 4:** Background WorkManager alert polling + system notifications (life-safety + severe channels) + 1050 Hz NWR-style notification audio + battery-optimization whitelist + POST_NOTIFICATIONS permission flow
- **Plan 5:** Release signing keystore + GitHub Actions APK build on tag + Play Store internal track + README install instructions
```

Replace with:

```markdown
## What's pending

See [docs/ROADMAP.md](ROADMAP.md) for the Plan 5 outline. Headline pending items:

- **Plan 5:** Release signing keystore + GitHub Actions APK build on tag + Play Store internal track + README install instructions + Data Safety form
```

- [ ] **Step 4: Update ROADMAP — flip Plan 4 to ✅**

In `docs/ROADMAP.md`, find:

```markdown
| **Plan 4** — Background alerts | WorkManager periodic alert poll + system notifications (life-safety + severe channels) + 1050 Hz NWR-style notification audio + battery-optimization whitelist + POST_NOTIFICATIONS permission flow + full-screen intent for top-tier alerts | Not started | — |
```

Change to:

```markdown
| **Plan 4** — Background alerts | WorkManager 15-min periodic + ExpeditedWorkRequest 2-min escalation for top-tier alerts; 5 notification channels (life_safety / severe_weather / watches / advisories / app_updates) in 2 groups; 1050 Hz NWR-style audio (47 CFR § 11.45 compliant); FullScreenAlertActivity for life-safety alerts; permission cascade in onboarding (POST_NOTIFICATIONS + USE_FULL_SCREEN_INTENT + battery whitelist); bidirectional acknowledgment sync | ✅ **Shipped** | [`v0.4.0`](https://github.com/OniNoKen4192/SkyFrameAndroid/releases/tag/v0.4.0) |
```

Update the dependency diagram:

```
Plan 1 (foundation) ✓
   └── Plan 2 (sheets + trends) ✓
           └── Plan 3 (settings + onboarding) ✓ ──┐
                                                  ├── Plan 5 (distribution)
                                                  │
       Plan 4 (background alerts) ✓ ──────────────┘
```

- [ ] **Step 5: Add v0.4.0 to CHANGELOG**

In `CHANGELOG.md`, find:

```markdown
## [Unreleased]

Plan 4 (background WorkManager + notifications + 1050 Hz audio) is the next target — see [docs/ROADMAP.md](docs/ROADMAP.md).

---

## [v0.3.0] — 2026-05-19
```

Replace with:

```markdown
## [Unreleased]

Plan 5 (release signing + GitHub Actions APK pipeline + Play Store internal track) is the next target — see [docs/ROADMAP.md](docs/ROADMAP.md).

---

## [v0.4.0] — 2026-05-19

Plan 4 milestone: the headline native feature. Severe weather alerts arrive on the user's lock screen even when SkyFrame is closed. WorkManager 15-min periodic poll, ExpeditedWorkRequest 2-min escalation while top-tier alerts active, dedicated `FullScreenAlertActivity` for life-safety notifications, permission cascade appended to first-run onboarding.

### Added

- **`AlertCheckWorker`** — `@HiltWorker` CoroutineWorker registered via `AlertCheckScheduler.schedulePeriodic()` with KEEP policy + CONNECTED constraint at 15-min cadence. Fetches `/alerts/active`, classifies via existing `AlertNormalizer`, diffs against `LastSeenAlertRepository` + `AlertAcknowledgmentRepository`, fires notifications via `NotificationDispatcher`. `Result.retry()` on IOException; defensive `Result.success()` on other exceptions to prevent infinite loops.
- **`EscalationWorker`** — one-shot `ExpeditedWorkRequest` with 2-min initial delay, chained from `AlertCheckWorker` when top-tier (rank 1-4) alerts present. Self-chains while top-tier active; stops naturally when cleared. `OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST` graceful degradation.
- **`NotificationChannels`** — 5 channels in 2 groups, idempotent registration in `SkyFrameApp.onCreate`. Channel groups make them individually editable in system settings (API 26+).
- **1050 Hz NWR-style audio** — `notification_life_safety.ogg` (3 cycles of 500ms-on / 1000ms-off) and `notification_severe.ogg` (single 800ms tone). Generated by `tools/generate-notification-audio.py` (numpy + stdlib wave + ffmpeg). Module docstring documents 47 CFR § 11.45 constraint (NO EAS Attention Signal at 853+960 Hz, NO SAME header bursts).
- **`NotificationDispatcher`** — builds `NotificationCompat.Builder` for an Alert, routes by tier rank to channel, tier-colored accent, BigTextStyle expanded body, tap PendingIntent → MainActivity, DISMISS action PendingIntent → DismissReceiver. `setFullScreenIntent` gated to life_safety channel only.
- **`DismissReceiver`** — `@AndroidEntryPoint` BroadcastReceiver records ack + cancels notification.
- **`FullScreenAlertActivity`** — separate `ComponentActivity` for life-safety notifications. `setShowWhenLocked` + `setTurnScreenOn` + `requestDismissKeyguard`. Compose UI: tier-color top stripe, large ⚠ + uppercase event name in tier color, body text, [VIEW DETAILS] + [DISMISS] CTAs.
- **`AlertDiff`** — pure new-since-last-poll predicate; filters current alerts to those whose IDs are absent from both lastSeen and acknowledged sets.
- **`LastSeenAlertRepository`** — DataStore-backed `Set<String>` overwritten after each successful background poll.
- **MainActivity deep-link** — `onCreate` / `onNewIntent` handle `EXTRA_ALERT_ID` by calling `DashboardViewModel.openAlertDetail(id)` which opens `AlertDetailSheet` for that alert.
- **Permission cascade onboarding** — `PermissionScreen` Compose route inserted between SETTINGS and DASHBOARD on first run. Three rows: POST_NOTIFICATIONS (Android 13+), USE_FULL_SCREEN_INTENT (Android 14+), battery optimization whitelist. CONTINUE always enabled.
- **`SettingsScreen` denied-permission banner** — yellow banner when POST_NOTIFICATIONS denied + [GRANT] button deep-linking to system app settings.
- **Bidirectional acknowledgment sync** — `DashboardViewModel.dismissAlert(id)` cancels the system notification (was just ack-only); `DismissReceiver` also records ack (symmetric).

### Changed

- **`SkyFrameApp`** implements `Configuration.Provider` for Hilt-aware WorkManager. Manifest disables WorkManager startup auto-initializer (required when supplying `Configuration` manually). `onCreate` calls `NotificationChannels.createAll(this)` + `AlertCheckScheduler.schedulePeriodic(this)`.
- **`MainActivity`** start-destination is now 3-way: !isConfigured → SETTINGS, configured + permissionsPromptedAt == 0L → PERMISSIONS, else → DASHBOARD. v0.3.0 users upgrading to v0.4.0 will see PermissionScreen once on first launch.
- **`SettingsRepository.Snapshot.permissionsPromptedAt: Long`** — new field tracking whether the user has seen the permission cascade.
- **`AndroidManifest.xml`** declares `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` + `FullScreenAlertActivity` + `DismissReceiver`.

### Test count

153 → ~177 (+~24 new tests across `AlertDiff`, `LastSeenAlertRepository`, `NotificationIds`, `AlertCheckScheduler`, `AlertCheckWorker`, `EscalationWorker`).

---

## [v0.3.0] — 2026-05-19
```

- [ ] **Step 6: Update README**

In `README.md`, change:

```markdown
**Current tag:** [v0.3.0](https://github.com/OniNoKen4192/SkyFrameAndroid/releases/tag/v0.3.0) (Plans 1 + 2 + 3 of 5 complete) · [CHANGELOG](CHANGELOG.md) · [Roadmap](docs/ROADMAP.md) · [Project status](docs/PROJECT_STATUS.md)
```

to:

```markdown
**Current tag:** [v0.4.0](https://github.com/OniNoKen4192/SkyFrameAndroid/releases/tag/v0.4.0) (Plans 1 + 2 + 3 + 4 of 5 complete) · [CHANGELOG](CHANGELOG.md) · [Roadmap](docs/ROADMAP.md) · [Project status](docs/PROJECT_STATUS.md)
```

Find the Status by area section and change:

```markdown
- ⏳ Background notifications (the headline native feature) — [Plan 4](docs/ROADMAP.md)
```

to:

```markdown
- ✅ Background notifications (the headline native feature) — [Plan 4](docs/ROADMAP.md)
```

Find the test count line:

```markdown
153 unit tests as of v0.3.0
```

and change to:

```markdown
~177 unit tests as of v0.4.0
```

- [ ] **Step 7: Commit all doc updates together**

```powershell
git add docs/PROJECT_STATUS.md docs/ROADMAP.md CHANGELOG.md README.md
git commit -m "$(@'
docs: update PROJECT_STATUS, ROADMAP, CHANGELOG, README for v0.4.0

PROJECT_STATUS: Plan 4 implemented-features list organized by phase
(foundation, audio, diff layer, worker baseline, dispatcher,
escalation, full-screen, sync, onboarding). Header date + tag bumped
to v0.4.0. What's pending trimmed to Plan 5 only. Test count 153 -> ~177.

ROADMAP: Plan 4 row flipped to Shipped at v0.4.0. Dependency diagram
updated.

CHANGELOG: v0.4.0 release notes - WorkManager periodic + expedited
escalation, 5 notification channels with 1050 Hz NWR-style audio
(47 CFR section 11.45 compliant), FullScreenAlertActivity for
life-safety, permission cascade onboarding, bidirectional ack sync.

README: tag badge + status-by-area flipped Plan 4 to shipped. Test
count updated.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task J.3: Final test run + APK build + tag v0.4.0 + push

- [ ] **Step 1: Run the full test suite**

```powershell
./gradlew.bat :app:testDebugUnitTest --no-daemon 2>&1 | Select-Object -Last 6
```

Expected: BUILD SUCCESSFUL.

```powershell
$xml = Get-ChildItem app/build/test-results/testDebugUnitTest/*.xml
$total = 0
foreach ($f in $xml) { $total += (Select-String -Path $f.FullName -Pattern '<testcase ').Count }
"Total tests: $total"
```

Expected: ~177 tests, 0 failures.

- [ ] **Step 2: Build the debug APK**

```powershell
./gradlew.bat :app:assembleDebug --no-daemon 2>&1 | Select-Object -Last 5
```

Expected: BUILD SUCCESSFUL. APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 3: Manual smoke verification (per Phase J.1 SMOKE_TEST additions)**

Before tagging, run through SMOKE_TEST.md's new Plan 4 sections at minimum. Critical items:
- Notification channels visible in system settings
- PermissionScreen reachable on fresh install
- Synthetic alert injection → notification fires + tap routes to AlertDetailSheet
- Bidirectional dismissal works
- Audio sounds like a single clean 1050 Hz tone (NOT a dual-tone EAS approximation)

- [ ] **Step 4: Tag v0.4.0**

```powershell
git tag -a v0.4.0 -m "Plan 4 milestone: background WorkManager alert polling + system notifications (5 channels, 2 groups, 1050 Hz NWR-style audio, 47 CFR 11.45 compliant) + FullScreenAlertActivity for life-safety alerts + ExpeditedWorkRequest escalation chain for top-tier alerts + permission cascade onboarding (POST_NOTIFICATIONS + USE_FULL_SCREEN_INTENT + battery whitelist) + bidirectional acknowledgment sync. ~177 unit tests, 0 failures."
```

- [ ] **Step 5: Push main + tag (confirm with user before pushing per project convention)**

```powershell
git push origin main
git push origin v0.4.0
```

Expected: both pushes succeed. Visit https://github.com/OniNoKen4192/SkyFrameAndroid/releases — v0.4.0 should appear.

- [ ] **Step 6: Verify tag exists locally + remotely**

```powershell
git tag --list | Select-String "v0.4.0"
git ls-remote --tags origin | Select-String "v0.4.0"
```

Expected: both show `v0.4.0`.

---

**Phase J milestone — Plan 4 complete.** v0.4.0 tagged on GitHub. The headline native feature ships.

---

## Plan 4 Self-Review

### Spec coverage check

Walked through each requirement in [the design spec](../specs/2026-05-19-skyframe-android-plan-4-background-alerts-design.md):

- **What ships #1 (`AlertCheckWorker` + scheduler):** Tasks D.1 + D.2 + D.3 ✓
- **What ships #2 (Expedited escalation):** Task F.1 ✓
- **What ships #3 (5 notification channels):** Task A.2 ✓
- **What ships #4 (Python audio generator):** Tasks B.1 + B.2 ✓
- **What ships #5 (Notification UX with tap + DISMISS):** Tasks E.1 + E.2 + E.3 ✓
- **What ships #6 (`FullScreenAlertActivity`):** Task G.1 ✓
- **What ships #7 (Permission cascade in onboarding):** Tasks I.1 + I.2 + I.3 ✓
- **What ships #8 (SettingsScreen status banner):** Task I.4 ✓
- **What ships #9 (Bidirectional acknowledgment sync):** Task H.1 (in-app→system); Task E.2 (system→in-app via DismissReceiver) ✓
- **All 5 brainstorm decisions:** appended permission cascade (I.x), Python generator (B.x), foreground-only update check (no change, deferred), system channels only (no in-app toggle ship), dedicated FullScreenAlertActivity (G.1) — all honored ✓
- **Documentation updates:** Task J.1 covers SMOKE_TEST, Task J.2 covers PROJECT_STATUS + ROADMAP + CHANGELOG + README ✓

No spec gaps.

### Placeholder scan

- No `TBD` / `TODO` / `implement later` in committed code (one `TODO Phase J` comment in `NotificationDispatcher.setSmallIcon` re: a future dedicated monochrome status-bar icon — that's a deliberate marker for v0.5+ polish, not a Plan 4 unfinished item)
- "Add appropriate error handling" / "handle edge cases": none — every error path is named with its handling rule
- "Write tests for the above" without code: none — every test step has the full test body
- "Similar to Task N": none — each task self-contained
- Steps that describe without showing code: none — code blocks present for every code step

### Type consistency check

- `AlertDiff.diff(current, lastSeen, acknowledged)` signature consistent: C.1 declaration, D.2 + F.1 consumers
- `LastSeenAlertRepository.read()` / `.write(ids)` signatures consistent: C.2 declaration, D.2 + F.1 consumers
- `NotificationIds.forAlertId(id: String): Int` signature consistent: A.3 declaration, E.1 + H.1 consumers (NOT `forAlert(alert)` — uniformly takes the id String)
- `NotificationExtras.ALERT_ID` / `NOTIFICATION_ID` keys consistent across NotificationDispatcher, DismissReceiver, MainActivity, FullScreenAlertActivity
- `FullScreenAlertActivity.EXTRA_EVENT` / `EXTRA_BODY` / `EXTRA_TIER_COLOR` companion-object keys consistent between dispatcher (publisher) and activity (consumer)
- `EscalationWorker.UNIQUE_WORK_NAME` referenced consistently: F.1 declaration, F.1 test, J.1 SMOKE_TEST
- `AlertCheckScheduler.UNIQUE_WORK_NAME` referenced consistently: D.1 declaration, D.1 test
- `SettingsRepository.Snapshot.permissionsPromptedAt: Long` shape consistent: I.1 declaration, I.3 reader, J.2 doc
- `NotificationChannels.LIFE_SAFETY` etc. constants consistent: A.2 declaration, E.1 router, G.1 + E.1 setFullScreenIntent gate
- `DashboardViewModel.openAlertDetail(id)` / `dismissAlert(id)` signatures consistent: existing + E.3 add + H.1 modify; both take String id
- `AlertTier.baseColor` accessed as `Long` (matches the existing enum's `baseColor: Long` declaration verified during plan writing) — used in NotificationDispatcher `setColor(alert.tier.baseColor.toInt())` and FullScreenAlertActivity intent extra

No type-consistency issues.

### Scope check

Plan 4 is one focused milestone — background alert polling + notification UX + permission cascade. All work serves the spec's stated goals. No drift into Plan 5 territory (no signing, no Play Store, no GitHub Actions APK pipeline). YAGNI items (update-check WorkManager, in-app master toggle) were explicitly excluded per the design's brainstorm decisions.

### Ambiguity check

- Phase B specifies the exact `.ogg` durations (3 cycles × 1500ms total = 4.5s for life_safety, single 800ms for severe) so audio character isn't subjective.
- Phase D `AlertCheckWorker` handles network vs. parse exceptions with explicit different policies (retry vs. success) per the spec's error handling table.
- Phase E `NotificationDispatcher.channelFor` enumerates exact rank ranges (1..4, 5, 6..8, 9..13) matching the spec's channel table 1:1.
- Phase F `EscalationWorker.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST` policy explicit so quota-exhaustion behavior isn't ambiguous.
- Phase G `FullScreenAlertActivity` reads minimal data from intent extras (event name, body, tier color, alert id, notification id) rather than depending on the in-app WeatherResponse — explicit so future readers don't try to "improve" it by fetching from the ViewModel.
- Phase I `permissionsPromptedAt == 0L` semantics explicit: existing v0.3.0 installs upgrading to v0.4.0 will see PermissionScreen on first v0.4.0 launch (because they default to 0L), which IS the desired behavior.

---

## Execution Handoff

Plan complete and saved to [docs/superpowers/plans/2026-05-19-skyframe-android-plan-4-background-alerts.md](2026-05-19-skyframe-android-plan-4-background-alerts.md). Total: 22 tasks across 10 phases (A–J), ~24 new unit tests for a ~177 total, ends with `v0.4.0` tagged on GitHub.

Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Plans 1–3 used the hybrid approach (direct for mechanical, subagents for judgment). That continues to be a reasonable choice here too.

Which approach?
