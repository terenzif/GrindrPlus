# Manager UI — LSPatch tab & remote mappings

Bottom nav (this fork): **LSPatch · Block Log · Home · News · Settings**.

## What was `airdns`?

Upstream GrindrPlus (`R0rt1z2`) hosted Grindr + mod APKs on **`gplusapks.airdns.org`** so the manager could one-click download both from `manifest.json`. That was **their** CDN — not something this fork needs or replaces.

**We do not host Grindr APKs.** F.Ter already pulls Grindr via Aurora Store (or SAI). Module APKs come from **GitHub Releases**. Mapping packs are already **bundled + remote** (`mapping-packs/`). No ad-hoc APK hosting.

## Does LSPatch still work?

Yes, as a **local patch** flow: pick Grindr APK + module APK → LSPatch embeds → install. The pipeline (`InstallScreen` → `Installation` → `PatchApkStep`) is unchanged.

| Input | Source on this fork |
| --- | --- |
| Grindr APK | Aurora Store / SAI (local file — Custom Files) |
| Module APK | [Releases](https://github.com/terenzif/GrindrPlus/releases) (or Custom Files) |
| Mappings | Remote → cache → assets → literals (no LSPatch re-run needed for new packs) |

`manifest.json` lists the current tip version and the **mod** URL only (Grindr URL left empty on purpose). Install then routes you to Custom Files for the Grindr APK you already downloaded.

## Remote mapping packs + LSPatch

They compose: after patch/install, init still runs `MappingDictionary.loadForVersion`. Publishing `mapping-packs/<versionCode>.json` does not require re-LSPatching.

LSPosed remains preferred when available; LSPatch is the no-LSPosed path.

## How to use LSPatch here

1. Grindr **26.16.1** via Aurora Store (or SAI bundle) — save the APK/bundle.
2. Module APK from Releases (or let the tip row’s mod URL download).
3. Manager → **LSPatch** → **Custom Files** → Grindr + mod → install.

## News tab

See [news.md](news.md) — wiki + Releases, not Telegram.
