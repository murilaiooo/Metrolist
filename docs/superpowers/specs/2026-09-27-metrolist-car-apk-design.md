# MetroList Car APK Design

Date: 2026-09-27

## 1. Purpose

MetroList Car is a dedicated Android head-unit product for the user's in-car multimedia system. It is not a tablet skin and not a replacement for the upstream Android app.

The goal is a car-first player that:
- imports Windows-generated `.metrolistbackup` archives safely;
- plays the imported library fully offline;
- remains usable with unreliable or absent network connectivity;
- restores queue, track, position, and play/pause intent after head-unit power loss;
- treats physical media controls and system audio controls as first-class inputs;
- uses large, predictable touch targets on unreliable Android head-unit display metrics;
- keeps the approved Windows and standard Android products isolated and regression-safe.

Windows 0.2.2 is the compatibility baseline. The Car product consumes its portable backup contract and must not require Windows changes merely to simplify Car implementation.

## 2. Target hardware and operating assumptions

Primary target:
- AC8257V/WAB family, alps UJC201_64;
- ARM64;
- approximately 6 GB RAM;
- approximately 128 GB storage;
- Android capabilities treated as API 28/29-class behavior even if firmware branding is misleading;
- fixed landscape head-unit display;
- touch plus physical head-unit/steering-wheel controls;
- removable USB storage used for large offline library transfer.

The head unit may report unreliable DPI, density, or physical display metrics. Car UI sizing must therefore not depend on phone/tablet classification or physical-DPI claims.
## 3. Product boundary

MetroList Car will be implemented as a dedicated `car` product flavor inside the existing `:app` Android module.

This is preferred over a separate `:car-app` module because the current playback, Room, Media3, InnerTube, download, queue, and MediaSession infrastructure already lives in `:app`. Extracting those systems into new shared modules would introduce unnecessary risk.

It is also preferred over a runtime "car mode" in the regular Android app because that would spread conditional behavior across the normal mobile UI.

The Car flavor will:
- use its own application ID, `com.metrolist.music.car`;
- use its own launcher surface;
- use a dedicated `CarMainActivity`;
- expose `BuildConfig.IS_CAR = true`;
- disable Cast and updater surfaces;
- use a dedicated car-first navigation shell;
- reuse the existing `MusicService`, Room database model, Media3 player/download infrastructure, queue model, and InnerTube stack where appropriate.

The standard Foss, GMS, and Izzy products remain behaviorally unchanged unless a shared bug fix is independently justified.

## 4. Independent Car versioning

Car has its own semantic-version source of truth:
`metrolistCarVersion=0.1.0`

This value is independent from:
- Windows 0.2.2;
- upstream Android 13.7.0.

The Car version must drive APK `versionName`, be visible discreetly in the Car UI, and appear in diagnostics, crash reports, and playback error reports.

Every APK intentionally handed to the user for installation receives a new Car version according to the repository versioning policy.
## 5. Car-first interaction model

The Car UI is designed for a fixed landscape multimedia unit, not a handheld device.

Primary destinations:
- Now Playing;
- Downloads;
- Library;
- Search;
- More / Settings.

Downloads is the default library destination because the product is offline-first. If a prior playback session is being restored, the UI may return directly to the active playback experience instead of forcing the user back to Downloads.

Now Playing prioritizes:
- large artwork;
- title and artist;
- previous;
- play/pause;
- next;
- large seek/progress control;
- queue;
- secondary shuffle/repeat actions;
- favorite/library action where appropriate.

The UI must avoid:
- on-screen volume controls;
- tiny overflow targets;
- hover-dependent behavior;
- long-press-only essential actions;
- dense phone-style toolbars;
- narrow sliders;
- controls that duplicate dedicated physical head-unit controls without benefit.

Touch targets should be intentionally large and forgiving. Visual icon size and interactive hit area are separate concerns.

## 6. System volume and audio behavior

Volume is controlled by the head unit and Android system. MetroList Car will not expose a volume slider or volume up/down/mute buttons in its player UI.

`VOLUME_UP`, `VOLUME_DOWN`, and `VOLUME_MUTE` remain system-owned events and are not intercepted by Car-specific routing.

The existing AudioManager/audio-focus behavior remains the playback authority for:
- navigation prompts;
- calls;
- competing media apps;
- temporary focus loss;
- focus regain.

The Car flavor must not fight system volume or force its own output gain as a substitute for the system's dedicated controls.
## 7. Physical controls and CarInputRouter

MediaSession remains the primary integration point for standard media commands:
- MEDIA_PLAY;
- MEDIA_PAUSE;
- MEDIA_PLAY_PAUSE;
- MEDIA_NEXT;
- MEDIA_PREVIOUS;
- MEDIA_STOP.

Car must also handle the reality that low-cost Android head units and CANBUS adapters may emit non-standard key codes or scan codes.

A small hidden diagnostics surface in Settings will expose raw input observations:
- key code;
- scan code;
- action;
- input source;
- interpreted action, if any.

No vendor-specific mapping is added by guesswork. Mappings are added only after real hardware observation.

A `CarInputRouter` provides two layers:
1. standard Android/MediaSession behavior;
2. confirmed AC8257-specific mappings.

Back navigates within the app. At the root it must not stop playback. Home remains system-owned.

## 8. Display adaptation

Car must not rely on the head unit's physical DPI claims or phone/tablet breakpoints.

A `CarViewport` abstraction will derive UI scale from the actual rendered pixel viewport using a landscape reference canvas, initially 1280x720.

The implementation should clamp scale to safe minimum/maximum values so anomalous display metrics cannot produce microscopic or oversized controls.

The viewport layer must make car-specific sizing deterministic while leaving existing mobile adaptive-layout behavior unchanged.
## 9. Offline-first startup and playback restoration

App startup must not block on login, YouTube, synchronization, or network reachability.

Startup order:
1. initialize local persistence and Media3;
2. restore local queue/player state;
3. render local library immediately;
4. restore local artwork;
5. initialize optional online capabilities asynchronously.

The existing app already persists queue, current index, current position, playback state, and `playWhenReady`.

The Car flavor changes restoration semantics:
- if playback was active when state was last persisted, restore the queue, track, position, and resume playback;
- if playback was paused, restore the same queue, track, and position but remain paused.

The app must not depend on `onDestroy()` for important state. Queue/player state remains persisted incrementally so abrupt ACC power loss, process kill, or deep sleep does not erase the active session.

The existing persistence cadence may be tightened for Car if testing shows that 15-second player-state persistence is too coarse for power-loss recovery, but such a change must be isolated and tested.

## 10. Scheduler boundary

The standard Android variants currently return a Media3 `PlatformScheduler` from `ExoDownloadService.getScheduler()`.

Media3 1.10.1 defines this method as nullable.

For Car only:
`getScheduler() = null`

Foss, GMS, and Izzy retain their existing scheduler behavior.

User-initiated downloads still use the existing DownloadManager/DownloadService path. The Car flavor simply avoids depending on JobScheduler/PlatformScheduler for deferred resumption on unreliable head-unit firmware.
## 11. Portable backup contract

Windows 0.2.2 remains the producer of the portable backup.

Car consumes backup format v1 with the currently validated profile:
- `appName = Metrolist`;
- `backupFormatVersion = 1`;
- `sourceDatabaseVersion = 38`;
- `type = offline_downloads`;
- `cacheStrategy = media3_simple_cache_spans`;
- song IDs aligned with request IDs/cache keys;
- metadata, transfer records, cache spans, artwork, file hashes, and manifest counts validated before commit.

The Windows 0.2.2 hotfix changed stream resolution only. It did not modify `desktop-portable`, the manifest, the portable models, or the validator contract.

Car gets its own reader/validator implementation of this contract. The Windows exporter is not refactored merely to share source code.

Compatibility is enforced through contract tests against real Windows-generated archives.

## 12. Import entry point

Car exposes a prominent `Import Windows Library` action.

The file is selected using Android's Storage Access Framework so USB storage can be read without hard-coded filesystem paths or broad storage permissions.

The importer accepts `.metrolistbackup` and works directly from the selected document URI. It must not require a second full-size copy of a multi-gigabyte archive inside app storage.

Import runs in a dedicated foreground service with persistent progress reporting. It does not depend on WorkManager, JobScheduler, or PlatformScheduler.
## 13. Import validation

Import uses a two-pass design.

Pass A is read-only validation:
- parse and validate manifest;
- verify supported backup format/profile;
- reject path traversal and unsafe paths;
- verify file list and required metadata;
- verify counts and uniqueness;
- verify every declared SHA-256;
- verify span ordering and continuity;
- verify each resource content length;
- verify transfer IDs/cache keys match expected song identity;
- estimate required destination space;
- reject insufficient storage before mutation.

Any failure in Pass A leaves Room, Media3 cache, artwork storage, and download index unchanged.

Pass B performs import only after Pass A succeeds.

The importer must fail with actionable error messages including Car version and import stage.

## 14. Media3 cache reconstruction

Portable `.span` files contain logical cache ranges. They are not copied directly into Media3's private cache-file layout.

The importer writes span bytes through Media3 public cache APIs, using `CacheDataSink` against the existing `@DownloadCache` `SimpleCache`.

The cache identity contract remains:
`songId = cacheKey = requestId = uri = customCacheKey`

This makes imported audio a valid member of Media3's cache instead of an implementation-specific filesystem imitation.

After cache reconstruction, the importer writes a completed Media3 download record through the writable download index associated with the same `StandaloneDatabaseProvider`.

The final download state must be `STATE_COMPLETED`, with truthful content length and timestamps.
## 15. Room metadata import

Portable song, artist, relationship, and format metadata is mapped into the current Room entities.

The importer uses the current schema and DAO layer rather than replacing the SQLite database file.

Relevant imported state includes:
- SongEntity;
- ArtistEntity;
- SongArtistMap;
- FormatEntity;
- download status and download date.

Portable album arrays/maps may remain intentionally empty where the established v1 contract relies on song-level album ID/name fields.

Room writes are grouped transactionally where possible.

The importer is idempotent by song ID:
- already-valid imported content is skipped;
- incomplete/corrupt local content is repaired;
- new content is added;
- repeated import does not create duplicate songs or relationships.

## 16. Offline artwork

Portable artwork must remain available without network access.

Car stores imported artwork in an app-owned deterministic location such as:
`filesDir/car-artwork/<songId>.img`

The Car UI resolves artwork:
1. imported local artwork;
2. original remote thumbnail URL;
3. placeholder.

The original thumbnail URL remains unchanged in song metadata.

This avoids depending on undocumented Coil cache internals while preserving network artwork fallback when connectivity exists.
## 17. Import interruption and recovery

Large libraries can take long enough for ACC power loss, sleep, or process death to occur during import.

The importer maintains a durable import journal with:
- source archive identity;
- validated manifest identity;
- import phase;
- songs/resources completed;
- artifacts created by the in-progress import;
- last durable checkpoint.

On next launch, an interrupted import is detected and the user can:
- Resume Import;
- Discard Incomplete Import.

Resume must continue from verified durable boundaries instead of starting the entire archive again.

Discard removes only artifacts attributable to the incomplete transaction. It must not erase previously valid library content.

The importer must never leave Room claiming a song is downloaded while its Media3 cache/download-index state is incomplete.

## 18. Online capabilities

Car 0.1.0 retains selected online capabilities, but they are secondary to local playback.

Required product surfaces:
- Downloads;
- Library;
- Playlists;
- Search;
- Online browsing;
- Player;
- Queue;
- portable import;
- Settings;
- diagnostics.

Features such as Cast, updater UI, recognition, Listen Together, social integrations, and experimental utilities are not exposed in the initial Car shell.

They do not need to be physically deleted from shared source code for 0.1.0 unless they cause a demonstrated runtime problem.
## 19. Diagnostics

Car Settings includes a diagnostics section with:
- MetroList Car semantic version;
- Android API level;
- device/manufacturer/model strings;
- actual pixel viewport;
- Android-reported density values;
- available/internal storage;
- Media3 download count;
- Room downloaded-song count;
- last import status;
- raw physical-button test mode.

Playback and import errors include the Car version and enough stage/context data to reproduce the issue.

Diagnostics must not expose authentication secrets, cookies, or private tokens.

## 20. Test strategy

Portable contract tests cover:
- real Windows-generated backup;
- invalid manifest values;
- missing files;
- wrong hash;
- duplicate IDs;
- malformed/truncated archive;
- ZIP traversal;
- span gaps/overlap/order errors;
- insufficient space;
- repeated/idempotent import.

Importer integration tests verify:
- Room entities;
- Media3 cache readability;
- completed DownloadIndex entries;
- offline artwork;
- rollback/recovery;
- resume after interruption.

Car-specific tests verify:
- Car launcher;
- Car version wiring;
- scheduler is null only for Car;
- viewport scaling;
- physical input routing;
- volume events are not consumed;
- playback-state restore semantics;
- Back does not stop music.
## 21. Regression gates

Existing Android products remain green with:
`testFossDebugUnitTest`
`testGmsDebugUnitTest`
`testIzzyDebugUnitTest`
`assembleFossDebug`
`assembleGmsDebug`
`assembleIzzyDebug`

Car adds its own unit/integration/assembly gates.

Windows/portable tests remain green because Windows 0.2.2 is the compatibility producer.

No Windows architecture changes are part of the Car implementation unless a concrete, reproducible compatibility defect proves they are necessary.

## 22. Physical head-unit acceptance sequence

After local automated gates pass and only with explicit user authorization:
1. install a versioned Car APK;
2. verify launcher/display/touch sizing;
3. run physical-button diagnostics and record real key/scan codes;
4. verify system volume buttons remain system-controlled;
5. import a small real Windows backup;
6. verify local metadata and artwork;
7. disable network and verify playback, seek, next/previous, queue;
8. power-cycle or reproduce head-unit sleep/restart;
9. verify queue/track/position and playing-vs-paused restoration;
10. test interrupted import recovery;
11. import the large real library;
12. verify counts, storage, playback, and reimport idempotency.

Confirmed non-standard key mappings may be added in a subsequent versioned Car build after measurement.

## 23. Safety and release rules

Do not reset, clean, or delete unrelated user data.

Do not push or install an APK without explicit user authorization.

Every APK intentionally handed to the user gets a new Car semantic version.

Do not change the Windows 0.2.2 portable format without an explicit versioned compatibility decision.

Root-cause failures rather than bypassing them with destructive cleanup.

Behavior changes use tests first where practical, and completion claims require fresh verification evidence.
## 24. Non-goals for Car 0.1.0

Car 0.1.0 does not aim to:
- redesign the standard Android mobile app;
- replace Android's system volume UI;
- emulate Android Auto as the primary display surface;
- guess CANBUS/vendor key mappings;
- refactor Windows 0.2.2;
- redesign the portable backup format;
- delete every unused mobile feature from shared source;
- introduce a new Room schema without demonstrated need.

## 25. Success criteria

Car 0.1.0 is successful when:
- it installs independently from the standard Android app;
- its UI is reliably usable on the target head unit despite bad DPI reporting;
- physical standard media controls work through MediaSession;
- system volume remains controlled by the head unit;
- measured non-standard buttons can be diagnosed and mapped safely;
- a Windows 0.2.2 portable backup imports without direct cache-file hacks;
- imported songs appear as real completed Media3 downloads;
- imported artwork and audio work with network disabled;
- large imports can resume after interruption;
- reimport is safe and idempotent;
- playback restores queue, track, position, and prior playing/paused intent after restart;
- standard Android and Windows regression gates remain green.

This document defines the architecture and behavior required before implementation planning begins.
