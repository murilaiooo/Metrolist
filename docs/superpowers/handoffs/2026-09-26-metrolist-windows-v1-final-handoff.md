# MetroList Windows V1 Final Handoff - 2026-09-26

## Start here

Continue the MetroList Windows project from:

`I:\codex\metrolist-windows\.worktrees\windows-v1`

Read this handoff first, then read the approved V1 design/spec and implementation plan if needed:

- `docs/superpowers/specs/2026-09-26-metrolist-windows-v1-design.md`
- `docs/superpowers/plans/2026-09-26-metrolist-windows-v1.md`

Invoke Superpowers as applicable, especially:

- `using-superpowers`
- `systematic-debugging` for any real failure
- `test-driven-development` for regressions
- `verification-before-completion`

Do not reset or clean the intentional uncommitted diff. Do not push or merge unless the user explicitly asks. Do not delete user files. Do not touch unrelated PC files.

## Executive state

The Windows V1 product work is complete and user-validated through Priority 7. A final technical review was performed before closure. No known Critical or Important issue remains after that review.

The only meaningful product gate still outside the computer is the final physical car import test of a real `.metrolistbackup`.
## Final installed package

Final package version: **0.1.2**

MSI:

`I:\codex\metrolist-windows\.worktrees\windows-v1\desktop-app\build\compose\binaries\main\msi\MetroListWindows-0.1.2.msi`

Installed executable:

`C:\Program Files\MetroListWindows\MetroListWindows.exe`

Desktop shortcut:

`MetroList Windows.lnk` -> `C:\Program Files\MetroListWindows\MetroListWindows.exe`

The separate development shortcut still exists:

`MetroList Windows - TESTE.lnk` -> current app-image in the worktree.

The MSI upgrade from installed **0.1.1** to **0.1.2** was tested with silent `msiexec` and returned exit code 0. Windows registry then reported MetroListWindows version 0.1.2.

The installed `desktop-app`, `desktop-core`, and `desktop-export` JARs were SHA-256 compared against the freshly built app-image and were identical.

Installed-package checks all returned 0:

- auth-helper `--self-test`
- auth-helper `--runtime-check`
- MetroListWindows `--startup-check`
- MetroListWindows `--runtime-open-check`
## User-confirmed working product flow

The user manually confirmed the following through successive product batches:

- normal YouTube Music sign-in
- Home/Search/Library navigation
- song playback
- playback queue
- Previous/Next
- auto-advance
- shuffle
- repeat modes
- richer now-playing/player UI
- artwork display/cache
- albums/artists/richer catalog navigation
- single-song downloads with live progress
- playlist downloads
- up to 3 concurrent downloads
- retry/cancel/remove flows
- download filters/search/grouping
- download progress and speed feedback
- export of all completed offline downloads
- export review summary and progress phases
- backup validation/finalization UX
- database migration to schema v1
- final UI polish/refactor

The user explicitly said the final polished UI was "perfeito".

Do not rewrite these working areas unless there is a concrete reproduced regression.
## Important issues found and fixed in final review

### 1. Retry completion race

A real race existed in `PersistentOfflineQueue`: if the UI retried a record immediately after it became `FAILED`, while the old job was still completing, `launchIfNeeded()` could see the old job as active and refuse to start the retry. The record could then remain `QUEUED` forever.

A deterministic regression test was added:

`retryTriggeredWhileFailedJobIsStillCompletingIsNotLeftQueued`

The test failed before the fix and passed afterward.

The fix makes `invokeOnCompletion` remove the old job and relaunch the video if the store still says `QUEUED`.

### 2. Queue/store close race

A real lifecycle race existed when closing the app with an active blocking transfer. `queue.close()` cancelled the scope but late completion callbacks could still call `refresh()` or inspect the store after `OfflineStore` had already been closed.

A regression test was added:

`closingQueueBeforeStoreDoesNotTouchClosedStoreFromLateJobCompletion`

The test failed before the fix and passed afterward.

The queue now has an atomic `closed` guard. New launches, refreshes, and completion callbacks stop touching the store once the queue is closed.
### 3. MSI version collision

The machine already had MetroListWindows **0.1.1** installed, while the newly reviewed MSI was also configured as 0.1.1 even though its internal JARs were different.

This was treated as an Important packaging defect because Windows Installer upgrades would be ambiguous.

`desktop-app/build.gradle.kts` was changed to:

`packageVersion = "0.1.2"`

A new `MetroListWindows-0.1.2.msi` was generated and installed over 0.1.1. The upgrade completed successfully and the installed application now reports version 0.1.2.

## Final verification evidence

Fresh desktop gate after review fixes:

`./gradlew.bat :desktop-core:test :desktop-export:test :desktop-app:test :desktop-app:createDistributable :desktop-app:packageMsi`

Result: **BUILD SUCCESSFUL**.

Package verifier:

`powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-windows-package.ps1`

Result: **WINDOWS_PACKAGE_VERIFY_OK**.

`git diff --check` has no whitespace errors. It only emits normal Windows LF/CRLF conversion warnings.
## Android gate

Android was verified after the Windows review.

The first parallel multi-variant run triggered a JVM crash in a Gradle test executor during Izzy:

- `EXCEPTION_ACCESS_VIOLATION`
- problematic frame inside `jvm.dll`
- Temurin JDK 21.0.12.1
- crash log: `app/hs_err_pid15120.log`
- replay log: `app/replay_pid15120.log`

This was isolated as an environment/JVM concurrency issue, not a MetroList code regression.

Izzy was rerun alone with:

`./gradlew.bat --no-daemon --max-workers=1 :app:testIzzyDebugUnitTest`

Result: **BUILD SUCCESSFUL**.

Then all three Android variants were rerun serially with one worker:

`./gradlew.bat --no-daemon --max-workers=1 :app:testFossDebugUnitTest :app:testGmsDebugUnitTest :app:testIzzyDebugUnitTest :app:assembleFossDebug :app:assembleGmsDebug :app:assembleIzzyDebug`

Result: **BUILD SUCCESSFUL**, 205 actionable tasks.

Do not treat the earlier JVM crash as an app regression unless it reproduces deterministically in a clean serial run.
## User data and install-state checks

The final MSI upgrade preserved the user's local data.

Current app-data root:

`%LOCALAPPDATA%\MetroListWindows`

Confirmed present after the 0.1.2 upgrade:

- DPAPI session file: `auth\session.bin`
- offline database: `desktop\offline.db`
- offline media directory: `desktop\media`

The final check intentionally did not decrypt or print session credentials.

At the time of the final check:

- `session.bin` existed
- `offline.db` existed
- the media directory contained offline `.media` files

Do not infer the UI's completed-download count from raw media-file count because stale/orphaned/local test files may exist. Use the database/UI as source of truth.

The current desktop database schema version is **1**, with explicit v0 -> v1 migration that preserves existing `offline_records`.
## UI/refactor state

Priority 7 was completed and user-approved.

The former monolithic `App.kt` was reduced from roughly 2,700+ lines to about 580 lines. UI was split conservatively without changing the validated behavior.

Important UI files now include:

- `App.kt`
- `ChromeUi.kt`
- `CatalogScreens.kt`
- `CatalogRows.kt`
- `DownloadsScreen.kt`
- `DownloadComponents.kt`
- `PlayerUi.kt`
- `UiTheme.kt`
- `UiUtils.kt`
- `DownloadUiModels.kt`

The app has a polished desktop navigation rail/sidebar, light/dark theme support, consistent rounded surfaces/cards, artwork treatment, download filter chips, and improved player controls.

The user manually reviewed the visual result and said it was perfect.

Do not collapse these files back into `App.kt` or perform a broad UI rewrite without a concrete need.
## Git/worktree state

Worktree:

`I:\codex\metrolist-windows\.worktrees\windows-v1`

Branch:

`feature/windows-v1`

HEAD at handoff:

`8b5da69b0`

There is a large intentional uncommitted diff spanning desktop app/core/export plus new tests, probes, UI split files, and handoff docs.

Do not run `git reset`, `git clean`, or otherwise discard the diff.

Do not push or merge unless the user explicitly requests it.

The final review intentionally did not create a commit.

The user has repeatedly instructed that only this development project may be touched on the PC. Keep all commands tightly scoped to the worktree or the app's own install/app-data paths when necessary.
## Real backup / car import status

The user manually completed a real export after the new export UX was implemented and said they placed the backup in a folder for later use.

That real export visually completed successfully in the app.

During the final review, the obvious recursive search under `I:\codex` surfaced the known probe/export backups but did not unambiguously identify the user's manually exported backup by name.

Therefore:

- do not assume a specific real-backup path from this handoff
- if the next task is the car import, first locate the exact file the user intends to use
- do not overwrite or modify that file
- validate/read-only if useful before the physical import
- the physical car import itself must be performed by the user

Previously verified synthetic/probe coverage already includes:

- queue -> SQLite -> backup
- final 3-song car-format probe
- Zip64 >4 GiB probe
- backup validator
- normal app export path

Do not rerun the giant probes reflexively unless a regression or the user specifically requests them.
## V1 scope reminder

Keep the approved V1 exclusions unless the user explicitly starts post-V1 work:

- lyrics
- Last.fm
- Discord RPC
- Listen Together
- Cast
- Android Auto
- widgets
- alarms/sleep timer
- Wrapped/stats
- AI translation
- EQ
- advanced themes
- podcasts
- upload to YouTube
- advanced local playlist editing

## Recommended next action

If the user wants to finish the V1:

1. Use the installed **MetroList Windows** 0.1.2, not the TESTE shortcut.
2. Locate the exact real `.metrolistbackup` intended for the car.
3. Optionally run a read-only validator against that exact file if the user wants one final computer-side check.
4. Have the user import it into the car.
5. Confirm songs/artwork/metadata appear and a few tracks play.
6. If the car import succeeds, V1 can be considered fully closed.
7. Only after the user asks, discuss committing/tagging/pushing/release distribution.

If the car import fails, do not guess. Capture the exact car error/behavior, invoke systematic debugging, inspect the backup structure against the known-good probe format, add a regression test, and make the smallest fix possible.
## Suggested prompt for the next conversation

> Continue the MetroList Windows project from `I:\codex\metrolist-windows\.worktrees\windows-v1`. First read `docs/superpowers/handoffs/2026-09-26-metrolist-windows-v1-final-handoff.md`. Invoke Superpowers as applicable. The Windows V1 has completed product work and final technical review, with no known Critical/Important issue remaining. The final installed package is MetroListWindows 0.1.2 in `C:\Program Files\MetroListWindows`, and Android Foss/GMS/Izzy are green. Do not rewrite validated download/export/player architecture, do not reset the intentional uncommitted diff, do not delete user files, and do not push/merge. The remaining external gate is the real `.metrolistbackup` import in the car. Help me locate/validate the exact real backup if needed, then handle the car-import result. If it fails, use systematic debugging and TDD rather than guessing.
