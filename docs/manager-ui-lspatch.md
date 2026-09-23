# Manager UI — LSPatch tab & remote mappings

Bottom nav (this fork): **LSPatch · Block Log · Home · News · Settings**.

## What was `airdns`?

Upstream GrindrPlus hosted Grindr + mod APKs on **`gplusapks.airdns.org`** for one-click HTTP downloads. This fork does **not** host Grindr APKs.

## Grindr download engine (in-app)

LSPatch install uses **Aurora OSS `gplayapi`** — the same Play Store protocol Aurora Store uses:

1. Anonymous auth via Aurora dispenser (`https://auroraoss.com/api/auth`)
2. App details + purchase/delivery for `com.grindrapp.android`
3. Download split APKs from Play CDN, zip → existing `ExtractBundleStep` / LSPatch pipeline

Implemented in:

- `manager/play/PlayHttpClient.kt` — real `postAuth` (library default stubs it)
- `manager/play/PlayStoreSession.kt` — anonymous session
- `manager/installation/steps/PlayGrindrDownloadStep.kt` — used when `manifest.json` Grindr URL is blank

**Not** launching the Aurora Store app. Custom Files remains an offline fallback.

Module APK still comes from GitHub Releases (`manifest.json` mod URL). Mapping packs stay remote/bundled (`MappingDictionary.loadForVersion`).

## Does LSPatch still work?

Yes: Play (or Custom Files) → extract → patch → install. Remote packs apply after first online Grindr start without re-patching.

## News tab

Wiki CTA + GitHub Releases list — see [news.md](news.md). No Telegram feed in the UI.
