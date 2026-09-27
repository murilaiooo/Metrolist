# MetroList Ecosystem Integration Audit

Date: 2026-09-27

## Scope

Revision note: the runtime storage assumption was clarified after this audit. The normal Car Library lives in the head unit's persistent local storage. USB/removable media, if used at all, is only a transfer mechanism and is not the normal playback source.

This audit reviews MetroList as one ecosystem with two product surfaces:
- MetroList Windows;
- MetroList Car.

The review covers current code, current working-tree state, specs, plans, handoffs, versioning, portable contracts, library/playlist models, playback, search, artwork, authentication, storage, diagnostics, and release boundaries.

This is a documentation/review artifact only. It does not authorize implementation, packaging, installation, destructive cleanup, or changes to user data.

## Executive verdict

MetroList is **not yet fully specified as one coherent Windows + Car ecosystem**.

The strongest integrated path is:
Windows offline download -> legacy portable backup -> Car validation/import -> Android Room/Media3 playback.

The project also has a good Car product boundary, independent Car versioning, car-specific playback shell, physical-input handling, and a stable Windows 0.2.2 baseline.

However, important cross-product behaviors remain undefined or contradictory. Continuing implementation without resolving them would create two apps that work individually but gradually disagree about library meaning, playlists, offline state, account state, search, and source ownership.

## Current repository state observed

Branch: `main`, ahead of `upstream/main`.

Committed Car milestones:
- Car product flavor/version/launcher;
- scheduler + playback restore;
- viewport + input routing;
- car-first playback shell;
- legacy portable backup validator;
- Media3/Room/artwork legacy import targets.

Current HEAD observed:
`ee8fc297b feat(car): import portable media and metadata`.

The working tree also contains uncommitted follow-on legacy-import work plus revised Car design documentation and the new Car Library plan.
## What is already well connected

### 1. Song identity in the legacy path

Windows and Car both use the song/video ID as the core identity for:
- portable song ID;
- cache key;
- Media3 request ID;
- download identity.

This is a strong compatibility boundary.

### 2. Truthful audio-format metadata

Windows exports known format data such as:
- itag;
- MIME type;
- codecs;
- bitrate;
- sample rate;
- content length;
- loudness metadata.

Car maps those values into the Android `FormatEntity` instead of inventing quality values.

### 3. Legacy backup validation

Windows writes a versioned `.metrolistbackup` profile and Car has its own reader/validator.

This gives producer/consumer separation and real contract testing.

### 4. Car playback infrastructure

Car reuses the mature Android playback stack:
- Media3;
- MusicService;
- MediaSession;
- Room;
- queue state;
- download cache.

The car-specific UI is isolated in the Car flavor instead of turning the mobile UI into a runtime car mode.

### 5. Product version isolation

Windows and Car have independent semantic versions:
- Windows: `metrolistWindowsVersion`;
- Car: `metrolistCarVersion`.

This is correct and should be preserved.
## Critical integration gaps

### C1. No single ecosystem contract exists

There is no central document that defines, for every feature/data type, whether it is:
- shared between Windows and Car;
- Windows-owned;
- Car-owned;
- online-account-owned;
- derived/cache-only.

Without this ownership model, new features can accidentally be implemented differently on each side.

A dedicated ecosystem contract is required before further architectural implementation.

### C2. The new direct Car Library and the existing Car data model are not unified

The new design says Car should read media directly from a selected folder without copying audio into app-private storage.

But existing Car concepts still assume offline music is represented by:
- Room `SongEntity.isDownloaded`;
- Media3 completed downloads;
- `database.downloadedSongs(...)`.

Car Library tracks stored in head-unit local memory are not Media3 downloads.

Therefore the Car app currently has two incompatible concepts of “offline music” unless a unified catalog/source abstraction is defined.

### C3. Local search does not include the future local Car Library

The existing Android local search is Room/database based.

The new direct-library design proposes a lightweight index over the Car Library folder in head-unit storage rather than importing every Car Library song as a completed Room/Media3 download.

Without an explicit unified search/catalog layer, Car search would omit Car Library tracks or force the direct library back into the old import architecture.

### C4. Authentication/account behavior is undefined for Car

Car has its own application ID and therefore its own Android app storage/DataStore.

The standard Android `App` loads YouTube account state from that app-private DataStore.

Car currently has no documented sign-in/account UX, yet the spec says Car retains online search/browse capabilities.

The project must explicitly define:
- whether Car login is optional;
- how the user signs in on the head unit;
- whether Windows and Car credentials are intentionally separate;
- which online features work anonymously;
- what happens when Car is offline or not signed in.

Credentials/cookies must never be transferred inside Car Library or portable media.
### C5. Playlist ownership and edit semantics conflict

Windows V2 documentation describes local playlists as Windows-owned.

The new Car Library design says playlists and ordering should be exported to Car.

What is not defined:
- whether exported Windows playlists are read-only snapshots on Car;
- whether Car may edit them;
- whether Car-created playlists ever flow back to Windows;
- how a linked YouTube playlist ID is represented;
- what happens when Windows removes/reorders a playlist after Car has local changes.

A one-way or bidirectional ownership rule must be explicit before playlist UI is implemented.

### C6. “Downloads” is no longer an accurate primary Car source model

Current Car navigation starts at `DOWNLOADS`.

The new preferred workflow is a Car Library folder transferred into the head unit's persistent local storage, containing tracks downloaded on Windows rather than on Car.

The UI/data model must decide whether the primary concept is:
- Offline Library;
- Music;
- Sources;
- or a unified Downloads/Offline surface.

Otherwise the app will present Car Library files as “downloads” even though they are not downloads created by Car.

### C7. Playback source precedence is undefined

The same song ID can potentially exist in several places:
- active local Car Library;
- legacy imported Media3 cache;
- Car-local download;
- online stream.

The project needs one deterministic policy for:
- catalog deduplication;
- preferred playback source;
- fallback when the local Car Library folder/file becomes unavailable;
- artwork source;
- format metadata source.

Without this, duplicate rows and unpredictable playback are likely.

### C8. The original Car implementation plan is now partially obsolete

The original plan assumes legacy import is the main offline path and says Windows should not change.

The revised design and new Car Library plan deliberately add:
- Windows Car Library export;
- higher-quality artwork preservation;
- direct playback from the Car Library stored in head-unit local memory.

The original plan is still useful for completed Car Tasks 1-6 and legacy fallback work, but its later transfer/UI assumptions are no longer authoritative.
## Important integration gaps

### I1. Favorites / likes / library membership

Android has song fields such as:
- `liked`;
- `likedDate`;
- `inLibrary`.

The Windows library snapshot does not model these as a cross-product contract.

Car design mentions a favorite/library action “where appropriate,” but no ownership/sync behavior is defined.

These states must be explicitly classified as shared, online-account-owned, or platform-local.

### I2. Listening history / statistics

Android stores play-time and other library state.

No Windows <-> Car contract exists for listening history, play counts, statistics, or recent playback.

If intentionally local, that must be documented.

### I3. Queue and playback position across products

Car correctly persists its own queue/position across head-unit restart.

Windows has a separate player/queue implementation.

There is no cross-device queue handoff contract.

This is acceptable if queue/position are intentionally platform-local, but that rule is not centrally documented.

### I4. Car-originated downloads

The Car product retains Android download infrastructure.

The project does not yet define how a song downloaded directly on Car coexists with:
- the local Car Library;
- a legacy imported backup;
- a future refreshed Windows library.

Car-local downloads need explicit provenance and must not silently mutate the Windows-produced Car Library folder.

### I5. Incremental deletion semantics

The Car Library plan defines incremental additions/changes but does not fully specify removals.

Required behavior includes:
- song removed from Windows library;
- playlist deleted;
- track removed from playlist;
- artwork replaced;
- stale/orphan files in the transferred Car Library folder.

The final manifest must be authoritative, and cleanup must never make the previous valid revision unusable mid-export.

### I6. Multiple Car Libraries

The docs do not say whether Car v1 supports:
- exactly one active Car Library in head-unit storage;
- multiple simultaneous Car Libraries;
- switching libraries.

This affects IDs, search, duplicate handling, UI, persistent local-folder access, and diagnostics.
### I7. Contract version compatibility matrix

Product SemVer is documented, but data-contract compatibility is not fully governed.

The ecosystem needs separate versioning for:
- legacy backup format;
- Car Library folder format.

Diagnostics should be able to report:
- Car product version;
- Windows producer version;
- Car Library format version;
- library ID;
- library revision.

Incompatible format changes must not be hidden inside a product PATCH.

### I8. Rich album/artist schema is not exact yet

The legacy portable profile intentionally leaves album arrays/maps empty and stores only song-level album ID/name.

The new Car Library should not inherit that limitation accidentally.

The exact v1 schema still needs to define:
- ordered artist references;
- artist IDs/names;
- album ID/name;
- optional album artwork;
- missing IDs;
- episodes/videos.

### I9. Integrity vs startup performance

The new design correctly says Car should become browsable quickly from `library.json`.

It also wants integrity information.

The missing rule is when Car verifies:
- path existence;
- file size;
- cryptographic hash.

Hashing thousands of tracks on every startup would defeat the direct-library design.

Validation must distinguish index validation, lightweight availability checks, and lazy/deep integrity checks.

### I10. Car diagnostics are missing ecosystem fields

Current diagnostics planning focuses on device/import state.

Direct-library diagnostics should also expose:
- active source mode;
- active library ID/revision;
- format version;
- producer version;
- local Car Library path/access state;
- missing-file count;
- Car Library/Car-local/legacy source counts.

### I11. Shared Android App initialization is broader than the Car product

The Car flavor uses the shared Android `App` class.

That class initializes settings and optional services used by the mobile product, including online/account-related infrastructure.

This is not automatically wrong, but the Car isolation boundary should explicitly define what is allowed to initialize in the head-unit product and what should remain dormant.
## Documentation consistency problems

### D1. Stale Task 6 pause handoff

`docs/superpowers/handoffs/2026-09-27-metrolist-car-apk-task6-paused-handoff.md` says Task 6 is uncommitted and incomplete.

Current HEAD contains:
`ee8fc297b feat(car): import portable media and metadata`.

Therefore that handoff is now a historical snapshot, not current state.

### D2. Old handoffs still describe .metrolistbackup as the sole compatibility boundary

That was true at the time.

The revised architecture now keeps `.metrolistbackup` as legacy backup/restore while introducing Car Library as the preferred everyday transfer boundary.

Historical handoffs should remain for history but should be clearly marked as superseded for current development.

### D3. Original Car plan conflicts with the revised Car Library direction

The original plan says later Car UI should read `database.downloadedSongs(...)` and that Windows product code should not change.

The new architecture requires a new Windows exporter and direct local Car Library catalog.

The two documents need an explicit precedence rule so an implementer cannot accidentally follow the wrong later-stage instructions.

### D4. No current-state handoff exists after Task 6 commit + Task 7 work

The repo needs one current handoff that records:
- committed Car Tasks 1-6;
- uncommitted Task 7 legacy-import work;
- the ecosystem audit;
- the revised Car Library direction;
- the fact that implementation should not proceed until cross-product ownership decisions are approved.
## Recommended ecosystem model

This audit recommends that every MetroList feature be assigned to exactly one of four ownership classes:

1. **Shared portable contract**
   Data intentionally transferred Windows -> Car, such as song identity, core metadata, audio files, artwork, and exported playlist snapshots.

2. **Platform-local state**
   State that belongs to one device/product, such as UI preferences, current queue, current playback position, local cache internals, and device diagnostics unless explicitly promoted later.

3. **Online-account state**
   State owned by YouTube Music or another connected service, reached independently by each product after authentication. Credentials are never transported through MetroList library files.

4. **Derived/cache state**
   Rebuildable indexes, thumbnails, temporary hashes, and performance caches that must never become the only source of truth.

Every future feature should declare its ownership class before implementation.

## Recommended unified Car source model

Car should present one logical catalog while preserving provenance.

Potential source types:
- `CAR_LIBRARY_LOCAL`;
- `LEGACY_IMPORTED`;
- `CAR_LOCAL_DOWNLOAD`;
- `ONLINE`.

One logical song ID may have more than one available source.

The catalog/search/UI should deduplicate by logical song identity and choose playback source deterministically.

This is the missing bridge between the new direct local-library design and the existing Android Room/Media3 architecture.

## Recommended v1 ownership defaults

These are recommendations for the next ecosystem spec, not implemented decisions:
- Windows-exported Car Library playlists: read-only snapshots on Car v1;
- Car-created playlist edits: Car-local unless a future bidirectional protocol is designed;
- queue/position: platform-local;
- likes/history/statistics: platform-local or online-account-owned, not silently embedded in Car Library v1;
- one active Car Library in head-unit storage at a time for v1;
- Car-local downloads remain Car-local;
- Car Library manifest is authoritative for Windows-exported content;
- credentials never cross the Windows/Car file boundary.
## Required next documentation before implementation resumes

The architectural spec has now been written and is awaiting user review/approval:
`docs/superpowers/specs/2026-09-27-metrolist-ecosystem-contract-design.md`

It defines:
- ownership-class rules;
- canonical song/media identity;
- Car source/provenance model;
- unified catalog/search behavior;
- playlist ownership and edit rules;
- likes/history/queue classification;
- Car authentication behavior;
- direct-library manifest schema;
- incremental add/change/remove semantics;
- source precedence/fallback;
- Car Library version compatibility;
- diagnostics contract;
- error/unavailable behavior;
- test/golden-fixture matrix.

Only after that spec is approved should the implementation plans be reconciled.

## Audit conclusion

The project has a strong technical foundation and several well-designed compatibility pieces, but the answer to “is everything between Windows and Car fully interconnected and documented?” is currently **no**.

The remaining risk is not basic playback. The risk is semantic drift: Windows and Car gradually assigning different meanings to library, playlist, downloaded/offline, favorite, search, account, and source state.

This audit makes those loose ends explicit so they can be closed before more product code is added.
