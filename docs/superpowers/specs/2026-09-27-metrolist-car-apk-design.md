# MetroList Car APK Design

> **ECOSYSTEM REVIEW GATE:** This Car-specific design must now be read together with `docs/superpowers/reviews/2026-09-27-metrolist-ecosystem-integration-audit.md`. The audit identified cross-product ownership gaps that require an approved ecosystem contract before further architectural implementation resumes.

Date: 2026-09-27

## 1. Purpose

MetroList Car is a dedicated Android head-unit product for the user's in-car multimedia system. It is not a tablet skin and not a replacement for the upstream Android app.

The goal is a car-first player that:
- supports Windows-generated `.metrolistbackup` archives as the legacy backup/restore compatibility path;
- supports a new folder-based **MetroList Car Library** as the preferred high-volume Windows -> car transfer path;
- reads Car Library media directly from a user-selected folder in the head unit's persistent local storage without duplicating multi-gigabyte audio into app-private storage;
- plays the available library fully offline;
- remains usable with unreliable or absent network connectivity;
- restores queue, track, position, and play/pause intent after head-unit power loss;
- treats physical media controls and system audio controls as first-class inputs;
- uses large, predictable touch targets on unreliable Android head-unit display metrics;
- keeps the approved Windows and standard Android products isolated and regression-safe.

Windows 0.2.2 remains the proven compatibility baseline. Its existing download/playback and `.metrolistbackup` behavior must not regress. The new high-quality-artwork preservation and Car Library folder exporter are explicitly scoped Windows additions because they remove the high-volume import bottleneck; they must be implemented alongside, not by rewriting, the proven pipeline.

## 2. Target hardware and operating assumptions

Primary target:
- AC8257V/WAB family, alps UJC201_64;
- ARM64;
- approximately 6 GB RAM;
- approximately 128 GB storage;
- Android capabilities treated as API 28/29-class behavior even if firmware branding is misleading;
- fixed landscape head-unit display;
- touch plus physical head-unit/steering-wheel controls;
- large offline libraries are transferred into the head unit's persistent local storage before normal use; the transfer medium itself is not part of the runtime architecture.

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

## 12. Library transfer entry points

Car exposes two distinct paths:
- **Open Car Library Folder** — preferred for normal Windows -> car use. The MetroList Car Library folder is first transferred into the head unit's persistent local storage. The user selects that local folder in Car; Car reads its manifest/index and uses the media in place without copying the full audio library into app-private storage.
- **Import Windows Backup** — legacy/compatibility backup-and-restore path for `.metrolistbackup`.

The existing archive importer remains available for backups, migrations, recovery, and compatibility. It is no longer the preferred high-volume transfer workflow.

For folder libraries, the selected folder in head-unit persistent storage is the authoritative media location. Car may maintain a small metadata/index/artwork cache for fast startup, but it must not silently duplicate the audio payload. If the folder is moved, deleted, corrupted, or access is lost, Car reports the library as unavailable and can recover when the same `libraryId` is selected/found again.

Car Library must have a stable library identity and a versioned manifest. Relative paths are used so the exported folder remains valid if it is copied or moved to a different valid location in head-unit storage.

The local folder should be selected/accessed using the safest storage mechanism supported by the target Android firmware. Storage Access Framework may be used for persistent folder access where appropriate; the design must not depend on removable-media mount behavior.

The legacy archive importer accepts `.metrolistbackup` and works directly from the selected document URI. It must not require a second full-size copy of a multi-gigabyte archive inside app storage.

Legacy archive import runs in a dedicated foreground service with persistent progress reporting. It does not depend on WorkManager, JobScheduler, or PlatformScheduler.
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

## 16. High-quality offline artwork

Artwork quality is a product requirement, not a storage optimization target. Windows should preserve the best practical source artwork instead of exporting a low-resolution thumbnail merely to save space.

For new downloads/exports:
- prefer the highest-quality artwork URL/source available from the upstream metadata;
- target approximately 1000x1000 or better when the source provides it;
- never upscale a small thumbnail and call it high quality;
- do not downscale a better original merely to reduce export size;
- preserve enough metadata to distinguish the source/original artwork from UI thumbnails.

Car Library stores high-quality artwork alongside the portable library so it remains available offline. Car may generate/cache smaller derivatives for dense list rows, but the large Now Playing surface must resolve to the high-quality local artwork when available.

For legacy `.metrolistbackup` imports, Car stores imported artwork in an app-owned deterministic location such as:
`filesDir/car-artwork/<songId>.img`

Artwork resolution order:
1. high-quality local Car Library artwork from the active head-unit library folder;
2. imported legacy local artwork;
3. original remote thumbnail URL;
4. placeholder.

The original remote thumbnail URL remains unchanged in song metadata.
## 17. MetroList Car Library folder contract

The preferred transfer format is a normal folder tree rather than a monolithic archive. A representative layout is:

```text
MetroList Car Library/
├── library.json
├── media/
├── artwork/
└── metadata/
```

The exact manifest schema is versioned before implementation. It must contain enough information for Car to build the usable catalog without scanning every audio file or extracting metadata from every media payload.

At minimum the index represents:
- stable library ID and format version;
- song ID;
- title, artist, album, duration, and ordering data;
- relative media path;
- relative high-quality artwork path when available;
- playlists and playlist ordering where exported;
- truthful audio format metadata already known by Windows;
- integrity/size information needed to detect missing or damaged files.

Windows export is incremental. Re-exporting an existing Car Library copies/writes only new or changed media/artwork/metadata and atomically replaces the final index after the export is internally consistent. Unchanged multi-gigabyte media is not rewritten.

Car opening/refreshing a library is index-first. It should become browsable from the manifest quickly even with thousands of tracks. Refresh compares library/index identity and changed entries instead of performing a mandatory full import.

The folder contract must use portable filenames/relative paths compatible with transfer from Windows into the target Android head unit's local storage. The design must not rely on a stable Windows drive letter or a particular Android absolute storage path.

The existing `.metrolistbackup` format remains unchanged and supported as a separate compatibility path.

## 18. Import interruption and recovery

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

## 19. Online capabilities

Car 0.1.0 retains selected online capabilities, but they are secondary to local playback.

Required product surfaces:
- Downloads;
- Library;
- Playlists;
- Search;
- Online browsing;
- Player;
- Queue;
- Open Car Library Folder / Refresh Library;
- legacy portable backup import;
- Settings;
- diagnostics.

Features such as Cast, updater UI, recognition, Listen Together, social integrations, and experimental utilities are not exposed in the initial Car shell.

They do not need to be physically deleted from shared source code for 0.1.0 unless they cause a demonstrated runtime problem.
## 20. Diagnostics

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

## 21. Test strategy

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

Car Library contract/integration tests cover:
- thousands-of-tracks manifest parsing without mandatory media-file scanning;
- persistent access/reopen behavior for the selected local Car Library folder;
- relative-path resolution when the Car Library folder is copied/moved within valid head-unit storage;
- missing/moved/corrupt local-library file behavior and recovery when the same library is selected/found again;
- incremental refresh with unchanged media left untouched;
- missing/corrupt media detection without making the rest of the library unusable;
- high-quality artwork selection and smaller derived UI-cache behavior;
- playlist/order preservation;
- exFAT/FAT-safe relative paths and filenames.

Car-specific tests verify:
- Car launcher;
- Car version wiring;
- scheduler is null only for Car;
- viewport scaling;
- physical input routing;
- volume events are not consumed;
- playback-state restore semantics;
- Back does not stop music.
## 22. Regression gates

Existing Android products remain green with:
`testFossDebugUnitTest`
`testGmsDebugUnitTest`
`testIzzyDebugUnitTest`
`assembleFossDebug`
`assembleGmsDebug`
`assembleIzzyDebug`

Car adds its own unit/integration/assembly gates.

Windows/portable tests remain green because Windows 0.2.2 is the compatibility producer.

The existing Windows 0.2.2 download/playback/backup architecture remains the compatibility baseline. The new Car Library exporter and higher-quality artwork preservation are deliberate new compatibility features and must be isolated from the proven download pipeline. The legacy `.metrolistbackup` contract remains backward compatible.

## 23. Physical head-unit acceptance sequence

After local automated gates pass and only with explicit user authorization:
1. install a versioned Car APK;
2. verify launcher/display/touch sizing;
3. run physical-button diagnostics and record real key/scan codes;
4. verify system volume buttons remain system-controlled;
5. open a small real Car Library folder already transferred into head-unit storage and verify it becomes browsable without duplicating its audio payload;
6. verify high-quality local artwork, metadata, playlists, and direct media playback;
7. disable network and verify playback, seek, next/previous, queue;
8. restart the app/head unit and verify the same local Car Library reopens without reimporting or duplicating its audio payload;
9. refresh an incrementally updated Car Library and verify unchanged media is not re-imported/copied;
10. power-cycle or reproduce head-unit sleep/restart and verify queue/track/position plus playing-vs-paused restoration;
11. test a small legacy `.metrolistbackup` import and interrupted-import recovery;
12. open the large real Car Library and verify startup/refresh performance, counts, storage use, and playback.

Confirmed non-standard key mappings may be added in a subsequent versioned Car build after measurement.

## 24. Safety and release rules

Do not reset, clean, or delete unrelated user data.

Do not push or install an APK without explicit user authorization.

Every APK intentionally handed to the user gets a new Car semantic version.

Do not change the Windows 0.2.2 portable format without an explicit versioned compatibility decision.

Root-cause failures rather than bypassing them with destructive cleanup.

Behavior changes use tests first where practical, and completion claims require fresh verification evidence.
## 25. Non-goals for Car 0.1.0

Car 0.1.0 does not aim to:
- redesign the standard Android mobile app;
- replace Android's system volume UI;
- emulate Android Auto as the primary display surface;
- guess CANBUS/vendor key mappings;
- refactor the proven Windows 0.2.2 download/playback pipeline;
- replace or redesign the legacy portable backup format;
- delete every unused mobile feature from shared source;
- introduce a new Room schema without demonstrated need.

## 26. Success criteria

Car 0.1.0 is successful when:
- it installs independently from the standard Android app;
- its UI is reliably usable on the target head unit despite bad DPI reporting;
- physical standard media controls work through MediaSession;
- system volume remains controlled by the head unit;
- measured non-standard buttons can be diagnosed and mapped safely;
- Windows can produce a versioned Car Library folder with high-quality source artwork and an index suitable for thousands of tracks;
- Car can open that folder directly from head-unit persistent local storage without duplicating the audio payload into app-private storage;
- the folder library becomes browsable quickly from its index and supports incremental refresh;
- high-quality local artwork and audio work with network disabled;
- app/head-unit restart preserves access to the same local library, and moved/lost folders produce a clear recoverable unavailable state;
- a Windows 0.2.2 portable backup still imports without direct cache-file hacks;
- legacy imported songs appear as real completed Media3 downloads;
- large legacy imports can resume after interruption;
- legacy reimport is safe and idempotent;
- playback restores queue, track, position, and prior playing/paused intent after restart;
- standard Android and Windows regression gates remain green.

This revision supersedes the transfer/artwork assumptions in the existing Car implementation plan. Per the Superpowers design gate, the implementation plan must be revised only after this written spec revision is reviewed and approved. No product implementation is authorized by this documentation update.
