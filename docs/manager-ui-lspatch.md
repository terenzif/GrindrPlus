# Manager UI — Install tab (Morphe)

Bottom nav (Morphe): **Install · Block Log · Home · News · Settings**. Alloy omits Install.

## Primary path (rootless)

1. User installs **stock Grindr** from Play (`com.grindrapp.android`).
2. GrindMod Install → **Create / Update Grindr++**.
3. Pipeline: export installed APK/splits → clone package `com.grindrapp.android.plus` (label **Grindr++**, brand icon) → LSPatch `-l 2` embed → session install.
4. Stock Play Grindr remains; Grindr++ is the modded clone. After a Play update, tap Update again (mapping packs apply).

Module DEX still comes from GitHub Releases (`manifest.json` mod URL). Grindr CDN URLs are not the happy path.

## Emergency fallback

Custom Files (local Grindr bundle zip + mod) remains available under Install advanced section for lab/offline use — not the documented primary UX.

## News tab

Wiki CTA + GitHub Releases — see [news.md](news.md).
