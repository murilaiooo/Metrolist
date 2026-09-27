# MetroList Car Library + High-Quality Artwork Implementation Plan

> **EXECUTION AUTHORIZED 2026-09-27.** Reconciled against the user-approved ecosystem contract in `docs/superpowers/specs/2026-09-27-metrolist-ecosystem-contract-design.md`. Windows 0.2.3 changes only the desktop WebView2/auth profile path and does not change portable media, IDs, export contracts, or Car compatibility assumptions.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a fast folder-based Windows -> Car library path that preserves high-quality artwork, supports incremental export/refresh, and plays media directly from a Car Library folder stored in the head unit's persistent local memory without duplicating the audio payload.

**Architecture:** Keep Windows 0.2.2 download/playback and legacy `.metrolistbackup` behavior intact. Add a versioned `MetroList Car Library` folder contract beside it, with Windows as producer and Car as index-first consumer. The exported folder is transferred into the head unit's persistent local storage; Car opens that local folder in place, keeps only lightweight derived index/cache state, and does not duplicate the audio payload into app-private storage.

**Tech Stack:** Kotlin 2.4.20, Android/Compose/Media3/Room/Coil on Car, existing Windows desktop/export modules, kotlinx.serialization, JUnit/Robolectric.

**Spec:** `docs/superpowers/specs/2026-09-27-metrolist-car-apk-design.md`

## Global Constraints

- Windows 0.2.2 remains the proven compatibility baseline; do not rewrite its working download/playback pipeline.
- Legacy `.metrolistbackup` format v1 remains supported and backward compatible.
- Car Library is a separate versioned folder contract with relative paths and stable library identity.
- Car Library audio is read in place; do not silently copy the multi-gigabyte media payload into app-private storage.
- Prefer source artwork around 1000x1000 or better when available; never upscale a small thumbnail and call it high quality.
- Export/refresh must be incremental and safe for thousands of tracks.
- Relative paths must survive different Windows export locations and the Car Library being copied/moved to another valid location in head-unit storage.
- Implementation is authorized. Do not push, install the APK on the head unit, or perform physical-device mutation without separate authorization.

## Review Focus

1. **Persistent local-library reopen/path change:** the same library ID must reopen after app/head-unit restart, and relative paths must remain valid if the Car Library folder is copied/moved to another valid location in head-unit storage. Task 4 owns the test.
2. **Interrupted incremental export:** old valid `library.json` must remain authoritative until all changed files are durable; a partial export must never publish a half-updated index. Task 3 owns the test.
3. **Large library startup:** opening thousands of tracks must parse the index without mandatory full media scanning/hash verification before browsing. Task 4 owns the test.
4. **Artwork source regression:** a low-resolution cached thumbnail must not replace a better upstream artwork source in a new export. Task 1 owns the test.
5. **Removed/corrupt Car Library file:** one unavailable track must be reported/disabled without making the rest of the library unusable. Task 4 owns the test.

---

## File Structure
Keep the new contract isolated from the legacy archive contract.

Windows-side responsibilities:
- `desktop-core`: select/preserve the best available artwork source metadata without changing audio resolution/download behavior.
- `desktop-portable`: define Car Library v1 manifest models and validation helpers beside existing backup v1 models.
- `desktop-export`: produce/update a normal Car Library directory incrementally and atomically.
- `desktop-app`: expose a distinct `Export Car Library` flow without removing legacy backup export.

Car-side responsibilities under `app/src/car/kotlin/com/metrolist/music/car/library/`:
- manifest models/validation;
- persistent local Car Library folder selection/access;
- lightweight library index/state;
- direct local media path/document-URI resolution;
- refresh/diff behavior;
- availability/error state.

Car UI changes remain under the existing car UI package and consume the library subsystem through narrow interfaces.

### Task 1: Preserve high-quality artwork at the Windows source boundary

**Files:**
- Inspect/modify the existing Windows artwork metadata/resolution code in `desktop-core` rather than creating a second downloader.
- Create focused artwork-selection policy file only if current responsibilities are mixed.
- Test beside the existing desktop-core artwork/download metadata tests.

**Interfaces:**
- Produces a deterministic `bestArtworkSource(...)` policy used by future Car Library export.
- The selected source retains the highest practical upstream resolution; target ~1000x1000 or better when offered.
- Existing small UI thumbnails may remain for Windows list rendering, but they are not the authoritative export artwork.

- [ ] **Step 1: Write RED artwork-selection tests**
  Assert a 120/320/544/1200 candidate set selects 1200; a single 544 source stays 544 without fake upscale; an existing low-res cache entry cannot override a better upstream source.

- [ ] **Step 2: Run focused desktop-core RED tests**
  Expected: FAIL on the new quality-selection behavior.

- [ ] **Step 3: Implement the minimal source-selection/preservation policy**
  Reuse existing artwork/network infrastructure. Do not touch audio resolver/download logic.

- [ ] **Step 4: Run focused tests plus existing desktop-core suite**
  Expected: PASS.

- [ ] **Step 5: Commit**
  Commit only the artwork-policy/tests as `feat(windows): preserve high quality artwork sources`.

### Task 2: Define and validate MetroList Car Library v1
**Files:**
- Create: `desktop-portable/src/main/kotlin/.../CarLibraryModels.kt`
- Create: `desktop-portable/src/main/kotlin/.../CarLibraryValidator.kt`
- Test: matching `desktop-portable` Car Library contract tests.
- Create Car-side mirror models/validator under `app/src/car/kotlin/com/metrolist/music/car/library/`.
- Test: `app/src/testCar/kotlin/com/metrolist/music/car/library/CarLibraryContractTest.kt`.

**Interfaces:**
- Root folder contains `library.json`, `media/`, `artwork/`, and optional `metadata/`.
- Manifest includes `formatVersion = 1`, stable `libraryId`, generation/revision identity, songs, playlists, relative media/artwork paths, truthful known audio metadata, file sizes, and integrity metadata.
- Paths are normalized relative paths only; absolute paths, `..`, drive letters, and traversal are rejected.
- Windows and Car independently validate the same fixtures.

- [ ] **Step 1: Write shared-contract fixture tests**
  Cover one-song valid library, playlist ordering, missing artwork allowed, invalid traversal, duplicate song IDs, duplicate relative media paths, malformed version, and unsafe filenames.

- [ ] **Step 2: Run Windows and Car RED contract tests**
  Expected: FAIL because Car Library v1 types do not exist.

- [ ] **Step 3: Implement Windows contract models/validator**
  Keep these beside, not inside, the legacy `.metrolistbackup` model path.

- [ ] **Step 4: Implement Car reader/validator against the same serialized fixture semantics**
  Do not share source by refactoring the proven legacy portable implementation.

- [ ] **Step 5: Run GREEN on both sides**
  Expected: PASS with byte-compatible/semantic-compatible fixture parsing.

- [ ] **Step 6: Commit**
  `feat(portable): define car library v1 contract`.

### Task 3: Add incremental and atomic Windows Car Library export
**Files:**
- Create a dedicated Car Library exporter in `desktop-export`.
- Add focused export state/diff helper if needed.
- Modify `desktop-app` export UI/controller to add `Export Car Library` as a separate action.
- Test exporter in `desktop-export`; test UI wiring in `desktop-app`.

**Interfaces:**
- `exportCarLibrary(destination, selection, onProgress): CarLibraryExportResult`.
- Existing destination library is identified by `libraryId` and current manifest.
- Unchanged media/artwork is reused in place; new/changed files are written via temporary names and promoted safely.
- Final `library.json` is replaced atomically only after every referenced changed artifact is durable.
- Export progress reports tracks/files/bytes and distinguishes copied, reused, updated, failed.

- [ ] **Step 1: Write RED first-export tests**
  Assert expected folder tree, high-quality artwork bytes, relative paths, playlist/order metadata, and valid manifest.

- [ ] **Step 2: Write RED incremental-export tests**
  Export twice with one added track and one changed artwork. Assert unchanged media is not rewritten and manifest revision changes only after new artifacts succeed.

- [ ] **Step 3: Write RED interruption/failure test**
  Inject failure before final manifest promotion. Assert the previous valid manifest and its referenced files remain usable.

- [ ] **Step 4: Implement exporter and progress model**
  Reuse existing offline media bytes directly; do not transcode/re-download audio merely for Car Library.

- [ ] **Step 5: Add distinct Windows UI action**
  Keep legacy `.metrolistbackup` export available. Label the folder workflow clearly as the recommended car transfer path.

- [ ] **Step 6: Run desktop-export/app suites**
  Expected: PASS without changing the legacy archive output contract.

- [ ] **Step 7: Commit**
  `feat(windows): add incremental car library export`.

### Task 4: Open and index a Car Library from head-unit local storage
**Files:**
- Create: `app/src/car/kotlin/com/metrolist/music/car/library/CarLibraryRepository.kt`
- Create: `CarLibraryTreeAccess.kt`
- Create: `CarLibraryIndex.kt`
- Create: `CarLibraryAvailability.kt`
- Test matching repository/tree/index tests under `app/src/testCar/.../library/`.

**Interfaces:**
- `open(location): CarLibraryOpenResult` opens a Car Library folder already present in head-unit persistent storage and validates `library.json`.
- `refresh(): CarLibraryRefreshResult` compares library/revision identity and changed entries.
- Repository exposes a lightweight indexed catalog and per-track local path/document URI; it does not duplicate audio into app-private storage.
- Missing/moved/corrupt files return explicit unavailable state while preserving enough index state for recovery.

- [ ] **Step 1: Write RED open/index tests**
  Assert thousands of manifest entries become catalog entries without opening every media payload.

- [ ] **Step 2: Write RED local-path and unavailable-file tests**
  Assert relative resolution works if the same library is copied/moved to another valid local location, and one missing track does not invalidate unrelated tracks.

- [ ] **Step 3: Write RED persistent-access/restart test**
  Recreate repository state and assert the same local library can reopen after app/head-unit restart; if the folder/access is gone, return a clear unavailable state with zero destructive mutation.

- [ ] **Step 4: Implement local-folder access and index-first repository**
  Use the safest storage mechanism supported by the target Android firmware. SAF/document-tree access may be used when needed for persistent permission, but runtime behavior must not depend on removable-media mounting. Cache only lightweight derived metadata needed for startup/UX.

- [ ] **Step 5: Run focused Car library tests**
  Expected: PASS.

- [ ] **Step 6: Commit**
  `feat(car): open local car libraries in place`.

### Task 5: Play local Car Library media through the existing player
**Files:**
- Add a narrow Car Library local-media resolver/adapter under the car library/playback boundary.
- Modify Car-only player queue construction where necessary.
- Test local path/document-URI playback request construction and library-unavailable behavior.

**Interfaces:**
- A Car Library track resolves to its file in the selected head-unit local library folder, using a direct local path or document/content URI as required by the storage API.
- Existing MediaSession/player behavior remains authoritative for play/pause/seek/queue.
- Direct folder tracks do not need fake Media3 completed-download records merely to be playable.
- Legacy imported downloads continue through the existing Media3 cache/download-index path.

- [ ] **Step 1: Write RED resolver tests**
  Assert a local Car Library track produces the correct playable locator/cache identity metadata; an unavailable library produces a typed unavailable result, not a network fallback masquerading as local media.

- [ ] **Step 2: Implement Car-only local Car Library media resolution**
  Keep standard Android variants and legacy imported-download behavior unchanged.

- [ ] **Step 3: Run focused playback tests and Car unit tests**
  Expected: PASS.

- [ ] **Step 4: Commit**
  `feat(car): play car library media in place`.

### Task 6: Resolve high-quality Car Library artwork efficiently

**Files:**
- Extend/create `CarArtworkResolver.kt`.
- Add a small derived-thumbnail cache helper only for list/grid rendering if profiling/tests justify it.
- Test artwork precedence and source quality.

**Interfaces:**
- Resolution order: active local Car Library high-quality artwork -> legacy imported local artwork -> remote URL -> placeholder.
- Now Playing requests the high-quality local asset.
- Dense rows may use a derived cached thumbnail without replacing/deleting the high-quality source.

- [ ] **Step 1: Write RED artwork precedence/quality tests**
  Assert Car Library artwork wins, Now Playing does not select a derived low-res thumbnail, and missing local artwork falls back correctly.

- [ ] **Step 2: Implement resolver and bounded thumbnail caching**
  Never overwrite source artwork with the derivative.

- [ ] **Step 3: Run focused tests**
  Expected: PASS.

- [ ] **Step 4: Commit**
  `feat(car): use high quality offline artwork`.

### Task 7: Add Car Library selection, refresh, and availability UI
**Files:**
- Modify/create Car Library/More/Settings screens from the existing Car plan.
- Keep legacy `Import Windows Backup` screen/action.
- Add tests for UI state models/navigation where practical.

**Interfaces:**
- Primary action: `Open Car Library Folder`.
- Active state shows library name/revision/track count and `Refresh Library`.
- Unavailable state explains that the local Car Library folder was moved, deleted, corrupted, or access was lost, and offers reselect/recover without deleting index state.
- Legacy backup import remains visibly separate and secondary.

- [ ] **Step 1: Write RED state/navigation tests**
  Cover no library, active, refreshing, unavailable, invalid manifest, and legacy import entry.

- [ ] **Step 2: Implement local-folder selection/access**
  Prefer the safest mechanism supported by the target firmware. Use `OpenDocumentTree` with persisted permission if needed, but do not design around removable-media mount paths or broad storage access.

- [ ] **Step 3: Implement refresh/status surfaces**
  Refresh must use Task 4's diff/index path, not trigger a legacy import.

- [ ] **Step 4: Run Car UI/unit tests and assemble**
  Expected: PASS.

- [ ] **Step 5: Commit**
  `feat(car): add direct library management ui`.

### Task 8: Integrate with the paused legacy-import work without discarding it

**Files:**
- Preserve the existing Task 6/7 legacy importer files already present in the working tree.
- Reconcile the original `docs/superpowers/plans/2026-09-27-metrolist-car-apk.md` Tasks 6-8 with this plan during execution, not by deleting completed work.
- Update diagnostics to distinguish `CAR_LIBRARY` vs `LEGACY_BACKUP_IMPORT`.

**Interfaces:**
- Direct Car Library and legacy backup import coexist.
- Legacy importer remains resumable/idempotent according to the original plan.
- Diagnostics identify active source mode, local Car Library ID/revision/path-access state, and last legacy import status.

- [ ] **Step 1: Review current uncommitted importer state before any edit**
  Preserve all existing work and identify which original tests are already GREEN/RED.

- [ ] **Step 2: Finish legacy importer only according to its original tests**
  Do not force the direct-library architecture through Media3 cache reconstruction.

- [ ] **Step 3: Add coexistence tests**
  Assert direct library can be selected without corrupting legacy imported downloads and switching source modes does not delete either source.

- [ ] **Step 4: Run full Car unit suite**
  Expected: PASS.

- [ ] **Step 5: Commit the smallest coherent legacy/coexistence change**
  Do not squash unrelated historical Car work.

### Task 9: End-to-end gates and physical transfer acceptance
**Files:**
- Update Car release history only when a versioned APK is intentionally packaged/handed to the user.
- Update Windows release history/version only when the new Windows feature is intentionally packaged for user use.

**Interfaces:**
- Produces a real Windows Car Library folder from the user's existing offline library.
- Produces a Car build that opens that folder directly from the head unit's persistent local storage.
- Legacy backup remains a verified fallback.

- [ ] **Step 1: Run Windows focused + regression gates**
  Verify desktop-core/export/app tests and package checks appropriate to the eventual Windows version.

- [ ] **Step 2: Run standard Android regression gate**
  Foss/GMS/Izzy tests and assemblies remain green.

- [ ] **Step 3: Run full Car gate**
  `:app:testCarDebugUnitTest :app:assembleCarDebug` passes.

- [ ] **Step 4: Generate a small real Car Library fixture using Windows**
  Verify manifest, high-quality artwork, playlists, and direct media files.

- [ ] **Step 5: Stop for explicit installation/physical-test authorization**
  Do not install the APK or modify the head unit without a separate user go-ahead.

- [ ] **Step 6: Physical acceptance after authorization**
  Open the Car Library folder already transferred into head-unit storage; verify fast catalog availability, high-quality Now Playing artwork, offline playback/seek/queue, app/head-unit restart reopen behavior, incremental refresh, and one legacy backup import.

- [ ] **Step 7: Large-library acceptance**
  Transfer the real thousands-of-tracks Car Library into head-unit storage. Confirm the app does not duplicate the audio payload into app-private storage, startup/refresh remain practical, and unchanged files are not recopied during incremental export/transfer refresh.

## Execution Order Relative to Existing Car Work

The current Car branch/worktree already contains completed Tasks 1-5 from the original Car plan and in-progress legacy importer work. Do not restart those tasks.

When implementation is authorized:
1. preserve and inventory the current working tree;
2. finish or safely checkpoint the already-started legacy importer work according to its existing tests;
3. execute this plan's Tasks 1-7 without rewriting proven Car shell/input/playback work;
4. reconcile coexistence in Task 8;
5. run Task 9 gates;
6. stop before installation/physical validation unless explicitly authorized.

This plan intentionally keeps `.metrolistbackup` as a fallback while making Car Library the preferred everyday transfer path.
