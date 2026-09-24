# Manager UI — LSPatch tab & remote mappings

Bottom nav (this fork): **LSPatch · Block Log · Home · News · Settings**.

## What was `airdns`?

Upstream GrindrPlus hosted Grindr + mod APKs on **`gplusapks.airdns.org`** for one-click HTTP downloads. This fork does **not** host Grindr APKs.

## Grindr download engine (in-app)

LSPatch install uses **Aurora OSS `gplayapi`** for Play delivery when the manifest Grindr URL is blank:

1. Anonymous auth via token dispenser (`https://auroraoss.com/api/auth`)
2. App details + purchase/delivery for `com.grindrapp.android`
3. Download split APKs from Play CDN → zip → `ExtractBundleStep`

**Cloudflare 403/429:** the public dispenser often blocks non–Aurora-Store clients or rate-limits retries. The manager rotates store-like User-Agents; if auth still fails, use **Custom Files** (local Grindr APK) — do not hammer Install. The Aurora maintainer asks third-party apps not to treat their dispenser as a permanent CDN; Custom Files remains the reliable path.

Implemented in `PlayHttpClient`, `PlayStoreSession`, `PlayGrindrDownloadStep`.

Module APK still comes from GitHub Releases (`manifest.json` mod URL). Mapping packs stay remote/bundled (`MappingDictionary.loadForVersion`).

## Does LSPatch still work?

Yes: Play (or Custom Files) → extract → patch → install. Remote packs apply after first online Grindr start without re-patching.

## News tab

Wiki CTA + GitHub Releases list — see [news.md](news.md). No Telegram feed in the UI.
