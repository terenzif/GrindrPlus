# E2E runbook — Settings ↔ runtime

How to verify that Manager **Settings** match module runtime for GrindrPlus Ver.5.

**Related:** [vision.md](vision.md), [adr/0002-settings-hook-truthfulness.md](adr/0002-settings-hook-truthfulness.md), [wave0-p0-debt.md](wave0-p0-debt.md), [e2e-wave4-results.md](e2e-wave4-results.md)

## Preconditions

1. Module built and installed:
   - **Alloy** (`com.grindrplus.alloy`) via LSPosed, **or**
   - **Morphe** (`com.grindrplus.morphe`) via Morphe A / LSPatch integrated path (slim `-m` preferred).
   See [dual-apk-migration.md](dual-apk-migration.md) / [ADR 0005](adr/0005-dual-apk-morphe-alloy.md).
2. Target Grindr `versionCode` known; prefer a pack under `mapping-packs/<versionCode>.json` or `app/src/main/assets/mappings/`.
3. Bridge connected (Manager Settings reads config; module writes runtime status once ADR 0002 lands).
4. Record pack source used: remote / cache / assets / none (`MappingDictionary.loadForVersion` order — [remote-mapping-packs.md](remote-mapping-packs.md)).
5. Record delivery channel: `morphe` | `alloy` (`DeliveryChannel` / `BuildConfig.DELIVERY_CHANNEL`).

## Procedure (per row)

1. Set the setting to **ON** (or the listed value), force Grindr process restart if required.
2. Execute **ON assert**; record achieved status (`enabled` / `skipped` / `partial` / `failed`) and reason.
3. Set to **OFF**, restart if required.
4. Execute **OFF assert**; confirm no residual effect and UI shows `disabled`.
5. File bugs when UI shows effective ON but runtime is skip/fail (cosmetic toggle — P0).

## Known soft-skips on Grindr 26.16.1 (`versionCode` 179451)

From `mapping-packs/179451.json` `hooks` + hook source comments. These **must** surface as `skipped` (not `enabled`) when the user toggle is ON:

| Setting (UI title) | Hook class | Pack / code reason |
| --- | --- | --- |
| Chat terminal | `ChatTerminal` | `chatMessageMetaData absent` |
| Video calls | `EmptyCalls` | `IndividualChatNavViewModel/isTalkBefore absent` |
| Disable shuffle | `DisableShuffle` | `fingerprint absent on this DEX` / ShuffleUiState absent |
| Notification Alerts | `NotificationAlerts` | `notification_reminder_time absent` |

Sources: `mapping-packs/179451.json`; `hooks/ChatTerminal.kt`, `EmptyCalls.kt`, `DisableShuffle.kt`, `NotificationAlerts.kt`.

---

## Matrix skeleton — Manage Hooks

Registration order from `HookManager.registerHooks` in `app/src/main/java/com/grindrplus/utils/HookManager.kt`.  
UI titles = `hook.hookName`. Fill assert cells during Ver.5 E2E; status notes may cite pack `hooks.*.status`.

| setting | hook | ON assert | OFF assert | status notes |
| --- | --- | --- | --- | --- |
| Allow screenshots | `AllowScreenshots` | TBD — screenshots/recording possible in blocked surfaces | TBD — OS/app screenshot blocks restored where applicable | |
| Anti Block | `AntiBlock` | TBD — block/unblock notification or toast path fires | TBD — no GP block events from this hook | Pack may be `mapped` |
| Anti Detection | `AntiDetection` | TBD — root/emulator signals hidden per hook design | TBD — detections not altered by GP | |
| Ban management | `BanManagement` | TBD — ban detail tools reachable | TBD — tools inactive | Pack may be `partial` (`deviceUtility skip`) |
| Chat indicators | `ChatIndicators` | TBD — outgoing chat markers suppressed | TBD — stock indicators restored | Pack may be `mapped` |
| Chat terminal | `ChatTerminal` | TBD — terminal/commands available **or** status=`skipped` | TBD — terminal absent | **Soft-skip on 26.16.1** |
| Disable analytics | `DisableAnalytics` | TBD — analytics sinks hooked/no-op | TBD — stock analytics path | |
| Disable boosting | `DisableBoosting` | TBD — boost upsells hidden/disabled | TBD — boost UI stock | Pack may be `partial` |
| Disable shuffle | `DisableShuffle` | TBD — shuffle forced off **or** status=`skipped` | TBD — shuffle stock | **Soft-skip on 26.16.1** |
| Disable updates | `DisableUpdates` | TBD — forced update prompt suppressed | TBD — stock update gating | |
| Video calls | `EmptyCalls` | TBD — video call on empty chat **or** status=`skipped` | TBD — stock empty-chat call rules | **Soft-skip on 26.16.1** |
| Enable unlimited | `EnableUnlimited` | TBD — Unlimited feature flags / roles as designed | TBD — stock entitlement | Device confirmation required |
| Expiring media | `ExpiringMedia` | TBD — view/save beyond expiry | TBD — stock expiry | |
| Feature granting | `FeatureGranting` | TBD — granted features present | TBD — stock feature set | |
| Local saved phrases | `LocalSavedPhrases` | TBD — local phrases persist beyond stock limits | TBD — stock phrases only | Pack may be `mapped` |
| Location spoofer | `LocationSpoofer` | TBD — spoofed coords observed | TBD — real location path | |
| Notification Alerts | `NotificationAlerts` | TBD — reminder/warning suppressed **or** status=`skipped` | TBD — stock warnings | **Soft-skip on 26.16.1** |
| Online indicator | `OnlineIndicator` | TBD — green-dot duration follows setting | TBD — stock duration | Ties to Other Setting `online_indicator` |
| Profile details | `ProfileDetails` | TBD — extra fields visible | TBD — stock profile fields | Pack may be `partial` |
| Profile views | `ProfileViews` | TBD — view stealth behavior | TBD — stock view signaling | |
| Quick block | `QuickBlock` | TBD — quick-block action available | TBD — action absent | |
| Timber Logging | `TimberLogging` | TBD — Timber logs when DEBUG tree rules apply | TBD — no forced Timber | Debug-oriented |
| Unlimited albums | `UnlimitedAlbums` | TBD — album view beyond stock limits | TBD — stock album limits | |
| Unlimited profiles | `UnlimitedProfiles` | TBD — profile browse limits lifted | TBD — stock limits | |
| Unlock Explorer | `UnlockExplorer` | TBD — Explorer profiles unlocked | TBD — stock Explorer locks | |

### Registered but commented out in `HookManager` (not in Manage Hooks until re-enabled)

| setting | hook | notes |
| --- | --- | --- |
| Favorites | `Favorites` | Commented in `HookManager`; grid columns still in Other Settings |
| Status Dialog | `StatusDialog` | Commented out |
| Keep Alive WebSocket | `WebSocketAlive` | Commented out |

---

## Matrix skeleton — high-priority Other Settings

From `SettingsViewModel` group `other` (`id = "other"`). Prefer these for Ver.5 E2E before lower-priority manager chrome.

| setting | hook / consumer | ON assert | OFF assert | status notes |
| --- | --- | --- | --- | --- |
| Command Prefix | Chat terminal / commands | TBD — commands accept configured prefix | TBD — default `/` or prior value | Depends on Chat terminal not skipped |
| Online indicator duration (mins) | `OnlineIndicator` | TBD — duration matches minutes value | TBD — default/stock | Numeric `online_indicator` |
| Show BMI in Profile | `ProfileDetails` (GUI) | TBD — BMI row visible when data present | TBD — BMI hidden | May interact with GUI safety checks |
| Enable Cookie Tap | feature flags / hooks | TBD — cookie tap send path works | TBD — cannot send cookie taps | |
| Enable Star Section | feature flags | TBD — star/VIP section visible | TBD — section hidden | |
| Enable Interest Section | feature flags | TBD — interests on profile | TBD — section hidden | |
| Disable profile swipe | GUI / navigation hooks | TBD — click opens profile, swipe disabled | TBD — stock swipe | |
| Force old AntiBlock behavior | `AntiBlock` | TBD — legacy path used | TBD — current path | Test-only; keep OFF in prod |
| Use toasts for AntiBlock hook | `AntiBlock` | TBD — toasts instead of notifications | TBD — notifications | |
| Do GUI safety checks | GUI hooks | TBD — unsafe GUI hooks gated | TBD — checks skipped (risk of glitches) | Default ON |

### Manager opt-in (telemetry)

| setting | consumer | ON assert | OFF assert | status notes |
| --- | --- | --- | --- | --- |
| Opt-in analytics | Manager `analytics` | TBD — anonymous events may send | TBD — no analytics transmit | Allowed by [vision.md](vision.md); not a hook |

---

## Wave 4 code-verified matrix

**Target:** Grindr `26.16.1` / `versionCode` `179451` with pack `mapping-packs/179451.json` (same asset under `app/src/main/assets/mappings/`).  
**Sources:** `HookManager.registerHooks` (consumes `hooks.*.status` before `init`), pack `hooks` block, hook soft-skips in source.  
**Results log:** [e2e-wave4-results.md](e2e-wave4-results.md) — code-verified gate only; **no device E2E claimed**.

### Legend

| Column | Meaning |
| --- | --- |
| `user toggle` | Config intent (`ON` = hook enabled in Settings) |
| `expected runtimeStatus` | Status `HookManager` should persist after init on this pack (`enabled` / `partial` / `skipped` / `disabled` / `failed`) |
| `e2e assertion` | What a device tester should confirm |
| `verification` | `code-verified` = proven from pack + source/tests without a device; `needs-device` = behavioral / UI confirmation still required |

**Rules applied**

1. Pack `hooks.status = skipped` → `expected runtimeStatus = skipped`, `verification = code-verified` (`HookManager` returns before `init`).
2. Pack `partial` → `expected runtimeStatus = partial` after successful `init`.
3. Pack `mapped` (or absent) + wired → status `enabled` after successful `init`; `MappingDictionary.resolve` wiring may be `code-verified`, behavior always `needs-device`.
4. Cosmetic no-ops **without** a pack skip → listed as **FAIL debt** (must not look fully effective).

### Manage Hooks (registration order)

| setting | hook class | user toggle | expected runtimeStatus | e2e assertion | verification |
| --- | --- | --- | --- | --- | --- |
| Allow screenshots | `AllowScreenshots` | ON | `enabled` | Screenshots/recording possible on normally blocked surfaces | needs-device |
| Anti Block | `AntiBlock` | ON | `enabled` | Block/unblock notification or toast path fires | code-verified (`AntiBlock.*` resolve wiring); needs-device (behavior) |
| Anti Detection | `AntiDetection` | ON | `enabled` | Root/emulator signals hidden per hook design | needs-device |
| Ban management | `BanManagement` | ON | `partial` | Ban-detail path works; `deviceUtility` site skipped (empty pack name) | code-verified (pack `partial` + HookManager); needs-device (remaining sites) |
| Chat indicators | `ChatIndicators` | ON | `enabled` | Outgoing chat markers suppressed | code-verified (`ChatIndicators.*` resolve wiring); needs-device (behavior) |
| Chat terminal | `ChatTerminal` | ON | `skipped` | UI/reason shows skip (`chatMessageMetaData absent`); commands unavailable | code-verified (HookManager consumes pack `skipped`) |
| Disable analytics | `DisableAnalytics` | ON | `enabled` | Analytics SDK sinks hooked/no-op | needs-device |
| Disable boosting | `DisableBoosting` | ON | `partial` | Boost upsells reduced; not all sites mapped | code-verified (pack `partial` + HookManager); needs-device (behavior) |
| Disable shuffle | `DisableShuffle` | ON | `skipped` | Status=`skipped` (`fingerprint absent on this DEX`); shuffle stock | code-verified (HookManager consumes pack `skipped`) |
| Disable updates | `DisableUpdates` | ON | `enabled` | Forced update prompt suppressed | code-verified (`DisableUpdates.*` resolve wiring); needs-device (behavior) |
| Video calls | `EmptyCalls` | ON | `skipped` | Status=`skipped` (`IndividualChatNavViewModel/isTalkBefore absent`) | code-verified (HookManager consumes pack `skipped`) |
| Enable unlimited | `EnableUnlimited` | ON | `enabled` | Unlimited roles / paywall / hide sites as designed | code-verified (`EnableUnlimited.*` resolve wiring); needs-device (behavior) |
| Expiring media | `ExpiringMedia` | ON | `enabled` | View/save beyond stock expiry | needs-device (no pack `hooks.status`; FQCN map in hook) |
| Feature granting | `FeatureGranting` | ON | `enabled` | Granted feature flags present | code-verified (`FeatureGranting.*` resolve wiring); needs-device (behavior) |
| Local saved phrases | `LocalSavedPhrases` | ON | `enabled` | Local phrases persist beyond stock limits | code-verified (`LocalSavedPhrases.*` resolve wiring); needs-device (behavior) |
| Location spoofer | `LocationSpoofer` | ON | `enabled` | Spoofed coordinates observed | needs-device |
| Notification Alerts | `NotificationAlerts` | ON | `skipped` | Status=`skipped` (`notification_reminder_time absent`) | code-verified (HookManager consumes pack `skipped`) |
| Online indicator | `OnlineIndicator` | ON | `enabled` | Green-dot duration follows Other Setting minutes | code-verified (`OnlineIndicator.*` resolve wiring); needs-device (behavior) |
| Profile details | `ProfileDetails` | ON | `partial` | Extra profile fields partially applied | code-verified (pack `partial` + HookManager); needs-device (behavior) |
| Profile views | `ProfileViews` | ON | `enabled` | View stealth behavior | needs-device |
| Quick block | `QuickBlock` | ON | `enabled` | Quick-block action available | code-verified (`QuickBlock.*` resolve wiring); needs-device (behavior) |
| Timber Logging | `TimberLogging` | ON | `enabled` (**FAIL debt**) | Release builds: `init()` returns when `!BuildConfig.DEBUG` — no pack skip, so HookManager still records `enabled` (cosmetic ON) | **FAIL debt** — code-verified cosmetic no-op without pack skip; needs-device only if DEBUG build |
| Unlimited albums | `UnlimitedAlbums` | ON | `enabled` | Album view beyond stock limits | needs-device (pack symbol present; no `hooks.status`) |
| Unlimited profiles | `UnlimitedProfiles` | ON | `enabled` | Profile browse limits lifted | needs-device |
| Unlock Explorer | `UnlockExplorer` | ON | `enabled` | Explorer profiles unlocked | needs-device |

For every row above: user toggle **OFF** → expected `runtimeStatus = disabled` (code-verified via `HookManager`); OFF behavioral asserts remain `needs-device`.

### High-priority Other Settings

| setting | hook class | user toggle | expected runtimeStatus | e2e assertion | verification |
| --- | --- | --- | --- | --- | --- |
| Command Prefix | `ChatTerminal` / commands | any | N/A (config); effective only if Chat terminal not `skipped` | Prefix accepted in chat commands | **dead on 26.16.1** while Chat terminal pack-skipped — code-verified dependency; needs-device only after Chat terminal remapped |
| Online indicator duration (mins) | `OnlineIndicator` | numeric | inherits Online indicator (`enabled` when hook ON) | Duration matches configured minutes | code-verified (config → hook); needs-device (UI timing) |
| Show BMI in Profile | `ProfileDetails` | ON/OFF | inherits Profile details (`partial` when hook ON) | BMI row visible/hidden when data present | needs-device (GUI; interacts with `do_gui_safety_checks`) |
| Enable Cookie Tap | `FeatureGranting` | ON/OFF | inherits Feature granting (`enabled` when hook ON) | Cookie tap send path works / blocked | code-verified (`Feature("CookieTap", …)` wiring); needs-device (behavior) |
| Enable Star Section | `FeatureGranting` | ON/OFF | inherits Feature granting | Star/VIP section visible / hidden | code-verified (`Feature("VipFlag", …)` wiring); needs-device (behavior) |
| Enable Interest Section | `FeatureGranting` | ON/OFF | inherits Feature granting | Interests section on profile | code-verified (`enable-mutual-taps-no-paywall` wiring); needs-device (behavior) |
| Disable profile swipe | `UnlimitedProfiles` | ON/OFF | inherits Unlimited profiles (`enabled` when hook ON) | Click opens profile; swipe disabled | needs-device (`onProfileClicked` may soft-skip on Cascade V2 — confirm on device) |
| Force old AntiBlock behavior | `AntiBlock` | ON/OFF | inherits Anti Block | Legacy AntiBlock path used | needs-device (test-only; keep OFF in prod) |
| Use toasts for AntiBlock hook | `AntiBlock` | ON/OFF | inherits Anti Block | Toasts instead of notifications | needs-device |
| Do GUI safety checks | GUI hooks (`ProfileDetails`, `LocationSpoofer`, …) | ON/OFF | N/A (config gate) | Unsafe GUI hooks gated when ON | needs-device |
| Favorites grid columns | `Favorites` (not registered) | numeric | N/A | Columns change favorites grid | **FAIL debt** — Favorites hook commented out in `HookManager`; cosmetic setting with no runtime consumer |

### FAIL debt (cosmetic without pack skip)

| Item | Evidence | Desired fix |
| --- | --- | --- |
| Timber Logging | `TimberLogging.init` early-returns when `!BuildConfig.DEBUG`; no `hooks.TimberLogging.status=skipped` | Pack skip and/or HookManager records `skipped` on release no-op |
| Favorites grid columns | `Favorites` not in `HookManager` register list; Other Setting still exposed | Hide setting or re-enable `Favorites` with truthful status |

---

## Sign-off

| Field | Value |
| --- | --- |
| Grindr versionName / versionCode | |
| Pack schema / source | |
| Tester | |
| Date | |
| Open defects | |
