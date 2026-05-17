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
