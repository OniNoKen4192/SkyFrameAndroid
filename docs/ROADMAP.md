# SkyFrame Android — Roadmap

5-plan rollout from initial scaffold to Play Store release. Single source of truth for "where are we." Update the status line for a plan as soon as it ships.

| Plan | Scope | Status | Tag |
|---|---|---|---|
| **Plan 1** — Foundation + MVP dashboard | Repo migration, Kotlin/Compose/Hilt/Ktor scaffold, full NWS data layer, HUD theming, 3 screens (NOW/HOURLY/OUTLOOK), basic AlertBanner | ✅ **Shipped** | [`v0.1.1-mvp`](https://github.com/OniNoKen4192/SkyFrameAndroid/releases/tag/v0.1.1-mvp) |
| **Plan 1B** _(likely)_ — Trend data | Add `/observations?limit=6` fetch + revive trend arrows in HudMetricBar | Not started | — |
| **Plan 2** — Full alert UX | AlertDetailSheet (tap alert event → NWS description with HAZARD/SOURCE/IMPACT tier-colored), ForecastNarrativeSheet (▶ glyph / day-row tap → day+night narrative), StationOverrideSheet (Footer LINK tap → AUTO/FORCE_SECONDARY with live preview) | Not started | — |
| **Plan 3** — Settings + onboarding + updates | Replace Settings Toast stub with real screen; first-run onboarding flow with permissions; GPS autodetect button; opt-in GitHub release polling | Not started | — |
| **Plan 4** — Background alerts | WorkManager periodic alert poll + system notifications (life-safety + severe channels) + 1050 Hz NWR-style notification audio + battery-optimization whitelist + POST_NOTIFICATIONS permission flow + full-screen intent for top-tier alerts | Not started | — |
| **Plan 5** — Distribution | Release signing keystore + GitHub Actions tag→APK pipeline + Play Store internal track + README install instructions + Data Safety form | Not started | — |

## Plan dependencies

```
Plan 1 (foundation)
   ├── Plan 1B (trends) ─────────┐
   ├── Plan 2 (sheets) ─────────┤
   │       └──── Plan 3 (settings + onboarding) ──┐
   │                                              ├── Plan 5 (distribution)
   │                                              │
   └── Plan 4 (background alerts) ────────────────┘
       (independent of Plans 2/3 — could ship out of order)
```

Plan 2 and Plan 4 are independent and can run in either order. Plan 3 depends on having something real to put in Settings (so works best after Plan 2 lands the StationOverrideSheet). Plan 5 needs all user-facing features done.

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
