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

Patching is owned by Morphe A (`PatchBackend` / `LSPatchIntegratedBackend` / `MorpheOrchestrator`); see [morphe-a.md](morphe-a.md) and [ADR 0003](adr/0003-morphe-a-patch-backend.md).

## Pinning LSPatch / Vector artifacts

Morphe A requires **pinned** LSPatch/Vector artifacts (checksum or explicit revision). **No blind nightlies** in CI or on-device patch flows.

| Item | Today | Target |
| --- | --- | --- |
| Dev helper | `scripts/setup_lspatch.gradle.kts` resolves JingMatrix LSPatch via unpinned `nightly.link` | Prefer a **release-tag** ZIP URL + checksum; leave the nightly path only as an explicit local override |
| Shipped binary | `app/libs/lspatch.jar` (+ extracted `assets/lspatch/so*`) checked in or produced by the helper | Record the exact revision/tag used when refreshing the jar |

Until the Gradle helper is switched to a release URL, treat every `setupLSPatch` run as a supply-chain event: verify the jar before committing, and document the pin in [ADR 0003](adr/0003-morphe-a-patch-backend.md).

## News tab

Wiki CTA + GitHub Releases list — see [news.md](news.md). No Telegram feed in the UI.
