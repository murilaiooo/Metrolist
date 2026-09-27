# MetroList Windows V2 — Playlist Import, Local Library, and Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Import up to 1,000 songs from TXT/paste, match conservatively against YouTube Music, create a local or YouTube Music playlist, and allow later explicit sync.

**Architecture:** Add a separate Windows-owned `library.db` in `desktop-core`; keep `offline.db` untouched. Parse and match in focused units, persist sessions/results for resume, then expose local-playlist and sync services to the Compose app. Reuse `YouTube.search`, `YouTube.createPlaylist`, and `YouTube.addToPlaylist`; no second API stack.

**Tech Stack:** Kotlin/JVM 21, coroutines, SQLite/JDBC, kotlinx.serialization, MetroList/InnerTube, Compose Desktop, kotlin.test.

**Spec:** `docs/superpowers/specs/2026-09-26-metrolist-windows-v2-design.md`

## Global Constraints
- At least 1,000 input lines per import.
- Maximum four concurrent match searches.
- Ready threshold: 0.90; Review threshold: 0.70.
- Original recording always outranks alternate versions; explicit wins over equivalent clean.
- Review/Not found rows are never silently added.
- Local playlists never modify YouTube Music until an explicit sync/create action.
- Preserve `offline.db`, download queue, player, exporter, and Android schema.
- No new fuzzy-matching dependency unless tests prove the standard-library implementation inadequate.
- Do not bump version, reset/clean/push/merge, or commit without explicit authorization.

## Review Focus
- Duplicate input lines remain independently ordered but can resolve to the same video ID without corrupting the session.
- TXT with BOM, CRLF, blank lines, numbering, tabs, and hyphenated song titles parses without losing meaningful text.
- Network failure halfway through 1,000 rows preserves completed decisions and can resume.
- YouTube sync succeeds for most tracks but one add fails: linked playlist remains valid and failed tracks remain visible/retryable.
- A remembered manual match becomes unavailable: it is invalidated and rematched rather than blindly reused.

---
### Task 1: Versioned local-library persistence

**Files:**
- Create: `desktop-core/src/main/kotlin/com/metrolist/desktop/library/LibraryModels.kt`
- Create: `desktop-core/src/main/kotlin/com/metrolist/desktop/library/LibraryStore.kt`
- Test: `desktop-core/src/test/kotlin/com/metrolist/desktop/library/LibraryStoreTest.kt`

**Interfaces:**
- Produces: `LocalPlaylist`, `LocalPlaylistTrack`, `ImportSession`, `ImportRow`, `RememberedMatch`.
- Produces: `LibraryStore(databasePath: Path)` with CRUD for playlists, ordered tracks, import sessions/rows, and remembered matches.

- [ ] **Step 1: Write failing schema/round-trip tests**
Cover schema version 1, playlist ordering, import-row ordering, remembered-match upsert, reopen persistence, and duplicate video IDs at different positions.

- [ ] **Step 2: Verify RED**
Run: `./gradlew.bat :desktop-core:test --tests "com.metrolist.desktop.library.LibraryStoreTest"`

- [ ] **Step 3: Implement `LibraryStore`**
Use a separate SQLite file, WAL, explicit `PRAGMA user_version`, transactions for playlist reorder/finalization, and JSON only for structured metadata that does not need relational queries.

- [ ] **Step 4: Verify GREEN**
Run the focused test. Confirm no access to `OfflineStore` or `offline.db`.

### Task 2: Robust TXT/paste parser

**Files:**
- Create: `desktop-core/src/main/kotlin/com/metrolist/desktop/library/importer/PlaylistTextParser.kt`
- Test: `desktop-core/src/test/kotlin/com/metrolist/desktop/library/importer/PlaylistTextParserTest.kt`

**Interfaces:**
- Produces: `data class ParsedImportLine(val position: Int, val original: String, val left: String, val right: String?)`
- Produces: `PlaylistTextParser.parse(text: String): List<ParsedImportLine>`

- [ ] **Step 1: Write failing parser cases**
Cover BOM, CRLF/LF, numbered lines, tab separation, `Artist - Song`, `Song - Artist`, hyphens inside titles, plain-title lines, whitespace, and >1,000-line truncation rejection.

- [ ] **Step 2: Verify RED**
Run: `./gradlew.bat :desktop-core:test --tests "com.metrolist.desktop.library.importer.PlaylistTextParserTest"`

- [ ] **Step 3: Implement minimal parser**
Preserve original text; do not guess artist/title orientation. Strip only obvious list numbering and surrounding whitespace.

- [ ] **Step 4: Verify GREEN**
Same focused test.
### Task 3: Candidate normalization, version detection, and scoring

**Files:**
- Create: `desktop-core/src/main/kotlin/com/metrolist/desktop/library/importer/SongMatchModels.kt`
- Create: `desktop-core/src/main/kotlin/com/metrolist/desktop/library/importer/SongTextNormalizer.kt`
- Create: `desktop-core/src/main/kotlin/com/metrolist/desktop/library/importer/SongVersionClassifier.kt`
- Create: `desktop-core/src/main/kotlin/com/metrolist/desktop/library/importer/SongMatchScorer.kt`
- Test: `desktop-core/src/test/kotlin/com/metrolist/desktop/library/importer/SongMatchScorerTest.kt`

**Interfaces:**
- Produces: `enum class MatchState { READY, REVIEW, NOT_FOUND }`
- Produces: `enum class SongVersionKind { ORIGINAL, REMASTER, DELUXE, ACOUSTIC, UNPLUGGED, LIVE, REMIX, RADIO_EDIT, COVER, KARAOKE, SPED_UP, SLOWED, NIGHTCORE, OTHER }`
- Produces: `SongMatchDecision(selected: SongItem?, candidates: List<ScoredCandidate>, state: MatchState, score: Double)`
- Produces: `SongMatchScorer.rank(input: ParsedImportLine, candidates: List<SongItem>): SongMatchDecision`

- [ ] **Step 1: Write failing normalization/version tests**
Pin punctuation/case/feat normalization while preserving meaningful version markers.

- [ ] **Step 2: Write failing ranking tests**
Cover original vs live/remix/radio-edit; original vs remaster/deluxe/acoustic; explicit vs equivalent clean; naturally non-explicit; duration/album tie-break; cover/karaoke/sped/slowed rejection; 0.90/0.70 thresholds.

- [ ] **Step 3: Verify RED**
Run the scorer test class.

- [ ] **Step 4: Implement scoring**
Use deterministic standard-library similarity. Evaluate both left/right orientations when both exist. Candidate ordering is stable for equal scores.

- [ ] **Step 5: Apply version policy after base identity scoring**
Undesirable variants cannot become READY merely from strong title similarity. Fallback remaster/deluxe/acoustic/unplugged is REVIEW when no reliable original exists.

- [ ] **Step 6: Verify GREEN**
Run all importer unit tests.
### Task 4: Bounded search and resumable matching engine

**Files:**
- Create: `desktop-core/src/main/kotlin/com/metrolist/desktop/library/importer/SongSearchGateway.kt`
- Create: `desktop-core/src/main/kotlin/com/metrolist/desktop/library/importer/PlaylistImportMatcher.kt`
- Test: `desktop-core/src/test/kotlin/com/metrolist/desktop/library/importer/PlaylistImportMatcherTest.kt`

**Interfaces:**
- Produces: `fun interface SongSearchGateway { suspend fun search(query: String): List<SongItem> }`
- Produces: `PlaylistImportMatcher.match(sessionId: String)`, `retryRow(sessionId: String, position: Int, query: String?)`, and progress `StateFlow<ImportProgress>`.
- Production gateway delegates to existing MetroList search with song filter.

- [ ] **Step 1: Write failing engine tests**
Verify max four in-flight searches, progress counts, candidate cap of eight per row, partial network failure persistence, cancellation preserving completed rows, resume skipping completed rows, duplicate input handling.

- [ ] **Step 2: Verify RED**
Run the matcher test class.

- [ ] **Step 3: Implement bounded matching**
Use coroutine `Semaphore(4)`; generate both plausible query orientations; dedupe candidates by video ID before scoring.

- [ ] **Step 4: Add remembered-match fast path**
A stored match is reused only after metadata validation; invalid/unavailable remembered targets fall through to search.

- [ ] **Step 5: Verify GREEN**
Run importer + store tests together.
### Task 5: Local playlist service

**Files:**
- Create: `desktop-core/src/main/kotlin/com/metrolist/desktop/library/LocalPlaylistService.kt`
- Test: `desktop-core/src/test/kotlin/com/metrolist/desktop/library/LocalPlaylistServiceTest.kt`

**Interfaces:**
- Produces: `createFromImport(sessionId: String, name: String): LocalPlaylist`
- Produces: `rename`, `delete`, `removeTrack`, `moveTrack`, `songs(playlistId): List<SongItem>`, and `searchSongs(playlistId: String, query: String): List<SongItem>`.
- Produces conversion between persisted `LocalPlaylistTrack` and existing `SongItem`.

- [ ] **Step 1: Write failing service tests**
Finalization includes READY rows only; unresolved rows stay in import session; track order is preserved; rename/remove/reorder are atomic; playlist-local search is case-insensitive and preserves playlist order.

- [ ] **Step 2: Verify RED**
Run the service test.

- [ ] **Step 3: Implement service and conversions**
Do not create a second playback/download model.

- [ ] **Step 4: Verify GREEN**
Run library tests.

### Task 6: Explicit YouTube Music playlist synchronization

**Files:**
- Create: `desktop-core/src/main/kotlin/com/metrolist/desktop/library/YouTubePlaylistGateway.kt`
- Create: `desktop-core/src/main/kotlin/com/metrolist/desktop/library/PlaylistSyncService.kt`
- Test: `desktop-core/src/test/kotlin/com/metrolist/desktop/library/PlaylistSyncServiceTest.kt`

**Interfaces:**
- Produces: `YouTubePlaylistGateway.create(name: String): String`
- Produces: `YouTubePlaylistGateway.add(playlistId: String, videoId: String): Result<Unit>`
- Production implementation delegates to `YouTube.createPlaylist` and `YouTube.addToPlaylist`.
- Produces: `PlaylistSyncService.sync(localPlaylistId: String): SyncResult`.

- [ ] **Step 1: Write failing sync tests**
Cover new remote playlist, already-linked playlist, per-track failure, retry, zero duplicate remote creation, and no destructive delete/update.

- [ ] **Step 2: Verify RED**
Run the sync service test.

- [ ] **Step 3: Implement explicit sync**
Persist remote playlist ID immediately after successful creation; add tracks sequentially or with conservative bounded concurrency only if the endpoint proves stable.

- [ ] **Step 4: Verify GREEN**
Run sync + library tests.
### Task 7: Runtime integration and import/review UI

**Files:**
- Modify: `desktop-app/src/main/kotlin/com/metrolist/desktop/DesktopRuntime.kt`
- Modify: `desktop-app/src/main/kotlin/com/metrolist/desktop/AppState.kt`
- Modify: `desktop-app/src/main/kotlin/com/metrolist/desktop/App.kt`
- Create: `desktop-app/src/main/kotlin/com/metrolist/desktop/PlaylistImportUi.kt`
- Create: `desktop-app/src/main/kotlin/com/metrolist/desktop/LocalPlaylistUi.kt`
- Test: `desktop-app/src/test/kotlin/com/metrolist/desktop/PlaylistImportUiTest.kt`
- Test: `desktop-app/src/test/kotlin/com/metrolist/desktop/LocalPlaylistUiTest.kt`

**Interfaces:**
- `DesktopRuntime` owns one `LibraryStore`, `LocalPlaylistService`, matcher, and sync service under `%LOCALAPPDATA%\MetroListWindows\desktop\library.db`.
- App exposes `Create / Import Playlist`, TXT chooser, paste input, progress, All/Ready/Review/Not found filters, candidate selection, local/YouTube destination, and later `Sync to YouTube Music`.

- [ ] **Step 1: Write failing UI-state tests**
Cover summary counts, filters, destination selection, unresolved-row exclusion, candidate replacement, local search, rename, reorder, remove, delete confirmation, sync progress/error state, and Local/YouTube badge. Choosing YouTube Music finalizes the same local mirror first, then explicitly invokes sync so the reviewed IDs are never rematched.

- [ ] **Step 2: Verify RED**
Run both new app test classes.

- [ ] **Step 3: Implement runtime lifecycle**
Close `LibraryStore` with runtime; matching/database work stays off the UI thread.

- [ ] **Step 4: Implement import/review screens**
Use existing artwork component and Material icons. File import reads UTF-8 TXT; paste uses the same parser path.

- [ ] **Step 5: Integrate local playlists into Library**
Local playlists are clearly marked and support play, shuffle, download, search within playlist, rename, reorder, remove track, and delete with confirmation through the local service; playback/download reuse existing runtime methods.

- [ ] **Step 6: Verify GREEN**
Run: `./gradlew.bat :desktop-core:test :desktop-app:test :desktop-app:createDistributable`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Manual acceptance slice**
Test with a realistic several-hundred-line list before proceeding. Confirm the user only needs to review uncertain matches and that no local playlist touches YouTube until explicitly requested.
