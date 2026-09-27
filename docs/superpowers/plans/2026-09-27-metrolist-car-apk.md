# MetroList Car APK Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build MetroList Car 0.1.0 as an isolated, car-first Android head-unit product that safely imports Windows 0.2.2 `.metrolistbackup` archives, plays them fully offline, honors physical/system controls, and resumes playback after head-unit restart.

**Architecture:** Add a `car` flavor to the existing `:app` module so Car can reuse MusicService, Media3, Room, queue, and InnerTube without refactoring the approved Android/Windows products. Car owns its launcher, touch-first landscape UI, input routing, portable importer, import journal, diagnostics, and version line. Windows 0.2.2 remains the producer and its portable contract remains unchanged.

**Tech Stack:** Kotlin 2.4.20, Android Gradle Plugin 9.4.1, Compose 1.12.1, Media3 1.10.1, Room 2.8.5, Hilt 2.60.1, Coil 3.6.3, kotlinx.serialization, JUnit 4, Robolectric 4.17.

**Spec:** `docs/superpowers/specs/2026-09-27-metrolist-car-apk-design.md`

## Global Constraints

- Windows 0.2.2 is the compatibility producer; do not change `desktop-portable` unless a concrete compatibility defect proves it necessary.
- Car SemVer source: `gradle.properties -> metrolistCarVersion`, starting at `0.1.0`.
- Car application ID: `com.metrolist.music.car` for non-debug builds.
- Portable profile remains format v1 / database schema 38 / `media3_simple_cache_spans`.
- System volume is owned by Android/head-unit hardware; Car must not expose or consume volume controls.
- `PlatformScheduler` remains unchanged for Foss/GMS/Izzy and is disabled only for Car.
- No push and no APK installation without explicit user authorization.
## Review Focus

1. **Bad head-unit metrics:** zero, tiny, oversized, or contradictory viewport/density values must clamp to a usable scale instead of producing microscopic controls or divide-by-zero behavior. Task 3 owns the test.
2. **USB URI lost after restart:** a selected backup must use persistable URI permission; if the provider cannot be reopened, resume must fail clearly without mutating the existing library. Task 7 owns the test.
3. **Power loss mid-song import:** cache written but DownloadIndex/Room incomplete must resume by repairing only missing layers, without duplication. Task 7 owns the test.
4. **Pre-existing library content:** discarding an interrupted import must never remove a song/cache/artwork that existed before that import began. Task 7 owns the test.
5. **Unknown/volume hardware keys:** volume and unknown vendor keys must be observed for diagnostics but never consumed or mapped by guesswork. Task 3 owns the test.

---

## File Structure

Car-only product code lives under `app/src/car/kotlin/com/metrolist/music/car/` except flavor stubs that must match existing shared package names.

Build boundary:
- `gradle.properties`
- `app/build.gradle.kts`
- `app/src/car/AndroidManifest.xml`
- `app/src/car/kotlin/com/metrolist/music/CarMainActivity.kt`
- Car Cast stubs mirroring Foss/Izzy package paths.

Car subsystems:
- `car/input/`: physical-key interpretation and observations.
- `car/ui/`: viewport, navigation shell, player, library/search/settings surfaces.
- `car/importer/`: portable models, validation, Media3/Room/artwork targets, journal, coordinator.
- `car/service/`: foreground import service.
- `car/diagnostics/`: device/import/input diagnostic models.

Flavor tests live under `app/src/testCar/kotlin/com/metrolist/music/car/`.
### Task 1: Establish the Car flavor, version identity, and launcher boundary

**Files:**
- Modify: `gradle.properties`
- Modify: `app/build.gradle.kts`
- Create: `app/src/car/AndroidManifest.xml`
- Create: `app/src/car/kotlin/com/metrolist/music/CarMainActivity.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/cast/CastOptionsProvider.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/playback/CastConnectionHandler.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/ui/component/CastButton.kt`
- Test: `app/src/testCar/kotlin/com/metrolist/music/car/CarBuildIdentityTest.kt`
- Test: `app/src/testCar/kotlin/com/metrolist/music/car/CarManifestTest.kt`

**Interfaces:**
- Produces: `BuildConfig.IS_CAR: Boolean`.
- Produces: APK `versionName = metrolistCarVersion`.
- Produces: Car `versionCode = major * 1_000_000 + minor * 1_000 + patch`.
- Produces: `CarMainActivity` as the only launcher entry for the Car flavor.

- [ ] **Step 1: Write the failing Car build-identity test**
  Assert `BuildConfig.IS_CAR == true`, `BuildConfig.VERSION_NAME == "0.1.0"`, and `BuildConfig.VERSION_CODE == 1000`.

- [ ] **Step 2: Run the missing variant test**
  Run: `./gradlew.bat :app:testCarDebugUnitTest --tests "*CarBuildIdentityTest"`
  Expected: FAIL because the `car` product flavor/test task does not exist yet.
- [ ] **Step 3: Add Car SemVer parsing and the `car` flavor**
  Add `metrolistCarVersion=0.1.0` to `gradle.properties`. In `app/build.gradle.kts`, parse exactly three numeric SemVer components, reject components outside `0..999`, derive the version code formula above, default `IS_CAR=false`, and override it to true for `car`. Set Cast/updater false and Car application ID/version values.

- [ ] **Step 4: Add flavor stubs and minimal launcher**
  Mirror the no-Cast Foss/Izzy stubs under `src/car`. Add a minimal `@AndroidEntryPoint CarMainActivity` that can compile before the full shell exists.

- [ ] **Step 5: Write the failing manifest test**
  With Robolectric/PackageManager, assert the Car package has one launcher targeting `CarMainActivity`, requests landscape for that activity, and does not expose `MainActivityAlias` as a launcher.

- [ ] **Step 6: Add the Car manifest overlay**
  Remove/disable launcher aliases for this flavor, declare `CarMainActivity` as MAIN/LAUNCHER with landscape orientation, keep `MusicService`/MediaButton infrastructure from main, and remove Cast metadata as Foss already does.

- [ ] **Step 7: Run Car identity tests and assemble**
  Run: `./gradlew.bat :app:testCarDebugUnitTest :app:assembleCarDebug`
  Expected: PASS / BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**
  `git add gradle.properties app/build.gradle.kts app/src/car app/src/testCar && git commit -m "feat(car): establish Car product flavor"`

### Task 2: Isolate scheduler behavior and restore playing/paused intent

**Files:**
- Modify: `app/src/main/kotlin/com/metrolist/music/playback/ExoDownloadService.kt`
- Modify: `app/src/main/kotlin/com/metrolist/music/playback/MusicService.kt`
- Create: `app/src/main/kotlin/com/metrolist/music/playback/PlaybackRestorePolicy.kt`
- Test: `app/src/test/kotlin/com/metrolist/music/playback/PlaybackRestorePolicyTest.kt`
**Interfaces:**
- Produces: `fun shouldUsePlatformScheduler(isCar: Boolean): Boolean`.
- Produces: `fun restoredPlayWhenReady(isCar: Boolean, savedPlayWhenReady: Boolean): Boolean`.

- [ ] **Step 1: Write RED policy tests**
  Assert scheduler is disabled only for Car. Assert Car restores saved true/false, while Foss/GMS/Izzy retain the current restore-paused behavior.

- [ ] **Step 2: Run RED**
  Run: `./gradlew.bat :app:testFossDebugUnitTest --tests "*PlaybackRestorePolicyTest"`
  Expected: FAIL because policy functions do not exist.

- [ ] **Step 3: Implement the pure policy and wire both services**
  `ExoDownloadService.getScheduler()` returns null when `BuildConfig.IS_CAR`; otherwise it returns the existing `PlatformScheduler`. During persisted player-state restoration, seek first, then apply `restoredPlayWhenReady(...)`.

- [ ] **Step 4: Run focused tests plus Car assembly**
  Run: `./gradlew.bat :app:testFossDebugUnitTest --tests "*PlaybackRestorePolicyTest" :app:testCarDebugUnitTest :app:assembleCarDebug`
  Expected: PASS.

- [ ] **Step 5: Commit**
  `git add app/src/main/kotlin/com/metrolist/music/playback app/src/test/kotlin/com/metrolist/music/playback && git commit -m "feat(car): isolate scheduler and playback restore"`

### Task 3: Add deterministic viewport sizing and physical-input routing

**Files:**
- Create: `app/src/car/kotlin/com/metrolist/music/car/ui/CarViewport.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/input/CarInputRouter.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/input/CarInputDiagnostics.kt`
- Test: `app/src/testCar/kotlin/com/metrolist/music/car/ui/CarViewportTest.kt`
- Test: `app/src/testCar/kotlin/com/metrolist/music/car/input/CarInputRouterTest.kt`
**Interfaces:**
- Produces: `CarViewportMetrics.from(widthPx: Int, heightPx: Int): CarViewportMetrics`.
- Reference canvas: 1280x720; scale = `min(width/1280f, height/720f).coerceIn(0.75f, 1.5f)`.
- Produces: `fun CarViewportMetrics.pxToDp(referencePx: Float, density: Float): Dp` using rendered pixels, not physical-DPI assumptions.
- Produces: `data class CarKeySignature(keyCode: Int, scanCode: Int, source: Int)`.
- Produces: `data class CarInputDecision(val action: CarMediaAction?, val consume: Boolean)`.
- Produces: `CarInputRouter.decide(signature, keyAction): CarInputDecision`.

- [ ] **Step 1: Write viewport RED tests**
  Assert 1280x720 => 1.0, 1024x600 => 0.8, 1920x1080 => 1.5, zero/negative dimensions => safe minimum 0.75, and rendered primary/secondary hit targets resolve from 80/64 reference pixels.

- [ ] **Step 2: Write input RED tests**
  Assert standard media keys are recognized but not consumed, volume keys are never consumed, unknown keys are not consumed, and a supplied confirmed vendor mapping is consumed only for its exact signature.

- [ ] **Step 3: Run RED**
  Run: `./gradlew.bat :app:testCarDebugUnitTest --tests "*CarViewportTest" --tests "*CarInputRouterTest"`
  Expected: FAIL because Car viewport/router types do not exist.

- [ ] **Step 4: Implement viewport, router, and observation state**
  `CarInputDiagnostics` exposes the last raw key observation as StateFlow. Keep the vendor mapping table empty initially.

- [ ] **Step 5: Run GREEN**
  Run the same focused Car tests.
  Expected: PASS.

- [ ] **Step 6: Commit**
  `git add app/src/car/kotlin/com/metrolist/music/car app/src/testCar/kotlin/com/metrolist/music/car && git commit -m "feat(car): add viewport and input routing"`
### Task 4: Build the car-first playback shell

**Files:**
- Expand: `app/src/car/kotlin/com/metrolist/music/CarMainActivity.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/ui/CarApp.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/ui/CarNavigation.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/ui/CarPlayerConnectionHost.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/ui/player/CarNowPlayingScreen.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/ui/player/CarMiniPlayer.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/ui/player/CarNowPlayingControls.kt`
- Test: `app/src/testCar/kotlin/com/metrolist/music/car/ui/CarNavigationTest.kt`
- Test: `app/src/testCar/kotlin/com/metrolist/music/car/ui/player/CarNowPlayingControlsTest.kt`

**Interfaces:**
- Produces: `enum class CarDestination { NOW_PLAYING, DOWNLOADS, LIBRARY, SEARCH, MORE }`.
- Produces: `defaultCarNowPlayingControls(): List<CarPlayerControl>`.
- `CarPlayerConnectionHost` binds to existing `MusicService` and exposes `StateFlow<PlayerConnection?>`.
- CarMainActivity records raw key events; it consumes only confirmed vendor mappings and delegates standard/system keys to Android.

- [ ] **Step 1: Write RED navigation/control tests**
  Assert Downloads is the cold-start destination, active restored playback can select Now Playing, Back pops one Car route without stopping playback, and the default control list contains previous/play-pause/next/seek/queue/shuffle/repeat but no volume action.

- [ ] **Step 2: Run RED**
  Run: `./gradlew.bat :app:testCarDebugUnitTest --tests "*CarNavigationTest" --tests "*CarNowPlayingControlsTest"`
  Expected: FAIL.
- [ ] **Step 3: Implement service binding and root composition**
  Reuse the existing `MusicBinder -> PlayerConnection` construction pattern without initializing Listen Together. Keep MusicService alive independently of activity Back/Home navigation.

- [ ] **Step 4: Implement the Car shell and player**
  Use CarViewport sizing, persistent navigation, large artwork/title tap region, large transport controls, large seek target, and no volume UI. Local imported artwork support can remain a placeholder resolver interface until Task 6 supplies storage.

- [ ] **Step 5: Wire physical vendor actions**
  For `consume=true` decisions only, translate actions to the current PlayerConnection player methods. Continue delegating standard media and volume events to Android/MediaSession.

- [ ] **Step 6: Run focused tests and assemble**
  Run: `./gradlew.bat :app:testCarDebugUnitTest :app:assembleCarDebug`
  Expected: PASS.

- [ ] **Step 7: Commit**
  `git add app/src/car && git commit -m "feat(car): add car-first playback shell"`

### Task 5: Implement the portable v1 models and read-only validator

**Files:**
- Create: `app/src/car/kotlin/com/metrolist/music/car/importer/CarPortableModels.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/importer/CarPortableArchiveValidator.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/importer/ValidatedCarBackup.kt`
- Test: `app/src/testCar/kotlin/com/metrolist/music/car/importer/CarPortableArchiveValidatorTest.kt`
- Test: `app/src/testCar/kotlin/com/metrolist/music/car/importer/CarPortableRealBackupContractTest.kt`

**Interfaces:**
- Produces: `CarPortableArchiveValidator.validate(input: InputStream, availableDestinationBytes: Long): ValidatedCarBackup`.
- `ValidatedCarBackup` contains manifest plus parsed songs/artists/maps/formats/downloads and validated resource/file declarations.
- Compatibility values are exact: Metrolist / format 1 / source app profile 13.6.0 / DB 38 / offline_downloads / media3_simple_cache_spans / totalBackupBytes null.
- [ ] **Step 1: Write a tiny valid in-memory ZIP fixture**
  Include all seven required metadata paths, one song/resource/transfer, one deflated span, optional artwork, and exact SHA-256 values.

- [ ] **Step 2: Write RED validator tests**
  Cover valid archive, wrong profile, duplicate IDs, missing entry, undeclared entry, non-deflated entry, unsafe path, wrong hash, span gap/overlap, mismatched content length, malformed metadata, and insufficient destination space.

- [ ] **Step 3: Run RED**
  Run: `./gradlew.bat :app:testCarDebugUnitTest --tests "*CarPortableArchiveValidatorTest"`
  Expected: FAIL.

- [ ] **Step 4: Implement streaming validation**
  Use `ZipInputStream` so SAF content can be validated without filesystem paths. Buffer only JSON metadata and hashes; never buffer media payloads in full.

- [ ] **Step 5: Run GREEN**
  Run the focused validator test.
  Expected: PASS.

- [ ] **Step 6: Add and run the real Windows-backup contract probe**
  `CarPortableRealBackupContractTest` reads `METROLIST_CAR_BACKUP_PATH`; when the variable is absent it skips, but when present it must validate that exact Windows-generated archive through the Car validator. For the local gate, point it at a real Windows 0.2.2 `.metrolistbackup` and run `./gradlew.bat :app:testCarDebugUnitTest --tests "*CarPortableRealBackupContractTest"`; expected: one executed PASS, not skipped.

- [ ] **Step 7: Commit**
  `git add app/src/car/kotlin/com/metrolist/music/car/importer app/src/testCar/kotlin/com/metrolist/music/car/importer && git commit -m "feat(car): validate portable backup v1"`

### Task 6: Add real Media3, Room, and artwork import targets

**Files:**
- Create: `app/src/car/kotlin/com/metrolist/music/car/importer/CarMedia3ImportTarget.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/importer/CarRoomImportTarget.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/importer/CarArtworkStore.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/importer/CarImportInspector.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/importer/CarPortableMappers.kt`
- Test: `app/src/testCar/kotlin/com/metrolist/music/car/importer/CarMedia3ImportTargetTest.kt`
- Test: `app/src/testCar/kotlin/com/metrolist/music/car/importer/CarPortableMappersTest.kt`
**Interfaces:**
- `CarMedia3ImportTarget.writeResource(resource, openSpan): Unit` writes bytes with `CacheDataSink`.
- `CarMedia3ImportTarget.putCompletedDownload(transfer): Unit` uses `DefaultDownloadIndex(databaseProvider)`.
- `CarRoomImportTarget.upsertSongBundle(song, artists, maps, format): Unit` writes current entities in a Room transaction.
- `CarArtworkStore.write(songId: String, input: InputStream): File` writes `filesDir/car-artwork/<songId>.img` atomically.
- `CarImportInspector.inspect(songId, contentLength): CarLocalImportState` reports which layers already exist.

- [ ] **Step 1: Write RED Media3 integration test**
  Under Robolectric, create temporary `SimpleCache` + `StandaloneDatabaseProvider`, import split spans, assert `cache.isCached(key, 0, length)`, and assert `DefaultDownloadIndex.getDownload(id).state == STATE_COMPLETED`.

- [ ] **Step 2: Write RED mapping/idempotency tests**
  Assert portable fields map truthfully to current SongEntity/ArtistEntity/FormatEntity and repeated relation upserts do not create duplicates.

- [ ] **Step 3: Run RED**
  Run: `./gradlew.bat :app:testCarDebugUnitTest --tests "*CarMedia3ImportTargetTest" --tests "*CarPortableMappersTest"`
  Expected: FAIL.

- [ ] **Step 4: Implement targets**
  Use public Media3 APIs only; never copy portable span files into SimpleCache's private filename layout. Preserve truthful itag/mime/codecs/bitrate/sampleRate/contentLength values.

- [ ] **Step 5: Run GREEN**
  Run the focused tests.
  Expected: PASS.

- [ ] **Step 6: Commit**
  `git add app/src/car/kotlin/com/metrolist/music/car/importer app/src/testCar/kotlin/com/metrolist/music/car/importer && git commit -m "feat(car): import portable media and metadata"`
### Task 7: Add journaled two-pass import, SAF persistence, and foreground service

**Files:**
- Create: `app/src/car/kotlin/com/metrolist/music/car/importer/CarImportJournal.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/importer/CarImportJournalStore.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/importer/CarImportCoordinator.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/importer/CarImportRepository.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/service/CarImportService.kt`
- Modify: `app/src/car/AndroidManifest.xml`
- Test: `app/src/testCar/kotlin/com/metrolist/music/car/importer/CarImportCoordinatorTest.kt`
- Test: `app/src/testCar/kotlin/com/metrolist/music/car/importer/CarImportJournalStoreTest.kt`

**Interfaces:**
- `CarImportCoordinator.validate(uri): CarImportPreflight` is mutation-free.
- `CarImportCoordinator.import(uri, resume: Boolean, onProgress): CarImportResult`.
- Journal records archive identity, phase, per-song completion, and which cache/index/Room/artwork artifacts were created by this import.
- `CarImportRepository` exposes `StateFlow<CarImportStatus>`.
- [ ] **Step 1: Write RED recovery tests**
  Cover failure after cache but before DownloadIndex, failure after DownloadIndex but before Room, resume repairing only missing layers, and repeated import skipping a fully valid song.

- [ ] **Step 2: Write RED ownership/discard tests**
  Pre-populate a song before import, interrupt, then discard. Assert only artifacts created by this import are removed; pre-existing song/cache/artwork remain.

- [ ] **Step 3: Write RED SAF reopen test**
  Model a persisted URI that cannot be reopened after process restart. Assert resume returns a specific source-unavailable failure and performs zero mutation.

- [ ] **Step 4: Run RED**
  Run: `./gradlew.bat :app:testCarDebugUnitTest --tests "*CarImportCoordinatorTest" --tests "*CarImportJournalStoreTest"`
  Expected: FAIL.

- [ ] **Step 5: Implement atomic journal and coordinator**
  Journal writes use temp-file + atomic rename. Pass A validates by reopening the URI and checking free space. Pass B reopens the URI, imports one resource at a time, checkpoints only after all required layers for that song are valid, and leaves recoverable state on interruption.

- [ ] **Step 6: Implement foreground import service**
  Accept the persisted content URI via Intent, expose progress notification with song count/bytes/stage, support Resume and Discard through repository actions, and never schedule via WorkManager/JobScheduler/PlatformScheduler.

- [ ] **Step 7: Run GREEN**
  Run focused tests plus `:app:assembleCarDebug`.
  Expected: PASS.

- [ ] **Step 8: Commit**
  `git add app/src/car && git commit -m "feat(car): add resumable portable import"`
### Task 8: Finish Car library, search, import, settings, and diagnostics surfaces

**Files:**
- Create: `app/src/car/kotlin/com/metrolist/music/car/ui/screens/CarDownloadsScreen.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/ui/screens/CarLibraryScreen.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/ui/screens/CarSearchScreen.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/ui/screens/CarMoreScreen.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/ui/screens/CarImportScreen.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/ui/screens/CarDiagnosticsScreen.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/ui/CarArtworkResolver.kt`
- Create: `app/src/car/kotlin/com/metrolist/music/car/diagnostics/CarDiagnosticsSnapshot.kt`
- Test: `app/src/testCar/kotlin/com/metrolist/music/car/ui/CarArtworkResolverTest.kt`
- Test: `app/src/testCar/kotlin/com/metrolist/music/car/diagnostics/CarDiagnosticsSnapshotTest.kt`

**Interfaces:**
- Downloads uses the existing `database.downloadedSongs(...)` flow and PlayerConnection queue actions.
- Search reuses current LocalSearch/OnlineSearch view-model/domain logic but renders Car-specific result rows and touch targets.
- Artwork order: `filesDir/car-artwork/<songId>.img` -> remote thumbnail URL -> placeholder.
- Diagnostics snapshot includes Car version, API/device strings, actual viewport pixels, reported density, storage, Room downloaded count, Media3 completed count, last import state, and latest raw key observation.
- [ ] **Step 1: Write RED artwork/diagnostics tests**
  Assert local artwork wins offline, remote URL is fallback, missing both yields placeholder; assert diagnostics includes `BuildConfig.VERSION_NAME` and never includes auth cookies/tokens.

- [ ] **Step 2: Run RED**
  Run: `./gradlew.bat :app:testCarDebugUnitTest --tests "*CarArtworkResolverTest" --tests "*CarDiagnosticsSnapshotTest"`
  Expected: FAIL.

- [ ] **Step 3: Implement Downloads and Library**
  Use car-sized rows, obvious selected/playing state, simple sort/filter affordances, and queue actions; no phone-only swipe/long-press dependency.

- [ ] **Step 4: Implement Search and online state**
  Local search must remain available offline. Online search/browse must show a clear offline state instead of indefinite loading.

- [ ] **Step 5: Implement Import/More/Diagnostics**
  Launch `ActivityResultContracts.OpenDocument`, request persistable read permission, start CarImportService, show validation/import/resume/discard status, and expose physical-button diagnostics.

- [ ] **Step 6: Run all Car tests and assembly**
  Run: `./gradlew.bat :app:testCarDebugUnitTest :app:assembleCarDebug`
  Expected: PASS.

- [ ] **Step 7: Commit**
  `git add app/src/car app/src/testCar && git commit -m "feat(car): complete car library and diagnostics UI"`
### Task 9: Add contract/regression gates and package Car 0.1.0

**Files:**
- Create after all automated gates pass: `docs/superpowers/releases/car-release-history.md`
- Do not modify Windows product code unless a reproducible compatibility failure is proven.

**Interfaces:**
- Produces: a verified Car APK whose embedded version is exactly `0.1.0`.
- Produces: release-history entry only when the APK is intentionally handed to the user.

- [ ] **Step 1: Run portable/Windows regression tests**
  Run: `./gradlew.bat :desktop-core:test :desktop-export:test :desktop-app:test :desktop-app:createDistributable :desktop-app:packageMsi`
  Then run: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\\scripts\\verify-windows-package.ps1`
  Expected: BUILD SUCCESSFUL and `WINDOWS_PACKAGE_VERIFY_OK`; `git diff --name-only 9ac2df388..HEAD -- desktop-portable` must remain empty except Car-plan/spec documentation is outside that module.

- [ ] **Step 2: Run full standard Android regression gate**
  Run: `./gradlew.bat --no-daemon --max-workers=1 :app:testFossDebugUnitTest :app:testGmsDebugUnitTest :app:testIzzyDebugUnitTest :app:assembleFossDebug :app:assembleGmsDebug :app:assembleIzzyDebug`
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run full Car gate**
  Run: `./gradlew.bat --no-daemon --max-workers=1 :app:testCarDebugUnitTest :app:assembleCarDebug`
  Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Verify packaged identity**
  Inspect the produced APK with Android build tools and assert package is the Car package/debug variant, versionName is `0.1.0`, requested orientation is landscape, launcher is CarMainActivity, and MainActivityAlias is not a launcher.

- [ ] **Step 5: Verify repository scope**
  Run: `git status --short --branch` and `git diff --stat 8f5e38883..HEAD`.
  Expected: no unrelated user files; Windows changes absent except documentation that explicitly records Car release state.

- [ ] **Step 6: Prepare release note without installing**
  If all gates pass, add `docs/superpowers/releases/car-release-history.md` with Car 0.1.0 status `built, awaiting physical head-unit validation`. Do not claim physical approval.

- [ ] **Step 7: Commit local release metadata**
  Run: `git add docs/superpowers/releases/car-release-history.md && git commit -m "docs(car): record Car 0.1.0 test build"`. Do not push.

- [ ] **Step 8: Stop for explicit installation authorization**
  Report APK path, SHA-256, embedded version/package, automated gate results, and the physical smoke checklist. Do not install to the head unit until the user explicitly authorizes it.
## Physical Validation After Explicit Authorization

This is not performed during implementation without a separate user go-ahead.

1. Install the already-verified Car APK.
2. Confirm landscape sizing and touch targets on the AC8257 head unit.
3. Open Input Diagnostics and record real keyCode/scanCode/source values for panel and steering-wheel buttons.
4. Confirm Volume+/Volume-/Mute remain entirely system-controlled.
5. Confirm standard Play/Pause/Next/Previous reach MediaSession.
6. Import a small real Windows 0.2.2 backup from USB.
7. Disable network and verify artwork, playback, seek, queue, next/previous.
8. Power-cycle/sleep-restart while playing; verify same queue/track/position resumes playing.
9. Repeat while paused; verify it returns paused.
10. Interrupt an import and verify Resume works.
11. Import the large real library and verify counts/storage/playback.
12. Re-import the same backup and verify no duplication.
13. Only after observing non-standard physical keys, add a versioned mapping in a subsequent Car build.
