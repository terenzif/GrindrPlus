# Remote mapping packs

JSON packs served from this folder are the **default remote source** for
`MappingDictionary` (schemaVersion 1).

## Layout

```
mapping-packs/index.json          # Install UI version list (versionCode + versionName)
mapping-packs/<versionCode>.json  # pack payload
```

Example index:

```
https://raw.githubusercontent.com/terenzif/GrindrPlus/master/mapping-packs/index.json
```

Example pack (26.16.1):

```
https://raw.githubusercontent.com/terenzif/GrindrPlus/master/mapping-packs/179451.json
```

Bundled offline copies live under `app/src/main/assets/mappings/` (same filenames, including `index.json`).

The LSPatch **Download & Patch** dropdown is built from `index.json` (remote, else bundled), not from `manifest.json` keys. `manifest.json` still supplies the **module APK** URL.

## Publish a new pack (no module APK rebuild)

1. Add or update `mapping-packs/<versionCode>.json` on `master` (PR or direct push).
2. Update `mapping-packs/index.json` (and mirror under `app/src/main/assets/mappings/`).
3. Optionally also copy the pack into `app/src/main/assets/mappings/` for the next APK build.
4. Devices with a module that supports remote fetch will pick up the new pack on next Grindr start (soft-fail → assets/literals if offline).

See `docs/remote-mapping-packs.md` in the repository root docs.
