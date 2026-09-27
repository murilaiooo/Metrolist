# MetroList Car APK — Task 6 Paused Handoff

> **HISTORICAL SNAPSHOT — SUPERSEDED.** Task 6 was subsequently committed as `ee8fc297b feat(car): import portable media and metadata`. Do not use this file as the current resume point. See `docs/superpowers/reviews/2026-09-27-metrolist-ecosystem-integration-audit.md` and the latest ecosystem handoff.

Date: 2026-09-27
Repository: `I:\codex\metrolist-windows`
Branch: `main`
Reason: user explicitly requested that current work be paused and documented.

## Current baseline

Windows compatibility baseline:
- MetroList Windows 0.2.2
- commit `9ac2df388`
- `desktop-portable` format remains unchanged
- real Windows-generated `.metrolistbackup` compatibility has been validated by the Car validator

Car plan/spec:
- Design: `docs/superpowers/specs/2026-09-27-metrolist-car-apk-design.md`
- Plan: `docs/superpowers/plans/2026-09-27-metrolist-car-apk.md`
- Car version: `0.1.0`
## Completed Car tasks

Task 1 — Car product flavor:
- commit `c5224afaf feat(car): establish Car product flavor`
- separate Car application ID/version line
- dedicated landscape launcher
- Car Cast/updater-disabled boundary
- Car build/manifest tests green

Task 2 — Scheduler and playback restoration:
- commit `fd9720caf feat(car): isolate scheduler and playback restore`
- Car disables PlatformScheduler only for the Car flavor
- Car restores saved play/pause intent, queue, index, and position
- standard Android variants preserve their existing behavior

Task 3 — Viewport and physical input:
- commit `81aebdf62 feat(car): add viewport and input routing`
- deterministic car viewport sizing
- standard media controls remain MediaSession-driven
- volume is not consumed by the app
- vendor/CANBUS mappings require real hardware observation

Task 4 — Car-first playback shell:
- commit `2b7887f58 feat(car): add car-first playback shell`
- car-specific shell/player UI
- no on-screen volume controls
- large transport controls and car-first navigation

Task 5 — Portable backup v1 validation:
- commit `bd4b8fd83 feat(car): validate portable backup v1`
- streaming Car-side reader/validator
- independent Car portable models
- validation of hashes, paths, profile, metadata, span continuity, and storage requirements
- synthetic validator suite passed
- real Windows backup probe passed against:
  `I:\codex\metrolist-windows\Metrolist_Offline_Downloads_2026-09-27_06-58.metrolistbackup`
## Current Task 6 status — PAUSED, NOT COMPLETE

Task 6 is "Add real Media3, Room, and artwork import targets."

The following Task 6 product files currently exist in the working tree but are UNTRACKED and UNCOMMITTED:
- `app/src/car/kotlin/com/metrolist/music/car/importer/CarMedia3ImportTarget.kt`
- `app/src/car/kotlin/com/metrolist/music/car/importer/CarRoomImportTarget.kt`
- `app/src/car/kotlin/com/metrolist/music/car/importer/CarArtworkStore.kt`
- `app/src/car/kotlin/com/metrolist/music/car/importer/CarImportInspector.kt`
- `app/src/car/kotlin/com/metrolist/music/car/importer/CarPortableMappers.kt`

The following Task 6 tests are also UNTRACKED and UNCOMMITTED:
- `app/src/testCar/kotlin/com/metrolist/music/car/importer/CarMedia3ImportTargetTest.kt`
- `app/src/testCar/kotlin/com/metrolist/music/car/importer/CarPortableMappersTest.kt`
- `app/src/testCar/kotlin/com/metrolist/music/car/importer/CarImportTargetsTest.kt`

Do not treat Task 6 as finished. These files must be reviewed and verified before commit.
## What the uncommitted Task 6 implementation currently attempts

`CarMedia3ImportTarget`:
- writes portable cache spans through public Media3 `CacheDataSink`
- removes partial cached resource before reconstruction
- writes content length metadata
- creates a `DownloadRequest` with `requestId = uri = customCacheKey = songId`
- inserts a completed `Download` into `DefaultDownloadIndex`

`CarRoomImportTarget`:
- maps portable song/artist/format metadata into current Room entities
- preserves existing non-portable song state by merging into existing `SongEntity`
- recreates song/artist relationships inside a Room transaction
- marks the song as downloaded

`CarArtworkStore`:
- stores artwork under `car-artwork/<songId>.img`
- uses a `.part` file then rename for atomic-ish replacement
- rejects unsafe song IDs

`CarImportInspector`:
- inspects four layers independently:
  cache
  DownloadIndex
  Room downloaded state
  artwork file

`CarPortableMappers`:
- maps portable metadata without inventing missing audio quality values
- requires real mimeType/codecs/bitrate
- preserves portable timestamps and quality metadata
## Last test state

The first Task 6 RED run found errors in the new test source itself:
- incorrect Room-test setup reference
- stray syntax at the end of `CarImportTargetsTest.kt`

The file visible on disk after that run no longer contained those exact source errors.

A second focused Task 6 run was then started in conservative JVM mode. The user requested a pause before a verified final result was obtained.

Therefore:
- Task 6 does NOT have a fresh GREEN result
- do not commit Task 6 until the focused suite completes successfully
- no claim should be made that Media3/Room/artwork import is complete yet

The focused suite to run on resume is:
`CarMedia3ImportTargetTest`
`CarPortableMappersTest`
`CarImportTargetsTest`
## JVM/build stability issue

This Windows machine has repeatedly experienced native crashes with Temurin JDK 21.0.12 during Gradle/Kotlin/ASM/R8 work.

Observed failures included:
- `EXCEPTION_ACCESS_VIOLATION`
- crashes inside `jvm.dll`
- ParallelGC threads
- C2 compiler threads

The normal repository setting is restored and MUST remain:
`org.gradle.jvmargs=-Xmx4096M -Dkotlin.daemon.jvm.options\="-Xmx4096M" -XX:+UseParallelGC`
`org.gradle.parallel=true`

For focused verification only, conservative temporary settings proved stable:
- max 1 Gradle worker
- no persistent daemon
- configuration cache disabled when needed
- `UseSerialGC`
- `-Xint`
- reduced heap

Because a full-computer freeze can prevent PowerShell `finally` cleanup, always verify `gradle.properties` after any interrupted conservative build.

If it differs from HEAD, restore only that file:
`git restore --source=HEAD -- gradle.properties`

Do not delete caches, reset the repo, clean unrelated files, or use destructive recovery commands.
## Safe resume procedure

1. Open the repository at `I:\codex\metrolist-windows` on `main`.
2. Read this handoff, the Car design spec, and the implementation plan.
3. Invoke Superpowers before acting.
4. Run `git status --short --branch`.
5. Confirm `gradle.properties` matches HEAD before running tests.
6. Preserve all untracked Task 6 files listed above.
7. Review the Task 6 implementation against the tests before editing.
8. Run the three focused Task 6 tests in conservative single-worker mode.
9. If RED, root-cause the actual failure; do not weaken the tests to force GREEN.
10. When the focused suite is GREEN, run `:app:testCarDebugUnitTest` and `:app:assembleCarDebug` in the same safe mode.
11. Only then commit Task 6 as:
    `feat(car): import portable media and metadata`
12. Continue with Task 7 from the implementation plan.

Do not push.
Do not install an APK.
Do not bump Car beyond 0.1.0 merely for local implementation work.
## Repository state at pause

HEAD at documented pause:
`bd4b8fd83 feat(car): validate portable backup v1`

Expected branch relation:
`main...upstream/main [ahead 25]`

Expected working-tree changes:
- only the eight untracked Task 6 source/test files listed above
- this handoff document after it is written
- no intended modification to `gradle.properties`

The earlier temporary JVM override has already been restored to HEAD before writing this handoff.

## Remaining plan after Task 6

Task 7:
- two-pass import coordinator
- durable import journal
- SAF persisted URI
- foreground import service
- resume/discard ownership safety

Task 8:
- Downloads/Library/Search/Import/Settings/Diagnostics surfaces
- local artwork resolver
- head-unit diagnostics

Task 9:
- full Car gate
- standard Android regression gate
- Windows/portable regression gate
- package/version verification
- release-history entry
- stop before physical installation and request explicit authorization

No physical Car APK validation has happened in this phase yet.
