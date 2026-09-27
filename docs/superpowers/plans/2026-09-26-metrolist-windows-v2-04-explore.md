# MetroList Windows V2 — Explore Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a useful Explore destination backed by MetroList/YouTube Music discovery data, with no AI service, recommendation engine, or filler.

**Architecture:** Reuse `YouTube.explore()` for new releases and mood/genre browse endpoints, and reuse existing catalog item rendering/navigation. Keep Explore state inside `DesktopAppState`; only add endpoint handling that the existing MetroList code already supports.

**Tech Stack:** Kotlin/JVM 21, existing MetroList/InnerTube models, Compose Desktop Material 3, kotlin.test.

**Spec:** `docs/superpowers/specs/2026-09-26-metrolist-windows-v2-design.md`

## Global Constraints
- Explore must not duplicate Home merely to fill space.
- Prefer existing `YouTube.explore()`, `ExplorePage`, and `MoodAndGenres.Item.endpoint`.
- No external AI/recommendation service and no new API stack.
- Use the V2 list/grid/catalog components from Plan 03.
- Preserve Android code behavior and all V1 flows.
- No version bump, reset/clean/push/merge, or commit without explicit authorization.

## Review Focus
- Explore returns an empty section: screen remains useful and does not show blank headers.
- Mood/genre endpoint fails after the initial page loaded: only the detail state errors, not the whole app.
- Rapid clicks between Explore categories cannot let an older response replace the latest selection.
- Unsupported item types are filtered rather than crashing rendering.
- Explore and Home retain independent loading/error state so navigation does not surface stale messages.

---
### Task 1: Explore catalog gateway and state

**Files:**
- Modify: `desktop-app/src/main/kotlin/com/metrolist/desktop/Catalog.kt`
- Modify: `desktop-app/src/main/kotlin/com/metrolist/desktop/AppState.kt`
- Test: `desktop-app/src/test/kotlin/com/metrolist/desktop/AppStateTest.kt`
- Test: `desktop-app/src/test/kotlin/com/metrolist/desktop/ExploreCatalogTest.kt`

**Interfaces:**
- Produces: `ExploreContent(newReleaseAlbums: List<AlbumItem>, moods: List<MoodAndGenres.Item>)`.
- Add: `DesktopCatalogGateway.explore(): ExploreContent`.
- Add: `DesktopCatalogGateway.browseMood(endpoint: BrowseEndpoint): List<CatalogDetailSection>` only if existing browse helpers cannot be reused.
- Add `AppDestination.EXPLORE`.

- [ ] **Step 1: Write failing gateway tests**
Verify `YouTube.explore()` output is mapped without inventing content and unsupported/empty values are filtered.

- [ ] **Step 2: Write failing AppState race tests**
Rapid navigation/category selection must ignore stale responses, using the same request-generation discipline as Search/playlist/detail.

- [ ] **Step 3: Verify RED**
Run the two focused test classes.

- [ ] **Step 4: Implement gateway and state**
Keep Explore request state separate enough that returning to Home does not display an Explore error.

- [ ] **Step 5: Verify GREEN**
Run `:desktop-app:test`.
### Task 2: Explore screen and navigation

**Files:**
- Modify: `desktop-app/src/main/kotlin/com/metrolist/desktop/ChromeUi.kt`
- Create: `desktop-app/src/main/kotlin/com/metrolist/desktop/ExploreScreen.kt`
- Modify: `desktop-app/src/main/kotlin/com/metrolist/desktop/App.kt`
- Test: `desktop-app/src/test/kotlin/com/metrolist/desktop/ExploreUiTest.kt`

**Interfaces:**
- Explore screen consumes `ExploreContent`, existing `CatalogItemRow`/`CatalogGridCard`, and existing album/artist/playlist navigation callbacks.

- [ ] **Step 1: Write failing presentation tests**
Cover destination label/icon, new-release section visibility, mood chips/cards, empty/loading/error states, and supported-item filtering.

- [ ] **Step 2: Verify RED**
Run `ExploreUiTest`.

- [ ] **Step 3: Add Explore to primary navigation**
Use a clear Material icon and keep order Home, Explore, Search, Library, Downloads.

- [ ] **Step 4: Implement the screen**
New releases use artwork-led grid; moods/genres use compact discoverable controls. Do not add sections that only mirror Home.

- [ ] **Step 5: Wire mood/genre details**
Use existing browse endpoint/model support. If an endpoint requires new parsing not present in MetroList, stop and reassess before expanding scope.

- [ ] **Step 6: Verify slice**
Run `./gradlew.bat :desktop-app:test :desktop-app:createDistributable`.
Expected: BUILD SUCCESSFUL, followed by user acceptance of whether Explore is genuinely useful rather than redundant.
