# PairIP lab wiki — Grindr 26.19 + Alloy/Vector

Canonical write-up of the Alloy PairIP campaign (successes, failures, confounds) so the work is reusable.

## Read order

1. **[TOMBSTONE.md](TOMBSTONE.md)** — path closed; reopen rules  
2. **[EXPERIMENT_LOG.md](EXPERIMENT_LOG.md)** — full matrix v1–v11, lab hygiene, repro  
3. **[tombstone_20_sigill_summary.md](tombstone_20_sigill_summary.md)** — condensed crash  
4. **[tombstone_20_sigill.txt](tombstone_20_sigill.txt)** — raw Android tombstone  
5. **[ADR 0009](../../adr/0009-pairip-static-decrypt.md)** — product direction (Morphe B)  
6. **[ADR 0007](../../adr/0007-morphe-b-bytecodepatch.md)** — bytecode rewrite scaffold  

## One-paragraph verdict

On Play **26.19.0 / 185656**, Vector injection breaks real PairIP; stubbing PairIP reaches `init_ok` then **SIGILL inside `libsqliteJni!JNI_OnLoad`** (encrypted native). Alloy runtime bypass is **tombstoned**. Use non-PairIP hosts for Alloy green; use **Morphe B static decrypt** for PairIP Play.

## Quick links

| Item | Path |
|------|------|
| Bypass code (diagnostic) | `app/src/module/java/com/gpp/hooks/PairIpEarlyBypass.kt` |
| Stub SO | `native/pairip-stub/` |
| Inventory script | `scripts/pairip_inventory.py` |
| DBG session lines | [debug-9ee712.log](debug-9ee712.log) |
| Control success (no PairIP) | Experiment log § B2 — Grindr 26.16.1 / 179451 |
