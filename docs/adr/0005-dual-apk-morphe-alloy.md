# ADR 0005: Dual APK — Morphe (rootless) and Alloy (rooted)

- **Status:** Accepted (amended 2026-10-07)
- **Date:** 2026-10-05
- **Related:** [vision.md](../vision.md), [0003-morphe-a-patch-backend.md](0003-morphe-a-patch-backend.md), [0004-morphe-b.md](0004-morphe-b.md)

## Context

A single legacy `com.grindrplus` APK mixed Vector/LSPosed-module UX and LSPatch install UX. Users need a clear product split. Continuity must favor the **rootless / UI-first** audience, not rooted power users.

**Product naming (2026-10-07):** umbrella product is **Grindr++**; Manager UI for both flavors is **GrindMod** (`com.gpp.*`). Morphe also installs a **Grindr++** clone beside stock Play Grindr.

## Decision

### 1. Exactly two user-facing APKs

| Flavor | `applicationId` | Launcher label | Role |
| --- | --- | --- | --- |
| `morphe` | `com.gpp.morphe` | **GrindMod** | Rootless Manager: export installed Play Grindr → clone `com.grindrapp.android.plus` labeled **Grindr++** → LSPatch `-l 2` |
| `alloy` | `com.gpp.alloy` | **GrindMod** | Rooted Vector module + Manager **without** Install tab; Settings toggle for Vector enable/disable (root + CLI) |

### 2. Drawer model (asymmetric by design)

```text
Rootless:  Grindr (Play) | Grindr++ (clone) | GrindMod (Manager)
Rooted:    Grindr (Play + hooks) | GrindMod (Alloy)
```

Alloy has no Grindr++ clone; experts toggle modding from GrindMod Settings (or Vector). Hot reload (API 102) is code-swap only — not enable/disable.

### 3. Rootless install source

**Primary:** copy APK/splits from installed `com.grindrapp.android` (Play). No CDN / ambiguous third-party Grindr download as happy path. Custom Files remain emergency fallback only.

### 4. Slim embed payload is not a third product

LSPatch `-m` embed payload is build-only for the Morphe pipeline.

### 5. Dependency ownership

- `morphe`: LSPatch; Install UX; never a Vector module.
- `alloy`: sole Vector registrant (libxposed API 102; ADR 0008); Install nav omitted; root CLI toggle allowed.
- Bridge permission `com.gpp.permission.ACCESS_BRIDGE_SERVICE` stays signature-shared.

## Consequences

- **Positive:** Stock Play stays; patch visible as Grindr++; Manager disambiguated as GrindMod; no Grindr CDN host.
- **Negative:** Dual storage on Morphe; Alloy UX differs (documented).
- **Follow-ups:** ADR 0006, Morphe B rewriter.

## Migration

1. Uninstall legacy `com.grindrplus*` / old same-package patched hosts as needed.
2. Install `gpp-morphe-*.apk` and/or `gpp-alloy-*.apk`.
3. Morphe: install stock Grindr from Play → GrindMod Install → Create Grindr++.
4. Alloy: enable in Vector (or GrindMod Settings → Modding active); scope **only** Grindr.
