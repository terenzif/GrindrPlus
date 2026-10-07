# Dual APK migration (Morphe / Alloy)

GrindMod ships two packages (Manager label **GrindMod**):

| Audience | Package | Artifact |
| --- | --- | --- |
| Rootless | `com.gpp.morphe` | `gpp-morphe-*.apk` |
| Rooted | `com.gpp.alloy` | `gpp-alloy-*.apk` |

Product / Morphe clone label: **Grindr++** (`com.grindrapp.android.plus`).

## Morphe

1. Install stock Grindr from Play.
2. Install `gpp-morphe-*.apk`.
3. GrindMod → Install → Create / Update Grindr++.

## Alloy

1. Install `gpp-alloy-*.apk`.
2. Enable the module in **Vector** and scope `com.grindrapp.android`, **or** use GrindMod Settings → **Modding active**.
3. Do not expect a Grindr++ clone on Alloy — stock Grindr + hooks.
