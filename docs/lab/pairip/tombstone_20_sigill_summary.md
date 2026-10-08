# Tombstone summary — SIGILL post Alloy init

Source: [`tombstone_20_sigill.txt`](tombstone_20_sigill.txt)  
Device: Mi A2 lite / Evolution X · Timestamp: 2026-10-08 13:42:57 +0200  
Process: `com.grindrapp.android` pid **18330**, tid **20333** (`DefaultDispatch`) · uptime 24s

```
signal 4 (SIGILL), code 1 (ILL_ILLOPC), fault addr 0x0000007ab70527c0
pc  0000007ab70527c0  lr  0000007bc04e9a28
```

## Top of backtrace

```
#00 libsqliteJni.so (JNI_OnLoad+12)   [split_config.arm64_v8a.apk]
#01 JVM_NativeLoad
#05 System.loadLibrary
#07 wi5.<clinit>+160                 [base.apk]
#14 xi5.open+6
#20 iph.open+38
… coroutine / collect chain …
```

## Memory at PC (encrypted / non-instruction)

```
0000007ab70527c0 8999fd3efe357ef5 eee4a4c1d36b0dd2
```

Fault maps into `split_config.arm64_v8a.apk` r-x segment (lib load), not into `gpp_libpairipcore_stub.so` (also mapped elsewhere).

## Preceding GPP logs (same launch)

- `install_done_v11` stubs=6 wired=5 stringFieldsFilled=61
- `redirect_pairipcore_stub` + `gpp_pairip_stub: JNI_OnLoad stub`
- `init_ok` (~6.2s)
- nativeloader: `libsqliteJni.so` … `ok`
- then Fatal signal 4
