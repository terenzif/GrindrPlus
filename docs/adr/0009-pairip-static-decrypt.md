# ADR 0009: PairIP on Play hosts — static decrypt via Morphe B

- **Status:** Accepted (direction); implementation TBD
- **Date:** 2026-10-08
- **Related:** [0007-morphe-b-bytecodepatch.md](0007-morphe-b-bytecodepatch.md), [0005-dual-apk-morphe-alloy.md](0005-dual-apk-morphe-alloy.md), [0008-libxposed-api-102.md](0008-libxposed-api-102.md)
- **Lab evidence:** [docs/lab/pairip/](../lab/pairip/) — start with [TOMBSTONE](../lab/pairip/TOMBSTONE.md) + [EXPERIMENT_LOG](../lab/pairip/EXPERIMENT_LOG.md)

## Context

Grindr Play **26.19.0 / 185656** ships Google PairIP (`com.pairip.*`, `libpairipcore.so`, opaque `assets/*` VM blobs). Alloy + Vector injection crashes PairIP natives; stubbing PairIP reaches `GrindrPlus.init` then dies with **SIGILL in `libsqliteJni.so!JNI_OnLoad`** (encrypted/garbage opcodes at PC). Real `pairipcore` under Vector also SIGILL/SIGSEGV. `System.loadLibrary` hooks break split-APK library namespaces.

Alloy early-bypass (`PairIpEarlyBypass` v6–v11) proved the failure mode and is **not** a product path for PairIP Play hosts.

## Decision

1. **Stop** iterating Alloy runtime PairIP stubs as the primary fix for Play PairIP builds.
2. Treat PairIP remediation as a **Morphe B / static** problem: offline decrypt or rewrite of PairIP-protected DEX (and native payloads if required) inside the existing dexlib2 / `MorpheBPatchEngine` pipeline (ADR 0007), fail-soft per site, Settings-honest.
3. Keep Alloy lab green on **non-PairIP** Grindr builds; soft-detect PairIP and document gap until static packs land.
4. Retain lab tombstone + bypass scaffolding as evidence; do not advertise Settings toggles that imply PairIP Play is fixed.

## Consequences

- Ver.5 “green” on Alloy may complete against non-PairIP hosts first; PairIP Play is a Morphe B (or pack) deliverable.
- Morphe A clone/LSPatch path may still hit PairIP on the clone until B decrypt runs in the pipeline (B → A order already in ADR 0007).
- Mapping packs stay versionCode-driven; PairIP decrypt artifacts should key off the same `versionCode`.
