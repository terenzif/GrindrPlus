<p align="center">
  <img src="gplus_icon.svg" alt="GrindrPlus" width="150" height="150">
</p>

<h1 align="center">GrindrPlus</h1>

<p align="center">
  Fork maintained by <a href="https://github.com/terenzif">@terenzif</a> — Xposed / LSPosed module for Grindr.
</p>

<p align="center">
  <a href="https://github.com/terenzif/GrindrPlus/actions"><img src="https://img.shields.io/github/actions/workflow/status/terenzif/GrindrPlus/build_apk.yml?branch=master&logo=github&label=Build" alt="Build"></a>
  <a href="https://github.com/terenzif/GrindrPlus/releases"><img src="https://img.shields.io/github/v/release/terenzif/GrindrPlus?include_prereleases&label=Release" alt="Release"></a>
</p>

## What this fork is

This repository is the **active fork** of GrindrPlus after the upstream project ([R0rt1z2/GrindrPlus](https://github.com/R0rt1z2/GrindrPlus)) was archived and after the PairIP / VM phase. Here we continue support, mappings, and releases for personal / lab use.

**Current direction (Phase 2):** target **Grindr 26.16.1** (`versionCode` 179451), with per-hook soft-fail and an updated version gate. Historical baseline still documented: **25.20.0**.

**In progress:** **product ↔ mapping** separation — JSON packs per `versionCode` under `app/src/main/assets/mappings/` and a `MappingDictionary` stub, so a new Grindr release can need a pack instead of a full core rebuild. Literals in `Obfuscation.kt` remain the live source until the migration (Phase B) is complete.

This module is **not** affiliated with Grindr LLC. Use at your own risk.

## Disclaimer

Free mod, no warranty. We are not responsible for lost chats, bans, or other issues. This project does not collect personal data and does not serve ads — the code is open source (GPL-3.0).

## Download

- Releases from this fork: [Releases](https://github.com/terenzif/GrindrPlus/releases)
- CI builds: [Actions](https://github.com/terenzif/GrindrPlus/actions)

Each build supports **one** specific Grindr version (currently **26.16.1**). A different client may fail to start the module or degrade individual hooks.

## Installation (LSPosed, recommended)

**Requirements:** root (Magisk / KernelSU) + working [LSPosed](https://github.com/JingMatrix/LSPosed) (JingMatrix fork recommended on recent Android).

1. Install the module APK from [Releases](https://github.com/terenzif/GrindrPlus/releases) (or CI).
2. Install Grindr **26.16.1** (Play Store or APKMirror bundle + [SAI](https://github.com/Aefyr/SAI/releases)).
3. Enable the module in LSPosed and add Grindr to the scope.
4. Open Grindr and verify.

**Quick check:** long-press the **Browse** tab → GrindrPlus status popup; unlimited cascade profiles and no third-party ads.

> LSPatch / no-root still exists in the manager code, but on this fork the **supported and tested** path is LSPosed. LSPatch has known limits (Google login, maps, stability).

## Features (inherited / maintained)

<details>
  <summary>Chat</summary>

  - Command console (`/help`)
  - Video calls on new chats
  - Hide chat indicators
  - Delete messages regardless of age
</details>

<details>
  <summary>Media</summary>

  - Unlimited expiring photos
  - View all received albums
  - Screenshots allowed
</details>

<details>
  <summary>Global</summary>

  - Ban details
  - Spoof Android ID
  - Reduced analytics
  - Developer features
  - Mod settings / hook management
  - Disable forced updates
</details>

<details>
  <summary>Profiles / Location / Premium</summary>

  - BMI, boost indicator, copy profile ID, distance, hidden fields, online status, favorites layout
  - Teleport / spoof location / saved locations
  - Unlimited cascade, Explore, filters, no third-party ads, saved phrases, no boost upsell, hide views, incognito
</details>

Some hooks on 26.16.1 are **skipped** or **partial** when the DEX fingerprint is gone — see soft-fail in `HookManager`.

## Known issues (notable)

- **Incognito:** unstable / turns itself off.
- **“Viewed Me”:** server-side, not modifiable.
- **Boost / Roaming:** disabled by default; re-enable by turning off the “Disable Boosting” hook.
- **Crash / albums:** try disabling “Unlimited Albums”.
- **Ad blocker:** empty profiles → whitelist `cdn.cookielaw.org` or disable AdAway.

## Development

See [docs/README.md](docs/README.md).

Mapping packs: `app/src/main/assets/mappings/<versionCode>.json`. Loader: `com.grindrplus.core.mapping.MappingDictionary` (stub — not yet wired into `init`).

## Credits

- Original idea and mod: [ElJaviLuki/GrindrPlus](https://github.com/ElJaviLuki/GrindrPlus)
- Rewrite and historical maintenance through archive: [R0rt1z2/GrindrPlus](https://github.com/R0rt1z2/GrindrPlus) and contributors
- LSPosed / LSPatch: [JingMatrix](https://github.com/JingMatrix)

## License

GPL-3.0 — see [LICENSE](LICENSE). Do not rebrand this project as your own: credit upstream and this fork.
