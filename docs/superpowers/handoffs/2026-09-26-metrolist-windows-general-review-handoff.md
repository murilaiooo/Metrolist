# MetroList Windows — General Review Handoff

Date: 2026-09-26
Machine: DESKTOP-OEAGULM
Project root: `I:\codex\metrolist-windows`
Worktree: `I:\codex\metrolist-windows\.worktrees\windows-v1`
Branch: `feature/windows-v1`
HEAD: `8b5da69b0f2576ff0030060776026c3aa1f94126`
Upstream merge-base: `28fa8f042bfabb880bdecc0d9fa37e86cf72b90e`
Branch is currently 13 commits ahead of `upstream/main`.

## User goal

Do a full technical review of the Windows app and fix everything required for it
to work reliably end-to-end. The primary product goal is:

- sign in to YouTube Music;
- browse/search/library/playlists;
- play songs;
- download individual songs and entire playlists;
- show complete live feedback for all download activity;
- preserve offline downloads;
- export all completed offline songs to a real `.metrolistbackup`;
- import that backup into the untouched MetroList app in the car;
- work as a normal standalone Windows application.

Do not expand scope into unrelated MetroList features.

## Superpowers workflow for the next conversation

Invoke these before doing code changes:

1. `using-superpowers`
2. `systematic-debugging` for every failing behavior
3. `requesting-code-review` for a fresh review of the whole Windows diff
4. `test-driven-development` for fixes
5. `verification-before-completion` before any success claim

Important process rules:

- root-cause first; do not guess;
- review the current diff before editing;
- use small, evidence-driven fixes;
- stop after 3 failed fix attempts and question the architecture;
- keep Android MetroList green;
- do not change Android DB schema/version;
- do not push/merge unless explicitly requested;
- do not delete user files;
- do not touch unrelated folders/software on the PC;
- runtime/dev artifacts stay inside this project unless the app itself needs
  `%LOCALAPPDATA%\MetroListWindows` or its installed Program Files directory.

The user explicitly wants careful execution with medium-sized steps, not tiny
micro-steps and not huge loop-prone batches.

## What is already working

Verified previously:

- WebView2 YouTube Music login helper works.
- DPAPI session storage works.
- InnerTube/InnerTubeX stream resolution works.
- HIGH audio quality maps to the existing MetroList/InnerTubeX high-quality selector.
- One-song real download worked.
- Persistent SQLite queue exists with max 3 concurrent jobs.
- Resume via `.part` and HTTP range chunks exists.
- 403/410/416 can trigger fresh stream resolution.
- Player uses libmpv and passed a real WebM/Opus smoke test.
- Home/Search/Library/playlist detail UI works.
- Offline backup writer is compatible with the known-good car backup profile.
- A one-song generated backup was already successfully imported and played offline in the car.
- A three-song generated backup passed internal validator and NanaZip/7-Zip checks.
- Zip64 was tested with 4,296,015,872 logical bytes and passed validation.
- Standalone app-image and MSI packaging exist.
- The installed app was previously fixed to include `java.net.http` in the jlink runtime.
- Android `:app:assembleFossDebug` passed after the Windows work.

Known install path:
`C:\Program Files\MetroListWindows\MetroListWindows.exe`

Desktop shortcut:
`D:\ESTE COMPUTADOR\Desktop\MetroList Windows.lnk`

## Current unresolved bug

User reported that song playback works, but downloads either appear to do
nothing or provide insufficient feedback. A download was reproduced in the UI
stuck at:

`Downloading · 0 B · calculating speed...`

The exact stuck item during diagnosis had:

- videoId: `IurTYZu_Pqg`
- persisted bytes: `0`
- persisted total: `null`

A dedicated diagnostic probe then tested the SAME item outside the queue and
proved the external layers are healthy:

- stream resolver: 192 ms
- selected itag: 251
- content length: 3,159,142 bytes
- client/profile: `VISIONOS / VISIONOS_0_1__nopo`
- first 1 MiB HTTP range: status 206
- first range bytes: 1,048,576
- first range time: 345 ms

Therefore do NOT blame YouTube, the account, or general network connectivity
without new evidence. The failure is somewhere between the queue/worker/fetcher
integration, installed artifact/classpath, or state/progress propagation.

## Current uncommitted work

There are intentional, uncommitted changes for live download feedback:

- `desktop-app/build.gradle.kts`
- `desktop-app/src/main/kotlin/com/metrolist/desktop/App.kt`
- `desktop-app/src/main/kotlin/com/metrolist/desktop/DesktopRuntime.kt`
- `desktop-core/src/main/kotlin/com/metrolist/desktop/media/ResolvedMediaFetcher.kt`
- `desktop-core/src/main/kotlin/com/metrolist/desktop/offline/MetroListOfflineWorker.kt`
- `desktop-core/src/main/kotlin/com/metrolist/desktop/offline/OfflineModels.kt`
- `desktop-core/src/main/kotlin/com/metrolist/desktop/offline/PersistentOfflineQueue.kt`
- progress-related core tests
- two portable probe call sites
- new `DownloadStageProbeMain.kt`

The live-progress changes currently add:

- `MediaTransferProgress(bytesDownloaded, totalBytes, bytesPerSecond)`
- progress callbacks from range fetcher → worker → persistent queue
- `bytesPerSecond` in `OfflineRecord`
- persistence + StateFlow refresh on each progress update
- global download activity bar
- per-song status text/progress bars
- queued/downloading/downloaded/retry button labels
- size, percent, speed, codec, bitrate and sample-rate display
- automatic started/completed/failed messages

Core progress tests passed, and `desktop-app:test` / compile passed after integration.

## Important installation/classpath investigation

At one point the installed `Program Files\MetroListWindows\app` directory had
both old and new desktop-core JARs. This looked like a plausible classpath cause.

However, the CURRENT comparison between the installed app directory and the
current app-image shows only `.package` as an extra top-level file. Therefore:

**Do not treat stale JARs as a confirmed root cause. Re-check current state.**

A prior attempt to rename the installed `app` directory to a backup failed
with Access Denied. No files were deleted. A subsequent copy operation copied
new files over the existing folder.

At the moment of this handoff, no MetroList application process was returned by
the process check. Re-confirm this before manipulating the installed directory.

The next conversation should prefer testing from the current app-image first,
then update/install only after the app-image behavior is proven.

## Required first actions in the new conversation

1. Read upstream `AGENTS.md`.
2. Read:
   - `docs/superpowers/specs/2026-09-26-metrolist-windows-v1-design.md`
   - `docs/superpowers/plans/2026-09-26-metrolist-windows-v1.md`
   - this handoff.
3. Inspect `git status`, committed diff from upstream base, and uncommitted diff.
4. Use Superpowers `requesting-code-review` to review the entire Windows change set.
5. Categorize findings into Critical / Important / Minor.
6. Fix Critical and Important findings before polishing.
7. Reproduce the download bug from the CURRENT app-image, not an uncertain old install.
8. Use systematic debugging to isolate:
   - queue state transition;
   - worker invocation;
   - resolver result;
   - progress callback invocation;
   - SQLite update;
   - StateFlow emission;
   - Compose rendering.
9. Only after the app-image passes, refresh the installed build/shortcut.
10. Do not claim 100% until the end-to-end matrix below is freshly verified.

A useful immediate hypothesis test is to restart the current app-image after the
stuck record is recovered from DOWNLOADING → QUEUED and see whether the new
progress callback path advances the same record beyond 0 B.

## End-to-end verification matrix

Before calling the app complete, freshly verify all of these:

- startup from installed Desktop shortcut;
- saved session restores correctly;
- fresh login works;
- Home loads;
- Search returns songs;
- Library returns playlists;
- playlist opens all pages/continuations;
- online playback works;
- completed offline playback prefers local media;
- single-song download visibly enters Preparing/Queued/Downloading;
- percent, downloaded bytes, total bytes and speed update live;
- completion is visible immediately;
- failure shows a human-readable error and Retry;
- retry works;
- app restart recovers/resumes interrupted downloads;
- max concurrency remains 3;
- entire playlist download works and deduplicates songs;
- Downloads screen totals are correct;
- Remove removes record/media intentionally;
- Export all includes every and only COMPLETED item;
- generated backup passes PortableValidator and 7-Zip;
- >4 GiB logical Zip64 probe remains green;
- MSI/app-image package verifier passes;
- Android `:app:assembleFossDebug` remains green;
- final backup is spot-checked on the real car MetroList offline.

Do not substitute unit tests for the real UI/download/install checks.

## Key artifacts / references

Known-good user backup:
`I:\codex\metrolist-windows\Metrolist_Offline_Downloads_2026-07-30_20-50.metrolistbackup`

Design spec:
`docs/superpowers/specs/2026-09-26-metrolist-windows-v1-design.md`

Implementation plan:
`docs/superpowers/plans/2026-09-26-metrolist-windows-v1.md`

Progress ledger:
`.superpowers/sdd/2026-09-26-metrolist-windows-v1/progress.md`

The known-good backup is read-only compatibility evidence. Do not overwrite it.

## Prompt for the next conversation

Use the full prompt below as the first message:

> Continue the MetroList Windows project from
> `I:\codex\metrolist-windows\.worktrees\windows-v1`.
> First read
> `docs/superpowers/handoffs/2026-09-26-metrolist-windows-general-review-handoff.md`.
> Invoke Superpowers before acting: using-superpowers, systematic-debugging,
> requesting-code-review, test-driven-development, and
> verification-before-completion as applicable.
> Do a complete technical review of the Windows app and fix every Critical and
> Important issue needed for reliable end-to-end operation. Start by reviewing
> the full committed + uncommitted Windows diff, then reproduce and root-cause
> the current download stuck-at-0-B issue from the current app-image. Do not
> guess, do not make unrelated changes, do not delete user files, and do not
> push/merge. Keep Android green. The goal is a Windows app that reliably
> logs in, browses, plays, downloads with full live visual feedback, resumes,
> downloads playlists, exports all completed songs to a valid
> `.metrolistbackup`, packages cleanly, and passes the final car import test.
