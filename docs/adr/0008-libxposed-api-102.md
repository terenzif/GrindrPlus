# ADR 0008: libxposed / Vector API 102

- **Status:** Accepted
- **Date:** 2026-10-07
- **Related:** [0005-dual-apk-morphe-alloy.md](0005-dual-apk-morphe-alloy.md), [0002-settings-hook-truthfulness.md](0002-settings-hook-truthfulness.md), [0006-alloy-dexkit-fingerprints.md](0006-alloy-dexkit-fingerprints.md)

## Context

Manifests and docs briefly claimed Vector **API 103**. That level does not exist; [libxposed/api](https://github.com/libxposed/api) tops out at **102**, shipped in [Vector 2.2](https://github.com/JingMatrix/Vector/releases/tag/v2.2). Alloy/embed still used the legacy `de.robv.android.xposed` entry and hook engine, so a numeric bump alone would be cosmetic.

GrindMod Settings already has **Modding active** via root + Vector CLI ([`VectorModToggle`](../../app/src/main/java/com/gpp/manager/vector/VectorModToggle.kt)). That enable/disable path must stay distinct from API 102 **code** hot-reload.

## Decision

1. Alloy (`com.gpp.alloy`) and embed (`com.gpp.morphe.payload`) target **libxposed API 102** (`minApiVersion` / `targetApiVersion` = 102) with a single Java entry `com.gpp.GppXposedModule`.
2. Registration uses `META-INF/xposed/{module.prop,java_init.list,scope.list}`; legacy `assets/xposed_init` and `xposedminversion` meta are removed.
3. Hook façade (`Hooker` / `HookAdapter`) sits on `XposedInterface`; feature hooks keep `Class.hook` / `HookStage`.
4. Alloy Manager binds `libxposed/service` for framework caps, remote prefs write, running targets, and code hot-reload requests — **not** for Modding active (CLI + force-stop remains).
5. Bridge/Config stay the shared Settings path for Morphe/embed and Alloy fallback. Remote prefs are first-class on Alloy when Vector exposes them.
6. Optional mapping-pack field `invoke` (`special` | `direct` | `hooked`) is documented for future fragile call sites; façade `GppXposed.invoke` lands now. Parser may wait until the first pack needs it.
7. Morphe Manager (`com.gpp.morphe`) remains non-module.

## Consequences

- Runtime requirement for Alloy: Vector ≥ 2.2.
- Hot-reload accepts only when module-owned work can drain safely; rejection is soft-fail.
- LSPatch pin must load modern modules for Create Grindr++ embed path.
- App `compileSdk` is **37** (libxposed service/interface 102.0.0 AAR metadata); `targetSdk` stays 34. Lab SDK may need `platforms;android-37` (or a junction from `android-37.0`).
