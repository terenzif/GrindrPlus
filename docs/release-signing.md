# Release signing (terenzif fork)

## GitHub secrets

| Secret | Purpose |
| --- | --- |
| `KEYSTORE_BASE64` | PKCS12 keystore, base64 |
| `KEY_ALIAS` | key alias |
| `KEYSTORE_PASSWORD` | store password |
| `KEY_PASSWORD` | key password (same as store for PKCS12) |

Used by `.github/workflows/build_apk.yml` (sign release when all four secrets are present).

Do **not** commit keystores or passwords. Rotate by regenerating the PKCS12 and updating secrets.

## Publish a signed build

Actions → **Build APK and Release** → `create_release=true` (optional tag). Or push a `v*` tag.

Gradle always emits `GPlus_v*-release-unsigned.apk` when no AGP signing config is set. After `apksigner` sign+verify succeeds, the workflow renames that file to `GPlus_v*-release.apk` (drops `-unsigned`) and uploads / attaches the renamed path. Debug APKs and unsigned release builds (missing secrets) are unchanged.

## Artifact name pattern

| Condition | Filename |
| --- | --- |
| Signed release (secrets present) | `GPlus_v{version}-release.apk` |
| Unsigned release (no secrets) | `GPlus_v{version}-release-unsigned.apk` |
| Debug | `GPlus_v{version}-debug.apk` |

## History

- PKCS12 CI signing: [#70](https://github.com/terenzif/GrindrPlus/pull/70)
- Rename after apksigner: [#73](https://github.com/terenzif/GrindrPlus/pull/73)
