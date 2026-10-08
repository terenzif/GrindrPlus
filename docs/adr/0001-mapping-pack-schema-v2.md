# ADR 0001: Mapping pack schema v2

- **Status:** Accepted (design for Ver.5)
- **Date:** 2026-10-04
- **Deciders:** GrindrPlus Ver.5 maintainers
- **Related:** [vision.md](../vision.md), [remote-mapping-packs.md](../remote-mapping-packs.md), `MappingPack` / `MappingDictionary` / `TargetFingerprint`

## Context

Schema v1 packs (`schemaVersion: 1`) already ship under `mapping-packs/<versionCode>.json` and `app/src/main/assets/mappings/`. Types live in:

- `app/src/main/java/com/grindrplus/core/mapping/MappingPack.kt`
- `app/src/main/java/com/grindrplus/core/mapping/MappingDictionary.kt`
- `app/src/main/java/com/grindrplus/core/mapping/TargetFingerprint.kt`

Today:

- Symbols use `kind` mainly as `"class"` / `"field"`, with an optional top-level `method` string on the symbol entry.
- Pack `hooks.<HookClass>.status` is parsed into `MappingHookStatus` but **`HookManager` does not consume it** (`app/src/main/java/com/grindrplus/utils/HookManager.kt`).
- When a pack is active, missing keys still fall through to **compile-time literals** pinned to Grindr `26.16.1` (`179451`) in `GrindrPlus.kt`, `Obfuscation.kt`, `RetrofitUtils.kt`, etc. That is a cross-version hazard.
- There is no catalog file; clients probe `{base}/{versionCode}.json` only.

## Decision

Adopt **schemaVersion 2** with these rules:

### 1. Symbol `kind` for methods / fields (or `members`)

Extend the symbol model so method and field targets are first-class:

- Keep `kind: "class"` for type targets (`name` = R8 / FQCN).
- Add / standardize `kind: "method"` and `kind: "field"` (preferred), **or** a nested `members` map under a class symbol for grouped members — implementers may choose one shape, but loaders must accept the chosen v2 shape consistently.
- Schema v2 packs must not rely on the ambiguous “class + optional `method` string” as the only method encoding for new keys (v1 packs remain readable via soft migrator).

### 2. `HookManager` consumes `hooks.status`

`HookManager.registerHooks` must read `MappingDictionary.current?.hooks` (or equivalent API) before calling `hook.init()`:

| Pack status | Runtime behavior |
| --- | --- |
| `mapped` / absent with present symbols | Normal init when user toggle is ON |
| `partial` | Init allowed; surface `partial` + reason to Settings |
| `skipped` | Do not init; surface `skipped` + reason |
| `failed` / unknown | Treat as soft-fail; surface `failed` + reason |

User disable still wins (`Config.isHookEnabled` → `disabled`).

### 3. Active pack ⇒ missing key soft-skips (no cross-version literal fallback)

When a pack is **active** for the installed `versionCode`:

- A missing symbol key, empty `name`, or unresolved member **soft-skips** that hook site.
- Do **not** fall back to `26.16.1` / `179451` compile-time literals for that key.
- Literals remain only when **no** pack activated (load order exhausted), and even then Ver.5 platform work aims to shrink that path (see [wave0-p0-debt.md](../wave0-p0-debt.md)).

### 4. Migrator 1 → 2 is soft

- Loaders accept `schemaVersion` 1 and 2.
- A soft migrator maps v1 fields (`kind`/`name`/`method`/`fingerprint`/`hooks`) into the v2 in-memory model.
- Unknown future `schemaVersion` continues to soft-fail (log + leave pack inactive), matching current `MappingDictionary` behavior.

### 5. `index.json` pack catalog

Publish `{base}/index.json` alongside packs (default base documented in [remote-mapping-packs.md](../remote-mapping-packs.md)), listing available `versionCode` / `versionName` / optional integrity hints. Clients may use the catalog for Manager version selection and pack-aware install; per-pack fetch remains `{base}/{versionCode}.json`.

### 6. Fingerprints

Keep runtime modes already implemented in `TargetFingerprint`:

- `struct:<sha256>`
- `implements …`
- `field …`

Optional future: `dex:` (string-pool / DEX-backed checks). Until implemented, unknown fingerprint forms remain soft-pass / warn-only — never abort init.

## Consequences

- **Positive:** Packs become the source of truth per Grindr build; Settings can show skip/partial honestly; catalog enables pack-aware Grindr selection (Morphe A / Manager).
- **Negative:** Authors must publish complete packs for new `versionCode`s; incomplete packs yield more visible skips instead of silent wrong-version literals.
- **Migration:** Existing `mapping-packs/*.json` keep working via soft 1→2 migrator; new packs should set `"schemaVersion": 2`.

## References

- Example pack (v1, 26.16.1): `mapping-packs/179451.json`
- Tests: `app/src/test/java/com/grindrplus/core/mapping/MappingDictionaryTest.kt`, `TargetFingerprintTest.kt`
