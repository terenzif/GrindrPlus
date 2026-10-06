# Morphe B (post Ver.5 green)

Static + hybrid feature-parity path. Starts after Ver.5 green + device E2E.

See [adr/0004-morphe-b.md](adr/0004-morphe-b.md).

## Pipeline

```text
select APKs
    → MorpheBPatchEngine
         · FingerprintScanBytecodeBackend → assets/grindrplus/bytecode_scan.json
         · marker → assets/grindrplus/morphe_b.json
    → Morphe A LSPatchIntegratedBackend (-l 2 + slim embed)
    → SessionInstaller
```

`MorpheOrchestrator` runs B then A. B failures are non-fatal (A continues).

Bytecode today is a **dry-run fingerprint scan** (ADR 0007); full MorpheApp `bytecodePatch` rewriter is next.

## Delivery modes

| Mode | Meaning |
| --- | --- |
| `STATIC_RESOURCE` | Mutate APK before LSPatch (marker today; DEX rewriter later) |
| `RUNTIME_REMAP` | Remapped Xposed hooks for sites found on tip DEX |
| `DEFERRED` | No safe target — Settings stay skipped/honest |

## Candidates

| Feature | Mode | Notes |
| --- | --- | --- |
| Chat terminal | RUNTIME_REMAP | Needs `ChatTerminal.HANDLER` in pack |
| Video calls | RUNTIME_REMAP | `VideoCallHasNotChattedException` / fragment gates |
| Disable shuffle | DEFERRED | ShuffleUiState gone (Cascade V2) |
| Notification Alerts | DEFERRED | reminder fingerprint absent |
| Favorites | RUNTIME_REMAP | Soft-skip until `Favorites.FRAGMENT` remapped |
| Keep Alive WebSocket | RUNTIME_REMAP | `websocket.a` + `jcd` |
| Status Dialog | RUNTIME_REMAP | Material `TabLayout$TabView` |

## Package

`com.grindrplus.morphe.b` — `MorpheBCatalog`, `MorpheBPatchEngine`.
