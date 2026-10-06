# Dual APK migration (Morphe / Alloy)

GrindrPlus ships two packages (same launcher label **GrindrPlus**):

| Channel | Package | Artifact |
| --- | --- | --- |
| Rootless | `com.grindrplus.morphe` | `GrindrPlus-morphe-*.apk` |
| Rooted | `com.grindrplus.alloy` | `GrindrPlus-alloy-*.apk` |

Legacy `com.grindrplus` is retired as an install ID. See [ADR 0005](adr/0005-dual-apk-morphe-alloy.md).

## From legacy rootless / LSPatch

1. Install `GrindrPlus-morphe-*.apk`.
2. Open GrindrPlus → Install → patch/install Grindr again (Morphe A).
3. Uninstall legacy `com.grindrplus` when satisfied.

## From legacy LSPosed / Vector

1. Install `GrindrPlus-alloy-*.apk`.
2. Enable the module in **Vector** and scope `com.grindrapp.android`.
3. Reboot if required by the framework.
4. Uninstall legacy `com.grindrplus` when satisfied.

Both packages may coexist. Do not embed the full Manager into Grindr; Morphe uses a slim `-m` payload when available.
