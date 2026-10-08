# 🪦 TOMBSTONE — Alloy runtime PairIP bypass

```
                    _________________________
                   /                         \
                  |  PairIP Alloy runtime     |
                  |  neutralization           |
                  |  2026-10-08               |
                  |___________________________|
                           |     |
                           |     |
                      RIP  |     |  DO NOT DIG
                           |_____|
```

## Buried here

Attempts **v1–v11** to make **Grindr Play 26.19 / 185656** survive **Vector + `com.gpp.alloy`** by stubbing, blocking, or redirecting PairIP at runtime.

## Cause of death

After the best-effort stub path (`findLibrary` → stub `libpairipcore`, Java VM stubs, string seeds, trampoline wiring):

1. `GrindrPlus.init` reports **`init_ok`**
2. `libsqliteJni` loads from the split APK namespace
3. Process dies: **`SIGILL` / `ILL_ILLOPC` at `libsqliteJni.so!JNI_OnLoad+12`**
4. Instruction bytes at PC are garbage/encrypted — not a fixable Java NPE

Real `libpairipcore` under Vector also dies (SIGILL/SIGSEGV). Hooking `System.loadLibrary` breaks split native library namespaces.

## Epitaph

> Runtime PairIP bypass under Vector cannot restore integrity-decrypted native code.  
> Further `pairip-vN` Alloy loops waste tokens and lab time.  
> Continue under **Morphe B static decrypt** (ADR 0009).

## Estate (what survivors keep)

- Full experiment log: [EXPERIMENT_LOG.md](EXPERIMENT_LOG.md)
- Tombstone dump: [tombstone_20_sigill.txt](tombstone_20_sigill.txt)
- Soft-fail detector / diagnostic hooks: `PairIpEarlyBypass.kt` (not a product fix)
- Stub SO sources: `native/pairip-stub/`
- Inventory helper: `scripts/pairip_inventory.py`

## Reopen conditions

Only reopen an Alloy *runtime* PairIP track if **new evidence** shows:

- Vector can host real `pairipcore` without SIGILL, **or**
- Protected natives (e.g. `libsqliteJni`) are shown plaintext in the APK without PairIP decrypt,

…and that evidence is appended to the experiment log with a new dated section.
