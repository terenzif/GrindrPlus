# Development docs

## Ver.5

- [Vision](vision.md) — official Ver.5 scope, sequence (Morphe A → green → Morphe B), principles
- [ADRs](adr/) — architectural decisions
  - [0001 — Mapping pack schema v2](adr/0001-mapping-pack-schema-v2.md)
  - [0002 — Settings ↔ hook truthfulness](adr/0002-settings-hook-truthfulness.md)
  - [0003 — Morphe A patch backend](adr/0003-morphe-a-patch-backend.md)
  - [0004 — Morphe B static parity](adr/0004-morphe-b.md)
  - [0005 — Dual APK Morphe / Alloy](adr/0005-dual-apk-morphe-alloy.md)
  - [0006 — Alloy DexKit fingerprints](adr/0006-alloy-dexkit-fingerprints.md)
  - [0007 — Morphe B bytecodePatch](adr/0007-morphe-b-bytecodepatch.md)
- [Morphe A](morphe-a.md) — patch orchestrator (`PatchBackend` / LSPatch integrated)
- [Morphe B](morphe-b.md) — post-green static feature parity
- [Dual APK migration](dual-apk-migration.md) — `com.gpp.morphe` / `com.gpp.alloy`
- [Ver.5 green checklist](ver5-green-checklist.md)
- [E2E Settings ↔ runtime runbook](e2e-settings-runtime-runbook.md)
- [E2E Wave 4 results](e2e-wave4-results.md)
- [Wave 0 P0 debt freeze](wave0-p0-debt.md)
- [Wave 0 pack keys inventory](wave0-pack-keys-inventory.md)

## Dev env setup

Read here how to [set up dev environment](env_setup.md)

## Platform / manager

- [Remote mapping packs](remote-mapping-packs.md)
- [Manager UI — LSPatch & Play download](manager-ui-lspatch.md)
- [News tab](news.md)

## Analysis

- [Codebase analysis / critical issues](codebase-analysis-critical-issues.md)

## Patches

Patches are located in `com.gpp.hooks` package.
When targeting an obfuscated class/method, leave a comment with some sensible code snippet
used to search for the class in newer versions of the G app.

### Patch docs

Each patch should have it's own documentation in [patches](patches/README.md)
