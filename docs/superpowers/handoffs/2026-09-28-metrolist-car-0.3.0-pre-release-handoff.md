# MetroList Car 0.3.0 Pre-Release Handoff

Date: 2026-09-28

## Start here

Repository: `I:\codex\metrolist-windows`

Branch: `main`

HEAD at handoff creation: `da940f041004ead92777192c1a4b0d85bf546667`

Working tree: clean.

Before acting in the next conversation:

1. Read this handoff completely.
2. Read `docs/superpowers/plans/2026-09-28-metrolist-car-0.3.0-ux-performance-feedback.md`.
3. Read `docs/superpowers/testing/metrolist-car-test-lab.md`.
4. Read `docs/superpowers/releases/car-release-history.md`.
5. Invoke Superpowers as applicable, especially systematic-debugging, test-driven-development, verification-before-completion, and requesting-code-review.
6. Do not rewrite working download/import/playback architecture without a concrete regression.
7. Keep the user updated every meaningful batch of actions, not every command and not only at the end.

## Current product direction

MetroList Car is a dedicated Android head-unit UI. The Windows app remains the content preparation/export companion and MetroList Windows 0.2.1 remains the compatibility baseline.

The physical car is not the normal iteration environment. Use the AVD first, then take only an AVD-approved candidate to the car.

Primary AVD:
- `MetroList_Car_API29`
- Android 10 / API 29 / x86_64
- 2 GB RAM primary gate
- 1024x600 @ 160 dpi default
- viewport matrix: 800x480, 1024x600, 1280x720
- do not wipe/delete/recreate the AVD

The app must remain dark regardless of Android light/dark setting.

## User-approved Now Playing layout

The latest player layout is explicitly user-approved. Do not redesign it casually.

Current approved composition:
- album artwork large on the RIGHT;
- playback controls on the left-side content area, but visually balanced rather than shoved to the extreme left;
- elapsed and total track time shown numerically around the progress bar;
- Previous / Play-Pause / Next centered as the primary transport row;
- extra vertical separation between the primary transport row and the secondary-action row to reduce accidental taps;
- all controls have visible button backgrounds, not naked icons;
- secondary row is one centered, evenly-spaced sequence:
  - Dislike (red)
  - Shuffle
  - Queue
  - Repeat
  - Like (green)
- Like/Dislike are not at the outer extremes;
- artwork swipe:
  - deliberate swipe left = next track;
  - deliberate swipe right = previous track;
  - short swipe does nothing;
- large touch targets suitable for in-car use.

Latest UI commit:
- `a96c407ef feat(car): refine driver-safe player controls`

Earlier relevant player commits:
- `3dd92cb5d feat(car): add artwork swipe and player timing`
- `13434cf51 feat(car): move player controls to driver side`
- `b00847188 feat(car): redesign mini player for driving`
- `335e10b54 feat(car): unify automotive track rows`

## Disliked workflow: implemented and tested

The user wanted a durable negative-preference workflow for playlist cleanup and future ChatGPT playlist curation.

Behavior now implemented:
- Like remains the existing MetroList persistent Like behavior and is green.
- Dislike is red.
- Dislike is persisted BEFORE playback/queue mutation.
- Active disliked songs are filtered from normal Music/Search/Library/catalog queue construction.
- A disliked song can still be played only inside the dedicated Disliked review surface.
- Disliked is a secondary screen from More, not a sixth primary navigation-rail item.
- Each record stores durable metadata independent of the audio file.
- Duplicate dislikes are idempotent.
- Undo works before physical deletion.
- Deleted dislike history remains exportable.
- Export produces both:
  - `Disliked.txt` as human-readable `Artist — Song`;
  - `Disliked.json` with song ID, title, artists, album, source kinds, timestamps, deletion state.
- Bulk delete requires confirmation and states that deletion is not automatically reversible.
- Bulk delete is item-by-item and partial failures do not stop remaining items.
- Failed deletions remain Pending with error text.
- Managed downloads/cache/artwork use MetroList cleanup paths.
- Car Library source files are deleted only when exact target resolution succeeds and persistent write permission is present.
- A disliked song is excluded from future legacy backup import planning, including resume-journal filtering, so an old backup cannot silently reintroduce it while the dislike remains active.

Manual AVD validation performed:
- dislike a track;
- More showed `Review disliked songs (1)`;
- Disliked screen showed the track as Pending deletion;
- review playback available;
- Undo visible;
- Export visible;
- Delete all visible;
- confirmation dialog showed file count and history-preservation warning;
- cancellation path tested without deleting the test media.

Disliked implementation commit:
- `7386b67d9 feat(car): add disliked review workflow`

Design/plan doc commit:
- `30890ad22 docs(car): add disliked review workflow`

## Startup/performance status

Task 10 has instrumentation, but the cold-start target is NOT consistently met yet. Do not claim Task 10 performance gate complete.

Startup milestones now logged under stable tag:
- `ACTIVITY_CREATED`
- `CONTENT_SET`
- `SHELL_COMPOSED`
- `PLAYER_CONNECTED`
- `LIBRARY_REOPEN_STARTED`
- `LIBRARY_READY`

Instrumentation commit:
- `da940f041 perf(car): instrument startup milestones`

Fresh focused tracker tests pass.

Evidence on the 2 GB / 1024x600 AVD:

Single diagnostic cold trace:
- process start: 16:07:50.049
- Activity created: 16:07:50.377
- content set: +15 ms from Activity creation
- shell composed: +205 ms
- Android Displayed: 773 ms total
- library reopen started: +588 ms
- library ready: +595 ms
- player connected: +778 ms

Subsequent stabilized 3-run batch:
- cold TotalTime: 2106 ms, 2041 ms, 2045 ms
- cold median: 2045 ms
- hot TotalTime: 2 ms, 3 ms, 6 ms
- hot median: 3 ms

In the slower cold runs:
- shell composed around 636-640 ms;
- Car Library around 1.29-1.34 s;
- player around 1.56-1.60 s.

Conclusion supported by evidence:
- player connection is not blocking shell composition;
- Car Library reopen is not blocking shell composition;
- warm start comfortably passes the <800 ms target;
- cold start does not consistently pass the <=1500 ms target;
- the unresolved variance is between shell composition / process startup and Android's first displayed frame.

Do NOT rewrite Media3, Room, or playback restoration to chase the metric without new evidence.

Recommended next performance investigation:
1. instrument first actual draw / first frame in the Activity (ViewTreeObserver OnDraw or equivalent), distinct from Compose `SideEffect`;
2. compare process-start → Activity-created, Activity-created → first draw, first draw → Displayed;
3. inspect main-thread work between shell composition and first draw;
4. specifically verify whether early Room downloaded-song emission, provenance-store sync work, or another main-thread startup effect is delaying first draw;
5. apply only a minimal, test-backed optimization if a concrete blocker is identified;
6. repeat 3 cold + 3 hot measurements and record medians.

Startup evidence is documented in:
- `docs/superpowers/testing/metrolist-car-test-lab.md`

## Current automated gate

Fresh gate run immediately before handoff:

`\.\gradlew.bat --no-daemon --max-workers=1 :app:testCarDebugUnitTest :app:assembleCarDebug`

Result:
- BUILD SUCCESSFUL
- 85 actionable tasks
- 3 executed, 82 up-to-date
- runtime ~42 s

This proves the current Car debug variant compiles and all Car unit tests pass at HEAD around this handoff.

Current generated debug APK:
- `app/build/outputs/apk/car/debug/app-car-debug.apk`
- size observed: 56,413,741 bytes
- this is NOT yet the Car 0.3.0 release artifact because version bump/release acceptance has not been completed.

Current `gradle.properties` still has:
- `metrolistCarVersion=0.2.0`

Do not bump to 0.3.0 until Task 11 gates are complete.

## Remaining work

The next conversation should begin by finishing Task 10's cold-start investigation only if the <=1.5 s target remains required for release. Then execute Task 11 from the plan.

Task 11 still includes:
- full automated regression:
  - Car serial gate;
  - standard Android FOSS/GMS/Izzy test + assemble gate;
  - Windows desktop tests + distributable;
- full 1024x600 AVD functional matrix;
- 800x480 and 1280x720 viewport matrix;
- dark-mode verification while Android is in light mode;
- playback / seek / swipe / queue / mini-player / search;
- Like/Dislike and Disliked review/Undo/export/delete;
- small legacy backup import;
- Car Library open/refresh/process-restart/offline playback;
- large-backup acceptance, preferably user's real ~4 GB / 300+ track `.metrolistbackup`;
- interrupt/resume non-trivial import and verify journal recovery;
- fresh whole-diff code review from `IMPLEMENTATION_BASE_SHA` in the test-lab doc;
- requirement-by-requirement PASS/FAIL acceptance record;
- only after all gates: bump `metrolistCarVersion=0.3.0`, verify versionCode 3000, update release history, package final APK, compute SHA-256, clean-install exact artifact on AVD.

## Large backup context

The user reported existing legacy `.metrolistbackup` files around 4 GB with 300+ songs. Earlier failure on the physical head unit may have been an import with no visible feedback rather than a hard failure.

Do not assume legacy import is broken. Acceptance must prove:
- PREPARING/VALIDATING feedback begins early;
- byte/indeterminate progress is visible during long validation;
- throughput feedback remains alive;
- IMPORTING track counts progress;
- FINALIZING is visible;
- completion is visible;
- memory/I/O remain stable on 2 GB AVD;
- interrupted import resumes correctly.

The newer Car Library/direct-folder path is preferred for day-to-day content because it avoids slow re-import, but legacy backup compatibility remains required.

## Current repository state

Branch: `main`

HEAD before adding this handoff document:
- `da940f041004ead92777192c1a4b0d85bf546667`

Recent sequence:
- `da940f041 perf(car): instrument startup milestones`
- `a96c407ef feat(car): refine driver-safe player controls`
- `7386b67d9 feat(car): add disliked review workflow`
- `3dd92cb5d feat(car): add artwork swipe and player timing`
- `30890ad22 docs(car): add disliked review workflow`
- `13434cf51 feat(car): move player controls to driver side`
- `b00847188 feat(car): redesign mini player for driving`
- `335e10b54 feat(car): unify automotive track rows`

Do not push or merge unless the user explicitly asks.

## Suggested first prompt for the next conversation

Continue MetroList Car from `I:\codex\metrolist-windows` on `main`. First read `docs/superpowers/handoffs/2026-09-28-metrolist-car-0.3.0-pre-release-handoff.md`, then the 0.3.0 implementation plan and Car test-lab doc referenced inside it. Invoke Superpowers before acting. The latest player layout is user-approved and must not be redesigned. Car tests + assemble are green. Start by resolving the remaining inconsistent cold-start gate using the existing startup milestones and systematic debugging, without speculative Media3/Room rewrites. Then execute Task 11 acceptance/release exactly from the plan, including the 2 GB AVD matrix and large ~4 GB legacy-backup gate. Keep me updated after meaningful batches of actions, not every command and not only at the end.
