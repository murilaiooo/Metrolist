# MetroList Ecosystem Contract Design

Date: 2026-09-27

Status: **user-reviewed and approved for implementation on 2026-09-27**

## 1. Purpose

MetroList is one product ecosystem with two specialized clients:
- **MetroList Windows** for account access, discovery, playlist work, large-scale downloads, library preparation, and transfer;
- **MetroList Car** for fast, touch-first, offline-first playback on the Android head unit.

The two clients must share stable meanings for music identity, metadata, playlists, offline availability, artwork, and transfer contracts without forcing all internal state to synchronize.

This document is the authoritative cross-product contract. Car-only and Windows-only specs must conform to it.

It was created after:
`docs/superpowers/reviews/2026-09-27-metrolist-ecosystem-integration-audit.md`.

Implementation is authorized by the user as of 2026-09-27. Changes must still follow this contract and the task-level verification gates.

## 2. Governing principle

Every new MetroList feature or data field must be classified before implementation as exactly one of:

1. **Shared portable contract**
2. **Platform-local state**
3. **Online-account state**
4. **Derived/cache state**

If ownership is unclear, implementation stops until ownership is decided.
## 3. Ownership classes

### 3.1 Shared portable contract

Data intentionally transported from Windows to Car.

Examples:
- canonical song identity;
- title, ordered artists, album identity/name;
- duration and explicit flag;
- truthful audio format metadata;
- relative media path inside the Car Library;
- high-quality artwork path;
- Windows-exported playlist snapshots and ordering;
- Car Library format/library/revision identity.

The portable contract is versioned independently from both product versions.

### 3.2 Platform-local state

State that belongs to one installation/device.

Examples:
- current queue;
- current track position;
- playing/paused state;
- UI preferences;
- Car physical-button observations;
- Windows window/layout preferences;
- Car-local playlists;
- Car-local downloads;
- local listening history and play-time statistics unless explicitly promoted later.

Platform-local state is never silently written into a portable library.

### 3.3 Online-account state

State owned by YouTube Music or another remote account.

Examples where supported:
- remote playlists;
- remote likes/library membership;
- account profile/session.

Windows and Car authenticate independently. Credentials are never transferred through Car Library or `.metrolistbackup`.

### 3.4 Derived/cache state

Rebuildable implementation data.

Examples:
- resized artwork thumbnails;
- local search indexes;
- parsed manifest caches;
- content-URI resolution caches;
- transient hashes;
- UI paging/sort caches.

Derived state must never become the only source of truth.
## 4. Product boundaries

### Windows responsibilities

Windows is the preferred producer for large offline collections.

It owns:
- desktop authentication/session storage;
- discovery and search UX;
- bulk downloading and resume;
- local Windows playlist editing/import;
- optional explicit sync of Windows local playlists to YouTube Music;
- Car Library export;
- high-quality artwork preservation;
- legacy `.metrolistbackup` export.

Windows must not assume the final Car filesystem path. It exports a portable folder with relative paths; that folder is then transferred into the head unit's persistent local storage.

### Car responsibilities

Car owns:
- car-specific UI/navigation;
- playback state and queue restoration;
- physical media-key handling;
- persistent local Car Library access from head-unit storage;
- Car-local download state;
- Car-local playlists;
- Car diagnostics;
- optional Car-side online account session.

Car must remain useful with no network and no signed-in account.

### Shared responsibility

Windows and Car independently implement and test the same versioned data contracts.

They share semantics, not credentials or mutable internal databases.
## 5. Canonical media identity

For YouTube/MetroList-origin songs, the canonical logical song ID is the stable video/song ID already used by the existing Android and Windows paths.

The same logical song ID must represent the same musical item across:
- Windows library/downloads;
- Car Library;
- legacy portable backup;
- Android Room entities;
- Media3 download/cache identities where applicable.

A physical file path, cache filename, document URI, Windows drive letter, or Android storage path is never a logical song identity.

### 5.1 Source instance identity

A logical song may have multiple playable source instances.

Each source instance is identified by:
- logical song ID;
- source kind;
- source-specific locator;
- source revision/availability where applicable.

Different files for the same song do not create duplicate catalog songs.

## 6. Car source kinds

Car recognizes these source kinds:

- `CAR_LIBRARY_LOCAL`
- `LEGACY_IMPORTED`
- `CAR_LOCAL_DOWNLOAD`
- `ONLINE`

The logical catalog deduplicates by canonical song ID while retaining all available source instances.
## 7. Playback source precedence

For the same logical song, Car chooses a source deterministically.

Default precedence:

1. available `CAR_LIBRARY_LOCAL`;
2. complete `CAR_LOCAL_DOWNLOAD`;
3. complete `LEGACY_IMPORTED`;
4. `ONLINE` when network/account conditions permit.

Rationale:
- the Car Library stored in the head unit's persistent local memory is the preferred everyday Windows -> Car workflow;
- Car-local downloads are a durable local fallback;
- legacy imported backups remain supported but are not the preferred bulk path;
- network playback is last when an offline source exists.

### 7.1 Failure fallback

If the chosen source becomes unavailable before playback begins, Car may try the next valid source automatically.

If the local Car Library source becomes unavailable because the folder was moved, deleted, corrupted, or permission/access was lost:
- Car reports the source as unavailable;
- it may fall back to another complete local source for the same song;
- it must not silently start network playback when the user is offline or online playback is unavailable;
- the UI must explain when playback cannot continue.

### 7.2 Metadata precedence

Core canonical metadata comes from the active catalog record.

Source-specific format/availability metadata comes from the selected source.

No source may overwrite a higher-quality or more authoritative field merely because it was observed later.
## 8. Unified Car catalog

Car must expose one logical catalog to:
- Library;
- Offline/Downloads;
- Search;
- playlists;
- queue construction;
- Now Playing;
- diagnostics.

The catalog combines:
- active local Car Library index;
- Car-local Room/download state;
- legacy imported content;
- optional online results.

It must not require Car Library audio already present in head-unit storage to be duplicated into Media3 cache or represented as a fake completed download.

### 8.1 Offline meaning

A logical song is **offline available** when at least one complete non-network source is available.

The UI may show provenance such as:
- Car Library;
- Downloaded on Car;
- Imported backup.

The user should not have to understand Media3 internals.

### 8.2 Car home/default destination

The long-term Car information architecture should center on the user's playable music, not on the implementation term `Downloads`.

The current `DOWNLOADS` destination may remain during implementation, but the final product should use a user-facing offline/library concept that naturally includes Car Library tracks.

This spec does not require renaming before the first functional integration test, but it forbids treating Car Library tracks as Media3 downloads internally.
## 9. Search contract

Car local search queries the unified logical catalog.

It must return:
- active local Car Library songs;
- Car-local downloads;
- legacy imported songs;
- Car-local playlists where supported.

The same logical song appearing in multiple sources appears once in search results.

Online search is a separate capability and may be merged visually with local results only if provenance remains clear.

Opening a Car Library must not require scanning every audio payload before local search becomes usable.

The external manifest/index is the primary startup search input.

## 10. Playlist ownership

### 10.1 Windows local playlists

Windows local playlists remain Windows-owned editable entities.

When exported through Car Library, they become **read-only playlist snapshots on Car v1**.

The snapshot carries:
- stable exported playlist ID;
- name;
- ordered logical song IDs;
- optional linked YouTube playlist ID as metadata;
- source revision.

Car does not write playlist edits back into the Windows-produced Car Library stored on the head unit.

### 10.2 Car-local playlists

Car may create/edit its own local playlists.

Car-local playlists are platform-local in v1 and are not written back to Windows.

A future bidirectional playlist protocol requires a new design/version.

### 10.3 Online playlists

YouTube Music playlists remain online-account state.

Edits occur through the remote service and are not represented as implicit mutations to Car Library.
## 11. Likes, library membership, history, and statistics

### Likes and remote library membership

When backed by YouTube Music and the user is signed in, likes/library membership are online-account state.

Each client may read/change that remote state through its own authenticated session.

Car Library v1 does not transport account likes or account credentials.

### Local favorites

MetroList v1 does not introduce a new cross-device local-favorite protocol.

Any platform-only favorite/bookmark state remains local unless later promoted through a versioned design.

### Listening history and play counts

Current playback history, play-time totals, recents, and statistics are platform-local.

They are not exported in Car Library v1.

This avoids Windows and Car racing to merge counters without a defined synchronization protocol.

## 12. Queue and playback position

Queue, current index, seek position, repeat mode, shuffle state, and playing/paused intent are platform-local.

Car persists and restores its own playback state across head-unit restart.

Windows keeps its own player/queue state.

There is no Windows -> Car “continue playback from desktop” feature in v1.
## 13. Authentication and account behavior

Car Library, local search, local playlists, and offline playback must work without login.

Car uses its own application ID and stores its own account/session state.

### 13.1 Credential isolation

Windows credentials/session data are never copied into:
- Car Library;
- `.metrolistbackup`;
- artwork metadata;
- diagnostics exports.

Car authentication is independent.

### 13.2 Car online mode

If online browse/search/account actions are exposed, Car must provide an explicit account state:
- signed out;
- signing in;
- signed in;
- session invalid/error.

No indefinite loading is allowed when authentication is absent.

### 13.3 Anonymous online behavior

Online features that the underlying service supports anonymously may work without login.

Features requiring account state must clearly request/indicate sign-in.

The exact head-unit login UX may be designed separately, but its state contract is fixed here.

## 14. Car-local downloads

A download initiated on Car becomes `CAR_LOCAL_DOWNLOAD`.

It is stored in Car-owned Android/Media3 state and never written into the Windows-produced Car Library folder.

If the same song already exists in the active Car Library:
- the logical catalog still shows one song;
- both source instances may coexist;
- playback precedence follows Section 7.
## 15. MetroList Car Library v1

Car Library is the preferred high-volume Windows -> Car transfer format.

Representative layout:

```text
MetroList Car Library/
├── library.json
├── media/
├── artwork/
└── metadata/
```

### 15.1 Required manifest identity

`library.json` includes at minimum:
- `formatVersion = 1`;
- stable `libraryId`;
- monotonic or uniquely comparable `revision`;
- `createdAt`;
- `updatedAt`;
- Windows producer product/version;
- song collection;
- playlist snapshots;
- relative file references.

Product SemVer and Car Library format version are separate.

### 15.2 Song record

Each song record includes:
- canonical song ID;
- title;
- ordered artist references with IDs/names when known;
- album ID/name when known;
- duration;
- explicit flag;
- video/episode flags where applicable;
- relative media path;
- relative high-quality artwork path when available;
- truthful known audio format metadata;
- file size;
- integrity metadata.

Absolute filesystem paths are forbidden.
### 15.3 Artwork

Windows preserves the best practical source artwork offered upstream.

Target behavior:
- prefer approximately 1000x1000 or larger when available;
- preserve better originals rather than downscaling for storage savings;
- never upscale a small thumbnail and label it high quality;
- retain original/remote artwork metadata when useful.

Car:
- uses high-quality local artwork for Now Playing/detail views;
- may derive smaller thumbnails for lists;
- never replaces the high-quality source with the derived thumbnail.

### 15.4 Path portability

All manifest paths are normalized relative paths.

Reject:
- drive-letter paths;
- absolute Windows paths;
- absolute Android paths;
- traversal with `..`;
- paths escaping the selected root.

The same library remains valid when:
- the Windows-side export/transfer location changes;
- the Car Library folder is copied or moved to a different valid location in the head unit's persistent storage.

### 15.5 One active Car Library in v1

Car v1 supports exactly **one active Car Library at a time** in the head unit's persistent local storage.

The app may remember the library identity and last known location for recovery diagnostics, but search/playback/catalog resolve against one active local Car Library.

Supporting multiple simultaneous Car Libraries requires a later design because it changes dedupe, playlists, search, and UX.
## 16. Incremental export and removal semantics

The manifest is authoritative for the current Windows-produced revision.

Windows re-export:
- reuses unchanged media/artwork;
- writes only new/changed artifacts;
- writes changed files through safe temporary paths;
- publishes the new final manifest only after referenced artifacts are consistent.

### 16.1 Removal

If a song or playlist is absent from the new final manifest, it is removed from the logical exported revision.

Car refresh removes it from the active external catalog snapshot.

Windows may clean orphan files after the new manifest is safely committed, but orphan cleanup must never invalidate the previous manifest during an interrupted export.

### 16.2 Failed export

If export fails before final manifest promotion:
- the previous final manifest remains authoritative;
- Car continues to see the previous valid revision;
- temporary/incomplete artifacts are ignored.

### 16.3 Refresh

Car refresh compares:
- `libraryId`;
- revision;
- manifest entries.

Refresh updates the lightweight index and availability state without copying unchanged audio.

## 17. Integrity and startup performance

Opening a Car Library is index-first.

Startup validation checks:
- manifest parse/version;
- safe relative paths;
- duplicate IDs/path conflicts;
- required metadata structure.

It must not SHA-256 every audio file before the library becomes browsable.
### 17.1 File availability checks

Car may lazily check existence/size:
- when a track becomes visible;
- before playback;
- during background/explicit verification;
- during refresh of changed entries.

### 17.2 Cryptographic integrity

Hashes are used for:
- Windows export correctness;
- changed-file identity where useful;
- explicit/deep verification;
- diagnosing suspected corruption.

A missing or corrupt file marks only the affected source/track unavailable unless the manifest itself is invalid.

One bad audio file must not make the whole library unusable.

## 18. Persistent local-library lifecycle

The normal operating assumption is that the Car Library has already been transferred into the head unit's persistent local storage before use.

Car opens that local folder in place and does not duplicate its multi-gigabyte audio payload into app-private storage.

If Android requires a document-tree permission for that local folder, Car persists the permission. Otherwise it may use the platform's appropriate local-storage access path for the target head unit.

If the folder is moved, deleted, corrupted, or access is lost:
- the Car Library source becomes unavailable;
- its logical index may remain cached for diagnostics/recovery state;
- Car-local/legacy/online sources remain usable.

When the same `libraryId` is selected/found again:
- Car recognizes it as the same library;
- refreshes the revision;
- restores availability without importing/copying the audio into app-private storage.

USB/removable media is treated only as an optional transfer mechanism and is not part of the normal runtime architecture.

## 19. Legacy .metrolistbackup contract

The existing `.metrolistbackup` format v1 remains unchanged.

It is used for:
- backup/restore;
- migration;
- compatibility;
- recovery;
- cases where direct-folder use is undesirable.

Legacy import may populate Room/Media3/artwork state.

It is not the preferred bulk Windows -> Car everyday workflow.
## 20. Catalog deduplication rules

The unified Car catalog groups records by canonical song ID.

For each logical song it maintains:
- canonical display metadata;
- available source instances;
- source-specific audio metadata;
- source availability;
- artwork candidates.

### 20.1 Display metadata selection

Default order for display metadata:
1. active Car Library canonical metadata when present;
2. existing trusted Room/local metadata;
3. legacy portable metadata;
4. online metadata.

A later source may fill missing fields but should not degrade known data.

### 20.2 Artwork selection

Default artwork order:
1. high-quality active Car Library local artwork;
2. Car-local durable artwork;
3. legacy imported local artwork;
4. remote artwork;
5. placeholder.

## 21. Diagnostics contract

Car diagnostics must expose enough information to diagnose interoperability without exposing credentials.

Include:
- Car product version;
- Android/device/API identifiers;
- active source mode;
- active `libraryId`;
- Car Library format version;
- library revision;
- Windows producer version when available;
- Car Library local-path/access state;
- Car Library song count;
- Car-local download count;
- legacy imported count;
- unavailable/missing Car Library file count;
- latest import/refresh error;
- latest physical key observation.

Never include cookies, auth headers, tokens, or session secrets.
## 22. Error model

Cross-product failures should be typed by user-relevant cause, including:
- unsupported library format;
- invalid manifest;
- unsafe path;
- library disconnected;
- permission lost;
- media file missing;
- media file corrupt;
- insufficient storage for legacy import/local download;
- authentication required;
- network unavailable;
- source unavailable;
- refresh/export incomplete.

Errors must not mutate unrelated valid content.

An invalid new external revision must not destroy:
- the previous valid external index;
- Car-local downloads;
- legacy imported content;
- platform-local playlists.

## 23. Compatibility/versioning rules

Three version lines remain independent:

1. Windows product SemVer;
2. Car product SemVer;
3. portable data-contract versions.

Current contract tracks:
- legacy `.metrolistbackup` format v1;
- Car Library format v1.

A product PATCH may fix an implementation while keeping the same data format.

Any intentionally incompatible data-contract change requires:
- a new format version;
- explicit compatibility/migration behavior;
- producer/consumer tests;
- release documentation.

Car should reject unsupported future formats clearly rather than guessing.

## 24. Golden contract tests

Windows and Car must test common golden fixtures for Car Library v1 semantics.

Required fixture classes:
- minimal valid one-song library;
- large synthetic thousands-of-tracks manifest;
- multiple artists + album;
- playlist/order preservation;
- missing artwork;
- high-quality artwork selection;
- duplicate song ID;
- duplicate/unsafe relative path;
- missing media file;
- corrupt media file;
- revision with additions;
- revision with removals;
- interrupted export preserving old manifest;
- Car Library copied/moved to a different valid local storage location;
- unsupported future format.
## 25. Cross-product change rule

Before implementing a feature touching any of these areas:
- identity;
- library;
- playlists;
- downloads/offline state;
- search;
- artwork;
- account state;
- portable formats;
- playback source selection;

the implementation must answer:

1. What ownership class does this data belong to?
2. Does Windows produce/change it?
3. Does Car consume/change it?
4. Does it cross the portable boundary?
5. What happens when both products have state for the same logical item?
6. What compatibility test prevents drift?

If these answers are absent, the change is not ready for implementation.

## 26. Non-goals for v1

This contract does not introduce:
- real-time Windows <-> Car networking;
- cloud sync operated by MetroList;
- bidirectional Car-local playlist sync;
- cross-device queue handoff;
- merged listening-history counters;
- simultaneous multiple Car Libraries;
- credential/session transfer from Windows to Car;
- automatic modification of the Windows-produced Car Library by Car.

These may be designed later as versioned features.

## 27. Migration of existing plans

Committed Car Tasks 1-6 remain valid unless a specific conflict is identified.

Existing uncommitted legacy-import Task 7 work must be preserved and reviewed, not discarded.

The original Car plan's later assumptions about `database.downloadedSongs(...)` are superseded where they conflict with the unified catalog.

The Car Library planning draft is not executable until rewritten against this spec.

The revised Car-specific design remains subordinate to this ecosystem contract on cross-product questions.
## 28. Acceptance criteria

The ecosystem contract is satisfied when:

1. Windows and Car use the same canonical song identity.
2. Car presents one logical song even when several source instances exist.
3. Car Library tracks stored in head-unit memory appear in local catalog/search without being duplicated into app-private audio storage.
4. Source precedence and fallback are deterministic.
5. Windows playlist snapshots appear on Car with ordering intact and are read-only in v1.
6. Car-local playlists remain local.
7. Car-local downloads coexist with Car Library and legacy sources without duplicate logical songs.
8. Car Library and offline playback work without login.
9. Windows and Car credentials remain isolated.
10. Likes/history/queue ownership is explicit and no hidden cross-device sync is implied.
11. Incremental export supports add/change/remove while preserving the previous valid revision on failure.
12. App/head-unit restart reopens the persistent local Car Library without reimporting or duplicating the multi-gigabyte audio library.
13. Car starts from the manifest/index without hashing/scanning all audio before browsing.
14. High-quality artwork remains available to Now Playing.
15. Legacy backup remains supported.
16. Diagnostics identify product, contract, library, revision, provenance, and availability without secrets.
17. Golden contract tests run on both producer and consumer sides.
18. Windows 0.2.2's proven download/playback architecture is not rewritten merely for Car integration.

## 29. Documentation authority order

For current development, precedence is:

1. this ecosystem contract for Windows <-> Car semantics;
2. current Car design for Car-specific behavior;
3. current Windows V2 design/release baseline for Windows-specific behavior;
4. reconciled implementation plans produced after this spec is approved;
5. historical handoffs for provenance only.

Any older document that says `.metrolistbackup` is the sole Windows <-> Car boundary is historical. It remains the legacy compatibility boundary, while Car Library v1 is the preferred high-volume transfer contract.

## 30. Review gate

This spec must be reviewed and approved by the user before implementation planning resumes.

After approval, use Superpowers `writing-plans` to reconcile:
- `docs/superpowers/plans/2026-09-27-metrolist-car-apk.md`;
- `docs/superpowers/plans/2026-09-27-metrolist-car-library.md`;
- current uncommitted legacy-import work.

No product implementation is authorized by the creation of this document.
