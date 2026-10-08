## Learned User Preferences

- Product name is **Grindr++** (repo/folder `grindr-plus-plus`). Tagline on 16:9 art: take them all. Manager UI name is **GrindMod** (mask icon + gear badge). GPP remains an internal acronym; `com.gpp.*` applicationIds stay.
- Recycle Grace Barbarica cinematic dark/gold style, but never barbarians or GB lettering; wide graphics are 16:9. Brand phoenix: only `ref-phoenix-head` (not full `ref-phoenix-stencil`); flat solid gold-yellow with fake relief; Grindr-style mask face with stylized mouth (grind grin + solemn rebirth).
- Do not treat README or personal/lab-use wording as architectural constraints; do not invent product constraints the user never decided; treat user corrections as authoritative.
- Ver.5 must stay version-agnostic: no hardcoded single Grindr version as destiny.
- Dual delivery: `com.gpp.morphe` (rootless GrindMod Manager — never a Vector module) and `com.gpp.alloy` (sole rooted Vector registrant). Slim LSPatch `-m` embed is build-only. Do not give primary Manager continuity to the rooted channel.
- Rootless Install: **only** from Play-installed `com.grindrapp.android` → clone `com.grindrapp.android.plus` labeled Grindr++ (stock stays). No CDN Grindr happy path.
- Alloy: no Grindr++ drawer clone; Settings “Modding active” via root + Vector CLI + force-stop Grindr (hot reload ≠ enable/disable). Shortcut launcher not required.
- Delivery sequence: Morphe A complete in Ver.5 → Ver.5 green → then Morphe B.
- Settings toggles must match real runtime behavior; E2E verify — no cosmetic toggles.
- Prefer finding and resolving design problems before writing code; multi-agent multi-step plans are welcome when scoped.
- The Manager/module APK is not Grindr; Manager is GrindMod, not a second stock/modded Grindr icon by itself.

## Learned Workspace Facts

- Official Ver.5 vision and sequencing live in `docs/vision.md` (and ADRs under `docs/adr/`).
- Morphe A is orchestrator-rootless: export installed host → clone Grindr++ → LSPatch embed. Morphe B is static feature parity after Ver.5 “green” (`docs/adr/0007-morphe-b-bytecodepatch.md`).
- Dual APK IDs locked in `docs/adr/0005-dual-apk-morphe-alloy.md`; brand masters in `docs/brand/grindr-plus-plus/` (allowlist only).
- Rooted framework is **Vector** ([JingMatrix/Vector](https://github.com/JingMatrix/Vector)); legacy `isLsPosed` identifiers kept. Alloy/embed target **libxposed / Vector API 102** (ADR 0008). Modding active = Vector CLI; hot-reload = code swap only.
- Mapping is pack-driven by Grindr `versionCode`; soft-fail on missing packs.
- Artifact naming uses **gpp** and must not embed the Grindr host version in `versionName`.
- Settings↔runtime truthfulness is a Ver.5 pillar; anonymous opt-in telemetry via Manager analytics.
- Lab Android SDK/Studio under `C:\devbin`; unit tests need JDK 17/21 (lab JBR); JDK 25 breaks Robolectric.
- Legacy name **GrindrMod** (XDA) is unrelated; About should disclaim. Not Modr++.
