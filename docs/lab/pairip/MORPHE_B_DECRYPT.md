# Morphe B PairIP static decrypt (phase 1)

## What landed

| Piece | Role |
|-------|------|
| `PairIpStaticDecrypt` | Detect → splice natives → stub integrity DEX |
| `scripts/pairip_dump_natives.py` | Dump stock RX → splice into `.so` pack |
| `pairip-decrypt/185656/` | Lab pack for Play 26.19.0 / 185656 (`libsqliteJni.so`) |

Wired into `MorpheBPatchEngine.apply` **before** feature bytecode (ADR 0007/0009).

## Produce / refresh a pack

```text
# Vector off so PairIP can decrypt
adb shell su -c '/data/adb/modules/zygisk_vector/cli modules disable com.gpp.alloy'
adb shell am force-stop com.grindrapp.android
adb shell monkey -p com.grindrapp.android -c android.intent.category.LAUNCHER 1
# wait until Login/Home; lib must be mapped

python scripts/pairip_dump_natives.py --version-code 185656 \
  --out pairip-decrypt/185656 \
  --apk-so .tmp/pairip-185656/libsqliteJni.so

adb shell su -c '/data/adb/modules/zygisk_vector/cli modules enable com.gpp.alloy'
```

Extract encrypted `.so` from the arm64 split first if needed (see experiment log).

## Install path

Morphe Orchestrator → Morphe B (PairIP + bytecode) → LSPatch embed.  
Stage `pairip-decrypt/<versionCode>/` next to the exported APKs (or keep under repo root so resolve walks find it).

## Not done yet

- Auto-dump on device during Install
- Additional encrypted libs beyond `libsqliteJni`
- Full offline VM → Java rewrite (strings / virtualized methods)
- E2E proof: Grindr++ clone boots on 185656 with pack applied
