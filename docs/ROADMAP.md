# SkyFrame Android — Roadmap

5-plan rollout from initial scaffold to Play Store release. Single source of truth for "where are we." Update the status line for a plan as soon as it ships.

| Plan | Scope | Status | Tag |
|---|---|---|---|
| **Plan 1** — Foundation + MVP dashboard | Repo migration, Kotlin/Compose/Hilt/Ktor scaffold, full NWS data layer, HUD theming, 3 screens (NOW/HOURLY/OUTLOOK), basic AlertBanner | ✅ **Shipped** | [`v0.1.1-mvp`](https://github.com/OniNoKen4192/SkyFrameAndroid/releases/tag/v0.1.1-mvp) |
| **Plan 2** — Full alert UX + trends | AlertDetailSheet (tap alert event → NWS description with HAZARD/SOURCE/IMPACT tier-colored), ForecastNarrativeSheet (▶ glyph / day-row tap → day+night narrative), StationOverrideSheet (Footer LINK tap → AUTO/FORCE_SECONDARY with live preview), observation history fetch + trend arrows | ✅ **Shipped** | [`v0.2.0`](https://github.com/OniNoKen4192/SkyFrameAndroid/releases/tag/v0.2.0) |
| **Plan 3** — Settings + onboarding + updates | Replace Settings Toast stub with real screen; first-run onboarding flow (force-completion); GPS autodetect via platform LocationManager (no Play Services dep); JIT FINE_LOCATION permission; opt-in GitHub release polling (sideload-only, conditional on install source); synthetic update alert injection via existing AlertBanner pipeline | ✅ **Shipped** | [`v0.3.0`](https://github.com/OniNoKen4192/SkyFrameAndroid/releases/tag/v0.3.0) |
| **Plan 4** — Background alerts | WorkManager 15-min periodic + ExpeditedWorkRequest 2-min escalation for top-tier alerts; 5 notification channels (life_safety / severe_weather / watches / advisories / app_updates) in 2 groups; 1050 Hz NWR-style audio (47 CFR § 11.45 compliant); FullScreenAlertActivity for life-safety alerts; permission cascade in onboarding (POST_NOTIFICATIONS + USE_FULL_SCREEN_INTENT + battery whitelist); bidirectional acknowledgment sync | ✅ **Shipped** | [`v0.4.0`](https://github.com/OniNoKen4192/SkyFrameAndroid/releases/tag/v0.4.0) |
| **Plan 5** — Distribution | Release signing keystore + GitHub Actions tag→APK pipeline + Play Store internal track + README install instructions + Data Safety form | Not started | — |

## Plan dependencies

```
Plan 1 (foundation) ✓
   └── Plan 2 (sheets + trends) ✓
           └── Plan 3 (settings + onboarding) ✓ ──┐
                                                  ├── Plan 5 (distribution)
                                                  │
       Plan 4 (background alerts) ✓ ──────────────┘
```

Plan 3 and Plan 4 are independent and can run in either order. Plan 5 needs all user-facing features done.

## Out-of-roadmap (deferred indefinitely)

Per the design spec's explicit YAGNI list — not in any of Plans 1–5:

- iOS app (would require a self-hosted APNs relay; revisit only after user demand survey)
- Multiple saved locations
- Home screen widget
- Wear OS / Android Auto / tablet two-pane layouts
- Color picker / theme switcher
- Offline forecast cache
- Multi-user / sync

## How this doc is maintained

- **Update the Status column** as soon as a plan ships (in the same commit as the tag).
- **Add a row for any new sub-plan** (e.g. Plan 1B, Plan 2B) as it gets brainstormed.
- **The design spec** at [docs/superpowers/specs/2026-05-16-skyframe-android-design.md](superpowers/specs/2026-05-16-skyframe-android-design.md) remains the authoritative scope document; this roadmap is the convenience overview.
