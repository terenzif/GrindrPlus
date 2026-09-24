# Manager UI — LSPatch tab & remote mappings

Bottom nav (this fork): **LSPatch · Block Log · Home · News · Settings**.

## What was `airdns`?

Upstream GrindrPlus hosted Grindr + mod APKs on **`gplusapks.airdns.org`** for one-click HTTP downloads. This fork does **not** host Grindr APKs.

## Grindr download engine (in-app)

LSPatch install uses **Aurora OSS `gplayapi`** for Play delivery when the manifest Grindr URL is blank:

1. Anonymous auth via token dispenser (`https://auroraoss.com/api/auth`)
2. App details + purchase/delivery for `com.grindrapp.android`
3. Download split APKs from Play CDN → zip → `ExtractBundleStep`

**Cloudflare 403/429:** the public dispenser often blocks non–Aurora-Store clients or rate-limits retries. The manager rotates store-like User-Agents; if auth still fails, use **Custom Files** (local Grindr APK) — do not hammer Install.

**Delivery status 3** (`App not purchased / unavailable in your country`): dating / age-gated apps often refuse **anonymous** dispenser accounts (Aurora Store then needs a personal Google login — we don’t collect that). The manager still mirrors Aurora’s purchase path (`acquire` → purchase → delivery), US Pixel spoof locale, offerType / cert-hash variants, and **rotates anonymous sessions** (up to 4). If Play still fails and an **unpatched** Grindr is already installed, it **exports those APKs** automatically (same outcome as Custom Files). Already-LSPatched installs are refused (nested patch). Otherwise use Custom Files.

The Aurora maintainer asks third-party apps not to treat their dispenser as a permanent CDN; Custom Files remains the reliable path.

Implemented in `PlayHttpClient`, `PlayStoreSession`, `PlayPackageCerts`, `PlayGrindrDownloadStep`.

Module APK still comes from GitHub Releases (`manifest.json` mod URL). Mapping packs stay remote/bundled (`MappingDictionary.loadForVersion`).

## Does LSPatch still work?

Yes: Play (or Custom Files) → extract → patch → install. Remote packs apply after first online Grindr start without re-patching.

## News tab

Wiki CTA + `news.json` announcements — see [news.md](news.md). Home stays on GitHub Releases.
