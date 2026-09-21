---
name: code-review
description: >-
  GrindrPlus (terenzif fork) code-review standards for GitHub Copilot pull request
  reviews. Use for every PR review on this repository — LSPosed/Xposed Kotlin
  module, MappingDictionary packs, soft-fail hooks, version gates, and security
  constraints. Prefer medium+ findings that affect init abort, ClassNotFound
  cascades, or mapping pack schema consistency.
---

# GrindrPlus code review (Copilot)

You are reviewing **terenzif/GrindrPlus**: an **LSPosed / Xposed** module (Kotlin) that hooks the Grindr Android app. Public docs/README are English; the maintainer may discuss in Italian — keep review comments in **English**.

## What to prioritize (medium+)

Flag findings that can:

1. **Abort module init** or leave hooks uninitialized when a single symbol is wrong.
2. Cause **ClassNotFoundException / NoSuchMethodError cascades** from `findClass` / hooks on empty or wrong names.
3. Break **mapping pack schema consistency** (`schemaVersion`, `versionCode`, symbol keys, hook statuses).
4. Bypass **soft-fail** contracts (turning skippable misses into hard crashes).
5. Touch **security-sensitive** areas (device unlock, secrets, credentials) — see Security below.

Deprioritize style nits, rename bikesheds, and “support every Grindr version in one APK” demands.

## Architecture facts (do not fight these)

### Mapping packs

- Bundled packs live under `app/src/main/assets/mappings/<versionCode>.json`; remote copies under repo `mapping-packs/`.
- Runtime loader: `com.grindrplus.core.mapping.MappingDictionary`.
- Preferred init path: **`loadForVersion(modulePath, versionCode, cacheDir)`** — order is **remote → device cache → module APK assets → literals**. Soft-fails on network/parse/I/O (log + continue). `loadFromModuleApk` remains the assets-only step inside that chain.
- Do **not** require `GrindrPlus.context.assets` for packs — that `Context` is Grindr’s, not the module’s. `load(context, …)` is for manager app / tests only.
- `Obfuscation` / core / Retrofit resolve through `MappingDictionary.resolve(key, fallback)` with compile-time literal fallbacks.
- Pack contract: empty symbol `name` (`""`) means **explicit soft-skip** (same idea as empty Obfuscation strings). Callers must check `isNotEmpty()` / skip **before** `findClass`.

### Soft-fail > hard crash

- `HookManager` wraps each `hook.init()` in `try/catch (Throwable)` — one hook failure must not kill the rest.
- Prefer soft-skip + log over throwing when a mapping is absent, empty, or DEX-missing (document with `hooks.*.status` / `reason` or a code comment).
- **DEX-absent soft-skips are OK** when intentional (feature gone from that Grindr build, R8-stripped class, scaffolding pack). Do not demand a remapped class for every historical symbol.

### Version gate

- Each module build targets **one** Grindr `versionName` / `versionCode` (see `supported_target.json`, `version.json`; Play tip in `latest_play.json`).
- Do **not** require one APK to support every Grindr release. Multi-version support is via **packs + rebuilds**, not a universal binary.
- Version mismatch / unsupported client → degraded mode or gate dialog is expected; do not treat “other versions fail” as a defect by itself.

### Obfuscation / R8

- Short names (`us1`, `ak0`, …) remapped per build; packs hold the truth for a given `versionCode`.
- Fingerprints / notes in packs help humans; they are not a substitute for runtime soft-fail.

## Review checklist

When reviewing Kotlin hooks, mapping JSON, or init paths:

- [ ] Empty mapping names gated **before** `findClass` / `hook` / constructor lookup.
- [ ] New `findClass` sites tolerate missing classes (soft-skip or HookManager isolation) unless the symbol is truly required for boot.
- [ ] Pack `versionCode` matches filename and embedded field; `schemaVersion` compatible with loader.
- [ ] Pack keys stay stable (`core.userAgent`, `BanManagement.bannedArgs`, …); only `name` / method fields change across versions.
- [ ] Hook `status` values (`mapped` / `partial` / `skipped` / `unverified`) match reality; skipped symbols have a short `reason`.
- [ ] Init uses `loadForVersion` (remote→cache→assets→literals) or at least `loadFromModuleApk` for in-process Xposed; no regression to Grindr `context.assets` for module packs.
- [ ] No suggestion to remove device lock, commit secrets, or check in PIN / credential files.
- [ ] README/docs changes stay English and accurate vs soft-fail / single-target versioning.

## Security (hard rules)

**Never** suggest or approve:

- Removing or weakening **device lock / PIN / screen lock** for lab convenience in committed code or docs that publish secrets.
- Committing **secrets**, API keys, keystores, `local.properties` secrets, or **PIN files**.
- Instructions that print or persist the device PIN in the repository.

Lab unlock docs may exist privately for the maintainer; reviews must not expand them into public secret material.

## Comment style

- Be specific: file, symbol key, failure mode (init abort vs single-hook skip).
- Prefer actionable fixes aligned with soft-fail and packs.
- Skip low-value comments that ignore HookManager isolation or the single-target version gate.
- Resolve outdated threads when the head branch already implements the fix (e.g. `loadFromModuleApk`, ProfileBarView long-press port).
