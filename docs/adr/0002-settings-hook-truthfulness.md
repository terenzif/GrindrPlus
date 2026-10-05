# ADR 0002: Settings ↔ hook truthfulness

- **Status:** Accepted (design for Ver.5)
- **Date:** 2026-10-04
- **Deciders:** GrindrPlus Ver.5 maintainers
- **Related:** [vision.md](../vision.md), [0001-mapping-pack-schema-v2.md](0001-mapping-pack-schema-v2.md), [e2e-settings-runtime-runbook.md](../e2e-settings-runtime-runbook.md)

## Context

Manager Settings builds the **Manage Hooks** group from `Config.getHooksSettings()` in `app/src/main/java/com/grindrplus/manager/settings/SettingsViewModel.kt`. Each registered hook in `HookManager` gets a boolean switch via `Config.initHookSettings(hook.hookName, …)`.

Today the UI is effectively ON/OFF only. Pack metadata in `hooks.<Name>.status` (see `mapping-packs/179451.json`) is ignored at runtime, so a toggle can appear ON while the hook soft-skips or never applies — a **cosmetic toggle**. That violates the Ver.5 E2E Settings↔hooks pillar.

## Decision

### Runtime / UI states

Every Manage Hooks row exposes one of these states (plus an optional human-readable **reason**):

| State | Meaning |
| --- | --- |
| `enabled` | User ON and hook initialized successfully for this Grindr build |
| `disabled` | User OFF (config); hook not initialized |
| `skipped` | User ON but pack/runtime soft-skipped (missing fingerprint, empty mapping, `hooks.status=skipped`, etc.) |
| `partial` | User ON; hook ran but pack marks `partial` or only a subset of sites applied |
| `failed` | User ON; init threw or hard mapping failure after attempts |

`reason` is required for `skipped`, `partial`, and `failed` whenever known (prefer pack `hooks.*.reason`, else runtime message).

### No cosmetic toggles

- A switch must not look fully ON/effective when the hook did nothing.
- Prefer: keep the user preference, but show status badge / subtitle (`skipped — …`) so the control is not mistaken for a working feature.
- E2E acceptance uses [e2e-settings-runtime-runbook.md](../e2e-settings-runtime-runbook.md); known soft-skips on `26.16.1` must surface as `skipped`, not `enabled`.

### Ownership of truth

| Source | Owns |
| --- | --- |
| `Config` | User intent (want enabled?) |
| Active mapping pack `hooks` + `HookManager` | Achieved state after init |
| Settings UI (`SettingsViewModel` / setting types) | Presentation of intent + achieved state + reason |

## Consequences

- **Positive:** Users and testers can trust Settings; soft-skips become visible; Wave 0 “cosmetic toggles” debt has a clear done definition.
- **Negative:** Requires a bridge or persisted status channel from module runtime → Manager UI (exact transport is an implementation detail; may reuse `BridgeClient` / config mirror).
- **Follow-on:** HookManager must consume pack status ([ADR 0001](0001-mapping-pack-schema-v2.md)); E2E matrix rows must assert status notes, not only feature behavior.

## References

- Hook registration: `app/src/main/java/com/grindrplus/utils/HookManager.kt`
- Settings groups: `app/src/main/java/com/grindrplus/manager/settings/SettingsViewModel.kt`
- Pack hook statuses example: `mapping-packs/179451.json` → `"hooks"`
