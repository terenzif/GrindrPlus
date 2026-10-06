# Dual APK Split Stabilize Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (tightly coupled WIP stabilize) or superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the ADR 0005 dual-APK split (`morphe` / `alloy` / internal `embed`) build-green and commit it without junk/secrets.

**Architecture:** Product flavors on `delivery` dimension; Manager UI in `app/src/manager` shared by morphe+alloy; Install tab gated by `DeliveryChannel.showsInstallTab`; slim embed omits launcher; CI builds both user APKs.

**Tech Stack:** Android Gradle flavors, Kotlin, JUnit, GitHub Actions.

## Global Constraints

- Package IDs: `com.gpp.morphe`, `com.gpp.alloy`; embed `com.gpp.morphe.payload` is not a Releases product.
- Do not favor rooted channel for Manager UX continuity.
- Exclude tmp logs, APKs, `.kotlin/`, bak jars, secrets from commit.
- Verify with fresh `./gradlew` evidence before claiming green.

---

### Task 1: Verify and fix dual-APK builds

**Files:**
- Modify only if compile fails: `app/build.gradle.kts`, flavor manifests, `app/src/manager/**`, `app/src/main/**` delivery helpers
- Test: `app/src/test/java/com/grindrplus/core/DeliveryChannelTest.kt`

**Interfaces:**
- Consumes: ADR 0005 flavor IDs / `BuildConfig.DELIVERY_CHANNEL`
- Produces: green `assembleMorpheDebug`, `assembleAlloyDebug`, unit tests for both flavors

- [x] **Step 1: Run unit tests for both flavors** (JDK 21 / Studio JBR; JDK 25 fails Robolectric)

```bash
./gradlew --no-daemon testMorpheDebugUnitTest testAlloyDebugUnitTest
```

Expected: BUILD SUCCESSFUL, 0 failed tests

- [x] **Step 2: Assemble debug APKs** (`assembleMorpheDebug` + `assembleAlloyDebug` + `assembleEmbedDebug`)

```bash
./gradlew --no-daemon assembleMorpheDebug assembleAlloyDebug -x lint
```

Expected: APKs under `app/build/outputs/apk/morphe/debug` and `.../alloy/debug`

- [x] **Step 3: Fix compile/test failures if any** — documented JDK 17/21 requirement; no product code fix needed

- [x] **Step 4: Re-run Steps 1–2 until green**

---

### Task 2: Commit dual-APK split (named files only)

**Files:** product sources, docs/ADR, CI, AGENTS.md, plan; exclude tmp/debug artifacts

- [x] **Step 1: Stage named product/docs/CI files only**
- [x] **Step 2: Commit with outcome-focused message** (`597c4b9` + follow-up `3235337` for main Manager deletions)
- [x] **Step 3: `git status` confirms junk left unstaged**
