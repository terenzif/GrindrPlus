# PairIP experiment log — Alloy + Vector on Grindr Play 26.19

**Status: TOMBSTONED** for Alloy *runtime* neutralization.  
**Do not** open `pairip-v12+` Alloy hook loops. Next work is Morphe B static decrypt ([ADR 0009](../../adr/0009-pairip-static-decrypt.md)).

| | |
|--|--|
| Host | Grindr Play **26.19.0** / `versionCode` **185656** (splits: base + arm64 + it + xxhdpi) |
| Module | `com.gpp.alloy` (Vector / libxposed API 102) |
| Device | Xiaomi Mi A2 lite (`daisy`), Evolution X, Magisk + Zygisk Vector |
| Sessions | Cursor chat [GPP Alloy Vector103 A2](https://cursor.com) id `9ee7128e-16eb-47dc-8482-05faec536c40`; follow-up PairIP report chat |
| Dates | 2026-10-08 |
| Mapping pack | `185656` loads (`hasPack=true`) |
| Control | Non-PairIP **26.16.1 / 179451**: StatusDialog + profile details E2E **PASS** (same Alloy stack) |

Artifacts: [README](README.md) · [tombstone summary](tombstone_20_sigill_summary.md) · [full tombstone](tombstone_20_sigill.txt) · [DBG summaries](debug-9ee712.log) · stub `native/pairip-stub/` · code `PairIpEarlyBypass.kt`

---

## Lab hygiene (required for fair runs)

| Issue | Symptom | Mitigation |
|-------|---------|------------|
| LMK | Process dies with no Java FATAL; RSS ~410 MB; zram full | `gpp_swap` 512 MB under `/data/local/tmp` before launch; kill heavy apps |
| Autofill | Blocks unattended login | `settings put secure autofill_service null` for lab |
| Magisk | `VectorModToggle` `cliReady=false` | Magisk policy **allow (2)** for `com.gpp.alloy` |
| Play updates | Device bumps back to 26.19 | Pin / freeze store for non-PairIP A/B tests |
| `adb reverse` | DBG ingest | `adb reverse tcp:7460 tcp:7460` when using `AgentDebugLog` |

---

## What PairIP looks like on 185656

Confirmed on-device APK inventory:

- Java: `com.pairip.VMRunner`, `StartupLauncher`, `SignatureCheck`, `LicenseClient`, …
- Native: `lib/arm64-v8a/libpairipcore.so` (~577 KiB) in `split_config.arm64_v8a.apk`
- Also: `libsqliteJni.so` (~1.3 MiB) — **final crash site under stub**
- Opaque asset (VM blob candidate): `assets/tuE0pIDMxkU399iZ` (~256 KiB) in `base.apk`
- Helper: `python scripts/pairip_inventory.py <apk|extracted-dir>`

Stock (no Vector) launches; Alloy scoped + Vector injects → PairIP path diverges immediately.

---

## Experiment matrix

Legend: **PASS** partial progress · **FAIL** blocked · **CONFOUND** not PairIP logic

### Baseline

| ID | Setup | Result | Notes |
|----|--------|--------|-------|
| B0 | Stock Grindr 26.19, Alloy disabled | PASS | App runs; PairIP present |
| B1 | Alloy + Vector, no PairIP bypass | FAIL | Native PairIP crash (SIGSEGV / early death) with module injected |
| B2 | Alloy on **26.16.1 / 179451** (no PairIP) | PASS | Hooks/pack E2E for StatusDialog + hidden details; proves Alloy/Vector stack OK |

### Runtime bypass ladder (`PairIpEarlyBypass`)

| Ver | Hypothesis | Technique | Result | Evidence / next |
|-----|------------|-----------|--------|-----------------|
| v1–v5 | Block / stub PairIP Java early | Hook `VMRunner` / block `pairipcore` load; special-call trampolines | FAIL | NPE / incomplete launch; RecordTag / HomeActivity Method-null (queue notes) |
| v6 | Null `Method` trampolines after VM stub | Wire synthetic `onCreate$00x` / `onResume$00x` | PASS (partial) | `wired=5`, pack `hasPack=true` (`debug-9ee712` H30) |
| v7 | String bags null after stub | Observe Firebase Perf / `StringBuilder(null)` | FAIL (expected) | Confirmed StartupLauncher VM also fills strings |
| v8 | Seed critical string bags | Room DB name seed + inbox keys; blank-fill log prefixes; Room `xi4.B` fallback | PASS (partial) | Past Hilt/Room seed (`public_grindr_assignments.db`) |
| v9 | Maybe real `pairipcore` works under Vector | Allow real native | FAIL | **SIGILL `ILL_ILLOPC`** under Vector (anti-tamper) |
| v10 | Intercept `System.loadLibrary` to swap stub | Hook loadLibrary → stub path | FAIL | Breaks split ClassLoader ns: `libsqliteJni` loads via APEX `com_android_art` instead of apk `clns-7` |
| v11 | Redirect only `pairipcore` via `BaseDexClassLoader.findLibrary` | Magisk-pushed stub SO + Java stubs + seeds + null guards | PASS then FAIL | Stub `JNI_OnLoad` OK · sqlite ns **ok** · `init_ok` · then **SIGILL in `libsqliteJni!JNI_OnLoad+12`** |
| v11b | LMK confound | Add swap, free RAM, retest v11 | CONFOUND cleared | Still SIGILL; not LMK. Tombstone `tombstone_20` |

### Final crash (v11) — ground truth

```
signal 4 (SIGILL), code 1 (ILL_ILLOPC)
#00 libsqliteJni.so (JNI_OnLoad+12)   [split_config.arm64_v8a.apk]
#07 wi5.<clinit> → System.loadLibrary
```

- PC bytes look **encrypted / non-instruction** (not a normal JNI_OnLoad prologue).
- Stub `gpp_libpairipcore_stub.so` is mapped; crash is **not** inside the stub.
- Earlier hypothesis “encrypted Java method bodies need `executeVM`” was **too narrow**: at least some **native** `.text` (sqlite JNI) also depends on the real PairIP integrity/decrypt path.

Full dump: [`tombstone_20_sigill.txt`](tombstone_20_sigill.txt).

---

## What worked (keep)

1. **Alloy / Vector API 102 entry** loads on Grindr (`GppXposedModule`, META-INF/xposed).
2. **Non-PairIP host** is a valid Alloy lab green path.
3. **Pack 185656** soft-loads on PairIP host (`hasPack=true`) even when many hooks fail later.
4. **findLibrary redirect** (v11) is the only safe way found to swap `pairipcore` without breaking split native namespaces — still insufficient alone.
5. **Trampoline wiring + string seeds + null guards** get past early Java NPEs to `init_ok` — useful if a future static decrypt leaves residual nulls.
6. Lab ops: swap file, Magisk allow, autofill off, DBG reverse.

## What failed (do not retry without new theory)

1. Real `libpairipcore` under Vector (v9) — SIGILL/SIGSEGV.
2. `System.loadLibrary` hooks for stubbing (v10) — split ns breakage.
3. Stub-only `executeVM` / Java PairIP neutralization (v11) — SIGILL in protected native.
4. Treating silent death as PairIP-only without checking LMK (confound).
5. Assuming post-init SIGILL is Java VM bytecode only — tombstone falsifies that.

## Open questions for Morphe B / next owners

1. On **stock** (no Vector), does `libsqliteJni` `.text` at `JNI_OnLoad` match APK bytes or a decrypted in-memory image?
2. Which VM asset(s) PairIP reads for 185656 (`tuE0pIDMxkU399iZ` and others)?
3. Can offline tools (pairipcore-vm research, process dump after `JNI_OnLoad` of real core) produce rewrite recipes for dexlib2 / native?
4. Minimal decrypt set for login + Home vs full asset set?
5. Should Morphe B run PairIP decrypt **before** LSPatch embed (already B→A in ADR 0007)?

---

## Reproduction (tombstone)

```text
# prerequisites: Alloy installed, Vector scope Grindr, Magisk allow,
# stub at /data/local/tmp/gpp_libpairipcore_stub.so, swap optional but recommended

adb shell su -c '/data/adb/modules/zygisk_vector/cli modules enable com.gpp.alloy'
adb reverse tcp:7460 tcp:7460
adb logcat -c
adb shell am force-stop com.grindrapp.android
adb shell monkey -p com.grindrapp.android -c android.intent.category.LAUNCHER 1
# wait ~20–25s — expect init_ok then Fatal signal 4
adb shell su -c 'ls -lt /data/tombstones | head'
# pull newest tombstone_* (see docs/lab/pairip/tombstone_20_sigill.txt)
```

DBG tag used in session: `DBG9EE712` / `AgentDebugLog`.

---

## Pointers for future developers

| Goal | Start here |
|------|------------|
| Understand closure | [TOMBSTONE.md](TOMBSTONE.md), [ADR 0009](../../adr/0009-pairip-static-decrypt.md) |
| Replay crash | Tombstone + repro above |
| Inventory APK | `scripts/pairip_inventory.py` |
| Static rewrite scaffold | [ADR 0007](../../adr/0007-morphe-b-bytecodepatch.md), `app/src/morphe-b/` |
| Alloy without PairIP | Lab on pre-PairIP Grindr; do not claim Play 26.19 Alloy green |
| Public PairIP background | [pairipcore-vm intro](https://matrixeditor.github.io/pairipcore-vm/introduction.html), [Byteria writeup](https://blog.byterialab.com/reversing-googles-new-vm-based-integrity-protection-pairip/) |

When you learn something new, **append a dated section** below rather than rewriting history.

### Append-only updates

_(none yet)_
