# Remote mapping packs

JSON packs served from this folder are the **default remote source** for
`MappingDictionary` (schemaVersion 1).

## Layout

```
mapping-packs/<versionCode>.json
```

Example (26.16.1):

```
https://raw.githubusercontent.com/terenzif/GrindrPlus/master/mapping-packs/179451.json
```

Bundled offline copies live under `app/src/main/assets/mappings/` (same filenames).

## Publish a new pack (no module APK rebuild)

1. Add or update `mapping-packs/<versionCode>.json` on `master` (PR or direct push).
2. Optionally also copy into `app/src/main/assets/mappings/` for the next APK build.
3. Devices with a module that supports remote fetch will pick up the new pack on next Grindr start (soft-fail → assets/literals if offline).

See `docs/remote-mapping-packs.md` in the repository root docs.
