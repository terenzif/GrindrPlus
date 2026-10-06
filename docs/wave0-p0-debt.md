# Wave 0 — frozen P0 debts

Freeze list for Ver.5 Wave 0. These items are **in scope for green**; do not expand the list casually. Source analysis: [codebase-analysis-critical-issues.md](codebase-analysis-critical-issues.md), pack notes in `mapping-packs/179451.json`, and runtime paths below.

**Vision:** [vision.md](vision.md)

## P0 freeze

| ID | Debt | Evidence (paths) | Ver.5 done when |
| --- | --- | --- | --- |
| P0-1 | **Version gate abort in `GrindrPlus.init`** | `DialogManager.checkVersionCodes` sets `shouldShowVersionMismatchDialog`; `GrindrPlus.init` returns early and skips hook setup (`app/src/main/java/com/grindrplus/ui/DialogManager.kt`, `GrindrPlus.kt`) | Unsupported / unknown `versionCode` soft-continues with pack-driven skips; no hard abort of the whole module solely for version mismatch |
| P0-2 | **Cross-version literal fallbacks** | Literals documented as Grindr `26.16.1` (`179451`) in `GrindrPlus.kt`, `Obfuscation.kt`, `RetrofitUtils.kt`, etc.; used even when another pack is active if a key is missing | With an **active** pack, missing keys soft-skip — no `26.16.1` literal cross-fill ([ADR 0001](adr/0001-mapping-pack-schema-v2.md)) |
| P0-3 | **Cosmetic toggles** | Manage Hooks switches look ON while hooks soft-skip or never apply; Settings only mirror `Config` booleans (`SettingsViewModel`, `HookManager`) | UI shows `enabled` / `disabled` / `skipped` / `partial` / `failed` + reason ([ADR 0002](adr/0002-settings-hook-truthfulness.md)); E2E matrix records truth |
| P0-4 | **`hooks.status` ignored** | `MappingPack.hooks` / `MappingHookStatus` parsed in `MappingDictionary` but never read by `HookManager.registerHooks` | `HookManager` consumes pack status before `hook.init()` ([ADR 0001](adr/0001-mapping-pack-schema-v2.md)) |
| P0-5 | **Play tip ≠ mapping `versionCode`** | `PlayGrindrDownloadStep` logs when Play tip ≠ fork target and still downloads tip (`manager/installation/steps/PlayGrindrDownloadStep.kt`) | Install/download prefers pack-known / catalog `versionCode` (Morphe A + `index.json`, [ADR 0003](adr/0003-morphe-a-patch-backend.md)) |
| P0-6 | **Soft-skip list (26.16.1)** | Pack + hooks: Chat terminal, Video calls, Disable shuffle, Notification Alerts (`mapping-packs/179451.json`; `ChatTerminal`, `EmptyCalls`, `DisableShuffle`, `NotificationAlerts`) | Listed skips are intentional, surfaced in Settings as `skipped`, and covered in [e2e-settings-runtime-runbook.md](e2e-settings-runtime-runbook.md) — not silently “ON” |

## Soft-skip inventory (26.16.1 / 179451)

| UI setting | Hook class | Reason (pack) |
| --- | --- | --- |
| Chat terminal | `ChatTerminal` | `chatMessageMetaData absent` |
| Video calls | `EmptyCalls` | `IndividualChatNavViewModel/isTalkBefore absent` |
| Disable shuffle | `DisableShuffle` | `fingerprint absent on this DEX` |
| Notification Alerts | `NotificationAlerts` | `notification_reminder_time absent` |

Also marked `partial` in the same pack (not full skips, but not cosmetic-green): `DisableBoosting`, `ProfileDetails`, `BanManagement`.

## Explicitly not in this freeze

- Thread-safe maps / scheduler backoff (called out in analysis follow-ups; not Wave 0 P0).
- Morphe B static feature parity ([vision.md](vision.md) — after Ver.5 green).
- Rewriting every historical `Logger.writeRaw(stackTraceToString())` call site (sanitizer work already started; not listed above).

## Clearance

Wave 0 / Ver.5 platform acceptance requires P0-1…P0-6 closed or explicitly waived in writing with owner + date. Update this table’s “done when” column with PR links when clearing.
