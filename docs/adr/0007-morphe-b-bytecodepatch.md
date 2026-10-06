# ADR 0007: Morphe B — MorpheApp bytecodePatch as STATIC_RESOURCE

- **Status:** Accepted (fingerprint dry-run backend landed; full MorpheApp rewriter TBD)
- **Date:** 2026-10-05
- **Related:** [0004-morphe-b.md](0004-morphe-b.md), [0005-dual-apk-morphe-alloy.md](0005-dual-apk-morphe-alloy.md)

## Context

Morphe B today embeds a JSON marker and uses RUNTIME_REMAP. Tip-DEX residuals need real bytecode mutation. Upstream MorpheApp provides `bytecodePatch` / fingerprints / `.mpp` bundles ([morphe-patches-template](https://github.com/MorpheApp/morphe-patches-template)).

## Decision

1. Integrate MorpheApp patcher **inside** `MorpheBPatchEngine` via [MorpheBytecodeBackend](../../app/src/main/java/com/grindrplus/morphe/b/MorpheBytecodeBackend.kt) (not as Morphe Manager product).
2. Pipeline: **B (bytecode) → A (LSPatch `-l 2` + slim embed) → SessionInstaller** on `com.grindrplus.morphe`.
3. Fail-soft per patch; Settings stay honest on miss.
4. Pilot Chat terminal and/or Favorites Cascade residual after patcher dependency lands.

## Current state

Default backend is `FingerprintScanBytecodeBackend`: scans `classes*.dex` for [BytecodePilotCatalog](../../app/src/main/java/com/grindrplus/morphe/b/BytecodePilotCatalog.kt) needles and writes `assets/grindrplus/bytecode_scan.json` (dry-run, no instruction rewrite yet). `NoOpMorpheBytecodeBackend` remains for tests. Next: MorpheApp patcher module + real `bytecodePatch` mutate.

## Acceptance

- [x] ≥1 static bytecode path applied in dry-run (fingerprint scan + report asset).
- [ ] Real instruction rewrite via MorpheApp `.mpp` / patcher.
- Slim embed still used; Settings truthfulness preserved.
