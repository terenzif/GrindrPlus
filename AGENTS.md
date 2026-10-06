## Learned User Preferences

- Product name is Grindr++ (repo/folder `grindr-plus-plus`). Tagline on 16:9 art: take them all. GPP remains an internal acronym.
- Android Java/Kotlin package and applicationId use `com.gpp` (`com.gpp.morphe` / `com.gpp.alloy` / `com.gpp.morphe.payload`).
- Recycle Grace Barbarica cinematic dark/gold style, but never barbarians or GB lettering; product mark is GPP; wide graphics are 16:9.
- Brand phoenix art: use only `ref-phoenix-head`. Do not use the full `ref-phoenix-stencil` (reserved personal art) and do not invent a new phoenix body.
- Gold fill is flat solid gold-yellow with fake relief and overlapping flourishes, not metallic chrome.
- The phoenix face is a Grindr-style mask, not a rigid straight-on plate; add a stylized mouth that mixes a grind grin with solemn rebirth.
- Do not treat README or personal/lab-use wording as architectural or technology constraints.
- Do not invent product constraints the user never decided; treat user corrections as authoritative over agent assumptions.
- Ver.5 must stay version-agnostic: no hardcoded single Grindr version as destiny.
- Prefer dual delivery via two APKs (same UI name Grindr++): `com.gpp.morphe` (rootless Morphe A/B + LSPatch) and `com.gpp.alloy` (rooted Vector; NexAlloy-style DexKit later). Slim LSPatch `-m` embed is build-only, not a third user product.
- Do not give product/update continuity or the primary Manager UX to the rooted channel; rootless users rely on Manager UI, while rooted users typically use Vector.
- Delivery sequence is fixed: Morphe A complete in Ver.5 → Ver.5 green → then Morphe B.
- Prefer integrating MorpheApp `bytecodePatch` / patches-template as the Morphe B DEX rewriter inside the orchestrator, not as a separate Morphe Manager product.
- Settings checkboxes/toggles must match real runtime behavior; verify hooks E2E — no cosmetic toggles.
- Schema evolution/auto-migration, hot-reload, DI, circuit breaker, and anonymous-respecting telemetry are in scope.
- Prefer finding and resolving design problems before writing code; multi-agent multi-step plans are welcome when scoped.

## Learned Workspace Facts

- Official Ver.5 vision and sequencing live in `docs/vision.md` (and related ADRs under `docs/adr/`).
- Morphe A is orchestrator-rootless delivery: patch APK and embed Vector/LSPatch; it is not a product-hook/feature rewrite.
- Morphe B is static feature parity work and starts only after Ver.5 acceptance (“green”); residual tip-DEX gaps need a real DEX rewriter, not marker-only static resources (`docs/adr/0007-morphe-b-bytecodepatch.md`).
- Repo “Morphe A/B” naming is Ver.5 product pillars and is distinct from upstream MorpheApp.
- NexAlloy (ex ReVancedXposed) is the closest rooted reference for Morphe-style fingerprints at Vector/Xposed runtime; Alloy DexKit integration is `docs/adr/0006-alloy-dexkit-fingerprints.md` and does not cover the rootless LSPatch channel.
- Dual APK IDs are locked in `docs/adr/0005-dual-apk-morphe-alloy.md` (`morphe` / `alloy`); legacy `com.grindrplus*` is migrated to `com.gpp*`; Morphe keeps Manager+Install, Alloy is Vector-scoped without the Install tab.
- Rooted framework product name is **Vector** ([JingMatrix/Vector](https://github.com/JingMatrix/Vector)); keep legacy `isLsPosed` API/log identifiers for module compatibility. Module `xposedminversion` targets Vector API **103**.
- Mapping is pack-driven by Grindr `versionCode` (`mapping-packs/` plus `app/src/main/assets/mappings/`). Add a new pack for a new version; do not freeze an old pack as destiny. Soft-fail: missing/invalid packs must not hard-abort init.
- Module/APK artifact naming uses **gpp** and must not embed the Grindr host version in `versionName` / archives.
- Settings↔runtime truthfulness is a Ver.5 acceptance pillar (`docs/adr/0002-settings-hook-truthfulness.md`, E2E runbook).
- Anonymous opt-in telemetry is allowed via the existing Manager analytics setting.
- Lab Android SDK/Studio live under `C:\devbin`; E2E device work uses that SDK’s adb.
- Local unit tests need Gradle on JDK 17/21 (lab: `C:\devbin\android-studio\jbr`); JDK 25 breaks Robolectric.
