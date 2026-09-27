# MetroList Windows V2 — Integration and Acceptance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove the completed V2 works as one cohesive Windows application without regressing the validated V1 download/export flow or Android build.

**Architecture:** This plan adds no new product subsystem. It integrates Plans 01–04, performs requirement-by-requirement verification, fixes only reproduced regressions through systematic debugging/TDD, produces a final app-image for user acceptance, and packages an installer only after explicit version-bump authorization.

**Tech Stack:** Gradle/Kotlin JVM 21, Compose Desktop, existing Windows package scripts, Android Gradle variants.

**Spec:** `docs/superpowers/specs/2026-09-26-metrolist-windows-v2-design.md`

## Global Constraints
- No broad refactor during final integration.
- Any failure gets reproduced and root-caused before a fix.
- Preserve existing user session, offline media, offline database, and new library database across rebuilds.
- Do not rerun giant Zip64/full-library probes unless a changed area can affect them.
- Android validation runs serially with `--no-daemon --max-workers=1` because the prior parallel Izzy JVM crash was environmental.
- Do not bump version, install a replacement MSI, reset/clean/push/merge, or commit without explicit user authorization.

## Review Focus
- Existing V1 user data opens after V2 without migration loss.
- New `library.db` corruption/failure does not delete or mutate `offline.db`.
- Sign-out/sign-in does not accidentally sync local-only playlists.
- A large import can be cancelled/reopened without losing completed matching work.
- Final app-image uses the same packaged mpv/auth/runtime resources as the previously verified V1 path.

---
### Task 1: Requirements and test-suite gate

**Files:**
- Modify only for reproduced failures in the owning Plan's files/tests.
- Read: `docs/superpowers/specs/2026-09-26-metrolist-windows-v2-design.md`
- Read: all four V2 implementation plans.

- [ ] **Step 1: Build a requirement checklist from acceptance criteria 1–17**
Map each criterion to an automated test and/or manual product check. No criterion may be marked by inference from a neighboring test.

- [ ] **Step 2: Run Windows automated suite**
Run: `./gradlew.bat :desktop-core:test :desktop-export:test :desktop-app:test`
Expected: BUILD SUCCESSFUL with zero failed tests.

- [ ] **Step 3: Run whitespace/diff sanity**
Run: `git diff --check`
Expected: no whitespace errors; normal Windows CRLF warnings are acceptable.

- [ ] **Step 4: If any failure appears**
Invoke systematic-debugging, add a failing regression test, apply the smallest fix, rerun the owning focused suite, then rerun Step 2.

### Task 2: V1 regression smoke

**Files:** No planned source edits.

- [ ] **Step 1: Build current app-image**
Run: `./gradlew.bat :desktop-app:createDistributable`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: User smoke-check existing flow**
Using the TEST/app-image build: saved-session restore, Home/Search/Library navigation, one song playback, queue next/previous, one fresh song download with live progress, and one playlist download.

- [ ] **Step 3: Export smoke**
Export completed offline songs through the normal UI and confirm validator-backed completion. Do not overwrite the user's known backup.

- [ ] **Step 4: Regression response**
Any failure is treated as a regression; do not redesign the working subsystem while fixing it.
### Task 3: V2 product acceptance

**Files:** No planned source edits except targeted fixes from reproduced failures.

- [ ] **Step 1: Audio**
Confirm expanded/current player reports real codec/bitrate/sample rate and does not claim 320 kbps unless a real source ever reports that value.

- [ ] **Step 2: Large import**
Use a realistic TXT/paste dataset with hundreds of songs and, before final sign-off, the intended ~1,000-song list. Verify progress remains responsive and Review/Not found are manageable.

- [ ] **Step 3: Matching policy spot-check**
Manually inspect representative explicit/clean, original/live, original/remix, remaster, deluxe, acoustic, and unavailable cases.

- [ ] **Step 4: Local playlist**
Create local-only, play/shuffle/download/search/reorder/remove, restart app, confirm persistence, and verify it did not appear on YouTube Music.

- [ ] **Step 5: Sync**
Explicitly sync one local playlist to YouTube Music, confirm remote creation and track additions, then retry a simulated/real partial failure if encountered.

- [ ] **Step 6: UX**
Check List/Grid persistence, context actions, tooltips/icons, keyboard behavior, expanded player, queue mutation, narrow/wide window layout, and visible asynchronous feedback.

- [ ] **Step 7: Explore**
Confirm sections are useful and non-duplicative; remove filler rather than expanding scope if they are not.
### Task 4: Android and packaging gates

**Files:** No planned source edits.

- [ ] **Step 1: Android serial gate**
Run: `./gradlew.bat --no-daemon --max-workers=1 :app:testFossDebugUnitTest :app:testGmsDebugUnitTest :app:testIzzyDebugUnitTest :app:assembleFossDebug :app:assembleGmsDebug :app:assembleIzzyDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Windows distributable gate**
Run: `./gradlew.bat :desktop-core:test :desktop-export:test :desktop-app:test :desktop-app:createDistributable`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Installer authorization gate**
Do not change `packageVersion = "0.1.2"` or produce/install an upgrade MSI until the user explicitly authorizes the version bump. When authorized, choose the next version, package MSI, run `scripts/verify-windows-package.ps1`, and verify upgrade preserves `session.bin`, `offline.db`, media, artwork, and `library.db`.

- [ ] **Step 4: Final handoff**
Write a new V2 handoff recording exact build, validation evidence, user-confirmed flows, known limitations, and the next phase for the future landscape car APK. Do not begin the APK in this plan.
