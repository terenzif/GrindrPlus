# Wave 0 — Missing pack keys inventory (Ver.5)

Inventory of **hardcoded R8 class / method / field names** in the scanned sources that are **not** already
`MappingDictionary.resolve` / `className` keys (and are not already present as pack `symbols` keys for
26.16.1 / `179451`).

Target pack: `app/src/main/assets/mappings/179451.json` (versionName `26.16.1`).

Scanned files:

| File | Role |
| --- | --- |
| `core/http/Interceptor.kt` | HTTP header reflection |
| `hooks/EnableUnlimited.kt` | Unlimited / paywall / UI hide |
| `hooks/QuickBlock.kt` | Quick block menu |
| `hooks/FeatureGranting.kt` | Feature flags / grants |
| `hooks/OnlineIndicator.kt` | Online indicator duration |
| `hooks/DisableUpdates.kt` | Forced-update bypass |
| `core/Obfuscation.kt` | Already migrated via `resolve` |
| `GrindrPlus.kt` | Core class names via `resolve` |
| `utils/RetrofitUtils.kt` | Retrofit Result types via `resolve` |

## Naming convention (proposed)

- Prefer stable dotted keys: `{area}.{owner}.{member}` or `{HookName}.{role}`.
- `kind`: `class` | `method` | `field`.
- Fallback literals below are for **26.16.1 (179451)** only.
- Class symbols may also carry a pack `method` field (see `ProfileDetails.DISTANCE_UTILS`); separate
  `kind: method` keys are listed where the call site is independent of a single owning class entry.

## Already covered by MappingDictionary keys

These scanned sites already use `resolve` / pack keys — **no new key needed**.

| Key | Kind | Fallback (179451) | Source |
| --- | --- | --- | --- |
| `core.userAgent` | class | `lrc` | `GrindrPlus.kt` |
| `core.userSession` | class | `atc` | `GrindrPlus.kt` |
| `core.deviceInfo` | class | `wh3` | `GrindrPlus.kt` |
| `core.grindrLocationProvider` | class | `sw5` | `GrindrPlus.kt` |
| `core.serverDrivenCascadeRepo` | class | `us1` | `GrindrPlus.kt` |
| `core.ageVerificationActivity` | class | FQCN | `GrindrPlus.kt` |
| `core.browseExploreActivity` | class | FQCN | `GrindrPlus.kt` |
| `core.serverNotification` | class | FQCN | `GrindrPlus.kt` |
| `retrofit.fail` | class | `q84` | `RetrofitUtils.kt` |
| `retrofit.success` | class | `r84` | `RetrofitUtils.kt` |
| `retrofit.successValue` | field | `a` | `RetrofitUtils.kt` |
| `retrofit.failValue` | field | `a` | `RetrofitUtils.kt` |
| All `DisableBoosting.*` / `AntiBlock.*` / `ProfileDetails.*` / `ChatIndicators.*` | mixed | (see pack) | `Obfuscation.kt` |
| `FeatureGranting.settingDistanceVisibilityViewModel` | class | `n5b` | Pack symbol exists; **still hardcoded** in `FeatureGranting.kt` (wire-only) |
| `ProfileDetails.PROFILE_VIEW_HOLDER` | class | `r29` | Pack + `Obfuscation`; **also hardcoded** as `QuickBlock.profileViewHolder` (reuse) |
| `ProfileDetails.PROFILE_VIEW_STATE` | class | FQCN | Pack + `Obfuscation`; same FQCN as `EnableUnlimited.profileViewState` (reuse) |

Stable FQCNs / AndroidX / Play Core / Kotlin API names (`emit`, `invoke`, `getValue`, `getKey`, Material
`TabLayout`, etc.) are omitted from the missing list unless they are short R8 identifiers that move
with Grindr builds.

---

## Missing keys by source

### `Interceptor.kt` (HTTP)

Owner classes already keyed (`core.userSession`, `core.deviceInfo`, `core.userAgent`). Members are not.

| Proposed key | Kind | Fallback (179451) | Notes / JADX hint |
| --- | --- | --- | --- |
| `http.userSession.isLoggedInMethod` | method | `r` | `getJwt().length() > 0 &&` |
| `http.userSession.authTokenFlowMethod` | method | `x` | returns `StateFlow<String>` / `FlowKt.asStateFlow` |
| `http.userSession.rolesMethod` | method | `E` | `L-Grindr-Roles` header; was `F` on 25.20.0 |
| `http.deviceInfo.lazyField` | field | `d` | `public final kotlin.Lazy` on deviceInfo |
| `http.userAgent.stringMethod` | method | `a` | `getValue().getNameTitleCase()` |

### `EnableUnlimited.kt`

| Proposed key | Kind | Fallback (179451) | Notes / JADX hint |
| --- | --- | --- | --- |
| `EnableUnlimited.paywallUtils` | class | `xub` | interface; static `b()` shows `app_restart_required` (was `dk.c.d`) |
| `EnableUnlimited.paywallUtils.showMethod` | method | `b` | hook site on `xub` |
| `EnableUnlimited.persistentAdBannerContainer` | class | `h68` | ViewBinding for `persistent_banner_ad_compose_view` (was `Z4.a`) |
| `EnableUnlimited.persistentAdBannerContainer.bindMethod` | method | `a` | hide banner views |
| `EnableUnlimited.interstitialCollector.show` | class | `fo1` | static `FlowCollector` — `show()` |
| `EnableUnlimited.interstitialCollector.o39` | class | `o39` | static `FlowCollector` |
| `EnableUnlimited.interstitialCollector.d12` | class | `d12` | static `FlowCollector` |
| `EnableUnlimited.interstitialCollector.chatActivity` | class | `qy1` | ChatActivityV2 collector |
| `EnableUnlimited.interstitialCollector.profilesActivity` | class | `p49` | ProfilesActivity collector |
| `EnableUnlimited.viewsToHide.profileTagCascade` | class | `ny8` | `ProfileTagCascadeFragmentBinding` / `upsell_bottom_bar` |
| `EnableUnlimited.viewsToHide.drawerProfile` | class | `yr3` | `DrawerProfileBinding` plan/store cards |
| `EnableUnlimited.viewsToHide.fragmentRadar` | class | `eb9` | `FragmentRadarBinding` micros / right-now FABs |
| `EnableUnlimited.userSession.rolesUpdatedMethod` | method | `Y` | was `W`; `Y(List)` rolesUpdated |
| `EnableUnlimited.userSession.rolesMethod` | method | `E` | same member as `http.userSession.rolesMethod` — **alias / share one key** |

Recommended: use a single shared key `http.userSession.rolesMethod` for both Interceptor and EnableUnlimited.

### `QuickBlock.kt`

| Proposed key | Kind | Fallback (179451) | Notes / JADX hint |
| --- | --- | --- | --- |
| `QuickBlock.blockViewModel` | class | `mu0` | `STATUS_BLOCK_DIALOG_SHOWN` in `ju0`; dialog in `J()` |
| `QuickBlock.blockViewModel.showDialogMethod` | method | `J` | skip UI; block via HTTP |
| `QuickBlock.profileViewHolder.bindMethod` | method | `j` | `j(ProfileViewState)` binds toolbar / `menu_actions` |
| `QuickBlock.profileViewHolder.viewBindingField` | field | `b` | ViewBinding on ViewHolder |
| `QuickBlock.profileViewHolder.toolbarField` | field | `s` | toolbar on ViewBinding |

Reuse (do not invent a second class key): `ProfileDetails.PROFILE_VIEW_HOLDER` → `r29`.

### `FeatureGranting.kt`

| Proposed key | Kind | Fallback (179451) | Notes / JADX hint |
| --- | --- | --- | --- |
| `FeatureGranting.isFeatureFlagEnabled` | class | `iv6` | implements `IsFeatureFlagEnabled` |
| `FeatureGranting.isFeatureFlagEnabled.invokeMethod` | method | `a` | `Assignment.Flag` |
| `FeatureGranting.alertParamsField` | field | `P` | `AlertController.AlertParams` on `AlertDialog.Builder` |

Wire-only (key already in pack): `FeatureGranting.settingDistanceVisibilityViewModel` → `n5b`.

### `OnlineIndicator.kt`

| Proposed key | Kind | Fallback (179451) | Notes / JADX hint |
| --- | --- | --- | --- |
| `OnlineIndicator.utils` | class | `aq7` | `<= 600000` / `shouldShowOnlineIndicator` (was `Vm.m0`) |
| `OnlineIndicator.utils.shouldShowMethod` | method | `u` | was `a` on older build |
| `OnlineIndicator.isFeatureFlagEnabled` | class | `iv6` | **same class as** `FeatureGranting.isFeatureFlagEnabled` — share one key |
| `OnlineIndicator.profileUtilsV2.thresholdMethod` | method | `b` | owner FQCN stable: `com.grindrapp.android.utils.ProfileUtilsV2` |

Recommended shared class key: `FeatureGranting.isFeatureFlagEnabled` (or rename to `core.isFeatureFlagEnabled`).

### `DisableUpdates.kt`

| Proposed key | Kind | Fallback (179451) | Notes / JADX hint |
| --- | --- | --- | --- |
| `DisableUpdates.appUpgradeManager` | class | `xa0` | `Uri.parse("market://details?id=com.grindrapp.android")` + `deprecation_message` |
| `DisableUpdates.appUpgradeManager.showDeprecatedDialogMethod` | method | `b` | `.setMessage(R.string.deprecation_message)` |
| `DisableUpdates.appConfiguration.versionField` | field | `f` | was `d` on 25.20.0; spoofed as `$versionName.$versionCode` |

Play Core types (`AppUpdateInfo`, `zzm` / `zza`) are Google library symbols — optional pack entries if you want version pinning; not Grindr R8 short names.

### `Obfuscation.kt` / `GrindrPlus.kt` / `RetrofitUtils.kt`

No missing pack keys for R8 literals: every obfuscated name already goes through `MappingDictionary.resolve` (or `methodName` for `ProfileDetails.DISTANCE_UTILS`).

---

## Consolidated missing-key checklist

Deduped list for Ver.5 pack authoring (shared keys preferred).

| # | Proposed key | Kind | Fallback | Primary consumers |
| ---: | --- | --- | --- | --- |
| 1 | `http.userSession.isLoggedInMethod` | method | `r` | Interceptor |
| 2 | `http.userSession.authTokenFlowMethod` | method | `x` | Interceptor |
| 3 | `http.userSession.rolesMethod` | method | `E` | Interceptor, EnableUnlimited |
| 4 | `http.deviceInfo.lazyField` | field | `d` | Interceptor |
| 5 | `http.userAgent.stringMethod` | method | `a` | Interceptor |
| 6 | `EnableUnlimited.paywallUtils` | class | `xub` | EnableUnlimited |
| 7 | `EnableUnlimited.paywallUtils.showMethod` | method | `b` | EnableUnlimited |
| 8 | `EnableUnlimited.persistentAdBannerContainer` | class | `h68` | EnableUnlimited |
| 9 | `EnableUnlimited.persistentAdBannerContainer.bindMethod` | method | `a` | EnableUnlimited |
| 10 | `EnableUnlimited.interstitialCollector.show` | class | `fo1` | EnableUnlimited |
| 11 | `EnableUnlimited.interstitialCollector.o39` | class | `o39` | EnableUnlimited |
| 12 | `EnableUnlimited.interstitialCollector.d12` | class | `d12` | EnableUnlimited |
| 13 | `EnableUnlimited.interstitialCollector.chatActivity` | class | `qy1` | EnableUnlimited |
| 14 | `EnableUnlimited.interstitialCollector.profilesActivity` | class | `p49` | EnableUnlimited |
| 15 | `EnableUnlimited.viewsToHide.profileTagCascade` | class | `ny8` | EnableUnlimited |
| 16 | `EnableUnlimited.viewsToHide.drawerProfile` | class | `yr3` | EnableUnlimited |
| 17 | `EnableUnlimited.viewsToHide.fragmentRadar` | class | `eb9` | EnableUnlimited |
| 18 | `EnableUnlimited.userSession.rolesUpdatedMethod` | method | `Y` | EnableUnlimited |
| 19 | `QuickBlock.blockViewModel` | class | `mu0` | QuickBlock |
| 20 | `QuickBlock.blockViewModel.showDialogMethod` | method | `J` | QuickBlock |
| 21 | `QuickBlock.profileViewHolder.bindMethod` | method | `j` | QuickBlock |
| 22 | `QuickBlock.profileViewHolder.viewBindingField` | field | `b` | QuickBlock |
| 23 | `QuickBlock.profileViewHolder.toolbarField` | field | `s` | QuickBlock |
| 24 | `FeatureGranting.isFeatureFlagEnabled` | class | `iv6` | FeatureGranting, OnlineIndicator |
| 25 | `FeatureGranting.isFeatureFlagEnabled.invokeMethod` | method | `a` | FeatureGranting, OnlineIndicator |
| 26 | `FeatureGranting.alertParamsField` | field | `P` | FeatureGranting |
| 27 | `OnlineIndicator.utils` | class | `aq7` | OnlineIndicator |
| 28 | `OnlineIndicator.utils.shouldShowMethod` | method | `u` | OnlineIndicator |
| 29 | `OnlineIndicator.profileUtilsV2.thresholdMethod` | method | `b` | OnlineIndicator |
| 30 | `DisableUpdates.appUpgradeManager` | class | `xa0` | DisableUpdates |
| 31 | `DisableUpdates.appUpgradeManager.showDeprecatedDialogMethod` | method | `b` | DisableUpdates |
| 32 | `DisableUpdates.appConfiguration.versionField` | field | `f` | DisableUpdates |

**Count: 32 proposed new keys** (plus wire-only reuse of existing `FeatureGranting.settingDistanceVisibilityViewModel` and `ProfileDetails.PROFILE_VIEW_HOLDER` / `PROFILE_VIEW_STATE`).

---

## Current `hooks` status in `assets/mappings/179451.json`

There is no nested `hooks.status` object; each hook entry is `hooks.<HookName>.status` (+ optional `reason`).

| Hook | status | reason |
| --- | --- | --- |
| `DisableBoosting` | `partial` | — |
| `AntiBlock` | `mapped` | — |
| `ProfileDetails` | `partial` | — |
| `ChatIndicators` | `mapped` | — |
| `DisableShuffle` | `skipped` | fingerprint absent on this DEX |
| `EmptyCalls` | `skipped` | IndividualChatNavViewModel/isTalkBefore absent |
| `NotificationAlerts` | `skipped` | notification_reminder_time absent |
| `ChatTerminal` | `skipped` | chatMessageMetaData absent |
| `LocalSavedPhrases` | `mapped` | — |
| `BanManagement` | `partial` | deviceUtility skip |

Hooks scanned in this wave (`EnableUnlimited`, `QuickBlock`, `FeatureGranting`, `OnlineIndicator`,
`DisableUpdates`, HTTP interceptor) currently have **no** `hooks.*` entries in the pack.

Suggested Ver.5 follow-up statuses once keys land:

| Hook | Suggested initial status |
| --- | --- |
| `EnableUnlimited` | `unverified` / `partial` |
| `QuickBlock` | `unverified` |
| `FeatureGranting` | `partial` (distance VM already mapped) |
| `OnlineIndicator` | `unverified` |
| `DisableUpdates` | `unverified` |
| `HttpInterceptor` (or under `core`) | `unverified` |

---

## Out of scope (this document)

- Product Kotlin migration / `MappingDictionary.resolve` wiring.
- Fingerprint `struct:` hashes.
- Pack JSON edits to `179451.json`.
