# Configuration, inspector, and commands

Open the inspector with its creative-tab item, **F8** (rebindable), or `/sealanttools`. Open Create/Catnip configuration through Mods → Sealant → Config or `/sealantconfig`. The world continues running while the inspector is open. Reports update on request; copying writes only to the local clipboard.

## Permissions and persistence

Display preferences are CLIENT settings in `config/sealant-client.toml`. World physics uses the SERVER spec in the active game directory's `config/sealant-server.toml`, unless the world's `serverconfig` supplies an override. SERVER does not guarantee automatic per-world file creation. Multiplayer server state is authoritative.

World controls and configuration require permission level 2, validated again on the server using a closed action enum and ValueSpec checks. Disabled buttons are not the security boundary. No arbitrary command text, client permissions, or unconstrained UUIDs are trusted. Relevant SERVER edits are saved on the next tick; the server synchronizes effective mode/inset settings.

## World controls

| Setting | Default and meaning |
| --- | --- |
| `simulation.mode` | `LIGHTWEIGHT`: sealed dry displacement; `FIELD_LOW`: 5 Hz / 2 × 2 panels; `FIELD_FULL`: 20 Hz / 4 × 4 panels. |
| `takeover.entryMargin` | 0.125 blocks, range 0–0.5. Inset new entry; release outside original OBB. |
| `takeover.intersectionVolume` | false. If enabled, other Sable bodies require complete entry/exit with fixed approximately 1% tolerance; intermediate overlap keeps state. |
| `field.blockVolumes` | State-based declared occupancy rules, values 0–1. See [table and formulas](../architecture/block-volumes.md). |

Modes preserve W/G when pausing for lightweight operation. No catch-up exchange occurs during the pause. Mode changes do not enable auto takeover or custom physics if independently disabled. Complete-entry mode replaces entryMargin only for other Sable bodies; carrier, vanilla entities, eyes, and visuals are unchanged. Percent settings/reports are intentionally absent.

Auto/physics toggles are transient for the loaded dimension. Manual buoyancy/drag switches persist per Sable body. Effective native disable is manual OR chamber management, so a manual restore cannot override current takeover.

## Inspector pages and reset

Display controls affect local water/lighting/outlines and preference persistence. Water/air debug subscriptions are server-authorized, per player and dimension, and transient. Diagnostics provides target/status, copying, and profiling. Physics/maintenance provides auto/physics, native per-body flags, and group reset.

Marker targeting traces up to 64 blocks from the logical eye into chamber-local bounds; inside-box targeting is supported. A marker UUID identifies a chamber, whereas native-body buttons use the existing Sable block ray target and body UUID.

Inspector reset displays the full marker UUID and explicitly confirms clearing the entire geometric group's air/water state. The server checks the current target again and rejects a changed target. There is no undo. Reading/opening/copying reports does not reset state. The direct reset command executes immediately and has no GUI confirmation.

## Local settings

| Path | Default | Meaning |
| --- | --- | --- |
| visuals.visualEffects | true | Main chamber visual path; debug samples are independent. |
| visuals.externalWaterOverlay | true | Actual exposed inner/outer transparent-shell water overlays. |
| visuals.externalWaterCulling | true | Native sea-water atlas mask in vanilla/Sable VANILLA; no Sodium/Iris adapter. |
| visuals.waterSurface | true | Visible field water mesh only. |
| visuals.ambientLighting | true | Parent chamber lighting inheritance. |
| visuals.alwaysShowOutlines | false | Outlines without item/F3+B; existing F3+B remains. |
| visuals.waterOpacity | 145 | Water alpha multiplier, 0–255. |
| tools.debugPointLimit | 1024 | Local draw count, 16–1024; does not reduce server/network work. |
| tools.profileSeconds | 30 | 1–300 game-tick seconds per capture. |

Create's save/reset/discard workflow is reused. Display commands and GUI share persistent preferences. These settings do not include debug subscriptions, transient world toggles, W/G, or manual body user_data. No local display choice controls server swimming/breathing.

## Commands

| Command | Scope |
| --- | --- |
| `/sealant chamber status [marker UUID]` | Permission 2; local field, pressure, exchange/sleep diagnostics. |
| `/sealant chamber waterdebug on/off` | Permission 2; blue water, orange submerged openings, green air openings. |
| `/sealant chamber airdebug on/off` | Permission 2; actual pink GasPV samples in field modes; assumed-dry samples in lightweight. |
| `/sealant chamber physics on/off/status` | Permission 2; current dimension's custom simulation. |
| `/sealant auto on/off/status` | Permission 2; current dimension's automatic takeover. |
| `/sealant buoyancy on/off/status [body UUID]` | Permission 2; manual native buoyancy. |
| `/sealant drag on/off/status [body UUID]` | Permission 2; manual native water drag. |
| `/sealant chamber reset <marker UUID>` | Permission 2; immediate whole-group reset. |
| `/sealant perf start <1..300>` / `/sealant perf report` | Permission 2; game-tick capture window. |
| `/sealantvisual on/off/status` | Local persistent main visuals. |
| `/sealantvisual water on/off/status` | Local persistent water display and Sodium detection status. |
| `/sealantvisual light on/off/status` | Local persistent inherited lighting. |
| `/sealantvisual groups` | Local group lighting report. |

A 30-second capture means 600 game ticks and can take longer than 30 wall-clock seconds during lag. Phase `n` means calls, not TPS. Nested phases must not be summed blindly. See [performance](../development/performance.md).

The original tools and later water-material/overlay UI received user confirmation. Newly changed permissions, target rejection, persistence, and multiplayer behavior require focused checks rather than inheriting unrelated success reports. FIELD swimming and complete-entry feel have separate pending tests in [HANDOFF](../../HANDOFF.md).
