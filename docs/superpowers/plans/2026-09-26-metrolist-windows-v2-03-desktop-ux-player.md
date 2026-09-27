# MetroList Windows V2 — Desktop UX, Player, and Queue Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make MetroList Windows faster, clearer, and more professional for daily use through proven desktop music-player interaction patterns without copying another app's visual identity.

**Architecture:** Keep the existing Material 3 theme and split UI files. Add persisted presentation preferences, reusable list/grid components, clear context actions, an expanded Now Playing surface, and minimal queue-mutation APIs layered on the current player. Desktop-only hover/right-click/keyboard behavior stays in the presentation layer.

**Tech Stack:** Kotlin/JVM 21, Compose Desktop Material 3, Compose icons, coroutines, existing MpvPlayer, kotlin.test.

**Spec:** `docs/superpowers/specs/2026-09-26-metrolist-windows-v2-design.md`

## Global Constraints
- Every added control must reduce effort, improve clarity, or expose a real user task.
- Do not visually clone Spotify/Apple Music/Deezer/YouTube Music.
- Songs remain list-first; list/grid exists only for playlists/albums/artists/collections where useful.
- Preserve existing playback, download, artwork, and export behavior.
- Primary actions cannot require hover to be discoverable; future touch presentation must remain possible.
- Do not add a new UI framework or animation library.
- Do not bump version, reset/clean/push/merge, or commit without explicit authorization.

## Review Focus
- Keyboard shortcuts never trigger while the user is typing into search/import text fields.
- Queue removal/reordering around the currently playing index does not switch to the wrong song.
- Shuffle/repeat remain coherent after queue mutations.
- Small window widths remain usable instead of clipping the expanded player or grid controls.
- Right-click/ellipsis actions always target the row/card actually clicked, not stale selection state.

---
### Task 1: Persisted presentation preferences and list/grid primitives

**Files:**
- Create: `desktop-app/src/main/kotlin/com/metrolist/desktop/UiPreferences.kt`
- Create: `desktop-app/src/main/kotlin/com/metrolist/desktop/CatalogGrid.kt`
- Modify: `desktop-app/src/main/kotlin/com/metrolist/desktop/CatalogScreens.kt`
- Modify: `desktop-app/src/main/kotlin/com/metrolist/desktop/DesktopRuntime.kt`
- Test: `desktop-app/src/test/kotlin/com/metrolist/desktop/UiPreferencesTest.kt`
- Test: `desktop-app/src/test/kotlin/com/metrolist/desktop/CatalogPresentationTest.kt`

**Interfaces:**
- Produces: `enum class PresentationMode { LIST, GRID }`
- Produces: `UiPreferences(path: Path).libraryMode(key: LibraryTab): PresentationMode` and `setLibraryMode(...)`.
- Preferences file: `%LOCALAPPDATA%\MetroListWindows\desktop\ui.properties`.

- [ ] **Step 1: Write failing preference tests**
Verify default modes, persistence across reopen, invalid-value fallback, and independent mode per Library tab.

- [ ] **Step 2: Verify RED**
Run both new focused app tests.

- [ ] **Step 3: Implement `UiPreferences` using `java.util.Properties`**
Use atomic temp-file replace; no settings dependency.

- [ ] **Step 4: Implement reusable grid cards and Library toggle**
Playlists/albums/artists can switch LIST/GRID; songs remain LIST only. Artwork/title are primary in grid; actions remain keyboard/touch reachable.

- [ ] **Step 5: Verify GREEN**
Run `:desktop-app:test`.

### Task 2: Context actions and clearer iconography

**Files:**
- Create: `desktop-app/src/main/kotlin/com/metrolist/desktop/CatalogContextMenu.kt`
- Modify: `desktop-app/src/main/kotlin/com/metrolist/desktop/CatalogRows.kt`
- Modify: `desktop-app/src/main/kotlin/com/metrolist/desktop/CatalogGrid.kt`
- Modify: `desktop-app/src/main/kotlin/com/metrolist/desktop/ChromeUi.kt`
- Test: `desktop-app/src/test/kotlin/com/metrolist/desktop/CatalogActionsTest.kt`

**Interfaces:**
- Produces: `CatalogAction` sealed values for PlayNext, AddToQueue, Download/RemoveDownload, AddToLocalPlaylist, OpenAlbum, OpenArtist, CopyLink, RemoveFromLocalPlaylist.
- UI renders only actions valid for the item/context.

- [ ] **Step 1: Write failing action-availability tests**
Pin which actions exist for song, album, artist, YouTube playlist, and local-playlist song.

- [ ] **Step 2: Verify RED**
Run the action test.

- [ ] **Step 3: Implement shared action model and ellipsis menu**
Use Material icons with tooltips/content descriptions. Keep primary Play/Open/Download visible when space permits.

- [ ] **Step 4: Add desktop right-click entry point using Compose Desktop context-menu support**
It must call the same action model as the ellipsis menu, not duplicate logic.

- [ ] **Step 5: Verify compile/tests**
Run `:desktop-app:test :desktop-app:createDistributable`.
### Task 3: Safe queue mutation APIs

**Files:**
- Modify: `desktop-core/src/main/kotlin/com/metrolist/desktop/player/DesktopPlayer.kt`
- Modify: `desktop-core/src/main/kotlin/com/metrolist/desktop/player/MpvPlayer.kt`
- Create: `desktop-core/src/main/kotlin/com/metrolist/desktop/player/QueueMutation.kt`
- Modify: `desktop-app/src/main/kotlin/com/metrolist/desktop/DesktopRuntime.kt`
- Test: `desktop-core/src/test/kotlin/com/metrolist/desktop/player/MpvPlayerTest.kt`
- Test: `desktop-core/src/test/kotlin/com/metrolist/desktop/player/QueueMutationTest.kt`

**Interfaces:**
- Add: `suspend fun playNext(song: SongItem)`
- Add: `suspend fun addToQueue(song: SongItem)`
- Add: `suspend fun moveQueueItem(fromIndex: Int, toIndex: Int)`
- Add: `suspend fun removeQueueItem(index: Int)`
- Add: `suspend fun clearUpcoming()`

- [ ] **Step 1: Write failing pure queue-mutation tests**
Cover inserting after current, append, moving before/after current, removing before current, rejecting removal of current, clear-upcoming, duplicate song IDs as separate queue entries.

- [ ] **Step 2: Verify RED**
Run `QueueMutationTest`.

- [ ] **Step 3: Implement pure index-safe queue transformations**
Keep them independent from libmpv.

- [ ] **Step 4: Write failing Mpv integration tests**
Verify current song remains current through mutation, `queueIndex` updates correctly, and shuffle/repeat still produce valid next/previous behavior.

- [ ] **Step 5: Implement methods under existing transition mutex**
Rebuild playback order after mutation; never reload the current source solely because the queue changed.

- [ ] **Step 6: Verify GREEN**
Run all desktop player tests.
### Task 4: Expanded Now Playing and useful queue panel

**Files:**
- Modify: `desktop-app/src/main/kotlin/com/metrolist/desktop/PlayerUi.kt`
- Modify: `desktop-app/src/main/kotlin/com/metrolist/desktop/App.kt`
- Test: `desktop-app/src/test/kotlin/com/metrolist/desktop/PlayerUiTest.kt`

**Interfaces:**
- Produces: `NowPlayingUiState(expanded: Boolean, queueVisible: Boolean)` or equivalent minimal UI state.
- Consumes queue-mutation runtime methods and `PlayerState.audioFormat` from Plan 01.

- [ ] **Step 1: Write failing player presentation tests**
Cover expand/collapse state, quality label, current queue marker, queue action enablement, and empty/error states.

- [ ] **Step 2: Verify RED**
Run `PlayerUiTest`.

- [ ] **Step 3: Refine mini-player**
Keep artwork/title/artist, previous/play-next, progress, volume, queue/expand affordance. Avoid oversized text buttons where clear icons suffice; retain tooltips/content descriptions.

- [ ] **Step 4: Implement expanded Now Playing**
Use `BoxWithConstraints`: wide windows show large artwork/content plus queue side panel; narrower windows stack queue below. Include album, progress, shuffle/repeat, volume, download state, and real audio format.

- [ ] **Step 5: Wire queue mutations**
Jump, Play next, Add to queue, reorder, remove upcoming, clear upcoming.

- [ ] **Step 6: Verify GREEN**
Run `:desktop-core:test :desktop-app:test :desktop-app:createDistributable`.
### Task 5: Keyboard, focus, hover, and feedback polish

**Files:**
- Create: `desktop-app/src/main/kotlin/com/metrolist/desktop/DesktopShortcuts.kt`
- Modify: `desktop-app/src/main/kotlin/com/metrolist/desktop/App.kt`
- Modify: `desktop-app/src/main/kotlin/com/metrolist/desktop/CatalogRows.kt`
- Modify: `desktop-app/src/main/kotlin/com/metrolist/desktop/CatalogGrid.kt`
- Modify: `desktop-app/src/main/kotlin/com/metrolist/desktop/UiTheme.kt`
- Test: `desktop-app/src/test/kotlin/com/metrolist/desktop/DesktopShortcutsTest.kt`

**Interfaces:**
- Produces: pure `resolveShortcut(event, textInputFocused): DesktopShortcut?`.
- Initial shortcuts: Space play/pause, Left/Right seek, Up/Down volume, Ctrl+F active search/filter, Escape close transient surface.

- [ ] **Step 1: Write failing shortcut tests**
Include modified keys, text-input focus suppression, boundaries for seek/volume, and Escape precedence.

- [ ] **Step 2: Verify RED**
Run `DesktopShortcutsTest`.

- [ ] **Step 3: Implement root key routing and focus signals**
Search/import text fields explicitly report focus so media keys never steal typing.

- [ ] **Step 4: Add consistent hover/focus feedback**
Use existing Material colors/elevation; no custom animation framework.

- [ ] **Step 5: Manual UX pass**
Check common flows with mouse-only, keyboard-assisted, and narrow/wide window sizes. Remove any control that duplicates another without reducing effort.

- [ ] **Step 6: Slice verification**
Run `./gradlew.bat :desktop-core:test :desktop-app:test :desktop-app:createDistributable`.
Expected: BUILD SUCCESSFUL, then user visual acceptance before Explore.
