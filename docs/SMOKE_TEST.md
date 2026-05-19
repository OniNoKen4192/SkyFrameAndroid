# Plan 1 (v0.1.0-mvp) Manual Smoke Test

This checklist verifies the MVP works on a real device. Run after a fresh `./gradlew :app:installDebug` install on a connected device or emulator.

## Pre-flight

- [ ] Update `DEBUG_SEED_ZIP` and `DEBUG_SEED_EMAIL` in [app/build.gradle.kts](../app/build.gradle.kts) to a real US ZIP code and a contact email for the NWS User-Agent.
- [ ] `./gradlew :app:installDebug`
- [ ] `adb shell am start -n com.skyframe/.MainActivity`

## Dashboard rendering

- [ ] TopBar shows the configured location name (uppercased, cyan) + current time
- [ ] Status dot is cyan (online)
- [ ] NowScreen renders (default): hero temperature (large cyan), TEMP / FEEL line, 5 metric bars (HUMIDITY, WIND, PRESSURE, DEWPOINT, VISIBILITY) each with trend arrow
- [ ] Tap hero temperature → toggles between °F and °C; tap again → toggles back; DEWPOINT value also re-converts
- [ ] Tap HOURLY in bottom nav → chart + icon row + precip bars render
- [ ] Tap OUTLOOK in bottom nav → 7-day range bars with day labels + icons + precip %
- [ ] Footer shows `LINK.<stationid>`, fetched time (`HH:MM:SS`), `T-Xs` countdown
- [ ] After ~90s, footer fetched-time updates and T-Xs resets — confirms polling works

## Bottom nav

- [ ] Selected destination shows accent-color label with underline; unselected shows dim grey
- [ ] Top border of bottom nav is a 2dp accent line (hazard-stripe equivalent)

## Alert handling (synthetic test)

Temporarily inject a synthetic alert to verify the tier-driven accent flow:

1. In [app/src/main/kotlin/com/skyframe/data/nws/AlertNormalizer.kt](../app/src/main/kotlin/com/skyframe/data/nws/AlertNormalizer.kt), add this BEFORE the `.sortedWith` call:

   ```kotlin
   val synthetic = listOf(
       com.skyframe.domain.Alert(
           id = "synthetic:test",
           event = "Tornado Warning",
           tier = com.skyframe.domain.AlertTier.TORNADO_WARNING,
           severity = com.skyframe.domain.AlertSeverity.EXTREME,
           headline = "TEST",
           description = "test alert",
           issuedAt = kotlinx.datetime.Clock.System.now(),
           effective = kotlinx.datetime.Clock.System.now(),
           expires = kotlinx.datetime.Clock.System.now().plus(kotlin.time.Duration.parse("PT30M")),
           areaDesc = "Test County",
       )
   )
   ```

   then prepend `synthetic +` to the `.map { ... }` result.

2. `./gradlew :app:installDebug` and relaunch.

Verify:
- [ ] AlertBanner appears above TopBar with red hazard stripes
- [ ] Entire UI accent shifts to tornado-warning red (TopBar location, bottom nav, hero temp, range bars, chart strokes)
- [ ] Tap × → banner dismisses; UI accent reverts to base cyan
- [ ] **REVERT** the synthetic-injection edit before tagging or committing.

## Sheet flows (v0.2.0 / Plan 2)

### AlertDetailSheet

- [ ] Inject a synthetic alert per the "Alert handling" section above
- [ ] Tap the alert event name in the banner → AlertDetailSheet opens
- [ ] Sheet shows event name in tier color (red for tornado-warning) + meta line (ISSUED ... · EXPIRES ... · COUNTY)
- [ ] Description paragraphs render with HAZARD/SOURCE/IMPACT prefixes in the alert's tier color
- [ ] Swipe down dismisses the sheet
- [ ] System back dismisses the sheet
- [ ] Tap outside (on scrim) dismisses the sheet
- [ ] Tap [x] in title bar dismisses the sheet

### ForecastNarrativeSheet

- [ ] Tap ▶ next to "TEMP / FEEL" on NowScreen → opens TODAY's forecast
- [ ] Sheet shows two sections: ┌ TODAY ┐ and ┌ TONIGHT ┐ (or the NWS-preserved names like "THIS AFTERNOON")
- [ ] Each section body shows the dayDetailedForecast / nightDetailedForecast text
- [ ] Tap ▶ next to "NEXT 12H" on HourlyScreen → also opens TODAY
- [ ] Tap any day-of-week label on OutlookScreen → opens that day's forecast
- [ ] All dismissal methods work (swipe, back, scrim, [x])

### StationOverrideSheet

- [ ] Tap "LINK.<station>" in Footer → StationOverrideSheet opens
- [ ] Currently-active mode's radio is selected (AUTO by default)
- [ ] "Fetching…" text shows briefly under both PRIMARY and SECONDARY rows
- [ ] After ~1s: each row shows station ID + observed time + temp + LIVE/STALE indicator
- [ ] Tap FORCE SECONDARY radio → APPLY button enables (accent border)
- [ ] Tap APPLY → sheet closes, footer LINK now shows `[PIN]` suffix in amber
- [ ] Open sheet again → AUTO radio + selecting it + APPLY clears the pin

### Trend arrows (Phase A)

- [ ] After ~3 successful weather poll cycles (3 × 90s = 4.5 min), NowScreen metric bars should show ▲ ▼ · arrows in the accent color
- [ ] Arrows are hidden gracefully if the /observations history endpoint fails (no `·` dots showing in confusion)

### T-Xs countdown (G.1 fix)

- [ ] Footer's `T-Xs` value should decrement once per second (not freeze for 90s)

## Onboarding + Settings (v0.3.0 / Plan 3)

### First-run onboarding

- [ ] Uninstall app, then `./gradlew :app:installDebug` (without DEBUG_SEED_ZIP/EMAIL set in [app/build.gradle.kts](../app/build.gradle.kts))
- [ ] Launch — app should open directly to SettingsScreen, NOT dashboard
- [ ] System back button has no effect (force-completion mode)
- [ ] CANCEL button is hidden
- [ ] Fill LOCATION = `53154` (or another valid ZIP), EMAIL = your contact email
- [ ] Tap SAVE → button label becomes RESOLVING… → on success, app navigates to dashboard
- [ ] Subsequent app launches go directly to dashboard (config persisted)

### Settings reopen

- [ ] On dashboard, tap the hamburger (≡) in TopBar → SettingsScreen opens (no force-completion)
- [ ] CANCEL button is now visible
- [ ] Tap CANCEL → returns to dashboard, no settings changes
- [ ] Reopen Settings, tap location-name in TopBar → also opens Settings
- [ ] Form fields are pre-populated from the persisted snapshot

### GPS autodetect button states

- [ ] Open Settings, tap `⌖ USE MY LOCATION`
- [ ] System permission dialog appears (FINE_LOCATION)
- [ ] Tap "While using the app" → button shows `⌖ REQUESTING…` briefly, then LOCATION field populates with `lat, lon` to 4 decimals
- [ ] Tap SAVE → resolves via NWS → dashboard updates with new location

- [ ] Reopen Settings, manually clear the LOCATION field, tap `⌖ USE MY LOCATION` again
- [ ] No permission dialog this time (already granted) — LOCATION populates immediately

- [ ] In device settings, revoke location permission for SkyFrame
- [ ] Reopen Settings, tap GPS button → permission dialog appears
- [ ] Deny → button still says `⌖ USE MY LOCATION` (single denial)
- [ ] Tap GPS button again → denied permanently (some Android versions) → button changes to `⌖ GPS UNAVAILABLE — open system settings`
- [ ] Tap that button → deep-links to app's settings page

### Conditional GitHub update polling

This requires a sideload install (NOT installed via Play Store). Debug builds installed via `./gradlew :app:installDebug` count as sideload.

- [ ] Open Settings → the `[ ] Check GitHub for SkyFrame updates` checkbox is visible
- [ ] Enable the checkbox → immediate poll fires; if a newer GitHub release exists than the installed `versionName`, an "Update Available" alert appears in the dashboard's AlertBanner within ~1-2 seconds
- [ ] Tap the Update Available alert in the banner → AlertDetailSheet opens with release notes (no EXPIRES segment in meta line)
- [ ] Dismiss the alert (×) → banner clears; persists across launches until the cache clears
- [ ] Disable the checkbox → on next 24h cycle, no further polling; existing cached alert remains until the version installed catches up

To verify the synthetic alert renders WITHOUT needing a real newer release:
- [ ] Temporarily edit [app/build.gradle.kts](../app/build.gradle.kts) versionName to `"0.0.1"` (pretending the installed version is older than any real release)
- [ ] `./gradlew :app:installDebug` and relaunch
- [ ] Enable the update-check checkbox → "Update Available" alert appears using the actual latest GitHub release
- [ ] REVERT the versionName edit before tagging

### Update alert (Play Store install)

This would require a real Play Store install — defer until Plan 5 ships. For now, verify the install-source gate works in code by temporarily forcing `isFromPlayStoreOverride = true` in a manual test commit (revert before shipping).

## Regression

- [ ] `./gradlew :app:testDebugUnitTest` → ~153 tests pass, 0 failures (as of v0.3.0)
- [ ] `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL
- [ ] APK at `app/build/outputs/apk/debug/app-debug.apk` exists (~13 MB)

## What this does NOT verify

These come in Plans 4–5:
- Background WorkManager alert polling + notifications (Plan 4)
- 1050 Hz NWR-style notification audio (Plan 4)
- Release signing, Play Store distribution (Plan 5)

## Known limitations in v0.1.0-mvp

- Settings screen is a Toast stub ("Settings: lands in Plan 3"). Real location config is seeded via the debug-seed mechanism. *(Removed in v0.3.0 — real SettingsScreen ships.)*
- Sunrise/sunset times come from NWS `/points` `astronomicalData`. Some NWS grids return null for these fields — the app still renders correctly using epoch-zero placeholders.
- IBM Plex Mono font is bundled (~260 KB total) and renders correctly on all Android 8+ devices.
- Glow effect on text only renders on Android 12+ (API 31+). Lower API levels render crisp text without halo — acceptable degraded experience.
- Hero icon size flex (96dp vs 72dp clear-sky) may look subtly different at very wide phone screens — matches the web's known quirk.
