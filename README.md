<p align="center">
  <img src="docs/brand/grindr-plus-plus/hero-master.jpg" alt="Grindr++ — take them all" width="100%">
</p>

<p align="center">
  <img src="docs/brand/grindr-plus-plus/icon-master.jpg" alt="Grindr++" width="120" height="120">
</p>

<h1 align="center">Grindr++</h1>

<p align="center"><em>take them all</em></p>

<p align="center">
  Maintained by <a href="https://github.com/terenzif">@terenzif</a> — Xposed / Vector module for Grindr.
</p>

<p align="center">
  <a href="https://github.com/terenzif/grindr-plus-plus/actions/workflows/verify.yml"><img src="https://img.shields.io/github/actions/workflow/status/terenzif/grindr-plus-plus/verify.yml?branch=master&logo=github&label=Verify" alt="Verify"></a>
  <a href="https://github.com/terenzif/grindr-plus-plus/actions/workflows/build_apk.yml"><img src="https://img.shields.io/github/actions/workflow/status/terenzif/grindr-plus-plus/build_apk.yml?branch=master&logo=github&label=Build" alt="Build"></a>
  <a href="https://github.com/terenzif/grindr-plus-plus/releases/latest"><img src="https://img.shields.io/github/v/release/terenzif/grindr-plus-plus?label=Release" alt="Release"></a>
</p>

## What this is

**Grindr++** is its own product. It rose from the archived GrindrPlus line (ElJaviLuki → R0rt1z2, then this tree) after that project was frozen and after the PairIP / VM phase. Dual delivery uses `com.gpp.morphe` (rootless) and `com.gpp.alloy` (rooted Vector). The name, the mark, and the roadmap do not follow the archived product.

The phoenix is the product mark: a masked head, not a fork badge.

**Ver.5 direction:** version-agnostic module driven by **mapping packs** (schema v2), soft-fail hooks, Settings↔runtime truthfulness, and **Morphe A** rootless install (patch + embed Vector/LSPatch). Pack catalog: `mapping-packs/index.json`. Vision: [docs/vision.md](docs/vision.md).

**Mapping packs:** JSON per `versionCode` under `app/src/main/assets/mappings/` (bundled offline) **and** remotely from GitHub `mapping-packs/` so a new Grindr build can get a remapped pack **without** a full module APK rebuild. Loader: `MappingDictionary` — remote → device cache → assets → literals (soft-fail; active pack does not fall back to wrong-version literals). Details: [docs/remote-mapping-packs.md](docs/remote-mapping-packs.md).

This module is **not** affiliated with Grindr LLC. Use at your own risk.

## State now

- Dual APK: **Morphe** (Manager + LSPatch installer only — not a Vector module) and **Alloy** (sole Vector module).
- Remote mapping packs so version jumps do not always need a new APK.
- In-app **News** = wiki CTA + GitHub Releases (no Telegram feed).
- Soft-fail hooks: missing DEX fingerprints skip instead of crashing the host.

## Disclaimer

Free mod, no warranty. We are not responsible for lost chats, bans, or other issues. This project does not collect personal data and does not serve ads — the code is open source (GPL-3.0).

## Download & news

- Releases / APKs: [Releases](https://github.com/terenzif/grindr-plus-plus/releases)
- In-app **News** tab: wiki CTA + GitHub Releases list — [docs/news.md](docs/news.md)
- Wiki: [terenzif/grindr-plus-plus/wiki](https://github.com/terenzif/grindr-plus-plus/wiki)
- CI: [Verify](https://github.com/terenzif/grindr-plus-plus/actions/workflows/verify.yml) · [Build & Release](https://github.com/terenzif/grindr-plus-plus/actions/workflows/build_apk.yml)

Compatibility is **pack-driven** per installed Grindr `versionCode` (catalog: `mapping-packs/index.json`).

## Installation

### Rootless (Ver.5 / Morphe A — recommended product path)

Use the manager **LSPatch** tab: download/select Grindr → embed module via integrated LSPatch (Vector-family loader) → install. Details: [docs/morphe-a.md](docs/morphe-a.md), [docs/manager-ui-lspatch.md](docs/manager-ui-lspatch.md).

### Root (Vector — Alloy APK)

**Requirements:** Magisk / KernelSU + [JingMatrix/Vector](https://github.com/JingMatrix/Vector) (API 103+).

1. Install **`gpp-alloy-*.apk`** from [Releases](https://github.com/terenzif/grindr-plus-plus/releases) (or CI artifacts).
2. Install a Grindr build that has a mapping pack.
3. Enable the module in Vector and add Grindr to the scope.
4. Open Grindr and verify.

**Quick check:** long-press the **Browse** tab → Grindr++ status popup; unlimited cascade profiles and no third-party ads. In Settings → Manage Hooks, skipped/partial hooks show a status note (not silent no-ops).

## Features (maintained)

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

Some hooks are **skipped** or **partial** when the DEX fingerprint is gone — see soft-fail in `HookManager`.

## Known issues (notable)

- **Incognito:** unstable / turns itself off.
- **“Viewed Me”:** server-side, not modifiable.
- **Boost / Roaming:** disabled by default; re-enable by turning off the “Disable Boosting” hook.
- **Crash / albums:** try disabling “Unlimited Albums”.
- **Ad blocker:** empty profiles → whitelist `cdn.cookielaw.org` or disable AdAway.

## Development

See [docs/README.md](docs/README.md).

- Mapping packs (bundled): `app/src/main/assets/mappings/<versionCode>.json`
- Mapping packs (remote publish path): `mapping-packs/<versionCode>.json` — [docs/remote-mapping-packs.md](docs/remote-mapping-packs.md)
- Loader: `com.gpp.core.mapping.MappingDictionary`
- Brand assets: [docs/brand/grindr-plus-plus](docs/brand/grindr-plus-plus)

## Lineage

Grindr++ is not a continuation-in-name of GrindrPlus. The useful parts of that archive were carried forward; the product is this tree.

- Original idea and mod: [ElJaviLuki/GrindrPlus](https://github.com/ElJaviLuki/GrindrPlus)
- Rewrite through archive: [R0rt1z2/GrindrPlus](https://github.com/R0rt1z2/GrindrPlus) and contributors
- This product: [terenzif/grindr-plus-plus](https://github.com/terenzif/grindr-plus-plus)
- Vector / LSPatch: [JingMatrix](https://github.com/JingMatrix) ([Vector](https://github.com/JingMatrix/Vector), [LSPatch](https://github.com/JingMatrix/LSPatch))

## License

GPL-3.0 — see [LICENSE](LICENSE). Credit lineage if you ship a derivative; do not present this tree as the archived GrindrPlus project.
