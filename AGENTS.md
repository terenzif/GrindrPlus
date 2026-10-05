## Learned User Preferences

- Do not treat README or personal/lab-use wording as architectural or technology constraints.
- Do not invent product constraints the user never decided; treat user corrections as authoritative over agent assumptions.
- Ver.5 must stay version-agnostic: no hardcoded single Grindr version as destiny.
- Prefer rootless delivery via dual path: Morphe binary patch plus LSPosed/LSPatch/Vector hooks (JingMatrix/Vector with Morphe integration).
- Delivery sequence is fixed: Morphe A complete in Ver.5 → Ver.5 green → then Morphe B.
- Settings checkboxes/toggles must match real runtime behavior; verify hooks E2E — no cosmetic toggles.
- Schema evolution/auto-migration, hot-reload, DI, circuit breaker, and anonymous-respecting telemetry are in scope.
- Prefer finding and resolving design problems before writing code; multi-agent multi-step plans are welcome when scoped.

## Learned Workspace Facts

- Official Ver.5 vision and sequencing live in `docs/vision.md` (and related ADRs under `docs/adr/`).
- Morphe A is orchestrator-rootless delivery: patch APK and embed Vector/LSPatch; it is not a product-hook/feature rewrite.
- Morphe B is static feature parity work and starts only after Ver.5 acceptance (“green”).
- Mapping platform is pack-driven and soft-fail: missing/invalid packs or version mismatch must not hard-abort init.
- Settings↔runtime truthfulness is a Ver.5 acceptance pillar (`docs/adr/0002-settings-hook-truthfulness.md`, E2E runbook).
- Anonymous opt-in telemetry is allowed via the existing Manager analytics setting.
