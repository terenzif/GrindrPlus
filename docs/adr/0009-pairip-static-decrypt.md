# ADR 0009: PairIP on Play hosts — static decrypt via Morphe B

- **Status:** Accepted — phase 1 landed (native RX splice + integrity DEX stubs)
- **Date:** 2026-10-08
- **Related:** [0007-morphe-b-bytecodepatch.md](0007-morphe-b-bytecodepatch.md), [0005-dual-apk-morphe-alloy.md](0005-dual-apk-morphe-alloy.md), [0008-libxposed-api-102.md](0008-libxposed-api-102.md)
- **Lab evidence:** [docs/lab/pairip/](../lab/pairip/) — start with [TOMBSTONE](../lab/pairip/TOMBSTONE.md) + [EXPERIMENT_LOG](../lab/pairip/EXPERIMENT_LOG.md)

## Context

Grindr Play **26.19.0 / 185656** ships Google PairIP (`com.pairip.*`, `libpairipcore.so`, opaque `assets/*` VM blobs). Alloy + Vector injection crashes PairIP natives; stubbing PairIP reaches `GrindrPlus.init` then dies with **SIGILL in `libsqliteJni.so!JNI_OnLoad`**. On-disk `JNI_OnLoad` bytes are ciphertext; a **stock** (no Vector) process map shows plaintext AArch64 at the same offset.

## Decision

1. **Stop** Alloy runtime PairIP stubs as the product path (tombstoned).
2. **Morphe B phase 1** ([`PairIpStaticDecrypt`](../../app/src/morphe-b/java/com/gpp/morphe/b/PairIpStaticDecrypt.kt)):
   - Detect PairIP on input APKs.
   - Splice decrypted natives from `pairip-decrypt/<versionCode>/` (produced by [`scripts/pairip_dump_natives.py`](../../scripts/pairip_dump_natives.py) against stock Grindr).
   - DEX-stub `SignatureCheck.verifyIntegrity` / `LicenseClient.checkLicense` so LSPatch re-sign does not trip integrity; keep `StartupLauncher` / `VMRunner` + real `libpairipcore` for string/VM init on the **rootless clone** (no Vector).
   - Fail-soft when pack missing (`pairip-static-decrypt` deferred in Morphe B marker).
3. Alloy lab green remains on non-PairIP hosts until packs cover the tip.
4. Settings must not claim PairIP Play fixed without a pack apply.

## Pack layout

```
pairip-decrypt/185656/
  pack.json
  libsqliteJni.so    # encrypted ELF shell + stock-decrypted RX PT_LOAD
```

## Consequences

- Morphe A embed path runs B before LSPatch; place decrypt packs beside staged APKs or under repo `pairip-decrypt/`.
- Phase 2 (optional): more libs, offline VM string decrypt, automatic dump during Install when root is available.
- Mapping packs stay versionCode-driven; decrypt packs use the same key.