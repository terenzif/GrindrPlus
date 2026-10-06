# ADR 0004: Morphe B — static / hybrid feature parity (post Ver.5 green)

- **Status:** Accepted (implementation started)
- **Date:** 2026-10-04 (updated 2026-10-05)
- **Related:** [vision.md](../vision.md), [0003-morphe-a-patch-backend.md](0003-morphe-a-patch-backend.md), [morphe-b.md](../morphe-b.md)

## Context

Ver.5 delivers **Morphe A**: orchestrate APK patch + embed Vector/LSPatch. Some hooks remain fragile (R8 churn, Cascade V2, missing fingerprints). Morphe B adds **static APK mutations** plus **runtime remaps** for features that are pack-skipped or unregistered.

## Decision

1. **Gate:** Morphe B starts after Ver.5 green + device E2E (lab satisfied 2026-10-05).
2. **Scope:** Pack-skipped / unregistered features: Chat terminal, EmptyCalls, Disable shuffle, Notification Alerts, Favorites, WebSocketAlive, Status Dialog.
3. **Delivery:**
   - `MorpheBPatchEngine` runs **before** LSPatch in `MorpheOrchestrator` and embeds `assets/grindrplus/morphe_b.json`.
   - `RUNTIME_REMAP` hooks use MappingDictionary symbols (tip: `websocket.a`, `VideoCallHasNotChattedException`, …).
   - `DEFERRED` features stay Settings-honest skipped until DEX allows.
4. **Non-goal:** Do not replace HookManager wholesale.

## Consequences

- Rootless install path carries B marker automatically.
- Dual maintenance: hooks + static marker (DEX rewriter can plug into the same engine later).
