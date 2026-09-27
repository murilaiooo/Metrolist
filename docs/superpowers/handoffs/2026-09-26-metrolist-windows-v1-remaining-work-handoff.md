# MetroList Windows V1 — Remaining Work Handoff

Date: 2026-09-26
Machine: DESKTOP-OEAGULM
Worktree: `I:\codex\metrolist-windows\.worktrees\windows-v1`
Branch: `feature/windows-v1`
HEAD: `8b5da69b0f2576ff0030060776026c3aa1f94126`

## Current user-confirmed state

The user manually tested the current app-image and confirmed:

- song downloads now work;
- live download feedback is visible;
- playlist downloads work;
- playlist/offline export works;
- the app still feels very raw and early, but the core flow is now a good working base.

This manual confirmation is more important than re-running the same download diagnostics again.

Current app-image:
`desktop-app\build\compose\binaries\main\app\MetroListWindows\MetroListWindows.exe`

Important: the Desktop shortcut still points to the older installed build:
`C:\Program Files\MetroListWindows\MetroListWindows.exe`
Do not assume the shortcut is using the latest worktree build.
## Download bug that was fixed

The earlier stuck state was:

`DOWNLOADING / 0 B / total=null`

The same item `IurTYZu_Pqg` had a healthy resolver and HTTP transport outside the queue, proving YouTube/account/network were not the root cause.

The fixes aligned the Windows downloader more closely with the original MetroList Android behavior:

- recovered downloads no longer resume before the saved YouTube session is restored and validated;
- queue startup can defer recovered work until authentication is ready;
- stream resolution for downloads now uses `allowBoundedRange=false`, matching the original MetroList download path;
- if InnerTubeX does not provide content length, the fetcher probes `Range: bytes=0-0` and reads the total from `Content-Range`;
- live bytes/total/speed propagate fetcher -> worker -> queue -> SQLite -> StateFlow -> Compose;
- cancellation/removal no longer recreates removed jobs as FAILED;
- cancelled blocking transfers clean final and `.part` files;
- failed auth validation restores the previous global YouTube session instead of contaminating it.

A real isolated recovery probe using the same song and real production pipeline completed:
`0 B -> total 3,159,142 -> 1 MiB -> 2 MiB -> COMPLETED`
in about 812 ms.
## Important current verification evidence

The current worktree has also passed the following checks during this conversation:

- Windows module tests were green after the download/auth/state fixes.
- Search and playlist stale-response races were reproduced and fixed.
- Real queue -> SQLite -> backup probe passed:
  `QUEUE_EXPORT_OK state=COMPLETED bytes=3856993 backupBytes=3862195`.
- Final 3-song car backup probe passed:
  `FINAL_CAR_PROBE_OK songs=3 mediaBytes=19061996 backupBytes=19074264`.
- Zip64 probe passed with 4,296,015,872 logical bytes.
- The current app-image was rebuilt successfully with `:desktop-app:createDistributable`.
- The user then manually confirmed real song download and playlist export work.

Do not redo these checks reflexively. Re-run a targeted check only when a new change can affect that area, or before final release packaging.

The original one-song backup compatibility gate had already passed on the real car MetroList app in an earlier phase.
## Git state: preserve it

There is a large intentional uncommitted Windows diff containing the now-working fixes and review work.

Do **not** run reset/clean/checkout over it.

Current modified areas include:

- `desktop-app`: App UI, AppState race fixes, DesktopRuntime startup/auth/download integration, tests/probes;
- `desktop-core`: auth isolation, media fetcher/progress/content-length fallback, worker cancellation, queue resume/removal, stream hints, tests;
- `desktop-portable`: writer/validator hardening, Zip64/final-car/queue probes and tests.

There are also untracked tests/probes and the handoff directory.

Before editing in the next conversation:
1. read this handoff;
2. inspect `git status --short`;
3. inspect the relevant diff for the feature being changed;
4. preserve the working download/export behavior.

Do not push or merge unless the user explicitly asks.
## User-requested working style from now on

The user explicitly asked to stop long verification loops.

Preferred loop:

1. identify one missing feature or concrete bug;
2. compare with the original MetroList implementation when relevant;
3. implement the smallest coherent fix/feature;
4. build the current app-image if needed;
5. ask the user to test it manually;
6. use the user's result to decide the next step.

Only run technical tests when they are necessary to avoid handing over a broken build or when the changed area is risky.

Do not spend long stretches repeatedly proving already-working behavior.

For YouTube/download/player behavior, use the original MetroList Android source as the primary reference before inventing Windows-specific behavior.
## Remaining planned V1 work

The core vertical slice works, but the product/UI portion of the original V1 plan is incomplete.

### Priority 1 — Complete the desktop player

Current player has:
- play;
- pause/resume;
- seek;
- volume;
- local-first source selection.

Still planned and missing:
- previous;
- next;
- playback queue;
- automatic advance when a track ends;
- shuffle;
- repeat modes;
- clearer end/error state;
- richer now-playing UI.

This should be the next implementation phase because it will immediately make the app feel less like a prototype.
### Priority 2 — Artwork and richer presentation

Artwork caching/export was planned but is not implemented.

Current evidence:
- there is no desktop artwork cache implementation;
- backup metadata currently writes `thumbnailArtworkPath = null`.

Implement:
- fetch/cache thumbnail artwork locally when available;
- stable SHA-256 filename from thumbnail URL;
- display artwork in song rows, playlist/album screens, Downloads, and player;
- export artwork entries into `.metrolistbackup`;
- keep artwork failure non-fatal exactly as the spec requires.

Use the Android/original MetroList presentation and the known-good backup as references.
### Priority 3 — Finish navigation/catalog coverage

The original V1 expected Home, Search, Library, albums, artists, and playlists.

Current implementation is narrower:
- Search gateway currently requests only `FILTER_SONG`;
- Library gateway currently loads liked playlists;
- playlist detail exists;
- dedicated album and artist flows are not implemented.

Implement:
- richer Search results/categories;
- album screens and tracks;
- artist screens and releases/top songs;
- Library sections beyond playlists where supported by existing MetroList/InnerTube models;
- consistent navigation from Home/Search/playlist rows into album/artist destinations.

Reuse existing MetroList `YouTube` models/pages/endpoints instead of creating a new API layer.
### Priority 4 — Downloads/playlist UX

The download engine is working. Do not rewrite it without a concrete regression.

Improve the product layer around it:

- playlist-level progress such as X/Y complete;
- clearer playlist download state;
- cancel a playlist batch;
- Retry failed for all failed items;
- bulk remove/cleanup actions;
- optional filters/search in Downloads;
- "Remove downloaded" on playlist detail;
- clearer queued/active/completed grouping;
- better empty/loading/error states.

Preserve:
- max 3 active transfers;
- per-song failure isolation;
- dedupe by video ID;
- restart recovery;
- export of every completed song regardless of source playlist.
### Priority 5 — Export UX

The export backend is strong, but the UI is still minimal.

Current behavior:
- user clicks `Export all offline downloads`;
- chooses a file;
- backend validates before final promotion.

Still planned:
- pre-export summary with song count;
- estimated audio/artwork size;
- selected destination;
- visible export progress/status;
- clearer success/failure feedback;
- actionable disk-space/validation errors.

Do not weaken the existing writer/validator safety rules:
- completed items only;
- source file length must match;
- destination space check;
- write to `.part`;
- validate archive before final rename;
- support Long sizes and Zip64.
### Priority 6 — Authentication fallback and persistence hardening

Backend support for advanced session parsing exists, but the desktop UI does not expose the planned fallback.

Implement:
- an Advanced Session fallback in the signed-out/login UI;
- clear validation errors without exposing secrets;
- persist only after validation succeeds.

Also prepare the Windows-owned database for real released versions:
- explicit schema version/migration path;
- preserve existing offline records across upgrades;
- keep secrets out of SQLite.

Do not change the Android database schema/version.
### Priority 7 — UI structure and visual polish

The current UI is functional but concentrated heavily in one `App.kt` and visually basic.

After the product flows above are working, improve:

- split major screens/components cleanly;
- artwork-led rows/cards;
- better loading and empty states;
- icons and clearer actions;
- confirmation for destructive actions;
- context menus where useful;
- better desktop spacing/responsiveness;
- richer player bar;
- consistent success/error messaging;
- a more finished MetroList-like information hierarchy without blindly cloning Android layout.

Do not spend time on cosmetic polish before the missing V1 flows above are usable.
### Priority 8 — Final installed-build/release gates

Only after the remaining V1 product work is done:

- rebuild the app-image;
- package the updated MSI;
- install/update the Program Files build;
- update/recreate the Desktop shortcut so it points to the final installed build;
- verify saved-session restore from the installed app;
- verify one fresh song download and one playlist download from the installed app;
- verify offline playback of a completed local song;
- generate a fresh final backup from the finished build;
- run the final clean-car import spot check;
- run Android `:app:assembleFossDebug` after the final repository/build changes.

Do not repeatedly run this full matrix after every UI feature.
## Keep these out of V1 unless the user explicitly expands scope

Do not add these just because the Android MetroList has them:

- lyrics;
- Last.fm;
- Discord RPC;
- Listen Together;
- Cast;
- Android Auto;
- widgets;
- alarms/sleep timer;
- Wrapped/statistics;
- AI translation;
- equalizer;
- advanced themes;
- podcasts;
- upload-to-YouTube;
- advanced local playlist editing.

The goal is a polished Windows companion for browse/play/download/export, not a full Android feature-for-feature port.
## Safety and process constraints

- Work only in `I:\codex\metrolist-windows\.worktrees\windows-v1` unless the app itself needs its normal LocalAppData/installed path.
- Do not delete user files.
- Do not reset/clean away the current uncommitted work.
- Do not modify unrelated folders or software.
- Keep Android behavior and schema untouched.
- No yt-dlp, no transcoding, no replacement downloader stack.
- Continue using MetroList/InnerTube/InnerTubeX behavior as the reference.
- No push/merge unless explicitly requested.
- Prefer small coherent feature batches over giant review loops.
- After each visible feature batch, build the current app-image and ask the user to test.
## Prompt for the next conversation

> Continue the MetroList Windows project from
> `I:\codex\metrolist-windows\.worktrees\windows-v1`.
> First read
> `docs/superpowers/handoffs/2026-09-26-metrolist-windows-v1-remaining-work-handoff.md`.
> Also read the V1 design/spec and implementation plan.
> Invoke Superpowers as applicable, especially using-superpowers,
> test-driven-development for non-trivial fixes, systematic-debugging for real failures,
> and verification-before-completion before final release claims.
>
> The core flow is now user-confirmed working: song downloads work with live feedback,
> playlist downloads work, and export works. Do not rewrite the working download/export
> architecture without a concrete regression.
>
> Execute the remaining V1 product work in this priority order:
> 1. complete the player (queue, next/previous, auto-advance, shuffle, repeat);
> 2. artwork caching/display/export;
> 3. album/artist/richer Search and Library navigation;
> 4. playlist/download UX improvements;
> 5. export summary/progress UX;
> 6. Advanced Session login fallback and DB migration hardening;
> 7. UI polish/refactor;
> 8. final MSI/install/shortcut/Android/car gates.
>
> Work in short product batches. For each batch: inspect the original MetroList behavior,
> implement the coherent feature, build only what is needed, then ask me to test it.
> Avoid long repetitive verification loops. Do not delete user files, do not reset the
> intentional uncommitted diff, do not push/merge, and do not touch unrelated PC files.
