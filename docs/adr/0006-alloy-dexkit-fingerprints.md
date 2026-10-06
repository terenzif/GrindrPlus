# ADR 0006: Alloy — NexAlloy-style DexKit fingerprints (rooted)

- **Status:** Accepted (implementation started — AlloyDexKit + Favorites pilot)
- **Date:** 2026-10-05
- **Related:** [0005-dual-apk-morphe-alloy.md](0005-dual-apk-morphe-alloy.md), [vision.md](../vision.md)

## Context

The `alloy` APK (`com.gpp.alloy`) is the rooted Vector channel. Residual tip-DEX sites are fragile under R8 churn. [NexAlloy](https://github.com/NexAlloy/NexAlloy) maps Morphe/ReVanced-style fingerprints to DexKit + Xposed hooks at runtime.

## Decision

1. Depend on `org.luckypray:dexkit` for **alloy** (`alloyImplementation`); other flavors `compileOnly` so shared hooks compile.
2. Façade: `com.gpp.alloy.AlloyDexKit` (`ensureInitialized`, `findClassByStrings`).
3. Pilot: [Favorites.kt](../../app/src/main/java/com/grindrplus/hooks/Favorites.kt) falls back to DexKit string search when pack remap class is missing (Alloy only).
4. Init: `GrindrPlus.initializeCore` calls `AlloyDexKit.ensureInitialized` before `HookManager.init` on Alloy.

## Non-goals

- Replacing HookManager wholesale.
- Shipping DexKit as the primary discovery path on Morphe (Morphe B bytecode covers rootless static sites).

## Acceptance

- [x] Compile: alloy + morphe green with DexKit wiring.
- [x] Offline tip DEX (26.16.1): `FavoritesFragment` / `fragment_favorite_recycler_view` **miss**; `CascadeFavoritesItemUiModel` / `FavoritesHeaderData` **hit** — Favorites DexKit needles updated accordingly.
- [ ] Device (lab): tip Grindr recovers Favorites when pack class absent but string fingerprint hits — **blocked** by Grindr launch lab debt (disguised aliases + missing Play/config splits; stock reinstall needs real `config.armeabi_v7a` / density APKs).
