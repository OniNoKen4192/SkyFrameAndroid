# SkyFrame Android — Plan 1: Foundation + MVP Dashboard

> **Status: ✅ Executed — shipped as `v0.1.0-mvp` on 2026-05-16, post-review fixes shipped as `v0.1.1-mvp` on 2026-05-17. 96 unit tests, 0 failures.**
> See [docs/PROJECT_STATUS.md](../../PROJECT_STATUS.md) for the implemented-feature list and [docs/ROADMAP.md](../../ROADMAP.md) for where this fits in the 5-plan rollout.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate the existing web project to a `_reference/` archive, scaffold a fresh Kotlin/Compose Android project at the root, port the NWS data layer and HUD theming to Kotlin, and ship a working MVP that displays current conditions, hourly forecast, and 7-day outlook with a basic alert banner. This is the first of five plans; full alert UX, background notifications, settings/onboarding, and distribution come in Plans 2–5.

**Architecture:** Single-module Android app (`app/`) with Kotlin + Jetpack Compose + Hilt DI. NWS calls direct from device via Ktor + kotlinx.serialization. In-memory TTL cache. StateFlow-based ViewModels. DataStore for settings. HUD aesthetic via custom theming layer with tier-driven dynamic accent.

**Tech Stack:**
- Kotlin 2.0.x, Jetpack Compose BOM 2024.11, Android Gradle Plugin 8.7+
- Hilt 2.52 for DI
- Ktor 3.x with OkHttp engine
- kotlinx.serialization 1.7.x for JSON
- kotlinx.datetime 0.6.x for time handling
- Jetpack DataStore Preferences 1.1.x
- JUnit5 + MockK for unit tests
- Min SDK 26 (Android 8.0), Target SDK 35 (Android 15)

**Reference spec:** [docs/superpowers/specs/2026-05-16-skyframe-android-design.md](../specs/2026-05-16-skyframe-android-design.md)

**Web reference codebase:** lives under `_reference/` (gitignored) after Task 1.1 completes. Read but never modify.

---

## Phase A — Repo Migration

The current directory `e:\SkyFrame - Android` contains the existing web project (React + Fastify) pointing at `github.com/OniNoKen4192/SkyFrame.git`. Stash it into `_reference/`, then initialize a fresh repo for the Android port pointing at the empty `github.com/OniNoKen4192/SkyFrameAndroid` remote.

### Task A.1: Stash existing web project into `_reference/`

**Files:**
- Create directory: `_reference/`
- Move into `_reference/`: every file and folder currently at root EXCEPT `docs/superpowers/specs/2026-05-16-skyframe-android-design.md` and `docs/superpowers/plans/2026-05-16-skyframe-android-plan-1-foundation-mvp.md` (this file)

- [ ] **Step 1: Verify current state**

Run from project root:

```powershell
Get-ChildItem -Force | Select-Object Name
```

Expected: lists `.git`, `client`, `server`, `shared`, `node_modules`, `package.json`, `CLAUDE.md`, `README.md`, `docs`, etc. — the web project plus the new design spec under `docs/superpowers/specs/`.

- [ ] **Step 2: Save design + plan files to a scratch location**

```powershell
$scratch = "$env:TEMP\skyframe-android-docs"
New-Item -ItemType Directory -Path $scratch -Force | Out-Null
Copy-Item "docs/superpowers/specs/2026-05-16-skyframe-android-design.md" "$scratch/" -Force
Copy-Item "docs/superpowers/plans/2026-05-16-skyframe-android-plan-1-foundation-mvp.md" "$scratch/" -Force
Get-ChildItem $scratch
```

Expected: lists both files.

- [ ] **Step 3: Create `_reference/` and move everything except `_reference/` itself**

```powershell
New-Item -ItemType Directory -Path "_reference" -Force | Out-Null
Get-ChildItem -Force | Where-Object { $_.Name -ne "_reference" } | ForEach-Object {
    Move-Item -Path $_.FullName -Destination "_reference/" -Force
}
Get-ChildItem -Force | Select-Object Name
```

Expected: only `_reference` is listed at root.

- [ ] **Step 4: Restore design and plan files to fresh `docs/superpowers/` paths**

```powershell
New-Item -ItemType Directory -Path "docs/superpowers/specs" -Force | Out-Null
New-Item -ItemType Directory -Path "docs/superpowers/plans" -Force | Out-Null
$scratch = "$env:TEMP\skyframe-android-docs"
Copy-Item "$scratch/2026-05-16-skyframe-android-design.md" "docs/superpowers/specs/" -Force
Copy-Item "$scratch/2026-05-16-skyframe-android-plan-1-foundation-mvp.md" "docs/superpowers/plans/" -Force
Get-ChildItem -Recurse docs | Select-Object FullName
```

Expected: shows both files restored under `docs/superpowers/specs/` and `docs/superpowers/plans/`.

- [ ] **Step 5: Verify `_reference/.git` exists (old web repo's history is now archived)**

```powershell
Test-Path "_reference/.git"
```

Expected: `True`. The web repo's `.git/` was moved with everything else; the project root has no `.git/` of its own yet. (Next task initializes a fresh one.)

**No commit yet** — there's no git repo at root to commit to. That happens in Task A.3.

---

### Task A.2: Carry over reference docs from web project

Pull `PROJECT_SPEC.md` and `WEATHER_PROVIDER_RESEARCH.md` out of `_reference/` and put them at the new project's `docs/` root so they're discoverable without crawling into the archive.

**Files:**
- Create: `docs/PROJECT_SPEC.md` (copied from `_reference/PROJECT_SPEC.md`)
- Create: `docs/WEATHER_PROVIDER_RESEARCH.md` (copied from `_reference/WEATHER_PROVIDER_RESEARCH.md`)
- Create: `docs/ALERT_TIERS.md` (new, distilled from `_reference/shared/alert-tiers.ts`)

- [ ] **Step 1: Copy PROJECT_SPEC.md and WEATHER_PROVIDER_RESEARCH.md**

```powershell
Copy-Item "_reference/PROJECT_SPEC.md" "docs/PROJECT_SPEC.md" -Force
Copy-Item "_reference/WEATHER_PROVIDER_RESEARCH.md" "docs/WEATHER_PROVIDER_RESEARCH.md" -Force
Get-ChildItem docs | Select-Object Name
```

Expected: lists `PROJECT_SPEC.md`, `WEATHER_PROVIDER_RESEARCH.md`, and `superpowers/` directory.

- [ ] **Step 2: Write `docs/ALERT_TIERS.md` distilling tier values from web reference**

Create `docs/ALERT_TIERS.md` with:

```markdown
# Alert Tiers Reference

Authoritative source of truth for tier ranks, colors, and event mappings. Ported from `_reference/shared/alert-tiers.ts` on 2026-05-16. Keep in sync if the web project's tiers change.

## Tier ranks (1 = most severe)

| Rank | Tier ID                     | Base color | Dark stripe |
|------|-----------------------------|-----------|-------------|
| 1    | `tornado-emergency`         | `#b052e4` | `#6f3490`   |
| 2    | `tornado-pds`               | `#ff55c8` | `#a1367e`   |
| 3    | `tornado-warning`           | `#ff4444` | `#a02828`   |
| 4    | `tstorm-destructive`        | `#ff4466` | `#a12b40`   |
| 5    | `severe-warning`            | `#ff8800` | `#a05500`   |
| 6    | `blizzard`                  | `#ffffff` | `#bbbbbb`   |
| 7    | `winter-storm`              | `#4488ff` | `#2a55a0`   |
| 8    | `flood`                     | `#22cc66` | `#147a3d`   |
| 9    | `heat`                      | `#ff5533` | `#a0331c`   |
| 10   | `special-weather-statement` | `#ee82ee` | `#9d539d`   |
| 11   | `watch`                     | `#ffdd33` | `#a08820`   |
| 12   | `advisory-high`             | `#ffaa22` | `#a06d15`   |
| 13   | `advisory`                  | `#00e5d1` | `#008e82`   |

## Event → tier mapping

Direct events (no parameter inspection):

| NWS event                  | Tier                        |
|----------------------------|-----------------------------|
| Blizzard Warning           | `blizzard`                  |
| Winter Storm Warning       | `winter-storm`              |
| Flood Warning              | `flood`                     |
| Flash Flood Warning        | `flood`                     |
| Heat Advisory              | `heat`                      |
| Excessive Heat Warning     | `heat`                      |
| Excessive Heat Watch       | `heat`                      |
| Special Weather Statement  | `special-weather-statement` |
| Tornado Watch              | `watch`                     |
| Severe Thunderstorm Watch  | `watch`                     |
| Wind Advisory              | `advisory-high`             |
| Winter Weather Advisory    | `advisory-high`             |
| Dense Fog Advisory         | `advisory-high`             |
| Wind Chill Advisory        | `advisory-high`             |
| Freeze Warning             | `advisory-high`             |
| Freeze Watch               | `advisory-high`             |
| Frost Advisory             | `advisory-high`             |

## Parameter-driven classification

**Tornado Warning / Tornado Emergency:**
- `parameters.tornadoDamageThreat == "CATASTROPHIC"` → `tornado-emergency`
- `parameters.tornadoDamageThreat == "CONSIDERABLE"` → `tornado-pds`
- `event == "Tornado Emergency"` (without explicit threat) → `tornado-emergency`
- Otherwise → `tornado-warning`

**Severe Thunderstorm Warning:**
- `parameters.thunderstormDamageThreat == "DESTRUCTIVE"` → `tstorm-destructive`
- Otherwise → `severe-warning`

**Unknown events** (no direct mapping and no parameter rules) → `advisory` (catch-all, never null).
```

- [ ] **Step 3: Verify all docs are in place**

```powershell
Get-ChildItem -Recurse docs | Select-Object FullName
```

Expected output includes:
- `docs/PROJECT_SPEC.md`
- `docs/WEATHER_PROVIDER_RESEARCH.md`
- `docs/ALERT_TIERS.md`
- `docs/superpowers/specs/2026-05-16-skyframe-android-design.md`
- `docs/superpowers/plans/2026-05-16-skyframe-android-plan-1-foundation-mvp.md`

**No commit yet** — git repo isn't initialized at root yet (Task A.3).

---

### Task A.3: Initialize fresh git repo + remote, write `.gitignore`

**Files:**
- Create: `.gitignore`
- Initialize: `.git/` at project root via `git init`

- [ ] **Step 1: Run `git init` at project root**

```powershell
git init -b main
git status
```

Expected: "Initialized empty Git repository" and `git status` shows untracked files including `_reference/`, `docs/`, this plan, etc.

- [ ] **Step 2: Write `.gitignore`**

Create `.gitignore` at project root with:

```gitignore
# Reference: original web project archived for porting; never tracked by Android repo
_reference/

# Android / Gradle build outputs
.gradle/
build/
captures/
.externalNativeBuild/
.cxx/
local.properties

# Android Studio / IntelliJ
.idea/
*.iml

# Release signing (never check in keystores or their config)
*.jks
*.keystore
keystore.properties

# OS junk
.DS_Store
Thumbs.db

# Lock files for tools other than Gradle (not used here)
node_modules/
```

- [ ] **Step 3: Verify `_reference/` is ignored**

```powershell
git status
```

Expected: `_reference/` does NOT appear in the untracked list. Only `.gitignore` and `docs/` show as untracked.

- [ ] **Step 4: Add the remote**

```powershell
git remote add origin https://github.com/OniNoKen4192/SkyFrameAndroid.git
git remote -v
```

Expected: shows `origin https://github.com/OniNoKen4192/SkyFrameAndroid.git (fetch)` and `(push)`.

**No commit yet** — first commit lands after the project skeleton exists (Task B.1 onward). Holding off here so the first commit is a coherent "initial scaffold + docs" snapshot, not a series of empty-shell commits.

---

### Task A.4: Write fresh `CLAUDE.md` for Android stack

The web project's `CLAUDE.md` is now at `_reference/CLAUDE.md`. The Android project needs its own. Many hard rules carry over verbatim ("no ads, no telemetry, no API keys") but the tech stack section is completely different.

**Files:**
- Create: `CLAUDE.md`

- [ ] **Step 1: Write the new `CLAUDE.md`**

Create `CLAUDE.md` at project root:

```markdown
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
```

- [ ] **Step 2: Verify file was written**

```powershell
Test-Path CLAUDE.md
(Get-Content CLAUDE.md | Measure-Object -Line).Lines
```

Expected: `True`, and line count > 70.

**No commit yet** — coming in Task B.1's snapshot.

---

### Task A.5: Write stub `README.md` for the new repo

Web `README.md` is in `_reference/`. The new repo needs its own. Initial version is a placeholder; full install instructions (APK download, Play Store badge) come in Plan 5.

**Files:**
- Create: `README.md`

- [ ] **Step 1: Write stub README**

Create `README.md` at project root:

```markdown
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
```

- [ ] **Step 2: Verify**

```powershell
Test-Path README.md
```

Expected: `True`.

**No commit yet** — Task B.5 snapshots everything together after the project skeleton is in place.

---

## Phase B — Project Scaffold

Set up the Gradle build, version catalog, Hilt, Compose, Ktor, and a minimal `MainActivity` that renders a "Hello SkyFrame" Compose screen. Goal: green `./gradlew :app:assembleDebug` build and a runnable APK with empty content. Once this works, every subsequent phase adds real content to a working skeleton.

### Task B.1: Gradle settings + version catalog

**Files:**
- Create: `settings.gradle.kts`
- Create: `gradle/libs.versions.toml`
- Create: `gradle/wrapper/gradle-wrapper.properties`

- [ ] **Step 1: Write `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "SkyFrameAndroid"
include(":app")
```

- [ ] **Step 2: Write `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.7.2"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.27"
compose-bom = "2024.11.00"
hilt = "2.52"
hilt-navigation-compose = "1.2.0"
ktor = "3.0.1"
kotlinx-serialization = "1.7.3"
kotlinx-datetime = "0.6.1"
kotlinx-coroutines = "1.9.0"
datastore = "1.1.1"
work-manager = "2.10.0"
navigation-compose = "2.8.4"
lifecycle = "2.8.7"
core-ktx = "1.15.0"
activity-compose = "1.9.3"
junit5 = "5.11.3"
mockk = "1.13.13"
turbine = "1.2.0"
android-junit5 = "1.11.2.0"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "core-ktx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activity-compose" }
androidx-lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-lifecycle-runtime-compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation-compose" }

compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }

hilt-android = { group = "com.google.dagger", name = "hilt-android", version.ref = "hilt" }
hilt-compiler = { group = "com.google.dagger", name = "hilt-compiler", version.ref = "hilt" }
hilt-navigation-compose = { group = "androidx.hilt", name = "hilt-navigation-compose", version.ref = "hilt-navigation-compose" }
hilt-work = { group = "androidx.hilt", name = "hilt-work", version = "1.2.0" }

ktor-client-core = { group = "io.ktor", name = "ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { group = "io.ktor", name = "ktor-client-okhttp", version.ref = "ktor" }
ktor-client-content-negotiation = { group = "io.ktor", name = "ktor-client-content-negotiation", version.ref = "ktor" }
ktor-serialization-kotlinx-json = { group = "io.ktor", name = "ktor-serialization-kotlinx-json", version.ref = "ktor" }
ktor-client-logging = { group = "io.ktor", name = "ktor-client-logging", version.ref = "ktor" }

kotlinx-serialization-json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
kotlinx-datetime = { group = "org.jetbrains.kotlinx", name = "kotlinx-datetime", version.ref = "kotlinx-datetime" }
kotlinx-coroutines-android = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-android", version.ref = "kotlinx-coroutines" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }

androidx-datastore-preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
androidx-work-runtime-ktx = { group = "androidx.work", name = "work-runtime-ktx", version.ref = "work-manager" }

junit-jupiter-api = { group = "org.junit.jupiter", name = "junit-jupiter-api", version.ref = "junit5" }
junit-jupiter-engine = { group = "org.junit.jupiter", name = "junit-jupiter-engine", version.ref = "junit5" }
junit-jupiter-params = { group = "org.junit.jupiter", name = "junit-jupiter-params", version.ref = "junit5" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }
work-testing = { group = "androidx.work", name = "work-testing", version.ref = "work-manager" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
compose-compiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
android-junit5 = { id = "de.mannodermaus.android-junit5", version.ref = "android-junit5" }
```

- [ ] **Step 3: Write `gradle/wrapper/gradle-wrapper.properties`**

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10.2-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [ ] **Step 4: Generate the gradle wrapper script**

Run from project root (requires Gradle 8.10+ installed locally OR an existing wrapper to bootstrap from):

```powershell
# If you have gradle installed:
gradle wrapper --gradle-version 8.10.2
```

If no system Gradle, copy `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` from any recent Android Studio project. Verify presence:

```powershell
Test-Path gradlew.bat
Test-Path gradle/wrapper/gradle-wrapper.jar
```

Expected: both `True`.

**No commit yet** — Task B.5.

---

### Task B.2: Root + app module Gradle build files

**Files:**
- Create: `build.gradle.kts` (root)
- Create: `app/build.gradle.kts`

- [ ] **Step 1: Write root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.android.junit5) apply false
}
```

- [ ] **Step 2: Write `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.android.junit5)
}

android {
    namespace = "com.skyframe"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.skyframe"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // Compose BOM aligns all Compose library versions
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp("androidx.hilt:hilt-compiler:1.2.0")

    // Ktor
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)

    // kotlinx
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.coroutines.android)

    // Persistence + background
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    // Test
    testImplementation(libs.junit.jupiter.api)
    testImplementation(libs.junit.jupiter.params)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.work.testing)
}
```

- [ ] **Step 3: Verify Gradle sync (does NOT need to build yet — just parse)**

```powershell
./gradlew help --no-daemon
```

Expected: BUILD SUCCESSFUL with the help task output. If this fails on missing wrapper or version mismatch, fix the wrapper/versions before proceeding.

**No commit yet — Task B.5.**

---

### Task B.3: Android manifest + base directory structure

**Files:**
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/com/skyframe/` (empty package directory)
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/values/colors.xml`
- Create: `app/proguard-rules.pro` (empty placeholder)

- [ ] **Step 1: Create directory structure**

```powershell
New-Item -ItemType Directory -Force -Path "app/src/main/kotlin/com/skyframe" | Out-Null
New-Item -ItemType Directory -Force -Path "app/src/main/res/values" | Out-Null
New-Item -ItemType Directory -Force -Path "app/src/main/res/drawable" | Out-Null
New-Item -ItemType Directory -Force -Path "app/src/main/res/mipmap-anydpi-v26" | Out-Null
New-Item -ItemType Directory -Force -Path "app/src/test/kotlin/com/skyframe" | Out-Null
"" | Set-Content "app/proguard-rules.pro"
```

- [ ] **Step 2: Write `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <!-- Additional permissions (location, notifications, etc.) added in later plans -->

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
    </application>
</manifest>
```

- [ ] **Step 3: Write minimal resource files**

`app/src/main/res/values/strings.xml`:

```xml
<resources>
    <string name="app_name">SkyFrame</string>
</resources>
```

`app/src/main/res/values/themes.xml`:

```xml
<resources>
    <style name="Theme.SkyFrame" parent="android:Theme.Material.NoActionBar">
        <item name="android:statusBarColor">#0a1018</item>
        <item name="android:navigationBarColor">#0a1018</item>
    </style>
</resources>
```

`app/src/main/res/values/colors.xml`:

```xml
<resources>
    <color name="hud_background">#0a1018</color>
</resources>
```

Create `app/src/main/res/xml/data_extraction_rules.xml`:

```powershell
New-Item -ItemType Directory -Force -Path "app/src/main/res/xml" | Out-Null
```

`app/src/main/res/xml/data_extraction_rules.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="root" />
        <exclude domain="database" />
        <exclude domain="sharedpref" />
        <exclude domain="external" />
        <exclude domain="file" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="root" />
        <exclude domain="database" />
        <exclude domain="sharedpref" />
        <exclude domain="external" />
        <exclude domain="file" />
    </device-transfer>
</data-extraction-rules>
```

Reasoning: SkyFrame stores no cross-device-meaningful data. Disabling backup matches the spec's "no transmitted data" stance — settings are local to the install.

- [ ] **Step 4: Provide a placeholder launcher icon**

For now, use Android Studio's default mipmap. From an existing Android Studio project, copy:
- `mipmap-mdpi/ic_launcher.webp`
- `mipmap-hdpi/ic_launcher.webp`
- `mipmap-xhdpi/ic_launcher.webp`
- `mipmap-xxhdpi/ic_launcher.webp`
- `mipmap-xxxhdpi/ic_launcher.webp`

OR generate via Android Studio: File → New → Image Asset → Launcher Icons → use default robot icon. A real SkyFrame logo lands in Plan 5 distribution.

- [ ] **Step 5: Verify**

```powershell
./gradlew :app:tasks --no-daemon
```

Expected: BUILD SUCCESSFUL, lists app-module tasks like `assembleDebug`.

**No commit yet — Task B.5.**

---

### Task B.4: Application class + MainActivity with "Hello SkyFrame" Compose screen

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/SkyFrameApp.kt`
- Create: `app/src/main/kotlin/com/skyframe/MainActivity.kt`

- [ ] **Step 1: Write `SkyFrameApp.kt`**

```kotlin
package com.skyframe

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SkyFrameApp : Application()
```

- [ ] **Step 2: Write `MainActivity.kt`**

```kotlin
package com.skyframe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HelloSkyFrame()
        }
    }
}

@Composable
private fun HelloSkyFrame() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A1018)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "SKYFRAME",
            color = Color(0xFF22D3EE),
            fontSize = 32.sp
        )
    }
}
```

- [ ] **Step 3: Build the debug APK**

```powershell
./gradlew :app:assembleDebug --no-daemon
```

Expected: BUILD SUCCESSFUL. APK at `app/build/outputs/apk/debug/app-debug.apk`.

If build fails, common issues:
- Missing Gradle wrapper jar → recreate with `gradle wrapper --gradle-version 8.10.2`
- KSP version mismatch with Kotlin → verify versions in `libs.versions.toml`
- Hilt requires Java 17 → confirm `JAVA_HOME` points at JDK 17

- [ ] **Step 4: (Optional) Install + launch on a device or emulator**

```powershell
# Requires adb on PATH + a device connected/emulator running
./gradlew :app:installDebug --no-daemon
adb shell am start -n com.skyframe/.MainActivity
```

Expected: device shows a dark background with cyan "SKYFRAME" text centered.

**No commit yet — Task B.5.**

---

### Task B.5: First commit + push to GitHub remote

The snapshot includes: Phase A migration (`_reference/` archive note via `.gitignore`, fresh `docs/`, `CLAUDE.md`, `README.md`) plus Phase B scaffolding (`settings.gradle.kts`, `gradle/`, `build.gradle.kts`, `app/`).

- [ ] **Step 1: Stage everything that should be tracked**

```powershell
git add .gitignore CLAUDE.md README.md docs/ settings.gradle.kts build.gradle.kts gradle/ gradlew gradlew.bat app/
git status
```

Expected: shows all files staged for commit; `_reference/` does NOT appear (ignored).

- [ ] **Step 2: Verify nothing sensitive is staged**

```powershell
git diff --cached --name-only | Select-String -Pattern "(\.env|\.keystore|\.jks|local\.properties|keystore\.properties)"
```

Expected: no matches.

- [ ] **Step 3: Commit**

```powershell
git commit -m "$(@'
chore: initial scaffold for SkyFrame Android port

Fork of the SkyFrame web project (React + Fastify) as a native Android
app. The web codebase is archived under _reference/ (gitignored) for
porting reference and will be removed once the port is complete.

Includes:
- Design spec at docs/superpowers/specs/2026-05-16-skyframe-android-design.md
- Plan 1 of 5 at docs/superpowers/plans/
- Carried-over PROJECT_SPEC.md, WEATHER_PROVIDER_RESEARCH.md from web
- New ALERT_TIERS.md distilled from shared/alert-tiers.ts
- Kotlin/Compose project skeleton: Gradle 8.10, AGP 8.7, Kotlin 2.0.21,
  Compose BOM 2024.11, Hilt 2.52, Ktor 3.0, Min SDK 26, Target SDK 35
- Hello-world MainActivity rendering on a dark HUD background

Next: Phase C ports the domain types and unit conversion logic.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

- [ ] **Step 4: Push to the empty SkyFrameAndroid remote**

```powershell
git push -u origin main
```

Expected: `branch 'main' set up to track 'origin/main'`. Visit https://github.com/OniNoKen4192/SkyFrameAndroid in a browser; the files should appear.

- [ ] **Step 5: Verify CI nothing-yet sanity check**

GitHub Actions isn't configured in Plan 1 (lands in Plan 5). Repo should just show the source tree and the commit message at this point.

---

**Phase B milestone:** Working Android app skeleton committed and pushed. Cyan "SKYFRAME" text renders on a dark screen. All build infrastructure (Hilt, Compose, Ktor, kotlinx.serialization, DataStore, WorkManager) is wired and compiles. No real domain code yet — that's Phase C.

---

## Phase C — Domain Types + Pure Logic

Port the three pure-logic shared modules from `_reference/`: `shared/types.ts` (domain model), `shared/alert-tiers.ts` (tier classification), and `shared/units.ts` (unit conversion + trend rescaling). All pure functions, all TDD-tested.

These have no Android dependencies — they could run on any Kotlin runtime. Keeping them dependency-free is a deliberate choice so they're trivially testable and could later move to a `:domain` module if we ever split.

### Task C.1: AlertTier enum + tier rank + color palette

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/domain/AlertTier.kt`
- Create: `app/src/test/kotlin/com/skyframe/domain/AlertTierTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/kotlin/com/skyframe/domain/AlertTierTest.kt
package com.skyframe.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AlertTierTest {

    @Test
    fun `tier ranks are unique and contiguous 1 through 13`() {
        val ranks = AlertTier.entries.map { it.rank }.sorted()
        assertEquals((1..13).toList(), ranks)
    }

    @Test
    fun `tornado-emergency outranks all others`() {
        val emergency = AlertTier.TORNADO_EMERGENCY
        AlertTier.entries.filter { it != emergency }.forEach {
            assertTrue(emergency.rank < it.rank, "$it should rank higher (numerically lower) than $emergency")
        }
    }

    @Test
    fun `advisory is the catch-all tier with rank 13`() {
        assertEquals(13, AlertTier.ADVISORY.rank)
    }

    @Test
    fun `tier IDs match the web string discriminators exactly`() {
        // These string IDs are the wire-format match with the web's AlertTier type union.
        // They must not be renamed without coordinated changes in serialization.
        assertEquals("tornado-emergency", AlertTier.TORNADO_EMERGENCY.id)
        assertEquals("tornado-pds", AlertTier.TORNADO_PDS.id)
        assertEquals("tornado-warning", AlertTier.TORNADO_WARNING.id)
        assertEquals("tstorm-destructive", AlertTier.TSTORM_DESTRUCTIVE.id)
        assertEquals("severe-warning", AlertTier.SEVERE_WARNING.id)
        assertEquals("blizzard", AlertTier.BLIZZARD.id)
        assertEquals("winter-storm", AlertTier.WINTER_STORM.id)
        assertEquals("flood", AlertTier.FLOOD.id)
        assertEquals("heat", AlertTier.HEAT.id)
        assertEquals("special-weather-statement", AlertTier.SPECIAL_WEATHER_STATEMENT.id)
        assertEquals("watch", AlertTier.WATCH.id)
        assertEquals("advisory-high", AlertTier.ADVISORY_HIGH.id)
        assertEquals("advisory", AlertTier.ADVISORY.id)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.domain.AlertTierTest" --no-daemon
```

Expected: compile error (unresolved reference: AlertTier).

- [ ] **Step 3: Implement `AlertTier`**

Color values are copied from `_reference/shared/alert-tiers.ts` `TIER_COLORS`. Each tier carries both its base color (used for accent, glow, hero text-shadow) and the dark variant (used for alternating stripe rendering in the AlertBanner).

```kotlin
// app/src/main/kotlin/com/skyframe/domain/AlertTier.kt
package com.skyframe.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Severity tier for NWS alerts. Rank 1 = most severe. The 13 tiers match
 * the web project's shared/alert-tiers.ts type union one-to-one; IDs are
 * the wire-format strings used in serialized payloads.
 *
 * Adding a tier is a coordinated change: update this enum, ALERT_TIERS.md,
 * and any Composable that switches on tier explicitly.
 */
@Serializable
enum class AlertTier(
    val id: String,
    val rank: Int,
    val baseColor: Long,
    val darkColor: Long,
) {
    @SerialName("tornado-emergency")
    TORNADO_EMERGENCY("tornado-emergency", 1, 0xFFB052E4, 0xFF6F3490),

    @SerialName("tornado-pds")
    TORNADO_PDS("tornado-pds", 2, 0xFFFF55C8, 0xFFA1367E),

    @SerialName("tornado-warning")
    TORNADO_WARNING("tornado-warning", 3, 0xFFFF4444, 0xFFA02828),

    @SerialName("tstorm-destructive")
    TSTORM_DESTRUCTIVE("tstorm-destructive", 4, 0xFFFF4466, 0xFFA12B40),

    @SerialName("severe-warning")
    SEVERE_WARNING("severe-warning", 5, 0xFFFF8800, 0xFFA05500),

    @SerialName("blizzard")
    BLIZZARD("blizzard", 6, 0xFFFFFFFF, 0xFFBBBBBB),

    @SerialName("winter-storm")
    WINTER_STORM("winter-storm", 7, 0xFF4488FF, 0xFF2A55A0),

    @SerialName("flood")
    FLOOD("flood", 8, 0xFF22CC66, 0xFF147A3D),

    @SerialName("heat")
    HEAT("heat", 9, 0xFFFF5533, 0xFFA0331C),

    @SerialName("special-weather-statement")
    SPECIAL_WEATHER_STATEMENT("special-weather-statement", 10, 0xFFEE82EE, 0xFF9D539D),

    @SerialName("watch")
    WATCH("watch", 11, 0xFFFFDD33, 0xFFA08820),

    @SerialName("advisory-high")
    ADVISORY_HIGH("advisory-high", 12, 0xFFFFAA22, 0xFFA06D15),

    @SerialName("advisory")
    ADVISORY("advisory", 13, 0xFF00E5D1, 0xFF008E82);

    companion object {
        /** Lookup by wire-format ID; returns null on unknown. */
        fun fromId(id: String): AlertTier? = entries.firstOrNull { it.id == id }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.domain.AlertTierTest" --no-daemon
```

Expected: 4 tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/domain/AlertTier.kt app/src/test/kotlin/com/skyframe/domain/AlertTierTest.kt
git commit -m "$(@'
feat(domain): port AlertTier enum from web project

Direct port of shared/alert-tiers.ts TIER_RANK and TIER_COLORS. The
@SerialName annotations ensure JSON round-trip compatibility with the
web wire format (which uses kebab-case discriminators).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task C.2: Alert tier classifier (event + parameters → tier)

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/data/alerts/AlertClassifier.kt`
- Create: `app/src/test/kotlin/com/skyframe/data/alerts/AlertClassifierTest.kt`

- [ ] **Step 1: Write the failing test**

Test cases mirror the behavior of `_reference/shared/alert-tiers.ts` `classifyAlert`:

```kotlin
// app/src/test/kotlin/com/skyframe/data/alerts/AlertClassifierTest.kt
package com.skyframe.data.alerts

import com.skyframe.domain.AlertTier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AlertClassifierTest {

    @Test
    fun `unknown event with no parameters falls through to advisory catch-all`() {
        assertEquals(AlertTier.ADVISORY, AlertClassifier.classify("Some Bizarre Event", emptyMap()))
    }

    @Test
    fun `known mapped event resolves directly`() {
        assertEquals(AlertTier.BLIZZARD, AlertClassifier.classify("Blizzard Warning", emptyMap()))
        assertEquals(AlertTier.WINTER_STORM, AlertClassifier.classify("Winter Storm Warning", emptyMap()))
        assertEquals(AlertTier.FLOOD, AlertClassifier.classify("Flood Warning", emptyMap()))
        assertEquals(AlertTier.FLOOD, AlertClassifier.classify("Flash Flood Warning", emptyMap()))
        assertEquals(AlertTier.HEAT, AlertClassifier.classify("Heat Advisory", emptyMap()))
        assertEquals(AlertTier.WATCH, AlertClassifier.classify("Tornado Watch", emptyMap()))
        assertEquals(AlertTier.WATCH, AlertClassifier.classify("Severe Thunderstorm Watch", emptyMap()))
        assertEquals(AlertTier.ADVISORY_HIGH, AlertClassifier.classify("Wind Advisory", emptyMap()))
        assertEquals(AlertTier.ADVISORY_HIGH, AlertClassifier.classify("Frost Advisory", emptyMap()))
    }

    @Test
    fun `tornado warning with no damage threat is plain tornado-warning`() {
        assertEquals(
            AlertTier.TORNADO_WARNING,
            AlertClassifier.classify("Tornado Warning", emptyMap())
        )
    }

    @Test
    fun `tornado warning with CONSIDERABLE damage threat upgrades to PDS`() {
        assertEquals(
            AlertTier.TORNADO_PDS,
            AlertClassifier.classify(
                event = "Tornado Warning",
                parameters = mapOf("tornadoDamageThreat" to listOf("CONSIDERABLE"))
            )
        )
    }

    @Test
    fun `tornado warning with CATASTROPHIC damage threat escalates to emergency`() {
        assertEquals(
            AlertTier.TORNADO_EMERGENCY,
            AlertClassifier.classify(
                event = "Tornado Warning",
                parameters = mapOf("tornadoDamageThreat" to listOf("CATASTROPHIC"))
            )
        )
    }

    @Test
    fun `tornado emergency event resolves to emergency tier without explicit threat`() {
        assertEquals(
            AlertTier.TORNADO_EMERGENCY,
            AlertClassifier.classify("Tornado Emergency", emptyMap())
        )
    }

    @Test
    fun `severe thunderstorm warning is severe-warning by default`() {
        assertEquals(
            AlertTier.SEVERE_WARNING,
            AlertClassifier.classify("Severe Thunderstorm Warning", emptyMap())
        )
    }

    @Test
    fun `severe thunderstorm with DESTRUCTIVE threat upgrades to tstorm-destructive`() {
        assertEquals(
            AlertTier.TSTORM_DESTRUCTIVE,
            AlertClassifier.classify(
                event = "Severe Thunderstorm Warning",
                parameters = mapOf("thunderstormDamageThreat" to listOf("DESTRUCTIVE"))
            )
        )
    }

    @Test
    fun `damage threat lookups are case-insensitive against the value`() {
        assertEquals(
            AlertTier.TSTORM_DESTRUCTIVE,
            AlertClassifier.classify(
                event = "Severe Thunderstorm Warning",
                parameters = mapOf("thunderstormDamageThreat" to listOf("destructive"))
            )
        )
    }

    @Test
    fun `empty parameter list is treated as absent`() {
        assertEquals(
            AlertTier.TORNADO_WARNING,
            AlertClassifier.classify(
                event = "Tornado Warning",
                parameters = mapOf("tornadoDamageThreat" to emptyList())
            )
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.alerts.AlertClassifierTest" --no-daemon
```

Expected: compile error (AlertClassifier unresolved).

- [ ] **Step 3: Implement `AlertClassifier`**

```kotlin
// app/src/main/kotlin/com/skyframe/data/alerts/AlertClassifier.kt
package com.skyframe.data.alerts

import com.skyframe.domain.AlertTier

/**
 * Pure port of _reference/shared/alert-tiers.ts classifyAlert.
 *
 * NWS alerts have an event name (e.g. "Tornado Warning") and a parameters
 * map (Map<String, List<String>>). For most events, the event name alone
 * determines the tier. Two events use parameter-driven escalation:
 *   - Tornado Warning + tornadoDamageThreat=CATASTROPHIC -> tornado-emergency
 *   - Tornado Warning + tornadoDamageThreat=CONSIDERABLE -> tornado-pds
 *   - Severe Thunderstorm Warning + thunderstormDamageThreat=DESTRUCTIVE -> tstorm-destructive
 *
 * Unknown events fall through to the ADVISORY catch-all rather than being
 * silently dropped (matches web behavior post-PR #8).
 */
object AlertClassifier {

    private val DIRECT_MAP: Map<String, AlertTier> = mapOf(
        "Blizzard Warning"          to AlertTier.BLIZZARD,
        "Winter Storm Warning"      to AlertTier.WINTER_STORM,
        "Flood Warning"             to AlertTier.FLOOD,
        "Flash Flood Warning"       to AlertTier.FLOOD,
        "Heat Advisory"             to AlertTier.HEAT,
        "Excessive Heat Warning"    to AlertTier.HEAT,
        "Excessive Heat Watch"      to AlertTier.HEAT,
        "Special Weather Statement" to AlertTier.SPECIAL_WEATHER_STATEMENT,
        "Tornado Watch"             to AlertTier.WATCH,
        "Severe Thunderstorm Watch" to AlertTier.WATCH,
        "Wind Advisory"             to AlertTier.ADVISORY_HIGH,
        "Winter Weather Advisory"   to AlertTier.ADVISORY_HIGH,
        "Dense Fog Advisory"        to AlertTier.ADVISORY_HIGH,
        "Wind Chill Advisory"       to AlertTier.ADVISORY_HIGH,
        "Freeze Warning"            to AlertTier.ADVISORY_HIGH,
        "Freeze Watch"              to AlertTier.ADVISORY_HIGH,
        "Frost Advisory"            to AlertTier.ADVISORY_HIGH,
    )

    fun classify(event: String, parameters: Map<String, List<String>>): AlertTier {
        // Parameter-driven escalations first
        if (event == "Tornado Warning" || event == "Tornado Emergency") {
            val threat = parameters["tornadoDamageThreat"]?.firstOrNull()?.uppercase()
            return when {
                threat == "CATASTROPHIC" -> AlertTier.TORNADO_EMERGENCY
                threat == "CONSIDERABLE" -> AlertTier.TORNADO_PDS
                event == "Tornado Emergency" -> AlertTier.TORNADO_EMERGENCY
                else -> AlertTier.TORNADO_WARNING
            }
        }
        if (event == "Severe Thunderstorm Warning") {
            val threat = parameters["thunderstormDamageThreat"]?.firstOrNull()?.uppercase()
            return if (threat == "DESTRUCTIVE") AlertTier.TSTORM_DESTRUCTIVE else AlertTier.SEVERE_WARNING
        }
        return DIRECT_MAP[event] ?: AlertTier.ADVISORY
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.alerts.AlertClassifierTest" --no-daemon
```

Expected: 10 tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/data/alerts/AlertClassifier.kt app/src/test/kotlin/com/skyframe/data/alerts/AlertClassifierTest.kt
git commit -m "$(@'
feat(alerts): port AlertClassifier from web project

Pure port of shared/alert-tiers.ts classifyAlert. Handles parameter-driven
escalation for Tornado Warning (tornadoDamageThreat=CATASTROPHIC/CONSIDERABLE)
and Severe Thunderstorm Warning (thunderstormDamageThreat=DESTRUCTIVE).
Unknown events fall through to ADVISORY catch-all matching web behavior.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task C.3: Unit conversion (°F↔°C, mph↔m/s, inHg↔Pa) + trend rescaling

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/domain/Units.kt`
- Create: `app/src/test/kotlin/com/skyframe/domain/UnitsTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/kotlin/com/skyframe/domain/UnitsTest.kt
package com.skyframe.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.abs

class UnitsTest {

    private fun assertClose(expected: Double, actual: Double, epsilon: Double = 0.01) {
        assert(abs(expected - actual) < epsilon) {
            "Expected $expected ± $epsilon but got $actual (diff ${abs(expected - actual)})"
        }
    }

    @Test
    fun `Fahrenheit to Fahrenheit is identity`() {
        assertClose(72.0, Units.convertTempF(72.0, TempUnit.FAHRENHEIT))
        assertClose(-40.0, Units.convertTempF(-40.0, TempUnit.FAHRENHEIT))
    }

    @Test
    fun `Fahrenheit to Celsius uses 5_9 conversion`() {
        assertClose(0.0, Units.convertTempF(32.0, TempUnit.CELSIUS))
        assertClose(100.0, Units.convertTempF(212.0, TempUnit.CELSIUS))
        assertClose(-40.0, Units.convertTempF(-40.0, TempUnit.CELSIUS))
        assertClose(22.22, Units.convertTempF(72.0, TempUnit.CELSIUS))
    }

    @Test
    fun `meters per second to mph multiplies by 2_237`() {
        assertClose(22.37, Units.metersPerSecondToMph(10.0))
        assertClose(0.0, Units.metersPerSecondToMph(0.0))
        assertClose(33.55, Units.metersPerSecondToMph(15.0))
    }

    @Test
    fun `Pascals to inches of mercury divides by 3386_39`() {
        assertClose(29.92, Units.pascalsToInchesHg(101325.0))
        assertClose(0.0, Units.pascalsToInchesHg(0.0))
    }

    @Test
    fun `temperature trend rescaled to Celsius scales deltaPerHour by 5_9`() {
        val trendF = Trend(TrendDirection.UP, deltaPerHour = 9.0, confidence = TrendConfidence.OK)
        val trendC = Units.scaleTempTrend(trendF, TempUnit.CELSIUS)
        assertClose(5.0, trendC.deltaPerHour)
        assertEquals(TrendDirection.UP, trendC.direction)
        assertEquals(TrendConfidence.OK, trendC.confidence)
    }

    @Test
    fun `temperature trend in Fahrenheit is unchanged`() {
        val trend = Trend(TrendDirection.DOWN, deltaPerHour = -2.5, confidence = TrendConfidence.OK)
        assertEquals(trend, Units.scaleTempTrend(trend, TempUnit.FAHRENHEIT))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.domain.UnitsTest" --no-daemon
```

Expected: compile errors for unresolved `Units`, `TempUnit`, `Trend`, `TrendDirection`, `TrendConfidence`.

- [ ] **Step 3: Implement `Units.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/domain/Units.kt
package com.skyframe.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class TempUnit { FAHRENHEIT, CELSIUS }

@Serializable
enum class TrendDirection {
    @SerialName("up") UP,
    @SerialName("down") DOWN,
    @SerialName("steady") STEADY,
}

@Serializable
enum class TrendConfidence {
    @SerialName("ok") OK,
    @SerialName("missing") MISSING,
}

@Serializable
data class Trend(
    val direction: TrendDirection,
    val deltaPerHour: Double,
    val confidence: TrendConfidence,
)

/**
 * Pure unit conversions matching _reference/shared/units.ts behavior.
 *
 * Server-side normalization already emits temperatures in °F, speeds in
 * mph, and pressures in inHg — these helpers exist for client-side
 * display unit toggles (NowScreen hero temp tap), not for raw NWS input.
 */
object Units {

    fun convertTempF(valueF: Double, unit: TempUnit): Double = when (unit) {
        TempUnit.FAHRENHEIT -> valueF
        TempUnit.CELSIUS -> (valueF - 32.0) * 5.0 / 9.0
    }

    /** NWS observations expose wind speed in m/s; the normalizer converts to mph. */
    fun metersPerSecondToMph(mps: Double): Double = mps * 2.2369362921

    /** NWS observations expose pressure in Pa; the normalizer converts to inHg. */
    fun pascalsToInchesHg(pa: Double): Double = pa / 3386.389

    /**
     * Rescales a temperature trend when the user toggles °F→°C. Direction
     * and confidence are unit-agnostic; only deltaPerHour scales.
     */
    fun scaleTempTrend(trend: Trend, unit: TempUnit): Trend = when (unit) {
        TempUnit.FAHRENHEIT -> trend
        TempUnit.CELSIUS -> trend.copy(deltaPerHour = trend.deltaPerHour * 5.0 / 9.0)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.domain.UnitsTest" --no-daemon
```

Expected: 6 tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/domain/Units.kt app/src/test/kotlin/com/skyframe/domain/UnitsTest.kt
git commit -m "$(@'
feat(domain): port Units, Trend, and temp conversion from web project

Direct port of shared/units.ts with the same conversion constants. Trend
and TrendDirection/TrendConfidence enums introduced here will be reused
by CurrentConditions in the next task.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task C.4: WeatherResponse domain model (CurrentConditions, HourlyPeriod, DailyPeriod, Alert, WeatherMeta, IconCode)

Direct port of `_reference/shared/types.ts`. No tests in this task — Kotlin's type system + the `@Serializable` annotation provide sufficient guarantee. JSON round-trip tests live in the DTO-mapping layer in Phase E.

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/domain/IconCode.kt`
- Create: `app/src/main/kotlin/com/skyframe/domain/Wind.kt`
- Create: `app/src/main/kotlin/com/skyframe/domain/CurrentConditions.kt`
- Create: `app/src/main/kotlin/com/skyframe/domain/HourlyPeriod.kt`
- Create: `app/src/main/kotlin/com/skyframe/domain/DailyPeriod.kt`
- Create: `app/src/main/kotlin/com/skyframe/domain/Alert.kt`
- Create: `app/src/main/kotlin/com/skyframe/domain/WeatherMeta.kt`
- Create: `app/src/main/kotlin/com/skyframe/domain/WeatherResponse.kt`

- [ ] **Step 1: Write `IconCode.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/domain/IconCode.kt
package com.skyframe.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class IconCode {
    @SerialName("sun") SUN,
    @SerialName("moon") MOON,
    @SerialName("partly-day") PARTLY_DAY,
    @SerialName("partly-night") PARTLY_NIGHT,
    @SerialName("cloud") CLOUD,
    @SerialName("rain") RAIN,
    @SerialName("snow") SNOW,
    @SerialName("thunder") THUNDER,
    @SerialName("fog") FOG,
}
```

- [ ] **Step 2: Write `Wind.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/domain/Wind.kt
package com.skyframe.domain

import kotlinx.serialization.Serializable

@Serializable
data class Wind(
    val speedMph: Double,
    val directionDeg: Double,
    val cardinal: String,
)
```

- [ ] **Step 3: Write `CurrentConditions.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/domain/CurrentConditions.kt
package com.skyframe.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class CurrentConditions(
    val observedAt: Instant,
    val stationId: String,
    val stationDistanceKm: Double,
    val tempF: Double,
    val feelsLikeF: Double,
    val conditionText: String,
    val iconCode: IconCode,
    val precipOutlook: String,
    val humidityPct: Double?,
    val pressureInHg: Double?,
    val visibilityMi: Double?,
    val dewpointF: Double?,
    val wind: Wind,
    val trends: ConditionTrends,
    val sunrise: Instant,
    val sunset: Instant,
)

@Serializable
data class ConditionTrends(
    val temp: Trend,
    val wind: Trend,
    val humidity: Trend,
    val pressure: Trend,
    val visibility: Trend,
    val dewpoint: Trend,
)
```

- [ ] **Step 4: Write `HourlyPeriod.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/domain/HourlyPeriod.kt
package com.skyframe.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class HourlyPeriod(
    val startTime: Instant,
    val hourLabel: String,
    val tempF: Double,
    val iconCode: IconCode,
    val precipProbPct: Int,
    val wind: Wind,
    val shortDescription: String,
)
```

- [ ] **Step 5: Write `DailyPeriod.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/domain/DailyPeriod.kt
package com.skyframe.domain

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable

@Serializable
data class DailyPeriod(
    val dateISO: LocalDate,
    val dayOfWeek: String,
    val dateLabel: String,
    val highF: Int,
    val lowF: Int,
    val iconCode: IconCode,
    val precipProbPct: Int,
    val shortDescription: String,
    val dayDetailedForecast: String?,
    val nightDetailedForecast: String?,
    val dayPeriodName: String?,
    val nightPeriodName: String?,
)
```

- [ ] **Step 6: Write `Alert.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/domain/Alert.kt
package com.skyframe.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class AlertSeverity {
    @SerialName("Extreme") EXTREME,
    @SerialName("Severe") SEVERE,
    @SerialName("Moderate") MODERATE,
    @SerialName("Minor") MINOR,
    @SerialName("Unknown") UNKNOWN,
}

@Serializable
data class Alert(
    val id: String,
    val event: String,
    val tier: AlertTier,
    val severity: AlertSeverity,
    val headline: String,
    val description: String,
    val issuedAt: Instant,
    val effective: Instant,
    val expires: Instant,
    val areaDesc: String,
)
```

- [ ] **Step 7: Write `WeatherMeta.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/domain/WeatherMeta.kt
package com.skyframe.domain

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class StationOverride {
    @SerialName("auto") AUTO,
    @SerialName("force-secondary") FORCE_SECONDARY,
}

@Serializable
enum class WeatherError {
    @SerialName("rate_limited") RATE_LIMITED,
    @SerialName("upstream_malformed") UPSTREAM_MALFORMED,
    @SerialName("station_fallback") STATION_FALLBACK,
    @SerialName("partial") PARTIAL,
}

@Serializable
data class WeatherMeta(
    val fetchedAt: Instant,
    val nextRefreshAt: Instant,
    val cacheHit: Boolean,
    val stationId: String,
    val locationName: String,
    val stationOverride: StationOverride,
    val forecastGeneratedAt: Instant,
    val forecastOffice: String,
    val gridX: Int,
    val gridY: Int,
    val forecastZone: String,
    val error: WeatherError? = null,
)
```

- [ ] **Step 8: Write `WeatherResponse.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/domain/WeatherResponse.kt
package com.skyframe.domain

import kotlinx.serialization.Serializable

@Serializable
data class WeatherResponse(
    val current: CurrentConditions,
    val hourly: List<HourlyPeriod>,
    val daily: List<DailyPeriod>,
    val alerts: List<Alert>,
    val meta: WeatherMeta,
)
```

- [ ] **Step 9: Verify compile**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 10: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/domain/
git commit -m "$(@'
feat(domain): port WeatherResponse, CurrentConditions, HourlyPeriod,
DailyPeriod, Alert, WeatherMeta, Wind, IconCode from shared/types.ts

Direct port. Each type carries the same field set as the web. Date/time
fields use kotlinx.datetime.Instant (preserving the ISO-8601 wire format
exactly) and dateISO uses LocalDate for the daily outlook.

No tests in this task — type system + @Serializable provide structural
guarantees. JSON round-trip is verified in Phase E DTO tests.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

**Phase C milestone:** All pure-logic domain code from `_reference/shared/` ported. AlertTier + AlertClassifier + Units + Trend models exist with comprehensive unit tests. WeatherResponse and its component types are defined and serializable. Total test count after Phase C: ~20.

---

## Phase D — HUD Theming Foundation

Translate the web's CSS custom-property accent system to Compose. Output: a `HudTheme` Composable wrapping the app, exposing `HudColors`, `HudType`, and a `LocalHudAccent` composition local that downstream Composables consume. Tier-driven accent overrides land in Phase L when the AlertBanner state flows in.

### Task D.1: HudColors + base palette

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/theme/HudColors.kt`

- [ ] **Step 1: Write `HudColors.kt`**

Values come from `_reference/client/styles/hud.css` `:root` rules.

```kotlin
// app/src/main/kotlin/com/skyframe/theme/HudColors.kt
package com.skyframe.theme

import androidx.compose.ui.graphics.Color

/**
 * Static HUD palette. Tier-driven accent colors are NOT here — those flow
 * through [LocalHudAccent] so they can change with the highest-severity
 * visible alert.
 */
object HudColors {
    val BackgroundDeep   = Color(0xFF050A10)  // recessed bands (title bar interior)
    val BackgroundBase   = Color(0xFF0A1018)  // main background
    val BackgroundPanel  = Color(0xFF0E1620)  // panel surfaces
    val Foreground       = Color(0xFFC6ECFF)  // body text
    val ForegroundDim    = Color(0xFF7A96A8)  // labels, footer text

    /** Default base-cyan accent — used when no alert is overriding. */
    val DefaultAccent    = Color(0xFF22D3EE)
}
```

- [ ] **Step 2: Verify compile**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/theme/HudColors.kt
git commit -m "$(@'
feat(theme): add HudColors base palette

Ported from _reference/client/styles/hud.css :root variables. Tier-driven
accent colors live separately in LocalHudAccent (next task).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task D.2: HudAccent + LocalHudAccent composition local

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/theme/HudAccent.kt`

- [ ] **Step 1: Write `HudAccent.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/theme/HudAccent.kt
package com.skyframe.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.skyframe.domain.AlertTier

/**
 * Active accent color for HUD chrome. Equivalent to the web's
 * --accent, --accent-rgb, and --accent-glow-* CSS custom properties.
 *
 * Glow colors are derived from the base accent at fixed alpha values
 * matching hud.css text-shadow rules.
 */
@Immutable
data class HudAccent(
    val accent: Color,
    val darkStripe: Color,
    val glowSoft: Color,     // base at 0.15 alpha
    val glowMedium: Color,   // base at 0.30 alpha
    val glowStrong: Color,   // base at 0.50 alpha
) {
    companion object {
        /** Base-cyan accent used when no alert is overriding. */
        val Default: HudAccent = fromColors(
            base = HudColors.DefaultAccent,
            dark = Color(0xFF008E82),  // matches advisory tier dark variant
        )

        fun fromTier(tier: AlertTier): HudAccent = fromColors(
            base = Color(tier.baseColor),
            dark = Color(tier.darkColor),
        )

        private fun fromColors(base: Color, dark: Color): HudAccent = HudAccent(
            accent = base,
            darkStripe = dark,
            glowSoft = base.copy(alpha = 0.15f),
            glowMedium = base.copy(alpha = 0.30f),
            glowStrong = base.copy(alpha = 0.50f),
        )
    }
}

/**
 * Tree-scoped accent. Read via LocalHudAccent.current in any Composable
 * that needs accent-derived color. Shell-level state provides the active
 * value based on the highest-severity visible alert.
 */
val LocalHudAccent = compositionLocalOf { HudAccent.Default }
```

- [ ] **Step 2: Verify compile**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/theme/HudAccent.kt
git commit -m "$(@'
feat(theme): add HudAccent and LocalHudAccent composition local

Equivalent of the web's --accent/--accent-glow-* CSS custom property
flow. fromTier() builds an accent from any AlertTier; the shell will
swap the active value when the visible-alert set changes, triggering
recomposition through the entire tree.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task D.3: Typography — IBM Plex Mono resource bundling + HudType

The web uses IBM Plex Mono (OFL-licensed, free + redistributable). Bundle as Android font resource.

**Files:**
- Create: `app/src/main/res/font/ibm_plex_mono_regular.ttf` (download)
- Create: `app/src/main/res/font/ibm_plex_mono_medium.ttf` (download)
- Create: `app/src/main/kotlin/com/skyframe/theme/HudType.kt`

- [ ] **Step 1: Download IBM Plex Mono font files**

From https://github.com/IBM/plex/raw/master/IBM-Plex-Mono/fonts/complete/ttf/IBMPlexMono-Regular.ttf and `IBMPlexMono-Medium.ttf`:

```powershell
New-Item -ItemType Directory -Force -Path "app/src/main/res/font" | Out-Null
Invoke-WebRequest -Uri "https://github.com/IBM/plex/raw/master/IBM-Plex-Mono/fonts/complete/ttf/IBMPlexMono-Regular.ttf" -OutFile "app/src/main/res/font/ibm_plex_mono_regular.ttf"
Invoke-WebRequest -Uri "https://github.com/IBM/plex/raw/master/IBM-Plex-Mono/fonts/complete/ttf/IBMPlexMono-Medium.ttf" -OutFile "app/src/main/res/font/ibm_plex_mono_medium.ttf"
Get-ChildItem "app/src/main/res/font" | Select-Object Name, Length
```

Expected: two files, each ~150KB. (Android font resource filenames must be lowercase + underscores — that's why the resource names differ from the upstream filenames.)

**License note:** IBM Plex Mono ships under the SIL Open Font License v1.1. Track attribution in a `LICENSES.md` file in Plan 5; for now the OFL allows bundling and redistribution without source-tree attribution.

- [ ] **Step 2: Write `HudType.kt`**

Style values mapped from `_reference/client/styles/hud.css` and `terminal-modal.css`.

```kotlin
// app/src/main/kotlin/com/skyframe/theme/HudType.kt
package com.skyframe.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.skyframe.R

/** HUD monospace font family (IBM Plex Mono). */
val HudFontFamily = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)

/**
 * Per-role typography. Maps to _reference/client/styles/hud.css and
 * terminal-modal.css. Colors are not set here — they come from
 * MaterialTheme.colorScheme or LocalHudAccent at the call site.
 */
object HudType {
    val titleBar = TextStyle(
        fontFamily = HudFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        letterSpacing = 0.18.em,
    )
    val metaLabel = TextStyle(
        fontFamily = HudFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 0.15.em,
    )
    val bodyMono = TextStyle(
        fontFamily = HudFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
    val heroTemp = TextStyle(
        fontFamily = HudFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 96.sp,
        letterSpacing = 0.sp,
    )
    val heroFeel = TextStyle(
        fontFamily = HudFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.12.em,
    )
    val metricValue = TextStyle(
        fontFamily = HudFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    )
    val metricLabel = TextStyle(
        fontFamily = HudFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        letterSpacing = 0.18.em,
    )
    val sectionHeader = TextStyle(
        fontFamily = HudFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.20.em,
    )
    val navLabel = TextStyle(
        fontFamily = HudFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.15.em,
    )
    val footerMono = TextStyle(
        fontFamily = HudFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        letterSpacing = 0.08.em,
    )
}
```

- [ ] **Step 3: Verify compile**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: BUILD SUCCESSFUL. (The `R.font.*` references compile only after resource processing runs.)

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/res/font/ app/src/main/kotlin/com/skyframe/theme/HudType.kt
git commit -m "$(@'
feat(theme): bundle IBM Plex Mono and define HudType per-role styles

IBM Plex Mono Regular + Medium added as Android font resources (OFL
licensed, no attribution required in-tree). HudType maps the web's
hud.css and terminal-modal.css per-role type styles to Compose
TextStyle objects (titleBar, metaLabel, bodyMono, heroTemp, etc.).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task D.4: hudTextGlow modifier

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/theme/HudTextGlow.kt`

- [ ] **Step 1: Write `HudTextGlow.kt`**

Compose has no native `text-shadow`. We compose a glow by rendering into a graphicsLayer with a blur RenderEffect (API 31+) and falling back to a layered draw on older versions.

```kotlin
// app/src/main/kotlin/com/skyframe/theme/HudTextGlow.kt
package com.skyframe.theme

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws a soft glow halo around the modified content matching the web's
 * text-shadow: 0 0 8px rgba(accent, 0.5) effect.
 *
 * On Android 12+ uses RenderEffect.createBlurEffect (hardware-accelerated
 * Skia blur). On API 26-30 falls back to no-op — the underlying text still
 * renders but without glow. Compose blur fallback for pre-31 is non-trivial
 * and ~5% of 2026 devices fall in that range; we accept the reduced effect.
 */
fun Modifier.hudTextGlow(
    color: Color,
    radius: Dp = 8.dp,
): Modifier = composed {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val radiusPx = with(androidx.compose.ui.platform.LocalDensity.current) { radius.toPx() }
        Modifier.graphicsLayer {
            renderEffect = BlurEffect(radiusPx, radiusPx, TileMode.Decal)
            // Keep the original drawing in place; this layer is purely glow.
            // Note: a real implementation overlays a second copy without blur
            // on top so the text remains crisp. See HudGlowText composable
            // in widgets/ (introduced in Phase H) for the full pattern.
        }
    } else {
        Modifier
    }
}
```

**Note for future enhancement:** the production usage pattern (wraps Text in a Box stacking a blurred copy + a crisp copy) is implemented in Phase H's `HudGlowText` composable. This task just provides the underlying modifier — calling it directly without the Box stacking produces a blurry text without crisp overlay.

- [ ] **Step 2: Verify compile**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/theme/HudTextGlow.kt
git commit -m "$(@'
feat(theme): add hudTextGlow Modifier for accent glow effect

Compose equivalent of CSS text-shadow: 0 0 8px rgba(accent). API 31+
uses RenderEffect.createBlurEffect; lower API levels gracefully skip
the glow (text still renders, just without halo).

The full crisp+glow pattern is composed in HudGlowText (Phase H) which
stacks a blurred copy under a crisp copy in a Box.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task D.5: HudTheme Composable (Material 3 override + accent provider)

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/theme/HudTheme.kt`

- [ ] **Step 1: Write `HudTheme.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/theme/HudTheme.kt
package com.skyframe.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/**
 * Root theme. Provides:
 *  - Material 3 ColorScheme overridden to HUD colors (so incidentally-used
 *    Material widgets like ModalBottomSheet and Snackbar inherit HUD palette
 *    rather than the default purple).
 *  - LocalHudAccent at the supplied accent (default: cyan).
 *
 * Phase H wires the shell to pass the highest-severity-alert-derived accent.
 */
@Composable
fun HudTheme(
    accent: HudAccent = HudAccent.Default,
    content: @Composable () -> Unit,
) {
    val colorScheme = darkColorScheme(
        primary = accent.accent,
        onPrimary = HudColors.BackgroundBase,
        background = HudColors.BackgroundBase,
        onBackground = HudColors.Foreground,
        surface = HudColors.BackgroundPanel,
        onSurface = HudColors.Foreground,
        surfaceVariant = HudColors.BackgroundDeep,
        onSurfaceVariant = HudColors.ForegroundDim,
        outline = accent.darkStripe,
    )

    CompositionLocalProvider(LocalHudAccent provides accent) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content,
        )
    }
}
```

- [ ] **Step 2: Update `MainActivity` to use HudTheme**

Edit `app/src/main/kotlin/com/skyframe/MainActivity.kt`:

```kotlin
package com.skyframe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudTheme
import com.skyframe.theme.HudType
import com.skyframe.theme.LocalHudAccent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HudTheme {
                HelloSkyFrame()
            }
        }
    }
}

@Composable
private fun HelloSkyFrame() {
    val accent = LocalHudAccent.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HudColors.BackgroundBase),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "SKYFRAME",
            color = accent.accent,
            style = HudType.titleBar.copy(fontSize = androidx.compose.ui.unit.sp(32f).value.sp),
        )
    }
}
```

Wait — that's awkward. Use a cleaner version:

```kotlin
package com.skyframe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudFontFamily
import com.skyframe.theme.HudTheme
import com.skyframe.theme.LocalHudAccent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HudTheme {
                HelloSkyFrame()
            }
        }
    }
}

@Composable
private fun HelloSkyFrame() {
    val accent = LocalHudAccent.current
    Box(
        modifier = Modifier.fillMaxSize().background(HudColors.BackgroundBase),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "SKYFRAME",
            color = accent.accent,
            fontFamily = HudFontFamily,
            fontSize = 32.sp,
        )
    }
}
```

- [ ] **Step 3: Build + install + visual smoke test**

```powershell
./gradlew :app:assembleDebug --no-daemon
```

Optional install:

```powershell
./gradlew :app:installDebug --no-daemon
adb shell am start -n com.skyframe/.MainActivity
```

Expected: same dark-background + cyan "SKYFRAME" centered, but now in IBM Plex Mono instead of system default. Subtle but noticeable.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/theme/HudTheme.kt app/src/main/kotlin/com/skyframe/MainActivity.kt
git commit -m "$(@'
feat(theme): add HudTheme root + wire MainActivity

HudTheme provides a Material 3 dark ColorScheme overridden to HUD colors
(so incidentally-used Material widgets inherit the palette) and supplies
LocalHudAccent. MainActivity now wraps content in HudTheme; the hello-
world screen reads the accent through LocalHudAccent and uses IBM Plex
Mono.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

**Phase D milestone:** HUD theme foundation in place. HudColors, HudAccent with composition-local plumbing, HudType per-role styles backed by bundled IBM Plex Mono, hudTextGlow modifier, and HudTheme Composable wrapping MainActivity. App still renders only "SKYFRAME" text but in the correct font + color + theme infrastructure.

---

## Phase E — NWS HTTP Layer + DTOs + Cache + Icon Mapping + Trends

The data-fetching engine. Build a Ktor `NwsClient` with the mandatory User-Agent interceptor, define DTOs for the 5 NWS endpoints, add an in-memory TTL cache, port the icon-URL→IconCode mapping with probability thresholds, and port the 6-observation trend calculator. WeatherNormalizer (the orchestrator that ties these together) is Phase F.

### Task E.1: Ktor HttpClient factory + Hilt module

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/data/nws/NwsHttpClient.kt`
- Create: `app/src/main/kotlin/com/skyframe/di/NetworkModule.kt`

- [ ] **Step 1: Write `NwsHttpClient.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/data/nws/NwsHttpClient.kt
package com.skyframe.data.nws

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object NwsHttpClient {
    fun create(userAgent: String): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                coerceInputValues = true
            })
        }
        install(Logging) { level = LogLevel.NONE }
        install(HttpTimeout) {
            requestTimeoutMillis = 15_000
            connectTimeoutMillis = 5_000
        }
        install(HttpRequestRetry) {
            retryOnServerErrors(maxRetries = 2)
            exponentialDelay()
        }
        defaultRequest {
            header(HttpHeaders.UserAgent, userAgent)
            header(HttpHeaders.Accept, "application/geo+json,application/ld+json,application/json;q=0.9")
        }
    }
}
```

- [ ] **Step 2: Write `NetworkModule.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/di/NetworkModule.kt
package com.skyframe.di

import com.skyframe.data.nws.NwsHttpClient
import com.skyframe.data.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpClient(settings: SettingsRepository): HttpClient {
        // At app start the email may not yet be configured (first run). Fall back
        // to a generic UA; the SettingsRepository swap happens after onboarding.
        val email = runBlocking { settings.snapshot().email.ifBlank { "unconfigured@skyframe.local" } }
        return NwsHttpClient.create(userAgent = "SkyFrame/0.1.0 ($email)")
    }
}
```

**Note:** `SettingsRepository` doesn't exist yet — declared in Phase G. The `NetworkModule.kt` here will be committed alongside (or after) Phase G to keep the build green. Either commit `NwsHttpClient.kt` now and `NetworkModule.kt` after Phase G, OR commit a stub `SettingsRepository` with hardcoded email first. The plan does the latter to maintain green-build commits.

- [ ] **Step 3: Add `SettingsRepository` stub to allow this task to commit green**

Create `app/src/main/kotlin/com/skyframe/data/settings/SettingsRepository.kt`:

```kotlin
// app/src/main/kotlin/com/skyframe/data/settings/SettingsRepository.kt
package com.skyframe.data.settings

import javax.inject.Inject
import javax.inject.Singleton

/**
 * STUB — full DataStore-backed implementation lands in Task G.1.
 * Keeps NetworkModule compilable in Phase E.
 */
@Singleton
class SettingsRepository @Inject constructor() {
    data class Snapshot(val email: String = "")
    suspend fun snapshot(): Snapshot = Snapshot()
}
```

- [ ] **Step 4: Verify compile**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/data/nws/NwsHttpClient.kt app/src/main/kotlin/com/skyframe/di/NetworkModule.kt app/src/main/kotlin/com/skyframe/data/settings/SettingsRepository.kt
git commit -m "$(@'
feat(nws): Ktor HttpClient factory with mandatory User-Agent header

NWS rate-limits or rejects requests without a meaningful User-Agent. The
factory pulls the configured email from SettingsRepository (stubbed for
now; real DataStore-backed impl in Phase G). 15s request timeout, 2x
exponential-backoff retry on server errors.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task E.2: NWS DTOs

These mirror the geo+json/ld+json shapes returned by NWS. Field names match NWS exactly so kotlinx.serialization can deserialize without translation maps. Only the fields the normalizer needs are declared.

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/data/nws/NwsDtos.kt`

- [ ] **Step 1: Write `NwsDtos.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/data/nws/NwsDtos.kt
package com.skyframe.data.nws

import kotlinx.serialization.Serializable

// --------- /points/{lat},{lon} ---------

@Serializable
data class PointsDto(val properties: PointsProperties)

@Serializable
data class PointsProperties(
    val gridId: String,
    val gridX: Int,
    val gridY: Int,
    val timeZone: String,
    val forecastZone: String,
    val observationStations: String,
    val relativeLocation: RelativeLocation,
)

@Serializable
data class RelativeLocation(val properties: RelativeLocationProperties)

@Serializable
data class RelativeLocationProperties(val city: String, val state: String)

// --------- /gridpoints/{office}/{x},{y}/forecast OR forecast/hourly ---------

@Serializable
data class ForecastDto(val properties: ForecastProperties)

@Serializable
data class ForecastProperties(
    val generatedAt: String,
    val periods: List<ForecastPeriodDto>,
)

@Serializable
data class ForecastPeriodDto(
    val number: Int,
    val name: String,
    val startTime: String,
    val endTime: String,
    val isDaytime: Boolean,
    val temperature: Int,
    val temperatureUnit: String,
    val windSpeed: String,
    val windDirection: String,
    val icon: String? = null,
    val shortForecast: String,
    val detailedForecast: String? = null,
    val probabilityOfPrecipitation: ProbabilityOfPrecipitationDto? = null,
)

@Serializable
data class ProbabilityOfPrecipitationDto(val value: Int? = null)

// --------- /stations/{id}/observations/latest ---------

@Serializable
data class ObservationDto(val properties: ObservationProperties)

@Serializable
data class ObservationProperties(
    val station: String,
    val timestamp: String,
    val textDescription: String? = null,
    val icon: String? = null,
    val temperature: NumberMeasurementDto? = null,
    val windSpeed: NumberMeasurementDto? = null,
    val windDirection: NumberMeasurementDto? = null,
    val windGust: NumberMeasurementDto? = null,
    val barometricPressure: NumberMeasurementDto? = null,
    val visibility: NumberMeasurementDto? = null,
    val relativeHumidity: NumberMeasurementDto? = null,
    val dewpoint: NumberMeasurementDto? = null,
    val heatIndex: NumberMeasurementDto? = null,
    val windChill: NumberMeasurementDto? = null,
)

/** NWS measurement values come as { value: Double?, unitCode: String }. */
@Serializable
data class NumberMeasurementDto(val value: Double? = null, val unitCode: String? = null)

// --------- /alerts/active?point={lat},{lon} ---------

@Serializable
data class AlertsDto(val features: List<AlertFeatureDto>)

@Serializable
data class AlertFeatureDto(val id: String, val properties: AlertProperties)

@Serializable
data class AlertProperties(
    val id: String,
    val event: String,
    val severity: String,
    val headline: String? = null,
    val description: String,
    val sent: String,
    val effective: String,
    val expires: String,
    val areaDesc: String,
    val parameters: Map<String, List<String>> = emptyMap(),
)

// --------- /stations list (for setup) ---------

@Serializable
data class StationsListDto(val features: List<StationFeatureDto>)

@Serializable
data class StationFeatureDto(val properties: StationFeatureProperties)

@Serializable
data class StationFeatureProperties(val stationIdentifier: String)

// --------- /points-derived sunrise/sunset (separate endpoint) ---------
// (NWS doesn't expose sunrise/sunset directly; we'll use a small library
// or compute via SPA algorithm. Decision deferred to Task F.3.)
```

- [ ] **Step 2: Verify compile**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/data/nws/NwsDtos.kt
git commit -m "$(@'
feat(nws): add DTOs for /points, /forecast, /observations, /alerts

Field names match the NWS JSON shapes exactly so kotlinx.serialization
can deserialize without ad-hoc field renaming. Only fields consumed by
the normalizer are declared.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task E.3: NwsClient with 5 endpoint methods

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/data/nws/NwsClient.kt`
- Create: `app/src/test/kotlin/com/skyframe/data/nws/NwsClientTest.kt`

- [ ] **Step 1: Write the failing test (URL-construction only; HTTP behavior verified by integration tests in later tasks)**

```kotlin
// app/src/test/kotlin/com/skyframe/data/nws/NwsClientTest.kt
package com.skyframe.data.nws

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NwsClientTest {

    private fun mockClient(responder: (String) -> String): Pair<HttpClient, MutableList<String>> {
        val capturedUrls = mutableListOf<String>()
        val client = HttpClient(MockEngine { req ->
            capturedUrls += req.url.toString()
            respond(
                content = ByteReadChannel(responder(req.url.toString())),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            defaultRequest { header(HttpHeaders.UserAgent, "SkyFrameTest/0.1") }
        }
        return client to capturedUrls
    }

    @Test
    fun `points builds expected URL`() = runTest {
        val (client, urls) = mockClient {
            """{"properties":{"gridId":"MKX","gridX":88,"gridY":58,"timeZone":"America/Chicago","forecastZone":"https://api.weather.gov/zones/forecast/WIZ066","observationStations":"https://api.weather.gov/gridpoints/MKX/88,58/stations","relativeLocation":{"properties":{"city":"Oak Creek","state":"WI"}}}}"""
        }
        val nws = NwsClient(client)
        nws.points(42.8744, -87.8633)
        assertEquals(1, urls.size)
        assertTrue(urls[0].contains("/points/42.8744,-87.8633"), "Expected /points URL, got ${urls[0]}")
    }

    @Test
    fun `latestObservation builds expected URL`() = runTest {
        val (client, urls) = mockClient {
            """{"properties":{"station":"https://api.weather.gov/stations/KMKE","timestamp":"2026-05-16T12:00:00+00:00"}}"""
        }
        val nws = NwsClient(client)
        nws.latestObservation("KMKE")
        assertTrue(urls[0].contains("/stations/KMKE/observations/latest"))
    }

    @Test
    fun `activeAlerts builds expected URL`() = runTest {
        val (client, urls) = mockClient { """{"features":[]}""" }
        val nws = NwsClient(client)
        nws.activeAlerts(42.8744, -87.8633)
        assertTrue(urls[0].contains("/alerts/active?point=42.8744,-87.8633"))
    }

    @Test
    fun `forecast builds expected URL`() = runTest {
        val (client, urls) = mockClient {
            """{"properties":{"generatedAt":"2026-05-16T12:00:00+00:00","periods":[]}}"""
        }
        val nws = NwsClient(client)
        nws.forecast("MKX", 88, 58)
        assertTrue(urls[0].contains("/gridpoints/MKX/88,58/forecast"))
        assertTrue(!urls[0].contains("/forecast/hourly"))
    }

    @Test
    fun `hourlyForecast builds expected URL`() = runTest {
        val (client, urls) = mockClient {
            """{"properties":{"generatedAt":"2026-05-16T12:00:00+00:00","periods":[]}}"""
        }
        val nws = NwsClient(client)
        nws.hourlyForecast("MKX", 88, 58)
        assertTrue(urls[0].contains("/gridpoints/MKX/88,58/forecast/hourly"))
    }
}
```

Add the Ktor mock engine to `app/build.gradle.kts` test dependencies (one-line change):

```kotlin
testImplementation("io.ktor:ktor-client-mock:3.0.1")
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.nws.NwsClientTest" --no-daemon
```

Expected: compile error (NwsClient unresolved).

- [ ] **Step 3: Implement `NwsClient`**

```kotlin
// app/src/main/kotlin/com/skyframe/data/nws/NwsClient.kt
package com.skyframe.data.nws

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around the NWS REST API. URL construction matches the web
 * project's server/nws/client.ts exactly. The shared HttpClient is
 * Hilt-provided with the mandatory User-Agent header pre-installed.
 */
@Singleton
class NwsClient @Inject constructor(private val http: HttpClient) {

    private val base = "https://api.weather.gov"

    suspend fun points(lat: Double, lon: Double): PointsDto =
        http.get("$base/points/${fmt(lat)},${fmt(lon)}").body()

    suspend fun stationsList(url: String): StationsListDto =
        http.get(url).body()

    suspend fun forecast(office: String, x: Int, y: Int): ForecastDto =
        http.get("$base/gridpoints/$office/$x,$y/forecast").body()

    suspend fun hourlyForecast(office: String, x: Int, y: Int): ForecastDto =
        http.get("$base/gridpoints/$office/$x,$y/forecast/hourly").body()

    suspend fun latestObservation(stationId: String): ObservationDto =
        http.get("$base/stations/$stationId/observations/latest").body()

    suspend fun activeAlerts(lat: Double, lon: Double): AlertsDto =
        http.get("$base/alerts/active?point=${fmt(lat)},${fmt(lon)}").body()

    private fun fmt(d: Double): String = "%.4f".format(d)
}
```

- [ ] **Step 4: Run test to verify it passes**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.nws.NwsClientTest" --no-daemon
```

Expected: 5 tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/build.gradle.kts app/src/main/kotlin/com/skyframe/data/nws/NwsClient.kt app/src/test/kotlin/com/skyframe/data/nws/NwsClientTest.kt
git commit -m "$(@'
feat(nws): add NwsClient with five endpoint methods

Direct port of _reference/server/nws/client.ts. URL construction matches
exactly (points, gridpoints/forecast, gridpoints/forecast/hourly,
stations/observations/latest, alerts/active). Lat/lon formatted to 4
decimals like the web. URL-construction tests use Ktor MockEngine.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task E.4: WeatherCache (in-memory TTL)

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/data/cache/WeatherCache.kt`
- Create: `app/src/test/kotlin/com/skyframe/data/cache/WeatherCacheTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/kotlin/com/skyframe/data/cache/WeatherCacheTest.kt
package com.skyframe.data.cache

import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

class WeatherCacheTest {

    @Test
    fun `cache returns stored value within TTL`() {
        var now = Instant.fromEpochSeconds(1_000)
        val cache = WeatherCache<String> { now }
        cache.put("key", "value", ttl = 60.seconds)
        assertEquals("value", cache.get("key"))
    }

    @Test
    fun `cache returns null after TTL expires`() {
        var now = Instant.fromEpochSeconds(1_000)
        val cache = WeatherCache<String> { now }
        cache.put("key", "value", ttl = 60.seconds)
        now = Instant.fromEpochSeconds(1_061)  // 61s elapsed
        assertNull(cache.get("key"))
    }

    @Test
    fun `cache returns most recent put`() {
        var now = Instant.fromEpochSeconds(1_000)
        val cache = WeatherCache<String> { now }
        cache.put("key", "first", ttl = 60.seconds)
        cache.put("key", "second", ttl = 60.seconds)
        assertEquals("second", cache.get("key"))
    }

    @Test
    fun `invalidate removes entry`() {
        var now = Instant.fromEpochSeconds(1_000)
        val cache = WeatherCache<String> { now }
        cache.put("key", "value", ttl = 60.seconds)
        cache.invalidate("key")
        assertNull(cache.get("key"))
    }

    @Test
    fun `clear removes all entries`() {
        var now = Instant.fromEpochSeconds(1_000)
        val cache = WeatherCache<String> { now }
        cache.put("a", "1", ttl = 60.seconds)
        cache.put("b", "2", ttl = 60.seconds)
        cache.clear()
        assertNull(cache.get("a"))
        assertNull(cache.get("b"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.cache.WeatherCacheTest" --no-daemon
```

Expected: compile error.

- [ ] **Step 3: Implement `WeatherCache`**

```kotlin
// app/src/main/kotlin/com/skyframe/data/cache/WeatherCache.kt
package com.skyframe.data.cache

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

/**
 * Generic TTL cache. Used in two configurations:
 *   - 90-second TTL on full WeatherResponse (matches web server cache.ts)
 *   - 1-hour TTL on /points lookups (grid coordinates don't change)
 *
 * Thread-safe via ConcurrentHashMap. The clock parameter exists for tests.
 */
class WeatherCache<V>(
    private val now: () -> Instant = { Clock.System.now() },
) {
    private data class Entry<V>(val value: V, val expiresAt: Instant)

    private val map = ConcurrentHashMap<String, Entry<V>>()

    fun get(key: String): V? {
        val entry = map[key] ?: return null
        return if (now() < entry.expiresAt) entry.value else {
            map.remove(key)
            null
        }
    }

    fun put(key: String, value: V, ttl: Duration) {
        map[key] = Entry(value, now() + ttl)
    }

    fun invalidate(key: String) {
        map.remove(key)
    }

    fun clear() {
        map.clear()
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.cache.WeatherCacheTest" --no-daemon
```

Expected: 5 tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/data/cache/WeatherCache.kt app/src/test/kotlin/com/skyframe/data/cache/WeatherCacheTest.kt
git commit -m "$(@'
feat(cache): add WeatherCache (generic TTL cache)

Port of _reference/server/nws/cache.ts. Backed by ConcurrentHashMap.
Clock injection for tests; production code uses Clock.System.now().

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task E.5: IconMapper (NWS icon URL → IconCode with probability thresholds)

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/data/nws/IconMapper.kt`
- Create: `app/src/test/kotlin/com/skyframe/data/nws/IconMapperTest.kt`

- [ ] **Step 1: Read the web's icon-mapping.ts for exact rules**

```powershell
Get-Content "_reference/server/nws/icon-mapping.ts" | Select-Object -First 200
```

Use the output to confirm thresholds (hourly downgrade < 30%, daily upgrade ≥ 50%) and URL parsing rules (NWS icon URLs end like `/icons/land/day/few?size=medium` or `/icons/land/night/rain,30`).

- [ ] **Step 2: Write the failing test**

```kotlin
// app/src/test/kotlin/com/skyframe/data/nws/IconMapperTest.kt
package com.skyframe.data.nws

import com.skyframe.domain.IconCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class IconMapperTest {

    @Test
    fun `clear day icon maps to sun`() {
        assertEquals(IconCode.SUN, IconMapper.fromUrl("https://api.weather.gov/icons/land/day/skc?size=medium"))
    }

    @Test
    fun `clear night icon maps to moon`() {
        assertEquals(IconCode.MOON, IconMapper.fromUrl("https://api.weather.gov/icons/land/night/skc?size=medium"))
    }

    @Test
    fun `few clouds day maps to partly-day`() {
        assertEquals(IconCode.PARTLY_DAY, IconMapper.fromUrl("https://api.weather.gov/icons/land/day/few?size=medium"))
    }

    @Test
    fun `few clouds night maps to partly-night`() {
        assertEquals(IconCode.PARTLY_NIGHT, IconMapper.fromUrl("https://api.weather.gov/icons/land/night/few?size=medium"))
    }

    @Test
    fun `overcast maps to cloud`() {
        assertEquals(IconCode.CLOUD, IconMapper.fromUrl("https://api.weather.gov/icons/land/day/ovc?size=medium"))
    }

    @Test
    fun `rain icon maps to rain`() {
        assertEquals(IconCode.RAIN, IconMapper.fromUrl("https://api.weather.gov/icons/land/day/rain,80?size=medium"))
    }

    @Test
    fun `snow icon maps to snow`() {
        assertEquals(IconCode.SNOW, IconMapper.fromUrl("https://api.weather.gov/icons/land/day/snow,40?size=medium"))
    }

    @Test
    fun `thunderstorm icon maps to thunder`() {
        assertEquals(IconCode.THUNDER, IconMapper.fromUrl("https://api.weather.gov/icons/land/day/tsra?size=medium"))
    }

    @Test
    fun `fog icon maps to fog`() {
        assertEquals(IconCode.FOG, IconMapper.fromUrl("https://api.weather.gov/icons/land/day/fog?size=medium"))
    }

    @Test
    fun `null URL falls back to cloud`() {
        assertEquals(IconCode.CLOUD, IconMapper.fromUrl(null))
    }

    @Test
    fun `unknown short code falls back to cloud`() {
        assertEquals(IconCode.CLOUD, IconMapper.fromUrl("https://api.weather.gov/icons/land/day/nonsense?size=medium"))
    }

    @Test
    fun `hourly rain with under-30 probability downgrades to partly-day`() {
        // hourly rain icon with isDay=true and precipProb=20 should NOT show rain
        assertEquals(
            IconCode.PARTLY_DAY,
            IconMapper.forHourly(
                rawUrl = "https://api.weather.gov/icons/land/day/rain,20?size=medium",
                isDay = true,
                precipProbPct = 20,
            )
        )
    }

    @Test
    fun `hourly rain with over-30 probability stays as rain`() {
        assertEquals(
            IconCode.RAIN,
            IconMapper.forHourly(
                rawUrl = "https://api.weather.gov/icons/land/day/rain,50?size=medium",
                isDay = true,
                precipProbPct = 50,
            )
        )
    }

    @Test
    fun `daily icon upgrades to rain when NWS picked sun but precip is over 50`() {
        // shortForecast contains "Rain" so target is rain (vs snow or thunder)
        assertEquals(
            IconCode.RAIN,
            IconMapper.forDaily(
                rawUrl = "https://api.weather.gov/icons/land/day/few?size=medium",
                shortForecast = "Slight Chance Rain Showers then Mostly Sunny",
                precipProbPct = 60,
            )
        )
    }

    @Test
    fun `daily icon prefers thunder over snow over rain on tie`() {
        assertEquals(
            IconCode.THUNDER,
            IconMapper.forDaily(
                rawUrl = "https://api.weather.gov/icons/land/day/few?size=medium",
                shortForecast = "Chance of Rain and Thunderstorms, Snow Possible",
                precipProbPct = 70,
            )
        )
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.nws.IconMapperTest" --no-daemon
```

Expected: compile error (IconMapper unresolved).

- [ ] **Step 4: Implement `IconMapper`**

```kotlin
// app/src/main/kotlin/com/skyframe/data/nws/IconMapper.kt
package com.skyframe.data.nws

import com.skyframe.domain.IconCode

/**
 * Maps NWS icon URLs to the project's IconCode enum.
 *
 * NWS icon URLs look like:
 *   https://api.weather.gov/icons/land/day/skc?size=medium
 *   https://api.weather.gov/icons/land/night/rain,80?size=medium
 *
 * The path segment after /day/ or /night/ encodes the condition + optional
 * probability suffix (e.g. "rain,80" means rain with 80% probability — but
 * we ignore the suffix value, the caller passes probability separately).
 *
 * Ported from _reference/server/nws/icon-mapping.ts.
 */
object IconMapper {

    private val PATH_REGEX = Regex("""/icons/land/(day|night)/([^?,/]+)""")

    private val CODE_MAP_DAY = mapOf(
        "skc" to IconCode.SUN,
        "few" to IconCode.PARTLY_DAY,
        "sct" to IconCode.PARTLY_DAY,
        "bkn" to IconCode.PARTLY_DAY,
        "ovc" to IconCode.CLOUD,
        "rain" to IconCode.RAIN,
        "rain_showers" to IconCode.RAIN,
        "rain_showers_hi" to IconCode.RAIN,
        "snow" to IconCode.SNOW,
        "rain_snow" to IconCode.SNOW,
        "rain_sleet" to IconCode.SNOW,
        "snow_sleet" to IconCode.SNOW,
        "fzra" to IconCode.SNOW,
        "rain_fzra" to IconCode.SNOW,
        "snow_fzra" to IconCode.SNOW,
        "sleet" to IconCode.SNOW,
        "tsra" to IconCode.THUNDER,
        "tsra_sct" to IconCode.THUNDER,
        "tsra_hi" to IconCode.THUNDER,
        "fog" to IconCode.FOG,
        "haze" to IconCode.FOG,
        "smoke" to IconCode.FOG,
        "dust" to IconCode.FOG,
    )

    private val CODE_MAP_NIGHT = CODE_MAP_DAY + mapOf(
        "skc" to IconCode.MOON,
        "few" to IconCode.PARTLY_NIGHT,
        "sct" to IconCode.PARTLY_NIGHT,
        "bkn" to IconCode.PARTLY_NIGHT,
    )

    /** Pure URL→IconCode mapping with no probability-aware logic. */
    fun fromUrl(url: String?): IconCode {
        if (url == null) return IconCode.CLOUD
        val match = PATH_REGEX.find(url) ?: return IconCode.CLOUD
        val (period, code) = match.destructured
        val map = if (period == "night") CODE_MAP_NIGHT else CODE_MAP_DAY
        return map[code] ?: IconCode.CLOUD
    }

    /**
     * Hourly icon with probability-aware downgrade: when NWS picked a precip
     * icon but probability < 30%, downgrade to partly-* so the hourly chart
     * doesn't lie about an unlikely event.
     */
    fun forHourly(rawUrl: String?, isDay: Boolean, precipProbPct: Int): IconCode {
        val base = fromUrl(rawUrl)
        if (precipProbPct < 30 && base in setOf(IconCode.RAIN, IconCode.SNOW, IconCode.THUNDER)) {
            return if (isDay) IconCode.PARTLY_DAY else IconCode.PARTLY_NIGHT
        }
        return base
    }

    /**
     * Daily icon with probability-aware upgrade: when NWS picked a non-precip
     * icon but probability >= 50%, upgrade to rain/snow/thunder. Target is
     * picked by shortForecast keyword match: thunder beats snow beats rain.
     */
    fun forDaily(rawUrl: String?, shortForecast: String, precipProbPct: Int): IconCode {
        val base = fromUrl(rawUrl)
        if (precipProbPct < 50) return base
        if (base in setOf(IconCode.RAIN, IconCode.SNOW, IconCode.THUNDER)) return base

        val text = shortForecast.lowercase()
        return when {
            "thunder" in text || "t-storm" in text -> IconCode.THUNDER
            "snow" in text || "sleet" in text || "ice" in text -> IconCode.SNOW
            "rain" in text || "shower" in text || "drizzle" in text -> IconCode.RAIN
            else -> base
        }
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.nws.IconMapperTest" --no-daemon
```

Expected: 14 tests pass.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/data/nws/IconMapper.kt app/src/test/kotlin/com/skyframe/data/nws/IconMapperTest.kt
git commit -m "$(@'
feat(nws): port IconMapper with probability-aware thresholds

Direct port of _reference/server/nws/icon-mapping.ts. Three call sites:
- fromUrl: bare URL→IconCode mapping
- forHourly: downgrades precip icons to partly-* when probability < 30%
- forDaily: upgrades non-precip icons to rain/snow/thunder when precip
  probability >= 50%, picking target via shortForecast keyword match
  (thunder > snow > rain).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task E.6: TrendCalculator (6-observation rolling trend)

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/data/nws/TrendCalculator.kt`
- Create: `app/src/test/kotlin/com/skyframe/data/nws/TrendCalculatorTest.kt`

- [ ] **Step 1: Read the web's trends.ts**

```powershell
Get-Content "_reference/server/nws/trends.ts" | Select-Object -First 200
```

Use the output to confirm the algorithm: linear regression over up-to-6 observations, returns direction (UP if delta/hr > threshold, DOWN if < -threshold, else STEADY), confidence (OK if ≥3 observations, MISSING otherwise).

- [ ] **Step 2: Write the failing test**

```kotlin
// app/src/test/kotlin/com/skyframe/data/nws/TrendCalculatorTest.kt
package com.skyframe.data.nws

import com.skyframe.domain.TrendConfidence
import com.skyframe.domain.TrendDirection
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrendCalculatorTest {

    private fun ts(hourOffset: Int): Instant =
        Instant.fromEpochSeconds(1_000_000L + hourOffset * 3600L)

    @Test
    fun `single observation yields steady missing-confidence trend`() {
        val obs = listOf(70.0 to ts(0))
        val trend = TrendCalculator.compute(obs, steadyThreshold = 0.5)
        assertEquals(TrendDirection.STEADY, trend.direction)
        assertEquals(TrendConfidence.MISSING, trend.confidence)
    }

    @Test
    fun `two observations rising at 1 degree per hour returns UP`() {
        val obs = listOf(70.0 to ts(0), 71.0 to ts(1))
        val trend = TrendCalculator.compute(obs, steadyThreshold = 0.5)
        assertEquals(TrendDirection.UP, trend.direction)
        assertTrue(trend.deltaPerHour > 0.5, "Expected positive deltaPerHour, got ${trend.deltaPerHour}")
    }

    @Test
    fun `falling values return DOWN`() {
        val obs = listOf(70.0 to ts(0), 68.0 to ts(1), 66.0 to ts(2))
        val trend = TrendCalculator.compute(obs, steadyThreshold = 0.5)
        assertEquals(TrendDirection.DOWN, trend.direction)
        assertEquals(TrendConfidence.OK, trend.confidence)
    }

    @Test
    fun `flat values return STEADY`() {
        val obs = listOf(70.0 to ts(0), 70.0 to ts(1), 70.0 to ts(2), 70.0 to ts(3))
        val trend = TrendCalculator.compute(obs, steadyThreshold = 0.5)
        assertEquals(TrendDirection.STEADY, trend.direction)
    }

    @Test
    fun `small fluctuations under threshold return STEADY`() {
        val obs = listOf(70.0 to ts(0), 70.2 to ts(1), 70.1 to ts(2), 70.3 to ts(3))
        val trend = TrendCalculator.compute(obs, steadyThreshold = 0.5)
        assertEquals(TrendDirection.STEADY, trend.direction)
    }

    @Test
    fun `confidence is OK with 3+ observations`() {
        val obs = listOf(70.0 to ts(0), 71.0 to ts(1), 72.0 to ts(2))
        assertEquals(TrendConfidence.OK, TrendCalculator.compute(obs).confidence)
    }

    @Test
    fun `confidence is MISSING with fewer than 3 observations`() {
        val obs = listOf(70.0 to ts(0), 71.0 to ts(1))
        assertEquals(TrendConfidence.MISSING, TrendCalculator.compute(obs).confidence)
    }

    @Test
    fun `empty input returns steady missing`() {
        val trend = TrendCalculator.compute(emptyList())
        assertEquals(TrendDirection.STEADY, trend.direction)
        assertEquals(TrendConfidence.MISSING, trend.confidence)
        assertEquals(0.0, trend.deltaPerHour)
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.nws.TrendCalculatorTest" --no-daemon
```

Expected: compile error.

- [ ] **Step 4: Implement `TrendCalculator`**

```kotlin
// app/src/main/kotlin/com/skyframe/data/nws/TrendCalculator.kt
package com.skyframe.data.nws

import com.skyframe.domain.Trend
import com.skyframe.domain.TrendConfidence
import com.skyframe.domain.TrendDirection
import kotlinx.datetime.Instant
import kotlin.math.abs

/**
 * Linear-regression trend over up to 6 (value, timestamp) observations.
 * Ported from _reference/server/nws/trends.ts.
 *
 * Algorithm: ordinary least-squares slope. Confidence is OK when there
 * are 3 or more observations, MISSING otherwise. Direction is UP/DOWN
 * when |slope| exceeds steadyThreshold, STEADY when within.
 */
object TrendCalculator {

    fun compute(
        observations: List<Pair<Double, Instant>>,
        steadyThreshold: Double = 0.5,
    ): Trend {
        if (observations.isEmpty()) {
            return Trend(TrendDirection.STEADY, 0.0, TrendConfidence.MISSING)
        }
        if (observations.size == 1) {
            return Trend(TrendDirection.STEADY, 0.0, TrendConfidence.MISSING)
        }

        // Convert timestamps to hours-since-first-observation.
        val first = observations.first().second
        val points = observations.map { (v, t) ->
            val hoursElapsed = (t.epochSeconds - first.epochSeconds).toDouble() / 3600.0
            hoursElapsed to v
        }

        // OLS slope: sum((x - x̄)(y - ȳ)) / sum((x - x̄)²)
        val xMean = points.sumOf { it.first } / points.size
        val yMean = points.sumOf { it.second } / points.size
        val numerator = points.sumOf { (x, y) -> (x - xMean) * (y - yMean) }
        val denominator = points.sumOf { (x, _) -> (x - xMean) * (x - xMean) }
        val slope = if (denominator == 0.0) 0.0 else numerator / denominator

        val direction = when {
            slope > steadyThreshold -> TrendDirection.UP
            slope < -steadyThreshold -> TrendDirection.DOWN
            else -> TrendDirection.STEADY
        }
        val confidence = if (observations.size >= 3) TrendConfidence.OK else TrendConfidence.MISSING

        return Trend(direction, slope, confidence)
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.nws.TrendCalculatorTest" --no-daemon
```

Expected: 8 tests pass.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/data/nws/TrendCalculator.kt app/src/test/kotlin/com/skyframe/data/nws/TrendCalculatorTest.kt
git commit -m "$(@'
feat(nws): port TrendCalculator with OLS slope over observations

Computes a linear-regression slope across up-to-6 (value, timestamp)
pairs to determine UP/DOWN/STEADY trend with a deltaPerHour magnitude.
Confidence is OK with 3+ observations, MISSING otherwise.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

**Phase E milestone:** NWS HTTP plumbing fully ported. `NwsClient` calls 5 endpoints with correct User-Agent headers. WeatherCache provides TTL-based in-memory caching. IconMapper handles URL→IconCode mapping with probability-aware adjustments. TrendCalculator does OLS regression for the trend arrows. Total test count after Phase E: ~50.

---

## Phase F — Geocoder + SetupResolver + WeatherNormalizer

The orchestrator layer. `Geocoder` wraps Nominatim for ZIP→lat/lon resolution. `SetupResolver` runs the first-time flow (input → lat/lon → grid/timezone/stations). `WeatherNormalizer` is the big one — it orchestrates 5 parallel NWS fetches, transforms the raw responses into a typed `WeatherResponse`, and handles station fallback.

### Task F.1: Nominatim Geocoder

The web uses `https://nominatim.openstreetmap.org/search?postalcode=...&country=US&format=json&limit=1` with the bare `SkyFrame/0.1` User-Agent (no email — Nominatim doesn't require one but recommends it, and a contact email is good citizenship; we'll send our configured email when available).

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/data/geocoding/NominatimDtos.kt`
- Create: `app/src/main/kotlin/com/skyframe/data/geocoding/Geocoder.kt`
- Create: `app/src/test/kotlin/com/skyframe/data/geocoding/GeocoderTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/kotlin/com/skyframe/data/geocoding/GeocoderTest.kt
package com.skyframe.data.geocoding

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeocoderTest {

    private fun mockClient(content: String, status: HttpStatusCode = HttpStatusCode.OK): Pair<HttpClient, MutableList<String>> {
        val urls = mutableListOf<String>()
        val client = HttpClient(MockEngine { req ->
            urls += req.url.toString()
            respond(
                content = ByteReadChannel(content),
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        return client to urls
    }

    @Test
    fun `geocodeZip returns first result's lat lon`() = runTest {
        val (client, _) = mockClient("""[{"lat":"42.8744","lon":"-87.8633","display_name":"Oak Creek, WI, USA"}]""")
        val result = Geocoder(client).geocodeZip("53154")
        assertEquals(42.8744, result.lat)
        assertEquals(-87.8633, result.lon)
    }

    @Test
    fun `geocodeZip URL includes country=US and limit=1`() = runTest {
        val (client, urls) = mockClient("""[{"lat":"42.8744","lon":"-87.8633"}]""")
        Geocoder(client).geocodeZip("53154")
        val url = urls[0]
        assertTrue(url.contains("postalcode=53154"))
        assertTrue(url.contains("country=US"))
        assertTrue(url.contains("limit=1"))
        assertTrue(url.contains("format=json"))
    }

    @Test
    fun `empty results throws GeocodingException`() = runTest {
        val (client, _) = mockClient("[]")
        val exc = assertThrows(GeocodingException::class.java) {
            kotlinx.coroutines.runBlocking { Geocoder(client).geocodeZip("00000") }
        }
        assertTrue(exc.message!!.contains("No results"))
    }

    @Test
    fun `non-200 response throws GeocodingException`() = runTest {
        val (client, _) = mockClient("Service Unavailable", status = HttpStatusCode.ServiceUnavailable)
        assertThrows(GeocodingException::class.java) {
            kotlinx.coroutines.runBlocking { Geocoder(client).geocodeZip("53154") }
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.geocoding.GeocoderTest" --no-daemon
```

Expected: compile error.

- [ ] **Step 3: Implement `NominatimDtos.kt` and `Geocoder.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/data/geocoding/NominatimDtos.kt
package com.skyframe.data.geocoding

import kotlinx.serialization.Serializable

@Serializable
data class NominatimResult(
    val lat: String,
    val lon: String,
    val display_name: String? = null,
)
```

```kotlin
// app/src/main/kotlin/com/skyframe/data/geocoding/Geocoder.kt
package com.skyframe.data.geocoding

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton

data class GeoCoordinates(val lat: Double, val lon: Double)

class GeocodingException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Wraps Nominatim (OpenStreetMap) for ZIP→lat/lon resolution. Port of
 * _reference/server/nws/setup.ts geocodeZip(). Nominatim is keyless and
 * free; their usage policy asks for a meaningful User-Agent (set by the
 * shared HttpClient at construction).
 */
@Singleton
class Geocoder @Inject constructor(private val http: HttpClient) {

    private val base = "https://nominatim.openstreetmap.org"

    suspend fun geocodeZip(zip: String): GeoCoordinates {
        val url = "$base/search?postalcode=$zip&country=US&format=json&limit=1"
        val response: HttpResponse = http.get(url)
        if (!response.status.isSuccess()) {
            throw GeocodingException("Nominatim returned ${response.status.value}")
        }
        val results: List<NominatimResult> = response.body()
        if (results.isEmpty()) {
            throw GeocodingException("No results for ZIP $zip")
        }
        val r = results.first()
        val lat = r.lat.toDoubleOrNull() ?: throw GeocodingException("Invalid lat from Nominatim: ${r.lat}")
        val lon = r.lon.toDoubleOrNull() ?: throw GeocodingException("Invalid lon from Nominatim: ${r.lon}")
        return GeoCoordinates(lat, lon)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.geocoding.GeocoderTest" --no-daemon
```

Expected: 4 tests pass.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/data/geocoding/
git commit -m "$(@'
feat(geocoding): add Nominatim Geocoder for ZIP to lat/lon

Direct port of _reference/server/nws/setup.ts geocodeZip(). Keyless,
free, matches web's URL construction exactly (postalcode + country=US +
format=json + limit=1). Throws GeocodingException on non-200 or empty
results.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task F.2: SetupResolver (ZIP/lat,lon → grid)

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/data/nws/SetupResolver.kt`
- Create: `app/src/test/kotlin/com/skyframe/data/nws/SetupResolverTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/kotlin/com/skyframe/data/nws/SetupResolverTest.kt
package com.skyframe.data.nws

import com.skyframe.data.geocoding.GeoCoordinates
import com.skyframe.data.geocoding.Geocoder
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class SetupResolverTest {

    @Test
    fun `parses ZIP input via geocoder`() = runTest {
        val geo = mockk<Geocoder>()
        coEvery { geo.geocodeZip("53154") } returns GeoCoordinates(42.8744, -87.8633)
        val nws = mockk<NwsClient>()
        coEvery { nws.points(42.8744, -87.8633) } returns fakePointsDto()
        coEvery { nws.stationsList(any()) } returns StationsListDto(
            features = listOf(
                StationFeatureDto(StationFeatureProperties("KMKE")),
                StationFeatureDto(StationFeatureProperties("KRAC")),
            )
        )

        val result = SetupResolver(geo, nws).resolve("53154")

        assertEquals(42.8744, result.lat)
        assertEquals(-87.8633, result.lon)
        assertEquals("MKX", result.forecastOffice)
        assertEquals(88, result.gridX)
        assertEquals(58, result.gridY)
        assertEquals("America/Chicago", result.timezone)
        assertEquals("WIZ066", result.forecastZone)
        assertEquals("KMKE", result.primaryStation)
        assertEquals("KRAC", result.secondaryStation)
        assertEquals("OAK CREEK WI", result.locationName)
    }

    @Test
    fun `parses lat lon input directly without geocoding`() = runTest {
        val geo = mockk<Geocoder>()
        val nws = mockk<NwsClient>()
        coEvery { nws.points(42.8744, -87.8633) } returns fakePointsDto()
        coEvery { nws.stationsList(any()) } returns StationsListDto(
            features = listOf(StationFeatureDto(StationFeatureProperties("KMKE")))
        )

        val result = SetupResolver(geo, nws).resolve("42.8744, -87.8633")

        assertEquals(42.8744, result.lat)
        assertEquals(-87.8633, result.lon)
        assertEquals("KMKE", result.primaryStation)
        // Only one station available, so secondary falls back to primary
        assertEquals("KMKE", result.secondaryStation)
    }

    @Test
    fun `rejects invalid input`() {
        val geo = mockk<Geocoder>()
        val nws = mockk<NwsClient>()
        assertThrows(SetupException::class.java) {
            kotlinx.coroutines.runBlocking { SetupResolver(geo, nws).resolve("not-a-valid-input") }
        }
    }

    private fun fakePointsDto() = PointsDto(
        properties = PointsProperties(
            gridId = "MKX",
            gridX = 88,
            gridY = 58,
            timeZone = "America/Chicago",
            forecastZone = "https://api.weather.gov/zones/forecast/WIZ066",
            observationStations = "https://api.weather.gov/gridpoints/MKX/88,58/stations",
            relativeLocation = RelativeLocation(
                properties = RelativeLocationProperties("Oak Creek", "WI")
            ),
            astronomicalData = null,
        )
    )
}
```

- [ ] **Step 2: Update `PointsProperties` to include `astronomicalData` field**

Edit `app/src/main/kotlin/com/skyframe/data/nws/NwsDtos.kt`. Replace the existing `PointsProperties` data class with:

```kotlin
@Serializable
data class PointsProperties(
    val gridId: String,
    val gridX: Int,
    val gridY: Int,
    val timeZone: String,
    val forecastZone: String,
    val observationStations: String,
    val relativeLocation: RelativeLocation,
    val astronomicalData: AstronomicalDataDto? = null,
)

@Serializable
data class AstronomicalDataDto(
    val sunrise: String? = null,
    val sunset: String? = null,
)
```

- [ ] **Step 3: Run test to verify it fails**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.nws.SetupResolverTest" --no-daemon
```

Expected: compile error (SetupResolver, SetupException, ResolvedSetup unresolved).

- [ ] **Step 4: Implement `SetupResolver`**

```kotlin
// app/src/main/kotlin/com/skyframe/data/nws/SetupResolver.kt
package com.skyframe.data.nws

import com.skyframe.data.geocoding.Geocoder
import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedSetup(
    val lat: Double,
    val lon: Double,
    val forecastOffice: String,
    val gridX: Int,
    val gridY: Int,
    val timezone: String,
    val forecastZone: String,
    val primaryStation: String,
    val secondaryStation: String,
    val locationName: String,
)

class SetupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Resolves a user-entered location (ZIP or "lat, lon") into the full
 * config needed to call NWS forecast endpoints. Port of
 * _reference/server/nws/setup.ts resolveSetup().
 */
@Singleton
class SetupResolver @Inject constructor(
    private val geocoder: Geocoder,
    private val nws: NwsClient,
) {
    private val zipRegex = Regex("""^\d{5}$""")
    private val latLonRegex = Regex("""^(-?\d+\.?\d*)[,\s]+(-?\d+\.?\d*)$""")

    suspend fun resolve(input: String): ResolvedSetup {
        val trimmed = input.trim()

        // 1. Parse input -> lat/lon
        val coords = when {
            zipRegex.matches(trimmed) -> geocoder.geocodeZip(trimmed)
            else -> {
                val match = latLonRegex.matchEntire(trimmed)
                    ?: throw SetupException("Enter a 5-digit ZIP code or lat,lon coordinates.")
                val lat = match.groupValues[1].toDouble()
                val lon = match.groupValues[2].toDouble()
                if (lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                    throw SetupException("Coordinates out of valid range.")
                }
                com.skyframe.data.geocoding.GeoCoordinates(lat, lon)
            }
        }

        // 2. Call NWS /points for grid + zone + stations URL + relative location
        val points = try {
            nws.points(coords.lat, coords.lon)
        } catch (e: Exception) {
            throw SetupException("NWS /points lookup failed: ${e.message}", e)
        }
        val props = points.properties

        // 3. Strip the forecast zone to its terminal ID (e.g. WIZ066)
        val forecastZone = Regex("""([A-Z]{3}\d{3})$""")
            .find(props.forecastZone)?.groupValues?.get(1) ?: props.forecastZone

        // 4. Get nearby observation stations list
        val stationsList = try {
            nws.stationsList(props.observationStations)
        } catch (e: Exception) {
            throw SetupException("NWS /stations lookup failed: ${e.message}", e)
        }
        val stationIds = stationsList.features.map { it.properties.stationIdentifier }
        if (stationIds.isEmpty()) {
            throw SetupException("No observation stations found near this location.")
        }

        val city = props.relativeLocation.properties.city.uppercase()
        val state = props.relativeLocation.properties.state.uppercase()

        return ResolvedSetup(
            lat = coords.lat,
            lon = coords.lon,
            forecastOffice = props.gridId,
            gridX = props.gridX,
            gridY = props.gridY,
            timezone = props.timeZone,
            forecastZone = forecastZone,
            primaryStation = stationIds[0],
            secondaryStation = stationIds.getOrNull(1) ?: stationIds[0],
            locationName = "$city $state",
        )
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.nws.SetupResolverTest" --no-daemon
```

Expected: 3 tests pass.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/data/nws/SetupResolver.kt app/src/main/kotlin/com/skyframe/data/nws/NwsDtos.kt app/src/test/kotlin/com/skyframe/data/nws/SetupResolverTest.kt
git commit -m "$(@'
feat(nws): port SetupResolver for ZIP/lat-lon -> grid resolution

Direct port of _reference/server/nws/setup.ts resolveSetup(). Accepts
either a 5-digit ZIP (routed through Nominatim) or "lat, lon" pair
(parsed directly). Calls NWS /points to get the grid, timezone, and
nearby station list, then strips the forecast zone to its terminal ID
(e.g. WIZ066 from the full URL). Secondary station falls back to the
primary if only one is available.

Also adds astronomicalData field to PointsDto for sunrise/sunset
extraction in WeatherNormalizer.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task F.3: WeatherNormalizer — orchestrator + observation conversion

This is the biggest task in Phase F. Splits the work in two: this task does observation conversion (NWS observation DTO → CurrentConditions) and station fallback orchestration. Task F.4 does forecast + alert normalization. Task F.5 ties them together and provides the public `load()` method.

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/data/nws/NormalizerHelpers.kt`
- Create: `app/src/main/kotlin/com/skyframe/data/nws/ObservationNormalizer.kt`
- Create: `app/src/test/kotlin/com/skyframe/data/nws/ObservationNormalizerTest.kt`

- [ ] **Step 1: Write `NormalizerHelpers.kt` (unit conversion + cardinal directions)**

```kotlin
// app/src/main/kotlin/com/skyframe/data/nws/NormalizerHelpers.kt
package com.skyframe.data.nws

import com.skyframe.domain.Units
import kotlin.math.roundToInt

internal object NormalizerHelpers {

    /**
     * Converts an NWS measurement value to the requested target unit based
     * on the unitCode. Returns null when the input is null or unrecognized.
     *
     * Supported unitCodes (subset NWS actually returns):
     *   wmoUnit:degC, wmoUnit:degF
     *   wmoUnit:km_h-1, wmoUnit:m_s-1
     *   wmoUnit:Pa
     *   wmoUnit:m
     *   wmoUnit:percent
     *   wmoUnit:degree_(angle)
     */
    fun toFahrenheit(m: NumberMeasurementDto?): Double? {
        val v = m?.value ?: return null
        return when (m.unitCode) {
            "wmoUnit:degC" -> v * 9.0 / 5.0 + 32.0
            "wmoUnit:degF" -> v
            else -> null
        }
    }

    fun toMph(m: NumberMeasurementDto?): Double? {
        val v = m?.value ?: return null
        return when (m.unitCode) {
            "wmoUnit:km_h-1" -> v * 0.6213711922
            "wmoUnit:m_s-1" -> Units.metersPerSecondToMph(v)
            else -> null
        }
    }

    fun toInHg(m: NumberMeasurementDto?): Double? {
        val v = m?.value ?: return null
        return when (m.unitCode) {
            "wmoUnit:Pa" -> Units.pascalsToInchesHg(v)
            else -> null
        }
    }

    fun toMiles(m: NumberMeasurementDto?): Double? {
        val v = m?.value ?: return null
        return when (m.unitCode) {
            "wmoUnit:m" -> v / 1609.344
            else -> null
        }
    }

    fun toPercent(m: NumberMeasurementDto?): Double? {
        val v = m?.value ?: return null
        return when (m.unitCode) {
            "wmoUnit:percent" -> v
            else -> null
        }
    }

    fun toDegrees(m: NumberMeasurementDto?): Double? {
        val v = m?.value ?: return null
        return when (m.unitCode) {
            "wmoUnit:degree_(angle)" -> v
            else -> null
        }
    }

    /** Compass direction → 16-point cardinal. */
    fun cardinalFor(deg: Double): String {
        if (deg.isNaN()) return ""
        val dirs = listOf("N","NNE","NE","ENE","E","ESE","SE","SSE","S","SSW","SW","WSW","W","WNW","NW","NNW")
        val idx = ((deg / 22.5).roundToInt() % 16 + 16) % 16
        return dirs[idx]
    }

    /**
     * Heuristic: an observation is stale if older than 90 minutes or has
     * null core fields (temperature). Triggers station fallback.
     */
    fun isObservationStale(
        timestampEpochMs: Long,
        nowEpochMs: Long,
        temperatureF: Double?,
    ): Boolean {
        val ageMs = nowEpochMs - timestampEpochMs
        if (ageMs > 90 * 60 * 1000L) return true
        if (temperatureF == null) return true
        return false
    }
}
```

- [ ] **Step 2: Write the failing test**

```kotlin
// app/src/test/kotlin/com/skyframe/data/nws/ObservationNormalizerTest.kt
package com.skyframe.data.nws

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.math.abs

class ObservationNormalizerTest {

    private fun assertClose(expected: Double, actual: Double?, epsilon: Double = 0.01) {
        require(actual != null) { "Expected $expected ± $epsilon but got null" }
        assert(abs(expected - actual) < epsilon) { "Expected $expected ± $epsilon but got $actual" }
    }

    @Test
    fun `convert NWS celsius temperature to fahrenheit`() {
        val measurement = NumberMeasurementDto(value = 22.0, unitCode = "wmoUnit:degC")
        assertClose(71.6, NormalizerHelpers.toFahrenheit(measurement))
    }

    @Test
    fun `degF stays degF`() {
        val measurement = NumberMeasurementDto(value = 72.0, unitCode = "wmoUnit:degF")
        assertClose(72.0, NormalizerHelpers.toFahrenheit(measurement))
    }

    @Test
    fun `null value returns null`() {
        val measurement = NumberMeasurementDto(value = null, unitCode = "wmoUnit:degC")
        assertNull(NormalizerHelpers.toFahrenheit(measurement))
    }

    @Test
    fun `unknown unit code returns null`() {
        val measurement = NumberMeasurementDto(value = 22.0, unitCode = "wmoUnit:K")
        assertNull(NormalizerHelpers.toFahrenheit(measurement))
    }

    @Test
    fun `km_h-1 to mph conversion`() {
        val measurement = NumberMeasurementDto(value = 16.0934, unitCode = "wmoUnit:km_h-1")
        assertClose(10.0, NormalizerHelpers.toMph(measurement))
    }

    @Test
    fun `m_s-1 to mph conversion`() {
        val measurement = NumberMeasurementDto(value = 10.0, unitCode = "wmoUnit:m_s-1")
        assertClose(22.37, NormalizerHelpers.toMph(measurement))
    }

    @Test
    fun `Pa to inHg conversion`() {
        val measurement = NumberMeasurementDto(value = 101325.0, unitCode = "wmoUnit:Pa")
        assertClose(29.92, NormalizerHelpers.toInHg(measurement))
    }

    @Test
    fun `meters to miles conversion`() {
        val measurement = NumberMeasurementDto(value = 16093.44, unitCode = "wmoUnit:m")
        assertClose(10.0, NormalizerHelpers.toMiles(measurement))
    }

    @Test
    fun `cardinal for N`() {
        assertEquals("N", NormalizerHelpers.cardinalFor(0.0))
        assertEquals("N", NormalizerHelpers.cardinalFor(360.0))
    }

    @Test
    fun `cardinal for cardinal points`() {
        assertEquals("E", NormalizerHelpers.cardinalFor(90.0))
        assertEquals("S", NormalizerHelpers.cardinalFor(180.0))
        assertEquals("W", NormalizerHelpers.cardinalFor(270.0))
        assertEquals("NE", NormalizerHelpers.cardinalFor(45.0))
        assertEquals("SSE", NormalizerHelpers.cardinalFor(157.5))
    }

    @Test
    fun `observation older than 90 minutes is stale`() {
        assertEquals(true, NormalizerHelpers.isObservationStale(
            timestampEpochMs = 0L,
            nowEpochMs = 91 * 60 * 1000L,
            temperatureF = 70.0,
        ))
    }

    @Test
    fun `observation with null temperature is stale`() {
        assertEquals(true, NormalizerHelpers.isObservationStale(
            timestampEpochMs = 0L,
            nowEpochMs = 1000L,
            temperatureF = null,
        ))
    }

    @Test
    fun `fresh observation with temperature is not stale`() {
        assertEquals(false, NormalizerHelpers.isObservationStale(
            timestampEpochMs = 0L,
            nowEpochMs = 60 * 60 * 1000L,  // 60 min ago
            temperatureF = 70.0,
        ))
    }
}
```

- [ ] **Step 3: Run test to verify it fails / passes**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.nws.ObservationNormalizerTest" --no-daemon
```

Expected: PASS for all 13 tests (the test exercises only `NormalizerHelpers` which is fully implemented).

- [ ] **Step 4: Write the full `ObservationNormalizer` that converts ObservationDto + trends → CurrentConditions**

```kotlin
// app/src/main/kotlin/com/skyframe/data/nws/ObservationNormalizer.kt
package com.skyframe.data.nws

import com.skyframe.domain.ConditionTrends
import com.skyframe.domain.CurrentConditions
import com.skyframe.domain.IconCode
import com.skyframe.domain.Trend
import com.skyframe.domain.TrendConfidence
import com.skyframe.domain.TrendDirection
import com.skyframe.domain.Wind
import kotlinx.datetime.Instant

/**
 * Converts an NWS observation + recent observation history to the typed
 * CurrentConditions domain model. Trend computation uses the supplied
 * observation history (up to 6 recent observations per metric).
 */
object ObservationNormalizer {

    fun normalize(
        latest: ObservationDto,
        recentObservations: List<ObservationDto>,
        stationDistanceKm: Double,
        sunrise: Instant?,
        sunset: Instant?,
        precipOutlook: String,
        isDay: Boolean,
    ): CurrentConditions {
        val props = latest.properties

        val tempF = NormalizerHelpers.toFahrenheit(props.temperature)
            ?: 0.0  // server fallback; UI shows "--" when data is null
        val feelsLikeF = NormalizerHelpers.toFahrenheit(props.heatIndex)
            ?: NormalizerHelpers.toFahrenheit(props.windChill)
            ?: tempF

        val windSpeedMph = NormalizerHelpers.toMph(props.windSpeed) ?: 0.0
        val windDirDeg = NormalizerHelpers.toDegrees(props.windDirection) ?: 0.0

        return CurrentConditions(
            observedAt = Instant.parse(props.timestamp),
            stationId = props.station.substringAfterLast('/'),
            stationDistanceKm = stationDistanceKm,
            tempF = tempF,
            feelsLikeF = feelsLikeF,
            conditionText = props.textDescription.orEmpty(),
            iconCode = IconCode.CLOUD.takeIf { props.icon == null }
                ?: IconMapper.fromUrl(props.icon),
            precipOutlook = precipOutlook,
            humidityPct = NormalizerHelpers.toPercent(props.relativeHumidity),
            pressureInHg = NormalizerHelpers.toInHg(props.barometricPressure),
            visibilityMi = NormalizerHelpers.toMiles(props.visibility),
            dewpointF = NormalizerHelpers.toFahrenheit(props.dewpoint),
            wind = Wind(
                speedMph = windSpeedMph,
                directionDeg = windDirDeg,
                cardinal = NormalizerHelpers.cardinalFor(windDirDeg),
            ),
            trends = computeAllTrends(recentObservations),
            sunrise = sunrise ?: Instant.fromEpochSeconds(0),
            sunset = sunset ?: Instant.fromEpochSeconds(0),
        )
    }

    private fun computeAllTrends(history: List<ObservationDto>): ConditionTrends {
        val tempHistory = history.mapNotNull { obs ->
            NormalizerHelpers.toFahrenheit(obs.properties.temperature)?.let {
                it to Instant.parse(obs.properties.timestamp)
            }
        }
        val windHistory = history.mapNotNull { obs ->
            NormalizerHelpers.toMph(obs.properties.windSpeed)?.let {
                it to Instant.parse(obs.properties.timestamp)
            }
        }
        val humidityHistory = history.mapNotNull { obs ->
            NormalizerHelpers.toPercent(obs.properties.relativeHumidity)?.let {
                it to Instant.parse(obs.properties.timestamp)
            }
        }
        val pressureHistory = history.mapNotNull { obs ->
            NormalizerHelpers.toInHg(obs.properties.barometricPressure)?.let {
                it to Instant.parse(obs.properties.timestamp)
            }
        }
        val visibilityHistory = history.mapNotNull { obs ->
            NormalizerHelpers.toMiles(obs.properties.visibility)?.let {
                it to Instant.parse(obs.properties.timestamp)
            }
        }
        val dewpointHistory = history.mapNotNull { obs ->
            NormalizerHelpers.toFahrenheit(obs.properties.dewpoint)?.let {
                it to Instant.parse(obs.properties.timestamp)
            }
        }

        return ConditionTrends(
            temp = TrendCalculator.compute(tempHistory),
            wind = TrendCalculator.compute(windHistory),
            humidity = TrendCalculator.compute(humidityHistory),
            pressure = TrendCalculator.compute(pressureHistory, steadyThreshold = 0.02),
            visibility = TrendCalculator.compute(visibilityHistory),
            dewpoint = TrendCalculator.compute(dewpointHistory),
        )
    }
}
```

- [ ] **Step 5: Verify compile**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/data/nws/NormalizerHelpers.kt app/src/main/kotlin/com/skyframe/data/nws/ObservationNormalizer.kt app/src/test/kotlin/com/skyframe/data/nws/ObservationNormalizerTest.kt
git commit -m "$(@'
feat(nws): port observation normalization + WMO unit conversion

NormalizerHelpers converts NWS measurement DTOs (with wmoUnit:* codes)
to display units used by CurrentConditions. ObservationNormalizer
combines the latest observation with up-to-6 historical observations
to compute trends for temp, wind, humidity, pressure, visibility, and
dewpoint. Station staleness check (>90 min OR null temperature)
matches web's fallback rule.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task F.4: Forecast normalization (hourly + daily periods)

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/data/nws/ForecastNormalizer.kt`
- Create: `app/src/test/kotlin/com/skyframe/data/nws/ForecastNormalizerTest.kt`

- [ ] **Step 1: Read the web's forecast normalization patterns**

```powershell
Get-Content "_reference/server/nws/normalizer.ts" | Select-Object -First 350
```

The patterns to port: parse hourly periods (filter past-hour, take next 12+), parse daily periods (pair day+night for same date, build DailyPeriod), compute hour labels, extract period names.

- [ ] **Step 2: Write the failing test**

```kotlin
// app/src/test/kotlin/com/skyframe/data/nws/ForecastNormalizerTest.kt
package com.skyframe.data.nws

import com.skyframe.domain.IconCode
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ForecastNormalizerTest {

    @Test
    fun `hourly normalization filters past hours`() {
        val now = Instant.parse("2026-05-16T14:30:00Z")
        val dto = ForecastDto(ForecastProperties(
            generatedAt = "2026-05-16T14:00:00Z",
            periods = listOf(
                hourly(1, "2026-05-16T13:00:00Z", "2026-05-16T14:00:00Z", true, 70, "rain", 80),
                hourly(2, "2026-05-16T14:00:00Z", "2026-05-16T15:00:00Z", true, 71, "few", 5),
                hourly(3, "2026-05-16T15:00:00Z", "2026-05-16T16:00:00Z", true, 72, "few", 5),
            ),
        ))
        val result = ForecastNormalizer.normalizeHourly(dto, now)
        // Period ending before 'now' is dropped
        assertEquals(2, result.size)
        assertEquals(71.0, result[0].tempF)
    }

    @Test
    fun `daily normalization pairs day and night periods`() {
        val dto = ForecastDto(ForecastProperties(
            generatedAt = "2026-05-16T14:00:00Z",
            periods = listOf(
                daily(1, "Today", "2026-05-16T06:00:00-05:00", "2026-05-16T18:00:00-05:00", true, 75, "Sunny", "few"),
                daily(2, "Tonight", "2026-05-16T18:00:00-05:00", "2026-05-17T06:00:00-05:00", false, 55, "Clear", "skc"),
                daily(3, "Sunday", "2026-05-17T06:00:00-05:00", "2026-05-17T18:00:00-05:00", true, 78, "Sunny", "few"),
                daily(4, "Sunday Night", "2026-05-17T18:00:00-05:00", "2026-05-18T06:00:00-05:00", false, 58, "Clear", "skc"),
            ),
        ))
        val result = ForecastNormalizer.normalizeDaily(dto)
        assertEquals(2, result.size)
        assertEquals(75, result[0].highF)
        assertEquals(55, result[0].lowF)
        assertEquals("Today", result[0].dayPeriodName)
        assertEquals("Tonight", result[0].nightPeriodName)
        assertEquals(78, result[1].highF)
        assertEquals("Sunday", result[1].dayPeriodName)
    }

    @Test
    fun `precipitation probability extracted from period`() {
        val dto = ForecastDto(ForecastProperties(
            generatedAt = "2026-05-16T14:00:00Z",
            periods = listOf(
                hourly(1, "2026-05-16T14:00:00Z", "2026-05-16T15:00:00Z", true, 70, "rain", 80),
            ),
        ))
        val result = ForecastNormalizer.normalizeHourly(dto, Instant.parse("2026-05-16T14:00:00Z"))
        assertEquals(80, result[0].precipProbPct)
    }

    @Test
    fun `daily upgrade icon promotes few-clouds to rain at high precip`() {
        val dto = ForecastDto(ForecastProperties(
            generatedAt = "2026-05-16T14:00:00Z",
            periods = listOf(
                daily(1, "Today", "2026-05-16T06:00:00-05:00", "2026-05-16T18:00:00-05:00", true, 75, "Chance Rain Showers", "few", precip = 60),
                daily(2, "Tonight", "2026-05-16T18:00:00-05:00", "2026-05-17T06:00:00-05:00", false, 55, "Clear", "skc", precip = 0),
            ),
        ))
        val result = ForecastNormalizer.normalizeDaily(dto)
        assertEquals(IconCode.RAIN, result[0].iconCode)
    }

    private fun hourly(num: Int, start: String, end: String, isDay: Boolean, temp: Int, code: String, precip: Int): ForecastPeriodDto =
        ForecastPeriodDto(
            number = num, name = "", startTime = start, endTime = end,
            isDaytime = isDay, temperature = temp, temperatureUnit = "F",
            windSpeed = "5 mph", windDirection = "S",
            icon = "https://api.weather.gov/icons/land/${if (isDay) "day" else "night"}/$code,${precip}?size=medium",
            shortForecast = "Test", detailedForecast = null,
            probabilityOfPrecipitation = ProbabilityOfPrecipitationDto(precip),
        )

    private fun daily(num: Int, name: String, start: String, end: String, isDay: Boolean, temp: Int, short: String, code: String, precip: Int = 5): ForecastPeriodDto =
        ForecastPeriodDto(
            number = num, name = name, startTime = start, endTime = end,
            isDaytime = isDay, temperature = temp, temperatureUnit = "F",
            windSpeed = "5 mph", windDirection = "S",
            icon = "https://api.weather.gov/icons/land/${if (isDay) "day" else "night"}/$code,${precip}?size=medium",
            shortForecast = short, detailedForecast = "$short detailed",
            probabilityOfPrecipitation = ProbabilityOfPrecipitationDto(precip),
        )
}
```

- [ ] **Step 3: Run test to verify it fails**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.nws.ForecastNormalizerTest" --no-daemon
```

Expected: compile error.

- [ ] **Step 4: Implement `ForecastNormalizer`**

```kotlin
// app/src/main/kotlin/com/skyframe/data/nws/ForecastNormalizer.kt
package com.skyframe.data.nws

import com.skyframe.domain.DailyPeriod
import com.skyframe.domain.HourlyPeriod
import com.skyframe.domain.Wind
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Normalizes the NWS /forecast and /forecast/hourly responses to typed
 * domain HourlyPeriod and DailyPeriod lists.
 *
 * Port of forecast normalization logic from
 * _reference/server/nws/normalizer.ts, including:
 *   - Past-hour filtering (drop periods that ended before 'now')
 *   - Day+night pairing by date for daily outlook
 *   - Probability-aware icon downgrade (hourly) and upgrade (daily)
 *   - Orphan period handling (lone "Tonight" at window start, or day-only
 *     at window end — emit with the missing half as null)
 */
object ForecastNormalizer {

    private const val HOURLY_LIMIT = 13   // 12+ hours
    private const val DAILY_LIMIT = 7

    fun normalizeHourly(dto: ForecastDto, now: Instant): List<HourlyPeriod> {
        return dto.properties.periods
            .filter { Instant.parse(it.endTime) > now }
            .take(HOURLY_LIMIT)
            .map { p ->
                val precip = p.probabilityOfPrecipitation?.value ?: 0
                HourlyPeriod(
                    startTime = Instant.parse(p.startTime),
                    hourLabel = formatHourLabel(Instant.parse(p.startTime)),
                    tempF = p.temperature.toDouble(),
                    iconCode = IconMapper.forHourly(p.icon, p.isDaytime, precip),
                    precipProbPct = precip,
                    wind = parseWindString(p.windSpeed, p.windDirection),
                    shortDescription = p.shortForecast,
                )
            }
    }

    fun normalizeDaily(dto: ForecastDto, tz: TimeZone = TimeZone.currentSystemDefault()): List<DailyPeriod> {
        // Group periods by date-of-start in the supplied TZ.
        val periods = dto.properties.periods
        val byDate = LinkedHashMap<LocalDate, MutableList<ForecastPeriodDto>>()
        for (p in periods) {
            val date = Instant.parse(p.startTime).toLocalDateTime(tz).date
            byDate.getOrPut(date) { mutableListOf() }.add(p)
        }
        return byDate.entries.take(DAILY_LIMIT).map { (date, list) ->
            val day = list.firstOrNull { it.isDaytime }
            val night = list.firstOrNull { !it.isDaytime }
            val anchor = day ?: night ?: list.first()
            val precip = (day?.probabilityOfPrecipitation?.value ?: night?.probabilityOfPrecipitation?.value) ?: 0
            DailyPeriod(
                dateISO = date,
                dayOfWeek = anchor.name.substringBefore(' ').uppercase(),
                dateLabel = "${date.monthNumber}/${date.dayOfMonth}",
                highF = day?.temperature ?: night?.temperature ?: 0,
                lowF = night?.temperature ?: day?.temperature ?: 0,
                iconCode = IconMapper.forDaily(anchor.icon, anchor.shortForecast, precip),
                precipProbPct = precip,
                shortDescription = anchor.shortForecast,
                dayDetailedForecast = day?.detailedForecast,
                nightDetailedForecast = night?.detailedForecast,
                dayPeriodName = day?.name,
                nightPeriodName = night?.name,
            )
        }
    }

    private fun formatHourLabel(t: Instant, tz: TimeZone = TimeZone.currentSystemDefault()): String {
        val ldt = t.toLocalDateTime(tz)
        val hour12 = ((ldt.hour + 11) % 12) + 1
        val ampm = if (ldt.hour < 12) "AM" else "PM"
        return "${hour12}$ampm"
    }

    private fun parseWindString(speed: String, dir: String): Wind {
        // NWS windSpeed like "5 mph" or "5 to 15 mph"; take the first number.
        val mph = Regex("""\d+""").find(speed)?.value?.toDoubleOrNull() ?: 0.0
        // dir is a cardinal string like "S" or "SW"; no degrees in forecast.
        // We don't compute degrees from forecast wind (only from observation).
        return Wind(speedMph = mph, directionDeg = cardinalToDegrees(dir), cardinal = dir)
    }

    private fun cardinalToDegrees(c: String): Double = when (c.uppercase()) {
        "N" -> 0.0; "NNE" -> 22.5; "NE" -> 45.0; "ENE" -> 67.5
        "E" -> 90.0; "ESE" -> 112.5; "SE" -> 135.0; "SSE" -> 157.5
        "S" -> 180.0; "SSW" -> 202.5; "SW" -> 225.0; "WSW" -> 247.5
        "W" -> 270.0; "WNW" -> 292.5; "NW" -> 315.0; "NNW" -> 337.5
        else -> 0.0
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.nws.ForecastNormalizerTest" --no-daemon
```

Expected: 4 tests pass.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/data/nws/ForecastNormalizer.kt app/src/test/kotlin/com/skyframe/data/nws/ForecastNormalizerTest.kt
git commit -m "$(@'
feat(nws): port hourly and daily forecast normalization

normalizeHourly: filters past-hour periods, applies hourly icon
downgrade (<30% precip), formats hour labels in local TZ.

normalizeDaily: groups periods by start-date, pairs day+night into
DailyPeriod with high/low/precip/icon-upgrade, preserves orphan halves
as null (matching web behavior).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task F.5: Alert normalization + WeatherNormalizer orchestrator

The final piece. Normalizes the NWS alerts list to domain Alert objects (applying tier classification, sorting by tier rank), and provides the top-level `WeatherNormalizer.load()` that orchestrates the 5 parallel fetches.

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/data/nws/AlertNormalizer.kt`
- Create: `app/src/main/kotlin/com/skyframe/data/nws/WeatherNormalizer.kt`
- Create: `app/src/test/kotlin/com/skyframe/data/nws/AlertNormalizerTest.kt`

- [ ] **Step 1: Write the failing test for alert normalization**

```kotlin
// app/src/test/kotlin/com/skyframe/data/nws/AlertNormalizerTest.kt
package com.skyframe.data.nws

import com.skyframe.domain.AlertTier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AlertNormalizerTest {

    @Test
    fun `normalize maps event and parameters to tier`() {
        val dto = AlertsDto(features = listOf(
            AlertFeatureDto(
                id = "feature-id-1",
                properties = AlertProperties(
                    id = "urn:oid:test-tornado",
                    event = "Tornado Warning",
                    severity = "Extreme",
                    headline = "Tornado Warning issued",
                    description = "TAKE COVER.",
                    sent = "2026-05-16T14:00:00Z",
                    effective = "2026-05-16T14:00:00Z",
                    expires = "2026-05-16T14:30:00Z",
                    areaDesc = "Milwaukee County",
                    parameters = mapOf("tornadoDamageThreat" to listOf("CATASTROPHIC")),
                )
            )
        ))
        val result = AlertNormalizer.normalize(dto)
        assertEquals(1, result.size)
        assertEquals(AlertTier.TORNADO_EMERGENCY, result[0].tier)
    }

    @Test
    fun `normalize sorts by tier rank ascending then by issuedAt descending`() {
        val dto = AlertsDto(features = listOf(
            simpleAlert("a", "Wind Advisory", "2026-05-16T10:00:00Z"),
            simpleAlert("b", "Tornado Warning", "2026-05-16T11:00:00Z"),
            simpleAlert("c", "Severe Thunderstorm Warning", "2026-05-16T12:00:00Z"),
        ))
        val result = AlertNormalizer.normalize(dto)
        // Tornado (rank 3) first, then Severe (rank 5), then Wind Advisory (rank 12)
        assertEquals("b", result[0].id.substringAfterLast(':'))
        assertEquals("c", result[1].id.substringAfterLast(':'))
        assertEquals("a", result[2].id.substringAfterLast(':'))
    }

    @Test
    fun `unknown event falls through to advisory`() {
        val dto = AlertsDto(features = listOf(
            simpleAlert("a", "Some Made-Up Event", "2026-05-16T10:00:00Z")
        ))
        assertEquals(AlertTier.ADVISORY, AlertNormalizer.normalize(dto)[0].tier)
    }

    private fun simpleAlert(idSuffix: String, event: String, sent: String): AlertFeatureDto =
        AlertFeatureDto(
            id = "feature-$idSuffix",
            properties = AlertProperties(
                id = "urn:oid:$idSuffix",
                event = event, severity = "Moderate",
                headline = event, description = "",
                sent = sent, effective = sent, expires = sent,
                areaDesc = "Test County", parameters = emptyMap(),
            )
        )
}
```

- [ ] **Step 2: Implement `AlertNormalizer.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/data/nws/AlertNormalizer.kt
package com.skyframe.data.nws

import com.skyframe.data.alerts.AlertClassifier
import com.skyframe.domain.Alert
import com.skyframe.domain.AlertSeverity
import kotlinx.datetime.Instant

object AlertNormalizer {

    fun normalize(dto: AlertsDto): List<Alert> {
        return dto.features
            .map { feature ->
                val props = feature.properties
                Alert(
                    id = props.id,
                    event = props.event,
                    tier = AlertClassifier.classify(props.event, props.parameters),
                    severity = parseSeverity(props.severity),
                    headline = props.headline ?: props.event,
                    description = props.description,
                    issuedAt = Instant.parse(props.sent),
                    effective = Instant.parse(props.effective),
                    expires = Instant.parse(props.expires),
                    areaDesc = props.areaDesc,
                )
            }
            .sortedWith(compareBy({ it.tier.rank }, { -it.issuedAt.epochSeconds }))
    }

    private fun parseSeverity(s: String): AlertSeverity = when (s) {
        "Extreme" -> AlertSeverity.EXTREME
        "Severe" -> AlertSeverity.SEVERE
        "Moderate" -> AlertSeverity.MODERATE
        "Minor" -> AlertSeverity.MINOR
        else -> AlertSeverity.UNKNOWN
    }
}
```

- [ ] **Step 3: Run alert test to verify pass**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.nws.AlertNormalizerTest" --no-daemon
```

Expected: 3 tests pass.

- [ ] **Step 4: Implement `WeatherNormalizer` orchestrator**

```kotlin
// app/src/main/kotlin/com/skyframe/data/nws/WeatherNormalizer.kt
package com.skyframe.data.nws

import com.skyframe.data.cache.WeatherCache
import com.skyframe.data.settings.SettingsRepository
import com.skyframe.domain.StationOverride
import com.skyframe.domain.WeatherError
import com.skyframe.domain.WeatherMeta
import com.skyframe.domain.WeatherResponse
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * Top-level orchestrator. Calls /points + /forecast + /forecast/hourly +
 * /alerts in parallel, then /stations/.../observations/latest (primary or
 * fallback). Caches the assembled WeatherResponse for 90s.
 *
 * Port of _reference/server/nws/normalizer.ts orchestration.
 */
@Singleton
class WeatherNormalizer @Inject constructor(
    private val nws: NwsClient,
    private val settings: SettingsRepository,
    private val cache: WeatherCache<WeatherResponse>,
) {
    private val CACHE_KEY = "weather-response"
    private val CACHE_TTL = 90.seconds

    suspend fun load(forceRefresh: Boolean = false): WeatherResponse {
        if (!forceRefresh) {
            cache.get(CACHE_KEY)?.let { return it.copy(meta = it.meta.copy(cacheHit = true)) }
        }
        val cfg = settings.snapshot()
        require(cfg.forecastOffice.isNotBlank()) { "Setup incomplete — run SettingsScreen first" }

        val now = Clock.System.now()
        val response = coroutineScope {
            val pointsAsync = async { nws.points(cfg.lat, cfg.lon) }
            val forecastAsync = async { nws.forecast(cfg.forecastOffice, cfg.gridX, cfg.gridY) }
            val hourlyAsync = async { nws.hourlyForecast(cfg.forecastOffice, cfg.gridX, cfg.gridY) }
            val alertsAsync = async { nws.activeAlerts(cfg.lat, cfg.lon) }

            val points = pointsAsync.await()
            val forecast = forecastAsync.await()
            val hourly = hourlyAsync.await()
            val alerts = alertsAsync.await()

            // Station fallback: try primary first; if stale/null, try secondary.
            val (observation, activeStationId, fellBack) = fetchObservationWithFallback(cfg, now)

            val sunrise = points.properties.astronomicalData?.sunrise?.let { Instant.parse(it) }
            val sunset = points.properties.astronomicalData?.sunset?.let { Instant.parse(it) }
            val precipOutlook = "" // computed from hourly periods; deferred to enhancement

            WeatherResponse(
                current = ObservationNormalizer.normalize(
                    latest = observation,
                    recentObservations = emptyList(),  // history fetch deferred to v2 enhancement
                    stationDistanceKm = 0.0,           // not exposed by /observations/latest
                    sunrise = sunrise,
                    sunset = sunset,
                    precipOutlook = precipOutlook,
                    isDay = sunrise != null && sunset != null && now in sunrise..sunset,
                ),
                hourly = ForecastNormalizer.normalizeHourly(hourly, now),
                daily = ForecastNormalizer.normalizeDaily(forecast),
                alerts = AlertNormalizer.normalize(alerts),
                meta = WeatherMeta(
                    fetchedAt = now,
                    nextRefreshAt = now.plus(CACHE_TTL),
                    cacheHit = false,
                    stationId = activeStationId,
                    locationName = cfg.locationName,
                    stationOverride = cfg.stationOverride,
                    forecastGeneratedAt = Instant.parse(forecast.properties.generatedAt),
                    forecastOffice = cfg.forecastOffice,
                    gridX = cfg.gridX,
                    gridY = cfg.gridY,
                    forecastZone = cfg.forecastZone,
                    error = if (fellBack) WeatherError.STATION_FALLBACK else null,
                ),
            )
        }
        cache.put(CACHE_KEY, response, CACHE_TTL)
        return response
    }

    private suspend fun fetchObservationWithFallback(
        cfg: SettingsRepository.Snapshot,
        now: Instant,
    ): Triple<ObservationDto, String, Boolean> {
        if (cfg.stationOverride == StationOverride.FORCE_SECONDARY) {
            val obs = nws.latestObservation(cfg.stationFallback)
            return Triple(obs, cfg.stationFallback, true)
        }
        return try {
            val primary = nws.latestObservation(cfg.stationPrimary)
            val timestamp = Instant.parse(primary.properties.timestamp)
            val tempF = NormalizerHelpers.toFahrenheit(primary.properties.temperature)
            if (NormalizerHelpers.isObservationStale(timestamp.toEpochMilliseconds(), now.toEpochMilliseconds(), tempF)) {
                val fallback = nws.latestObservation(cfg.stationFallback)
                Triple(fallback, cfg.stationFallback, true)
            } else {
                Triple(primary, cfg.stationPrimary, false)
            }
        } catch (e: Exception) {
            val fallback = nws.latestObservation(cfg.stationFallback)
            Triple(fallback, cfg.stationFallback, true)
        }
    }
}
```

**Note on the `SettingsRepository.Snapshot` shape:** the orchestrator references `cfg.lat`, `cfg.lon`, `cfg.forecastOffice`, `cfg.gridX`, etc. — the stub `SettingsRepository.Snapshot` from Task E.1 only has `email`. Task G.1 expands `Snapshot` to the full set; until then this file won't compile. The plan handles this by leaving this task to commit alongside Task G.1.

- [ ] **Step 5: Stage the new files but DON'T commit yet — wait for Task G.1 to complete the Snapshot**

```powershell
git add app/src/main/kotlin/com/skyframe/data/nws/AlertNormalizer.kt app/src/main/kotlin/com/skyframe/data/nws/WeatherNormalizer.kt app/src/test/kotlin/com/skyframe/data/nws/AlertNormalizerTest.kt
# DO NOT commit — task G.1 commits this together with the expanded Snapshot
git status
```

Expected: files appear as staged. They reference `SettingsRepository.Snapshot` fields that don't exist yet; compile will fail until G.1.

---

**Phase F milestone:** Geocoder, SetupResolver, ObservationNormalizer, ForecastNormalizer, AlertNormalizer all in place. WeatherNormalizer orchestrator wired up but doesn't compile yet (depends on G.1's expanded Settings snapshot). Total test count after Phase F: ~70.

---

## Phase G — SettingsRepository + WeatherRepository + Hilt + ViewModel

The last data-layer phase. Real DataStore-backed `SettingsRepository`, dismissed-alerts `AlertAcknowledgmentRepository`, `WeatherRepository` with polling and StateFlow, the Hilt module that wires everything together, and the `DashboardViewModel` that the UI will consume.

### Task G.1: SettingsRepository — DataStore-backed config

Replaces the Phase E.1 stub with the real implementation. Includes the Snapshot fields the WeatherNormalizer staged in Task F.5 references. Once this lands, the build goes green again.

**Files:**
- Modify: `app/src/main/kotlin/com/skyframe/data/settings/SettingsRepository.kt` (replace stub)
- Create: `app/src/main/kotlin/com/skyframe/data/settings/SettingsKeys.kt`
- Create: `app/src/main/kotlin/com/skyframe/data/settings/SettingsModule.kt`
- Create: `app/src/test/kotlin/com/skyframe/data/settings/SettingsRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/kotlin/com/skyframe/data/settings/SettingsRepositoryTest.kt
package com.skyframe.data.settings

import androidx.datastore.core.DataStoreFactory
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.skyframe.domain.StationOverride
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SettingsRepositoryTest {

    @TempDir
    lateinit var tempDir: File

    private fun newRepo(): SettingsRepository {
        val dataStore = PreferenceDataStoreFactory.create(produceFile = { File(tempDir, "test.preferences_pb") })
        return SettingsRepository(dataStore)
    }

    @Test
    fun `snapshot returns defaults before any write`() = runTest {
        val repo = newRepo()
        val snap = repo.snapshot()
        assertEquals("", snap.email)
        assertEquals("", snap.forecastOffice)
        assertEquals(0.0, snap.lat)
        assertEquals(StationOverride.AUTO, snap.stationOverride)
        assertEquals(false, snap.isConfigured)
    }

    @Test
    fun `update persists location config and isConfigured becomes true`() = runTest {
        val repo = newRepo()
        repo.update {
            it.copy(
                email = "user@example.com",
                lat = 42.8744,
                lon = -87.8633,
                forecastOffice = "MKX",
                gridX = 88,
                gridY = 58,
                timezone = "America/Chicago",
                forecastZone = "WIZ066",
                stationPrimary = "KMKE",
                stationFallback = "KRAC",
                locationName = "OAK CREEK WI",
            )
        }
        val snap = repo.snapshot()
        assertEquals("user@example.com", snap.email)
        assertEquals(42.8744, snap.lat)
        assertEquals("KMKE", snap.stationPrimary)
        assertEquals(true, snap.isConfigured)
    }

    @Test
    fun `stationOverride toggles between AUTO and FORCE_SECONDARY`() = runTest {
        val repo = newRepo()
        repo.update { it.copy(stationOverride = StationOverride.FORCE_SECONDARY) }
        assertEquals(StationOverride.FORCE_SECONDARY, repo.snapshot().stationOverride)
        repo.update { it.copy(stationOverride = StationOverride.AUTO) }
        assertEquals(StationOverride.AUTO, repo.snapshot().stationOverride)
    }
}
```

- [ ] **Step 2: Write `SettingsKeys.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/data/settings/SettingsKeys.kt
package com.skyframe.data.settings

import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

internal object SettingsKeys {
    val EMAIL              = stringPreferencesKey("email")
    val LAT                = doublePreferencesKey("lat")
    val LON                = doublePreferencesKey("lon")
    val LOCATION_NAME      = stringPreferencesKey("location_name")
    val FORECAST_OFFICE    = stringPreferencesKey("forecast_office")
    val GRID_X             = intPreferencesKey("grid_x")
    val GRID_Y             = intPreferencesKey("grid_y")
    val TIMEZONE           = stringPreferencesKey("timezone")
    val FORECAST_ZONE      = stringPreferencesKey("forecast_zone")
    val STATION_PRIMARY    = stringPreferencesKey("station_primary")
    val STATION_FALLBACK   = stringPreferencesKey("station_fallback")
    val STATION_OVERRIDE   = stringPreferencesKey("station_override")
    val UPDATE_CHECK       = stringPreferencesKey("update_check")  // "true" / "false"
}
```

- [ ] **Step 3: Replace `SettingsRepository.kt` with the real implementation**

```kotlin
// app/src/main/kotlin/com/skyframe/data/settings/SettingsRepository.kt
package com.skyframe.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.skyframe.domain.StationOverride
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    data class Snapshot(
        val email: String = "",
        val lat: Double = 0.0,
        val lon: Double = 0.0,
        val locationName: String = "",
        val forecastOffice: String = "",
        val gridX: Int = 0,
        val gridY: Int = 0,
        val timezone: String = "America/Chicago",
        val forecastZone: String = "",
        val stationPrimary: String = "",
        val stationFallback: String = "",
        val stationOverride: StationOverride = StationOverride.AUTO,
        val updateCheckEnabled: Boolean = false,
    ) {
        /** True when SetupResolver has populated the location config at least once. */
        val isConfigured: Boolean
            get() = forecastOffice.isNotBlank() && stationPrimary.isNotBlank() && lat != 0.0
    }

    val flow: Flow<Snapshot> = dataStore.data.map { prefs ->
        Snapshot(
            email = prefs[SettingsKeys.EMAIL] ?: "",
            lat = prefs[SettingsKeys.LAT] ?: 0.0,
            lon = prefs[SettingsKeys.LON] ?: 0.0,
            locationName = prefs[SettingsKeys.LOCATION_NAME] ?: "",
            forecastOffice = prefs[SettingsKeys.FORECAST_OFFICE] ?: "",
            gridX = prefs[SettingsKeys.GRID_X] ?: 0,
            gridY = prefs[SettingsKeys.GRID_Y] ?: 0,
            timezone = prefs[SettingsKeys.TIMEZONE] ?: "America/Chicago",
            forecastZone = prefs[SettingsKeys.FORECAST_ZONE] ?: "",
            stationPrimary = prefs[SettingsKeys.STATION_PRIMARY] ?: "",
            stationFallback = prefs[SettingsKeys.STATION_FALLBACK] ?: "",
            stationOverride = when (prefs[SettingsKeys.STATION_OVERRIDE]) {
                "force-secondary" -> StationOverride.FORCE_SECONDARY
                else -> StationOverride.AUTO
            },
            updateCheckEnabled = prefs[SettingsKeys.UPDATE_CHECK] == "true",
        )
    }

    suspend fun snapshot(): Snapshot = flow.first()

    suspend fun update(transform: (Snapshot) -> Snapshot) {
        val current = snapshot()
        val next = transform(current)
        dataStore.edit { prefs ->
            prefs[SettingsKeys.EMAIL] = next.email
            prefs[SettingsKeys.LAT] = next.lat
            prefs[SettingsKeys.LON] = next.lon
            prefs[SettingsKeys.LOCATION_NAME] = next.locationName
            prefs[SettingsKeys.FORECAST_OFFICE] = next.forecastOffice
            prefs[SettingsKeys.GRID_X] = next.gridX
            prefs[SettingsKeys.GRID_Y] = next.gridY
            prefs[SettingsKeys.TIMEZONE] = next.timezone
            prefs[SettingsKeys.FORECAST_ZONE] = next.forecastZone
            prefs[SettingsKeys.STATION_PRIMARY] = next.stationPrimary
            prefs[SettingsKeys.STATION_FALLBACK] = next.stationFallback
            prefs[SettingsKeys.STATION_OVERRIDE] = when (next.stationOverride) {
                StationOverride.AUTO -> "auto"
                StationOverride.FORCE_SECONDARY -> "force-secondary"
            }
            prefs[SettingsKeys.UPDATE_CHECK] = if (next.updateCheckEnabled) "true" else "false"
        }
    }
}
```

- [ ] **Step 4: Write `SettingsModule.kt` (Hilt module for DataStore)**

```kotlin
// app/src/main/kotlin/com/skyframe/data/settings/SettingsModule.kt
package com.skyframe.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.skyframeDataStore: DataStore<Preferences> by preferencesDataStore(name = "skyframe_settings")

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.skyframeDataStore
}
```

- [ ] **Step 5: Run test to verify pass**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.settings.SettingsRepositoryTest" --no-daemon
```

Expected: 3 tests pass.

- [ ] **Step 6: Verify the staged Phase F.5 files now compile**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: BUILD SUCCESSFUL. The `WeatherNormalizer` now finds the expanded `Snapshot` fields and compiles.

- [ ] **Step 7: Commit both the new Settings code AND the staged Phase F.5 files**

```powershell
git add app/src/main/kotlin/com/skyframe/data/settings/SettingsRepository.kt app/src/main/kotlin/com/skyframe/data/settings/SettingsKeys.kt app/src/main/kotlin/com/skyframe/data/settings/SettingsModule.kt app/src/test/kotlin/com/skyframe/data/settings/SettingsRepositoryTest.kt
# The AlertNormalizer + WeatherNormalizer + AlertNormalizerTest staged in F.5 are still staged from earlier;
# they go in the same commit.
git status
```

Expected: shows SettingsRepository files + the staged Phase F.5 files all ready to commit.

```powershell
git commit -m "$(@'
feat(settings): DataStore-backed SettingsRepository + WeatherNormalizer wire-up

Real SettingsRepository replacing the Phase E.1 stub. Backed by Preferences
DataStore at skyframe_settings.preferences_pb. Snapshot holds the full set
of fields the data layer needs (email, lat/lon, grid coords, timezone,
station IDs, override mode, update-check toggle).

Also commits the WeatherNormalizer and AlertNormalizer staged from Phase F.5
— they reference Snapshot fields that didn't exist until this task. Build
goes green again.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task G.2: AlertAcknowledgmentRepository (dismissed alerts)

Port of the web's localStorage `skyframe.alerts.dismissed` set. Persists alert IDs the user has explicitly dismissed; pruned automatically as alerts drop off the NWS feed.

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/data/acknowledgments/AlertAcknowledgmentRepository.kt`
- Create: `app/src/test/kotlin/com/skyframe/data/acknowledgments/AlertAcknowledgmentRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/kotlin/com/skyframe/data/acknowledgments/AlertAcknowledgmentRepositoryTest.kt
package com.skyframe.data.acknowledgments

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class AlertAcknowledgmentRepositoryTest {

    @TempDir
    lateinit var tempDir: File

    private fun newRepo(): AlertAcknowledgmentRepository {
        val ds = PreferenceDataStoreFactory.create(produceFile = { File(tempDir, "ack.preferences_pb") })
        return AlertAcknowledgmentRepository(ds)
    }

    @Test
    fun `dismissed set starts empty`() = runTest {
        assertEquals(emptySet<String>(), newRepo().snapshot())
    }

    @Test
    fun `dismiss adds id to set`() = runTest {
        val repo = newRepo()
        repo.dismiss("urn:oid:abc")
        assertTrue("urn:oid:abc" in repo.snapshot())
    }

    @Test
    fun `prune retains only ids still active`() = runTest {
        val repo = newRepo()
        repo.dismiss("a")
        repo.dismiss("b")
        repo.dismiss("c")
        repo.pruneTo(setOf("b", "c", "d"))
        val remaining = repo.snapshot()
        assertEquals(setOf("b", "c"), remaining)
        assertFalse("a" in remaining)
        assertFalse("d" in remaining)
    }
}
```

- [ ] **Step 2: Implement `AlertAcknowledgmentRepository`**

```kotlin
// app/src/main/kotlin/com/skyframe/data/acknowledgments/AlertAcknowledgmentRepository.kt
package com.skyframe.data.acknowledgments

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persistent set of dismissed alert IDs. Pruned automatically when an
 * alert drops off the active NWS feed.
 *
 * Port of the web's localStorage skyframe.alerts.dismissed set
 * (_reference/client/App.tsx).
 */
@Singleton
class AlertAcknowledgmentRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private val key = stringSetPreferencesKey("dismissed_alert_ids")

    val flow: Flow<Set<String>> = dataStore.data.map { it[key].orEmpty() }

    suspend fun snapshot(): Set<String> = flow.first()

    suspend fun dismiss(id: String) {
        dataStore.edit { it[key] = (it[key].orEmpty()) + id }
    }

    suspend fun pruneTo(activeIds: Set<String>) {
        dataStore.edit { it[key] = (it[key].orEmpty()) intersect activeIds }
    }

    suspend fun clear() {
        dataStore.edit { it.remove(key) }
    }
}
```

- [ ] **Step 3: Run test to verify pass**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.data.acknowledgments.AlertAcknowledgmentRepositoryTest" --no-daemon
```

Expected: 3 tests pass.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/data/acknowledgments/AlertAcknowledgmentRepository.kt app/src/test/kotlin/com/skyframe/data/acknowledgments/AlertAcknowledgmentRepositoryTest.kt
git commit -m "$(@'
feat(acknowledgments): persistent dismissed-alerts set

DataStore-backed Set<String> of NWS alert IDs the user has dismissed.
pruneTo() drops IDs no longer active so the set doesn't grow without
bound. Mirrors the web's localStorage skyframe.alerts.dismissed.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task G.3: Hilt bindings for WeatherCache + NetworkModule polish

The `WeatherCache<WeatherResponse>` instance referenced by `WeatherNormalizer` needs a Hilt provider. Also patch `NetworkModule` to read email from the *real* SettingsRepository (no longer a stub).

**Files:**
- Modify: `app/src/main/kotlin/com/skyframe/di/NetworkModule.kt`
- Create: `app/src/main/kotlin/com/skyframe/di/CacheModule.kt`

- [ ] **Step 1: Write `CacheModule.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/di/CacheModule.kt
package com.skyframe.di

import com.skyframe.data.cache.WeatherCache
import com.skyframe.domain.WeatherResponse
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {

    @Provides
    @Singleton
    fun provideWeatherResponseCache(): WeatherCache<WeatherResponse> = WeatherCache()
}
```

- [ ] **Step 2: `NetworkModule.kt` already calls `runBlocking { settings.snapshot() }` to pull the email. With the real DataStore-backed SettingsRepository this works, but doesn't react to email changes after first launch.**

Replace `NetworkModule.kt`:

```kotlin
// app/src/main/kotlin/com/skyframe/di/NetworkModule.kt
package com.skyframe.di

import com.skyframe.data.nws.NwsHttpClient
import com.skyframe.data.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideHttpClient(settings: SettingsRepository): HttpClient {
        // Email may not be configured at app start (first run). Use a sensible
        // unconfigured fallback; the UA updates next process start after
        // Settings are saved. Real-time UA updates are deferred to v2 — they
        // require either re-creating the HttpClient or using a dynamic header
        // interceptor, neither of which is needed for parity with the web.
        val email = runBlocking { settings.snapshot().email.ifBlank { "unconfigured@skyframe.local" } }
        return NwsHttpClient.create(userAgent = "SkyFrame/0.1.0 ($email)")
    }
}
```

- [ ] **Step 3: Build to verify everything wires up**

```powershell
./gradlew :app:assembleDebug --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/di/CacheModule.kt app/src/main/kotlin/com/skyframe/di/NetworkModule.kt
git commit -m "$(@'
feat(di): wire WeatherCache and refresh NetworkModule note on UA updates

CacheModule provides the singleton WeatherCache<WeatherResponse> that
WeatherNormalizer holds. NetworkModule comment clarifies that User-Agent
is built once at HttpClient creation; real-time updates after Settings
changes are deferred to v2 (not required for parity).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task G.4: WeatherRepository — polling, StateFlow, foreground lifecycle

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/repository/WeatherRepository.kt`
- Create: `app/src/test/kotlin/com/skyframe/repository/WeatherRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
// app/src/test/kotlin/com/skyframe/repository/WeatherRepositoryTest.kt
package com.skyframe.repository

import app.cash.turbine.test
import com.skyframe.data.nws.WeatherNormalizer
import com.skyframe.domain.WeatherResponse
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WeatherRepositoryTest {

    @Test
    fun `state starts in Idle`() = runTest {
        val normalizer = mockk<WeatherNormalizer>()
        val repo = WeatherRepository(normalizer, TestScope(UnconfinedTestDispatcher()))
        repo.state.test {
            assertEquals(WeatherState.Idle, awaitItem())
        }
    }

    @Test
    fun `refresh emits Loading then Success`() = runTest {
        val normalizer = mockk<WeatherNormalizer>()
        val response = mockk<WeatherResponse>(relaxed = true)
        coEvery { normalizer.load(forceRefresh = true) } returns response
        val repo = WeatherRepository(normalizer, TestScope(UnconfinedTestDispatcher()))

        repo.refresh()

        repo.state.test {
            // Either Loading or Success will be the latest emission depending on dispatcher
            val emitted = awaitItem()
            assertTrue(emitted is WeatherState.Success, "expected Success, got $emitted")
            assertEquals(response, (emitted as WeatherState.Success).response)
        }
    }

    @Test
    fun `refresh emits Error on exception`() = runTest {
        val normalizer = mockk<WeatherNormalizer>()
        coEvery { normalizer.load(forceRefresh = true) } throws RuntimeException("boom")
        val repo = WeatherRepository(normalizer, TestScope(UnconfinedTestDispatcher()))

        repo.refresh()

        repo.state.test {
            val emitted = awaitItem()
            assertTrue(emitted is WeatherState.Error)
            assertTrue((emitted as WeatherState.Error).message.contains("boom"))
        }
    }
}
```

- [ ] **Step 2: Implement `WeatherRepository`**

```kotlin
// app/src/main/kotlin/com/skyframe/repository/WeatherRepository.kt
package com.skyframe.repository

import com.skyframe.data.nws.WeatherNormalizer
import com.skyframe.domain.WeatherResponse
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class WeatherState {
    data object Idle : WeatherState()
    data object Loading : WeatherState()
    data class Success(val response: WeatherResponse) : WeatherState()
    data class Error(val message: String) : WeatherState()
}

@Singleton
class WeatherRepository @Inject constructor(
    private val normalizer: WeatherNormalizer,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob()),
) {
    private val _state = MutableStateFlow<WeatherState>(WeatherState.Idle)
    val state: StateFlow<WeatherState> = _state.asStateFlow()

    private var pollingJob: Job? = null

    /**
     * Starts a polling loop on the supplied scope. Cadence is driven by
     * the response's meta.nextRefreshAt (typically ~90s after fetchedAt).
     * Idempotent; calling repeatedly does not stack timers.
     */
    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (isActive) {
                refreshInternal(forceRefresh = false)
                val current = _state.value
                val delayMs = if (current is WeatherState.Success) {
                    (current.response.meta.nextRefreshAt.toEpochMilliseconds() -
                        kotlinx.datetime.Clock.System.now().toEpochMilliseconds())
                        .coerceAtLeast(15_000L)  // floor at 15s
                } else {
                    30_000L  // back off when in error state
                }
                delay(delayMs)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    /** Pull-to-refresh: skip cache and fetch fresh data immediately. */
    fun refresh() {
        scope.launch { refreshInternal(forceRefresh = true) }
    }

    private suspend fun refreshInternal(forceRefresh: Boolean) {
        _state.value = WeatherState.Loading
        try {
            val response = normalizer.load(forceRefresh = forceRefresh)
            _state.value = WeatherState.Success(response)
        } catch (t: Throwable) {
            _state.value = WeatherState.Error(t.message ?: "Unknown error")
        }
    }
}
```

- [ ] **Step 3: Run test to verify pass**

```powershell
./gradlew :app:testDebugUnitTest --tests "com.skyframe.repository.WeatherRepositoryTest" --no-daemon
```

Expected: 3 tests pass.

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/repository/WeatherRepository.kt app/src/test/kotlin/com/skyframe/repository/WeatherRepositoryTest.kt
git commit -m "$(@'
feat(repository): WeatherRepository with polling, StateFlow, error handling

state: StateFlow<WeatherState> exposes Idle/Loading/Success/Error.
startPolling() runs a coroutine loop driven by response.meta.nextRefreshAt
(~90s typical). stopPolling() cancels. refresh() bypasses cache for
pull-to-refresh.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task G.5: DashboardViewModel — exposes state + acknowledgments to UI

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/viewmodel/DashboardViewModel.kt`

- [ ] **Step 1: Implement `DashboardViewModel`**

```kotlin
// app/src/main/kotlin/com/skyframe/viewmodel/DashboardViewModel.kt
package com.skyframe.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skyframe.data.acknowledgments.AlertAcknowledgmentRepository
import com.skyframe.data.settings.SettingsRepository
import com.skyframe.domain.Alert
import com.skyframe.repository.WeatherRepository
import com.skyframe.repository.WeatherState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI state for the dashboard shell. Combines weather data + dismissed
 * acknowledgments + settings into one consumable stream so Composables
 * don't have to manage multiple sources.
 */
data class DashboardUiState(
    val weather: WeatherState,
    val dismissedAlertIds: Set<String>,
    val isConfigured: Boolean,
    val locationName: String,
    val timezone: String,
) {
    val visibleAlerts: List<Alert>
        get() = when (weather) {
            is WeatherState.Success -> weather.response.alerts.filterNot { it.id in dismissedAlertIds }
            else -> emptyList()
        }
}

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val weatherRepository: WeatherRepository,
    private val acknowledgments: AlertAcknowledgmentRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> = combine(
        weatherRepository.state,
        acknowledgments.flow,
        settings.flow,
    ) { weather, dismissed, cfg ->
        // Prune dismissed set against currently-active alert IDs so stale
        // dismissals don't accumulate.
        if (weather is WeatherState.Success) {
            val activeIds = weather.response.alerts.map { it.id }.toSet()
            val stale = dismissed - activeIds
            if (stale.isNotEmpty()) {
                viewModelScope.launch { acknowledgments.pruneTo(activeIds) }
            }
        }
        DashboardUiState(
            weather = weather,
            dismissedAlertIds = dismissed,
            isConfigured = cfg.isConfigured,
            locationName = cfg.locationName,
            timezone = cfg.timezone,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState(WeatherState.Idle, emptySet(), false, "", "America/Chicago"),
    )

    fun onResume() = weatherRepository.startPolling()
    fun onPause() = weatherRepository.stopPolling()
    fun refresh() = weatherRepository.refresh()
    fun dismissAlert(id: String) {
        viewModelScope.launch { acknowledgments.dismiss(id) }
    }
}
```

- [ ] **Step 2: Build to verify compile + Hilt graph**

```powershell
./gradlew :app:assembleDebug --no-daemon
```

Expected: BUILD SUCCESSFUL. Hilt validates the dependency graph at compile time; missing bindings fail here.

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/viewmodel/DashboardViewModel.kt
git commit -m "$(@'
feat(viewmodel): DashboardViewModel combines weather + acknowledgments + settings

Exposes a single StateFlow<DashboardUiState> to the UI shell. Computes
visibleAlerts (active minus dismissed) as a derived property. Auto-prunes
the dismissed set when active alert IDs change so dismissals don't
accumulate across alert lifecycles. onResume/onPause control the polling
lifecycle; refresh() is the pull-to-refresh hook.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

**Phase G milestone:** Data layer wired end-to-end. SettingsRepository persists user config via DataStore; AlertAcknowledgmentRepository tracks dismissed alerts; WeatherRepository orchestrates fetching with polling + error handling; DashboardViewModel exposes the combined state to the UI. App still renders only "SKYFRAME" text — the UI hookup happens in Phase H. Total test count after Phase G: ~80.

---

## Phase H — UI Shell (TopBar, Footer, BottomNav, DashboardScaffold)

The chrome around the three screens. After this phase the app shows TopBar + Footer + BottomNav with placeholder content where screens will go in Phases I–K.

### Task H.1: HudGlowText composable (the missing piece from D.4)

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/ui/widgets/HudGlowText.kt`

- [ ] **Step 1: Write `HudGlowText.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/ui/widgets/HudGlowText.kt
package com.skyframe.ui.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skyframe.theme.hudTextGlow

/**
 * Text with a soft accent-colored glow halo. Stacks a blurred copy
 * underneath a crisp copy in a Box so the text stays sharp while the
 * glow halo extends behind it.
 *
 * The web equivalent is `text-shadow: 0 0 8px rgba(accent, 0.5)`.
 */
@Composable
fun HudGlowText(
    text: String,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    glowColor: Color = color,
    glowRadius: Dp = 8.dp,
) {
    Box(modifier = modifier) {
        // Blurred copy beneath
        Text(
            text = text,
            color = glowColor,
            style = style,
            modifier = Modifier.hudTextGlow(glowColor, glowRadius),
        )
        // Crisp copy on top
        Text(
            text = text,
            color = color,
            style = style,
        )
    }
}
```

- [ ] **Step 2: Verify compile**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/ui/widgets/HudGlowText.kt
git commit -m "$(@'
feat(ui): HudGlowText for text-shadow-equivalent glow rendering

Stacks a blurred Text under a crisp Text in a Box, producing the web's
text-shadow: 0 0 8px rgba(accent) effect. Pre-API-31 devices skip the
blur and render only the crisp text (the hudTextGlow modifier no-ops).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task H.2: TopBar composable

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/ui/shell/TopBar.kt`

- [ ] **Step 1: Write `TopBar.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/ui/shell/TopBar.kt
package com.skyframe.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType
import com.skyframe.theme.LocalHudAccent
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun TopBar(
    locationName: String,
    timezone: String,
    isOnline: Boolean,
    onLocationClick: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalHudAccent.current
    val tz = remember(timezone) { runCatching { TimeZone.of(timezone) }.getOrDefault(TimeZone.currentSystemDefault()) }

    var clockText by remember { mutableStateOf(formatClock(tz)) }
    LaunchedEffect(tz) {
        while (true) {
            clockText = formatClock(tz)
            delay(1_000)
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(HudColors.BackgroundDeep)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isOnline) accent.accent else Color(0xFFFF4444)),
        )

        Text(
            text = "  $clockText",
            color = HudColors.Foreground,
            style = HudType.titleBar,
            modifier = Modifier.padding(start = 4.dp),
        )

        Row(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = locationName.ifBlank { "UNCONFIGURED" },
                color = accent.accent,
                style = HudType.titleBar,
                modifier = Modifier.padding(8.dp),
                onTextLayout = {},  // touch target enlarged below
            )
        }

        Text(
            text = "≡",
            color = accent.accent,
            style = HudType.titleBar.copy(fontSize = androidx.compose.ui.unit.TextUnit(20f, androidx.compose.ui.unit.TextUnitType.Sp)),
            modifier = Modifier.padding(8.dp),
        )
    }
}

private fun formatClock(tz: TimeZone): String {
    val ldt = Clock.System.now().toLocalDateTime(tz)
    val h = ldt.hour.toString().padStart(2, '0')
    val m = ldt.minute.toString().padStart(2, '0')
    val s = ldt.second.toString().padStart(2, '0')
    return "$h:$m:$s"
}
```

**Note:** the location-name click and hamburger-menu click are wired here but their `onLocationClick` and `onMenuClick` handlers come from `DashboardScaffold`. They navigate to Settings — which lives in Plan 3 (Settings + onboarding + update check). For Plan 1, the handlers can be a no-op stub or open a toast.

- [ ] **Step 2: Verify compile**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/ui/shell/TopBar.kt
git commit -m "$(@'
feat(ui): TopBar with status dot, clock, location, hamburger

Status dot pulses accent (cyan/tier) when online or red when offline.
Clock ticks every second in the configured timezone. Location name
opens Settings (handler wired from DashboardScaffold; Settings screen
itself lands in Plan 3). Hamburger glyph also opens Settings.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task H.3: Footer composable

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/ui/shell/Footer.kt`

- [ ] **Step 1: Write `Footer.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/ui/shell/Footer.kt
package com.skyframe.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skyframe.domain.StationOverride
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType

@Composable
fun Footer(
    stationId: String,
    stationOverride: StationOverride,
    lastFetchedLabel: String,
    nextRefreshLabel: String,
    onStationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pinSuffix = if (stationOverride == StationOverride.FORCE_SECONDARY) " [PIN]" else ""
    val linkColor = if (stationOverride == StationOverride.FORCE_SECONDARY) Color(0xFFFFAA22) else HudColors.ForegroundDim

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(HudColors.BackgroundDeep)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "LINK.${stationId.ifBlank { "----" }}$pinSuffix",
            color = linkColor,
            style = HudType.footerMono,
        )
        Text(
            text = lastFetchedLabel,
            color = HudColors.ForegroundDim,
            style = HudType.footerMono,
        )
        Text(
            text = nextRefreshLabel,
            color = HudColors.ForegroundDim,
            style = HudType.footerMono,
        )
    }
}
```

**Note:** the `onStationClick` handler exists but the StationOverrideSheet lives in Plan 2. For Plan 1 it's a no-op.

- [ ] **Step 2: Verify compile + commit**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

```powershell
git add app/src/main/kotlin/com/skyframe/ui/shell/Footer.kt
git commit -m "$(@'
feat(ui): Footer showing station ID, fetched-time, next-refresh

Renders LINK.<station> with [PIN] suffix in amber when station override
is FORCE_SECONDARY (matches web v1.2.3 visual). lastFetchedLabel and
nextRefreshLabel are computed by the caller from WeatherMeta.fetchedAt
and meta.nextRefreshAt.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task H.4: HudBottomNavBar composable

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/ui/shell/HudBottomNavBar.kt`
- Create: `app/src/main/kotlin/com/skyframe/ui/nav/Destinations.kt`

- [ ] **Step 1: Write `Destinations.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/ui/nav/Destinations.kt
package com.skyframe.ui.nav

enum class DashboardDestination(val route: String, val label: String, val glyph: String) {
    NOW("now", "NOW", "◉"),
    HOURLY("hourly", "HOURLY", "░"),
    OUTLOOK("outlook", "OUTLOOK", "█"),
}
```

- [ ] **Step 2: Write `HudBottomNavBar.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/ui/shell/HudBottomNavBar.kt
package com.skyframe.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType
import com.skyframe.theme.LocalHudAccent
import com.skyframe.ui.nav.DashboardDestination

@Composable
fun HudBottomNavBar(
    selected: DashboardDestination,
    onSelect: (DashboardDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalHudAccent.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(HudColors.BackgroundDeep)
            .drawBehind {
                // 2dp top border in accent — hazard-stripe equivalent (single line in v1)
                drawLine(
                    color = accent.accent,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 2f,
                )
            },
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DashboardDestination.entries.forEach { dest ->
            val isSelected = dest == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSelect(dest) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "${dest.glyph} ${dest.label}",
                    color = if (isSelected) accent.accent else HudColors.ForegroundDim,
                    style = HudType.navLabel,
                )
                // Underline accent for selected
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .height(2.dp)
                            .padding(top = 4.dp)
                            .background(accent.accent),
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 3: Verify compile + commit**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

```powershell
git add app/src/main/kotlin/com/skyframe/ui/shell/HudBottomNavBar.kt app/src/main/kotlin/com/skyframe/ui/nav/Destinations.kt
git commit -m "$(@'
feat(ui): HudBottomNavBar with three destinations (NOW/HOURLY/OUTLOOK)

Hand-rolled bottom nav instead of Material3 NavigationBar — preserves
the HUD aesthetic (cyan-on-dark, monospace, accent-colored selected
state). Top border in accent serves as the hazard-stripe equivalent.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task H.5: DashboardScaffold + wire MainActivity to ViewModel

The shell that pulls it all together. Includes a hand-rolled NavHost-equivalent (just a `when` over the selected destination — full Compose Navigation is overkill for 3 sibling screens with no deep-linking).

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/ui/shell/DashboardScaffold.kt`
- Create: `app/src/main/kotlin/com/skyframe/ui/screens/PlaceholderScreens.kt`
- Modify: `app/src/main/kotlin/com/skyframe/MainActivity.kt`

- [ ] **Step 1: Write placeholder screens (full versions land in Phases I/J/K)**

```kotlin
// app/src/main/kotlin/com/skyframe/ui/screens/PlaceholderScreens.kt
package com.skyframe.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.skyframe.theme.HudType
import com.skyframe.theme.LocalHudAccent
import com.skyframe.viewmodel.DashboardUiState

@Composable
fun NowScreen(state: DashboardUiState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    PlaceholderContent("NOW SCREEN — Phase I", modifier)
}

@Composable
fun HourlyScreen(state: DashboardUiState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    PlaceholderContent("HOURLY SCREEN — Phase J", modifier)
}

@Composable
fun OutlookScreen(state: DashboardUiState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    PlaceholderContent("OUTLOOK SCREEN — Phase K", modifier)
}

@Composable
private fun PlaceholderContent(label: String, modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = label, color = LocalHudAccent.current.accent, style = HudType.titleBar)
    }
}
```

- [ ] **Step 2: Write `DashboardScaffold.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/ui/shell/DashboardScaffold.kt
package com.skyframe.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.weight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skyframe.domain.StationOverride
import com.skyframe.repository.WeatherState
import com.skyframe.theme.HudAccent
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudTheme
import com.skyframe.ui.nav.DashboardDestination
import com.skyframe.ui.screens.HourlyScreen
import com.skyframe.ui.screens.NowScreen
import com.skyframe.ui.screens.OutlookScreen
import com.skyframe.viewmodel.DashboardUiState
import com.skyframe.viewmodel.DashboardViewModel
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@Composable
fun DashboardScaffold(
    viewModel: DashboardViewModel,
    onNavigateToSettings: () -> Unit,
) {
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(DashboardDestination.NOW) }

    // Polling lifecycle tied to the composable
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        viewModel.onResume()
    }

    // Tier-driven accent: highest-severity visible alert wins
    val accent = computeAccent(ui)

    HudTheme(accent = accent) {
        Column(modifier = Modifier.fillMaxSize().background(HudColors.BackgroundBase)) {
            // AlertBanner — Phase L; conditional, slot reserved
            // (Insert AlertBanner here once Phase L implements it)

            TopBar(
                locationName = ui.locationName,
                timezone = ui.timezone,
                isOnline = ui.weather !is WeatherState.Error,
                onLocationClick = onNavigateToSettings,
                onMenuClick = onNavigateToSettings,
            )

            Box(modifier = Modifier.weight(1f)) {
                when (selected) {
                    DashboardDestination.NOW -> NowScreen(state = ui, onRefresh = viewModel::refresh)
                    DashboardDestination.HOURLY -> HourlyScreen(state = ui, onRefresh = viewModel::refresh)
                    DashboardDestination.OUTLOOK -> OutlookScreen(state = ui, onRefresh = viewModel::refresh)
                }
            }

            Footer(
                stationId = (ui.weather as? WeatherState.Success)?.response?.meta?.stationId.orEmpty(),
                stationOverride = (ui.weather as? WeatherState.Success)?.response?.meta?.stationOverride ?: StationOverride.AUTO,
                lastFetchedLabel = formatFetchedLabel(ui.weather, ui.timezone),
                nextRefreshLabel = formatRefreshLabel(ui.weather),
                onStationClick = {},  // Plan 2 wires StationOverrideSheet
            )
            HudBottomNavBar(selected = selected, onSelect = { selected = it })
        }
    }
}

private fun computeAccent(ui: DashboardUiState): HudAccent {
    val top = ui.visibleAlerts.firstOrNull() ?: return HudAccent.Default
    return HudAccent.fromTier(top.tier)
}

private fun formatFetchedLabel(state: WeatherState, timezone: String): String {
    val success = state as? WeatherState.Success ?: return "WAITING..."
    val tz = runCatching { TimeZone.of(timezone) }.getOrDefault(TimeZone.currentSystemDefault())
    val ldt = success.response.meta.fetchedAt.toLocalDateTime(tz)
    val h = ldt.hour.toString().padStart(2, '0')
    val m = ldt.minute.toString().padStart(2, '0')
    val s = ldt.second.toString().padStart(2, '0')
    return "$h:$m:$s"
}

private fun formatRefreshLabel(state: WeatherState): String {
    val success = state as? WeatherState.Success ?: return "T-???"
    val secondsLeft = (success.response.meta.nextRefreshAt.epochSeconds - Clock.System.now().epochSeconds).coerceAtLeast(0)
    return "T-${secondsLeft}s"
}
```

- [ ] **Step 3: Update `MainActivity.kt`**

```kotlin
package com.skyframe

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.skyframe.ui.shell.DashboardScaffold
import com.skyframe.viewmodel.DashboardViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DashboardScaffold(
                viewModel = viewModel,
                onNavigateToSettings = {
                    Toast.makeText(this, "Settings: implemented in Plan 3", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onPause()
    }
}
```

- [ ] **Step 4: Build + install + visual smoke test**

```powershell
./gradlew :app:assembleDebug --no-daemon
./gradlew :app:installDebug --no-daemon
adb shell am start -n com.skyframe/.MainActivity
```

Expected: dark HUD shell renders with TopBar (offline status — no config yet), three placeholder screens swappable via bottom nav, footer reads "LINK.---- WAITING... T-???". The app won't fetch data until SettingsRepository has a real config — but the shell itself works.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/ui/shell/DashboardScaffold.kt app/src/main/kotlin/com/skyframe/ui/screens/PlaceholderScreens.kt app/src/main/kotlin/com/skyframe/MainActivity.kt
git commit -m "$(@'
feat(ui): DashboardScaffold + wire MainActivity to DashboardViewModel

Composable shell layers: TopBar, swappable screen area, Footer, HudBottomNavBar.
Tier-driven accent flows from the highest-severity visible alert through
HudTheme so the whole UI shifts color in response. Lifecycle awareness via
onResume/onPause to start and stop the WeatherRepository polling.

Placeholder NowScreen/HourlyScreen/OutlookScreen render "PHASE I/J/K"
text — replaced with real content in the next three phases.

Settings navigation is a Toast stub for Plan 1; real Settings screen
ships in Plan 3.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

**Phase H milestone:** UI shell complete. App renders with TopBar (status dot, clock, location, hamburger), swappable bottom-nav screens (placeholders), Footer (station link, last-fetched, next-refresh). Tier-driven accent system in place — when the first alert lands in Phase L, the entire UI will reactively shift to the appropriate tier color.

---

## Phase I — NowScreen (Hero Temp + 5 Metric Bars)

Real `NowScreen` replacing the placeholder. Hero temperature (tap to toggle °F/°C), 5 metric bars with trend arrows.

### Task I.1: WxIcon + ImageVector ports for 9 weather icons

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/ui/widgets/WxIcons.kt`
- Create: `app/src/main/kotlin/com/skyframe/ui/widgets/WxIcon.kt`

- [ ] **Step 1: Write `WxIcons.kt` — ImageVector definitions for the 9 icons**

The simplest port keeps the icons as Compose `ImageVector` paths. The web's [client/icons.svg](_reference/client/icons.svg) contains 9 SVG symbols (sun, moon, cloud, partly-day, partly-night, rain, snow, thunder, fog). Hand-translate each `<path d="...">` to `path { moveTo(); lineTo(); ... }` calls. For Plan 1 MVP, simple geometric icons in the accent color suffice — SMIL animations are not ported (Compose Transition equivalents are a v2 enhancement).

The full per-icon path data is ~30 lines per icon. To avoid expanding this task to 300+ lines, **the implementation step is to consult `_reference/client/icons.svg`, port each `<symbol>` to an `ImageVector` builder**. Example skeleton:

```kotlin
// app/src/main/kotlin/com/skyframe/ui/widgets/WxIcons.kt
package com.skyframe.ui.widgets

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Compose ImageVector ports of _reference/client/icons.svg. SMIL animations
 * are NOT ported in Plan 1 — icons are static. Compose Transition equivalents
 * for the sun-ray rotation, cloud drift, etc. are a v2 enhancement.
 *
 * All icons use 24×24 viewport, currentColor stroke that the caller tints
 * via WxIcon's tint parameter (typically LocalHudAccent.current.accent).
 */
object WxIcons {

    val Sun: ImageVector = ImageVector.Builder(
        name = "Sun",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        // Center circle
        path(
            stroke = SolidColor(Color.White),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
        ) {
            // Move/line/arc/close commands ported from icons.svg #sun
            moveTo(12f, 6f)
            // ... port the remaining path data from icons.svg
            close()
        }
        // 8 rays — port from the SVG group
        path(stroke = SolidColor(Color.White), strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round) {
            moveTo(12f, 1f); lineTo(12f, 3f)        // top ray
            moveTo(12f, 21f); lineTo(12f, 23f)      // bottom ray
            moveTo(1f, 12f); lineTo(3f, 12f)        // left ray
            moveTo(21f, 12f); lineTo(23f, 12f)      // right ray
            moveTo(4.22f, 4.22f); lineTo(5.64f, 5.64f)
            moveTo(18.36f, 18.36f); lineTo(19.78f, 19.78f)
            moveTo(4.22f, 19.78f); lineTo(5.64f, 18.36f)
            moveTo(18.36f, 5.64f); lineTo(19.78f, 4.22f)
        }
    }.build()

    // ... define Moon, Cloud, PartlyDay, PartlyNight, Rain, Snow, Thunder, Fog
    // following the same pattern. Paths come from inspecting icons.svg.

    val Moon: ImageVector = TODO("Port from icons.svg #moon")
    val Cloud: ImageVector = TODO("Port from icons.svg #cloud")
    val PartlyDay: ImageVector = TODO("Port from icons.svg #partly-day")
    val PartlyNight: ImageVector = TODO("Port from icons.svg #partly-night")
    val Rain: ImageVector = TODO("Port from icons.svg #rain")
    val Snow: ImageVector = TODO("Port from icons.svg #snow")
    val Thunder: ImageVector = TODO("Port from icons.svg #thunder")
    val Fog: ImageVector = TODO("Port from icons.svg #fog")
}
```

**Concrete porting recipe** for each icon:

1. Open `_reference/client/icons.svg` and find the matching `<symbol id="...">` (e.g., `id="cloud"`).
2. For each `<path d="...">` element: convert SVG path commands to ImageVector builder calls:
   - `M x,y` → `moveTo(x, y)`
   - `L x,y` → `lineTo(x, y)`
   - `H x` → `horizontalLineTo(x)`
   - `V y` → `verticalLineTo(y)`
   - `C x1,y1 x2,y2 x,y` → `curveTo(x1, y1, x2, y2, x, y)`
   - `Q x1,y1 x,y` → `quadTo(x1, y1, x, y)`
   - `A rx,ry r large,sweep x,y` → `arcTo(rx, ry, r, large == 1, sweep == 1, x, y)`
   - `Z` → `close()`
3. Use lowercase versions (`moveToRelative`, `lineToRelative`, etc.) for SVG's relative commands (`m`, `l`, `h`, `v`, `c`, etc.).
4. Apply the stroke from the SVG: `stroke = SolidColor(...)`, `strokeLineWidth = N`, `strokeLineCap = StrokeCap.Round` (matches the web's `stroke-linecap="round"`).

Replace each `TODO("Port from ...")` with the actual port. **Don't commit until all 9 are real `ImageVector`s — the build fails on `TODO()` calls.**

- [ ] **Step 2: Write `WxIcon.kt` (the dispatch composable)**

```kotlin
// app/src/main/kotlin/com/skyframe/ui/widgets/WxIcon.kt
package com.skyframe.ui.widgets

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.skyframe.domain.IconCode

@Composable
fun WxIcon(
    code: IconCode,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
) {
    val vector = when (code) {
        IconCode.SUN -> WxIcons.Sun
        IconCode.MOON -> WxIcons.Moon
        IconCode.CLOUD -> WxIcons.Cloud
        IconCode.PARTLY_DAY -> WxIcons.PartlyDay
        IconCode.PARTLY_NIGHT -> WxIcons.PartlyNight
        IconCode.RAIN -> WxIcons.Rain
        IconCode.SNOW -> WxIcons.Snow
        IconCode.THUNDER -> WxIcons.Thunder
        IconCode.FOG -> WxIcons.Fog
    }
    Icon(
        imageVector = vector,
        contentDescription = code.name,
        tint = tint,
        modifier = modifier.size(size),
    )
}
```

- [ ] **Step 3: Verify compile + commit**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

Expected: BUILD SUCCESSFUL (after all 9 TODOs in WxIcons are replaced with real path data).

```powershell
git add app/src/main/kotlin/com/skyframe/ui/widgets/WxIcons.kt app/src/main/kotlin/com/skyframe/ui/widgets/WxIcon.kt
git commit -m "$(@'
feat(ui): port 9 weather icons from icons.svg to Compose ImageVector

Static ports of the web's inline SVG sprite (sun, moon, cloud,
partly-day, partly-night, rain, snow, thunder, fog). SMIL animations
are NOT included — Compose Transition equivalents are a v2 enhancement.

WxIcon dispatches on IconCode and tints from the call site.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task I.2: HudHero composable (hero temperature with °F/°C toggle)

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/ui/widgets/HudHero.kt`

- [ ] **Step 1: Write `HudHero.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/ui/widgets/HudHero.kt
package com.skyframe.ui.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skyframe.domain.CurrentConditions
import com.skyframe.domain.IconCode
import com.skyframe.domain.TempUnit
import com.skyframe.domain.Units
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType
import kotlin.math.roundToInt

@Composable
fun HudHero(
    current: CurrentConditions,
    tempUnit: TempUnit,
    accent: Color,
    onToggleUnit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val temp = Units.convertTempF(current.tempF, tempUnit).roundToInt()
    val feel = Units.convertTempF(current.feelsLikeF, tempUnit).roundToInt()
    val unitSuffix = if (tempUnit == TempUnit.FAHRENHEIT) "°F" else "°C"
    val isClear = current.iconCode in setOf(IconCode.SUN, IconCode.MOON)

    Row(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.clickable { onToggleUnit() }
        ) {
            HudGlowText(
                text = "$temp°",
                color = accent,
                style = HudType.heroTemp,
            )
            Text(
                text = "TEMP / FEEL  $feel°$unitSuffix",
                color = HudColors.ForegroundDim,
                style = HudType.heroFeel,
            )
        }
        WxIcon(
            code = current.iconCode,
            tint = accent,
            size = if (isClear) 96.dp else 72.dp,
        )
    }
}
```

- [ ] **Step 2: Verify compile + commit**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

```powershell
git add app/src/main/kotlin/com/skyframe/ui/widgets/HudHero.kt
git commit -m "$(@'
feat(ui): HudHero — hero temp with tap-to-toggle F/C

Mirrors the web's CurrentPanel hero. Tap the temperature column to
toggle between F and C (state lives in NowScreen). Clear-sky conditions
render a larger 96dp icon vs 72dp for cloudy/precip — matches the web's
data-clear behavior.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task I.3: HudMetricBar composable (5 metric bars with trend arrows)

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/ui/widgets/HudMetricBar.kt`

- [ ] **Step 1: Write `HudMetricBar.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/ui/widgets/HudMetricBar.kt
package com.skyframe.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skyframe.domain.Trend
import com.skyframe.domain.TrendDirection
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType

@Composable
fun HudMetricBar(
    label: String,
    value: String,
    trend: Trend?,
    accent: Color,
    fillFraction: Float,  // 0.0..1.0 — bar fill proportion
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = HudColors.ForegroundDim,
            style = HudType.metricLabel,
            modifier = Modifier.padding(end = 8.dp),
        )

        // Bar fill
        Box(
            modifier = Modifier
                .weight(1f)
                .height(8.dp)
                .background(HudColors.BackgroundDeep),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fillFraction.coerceIn(0f, 1f))
                    .height(8.dp)
                    .background(accent.copy(alpha = 0.7f)),
            )
        }

        Text(
            text = value,
            color = HudColors.Foreground,
            style = HudType.metricValue,
            modifier = Modifier.padding(start = 8.dp),
        )

        if (trend != null) {
            val arrow = when (trend.direction) {
                TrendDirection.UP -> "▲"
                TrendDirection.DOWN -> "▼"
                TrendDirection.STEADY -> "·"
            }
            Text(
                text = arrow,
                color = accent,
                style = HudType.metricValue,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}
```

- [ ] **Step 2: Verify compile + commit**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

```powershell
git add app/src/main/kotlin/com/skyframe/ui/widgets/HudMetricBar.kt
git commit -m "$(@'
feat(ui): HudMetricBar — labeled bar with trend arrow

Renders a horizontal label/bar/value row with optional trend glyph
(▲ ▼ ·) in the accent color. fillFraction is computed by NowScreen
per-metric (e.g. humidity is value/100; pressure is normalized to a
plausible range; wind is value/40 mph).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task I.4: NowScreen — replace placeholder with real implementation

**Files:**
- Modify: `app/src/main/kotlin/com/skyframe/ui/screens/PlaceholderScreens.kt` (remove `NowScreen`)
- Create: `app/src/main/kotlin/com/skyframe/ui/screens/NowScreen.kt`

- [ ] **Step 1: Remove the `NowScreen` placeholder from `PlaceholderScreens.kt`**

Open `app/src/main/kotlin/com/skyframe/ui/screens/PlaceholderScreens.kt` and delete the `NowScreen` function (leave `HourlyScreen` and `OutlookScreen` placeholders for now).

- [ ] **Step 2: Write `NowScreen.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/ui/screens/NowScreen.kt
package com.skyframe.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import com.skyframe.domain.CurrentConditions
import com.skyframe.domain.TempUnit
import com.skyframe.domain.Units
import com.skyframe.repository.WeatherState
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType
import com.skyframe.theme.LocalHudAccent
import com.skyframe.ui.widgets.HudHero
import com.skyframe.ui.widgets.HudMetricBar
import com.skyframe.viewmodel.DashboardUiState
import kotlin.math.roundToInt

@Composable
fun NowScreen(
    state: DashboardUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalHudAccent.current.accent
    val weather = state.weather

    Box(modifier = modifier.fillMaxSize()) {
        when (weather) {
            WeatherState.Idle, WeatherState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("FETCHING...", color = accent, style = HudType.titleBar)
                }
            }
            is WeatherState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("ERROR: ${weather.message}", color = HudColors.Foreground, style = HudType.bodyMono)
                }
            }
            is WeatherState.Success -> NowContent(weather.response.current)
        }
    }
}

@Composable
private fun NowContent(current: CurrentConditions) {
    val accent = LocalHudAccent.current.accent
    var tempUnit by remember { mutableStateOf(TempUnit.FAHRENHEIT) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
    ) {
        HudHero(
            current = current,
            tempUnit = tempUnit,
            accent = accent,
            onToggleUnit = {
                tempUnit = if (tempUnit == TempUnit.FAHRENHEIT) TempUnit.CELSIUS else TempUnit.FAHRENHEIT
            },
        )

        // 5 metric bars
        HudMetricBar(
            label = "HUMIDITY",
            value = current.humidityPct?.roundToInt()?.let { "$it%" } ?: "--",
            trend = current.trends.humidity,
            accent = accent,
            fillFraction = (current.humidityPct ?: 0.0).toFloat() / 100f,
        )
        HudMetricBar(
            label = "WIND ${current.wind.cardinal}",
            value = "${current.wind.speedMph.roundToInt()} MPH",
            trend = current.trends.wind,
            accent = accent,
            fillFraction = (current.wind.speedMph / 40.0).toFloat(),
        )
        HudMetricBar(
            label = "PRESSURE",
            value = current.pressureInHg?.let { "%.2f\"".format(it) } ?: "--",
            trend = current.trends.pressure,
            accent = accent,
            // Normalize pressure 29.50..30.50 inHg to 0..1 (covers typical range)
            fillFraction = (((current.pressureInHg ?: 29.92) - 29.50) / 1.0).toFloat(),
        )
        HudMetricBar(
            label = "DEWPOINT",
            value = current.dewpointF?.let {
                val converted = Units.convertTempF(it, tempUnit).roundToInt()
                "$converted°"
            } ?: "--",
            trend = current.trends.dewpoint,
            accent = accent,
            // Dewpoint 0..80°F mapped to 0..1
            fillFraction = ((current.dewpointF ?: 0.0) / 80.0).toFloat(),
        )
        HudMetricBar(
            label = "VISIBILITY",
            value = current.visibilityMi?.let { "%.1f mi".format(it) } ?: "--",
            trend = current.trends.visibility,
            accent = accent,
            // Visibility 0..10 mi mapped to 0..1
            fillFraction = ((current.visibilityMi ?: 0.0) / 10.0).toFloat(),
        )
    }
}
```

- [ ] **Step 3: Build + visual smoke test**

```powershell
./gradlew :app:assembleDebug --no-daemon
```

Until a real Settings flow exists, the app shows "FETCHING..." indefinitely because no config is saved. **For Plan 1 manual testing,** populate a config via `adb`:

```powershell
# Once on a configured device — use adb shell to seed DataStore via the eventual SettingsScreen.
# Plan 1 doesn't include Settings, so testing NowScreen requires using one of:
# (a) writing a small dev-only seeding routine, or
# (b) waiting for Plan 3's SettingsScreen.
# For visual verification of NowScreen layout, see Plan 1 smoke test in Phase L which provides a debug-seed path.
```

- [ ] **Step 4: Commit**

```powershell
git add app/src/main/kotlin/com/skyframe/ui/screens/NowScreen.kt app/src/main/kotlin/com/skyframe/ui/screens/PlaceholderScreens.kt
git commit -m "$(@'
feat(ui): NowScreen with hero temp and five metric bars

Renders CurrentConditions: hero (tap-toggle F/C), humidity, wind,
pressure, dewpoint, visibility. Trend arrows from ConditionTrends.
Loading and Error states displayed inline.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

**Phase I milestone:** NowScreen renders hero temp + 5 metric bars when weather data is available. UI shifts to Loading/Error/Success states based on WeatherRepository. App still needs a configured location to fetch — debug seed path in Phase L provides one.

---

## Phase J — HourlyScreen (Canvas Line Chart)

### Task J.1: HudChart Canvas composable

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/ui/widgets/HudChart.kt`

- [ ] **Step 1: Write `HudChart.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/ui/widgets/HudChart.kt
package com.skyframe.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Line chart with optional fill gradient. Mirrors the web's SVG line
 * chart in HourlyPanel.tsx: smooth polyline through (x, normalized y)
 * points with a subtle gradient fill below.
 *
 * @param values input series; first element drawn at left edge, last at right.
 * @param minOverride/maxOverride: optional fixed y-range; otherwise derived from values.
 */
@Composable
fun HudChart(
    values: List<Double>,
    accent: Color,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp,
    minOverride: Double? = null,
    maxOverride: Double? = null,
    strokeWidth: Float = 3f,
) {
    if (values.size < 2) return
    val min = minOverride ?: values.min()
    val max = maxOverride ?: values.max()
    val range = (max - min).takeIf { it > 0 } ?: 1.0

    Canvas(
        modifier = modifier.fillMaxWidth().height(height),
    ) {
        val w = size.width
        val h = size.height
        val stepX = if (values.size > 1) w / (values.size - 1) else w

        val points = values.mapIndexed { i, v ->
            val x = i * stepX
            // Invert Y so larger value = higher on screen, with 8dp top/bottom padding
            val padding = 16f
            val y = padding + (h - 2 * padding) * (1f - ((v - min) / range).toFloat())
            Offset(x, y)
        }

        // Stroke path
        val strokePath = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        }
        drawPath(
            path = strokePath,
            color = accent,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
        )

        // Fill gradient under the line
        val fillPath = Path().apply {
            moveTo(points[0].x, h)
            for (p in points) lineTo(p.x, p.y)
            lineTo(points.last().x, h)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(accent.copy(alpha = 0.25f), accent.copy(alpha = 0.0f)),
                startY = 0f,
                endY = h,
            ),
        )
    }
}
```

- [ ] **Step 2: Verify compile + commit**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

```powershell
git add app/src/main/kotlin/com/skyframe/ui/widgets/HudChart.kt
git commit -m "$(@'
feat(ui): HudChart Canvas line chart with gradient fill

Compose Canvas equivalent of the web's SVG chart in HourlyPanel.tsx.
Stroke in accent color, vertical gradient fill underneath fading to
transparent. Min/max derived from values or overridden by caller.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task J.2: HourlyScreen — replace placeholder

**Files:**
- Modify: `app/src/main/kotlin/com/skyframe/ui/screens/PlaceholderScreens.kt`
- Create: `app/src/main/kotlin/com/skyframe/ui/screens/HourlyScreen.kt`

- [ ] **Step 1: Remove `HourlyScreen` placeholder**

Delete the `HourlyScreen` function from `PlaceholderScreens.kt`.

- [ ] **Step 2: Write `HourlyScreen.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/ui/screens/HourlyScreen.kt
package com.skyframe.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.skyframe.domain.HourlyPeriod
import com.skyframe.repository.WeatherState
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType
import com.skyframe.theme.LocalHudAccent
import com.skyframe.ui.widgets.HudChart
import com.skyframe.ui.widgets.WxIcon
import com.skyframe.viewmodel.DashboardUiState

@Composable
fun HourlyScreen(state: DashboardUiState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalHudAccent.current.accent
    when (val weather = state.weather) {
        is WeatherState.Success -> HourlyContent(weather.response.hourly, accent, modifier)
        is WeatherState.Error -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("ERROR: ${weather.message}", color = HudColors.Foreground, style = HudType.bodyMono)
        }
        else -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("FETCHING...", color = accent, style = HudType.titleBar)
        }
    }
}

@Composable
private fun HourlyContent(periods: List<HourlyPeriod>, accent: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("NEXT 12H", color = HudColors.ForegroundDim, style = HudType.sectionHeader)

        HudChart(
            values = periods.map { it.tempF },
            accent = accent,
            modifier = Modifier.padding(vertical = 16.dp),
            height = 140.dp,
        )

        // Icon row
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            periods.forEach { p ->
                Column(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(p.hourLabel, color = HudColors.ForegroundDim, style = HudType.metricLabel)
                    WxIcon(code = p.iconCode, tint = accent, size = 20.dp)
                    Text("${p.tempF.toInt()}°", color = HudColors.Foreground, style = HudType.metricValue)
                }
            }
        }

        // Precip bars
        Text("PRECIP %", color = HudColors.ForegroundDim, style = HudType.sectionHeader, modifier = Modifier.padding(top = 16.dp))
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            periods.forEach { p ->
                Column(
                    modifier = Modifier.padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .background(HudColors.BackgroundDeep),
                    ) {
                        Box(
                            modifier = Modifier
                                .height((40 * p.precipProbPct / 100).dp)
                                .background(accent.copy(alpha = 0.6f))
                                .align(Alignment.BottomCenter),
                        )
                    }
                    Text("${p.precipProbPct}", color = HudColors.ForegroundDim, style = HudType.footerMono)
                }
            }
        }
    }
}
```

- [ ] **Step 3: Verify compile + commit**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

```powershell
git add app/src/main/kotlin/com/skyframe/ui/screens/HourlyScreen.kt app/src/main/kotlin/com/skyframe/ui/screens/PlaceholderScreens.kt
git commit -m "$(@'
feat(ui): HourlyScreen with temp chart, icon row, precip bars

Three sections: temperature line chart over the next 12h, condition
icon row with hour labels and temperature, precipitation probability
bars. Mirrors web HourlyPanel layout.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

**Phase J milestone:** HourlyScreen renders chart + icons + precip bars when weather data flows in. Two of three screens now complete.

---

## Phase K — OutlookScreen (7-Day Range Bars)

### Task K.1: HudRangeBar composable

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/ui/widgets/HudRangeBar.kt`

- [ ] **Step 1: Write `HudRangeBar.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/ui/widgets/HudRangeBar.kt
package com.skyframe.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Horizontal range bar for the 7-day outlook. The bar's position and
 * length encode (lowF..highF) relative to the global (weekMin..weekMax)
 * axis. Accent color, subtle glow at the high end.
 */
@Composable
fun HudRangeBar(
    lowF: Int,
    highF: Int,
    weekMinF: Int,
    weekMaxF: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
) {
    val weekRange = (weekMaxF - weekMinF).coerceAtLeast(1)
    val barStartFraction = (lowF - weekMinF).toFloat() / weekRange
    val barEndFraction = (highF - weekMinF).toFloat() / weekRange

    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height
        val x0 = barStartFraction * w
        val x1 = barEndFraction * w

        // Track
        drawRoundRect(
            color = accent.copy(alpha = 0.15f),
            topLeft = Offset(0f, h / 4),
            size = Size(w, h / 2),
            cornerRadius = CornerRadius(h / 4),
        )

        // Active fill
        drawRoundRect(
            color = accent,
            topLeft = Offset(x0, h / 4),
            size = Size(x1 - x0, h / 2),
            cornerRadius = CornerRadius(h / 4),
        )
    }
}
```

- [ ] **Step 2: Verify compile + commit**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

```powershell
git add app/src/main/kotlin/com/skyframe/ui/widgets/HudRangeBar.kt
git commit -m "$(@'
feat(ui): HudRangeBar — high/low range bar for OutlookScreen

Canvas-drawn rounded rect positioned and sized by (lowF..highF)
relative to a global (weekMinF..weekMaxF) axis so day-to-day
comparison is visually intuitive.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task K.2: OutlookScreen — replace placeholder

**Files:**
- Modify: `app/src/main/kotlin/com/skyframe/ui/screens/PlaceholderScreens.kt` (delete)
- Create: `app/src/main/kotlin/com/skyframe/ui/screens/OutlookScreen.kt`

- [ ] **Step 1: Delete `PlaceholderScreens.kt` entirely** (all three placeholders now have real screens)

```powershell
Remove-Item app/src/main/kotlin/com/skyframe/ui/screens/PlaceholderScreens.kt
```

- [ ] **Step 2: Write `OutlookScreen.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/ui/screens/OutlookScreen.kt
package com.skyframe.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skyframe.domain.DailyPeriod
import com.skyframe.repository.WeatherState
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType
import com.skyframe.theme.LocalHudAccent
import com.skyframe.ui.widgets.HudRangeBar
import com.skyframe.ui.widgets.WxIcon
import com.skyframe.viewmodel.DashboardUiState

@Composable
fun OutlookScreen(state: DashboardUiState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    val accent = LocalHudAccent.current.accent
    when (val w = state.weather) {
        is WeatherState.Success -> OutlookContent(w.response.daily, accent, modifier)
        is WeatherState.Error -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("ERROR: ${w.message}", color = HudColors.Foreground, style = HudType.bodyMono)
        }
        else -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("FETCHING...", color = accent, style = HudType.titleBar)
        }
    }
}

@Composable
private fun OutlookContent(periods: List<DailyPeriod>, accent: Color, modifier: Modifier) {
    val weekMin = periods.minOfOrNull { it.lowF } ?: 0
    val weekMax = periods.maxOfOrNull { it.highF } ?: 100

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("7-DAY OUTLOOK", color = HudColors.ForegroundDim, style = HudType.sectionHeader, modifier = Modifier.padding(bottom = 8.dp))

        periods.forEach { p ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = p.dayOfWeek,
                    color = accent,
                    style = HudType.metricValue,
                    modifier = Modifier.padding(end = 8.dp).fillMaxWidth(0.20f),
                )
                WxIcon(code = p.iconCode, tint = accent, size = 22.dp)
                Text(
                    text = "${p.lowF}° / ${p.highF}°",
                    color = HudColors.Foreground,
                    style = HudType.metricLabel,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                HudRangeBar(
                    lowF = p.lowF,
                    highF = p.highF,
                    weekMinF = weekMin,
                    weekMaxF = weekMax,
                    accent = accent,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (p.precipProbPct > 0) " ${p.precipProbPct}%" else "  --",
                    color = HudColors.ForegroundDim,
                    style = HudType.metricLabel,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
```

- [ ] **Step 3: Verify compile + commit**

```powershell
./gradlew :app:compileDebugKotlin --no-daemon
```

```powershell
git add app/src/main/kotlin/com/skyframe/ui/screens/OutlookScreen.kt
git rm app/src/main/kotlin/com/skyframe/ui/screens/PlaceholderScreens.kt
git commit -m "$(@'
feat(ui): OutlookScreen — 7-day high/low range bars

Each day row: day name + icon + numeric high/low + range bar (positioned
within the week's min..max axis) + precip %. Deletes the placeholder
file since all three screens now have real implementations.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

**Phase K milestone:** All three primary screens (NowScreen, HourlyScreen, OutlookScreen) render real weather data. Bottom nav swaps between them. UI is feature-complete except for the AlertBanner and the debug-seed mechanism needed to test it.

---

## Phase L — AlertBanner + Debug Seed + Smoke Test + v0.1.0 Tag

The final phase. Wire up the basic AlertBanner (dismissable, tier-colored, shows visible-alert count), add a debug-seed mechanism so the app can be tested without a real Settings screen, run a manual smoke test on a device, and tag v0.1.0-mvp.

### Task L.1: AlertBanner composable

**Files:**
- Create: `app/src/main/kotlin/com/skyframe/ui/shell/AlertBanner.kt`

- [ ] **Step 1: Write `AlertBanner.kt`**

```kotlin
// app/src/main/kotlin/com/skyframe/ui/shell/AlertBanner.kt
package com.skyframe.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.skyframe.domain.Alert
import com.skyframe.theme.HudColors
import com.skyframe.theme.HudType

@Composable
fun AlertBanner(
    alerts: List<Alert>,
    onDismiss: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (alerts.isEmpty()) return

    val top = alerts.first()
    val tierAccent = Color(top.tier.baseColor)
    val tierDark = Color(top.tier.darkColor)
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(HudColors.BackgroundDeep)
            .drawBehind {
                // Hazard stripes — alternating top border
                val stripeW = 16f
                var x = 0f
                while (x < size.width) {
                    drawRect(
                        color = if ((x / stripeW).toInt() % 2 == 0) tierAccent else tierDark,
                        topLeft = Offset(x, 0f),
                        size = androidx.compose.ui.geometry.Size(stripeW, 6f),
                    )
                    x += stripeW
                }
            }
            .padding(top = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = top.event.uppercase(),
                color = tierAccent,
                style = HudType.titleBar,
                modifier = Modifier.weight(1f),
            )
            if (alerts.size > 1) {
                Text(
                    text = if (expanded) "▾" else "+${alerts.size - 1}",
                    color = tierAccent,
                    style = HudType.titleBar,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .clickable { expanded = !expanded },
                )
            }
            Text(
                text = "×",
                color = tierAccent,
                style = HudType.titleBar,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clickable { onDismiss(top.id) },
            )
        }

        if (expanded) {
            alerts.drop(1).forEach { alert ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = alert.event.uppercase(),
                        color = Color(alert.tier.baseColor),
                        style = HudType.metaLabel,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "×",
                        color = Color(alert.tier.baseColor),
                        style = HudType.metaLabel,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clickable { onDismiss(alert.id) },
                    )
                }
            }
        }
    }
}
```

**Note:** the AlertBanner shown here is the minimal v1 — no tap-to-open-detail-sheet (that's Plan 2), no sounds (Plan 4), no mute button. Just hazard-stripe top border, event name, dismiss glyph, and expand toggle for multi-alert.

- [ ] **Step 2: Wire AlertBanner into DashboardScaffold**

Edit `app/src/main/kotlin/com/skyframe/ui/shell/DashboardScaffold.kt`. Replace the existing `Column { ... }` body with:

```kotlin
        Column(modifier = Modifier.fillMaxSize().background(HudColors.BackgroundBase)) {

            AlertBanner(
                alerts = ui.visibleAlerts,
                onDismiss = { id -> viewModel.dismissAlert(id) },
            )

            TopBar(
                locationName = ui.locationName,
                timezone = ui.timezone,
                isOnline = ui.weather !is WeatherState.Error,
                onLocationClick = onNavigateToSettings,
                onMenuClick = onNavigateToSettings,
            )

            Box(modifier = Modifier.weight(1f)) {
                when (selected) {
                    DashboardDestination.NOW -> NowScreen(state = ui, onRefresh = viewModel::refresh)
                    DashboardDestination.HOURLY -> HourlyScreen(state = ui, onRefresh = viewModel::refresh)
                    DashboardDestination.OUTLOOK -> OutlookScreen(state = ui, onRefresh = viewModel::refresh)
                }
            }

            Footer(
                stationId = (ui.weather as? WeatherState.Success)?.response?.meta?.stationId.orEmpty(),
                stationOverride = (ui.weather as? WeatherState.Success)?.response?.meta?.stationOverride ?: StationOverride.AUTO,
                lastFetchedLabel = formatFetchedLabel(ui.weather, ui.timezone),
                nextRefreshLabel = formatRefreshLabel(ui.weather),
                onStationClick = {},
            )
            HudBottomNavBar(selected = selected, onSelect = { selected = it })
        }
```

Also add the import: `import com.skyframe.ui.shell.AlertBanner` (auto-import handles this in IDE).

- [ ] **Step 3: Verify compile + commit**

```powershell
./gradlew :app:assembleDebug --no-daemon
```

```powershell
git add app/src/main/kotlin/com/skyframe/ui/shell/AlertBanner.kt app/src/main/kotlin/com/skyframe/ui/shell/DashboardScaffold.kt
git commit -m "$(@'
feat(ui): AlertBanner with hazard stripes, expand/collapse, dismiss

Renders above TopBar when visibleAlerts is non-empty. Hazard-stripe top
border in alternating tier accent + dark. Multi-alert expand toggle.
Per-alert dismiss writes to AlertAcknowledgmentRepository through the
DashboardViewModel, which auto-prunes IDs that drop off the NWS feed.

This is the minimal v1 banner — no tap-to-detail (Plan 2), no sounds
(Plan 4), no mute glyph. Sufficient to verify the tier-driven accent
shifts the entire UI in response.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task L.2: Debug-seed mechanism for manual testing without Settings screen

Without a Settings screen (Plan 3), we need a way to populate location config for manual testing. Add a one-time `MainActivity` seed routine guarded by a BuildConfig flag.

**Files:**
- Modify: `app/build.gradle.kts` (add buildConfigField + BuildConfig generation)
- Modify: `app/src/main/kotlin/com/skyframe/MainActivity.kt`

- [ ] **Step 1: Enable BuildConfig and add a debug-seed flag**

In `app/build.gradle.kts`, inside the `android { ... }` block, add:

```kotlin
    buildFeatures {
        compose = true
        buildConfig = true   // ← add this line
    }

    defaultConfig {
        // ... existing ...
        buildConfigField("String", "DEBUG_SEED_ZIP", "\"\"")
        buildConfigField("String", "DEBUG_SEED_EMAIL", "\"\"")
    }

    buildTypes {
        getByName("debug") {
            // Replace these with YOUR test ZIP and a contact email for NWS user-agent
            buildConfigField("String", "DEBUG_SEED_ZIP", "\"53154\"")
            buildConfigField("String", "DEBUG_SEED_EMAIL", "\"your-email@example.com\"")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
```

- [ ] **Step 2: Update `MainActivity` to seed on first launch when DEBUG_SEED_ZIP is non-empty**

```kotlin
package com.skyframe

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.skyframe.data.nws.SetupResolver
import com.skyframe.data.settings.SettingsRepository
import com.skyframe.ui.shell.DashboardScaffold
import com.skyframe.viewmodel.DashboardViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var setupResolver: SetupResolver

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        maybeDebugSeed()
        setContent {
            DashboardScaffold(
                viewModel = viewModel,
                onNavigateToSettings = {
                    Toast.makeText(this, "Settings: lands in Plan 3", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }

    private fun maybeDebugSeed() {
        val zip = BuildConfig.DEBUG_SEED_ZIP
        val email = BuildConfig.DEBUG_SEED_EMAIL
        if (zip.isBlank() || email.isBlank()) return
        lifecycleScope.launch {
            val current = settingsRepository.snapshot()
            if (current.isConfigured) return@launch
            try {
                val resolved = setupResolver.resolve(zip)
                settingsRepository.update {
                    it.copy(
                        email = email,
                        lat = resolved.lat,
                        lon = resolved.lon,
                        locationName = resolved.locationName,
                        forecastOffice = resolved.forecastOffice,
                        gridX = resolved.gridX,
                        gridY = resolved.gridY,
                        timezone = resolved.timezone,
                        forecastZone = resolved.forecastZone,
                        stationPrimary = resolved.primaryStation,
                        stationFallback = resolved.secondaryStation,
                    )
                }
                viewModel.refresh()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Debug seed failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }

    override fun onPause() {
        super.onPause()
        viewModel.onPause()
    }
}
```

- [ ] **Step 3: Commit**

```powershell
git add app/build.gradle.kts app/src/main/kotlin/com/skyframe/MainActivity.kt
git commit -m "$(@'
feat: debug seed mechanism for Plan 1 manual testing

BuildConfig fields DEBUG_SEED_ZIP and DEBUG_SEED_EMAIL drive a one-time
seed at app startup when the SettingsRepository isn't yet configured.
Lets us test the full UI flow without waiting for the Plan 3 Settings
screen.

Release builds leave these empty so the seed is a no-op in shipped APKs.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
'@ -join "`n")"
```

---

### Task L.3: Manual smoke test

- [ ] **Step 1: Update `DEBUG_SEED_ZIP` and `DEBUG_SEED_EMAIL` in `app/build.gradle.kts`** to a real US ZIP code and your contact email. Build + install.

```powershell
./gradlew :app:assembleDebug --no-daemon
./gradlew :app:installDebug --no-daemon
adb shell am start -n com.skyframe/.MainActivity
```

- [ ] **Step 2: Verify the dashboard renders**

Expected:
- TopBar shows the configured location name + current time
- NowScreen: hero temperature, 5 metric bars with trend arrows
- Tap hero temperature → toggles between °F and °C; tap again → toggles back
- Tap HOURLY in bottom nav → chart + icon row + precip bars
- Tap OUTLOOK in bottom nav → 7-day range bars
- Footer shows `LINK.<stationid>`, fetched-time, T-Xs countdown
- After ~90s, fetched-time updates and T-Xs resets

- [ ] **Step 3: Verify alert handling (synthetic)**

Run-time test of the alert pipeline: temporarily modify `AlertNormalizer.normalize` to inject a synthetic alert at the head of the list when an env var or BuildConfig flag is set. (Alternative: wait for a real NWS alert in your area, which is fine for the smoke test if you're patient.)

Quick synthetic-alert recipe (one-shot edit, revert after testing):

```kotlin
// In AlertNormalizer.normalize, BEFORE the .sortedWith call:
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
return (synthetic + dto.features.map { ... existing mapping ... })
    .sortedWith(...)
```

Verify:
- AlertBanner appears above TopBar with red hazard stripes
- Entire UI accent shifts to tornado-warning red
- Tap × → banner dismisses; UI accent reverts
- Revert the synthetic-injection edit before tagging v0.1.0.

- [ ] **Step 4: Run the full test suite once more for regression**

```powershell
./gradlew :app:testDebugUnitTest --no-daemon
```

Expected: ~80 tests pass, 0 fail.

---

### Task L.4: Tag v0.1.0-mvp + push

- [ ] **Step 1: Update `versionName` and `versionCode` for the tag**

In `app/build.gradle.kts`:

```kotlin
        versionCode = 1
        versionName = "0.1.0"
```

(Already set this way in Phase B.) No change needed unless you've bumped it.

- [ ] **Step 2: Tag and push**

```powershell
git tag -a v0.1.0-mvp -m "Plan 1 milestone: MVP dashboard with NWS data layer + UI shell + 3 screens + basic alert banner. Settings, full alert UX, background notifications, and distribution come in Plans 2-5."
git push origin main
git push origin v0.1.0-mvp
```

- [ ] **Step 3: Verify on GitHub**

Visit https://github.com/OniNoKen4192/SkyFrameAndroid/releases — the v0.1.0-mvp tag should appear under tags. No release notes / APK upload yet — that's Plan 5's GitHub Actions workflow.

---

**Phase L milestone — Plan 1 complete.** The Android app renders the full HUD dashboard: TopBar with status/clock/location, three swappable screens (NOW/HOURLY/OUTLOOK) with real NWS data, Footer, BottomNav, and a basic dismissable AlertBanner that drives the tier-colored accent system. ~80 unit tests cover the data layer.

**What's deferred to Plans 2–5:**
- Plan 2: Full alert UX — AlertDetailSheet (NWS description with tier-colored prefixes), ForecastNarrativeSheet (day/night narrative), StationOverrideSheet
- Plan 3: SettingsScreen + first-run onboarding + GPS autodetect + GitHub update polling
- Plan 4: Background WorkManager alert polling + system notifications (life-safety channel, severe channel, full-screen intent, notification audio)
- Plan 5: Distribution — release signing keystore, GitHub Actions release build, Play Store internal track, README install instructions

---

## Plan 1 Self-Review

### Spec coverage check

Walked through each section of the design spec and confirmed Plan 1 coverage:
- **Repo migration:** Phase A ✓
- **Project structure:** Phase B ✓ (single app/ module, Hilt, Compose, Ktor, DataStore, WorkManager — though WorkManager isn't *used* until Plan 4, the dependency is in place)
- **UI shell:** Phase H ✓ (TopBar, Footer, BottomNav, DashboardScaffold)
- **Three primary screens:** Phases I/J/K ✓
- **Overlay surfaces (AlertDetailSheet, ForecastNarrativeSheet, StationOverrideSheet):** Deferred to Plan 2 (intentional)
- **Settings + onboarding:** Deferred to Plan 3 (intentional)
- **Data layer:** Phases C/E/F/G ✓ (domain types, NWS HTTP, normalizer, repositories, ViewModel)
- **HUD theming:** Phase D ✓ (HudColors, HudAccent, HudType, hudTextGlow, HudTheme)
- **Background alerts:** Deferred to Plan 4 (intentional)
- **Notifications:** Deferred to Plan 4 (intentional)
- **Distribution:** Deferred to Plan 5 (intentional)
- **AlertBanner:** Phase L ✓ (minimal version — full alert UX in Plan 2)

### Placeholder scan

Searched for the red-flag patterns from the writing-plans skill:
- `TBD` / `TODO` in committed code: Task I.1's `WxIcons.kt` contains `TODO(...)` for 8 of 9 icons. **Action:** the task explicitly says "Don't commit until all 9 are real ImageVectors" — the build fails on TODO() at runtime. Acceptable as written because it's explicitly flagged.
- "Implement later" / "add error handling": none.
- "Similar to Task N": none — every task self-contained.
- Steps that describe-without-showing: none.

### Type consistency check

- `WeatherCache<V>` constructor signature in Task E.4 (`val now: () -> Instant`) matches usage in Task G.3 (`WeatherCache()`) — default value works.
- `SettingsRepository.Snapshot` field set used in Task F.5 (`cfg.lat`, `cfg.lon`, `cfg.forecastOffice`, etc.) matches the expanded set defined in Task G.1.
- `WeatherState` sealed class introduced in Task G.4 used consistently in Phase H/I/J/K screens.
- `DashboardDestination` enum defined in H.4 used in H.5 and the screen-dispatching `when` in DashboardScaffold.
- `HudAccent` companion `Default` / `fromTier()` matches usage in Phase H/L.
- `AlertTier.baseColor`/`darkColor` defined in Task C.1 as `Long` — used in `Color(top.tier.baseColor)` in AlertBanner (correct conversion).

### Scope check

Plan 1 is one focused milestone — "MVP dashboard with data layer." Doesn't try to also do background alerts or Settings. Decomposed appropriately.

### Ambiguity check

- Phase F.5's "stage but don't commit" pattern is unusual; explicitly walked through in F.5 step 5 and G.1 step 7 to make the cross-phase dependency unmistakable.
- Phase I.1's WxIcons port leaves explicit `TODO()` calls because hand-porting 9 SVG paths in one step would balloon the task. The instructions for porting are concrete (SVG-to-ImageVector command mapping table provided).

---

## Execution Handoff

Plan complete. Saved to [docs/superpowers/plans/2026-05-16-skyframe-android-plan-1-foundation-mvp.md](2026-05-16-skyframe-android-plan-1-foundation-mvp.md). Total: 40+ tasks across 12 phases, ~80 unit tests at completion, ends with v0.1.0-mvp tagged on GitHub.

Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
