# SkyFrame Android — Plan 4 Design: Background Alerts + Notifications

**Date:** 2026-05-19
**Tag at completion:** `v0.4.0`
**Parent spec:** [docs/superpowers/specs/2026-05-16-skyframe-android-design.md](2026-05-16-skyframe-android-design.md)
**Prior plans:** Plan 1 ([v0.1.1-mvp](https://github.com/OniNoKen4192/SkyFrameAndroid/releases/tag/v0.1.1-mvp)), Plan 2 ([v0.2.0](https://github.com/OniNoKen4192/SkyFrameAndroid/releases/tag/v0.2.0)), Plan 3 ([v0.3.0](https://github.com/OniNoKen4192/SkyFrameAndroid/releases/tag/v0.3.0))

## Goal

Ship the headline native feature: severe weather alerts arrive on the user's lock screen even when SkyFrame is closed. Background WorkManager polls `/alerts/active`, diffs against last-seen, fires tier-routed system notifications. Top-tier (tornado-warning and above) triggers a full-screen intent that wakes the screen with a dedicated, dramatic activity.

## What ships

1. **`AlertCheckWorker`** — WorkManager `PeriodicWorkRequest`, 15-min cadence (Android minimum). Fetches `/alerts/active`, classifies via existing `AlertNormalizer`, runs `AlertDiff`, fires notifications. Constraint: `NetworkType.CONNECTED`. `setRequiresBatteryNotLow(false)` — severe weather doesn't pause for low battery.

2. **Expedited escalation** — when last poll returns a top-tier active alert (`tornado-emergency` / `tornado-pds` / `tornado-warning` / `tstorm-destructive`, ranks 1–4), `AlertCheckWorker` schedules a one-shot `EscalationWorker` as `ExpeditedWorkRequest`. The escalation worker re-runs the same diff + notify logic and chains itself if top-tier remains active. Degrades gracefully to 15-min periodic when Android's expedited quota is exhausted.

3. **Five notification channels** (created in `SkyFrameApp.onCreate`, idempotent):

   | Channel ID | Importance | Sound | DND bypass | Full-screen intent | Tiers |
   |---|---|---|---|---|---|
   | `life_safety` | HIGH | `notification_life_safety.ogg` (looping) | yes | yes | ranks 1–4 |
   | `severe_weather` | HIGH | `notification_severe.ogg` (single) | no | no | rank 5 (severe-warning) |
   | `watches` | DEFAULT | system default | no | no | ranks 6–8 (blizzard, winter-storm, flood) |
   | `advisories` | LOW | none | no | no | ranks 9–13 |
   | `app_updates` | MIN | none | no | no | (synthetic update alerts) |

   Two channel groups: `weather_alerts` (first four) and `system` (app_updates). Channel groups make these individually editable in system settings.

4. **Python audio generator** at `tools/generate-notification-audio.py`. Numpy + stdlib `wave` to emit raw PCM, then `ffmpeg -f wav -i - -c:a libvorbis -q:a 5 …` to encode `.ogg`. Module docstring documents the 47 CFR § 11.45 constraint (no EAS Attention Signal at 853+960 Hz, no SAME header bursts — only 1050 Hz NWR-style WAT). Outputs:
   - `notification_life_safety.ogg` — 1050 Hz, ~500ms on / 1000ms off × 3 cycles (~4.5s total, loops on channel).
   - `notification_severe.ogg` — single ~800ms 1050 Hz tone.

   Both committed to `app/src/main/res/raw/`. Generator committed alongside.

5. **Notification UX** —
   - Title: `⚠ TORNADO WARNING` (event name uppercased; ⚠ glyph)
   - Body: `Until 22:15 · Milwaukee County` + first line of description
   - Color accent (`setColor`): tier base color from `ALERT_TIERS.md`
   - Tap → `MainActivity` with `EXTRA_ALERT_ID` → routes to `AlertDetailSheet` for that alert
   - `[VIEW DETAILS]` action — same as tap
   - `[DISMISS]` action — `DismissReceiver` adds ID to `AlertAcknowledgmentRepository` + `NotificationManagerCompat.cancel()`
   - Notification ID = stable hash of `alert.id` (re-fires replace; never stack)

6. **`FullScreenAlertActivity`** — separate `ComponentActivity`, life_safety channel only. `setShowWhenLocked(true)` + `setTurnScreenOn(true)`. Edge-to-edge Compose UI: tier-color background with dark hazard stripes (re-uses existing HUD styling), large tier-color event name, body text, expiration time, two big tap targets ([VIEW DETAILS] / [DISMISS]). VIEW DETAILS launches MainActivity + finishes self; DISMISS records ack + finishes self.

7. **Permission cascade in onboarding** — new `PermissionScreen` Compose route, inserted between `SETTINGS` and `DASHBOARD` in the NavHost on first run. Three rows, each with rationale + tap-to-request:
   - **POST_NOTIFICATIONS** (Android 13+) — "Required to alert you to severe weather when the app isn't open." `ActivityResultContracts.RequestPermission`.
   - **USE_FULL_SCREEN_INTENT** (Android 14+ only — hidden on older API; granted by default on `< 34`) — "Lets life-threatening alerts show on your lock screen." `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` system intent.
   - **Battery optimization whitelist** — "Improves background reliability on aggressive OEMs (Samsung, Xiaomi)." `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Skippable.

   Always-enabled `[CONTINUE]` button at the bottom — permissions are optional from the app's perspective; system enforces functional consequences. On continue: `settings.update { permissionsPromptedAt = System.currentTimeMillis() }` + navigate to dashboard with `popUpTo(PERMISSIONS) { inclusive = true }`.

8. **SettingsScreen status banner** — yellow banner at top of SettingsScreen when POST_NOTIFICATIONS denied: "SEVERE WEATHER ALERTS DISABLED — system notification permission required" + `[GRANT]` button deep-linking to `ACTION_APPLICATION_DETAILS_SETTINGS`.

9. **Bidirectional acknowledgment sync** —
   - In-app banner [×] dismissal → `DashboardViewModel.dismissAlert(id)` adds to `AlertAcknowledgmentRepository` AND calls `NotificationManagerCompat.cancel(NotificationIds.forAlert(alert))`.
   - System DISMISS action → `DismissReceiver` adds to `AlertAcknowledgmentRepository` AND cancels its own notification. Next AlertBanner state read (via existing Flow on the repository) clears it from the in-app banner.

## Out of scope

Per parent spec YAGNI: multi-location alerts; alert geofencing; tone customization; per-tier silence-window. Specific to Plan 4 — explicitly deferred:

- **Update-check WorkManager job** — Plan 3's foreground poll is enough; user has to open the app to see the alert anyway. Adding a daily WorkManager job is YAGNI for a single-user app.
- **In-app master toggle for severe weather alerts** — system channel settings are the source of truth. No second source of truth.
- **Per-channel in-app toggles** — same reason.

## Module structure (new + touched)

**New packages and files:**

```
app/src/main/kotlin/com/skyframe/
  background/
    AlertCheckWorker.kt              CoroutineWorker, @HiltWorker
    EscalationWorker.kt              one-shot ExpeditedWorkRequest
    AlertCheckScheduler.kt           PeriodicWorkRequest registration (KEEP policy)
    SkyFrameWorkerFactory.kt         HiltWorkerFactory wiring
  data/alerts/history/
    LastSeenAlertRepository.kt       DataStore<Set<String>> of last-poll alert IDs
    AlertDiff.kt                     pure: diff(current, lastSeen, ack): List<Alert>
  notifications/
    NotificationChannels.kt          createAll(context) - 5 channels + 2 groups
    NotificationIds.kt               forAlert(alert): Int (stable hash)
    NotificationDispatcher.kt        notify(alert) - builds + posts via NotificationManagerCompat
    DismissReceiver.kt               BroadcastReceiver for DISMISS action
    NotificationExtras.kt            constants: EXTRA_ALERT_ID, EXTRA_NOTIFICATION_ID
  ui/alert/
    FullScreenAlertActivity.kt       separate Activity, setShowWhenLocked + setTurnScreenOn
  ui/screens/
    PermissionScreen.kt              Compose route between SETTINGS and DASHBOARD

tools/
  generate-notification-audio.py     47 CFR § 11.45-compliant tone generator

app/src/main/res/raw/
  notification_life_safety.ogg       generated, committed
  notification_severe.ogg            generated, committed
```

**Touched existing files:**

- `app/src/main/AndroidManifest.xml` — add `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. Register `FullScreenAlertActivity` (with `android:showWhenLocked="true"` + `android:turnScreenOn="true"`). Register `DismissReceiver`.
- `app/src/main/kotlin/com/skyframe/SkyFrameApp.kt` — implement `Configuration.Provider` for Hilt-aware WorkManager. Call `NotificationChannels.createAll()` + `AlertCheckScheduler.schedulePeriodic()` in `onCreate`.
- `app/src/main/kotlin/com/skyframe/MainActivity.kt` — handle `EXTRA_ALERT_ID` intent extra (route to AlertDetailSheet for that alert). Update start-destination logic: not configured → SETTINGS; configured + `permissionsPromptedAt == 0L` → PERMISSIONS; else → DASHBOARD.
- `app/src/main/kotlin/com/skyframe/ui/nav/NavRoutes.kt` — add `PERMISSIONS = "permissions"`.
- `app/src/main/kotlin/com/skyframe/ui/nav/SkyFrameNavHost.kt` — wire PERMISSIONS destination; SettingsScreen onSaved navigates to PERMISSIONS on first run; PermissionScreen onContinue navigates to DASHBOARD.
- `app/src/main/kotlin/com/skyframe/ui/screens/SettingsScreen.kt` — status banner when POST_NOTIFICATIONS denied.
- `app/src/main/kotlin/com/skyframe/data/settings/SettingsRepository.kt` — add `permissionsPromptedAt: Long = 0L` to `Snapshot`; persist via `SettingsKeys.PERMISSIONS_PROMPTED_AT`.
- `app/src/main/kotlin/com/skyframe/viewmodel/DashboardViewModel.kt` — on `dismissAlert(id)`, call `NotificationManagerCompat.cancel(...)` for bidirectional sync.
- `app/build.gradle.kts` / `gradle/libs.versions.toml` — no new deps needed. `work-runtime-ktx`, `hilt-work` (incl. its ksp compiler), and `work-testing` are already wired from prior plans' scaffolding. Only the Plan 4 *usage* of `Configuration.Provider` + `@HiltWorker` activates them.

## Data flow

### Background poll (happy path)

```
SkyFrameApp.onCreate()
  ├── NotificationChannels.createAll()        (idempotent)
  └── AlertCheckScheduler.schedulePeriodic()  (KEEP policy = idempotent)

[every 15 min]
  AlertCheckWorker.doWork() →
    1. cfg = settings.snapshot()
    2. if !cfg.isConfigured: Result.success()  (skip, no NWS call)
    3. alertsDto = nws.activeAlerts(cfg.lat, cfg.lon)  (Result.retry on IOException)
    4. classified = AlertNormalizer.normalize(alertsDto)
    5. lastSeen = lastSeenAlertRepo.read()
    6. ack = acknowledgmentRepo.read()
    7. newAlerts = AlertDiff.diff(classified, lastSeen, ack)
    8. newAlerts.forEach { notificationDispatcher.notify(it) }
    9. lastSeenAlertRepo.write(classified.map { it.id }.toSet())
    10. if classified.any { it.tier.rank <= 4 }: EscalationWorker.enqueue()
    11. Result.success()
```

### Diff semantics

```kotlin
fun diff(
    current: List<Alert>,
    lastSeen: Set<String>,
    acknowledged: Set<String>,
): List<Alert> = current.filter { c ->
    c.id !in lastSeen && c.id !in acknowledged
}
```

Edge cases:
- **Alert reissued with same ID** (extended expires) → `id in lastSeen` → not re-notified.
- **Alert dismissed in-app, then ack-expired (>24h), then still in NWS active list** → `id in lastSeen` (was in last poll) → still not re-notified. Correct: same alert.
- **Brand new alert after ack-expired old one** → fresh ID → re-notified. Correct.

### Acknowledgment sync

```
[in-app × tap]            [system DISMISS tap]
DashboardViewModel        DismissReceiver
  .dismissAlert(id)         .onReceive(intent)
    ↓                         ↓
  ackRepo.add(id)           ackRepo.add(alertId)
  NotificationManager       NotificationManager
    .cancel(forAlert(...))    .cancel(notificationId)
                              (banner clears via repo Flow on
                               next state read)
```

`AlertAcknowledgmentRepository` is already atomic (per Plan 1 review). No new race-condition concerns introduced.

### FullScreenAlertActivity lifecycle

```
life_safety alert fires + screen off
  ↓
Notification with setFullScreenIntent(FullScreenAlertActivity)
  ↓
System wakes screen → FullScreenAlertActivity launches
  ↓
setShowWhenLocked(true) + setTurnScreenOn(true) take effect
  ↓
Compose UI: tier-color background, event, body, [VIEW DETAILS] [DISMISS]
  ↓
[VIEW DETAILS] → Intent to MainActivity + alertId → finish()
[DISMISS]      → ackRepo.add(id) + cancel(notificationId) → finish()
```

### Permission cascade

```
Plan 3 SettingsScreen SAVE (first run only)
  ↓
SkyFrameNavHost: navController.navigate(PERMISSIONS) {
    popUpTo(SETTINGS) { inclusive = true }
}
  ↓
PermissionScreen renders 3 rows (POST_NOTIFICATIONS, USE_FULL_SCREEN_INTENT, battery)
  ↓
User taps each row → system permission dialog or system intent
  ↓
User taps [CONTINUE] (always enabled)
  ↓
settings.update { permissionsPromptedAt = System.currentTimeMillis() }
navController.navigate(DASHBOARD) { popUpTo(PERMISSIONS) { inclusive = true } }
```

### Start-destination decision

```kotlin
val start = when {
    !snap.isConfigured              -> NavRoutes.SETTINGS    // Plan 3
    snap.permissionsPromptedAt == 0L -> NavRoutes.PERMISSIONS // Plan 4 new
    else                            -> NavRoutes.DASHBOARD
}
```

Returning to SettingsScreen post-onboarding does NOT re-route through PermissionScreen — user revisits permissions via the SettingsScreen status banner.

## Error handling

| Condition | Handling |
|---|---|
| Network failure in worker | `Result.retry()`; WorkManager exponential backoff (30s → 5h cap); periodic re-runs in 15min anyway |
| `/alerts/active` empty list | Normal "all clear"; worker writes empty set; existing system notifications stay until OS-expiration |
| Worker invoked pre-onboarding | `Result.success()` early return; no NWS call, no state change |
| POST_NOTIFICATIONS denied | Worker runs; `notify()` no-ops silently; SettingsScreen banner surfaces issue |
| Expedited quota exhausted | `EscalationWorker` falls back to 15-min periodic; documented in worker KDoc |
| Battery opt not whitelisted | App works; worker may be deferred during Doze; documented in onboarding rationale |
| DataStore corrupted | `LastSeenAlertRepository.read()` returns empty set; next poll treats all current alerts as new (self-healing, slightly noisy) |
| Notification audio file missing | System falls back to default channel sound; manual smoke test catches before tag |

## Testing strategy

**New unit tests (~24, brings total to ~177):**

- **`AlertDiffTest`** (~6): empty inputs; all-new; partial overlap; acknowledged-but-new; acknowledged-and-old; reissued same ID.
- **`LastSeenAlertRepositoryTest`** (~3): empty initial read; write-then-read roundtrip; overwrite semantics.
- **`NotificationIdsTest`** (~2): same alert ID → same Int; different IDs → different Ints.
- **`AlertCheckWorkerTest`** (~8, via `WorkManagerTestInitHelper`):
  - skips when `!isConfigured`
  - retries on IOException
  - fires notifications only for new alerts
  - persists current IDs to `LastSeenAlertRepository`
  - skips acknowledged alerts
  - schedules EscalationWorker when top-tier present
  - does NOT schedule escalation when no top-tier
  - same alert reissued does not re-fire
- **`EscalationWorkerTest`** (~3): chains itself when top-tier active; does not chain when cleared; uses same diff logic.
- **`AlertCheckSchedulerTest`** (~2): `schedulePeriodic` uses KEEP policy (idempotent); `cancelAll` removes work.

**NOT tested (deferred per existing testing posture):**

- `NotificationDispatcher` — would mock `NotificationManagerCompat`; marginal value; covered transitively + manual smoke test.
- `FullScreenAlertActivity` Compose rendering — hand-verified per `SMOKE_TEST.md`.
- `DismissReceiver` — thin wrapper; logic is in tested repos.
- That Android actually fires `PeriodicWorkRequest` at 15-min cadence — WorkManager's contract, not ours.
- That `setShowWhenLocked` actually wakes the screen — Activity framework behavior.
- That the Python audio generator produces 1050 Hz — `ffmpeg`/numpy contract; manual ear-test once.

## Implementation phases

- **Phase A — Foundation.** Manifest permissions, `NotificationChannels.createAll`, `NotificationIds.forAlert`, Hilt-aware WorkManager initializer (`SkyFrameApp implements Configuration.Provider`). No build-catalog work needed — deps are pre-wired.
- **Phase B — Audio generator + .ogg outputs.** Python script, both .ogg files generated and committed.
- **Phase C — `AlertDiff` + `LastSeenAlertRepository`.** Pure logic + DataStore repo; 9 tests.
- **Phase D — `AlertCheckWorker` (baseline).** CoroutineWorker, fetches + diffs + persists; logs only (no notifications yet). `AlertCheckScheduler` registers periodic. 8 tests via `WorkManagerTestInitHelper`.
- **Phase E — `NotificationDispatcher` + `DismissReceiver` + MainActivity deep-link.** Wire notifications into AlertCheckWorker.
- **Phase F — `EscalationWorker`.** ExpeditedWorkRequest chain when top-tier active; 3 tests.
- **Phase G — `FullScreenAlertActivity`.** Separate Activity, life_safety channel only.
- **Phase H — Bidirectional acknowledgment sync.** `DashboardViewModel.dismissAlert` cancels system notification.
- **Phase I — Permission cascade onboarding.** `PermissionScreen` Compose route, `permissionsPromptedAt` flag, MainActivity start-destination logic, SettingsScreen status banner.
- **Phase J — Documentation + ship.** PROJECT_STATUS, ROADMAP (flip to ✅), CHANGELOG, SMOKE_TEST. Tag `v0.4.0`.

Sequencing rationale: bottom-up (data → worker → notifications → escalation → full-screen → UX → docs). AlertCheckWorker exercised in isolation in Phase D before notification side-effects added in Phase E, so worker bugs and notification bugs don't tangle. Permission UX comes last because everything it gates is already working.

## Estimated scope

- ~14 new files, ~10 touched files
- ~24 new unit tests (total ~177)
- 10 phases, ~25-30 atomic tasks
- Ends with `v0.4.0` tagged on GitHub

## Decisions locked during brainstorm

1. **Permission flow:** appended to first-run onboarding via dedicated PermissionScreen (vs. JIT-only, vs. hybrid).
2. **Audio sourcing:** Python generator script + committed .ogg outputs (vs. Kotlin runtime generation, vs. hand-encoded only).
3. **Update-check scheduler:** stays foreground-only per Plan 3 (vs. moving to WorkManager job at midnight).
4. **Notification opt-out:** system channel settings only, no in-app master toggle (vs. master toggle, vs. per-channel).
5. **Full-screen intent target:** dedicated `FullScreenAlertActivity` for life_safety channel (vs. deep-link to MainActivity, vs. no full-screen intent).

## Key constraints (carried forward, unchanged)

- **EAS Attention Signal and SAME header tones must NOT be reproduced** (47 CFR § 11.45). Only NWR-style 1050 Hz sustained tone for notification audio.
- **No telemetry, no analytics, no third-party trackers.**
- **No API keys, no account-gated providers.** NWS only.
- **User-Agent header required** on every NWS request. WorkManager call uses the same `NwsClient` instance as foreground — header already set via the shared Ktor `HttpClient`.
- **Minimize dependencies.** No new dependencies added — `work-runtime-ktx`, `hilt-work`, and `work-testing` were pre-wired by prior plans' scaffolding and only get activated by Plan 4's usage.
