# MetroList Car 0.3.0 UX, Performance, and Feedback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver MetroList Car 0.3.0 as a deliberate automotive player with permanent dark UI, driver-side transport controls, right-side swipeable artwork, continuous long-operation feedback, and measurably faster startup while preserving the working Windows/Car data and playback architecture.

**Architecture:** Keep Media3, Room, unified catalog, Car Library direct-play, legacy backup compatibility, MediaSession, and Windows contracts intact. Add a small Car-only design system, a reusable operation-progress model, observable import/library operations, deterministic player layout/swipe policies, and ADB-based acceptance tooling. Optimize startup only after collecting a reproducible baseline and tracing measured bottlenecks.

**Tech Stack:** Kotlin 2.4.20, Android Gradle Plugin 9.4.1, Jetpack Compose, Material 3, Media3 1.10.1, Room 2.8.5, Hilt 2.60.1, Coil 3.6.3, kotlinx.serialization, JUnit 4, Robolectric 4.17, PowerShell, Android SDK/ADB/Emulator API 29.

**Spec:** `docs/superpowers/specs/2026-09-28-metrolist-car-0.3.0-ux-performance-feedback-design.md`

## Global Constraints

- Preserve Media3 playback/MediaSession, Room, unified catalog, Car Library direct-play, `.metrolistbackup` v1, Windows exporter contracts, playback restoration, CarInputRouter, and standard Android/Windows behavior.
- MetroList Car is permanently dark, independent of Android system theme.
- Primary player layout is driver-left: metadata/transport controls left, artwork right.
- Primary controls remain Previous, Play/Pause, Next; no in-app volume control.
- Artwork swipe: left = Next, right = Previous; slow commit threshold is approximately 18-20% of artwork width.
- Reference visual canvas is 1024x600 at 160 dpi; acceptance also covers 800x480 and 1280x720.
- Development AVD remains Android 10/API 29 with WHPX; the primary acceptance profile is approximately 2 GB RAM and at least 24 GB data storage. A 6 GB run is optional only for parity comparison.
- Development-lab startup targets: cold start to first meaningful Car frame <= 1.5 s; warm start < 0.8 s.
- Any perceptibly long operation must provide immediate, continuous, explicit Running/Success/Error feedback.
- Validation safety remains intact: legacy backup validation completes before destructive/committed library mutation.
- Do not bump `metrolistCarVersion` to 0.3.0 until the final user-testable APK is intentionally packaged.
- No push/merge. Preserve unrelated user files and unrelated working-tree content.

## Review Focus

1. **Large backup with unknown provider size:** validation must still show active/indeterminate feedback and item activity rather than a false 0%; Task 4 adds this test.
2. **Accidental artwork motion:** small drags, diagonal noise, or dragging when no adjacent queue item exists must not skip a track; Task 9 adds these policy tests.
3. **Small 800x480 head unit:** navigation, transport controls, artwork, and Mini Player must remain usable without clipping; Tasks 2, 7, and 8 add sizing tests and Task 11 performs AVD verification.
4. **System light mode:** splash/window background and every MetroList-owned Compose surface must remain dark; Task 2 adds policy/resource checks and Task 11 verifies it on-device.
5. **Startup race:** restored playback and asynchronous library reopen must not block first render, duplicate reopen work, or navigate unpredictably; Tasks 5 and 10 add ownership/timing tests.

---

## File Structure

New Car-only design/feedback units:
- `app/src/car/kotlin/com/metrolist/music/car/ui/theme/CarTheme.kt` — fixed dark Material color scheme.
- `app/src/car/kotlin/com/metrolist/music/car/ui/theme/CarDesignTokens.kt` — reference-pixel sizing values used through `CarViewportMetrics`.
- `app/src/car/kotlin/com/metrolist/music/car/feedback/CarOperationProgress.kt` — generic operation stage/status/progress model.
- `app/src/car/kotlin/com/metrolist/music/car/feedback/CarThroughputEstimator.kt` — deterministic smoothed throughput calculation.
- `app/src/car/kotlin/com/metrolist/music/car/ui/component/CarOperationPanel.kt` — shared progress/success/error presentation.
- `app/src/car/kotlin/com/metrolist/music/car/ui/component/CarTrackRow.kt` — shared automotive track row.
- `app/src/car/kotlin/com/metrolist/music/car/ui/player/CarNowPlayingLayout.kt` — pure layout metrics and hit-target policy.
- `app/src/car/kotlin/com/metrolist/music/car/ui/player/CarArtworkSwipePolicy.kt` — pure swipe decision logic.

New development-lab tooling:
- `scripts/car/car-test-lab.ps1` — AVD start/configure/install/viewport/startup-measure helpers.
- `docs/superpowers/testing/metrolist-car-test-lab.md` — reproducible setup and acceptance commands.

Existing files remain owners of existing behavior; this plan modifies them instead of cloning subsystems.
### Task 1: Make the Car emulator lab reproducible and capture the 0.2.0 baseline

**Files:**
- Create: `scripts/car/car-test-lab.ps1`
- Create: `docs/superpowers/testing/metrolist-car-test-lab.md`
- Read-only baseline source: `D:\ESTE COMPUTADOR\Desktop\MetroList-Car-0.2.0-FINAL-TEST.apk`

**Interfaces:**
- Produces PowerShell commands `start`, `set-viewport`, `install`, `measure-startup`, and `screenshot`.
- Uses existing SDK tools under `%LOCALAPPDATA%\Android\Sdk`; does not install product dependencies.
- Produces an `IMPLEMENTATION_BASE_SHA` recorded from `git rev-parse HEAD` before the first product-code change; Task 11 uses this exact SHA for whole-branch review.

- [ ] **Step 1: Record implementation base and the current AVD baseline before product changes**
  Run `git rev-parse HEAD` and write the SHA into the test-lab doc as `IMPLEMENTATION_BASE_SHA`. Then run ADB checks for Android version, `wm size`, `wm density`, RAM/data size, and `emulator -accel-check`. Record current 0.2.0 cold and warm `am start -W` timings in the test-lab doc.

- [ ] **Step 2: Write the test-lab script with explicit guardrails**
  The script must refuse unknown actions, target only `MetroList_Car_API29`, and never delete/wipe an AVD or user directory. `set-viewport` accepts only `800x480`, `1024x600`, or `1280x720`.

- [ ] **Step 3: Configure the primary 2 GB AVD profile**
  Set approximately 2 GB RAM and at least 24 GB data storage in the AVD config. Document 6 GB only as an optional non-destructive parity comparison profile.

- [ ] **Step 4: Verify every harness action**
  Run `start`, `set-viewport 1024x600`, `install 'D:\ESTE COMPUTADOR\Desktop\MetroList-Car-0.2.0-FINAL-TEST.apk'`, `measure-startup`, and `screenshot`. Expected: device remains online, size is correct, APK launches, timing is printed, screenshot is created.

- [ ] **Step 5: Commit**
  `git add scripts/car docs/superpowers/testing && git commit -m "test(car): add reproducible emulator lab"`

### Task 2: Add the permanent Car dark design system and tighten the navigation shell

**Files:**
- Create: `app/src/car/kotlin/com/metrolist/music/car/ui/theme/CarTheme.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/ui/theme/CarDesignTokens.kt`
- Create: `app/src/car/res/values/styles.xml`
- Modify: `app/src/car/AndroidManifest.xml`
- Modify: `app/src/car/kotlin/com/metrolist/music/car/ui/CarApp.kt`
- Modify: `app/src/car/kotlin/com/metrolist/music/car/ui/CarNavigation.kt`
- Modify: `app/src/testCar/kotlin/com/metrolist/music/car/ui/CarNavigationTest.kt`
- Create: `app/src/testCar/kotlin/com/metrolist/music/car/ui/theme/CarThemeTest.kt`

**Interfaces:**
- Produces `internal fun carColorScheme(): ColorScheme` and `@Composable fun CarTheme(content: @Composable () -> Unit)`; `CarTheme` always uses that dark scheme and has no system-theme branch.
- Produces fixed palette values: background `#0B0D10`, surface `#12161B`, elevated/container `#171C22`, primary `#ED5564`, on-background/on-surface `#F4F5F7`, muted/on-surface-variant `#B8C0CA`, outline `#333A44`.
- Produces `CarDesignTokens` reference-pixel values: rail width 112, content padding 28, primary Play/Pause hit target 136, Previous/Next hit target 112, secondary-action hit target 84, standard track-row height 110, Mini Player height 120, Mini Player control target 96, and major horizontal gap 36. Through the existing 0.8 viewport scale, primary/Previous-Next render at about 108.8/89.6 physical px on 1024x600.
- Renames internal `CarDestination.DOWNLOADS` to `CarDestination.MUSIC`; user-visible behavior remains Music.

- [ ] **Step 1: Write RED theme and navigation tests**
  Assert Car palette background/surface luminance is dark, foreground contrast colors are light, the theme policy never depends on system-light state, default destination is Music, and navigation still exposes exactly Now Playing/Music/Library/Search/More.

- [ ] **Step 2: Run RED**
  Run: `.\gradlew.bat :app:testCarDebugUnitTest --tests "*CarThemeTest" --tests "*CarNavigationTest"`
  Expected: FAIL because Car theme/tokens and MUSIC destination do not exist.

- [ ] **Step 3: Implement static Car Compose theme and dark Android window theme**
  Add `Theme.Metrolist.Car` with dark window/status/navigation backgrounds and light-system-bar flags disabled. Point `CarMainActivity` to it. Wrap `CarApp` in `CarTheme`, not default `MetrolistTheme()`.

- [ ] **Step 4: Apply Car design tokens and reduce rail visual width**
  Keep hit areas generous while reducing visible rail bulk. Replace duplicated raw reference-pixel constants in the shell with named Car tokens.

- [ ] **Step 5: Run GREEN and assemble**
  Run: `.\gradlew.bat :app:testCarDebugUnitTest --tests "*CarThemeTest" --tests "*CarNavigationTest" :app:assembleCarDebug`
  Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 6: AVD visual probe**
  Force Android UI mode light, launch Car, capture splash/first frame/Music/More. Expected: all MetroList-owned surfaces remain dark.

- [ ] **Step 7: Commit**
  `git add app/src/car app/src/testCar && git commit -m "feat(car): add permanent dark automotive theme"`
### Task 3: Introduce one reusable operation-progress model and presentation

**Files:**
- Create: `app/src/car/kotlin/com/metrolist/music/car/feedback/CarOperationProgress.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/feedback/CarThroughputEstimator.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/ui/component/CarOperationPanel.kt`
- Create: `app/src/testCar/kotlin/com/metrolist/music/car/feedback/CarOperationProgressTest.kt`
- Create: `app/src/testCar/kotlin/com/metrolist/music/car/feedback/CarThroughputEstimatorTest.kt`

**Interfaces:**
- Produces `enum class CarOperationStage { PREPARING, VALIDATING, IMPORTING, FINALIZING, OPENING_LIBRARY, REFRESHING_LIBRARY, PREPARING_QUEUE, RESTORING }`.
- Produces `data class CarOperationProgress(stage, completedItems: Int?, totalItems: Int?, bytesProcessed: Long?, totalBytes: Long?, bytesPerSecond: Long?, detail: String?)`.
- Produces derived `fraction: Float?` only when `totalBytes > 0`; clamp to 0..1.
- Produces `sealed interface CarOperationState { Idle, Running(progress), Success(summary), Error(message, actionHint) }`.
- Produces `CarThroughputEstimator(windowMillis = 3000L)`; samples take explicit timestamps for deterministic tests.

- [ ] **Step 1: Write RED model tests**
  Assert known totals produce clamped fractions, unknown/zero totals are indeterminate, completed/total items remain independent from byte totals, and Error retains actionable text.

- [ ] **Step 2: Write RED throughput tests**
  Assert rolling throughput ignores zero/negative elapsed time, smooths short spikes over the 3-second window, and resets cleanly for a new operation.

- [ ] **Step 3: Run RED**
  Run: `.\gradlew.bat :app:testCarDebugUnitTest --tests "*CarOperationProgressTest" --tests "*CarThroughputEstimatorTest"`
  Expected: FAIL because feedback types do not exist.

- [ ] **Step 4: Implement model and estimator**
  Keep these Android-free/pure Kotlin.

- [ ] **Step 5: Implement `CarOperationPanel`**
  Render stage title, linear determinate/indeterminate progress, items, human-readable bytes, percent, smoothed speed, success state, and error/action hint. Keep it dark-theme-native and avoid modal blocking.

- [ ] **Step 6: Run GREEN**
  Run the focused tests plus `:app:assembleCarDebug`.
  Expected: PASS.

- [ ] **Step 7: Commit**
  `git add app/src/car app/src/testCar && git commit -m "feat(car): add unified operation feedback model"`

### Task 4: Make legacy backup import observable from the first byte

**Files:**
- Modify: `app/src/car/kotlin/com/metrolist/music/car/importer/CarImportPorts.kt`
- Modify: `app/src/car/kotlin/com/metrolist/music/car/importer/CarContentUriImportSource.kt`
- Modify: `app/src/car/kotlin/com/metrolist/music/car/importer/CarImportCoordinator.kt`
- Modify: `app/src/car/kotlin/com/metrolist/music/car/importer/CarImportRepository.kt`
- Modify: `app/src/car/kotlin/com/metrolist/music/car/service/CarImportService.kt`
- Modify: `app/src/car/kotlin/com/metrolist/music/car/ui/CarMoreScreen.kt`
- Modify: `app/src/testCar/kotlin/com/metrolist/music/car/importer/CarImportCoordinatorTest.kt`
- Modify: `app/src/testCar/kotlin/com/metrolist/music/car/importer/CarImportRepositoryTest.kt`
- Modify: `app/src/testCar/kotlin/com/metrolist/music/car/service/CarImportServiceCommandTest.kt`
- Create: `app/src/testCar/kotlin/com/metrolist/music/car/importer/CarValidationProgressTest.kt`

**Interfaces:**
- Extend `CarImportSource` with `val sizeBytes: Long?`.
- `CarContentUriImportSource.sizeBytes` queries `OpenableColumns.SIZE`; failure returns null instead of blocking import.
- `CarImportCoordinator.runImport(..., onProgress: (CarOperationProgress) -> Unit)`.
- Preserve `CarImportPhase` as journal/error persistence; do not serialize generic UI stages into the journal.
- Validation raw-byte progress uses the archive/provider byte size. Import byte progress continues to use destination payload bytes.
- Progress emission is throttled to at most about 4 UI updates/second, plus guaranteed first/final stage emissions.

- [ ] **Step 1: Write RED validation progress tests**
  Using a fake source with known size, assert first callback is PREPARING, VALIDATING callbacks appear before IMPORTING, validation bytes advance monotonically, and the final validation sample reaches the known source size.

- [ ] **Step 2: Add Review Focus RED test for unknown size**
  Fake `sizeBytes = null`; assert VALIDATING is emitted with `totalBytes = null` and activity/detail changes while the stream is read. No false determinate 0% is allowed.

- [ ] **Step 3: Write RED phase-transition tests**
  Assert successful flow is PREPARING -> VALIDATING -> IMPORTING -> FINALIZING -> Success and interrupted/error paths retain the correct persisted `CarImportPhase`.

- [ ] **Step 4: Run RED**
  Run: `.\gradlew.bat :app:testCarDebugUnitTest --tests "*CarValidationProgressTest" --tests "*CarImportCoordinatorTest" --tests "*CarImportRepositoryTest"`
  Expected: FAIL.

- [ ] **Step 5: Implement source-size discovery and counted validation stream**
  Count raw archive bytes around the validator input; do not add a second validation pass. Feed the reusable throughput estimator and throttled progress callback.

- [ ] **Step 6: Map existing import/commit progress to generic stages**
  Existing song counts become completed/total items. COMMITTING displays as FINALIZING even when byte progress has reached 100%.

- [ ] **Step 7: Make repository, notification, and More use the same progress snapshot**
  Replace the current `phase · n/n tracks`-only text with `CarOperationPanel`. Notification title/text/progress must be derived from the identical progress object.

- [ ] **Step 8: Run GREEN and the real 10-track backup**
  Run focused tests, `:app:testCarDebugUnitTest`, then import the existing ~44.86 MB fixture in the AVD. Expected: visible validation activity, import progress, finalization, then Complete 10/10.

- [ ] **Step 9: Commit**
  `git add app/src/car app/src/testCar && git commit -m "feat(car): expose end-to-end backup import progress"`
### Task 5: Give Car Library open/refresh and queue preparation explicit operation state

**Files:**
- Modify: `app/src/car/kotlin/com/metrolist/music/car/library/CarLibrarySession.kt`
- Modify: `app/src/car/kotlin/com/metrolist/music/car/ui/CarApp.kt`
- Modify: `app/src/car/kotlin/com/metrolist/music/car/ui/CarLibraryScreen.kt`
- Modify: `app/src/car/kotlin/com/metrolist/music/car/ui/CarDownloadsScreen.kt`
- Modify: `app/src/car/kotlin/com/metrolist/music/car/ui/CarSearchScreen.kt`
- Modify: `app/src/testCar/kotlin/com/metrolist/music/car/library/CarLibraryRepositoryTest.kt`
- Create: `app/src/testCar/kotlin/com/metrolist/music/car/library/CarLibrarySessionTest.kt`

**Interfaces:**
- `CarLibrarySession.operation: StateFlow<CarOperationState>`.
- `reopen/open/refresh` set Running with RESTORING/OPENING_LIBRARY/REFRESHING_LIBRARY, then Success/Error.
- `CarApp` owns the one startup `reopen()` call; entering Library must not trigger a duplicate reopen.
- Music/Search/Library queue resolution uses PREPARING_QUEUE feedback tied to the selected song rather than silently disabling the whole screen.

- [ ] **Step 1: Write RED session-state tests**
  Assert each operation publishes Running before terminal state and preserves the last valid index on refresh failure when recovery is possible.

- [ ] **Step 2: Add startup-ownership RED test**
  Assert the startup owner invokes reopen once; Library screen state observation does not invoke a second reopen.

- [ ] **Step 3: Run RED**
  Run: `.\gradlew.bat :app:testCarDebugUnitTest --tests "*CarLibrarySessionTest" --tests "*CarLibraryRepositoryTest"`
  Expected: FAIL.

- [ ] **Step 4: Implement session operation flow**
  Keep repository direct-play semantics unchanged. Errors describe permission loss, missing library, invalid manifest, or unavailable media without erasing recoverable location state.

- [ ] **Step 5: Replace generic Loading UI with shared feedback**
  Library screen shows operation stage/details. Music/Search/Library show selected-row queue preparation and explicit unavailable errors.

- [ ] **Step 6: Run GREEN and AVD failure probes**
  Test valid reopen, invalid folder, lost persisted permission, refresh, and missing track. Expected: no silent state and no duplicate startup reopen.

- [ ] **Step 7: Commit**
  `git add app/src/car app/src/testCar && git commit -m "feat(car): surface library operation feedback"`

### Task 6: Build one automotive track-row component and use it across Music/Search/Library

**Files:**
- Create: `app/src/car/kotlin/com/metrolist/music/car/ui/component/CarTrackRow.kt`
- Modify: `app/src/car/kotlin/com/metrolist/music/car/library/CarArtworkResolver.kt`
- Modify: `app/src/car/kotlin/com/metrolist/music/car/ui/CarDownloadsScreen.kt`
- Modify: `app/src/car/kotlin/com/metrolist/music/car/ui/CarSearchScreen.kt`
- Modify: `app/src/car/kotlin/com/metrolist/music/car/ui/CarLibraryScreen.kt`
- Modify: `app/src/testCar/kotlin/com/metrolist/music/car/library/CarArtworkResolverTest.kt`
- Create: `app/src/testCar/kotlin/com/metrolist/music/car/ui/CarTrackRowModelTest.kt`

**Interfaces:**
- Produces `CarTrackRowModel(title, artist, artworkUri, explicit, isCurrent, isBusy)`.
- Artwork precedence remains Car Library local -> legacy local -> remote/fallback.
- Primary list rows no longer display provenance labels such as `Legacy backup` unless an availability/error explanation requires them.
- Reference row hit target is at least 88 px at 1024x600 after viewport scaling.

- [ ] **Step 1: Write RED row-model tests**
  Assert artwork precedence, current-playing state, explicit marker, empty artist fallback, busy state, and absence of provenance text from normal rows.

- [ ] **Step 2: Run RED**
  Run: `.\gradlew.bat :app:testCarDebugUnitTest --tests "*CarTrackRowModelTest" --tests "*CarArtworkResolverTest"`
  Expected: FAIL.

- [ ] **Step 3: Implement shared row and model mapping**
  Include square artwork, title, artist, current-state treatment, explicit marker, large whole-row click target, and selected-row busy indicator.

- [ ] **Step 4: Replace duplicated Music/Search/Library row markup**
  Preserve existing queue resolver calls and list ordering.

- [ ] **Step 5: Run GREEN and capture screenshots**
  Verify representative empty/populated/current-playing states at 1024x600 and 800x480.

- [ ] **Step 6: Commit**
  `git add app/src/car app/src/testCar && git commit -m "feat(car): unify automotive track rows"`
### Task 7: Redesign the Mini Player for quick in-car control

**Files:**
- Modify: `app/src/car/kotlin/com/metrolist/music/car/ui/player/CarMiniPlayer.kt`
- Modify: `app/src/car/kotlin/com/metrolist/music/car/ui/theme/CarDesignTokens.kt`
- Create: `app/src/testCar/kotlin/com/metrolist/music/car/ui/player/CarMiniPlayerSizingTest.kt`

**Interfaces:**
- Mini Player contains artwork, title, artist when space permits, Previous, Play/Pause, Next.
- Metadata/artwork region opens Now Playing; transport button taps do not trigger parent open action.
- No volume control.
- Reference control hit area >= 76 px at 1024x600; row remains fully visible at 800x480.

- [ ] **Step 1: Write RED sizing/policy tests**
  Assert control/row metrics at 800x480, 1024x600, and 1280x720; assert no volume token/control exists.

- [ ] **Step 2: Run RED**
  Run: `.\gradlew.bat :app:testCarDebugUnitTest --tests "*CarMiniPlayerSizingTest"`
  Expected: FAIL.

- [ ] **Step 3: Implement Mini Player layout**
  Keep visual controls restrained but hit areas large. Ensure metadata click and transport clicks have separate interaction regions.

- [ ] **Step 4: Run GREEN and AVD touch probe**
  Play a track, navigate away from Now Playing, exercise all three controls and open area at 1024x600 and 800x480.

- [ ] **Step 5: Commit**
  `git add app/src/car app/src/testCar && git commit -m "feat(car): redesign mini player for driving"`

### Task 8: Move driver controls left and artwork right with deterministic layout metrics

**Files:**
- Create: `app/src/car/kotlin/com/metrolist/music/car/ui/player/CarNowPlayingLayout.kt`
- Modify: `app/src/car/kotlin/com/metrolist/music/car/ui/player/CarNowPlayingScreen.kt`
- Modify: `app/src/car/kotlin/com/metrolist/music/car/ui/player/CarNowPlayingControls.kt`
- Modify: `app/src/car/kotlin/com/metrolist/music/car/ui/theme/CarDesignTokens.kt`
- Modify: `app/src/testCar/kotlin/com/metrolist/music/car/ui/player/CarNowPlayingControlsTest.kt`
- Create: `app/src/testCar/kotlin/com/metrolist/music/car/ui/player/CarNowPlayingLayoutTest.kt`

**Interfaces:**
- Produces `CarNowPlayingLayoutMetrics.forViewport(viewport): CarNowPlayingLayoutMetrics`.
- Metrics expose left control-region width, right artwork size, gaps, Play/Pause hit target, Previous/Next hit targets, and secondary hit targets.
- At 1024x600: the token/viewport combination renders Play/Pause at about 108.8 physical px and Previous/Next at about 89.6 physical px, satisfying the approved 104-112 / 88-96 target bands.
- Artwork remains on the right for all three acceptance viewports.

- [ ] **Step 1: Write RED layout tests**
  Assert controls region precedes artwork, no overlap, minimum hit sizes, and all content fits within 800x480, 1024x600, and 1280x720.

- [ ] **Step 2: Extend control RED tests**
  Assert essential controls remain Previous/Play-Pause/Next/Seek/Queue/Shuffle/Repeat and volume is absent.

- [ ] **Step 3: Run RED**
  Run: `.\gradlew.bat :app:testCarDebugUnitTest --tests "*CarNowPlayingLayoutTest" --tests "*CarNowPlayingControlsTest"`
  Expected: FAIL.

- [ ] **Step 4: Implement driver-side Now Playing**
  Left column: title, artist, seek/times, primary controls, subdued secondary controls. Right: artwork only. Do not overlay essential text on artwork.

- [ ] **Step 5: Run GREEN and AVD visual review**
  Capture 800x480, 1024x600, 1280x720 with a real track/artwork. Verify no clipping and reachable controls near the left/driver side.

- [ ] **Step 6: Commit**
  `git add app/src/car app/src/testCar && git commit -m "feat(car): move player controls to driver side"`
### Task 9: Add deliberate swipe-to-skip on the album artwork

**Files:**
- Create: `app/src/car/kotlin/com/metrolist/music/car/ui/player/CarArtworkSwipePolicy.kt`
- Modify: `app/src/car/kotlin/com/metrolist/music/car/ui/player/CarNowPlayingScreen.kt`
- Reference only: `app/src/main/kotlin/com/metrolist/music/ui/player/MiniPlayer.kt`
- Create: `app/src/testCar/kotlin/com/metrolist/music/car/ui/player/CarArtworkSwipePolicyTest.kt`

**Interfaces:**
- Produces `enum class CarArtworkSwipeAction { NONE, PREVIOUS, NEXT }`.
- Produces `decide(offsetPx, artworkWidthPx, velocityPxPerMs, canPrevious, canNext, isRtl): CarArtworkSwipeAction`.
- Slow threshold: commit only after >= 0.19 * artwork width.
- Slow commit threshold is `abs(offsetPx) >= 0.19 * artworkWidthPx`.
- Fast-fling commit requires both `abs(offsetPx) >= 0.10 * artworkWidthPx` and `abs(velocityPxPerMs) >= 1.2f`; use the existing MetroList mobile gesture as behavioral reference but make Car more deliberate.
- Gesture applies only to artwork, never the seek slider/control region.

- [ ] **Step 1: Write RED direction/threshold tests**
  Assert LTR left -> NEXT, right -> PREVIOUS, 18% slow drag -> NONE, 20% -> commit, a 10% drag at >=1.2 px/ms can commit, and a <10% fling cannot commit regardless of velocity.

- [ ] **Step 2: Add Review Focus RED tests**
  Assert tiny/diagonal-noise equivalent horizontal offsets do nothing, unavailable previous/next returns NONE, zero width is safe, and RTL reverses physical-direction interpretation correctly.

- [ ] **Step 3: Run RED**
  Run: `.\gradlew.bat :app:testCarDebugUnitTest --tests "*CarArtworkSwipePolicyTest"`
  Expected: FAIL.

- [ ] **Step 4: Implement pure policy**
  Keep queue capability checks outside animation state; the policy returns a decision only.

- [ ] **Step 5: Wire artwork drag animation**
  Artwork follows finger, resists unavailable directions, commits through existing `PlayerConnection.seekToPrevious()/seekToNext()`, then animates to rest. Cancelled gestures animate back without track change.

- [ ] **Step 6: Run GREEN and AVD gesture probe**
  Exercise previous/next, sub-threshold cancel, first/last queue item, and repeated swipes.

- [ ] **Step 7: Commit**
  `git add app/src/car app/src/testCar && git commit -m "feat(car): add artwork swipe navigation"`

### Task 10: Measure and optimize startup without weakening restoration

**Files:**
- Create: `app/src/car/kotlin/com/metrolist/music/car/startup/CarStartupMilestoneTracker.kt`
- Modify: `app/src/car/kotlin/com/metrolist/music/CarMainActivity.kt`
- Modify: `app/src/car/kotlin/com/metrolist/music/car/ui/CarApp.kt`
- Inspect and modify only when the recorded trace identifies blocking work there: `app/src/car/kotlin/com/metrolist/music/car/ui/CarPlayerConnectionHost.kt`
- Inspect and modify only when the recorded trace identifies blocking work there: `app/src/car/kotlin/com/metrolist/music/car/library/CarLibrarySession.kt`
- Create: `app/src/testCar/kotlin/com/metrolist/music/car/startup/CarStartupMilestoneTrackerTest.kt`
- Modify: `scripts/car/car-test-lab.ps1`

**Interfaces:**
- Tracker records explicit monotonic milestones with an injected clock: ACTIVITY_CREATED, CONTENT_SET, SHELL_COMPOSED, PLAYER_CONNECTED, LIBRARY_REOPEN_STARTED, LIBRARY_READY.
- Startup shell must not await player connection or library reopen.
- Existing playback restore semantics remain unchanged.
- `CarApp` owns exactly one library reopen and does it on background coroutine work.

- [ ] **Step 1: Write RED milestone-order tests**
  Assert shell can be marked before player/library completion, duplicate milestone marks do not corrupt timing, and elapsed durations are monotonic/non-negative.

- [ ] **Step 2: Run RED**
  Run: `.\gradlew.bat :app:testCarDebugUnitTest --tests "*CarStartupMilestoneTrackerTest"`
  Expected: FAIL.

- [ ] **Step 3: Instrument current code without optimizing**
  Add log output with one stable tag and milestone deltas. Mark first meaningful shell composition before asynchronous dependencies complete.

- [ ] **Step 4: Collect three cold and three warm AVD traces**
  Use the test-lab script. Record median `am start -W` time and milestone bottlenecks in the test-lab doc.

- [ ] **Step 5: Apply only evidence-backed optimizations**
  Move blocking work off first render, eliminate duplicate initialization, and defer optional diagnostics/network-adjacent work. Do not rewrite Media3/Room or alter playback persistence to chase the metric.

- [ ] **Step 6: Add regression tests for any extracted startup policy**
  Every behavior change made in Step 5 gets a failing test first, then implementation.

- [ ] **Step 7: Re-run measurements**
  Target <=1.5 s cold and <0.8 s warm on the primary 2 GB AVD. If a target is not reached, record the measured bottleneck and do not claim the gate passed.

- [ ] **Step 8: Run full Car tests**
  Run: `.\gradlew.bat :app:testCarDebugUnitTest :app:assembleCarDebug`
  Expected: PASS.

- [ ] **Step 9: Commit**
  `git add app/src/car app/src/testCar scripts/car docs/superpowers/testing && git commit -m "perf(car): optimize measured startup path"`

### Task 11: Run the complete AVD acceptance matrix, large-backup gate, release review, and package Car 0.3.0

**Files:**
- Modify only after all acceptance gates pass: `gradle.properties`
- Modify: `docs/superpowers/releases/car-release-history.md`
- Create: `docs/superpowers/reviews/2026-09-28-metrolist-car-0.3.0-acceptance.md`
- Update if test-lab findings changed: `docs/superpowers/testing/metrolist-car-test-lab.md`

**Interfaces:**
- Release candidate version: `metrolistCarVersion=0.3.0`, expected Car versionCode `3000` under the existing SemVer formula.
- Final debug candidate path: `app/build/outputs/apk/car/debug/app-car-debug.apk` unless the repository's existing packaging task deliberately copies the same verified bytes to a user-facing filename.
- Acceptance record contains exact APK hash/path, test commands, startup medians, viewport screenshots, backup fixture details, and unresolved findings.

- [ ] **Step 1: Run full automated regression before version bump**
  Run Car serial gate: `.\gradlew.bat --no-daemon --max-workers=1 :app:testCarDebugUnitTest :app:assembleCarDebug`.
  Run standard Android serial gate: `.\gradlew.bat --no-daemon --max-workers=1 :app:testFossDebugUnitTest :app:testGmsDebugUnitTest :app:testIzzyDebugUnitTest :app:assembleFossDebug :app:assembleGmsDebug :app:assembleIzzyDebug`.
  Run Windows gate: `.\gradlew.bat :desktop-core:test :desktop-export:test :desktop-app:test :desktop-app:createDistributable`.
  Expected for each: BUILD SUCCESSFUL. Record any pre-existing failure by name; do not omit red tests.

- [ ] **Step 2: Execute the AVD functional matrix at 1024x600**
  Clean install; cold/warm start; system light mode while MetroList remains dark; all five destinations; play/pause/previous/next; seek; artwork swipe; Mini Player; queue; search; small legacy backup import; Car Library open/refresh; process kill/restart; offline playback.

- [ ] **Step 3: Execute viewport matrix**
  Repeat primary visual/touch probes at 800x480 and 1280x720. Capture screenshots of Now Playing, Music, Library, Search, More, Mini Player, import progress, and at least one error state.

- [ ] **Step 4: Execute large-backup acceptance**
  Prefer the user’s real ~4 GB / 300+ track `.metrolistbackup` if available. Otherwise use a representative synthetic/streamed fixture that keeps validation active long enough to prove PREPARING/VALIDATING progress, bytes/indeterminate behavior, throughput, IMPORTING counts, FINALIZING, completion, and stable memory/I/O. Record exact fixture size and song count.

- [ ] **Step 5: Interrupt and resume a non-trivial import**
  Kill/restart during import, verify journal recovery, Resume, Discard behavior, no duplicate completed layers, and no loss of pre-existing library content.

- [ ] **Step 6: Run fresh code review**
  Read `IMPLEMENTATION_BASE_SHA` from the test-lab doc and review the full diff from that exact SHA to current HEAD against the spec and this plan. Fix all Critical and Important findings with TDD, rerun affected tests, then repeat the whole-branch review if fixes are substantial.

- [ ] **Step 7: Perform requirement-by-requirement verification**
  Check every success criterion in the spec against automated evidence or AVD evidence. The acceptance doc must explicitly mark each item PASS/FAIL with evidence.

- [ ] **Step 8: Bump version only after the gates are green**
  Change `metrolistCarVersion=0.3.0`, update Car release history, assemble the intended installable APK, compute checksum, and record the artifact.

- [ ] **Step 9: Final verification**
  Re-run `CarBuildIdentityTest` and assert versionName `0.3.0` / versionCode `3000`; run `:app:testCarDebugUnitTest :app:assembleCarDebug`; inspect `app/build/outputs/apk/car/debug/app-car-debug.apk`; compute SHA-256; then clean-install that exact APK on the AVD. No completion claim without fresh output.

- [ ] **Step 10: Commit release metadata**
  `git add gradle.properties docs/superpowers/releases docs/superpowers/reviews docs/superpowers/testing && git commit -m "release: package MetroList Car 0.3.0"`

## Execution Order and Checkpoints

Tasks 1-5 establish the reliable foundation: reproducible lab, dark shell, unified progress, observable legacy import, and observable Car Library behavior.

Tasks 6-9 deliver the automotive visual/interaction redesign: shared rows, Mini Player, driver-side Now Playing, and swipe artwork.

Task 10 is intentionally late enough to measure the real redesigned UI but still before release packaging. Optimization is evidence-driven, not speculative.

Task 11 is the release gate. The physical car is not used for normal iteration; it receives only an AVD-approved candidate.

## Definition of Done

The implementation is complete only when:
- all plan tasks are committed;
- all automated gates are green or any unrelated pre-existing failure is explicitly documented;
- the exact packaged APK passes a clean AVD install;
- MetroList remains dark with Android set to light;
- controls are left/driver-side and artwork is right-side;
- artwork swipe behavior passes threshold/direction tests and manual probe;
- import feedback starts during validation, not after it;
- the small backup and large-backup acceptance both produce observable progress;
- Car Library remains direct-play and recoverable;
- startup measurements are recorded and compared with baseline;
- 800x480, 1024x600, and 1280x720 screenshots are reviewed;
- no Critical or Important code-review findings remain;
- Car release history records 0.3.0 and the installable artifact checksum.
