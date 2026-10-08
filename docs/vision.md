# GrindrPlus Ver.5 — Vision

Official product vision for the Ver.5 release line. Implementation details live in ADRs and runbooks linked below; this document defines scope, sequencing, and non-negotiable principles.

## Principles

1. **Version-agnostic module** — GrindrPlus must not treat any single Grindr build (for example `26.16.1` / `versionCode` `179451`) as destiny. Compile-time literals may exist as last-resort aids, but architecture, Settings truthfulness, and release gates assume **pack-driven** targeting.
2. **Soft-fail, pack-driven mappings** — Resolve names through  
   `remote → device cache → module assets → literals`, with safe fallback at every step. Missing or invalid packs must **not** abort init. See `MappingDictionary.loadForVersion` in `app/src/main/java/com/grindrplus/core/mapping/MappingDictionary.kt` and [remote-mapping-packs.md](remote-mapping-packs.md).
3. **Personal / lab use must not constrain architecture** — Convenience for a single maintainer device or lab Grindr build must never freeze hard version gates, cosmetic Settings, or cross-version literal fallbacks into the product contract.

## What Ver.5 includes

| Pillar | Meaning |
| --- | --- |
| **Platform** | Soft-fail mapping platform (schema evolution, catalog, fingerprints), truthful Settings↔runtime contract, no hard abort on unsupported `versionCode`. |
| **E2E Settings ↔ hooks** | Every Manage Hooks toggle (and high-priority Other Settings) has a documented ON/OFF assert path; UI state matches runtime. See [e2e-settings-runtime-runbook.md](e2e-settings-runtime-runbook.md) and [adr/0002-settings-hook-truthfulness.md](adr/0002-settings-hook-truthfulness.md). |
| **Morphe A** | Orchestrator-rootless delivery: export installed Play Grindr → clone Grindr++ → LSPatch `-l 2` embed. Not a feature rewrite. See [adr/0003-morphe-a-patch-backend.md](adr/0003-morphe-a-patch-backend.md). |
| **Dual APK** | Two user-facing packages, Manager label **GrindMod**: `com.gpp.morphe` (rootless; creates **Grindr++** clone from installed Play Grindr) and `com.gpp.alloy` (rooted Vector; Settings modding toggle). Product umbrella **Grindr++**. See [adr/0005-dual-apk-morphe-alloy.md](adr/0005-dual-apk-morphe-alloy.md). |

Anonymous **opt-in** telemetry remains allowed (existing Manager switch `analytics` in `SettingsViewModel`).

## Explicitly after Ver.5 green

**Morphe B** — static / hybrid feature parity (`MorpheBPatchEngine` + runtime remaps). Starts only after Ver.5 acceptance (“green”). See [morphe-b.md](morphe-b.md).

**Alloy DexKit** — NexAlloy-style fingerprint → DexKit → Vector on the `alloy` APK (after dual-APK split).

## Sequence (non-negotiable order)

```text
Morphe A  →  Ver.5 green  →  Morphe B
Dual APK (morphe / alloy) lands as product shape; Alloy DexKit and Morphe B DEX rewriter follow the split.
```

1. Land Morphe A (patch backend / orchestrator around LSPatch).
2. Close Ver.5 platform + E2E Settings↔hooks acceptance (including Wave 0 P0 debt freeze clearance).
3. Only then expand into Morphe B static feature parity.
4. Ship dual APK (`morphe` / `alloy`) per ADR 0005; then Alloy DexKit and MorpheApp-style bytecode in B.

## Related decisions and freeze lists

| Doc | Role |
| --- | --- |
| [adr/0001-mapping-pack-schema-v2.md](adr/0001-mapping-pack-schema-v2.md) | Mapping pack schema v2 |
| [adr/0002-settings-hook-truthfulness.md](adr/0002-settings-hook-truthfulness.md) | Settings UI runtime states |
| [adr/0003-morphe-a-patch-backend.md](adr/0003-morphe-a-patch-backend.md) | Morphe A patch backend |
| [adr/0004-morphe-b.md](adr/0004-morphe-b.md) | Morphe B static parity |
| [adr/0005-dual-apk-morphe-alloy.md](adr/0005-dual-apk-morphe-alloy.md) | Dual APK Morphe / Alloy |
| [e2e-settings-runtime-runbook.md](e2e-settings-runtime-runbook.md) | E2E matrix skeleton |
| [wave0-p0-debt.md](wave0-p0-debt.md) | Frozen P0 debts for Wave 0 |
| [remote-mapping-packs.md](remote-mapping-packs.md) | Current pack load order (schema v1 baseline) |
| [manager-ui-lspatch.md](manager-ui-lspatch.md) | Manager LSPatch / Play download notes |
