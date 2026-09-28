# MetroList Car 0.3.0 UX, Performance, and Feedback Design

Date: 2026-09-28
Status: Approved design, pending implementation plan

## 1. Purpose

MetroList Car 0.3.0 is a car-first UX and runtime redesign built on the working Car infrastructure from 0.2.0.

The physical head-unit test showed that technical correctness alone is insufficient. The current build opens too slowly on real hardware, looks provisional, hides long-running work, and places important player controls too far from the driver.

This design keeps the proven playback, database, Windows interoperability, Car Library, Media3, and import foundations while replacing the Car presentation and progress model.

This document supplements `2026-09-27-metrolist-car-apk-design.md`. Where the two documents conflict on Car UI, startup presentation, or long-running-operation feedback, this document takes precedence.

## 2. Product outcomes

Car 0.3.0 must:
- look like a deliberate automotive music player rather than a tablet adaptation;
- remain dark at all times, independent of Android theme;
- place primary playback controls close to the driver;
- put album artwork on the right side of Now Playing;
- allow previous/next by horizontal artwork swipe;
- expose large, forgiving touch targets without visually oversized controls;
- provide immediate and continuous feedback for every perceptibly long operation;
- render useful local UI before slow startup work completes;
- remain fully usable offline;
- preserve Windows backup and Car Library compatibility.

## 3. Constraints and preserved architecture

The redesign must not rewrite working subsystems without a demonstrated regression.

Preserve:
- Media3 playback and MediaSession integration;
- existing Room data model;
- unified Car catalog;
- direct-play Car Library path;
- legacy `.metrolistbackup` v1 compatibility;
- Windows exporter contracts;
- player queue and playback restoration;
- CarInputRouter and raw input diagnostics;
- system-owned volume behavior;
- standard Android variants and Windows behavior.

Car remains a dedicated product flavor inside the existing Android app module.

Volume remains outside the MetroList Car UI. The Android system, head-unit buttons, and steering-wheel controls remain authoritative.

## 4. Primary target and development lab

Primary physical target remains the existing Android head unit described in the Car architecture spec.

The development lab becomes a first-class acceptance environment:
- AVD name: `MetroList_Car_API29`;
- Android 10 / API 29;
- landscape;
- reference test viewport: 1024x600;
- reference density: 160 dpi;
- WHPX hardware acceleration;
- primary acceptance memory profile: approximately 2 GB RAM;
- data partition: at least 24 GB so a multi-gigabyte source archive and imported payload can coexist during testing;
- optional 6 GB RAM comparison profile when diagnosing memory-sensitive behavior;
- ADB-driven installation, startup timing, screenshots, input, and file transfer.

Additional viewport presets must cover at least:
- 800x480;
- 1024x600;
- 1280x720.

The physical head unit remains the final hardware acceptance target, not the primary iteration environment.

## 5. Automotive interaction principles

The driver should be able to perform common playback actions with minimal visual attention.

Primary actions must:
- occupy predictable locations;
- have large hit areas;
- respond immediately to touch;
- avoid long-press-only behavior;
- avoid tiny overflow menus for essential actions;
- avoid visually dense toolbars;
- remain usable with imprecise touches.

Visual size and touch target size are separate. Controls may look restrained while exposing larger invisible or low-contrast hit regions.

Animations must communicate state and gesture direction, not decorate the screen. Avoid long, elaborate, or distracting motion.

Text required while driving should be short and scannable. Diagnostic and maintenance detail belongs in More, not in the main playback surface.

## 6. Permanent dark theme

MetroList Car must always render with a dark color scheme, even when Android is configured for light mode.

`CarApp` must not rely on the default `MetrolistTheme()` system-theme behavior. The Car flavor must request an explicit dark theme.

Requirements:
- no MetroList-owned white or light splash;
- no light loading surface;
- no light dialog owned by MetroList;
- no light empty state;
- no theme switch in Car 0.3.0;
- high text/icon contrast;
- dark surface hierarchy rather than one flat black sheet;
- restrained accent color.

System-owned UI such as Android DocumentsUI may still follow system or firmware behavior and is outside MetroList visual control.

## 7. Now Playing spatial model

Now Playing is redesigned for a left-side driver position.

From left to right after the navigation surface:
1. track metadata and primary playback controls;
2. album artwork on the right.

The existing Car 0.2.0 layout, which places artwork first and controls farther right, is reversed.

The left control region contains:
- title;
- artist;
- playback position / duration;
- large seek control;
- Previous;
- Play/Pause;
- Next;
- compact secondary actions.

The artwork region contains:
- high-quality square artwork;
- graceful fallback artwork;
- horizontal swipe interaction;
- no essential text overlaid on the artwork.

The layout must adapt to the three acceptance viewports without moving primary transport controls to the far side of the screen.

## 8. Primary player controls

Play/Pause is the dominant control.

At the 1024x600 reference viewport:
- Play/Pause should expose an interactive region approximately 104-112 reference pixels;
- Previous and Next should expose approximately 88-96 reference pixels;
- secondary controls should remain comfortably touchable but visually subordinate.

Exact rendered dp values are produced through the Car viewport abstraction rather than raw Android density assumptions.

Primary control order remains:
Previous, Play/Pause, Next.

Secondary actions may include:
- shuffle;
- queue;
- repeat;
- favorite/library action where currently supported.

Secondary actions must not visually compete with transport controls.

Disabled controls remain legible but clearly inactive.

## 9. Artwork swipe navigation

The album artwork supports horizontal swipe for track navigation.

Gesture semantics:
- swipe left: next track;
- swipe right: previous track;
- insufficient swipe: artwork returns to center without changing track;
- unavailable next/previous item: gesture resists or returns without changing track.

Implementation should reuse the proven gesture approach already present in the standard MetroList MiniPlayer where appropriate, rather than creating unrelated gesture semantics.

The Car gesture must use a more deliberate threshold than the standard mobile interaction.

Acceptance behavior:
- a slow drag must cross roughly 18-20% of artwork width before committing;
- a clearly fast fling may commit below that distance if velocity is unambiguous;
- accidental small horizontal motion must not change tracks;
- the artwork follows the finger during drag;
- after commit or cancel, artwork animates cleanly to its resting state;
- RTL behavior must remain logically correct.

Track changes must call the existing player navigation path and preserve queue semantics.

## 10. Mini player

The Mini Player remains visible outside Now Playing whenever a track is active.

It must be redesigned for automotive use and contain:
- artwork;
- track title;
- artist where space permits;
- Previous;
- Play/Pause;
- Next.

The whole metadata/artwork region opens Now Playing.

The interactive row and control hit areas must be large enough for imprecise touch.

The Mini Player must not add a volume control.

## 11. Music, Library, and Search surfaces

List surfaces must stop reading visually like generic diagnostic tables.

A normal track row should provide:
- artwork;
- title;
- artist;
- clear active-playing state;
- large row hit area;
- consistent spacing.

Essential actions must not depend on long press.

Current playback should be recognizable quickly without reading status labels such as "Legacy backup" on every row. Provenance belongs in diagnostic or maintenance contexts unless it is needed to explain availability.

Library and Search must share the same track-row language so switching destinations does not require relearning interaction patterns.

Empty states should explain the next useful action without filling the screen with diagnostic prose.

## 12. Navigation shell

The five conceptual destinations remain:
- Now Playing;
- Music;
- Library;
- Search;
- More.

The navigation rail is redesigned to reduce visual bulk while retaining generous hit areas.

The rail must:
- make the current destination obvious;
- keep labels/icons readable;
- avoid consuming space needed by driver-side playback controls;
- remain stable across supported viewport sizes.

The shell must render immediately during startup, even before catalog restoration finishes.

## 13. Unified long-running operation feedback

Car 0.3.0 introduces one reusable progress model for perceptibly long operations.

Conceptual states:
- Idle;
- Running;
- Success;
- Error.

A Running state carries, where available:
- operation stage;
- item progress;
- bytes processed;
- total bytes;
- percent;
- transfer/processing speed;
- human-readable current action.

The UI must never leave the user guessing whether a command was accepted, still running, stalled, completed, or failed.

Any operation expected to take more than a brief moment must publish feedback immediately.

Examples include:
- legacy backup validation;
- legacy backup import;
- Car Library open/index;
- Car Library refresh;
- large local catalog scan;
- storage-intensive maintenance tasks;
- startup restoration when it outlives first render.

Fast actions still require immediate state response through pressed state, selection, playback icon change, toast/snackbar where appropriate, or updated content.

## 14. Legacy backup import progress

The current importer validates before useful stream-progress reporting. On a multi-gigabyte archive this can appear frozen.

Car 0.3.0 must expose validation as a first-class observable stage.

Required stages:
1. Preparing;
2. Validating;
3. Importing;
4. Committing / Finalizing;
5. Complete or Error.

Validation must report progress while bytes are being read, not only after validation finishes.

For a large archive the UI should be able to show information such as:
- `Validating backup`;
- `1.43 GB / 4.08 GB`;
- `35%`;
- current throughput;
- current item/resource count where meaningful.

The importer must preserve its safety property: validation occurs before destructive or committed library mutation.

Importing must then report:
- songs completed / total songs;
- bytes processed / total bytes;
- percent;
- throughput;
- current phase.

Finalizing must explicitly tell the user that metadata/library commit work is occurring even if byte progress is already at 100%.

The background notification and in-app UI must derive from the same progress model so they cannot disagree.

Resume/discard behavior remains supported for interrupted legacy imports.

## 15. Progress calculation and throughput

Progress must be monotonic within a stage unless the operation restarts.

Byte totals must represent the work relevant to the displayed stage. The UI must not present a false 0% for minutes when bytes are actively being read.

Throughput should be smoothed over a short rolling interval so the display is useful rather than flickering on every buffer read.

Unknown totals use an indeterminate progress treatment plus concrete activity text.

Progress reporting must be throttled enough to avoid excessive Compose recomposition, notification churn, journal writes, or I/O overhead.

The progress model must remain independently testable without UI instrumentation.

## 16. Error handling

No long-running operation may fail silently.

Errors must include:
- what failed;
- enough context to distinguish validation, storage, permission, and playback failures;
- a useful next action when one exists.

Examples:
- backup incompatible;
- backup damaged or incomplete;
- insufficient destination storage;
- selected library folder no longer available;
- persisted permission lost;
- import interrupted and resumable;
- media file unavailable.

Technical stack traces remain in logs, not in normal driver-facing UI.

## 17. Startup architecture

Startup must prioritize first render over complete restoration.

Target sequence:
1. create Car activity and dark shell;
2. render navigation and stable empty/loading structure;
3. connect player/session;
4. restore local playback state;
5. reopen/index Car Library and merge catalog asynchronously;
6. update content as each dependency becomes ready.

The first meaningful Car UI must not block on:
- network;
- YouTube/InnerTube;
- Car Library full indexing;
- large database scans that can be deferred;
- optional diagnostics.

Current startup behavior must be instrumented before optimization so changes target measured bottlenecks.

No startup optimization may sacrifice correct queue restoration, local playback, or storage consistency.

## 18. Startup performance gates

Development-lab targets for the API 29 1024x600 AVD:
- cold start to first meaningful Car frame: target <= 1.5 seconds;
- warm start to first meaningful Car frame: target < 0.8 seconds.

These are engineering targets, not claims about physical hardware.

Physical-head-unit targets are established only after measuring the optimized build on that hardware.

Every release candidate must record:
- Activity launch timing;
- cold/warm classification;
- first-frame timing where available;
- any startup task exceeding the agreed budget.

A regression above the baseline requires investigation before release.

## 19. Steering-wheel and hardware controls

Steering-wheel media buttons are supported but are secondary to the touch redesign.

MediaSession remains primary for standard media keys.

Existing diagnostics remain available for:
- key code;
- scan code;
- input source;
- interpreted action.

Do not add guessed vendor mappings.

Volume up/down/mute remain system-owned and are not intercepted by MetroList.

Physical control validation occurs during final head-unit acceptance.

## 20. Visual quality requirements

The Car UI must look cohesive across Now Playing, Mini Player, Music, Library, Search, and More.

Required qualities:
- consistent spacing rhythm;
- consistent corner radii;
- consistent icon language;
- dark surface hierarchy;
- restrained typography;
- no debug-like information in primary playback surfaces;
- no duplicated controls;
- no tiny isolated tap targets;
- no phone-style visual density;
- high-quality artwork where available.

The 1024x600 viewport is the primary visual review canvas, but designs must survive 800x480 and 1280x720.

Screenshots of every primary destination become part of pre-release review.

## 21. Test strategy

Implementation follows TDD for new behavior and regression fixes.

Required focused tests include:
- Car theme remains dark when system theme is light;
- Now Playing layout keeps driver controls left and artwork right;
- artwork swipe left invokes Next;
- artwork swipe right invokes Previous;
- sub-threshold swipe changes nothing;
- unavailable previous/next does not change track;
- large touch targets satisfy Car sizing rules;
- legacy validation emits progress before import begins;
- import progress exposes bytes, percent, stage, and item counts;
- progress remains monotonic;
- completion and error states are explicit;
- Car shell does not wait for library reopening before first render;
- restart preserves imported/catalog state;
- Car Library remains direct-play without second audio copy.

Existing Car unit tests, standard Android variant tests, Windows tests, and packaging verification remain regression gates.

## 22. Emulator acceptance suite

Before a build is handed to the user for physical testing, run on the Car AVD:
- clean install;
- cold start;
- warm start;
- Android light mode with MetroList still dark;
- navigate all five destinations;
- play/pause/previous/next;
- seek;
- artwork swipe;
- Mini Player controls;
- queue;
- search;
- legacy backup import using the existing ~44.86 MB / 10-track fixture;
- interrupted import and resume/discard where practical;
- Car Library folder open and refresh;
- process kill and restart;
- offline playback;
- screenshot review at 1024x600;
- layout checks at 800x480 and 1280x720.

A large-archive acceptance must also exercise a multi-gigabyte or representative synthetic fixture before declaring the large-import UX ready.

The purpose is to prove continuous feedback and stable memory/I/O behavior, not merely successful completion.

## 23. Release acceptance

Car 0.3.0 is not ready for the head unit until:
- all automated Car tests pass;
- required Android/Windows regression suites pass;
- APK assembles successfully;
- startup targets are measured;
- all primary AVD screens receive visual approval;
- dark-mode invariance is verified;
- legacy small-backup import completes;
- large-backup progress behavior is verified;
- Car Library path is verified;
- no Critical or Important review findings remain.

Physical acceptance then verifies:
- real startup behavior;
- touchscreen ergonomics;
- artwork and text legibility;
- steering-wheel media keys;
- system volume behavior;
- storage/SAF behavior on the firmware;
- large real library performance.

A physical-only defect must be documented with logs or observed behavior and reproduced in the lab when feasible.

## 24. Non-goals

Car 0.3.0 does not:
- redesign the Windows app;
- rewrite the portable backup format;
- add an in-app volume control;
- guess CANBUS/vendor key mappings;
- add a light theme;
- replace Media3;
- replace Room;
- rewrite the working Car Library architecture;
- turn the Car build into Android Auto.

## 25. Versioning

This redesign ships as MetroList Car 0.3.0.

The version is bumped only when an APK is intentionally packaged for installation according to the repository versioning policy.

Intermediate development builds may remain internal until a user-testable package is intentionally produced.

## 26. Success criteria

The redesign succeeds when:
- the Car app feels intentionally designed for a fixed head unit;
- the user can reach Previous, Play/Pause, and Next quickly from the driver side;
- album artwork is on the right and supports deliberate swipe navigation;
- the app remains dark regardless of system theme;
- no long operation appears frozen;
- a multi-gigabyte backup exposes useful feedback from validation through completion;
- first useful UI appears materially faster than Car 0.2.0;
- the Windows-to-Car ecosystem remains compatible;
- emulator acceptance is strong enough that the physical car is primarily a final hardware validation step.
