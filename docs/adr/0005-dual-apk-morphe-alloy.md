# ADR 0005: Dual APK — Morphe (rootless) and Alloy (rooted)

- **Status:** Accepted
- **Date:** 2026-10-05
- **Related:** [vision.md](../vision.md), [0003-morphe-a-patch-backend.md](0003-morphe-a-patch-backend.md), [0004-morphe-b.md](0004-morphe-b.md)

## Context

A single `com.grindrplus` APK mixed Vector/LSPosed-module UX and LSPatch install UX. Users need a clear product split. Continuity must favor the **rootless / UI-first** audience, not rooted power users. Display name stays **GrindrPlus** for both; distinction is package ID and Releases assets.

## Decision

### 1. Exactly two user-facing APKs (option 1)

| Flavor | `applicationId` | Launcher label | Role |
| --- | --- | --- | --- |
| `morphe` | `com.grindrplus.morphe` | GrindrPlus | Rootless Manager: Morphe A/B + LSPatch `-l 2` install path |
| `alloy` | `com.grindrplus.alloy` | GrindrPlus | Rooted Vector module + Manager **without** LSPatch tab |

Third-level names are architecture-explicit: **morphe** = Morphe A/B + embed; **alloy** = NexAlloy-style fingerprint → DexKit → Vector (Phase 2).

### 2. No bare `com.grindrplus` continuity on one channel only

Both IDs are new. Legacy `com.grindrplus` requires a one-time migrate (reinstall / re-patch / re-scope). UI brand stays GrindrPlus.

### 3. Slim embed payload is not a third product

LSPatch `-m` must not embed the fat Compose Manager into Grindr. Build may produce an internal **embedPayload** artifact (hooks + bridge + Xposed entry) for the Morphe patch pipeline. Users do not install it from the primary Releases list.

### 4. Dependency ownership

- `morphe`: pin and package LSPatch v0.8; own Install / MorpheOrchestrator UX.
- `alloy`: no packaged `lspatch.jar`; Vector/Xposed meta + module entry; Install nav omitted.

### 5. Bridge / signature

Custom permission `com.grindrplus.permission.ACCESS_BRIDGE_SERVICE` remains signature-protected and shared by name across flavors signed with the same key. Do not assume a single `applicationId` for Manager vs in-process hooks; query the active delivery package explicitly where needed.

## Consequences

- **Positive:** Clear UX; rootless keeps the primary narrative; rooted channel is explicit; embed size/stability can be controlled.
- **Negative:** One-time package migration; CI ships two APKs; Bridge/package queries must be flavor-aware.
- **Follow-ups:** ADR 0006 (Alloy DexKit), Morphe B bytecode rewriter (0004/0007), slim embed stripping.

## Migration

1. Uninstall or keep legacy `com.grindrplus` until cutover.
2. Install `GrindrPlus-morphe-*.apk` and/or `GrindrPlus-alloy-*.apk`.
3. Morphe: re-run patch/install for Grindr. Alloy: enable module in Vector and scope Grindr.
