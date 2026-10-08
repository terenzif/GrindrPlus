# Wave 4 E2E results — Settings ↔ runtime

| Field | Value |
| --- | --- |
| Date | 2026-10-05 |
| Target | Grindr `26.16.1` / `versionCode` `179451` |
| Pack | `mapping-packs/179451.json` (+ assets mirror) |
| Module | `4.7.2-26.16.1_f73aff5-debug` on Mi A2 lite (Lineage 15, Magisk + JingMatrix LSPosed) |
| Status | **device E2E matrix + toggle path passed** (session `5dd9df`) |
| Runbook | [e2e-settings-runtime-runbook.md](e2e-settings-runtime-runbook.md) |

## Device evidence (2026-10-05)

| Check | Result | Evidence |
| --- | --- | --- |
| Pack load schema v2 | Pass | DBG: `hasPack=true`, `packSymbols=76`, `packSchema=2`, `versionCode=179451` |
| Hook init summary | Pass | `enabled=18, partial=3, skipped=4` |
| Pack skips | Pass | Chat terminal / Video calls / Disable shuffle / Notification Alerts → `runtimeStatus=skipped` with pack reasons in `grindrplus.json` |
| Pack partials | Pass | Ban management / Disable boosting / Profile details → `partial` |
| Toggle OFF → runtime | Pass | Disabled “Allow screenshots” → log `Hook Allow screenshots is disabled.` + config `runtimeStatus=disabled`; summary `disabled=1` |
| Toggle restore | Pass | Re-enabled → `enabled=18` again |
| Post-login session | Pass | `HomeActivityOriginal`; `Failed to fetch own profile` count **0** after login + relaunch |
| Unit tests | Pass | `gradlew testDebugUnitTest` BUILD SUCCESSFUL (Studio JBR 21 + `C:\devbin\android-sdk`) |

Log sink: workspace `debug-5dd9df.log` (NDJSON, session `5dd9df`).

## What still needs deeper device UX (not blocking matrix green)

- Behavioral ON/OFF asserts for every mapped Manage Hooks row’s *user-visible* effect (screenshots, albums, explorer, etc.).
- Partial-hook remaining sites (ban UI details, boost upsells, profile detail fields).
- Settings UI badge walkthrough in Manager (config already has `runtimeStatus`/`runtimeReason`).
- Remap soft-skipped hooks when DEX allows; Command Prefix blocked while Chat terminal skipped.

## Sign-off

| Role | Name | Date | Notes |
| --- | --- | --- | --- |
| Code-verified gate | (agent / static review) | 2026-10-04 | Device not run |
| Device E2E | autonomous agent + rooted lab device | 2026-10-05 | Matrix + toggle + login session; see `debug-5dd9df.log` |
