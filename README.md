# SkyFrame for Android

Local, ad-free weather dashboard for a configured location, with background severe-weather notifications.

**Status:** Under active development. Not yet shipped to Play Store or GitHub releases. See [docs/superpowers/specs/2026-05-16-skyframe-android-design.md](docs/superpowers/specs/2026-05-16-skyframe-android-design.md) for the design and [docs/superpowers/plans/](docs/superpowers/plans/) for implementation plans.

The original web version of SkyFrame remains at https://github.com/OniNoKen4192/SkyFrame.

## What this is

A native Android app that fetches weather data directly from the National Weather Service (NOAA) — no API keys, no third-party services, no telemetry. Displays current conditions, hourly forecast (12+ hours), 7-day outlook, and active NWS alerts. Background WorkManager polls for severe-weather alerts and fires system notifications even when the app is closed.

## Building from source

Requires Android Studio Ladybug (2024.2.1) or newer.

```
git clone https://github.com/OniNoKen4192/SkyFrameAndroid.git
cd SkyFrameAndroid
./gradlew assembleDebug
```

Install the resulting APK from `app/build/outputs/apk/debug/app-debug.apk` to a connected device.

Installation instructions for the released APK + Play Store link will be added once Plan 5 (Distribution) is complete.
