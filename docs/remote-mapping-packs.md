# Remote mapping packs

Schema stays **schemaVersion 1** (same as bundled assets).

## Why

When Grindr ships a new `versionCode`, F.Ter can publish a JSON pack on GitHub so devices download it **without** rebuilding / reinstalling the module APK. Bundled packs under `app/src/main/assets/mappings/` remain the offline fallback.

## Load order (`MappingDictionary.loadForVersion`)

1. **Remote** — `GET {base}/{versionCode}.json` (3s connect/read timeout)
2. **Device cache** — `filesDir/mapping-packs-cache/{versionCode}.json` (written after a successful remote fetch)
3. **Bundled assets** — module APK `assets/mappings/{versionCode}.json`
4. **Literals** — compile-time fallbacks in Kotlin (`Obfuscation` / callers) when no pack activates

All network / parse / I/O failures **soft-fail** (log + continue). Init must not abort.

## Default URL layout

| Piece | Value |
| --- | --- |
| Default base | `https://raw.githubusercontent.com/terenzif/GrindrPlus/master/mapping-packs` |
| Pack file | `{base}/{versionCode}.json` |
| Example (26.16.1) | https://raw.githubusercontent.com/terenzif/GrindrPlus/master/mapping-packs/179451.json |
| Repo folder | [`mapping-packs/`](https://github.com/terenzif/GrindrPlus/tree/master/mapping-packs) |

### Override base URL

Write a one-line file (no trailing slash required):

```
{filesDir}/mapping_pack_base_url.txt
```

Example alternate (release tag tree):

```
https://raw.githubusercontent.com/terenzif/GrindrPlus/mapping-packs/mapping-packs
```

Or any raw/CDN base that serves `{versionCode}.json`.

## Publish a new pack (no APK rebuild)

1. Fingerprint the Grindr APK (`versionCode` / R8 names) as usual.
2. Write `mapping-packs/<versionCode>.json` (schemaVersion 1).
3. Update `mapping-packs/index.json` (and `app/src/main/assets/mappings/index.json`) so Download & Patch lists the new version.
4. Open a PR / push to `master` on **terenzif/GrindrPlus**.
5. Optional: also copy into `app/src/main/assets/mappings/` so the next module build ships it offline.
6. Optional alternate channel: attach the JSON to a GitHub Release / tag named `mapping-packs` and point devices at that raw base via the override file.

Devices with a module that includes remote fetch will pick up the new file on the next Grindr process start (when online). Offline devices keep using cache or assets.

## Related

- Bundled multi-version corpus notes: Project store `docs/mapping-packs-multiversion.md`
- Loader: `com.grindrplus.core.mapping.MappingDictionary`
