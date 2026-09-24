# Manager UI — LSPatch tab & remote mappings

Bottom nav (this fork): **LSPatch · Block Log · Home · News · Settings**.

## What was `airdns`?

Upstream GrindrPlus hosted Grindr + mod APKs on **`gplusapks.airdns.org`** for one-click HTTP downloads. This fork does **not** host Grindr APKs.

## Grindr download engine (in-app)

LSPatch install uses **Aurora OSS `gplayapi`** for Play delivery when the manifest Grindr URL is blank:

1. Anonymous auth via token dispenser (`https://auroraoss.com/api/auth`)
2. App details + purchase/delivery for `com.grindrapp.android` at **`TARGET_GRINDR_VERSION_CODES[0]`** (Aurora-style pin — not Play tip). If Play cannot deliver that code, or delivers a different `versionCode`, Install fails and points to **Custom Files** (no tip fallback). Cached `grindr-*.zip` is reused only when its embedded `versionCode` matches the pin. Installed-APK fallback only when the on-device versionCode matches the pin.
3. Download split APKs from Play CDN → zip → `ExtractBundleStep`

**Cloudflare 403/429:** the public dispenser often blocks non–Aurora-Store clients or rate-limits retries. The manager rotates store-like User-Agents; if auth still fails, use **Custom Files** (local Grindr APK) — do not hammer Install.

**Auth:** prefers an **on-device Google account**, else the anonymous dispenser:

1. `AccountManager.getAuthToken` for `oauth2:…/googleplay` with Play Store `overridePackage` + cert (Aurora / microG path)
2. **Stock GMS:** OAuth access token (`ya29…`) → **AC2DM** (`android.clients.google.com/auth`) → **AAS** master token (same exchange Aurora’s WebView Google login uses)
3. `AuthHelper` `Token.AAS` → Play AUTH for FDFE purchase/delivery

Dating / age-gated apps often refuse anonymous delivery (status 3); a real Google login is how Aurora Store makes those work. Personal sessions are **not** rotated into anonymous on purchase failure.

**Delivery status 3** (`App not purchased / unavailable in your country`): anonymous mode rotates up to 4 dispenser sessions + offerType / cert-hash variants. If Play still fails and an **unpatched** Grindr is already installed, it **exports those APKs** automatically. Already-LSPatched installs are refused (nested patch). Otherwise use Custom Files.

The first local-token request may show a **Google account picker** (Android 8+ account visibility) and/or a **permission** prompt — select the Play Store account, approve, then tap Install again if needed. AAS tokens are cached in app prefs after a successful AC2DM mint.

The Aurora maintainer asks third-party apps not to treat their dispenser as a permanent CDN; Custom Files remains the reliable path.

Implemented in `PlayHttpClient`, `PlayStoreSession`, `PlayLocalAccountAuth`, `PlayAc2dm`, `PlayPackageCerts`, `PlayGrindrDownloadStep`.

Module APK still comes from GitHub Releases (`manifest.json` mod URL). Mapping packs stay remote/bundled (`MappingDictionary.loadForVersion`).

## Does LSPatch still work?

Yes: Play (or Custom Files) → extract → patch → install. Remote packs apply after first online Grindr start without re-patching.

## News tab

Wiki CTA + `news.json` announcements — see [news.md](news.md). Home stays on GitHub Releases.
