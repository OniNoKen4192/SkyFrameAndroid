# SkyFrame for Android

Local, ad-free weather dashboard for a configured location, with planned background severe-weather notifications.

**Current tag:** [v0.1.1-mvp](https://github.com/OniNoKen4192/SkyFrameAndroid/releases/tag/v0.1.1-mvp) (Plan 1 of 5 complete) · [CHANGELOG](CHANGELOG.md) · [Roadmap](docs/ROADMAP.md) · [Project status](docs/PROJECT_STATUS.md)

The original web version of SkyFrame remains at https://github.com/OniNoKen4192/SkyFrame.

## What this is

A native Android app that fetches weather data directly from the National Weather Service (NOAA) — no API keys, no third-party services, no telemetry. Single user. The design ships a HUD-style dashboard (current conditions, 12+ hour forecast chart, 7-day outlook, active NWS alerts) and, by Plan 4, will fire background system notifications for severe weather (tornado, flash flood, severe thunderstorm) even when the app is closed.

See the [design spec](docs/superpowers/specs/2026-05-16-skyframe-android-design.md) for the full product + architecture overview.

## Status by area

- ✅ NWS data layer (5 endpoints, in-app, no server)
- ✅ HUD theming (cyan-on-black, IBM Plex Mono, tier-driven dynamic accent)
- ✅ 3 dashboard screens (Now / Hourly / Outlook) with real NWS data
- ✅ Basic alert banner with dismiss + tier-color accent flow
- ⏳ Alert detail / forecast narrative / station override sheets — [Plan 2](docs/ROADMAP.md)
- ⏳ Settings screen + onboarding + GPS + GitHub update polling — [Plan 3](docs/ROADMAP.md)
- ⏳ Background notifications (the headline native feature) — [Plan 4](docs/ROADMAP.md)
- ⏳ Play Store + signed-APK release pipeline — [Plan 5](docs/ROADMAP.md)

## Building from source

Requires Android Studio Ladybug (2024.2.1) or newer, or just the command-line Android SDK + JDK 17+.

```bash
git clone https://github.com/OniNoKen4192/SkyFrameAndroid.git
cd SkyFrameAndroid
./gradlew assembleDebug
```

Install the resulting APK from `app/build/outputs/apk/debug/app-debug.apk` to a connected device.

For Plan 1 testing without the Settings screen (lands in Plan 3), seed a location in [app/build.gradle.kts](app/build.gradle.kts) — set `DEBUG_SEED_ZIP` to a real US ZIP and `DEBUG_SEED_EMAIL` to your contact email (required for the NWS User-Agent header). Then `./gradlew installDebug` and launch.

See [docs/SMOKE_TEST.md](docs/SMOKE_TEST.md) for the full manual verification checklist.

## Running tests

```bash
./gradlew testDebugUnitTest
```

96 unit tests as of v0.1.1-mvp, ~2 seconds. Covers the data layer end-to-end (NwsClient URL construction including locale safety, all normalizers, IconMapper thresholds, TrendCalculator OLS, AlertClassifier, WeatherCache, SettingsRepository, AlertAcknowledgmentRepository, WeatherRepository state flow). Compose UI is hand-verified per `docs/SMOKE_TEST.md`.

## Distribution

APK + Play Store release pipeline lands in Plan 5. Until then, this is a source-build project.

## Project rules

Per [CLAUDE.md](CLAUDE.md): no ads, no telemetry, no third-party trackers, no API keys, no account-gated providers (NWS + Nominatim only — both keyless), minimize dependencies. EAS Attention Signal and SAME header tones must NOT be reproduced in notification audio per 47 CFR § 11.45 — only NWR-style 1050 Hz sustained tones (Plan 4).

## License

TBD before Plan 5 ships. Will likely be MIT or Apache 2.0.
