# Morphe A — rootless patch orchestrator

Morphe A is the Ver.5 delivery pillar for **orchestrator-rootless** installs: patch Grindr APKs and embed the GrindrPlus module via Vector/LSPatch. It does **not** rewrite product hooks or features — that is **Morphe B**, after Ver.5 green. See [vision.md](vision.md) and [ADR 0003](adr/0003-morphe-a-patch-backend.md).

Ships in the **`morphe`** product flavor (`com.grindrplus.morphe`). The **`alloy`** flavor is rooted Vector-only and does not package LSPatch. See [ADR 0005](adr/0005-dual-apk-morphe-alloy.md).

Prefer a **slim embed payload** as `-m` (hooks + bridge + Xposed entry), not the full Manager APK, so Grindr’s process stays lighter.

## Components

| Type | Class | Role |
| --- | --- | --- |
| Seam | `PatchBackend` | Suspend API: input APKs + mod + keystore → patched output |
| Default backend | `LSPatchIntegratedBackend` | Integrated LSPatch (`-l 2`), Maps API key rewrite, optional embed-off copy |
| LSPatch pin | JingMatrix **v0.8** | `scripts/setup_lspatch.gradle.kts` + `app/libs/lspatch.jar` (upstream SHA-256 in `app/libs/lspatch-v0.8.sha256.txt`). Host CLI dry-run uses unstripped `lspatch-v0.8.jar`; Android `lspatch.jar` strips conflicting Guava/errorprone classes. |
| Orchestrator | `MorpheOrchestrator` | Select APKs → `PatchBackend.patch` → output ready for `SessionInstaller` |
| Install step | `PatchApkStep` | Thin `Installation` step; delegates via `MorpheOrchestrator` (injectable `PatchBackend`) |

Package: `com.grindrplus.manager.installation`.

## Rootless flow

```text
extract / select APKs
        ↓
MorpheOrchestrator.patchForInstall(...)   // or PatchApkStep
        ↓
PatchBackend.patch(...)                   // default: LSPatchIntegratedBackend
        ↓
outputDir (patched splits)
        ↓
InstallApkStep / SessionInstaller
```

`InstallScreen` → `Installation` is unchanged: still `ExtractBundleStep` → `PatchApkStep` → `InstallApkStep`. Morphe A only extracts the patch body behind a testable seam.

## Vector / LSPatch vs Morphe B

- **Vector / LSPatch** remain the rootless embed mechanism (`-l 2` integrated). GrindrPlus is the module payload (`-m`).
- **Morphe A** owns orchestration and the `PatchBackend` boundary; keystore and session install stay in the manager.
- **Morphe B** (later) may replace or complement how features are delivered; it must not be required for Ver.5 green rootless install.

## Artifact pinning

Pinned to JingMatrix/LSPatch **v0.8** (GitHub Release + SHA-256) via `scripts/setup_lspatch.gradle.kts`. Details: [ADR 0003](adr/0003-morphe-a-patch-backend.md), `app/libs/lspatch-v0.8.sha256.txt`.
