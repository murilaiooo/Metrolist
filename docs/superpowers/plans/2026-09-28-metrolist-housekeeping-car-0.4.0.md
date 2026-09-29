# MetroList Housekeeping + Car 0.4.0 Completion Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Reconcile the heavily changed MetroList repository, harden the two approved post-0.3.0 Car UI changes, update current-state documentation, and leave the codebase verified without importing any media/library payload.

**Architecture:** Preserve the released Windows/Car data contracts and the approved 0.3.0 player composition. Treat the redesigned navigation rail and reactive seek bar as the Car 0.4.0 user-facing delta, keep audio metering opt-in/lifecycle-bound, preserve all user backup data, and archive only disposable JVM crash diagnostics.

**Tech Stack:** Kotlin, Jetpack Compose, Media3, Gradle, Windows Compose Desktop, PowerShell/ADB.

**Spec:** `docs/superpowers/handoffs/2026-09-28-metrolist-car-0.3.0-release-handoff.md` plus the user's 2026-09-28 housekeeping request.

## Global Constraints

- Do not import a legacy backup or Car Library during this cleanup.
- Preserve the user backup file and all user media/download data.
- Do not redesign the approved Now Playing composition beyond the already-approved reactive seek bar.
- Do not push, merge, rewrite history, or delete branches.
- Keep Windows 0.3.1 contracts green while completing Car 0.4.0.
- Current work is on `main` because the user explicitly asked to organize and finalize the existing working tree containing the approved uncommitted changes.

## Review Focus

1. Audio metering must stop when the reactive player UI no longer needs it, so the feature does not add permanent per-sample CPU work.
2. The custom navigation rail and reactive seek bar must compile at 800x480, 1024x600, and 1280x720 without altering data/import state.
3. Release/version documentation must describe the actual post-0.3.0 code, not historical snapshots.
4. Disposable JVM crash artifacts should not clutter the repository root, while user backups remain untouched.
5. Windows, Car, and standard Android flavors must remain regression-green after shared playback changes.

---
### Task 1: Inventory and preserve the working state

**Files:** inspect Git state, release docs, local ignored artifacts.

- [x] Confirm the post-0.3.0 working diff and preserve the `.metrolistbackup` file unchanged.
- [x] Move only ignored JVM `hs_err_pid*.log` and `replay_pid*.log` files from repo root into `.superpowers/diagnostics/jvm-crashes/`.
- [x] Confirm `git diff --check` remains clean.

### Task 2: Bound reactive audio metering to the player lifecycle

**Files:**
- Modify: `app/src/main/kotlin/com/metrolist/music/playback/audio/VolumeNormalizationAudioProcessor.kt`
- Modify: `app/src/main/kotlin/com/metrolist/music/playback/MusicService.kt`
- Modify: `app/src/main/kotlin/com/metrolist/music/playback/PlayerConnection.kt`
- Modify: `app/src/car/kotlin/com/metrolist/music/car/ui/player/CarNowPlayingScreen.kt`
- Test: `app/src/test/kotlin/com/metrolist/music/playback/audio/AudioProcessorBufferReuseTest.kt`

- [x] Add a failing test proving disabling signal tracking clears the exposed meter level.
- [x] Run the focused test and observe the intended failure.
- [x] Implement explicit enable/disable tracking, make reads side-effect free, and disable tracking when the Compose effect is cancelled or playback stops.
- [x] Run focused tests and the Car unit/build gate.

### Task 3: Reconcile version and current documentation

**Files:**
- Modify: `docs/superpowers/releases/car-release-history.md`
- Create: `docs/superpowers/reviews/2026-09-28-metrolist-car-0.4.0-housekeeping-acceptance.md`
- Create: `docs/superpowers/handoffs/2026-09-28-metrolist-car-0.4.0-current-handoff.md`

- [x] Keep `metrolistCarVersion=0.4.0` as the coherent MINOR version for the approved user-facing feature batch.
- [x] Record navigation rail, reactive seek styling/audio response, lifecycle performance guard, and verification evidence.
- [x] State that older handoffs/audits remain historical snapshots and the new handoff is the current continuation point.
### Task 4: Full verification without imports

**Files:** no product-data mutation.

- [x] Run `git diff --check`.
- [x] Run Car tests + assemble with the verified Zulu JDK.
- [x] Run Foss, GMS, and Izzy unit tests + assemblies serially.
- [x] Run Windows `desktop-core`, `desktop-export`, `desktop-app` tests and `createDistributable`.
- [x] Install the exact generated Car APK on the existing AVD and launch it without importing anything.
- [x] Smoke 800x480, 1024x600, and 1280x720, restore 1024x600, and verify package identity/version.
- [x] Record final Git status and any remaining intentionally local artifacts.

## Execution ruling

Ruling: execute inline in the existing `main` working tree rather than creating a new worktree, because the target of this task is specifically the approved uncommitted post-0.3.0 state already present there. Cost if wrong: isolation is weaker, so every destructive Git/history operation remains prohibited and all user files are preserved.
