# Manager UI — LSPatch tab & remote mappings

Bottom nav (this fork): **LSPatch · Block Log · Home · News · Settings**.

## Does LSPatch still work?

**Partially.** The embedded LSPatch pipeline (`org.lsposed.patch.LSPatch`, `InstallScreen` → `Installation` → `PatchApkStep`) is still in the APK and can patch Grindr + module when you supply both APKs.

What broke on this fork:

| Piece | Status |
| --- | --- |
| Upstream `gplusapks.airdns.org` hosts in old `manifest.json` | **Offline** (connect timeout) |
| One-click Install from hosted Grindr URL | **Unavailable** — Grindr APK is not hosted on this repo |
| Module APK URL | **OK** — GitHub Releases (`manifest.json` points at latest smoke/signed build) |
| Custom Files (pick Grindr + mod) | **Supported** path for LSPatch on this fork |

## Remote mapping packs + LSPatch

Yes — they compose.

1. LSPatch embeds the GrindrPlus module into a Grindr APK (or you use LSPosed with a separate module install).
2. At Grindr process init, `MappingDictionary.loadForVersion` runs: **remote → device cache → bundled assets → literals**.
3. Publishing a new pack under `mapping-packs/<versionCode>.json` does **not** require re-running LSPatch or reinstalling the module, as long as the module binary already contains the remote loader (current builds).

LSPosed remains the **recommended** path (fewer Google/maps issues). Use LSPatch when you cannot run LSPosed.

## How to use LSPatch on this fork

1. Download Grindr **26.16.1** via Aurora Store (or SAI bundle).
2. Download the module APK from [Releases](https://github.com/terenzif/GrindrPlus/releases).
3. Manager → **LSPatch** → **Custom Files** → select both → install.
4. After first launch, remote packs can refresh mappings for that Grindr `versionCode`.

`manifest.json` keeps a `v4.7.2-26.16.1` entry with an empty Grindr URL and the Releases mod URL so the selector shows the current tip; Install refuses empty Grindr URL and tells you to use Custom Files.

## News tab (related)

See [news.md](news.md) — wiki + Releases, not Telegram.
