# SkyFrame Android

Native Android port of [SkyFrame](https://github.com/OniNoKen4192/SkyFrame). Local, ad-free weather dashboard for a configured location, with background severe-weather notifications. Forked at SkyFrame web v1.2.6.

Full context lives in [docs/PROJECT_SPEC.md](docs/PROJECT_SPEC.md), [docs/WEATHER_PROVIDER_RESEARCH.md](docs/WEATHER_PROVIDER_RESEARCH.md), and [docs/superpowers/specs/2026-05-16-skyframe-android-design.md](docs/superpowers/specs/2026-05-16-skyframe-android-design.md) — the source of truth for scope and approach.

The original web project is archived under `_reference/` (gitignored) for porting reference. Read but never modify.

## Hard rules

These are not preferences, they are product requirements. Do not relax them without explicit confirmation from the user.

- **No ads, no analytics, no telemetry, no third-party trackers** of any kind.
- **No API keys, no account-gated providers.** NWS (NOAA) is the sole weather data source. Nominatim (OpenStreetMap) is the sole geocoder for ZIP→lat/lon, also keyless.
- **No transmitted data beyond what is needed to fetch the forecast.** No crash reporters, no usage pings, no Firebase Analytics, no Play Services dependencies beyond what's strictly required for notifications.
- **EAS Attention Signal and SAME header tones must NOT be reproduced** in notification audio (47 CFR § 11.45). Only NWR-style 1050 Hz sustained tones are used.
- **Minimize dependencies.** Prefer a small vetted set over convenience-first pulls.

## Tech stack

- **Language:** Kotlin 2.0.x
- **UI:** Jetpack Compose (BOM 2024.11), Material 3 (overridden with HUD theme)
- **DI:** Hilt 2.52
- **HTTP:** Ktor 3.x with OkHttp engine
- **JSON:** kotlinx.serialization 1.7.x
- **Time:** kotlinx.datetime 0.6.x
- **Persistence:** Jetpack DataStore Preferences 1.1.x
- **Background:** WorkManager 2.10.x
- **Tests:** JUnit5 + MockK
- **Min SDK:** 26 (Android 8.0) — covers ~98% of devices
- **Target SDK:** 35 (Android 15)

## Project structure

```
app/                            Android module
  src/main/kotlin/com/skyframe/
    MainActivity.kt
    SkyFrameApp.kt              Hilt entry + WorkManager init
    ui/
      shell/                    DashboardScaffold (TopBar, BottomNav, AlertBanner)
      screens/                  NowScreen, HourlyScreen, OutlookScreen, SettingsScreen
      sheets/                   AlertDetailSheet, ForecastNarrativeSheet, StationOverrideSheet
      widgets/                  HudHero, HudMetricBar, HudRangeBar, HudChart, WxIcon
      onboarding/               WelcomeScreen, PermissionFlow
      nav/                      NavGraph
    theme/                      HudColors, HudAccent, HudType, hudTextGlow modifier
    data/
      nws/                      Ktor client, normalizer, icon mapping, trends, station fallback
      alerts/                   Tier classification
      cache/                    In-memory TTL cache
      settings/                 DataStore-backed SettingsRepository
      geocoding/                Nominatim client (matched to web's setup.ts)
      acknowledgments/          Dismissed + sound-acknowledged sets
      stations/                 Station override (auto / force-secondary)
      updates/                  GitHub release polling
    notifications/              Channels, builders, IDs
    background/                 WorkManager workers
    domain/                     WeatherResponse, Alert, AlertTier, etc.
    repository/                 WeatherRepository, etc.
    viewmodel/                  StateFlow-based VMs
  src/main/res/
    raw/                        notification_life_safety.ogg, notification_severe.ogg
  src/main/AndroidManifest.xml
  src/test/                     JVM unit tests
```

## Hard rules from the NWS provider

- **User-Agent header required** on every NWS request. Format: `SkyFrame/{version} ({email})`. Missing or generic User-Agent can be rate-limited or rejected.
- NWS endpoints (constructed at runtime from saved config):
  - Daily / 7-day: `/gridpoints/{office}/{gridX},{gridY}/forecast`
  - Hourly: `/gridpoints/{office}/{gridX},{gridY}/forecast/hourly`
  - Current conditions: `/stations/{stationId}/observations/latest`
  - Alerts: `/alerts/active?point={lat},{lon}`
  - Setup resolution: `/points/{lat},{lon}` → office/grid/timezone/stations

## Collaboration style

- **Educational tone.** Explain *why* on judgment calls; lay out pros/cons when there's a real choice. Brevity still applies to mechanical updates.
- **Don't add features beyond what was asked.** No speculative abstraction, no "while I'm here" cleanup.
- **Don't narrate the obvious.** Skip restatements of what the code plainly does.
- **Stack is committed.** Kotlin + Compose + Hilt + Ktor are decided. Stack-level changes warrant a brainstorm; default is "use what's there."

## Housekeeping

- Update implemented-features list in [docs/PROJECT_STATUS.md](docs/PROJECT_STATUS.md) when a feature ships (file created in Plan 5; until then, status tracking lives in plan documents).
- **PR workflow:** Feature branches named `feat/...` or `fix/...`. PRs via `gh pr create`. Merge via GitHub UI. Post-merge: `git checkout main && git pull && git branch -d <branch>`.
- **Commit convention:** Short imperative subject, multi-paragraph body, `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>` trailer.
