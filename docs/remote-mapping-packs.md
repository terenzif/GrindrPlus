# Remote mapping packs

Pack JSON uses **schemaVersion 2**. Schema v1 packs still load via a soft in-memory migrator
(`MappingDictionary.parsePack` sets `schemaVersion=2`, keeps symbols, ensures a `hooks` map).
New packs should publish `"schemaVersion": 2`.

## Why

When Grindr ships a new `versionCode`, F.Ter can publish a JSON pack on GitHub so devices download it **without** rebuilding / reinstalling the module APK. Bundled packs under `app/src/main/assets/mappings/` remain the offline fallback.

## Load order (`MappingDictionary.loadForVersion`)

1. **Remote** — `GET {base}/{versionCode}.json` (3s connect/read timeout)
2. **Device cache** — `filesDir/mapping-packs-cache/{versionCode}.json` (written after a successful remote fetch)
3. **Bundled assets** — module APK `assets/mappings/{versionCode}.json`
4. **Literals** — compile-time fallbacks in Kotlin (`Obfuscation` / callers) only when **no** pack activates

All network / parse / I/O failures **soft-fail** (log + continue). Init must not abort.

## Resolve fallback policy

`MappingDictionary.resolve(key, fallback)`:

| State | Result |
| --- | --- |
| No active pack | `fallback` (legacy literals) |
| Active pack, key present | `symbol.name` (empty string = explicit skip) |
| Active pack, key **missing** | `""` (soft-skip — **not** the compile-time fallback) |

This avoids cross-version hazards when a pack is loaded for a build that does not list every key.

## Pack catalog (`index.json`)

`MappingDictionary.loadIndex(cacheDir, remoteBaseUrl)` fetches `{base}/index.json` (soft-fail),
caches to `mapping-packs-cache/index.json`, and falls back to that cache when remote fails.

Shape:

```json
{
  "schemaVersion": 1,
  "packs": [
    { "versionCode": 179451, "versionName": "26.16.1", "confidence": "static-jadx" }
  ]
}
```

Catalog `schemaVersion` is independent of pack schema. Repo / assets copies live at
[`mapping-packs/index.json`](../mapping-packs/index.json) and `app/src/main/assets/mappings/index.json`.

## Default URL layout

| Piece | Value |
| --- | --- |
| Default base | `https://raw.githubusercontent.com/terenzif/GrindrPlus/master/mapping-packs` |
| Catalog | `{base}/index.json` |
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

Or any raw/CDN base that serves `{versionCode}.json` and `index.json`.

## Publish a new pack (no APK rebuild)

1. Fingerprint the Grindr APK (`versionCode` / R8 names) as usual.
2. Write `mapping-packs/<versionCode>.json` (`schemaVersion` 2 preferred; 1 still migrates).
3. Update `mapping-packs/index.json` (and optionally `app/src/main/assets/mappings/index.json`).
4. Open a PR / push to `master` on **terenzif/GrindrPlus**.
5. Optional: also copy into `app/src/main/assets/mappings/` so the next module build ships it offline.
6. Optional alternate channel: attach the JSON to a GitHub Release / tag named `mapping-packs` and point devices at that raw base via the override file.

Devices with a module that includes remote fetch will pick up the new file on the next Grindr process start (when online). Offline devices keep using cache or assets.

## Symbol kinds (schema v2)

| `kind` | `name` | Notes |
| --- | --- | --- |
| `class` | R8 / FQCN | Optional `fingerprint`, legacy `method` string still accepted on load |
| `method` | Member method name | Optional `note` for owning class |
| `field` | Member field name | Optional `note` for owning class |

## Fingerprint field

`symbols.*.fingerprint` is a JADX / identity hint. At runtime,
`TargetFingerprint` soft-validates what it can:

| Value | Runtime check |
| --- | --- |
| `struct:<sha256>` | Structural method/field/interface hash (prefix OK) |
| `implements Type` | Interfaces / superclass |
| `field Type` | Declared field types |
| `TypeName(…` | Simple name match |
| other | Soft-pass if not visible without DEX string-pool parse |

Mismatches log warnings only; init continues (literals / soft-fail).

## Related

- ADR: [adr/0001-mapping-pack-schema-v2.md](adr/0001-mapping-pack-schema-v2.md)
- Bundled multi-version corpus notes: Project store `docs/mapping-packs-multiversion.md`
- Loader: `com.grindrplus.core.mapping.MappingDictionary`
- Analysis handoff: [codebase-analysis-critical-issues.md](codebase-analysis-critical-issues.md)
