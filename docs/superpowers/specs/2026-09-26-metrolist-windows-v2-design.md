# MetroList Windows V2 Design

**Date:** 2026-09-26
**Status:** Approved by user on 2026-09-26.
**Project:** `I:\codex\metrolist-windows\.worktrees\windows-v1`
**Baseline:** Windows V1 0.1.2 behavior is working and must remain intact.

## 1. Product goal

V2 turns the stable Windows companion into a professional daily-use music application without cloning the visual identity of Spotify, Apple Music, Deezer, or YouTube Music.

The design should reuse proven interaction patterns from mature music players when they make MetroList faster, clearer, or easier to use. A new UI element or feature belongs only when it reduces effort, improves clarity, or exposes a function the user will actually use.

V2 adds four major capabilities:
1. intelligent playlist creation from a TXT file or pasted song list;
2. local playlists with optional YouTube Music synchronization;
3. best-available-source audio quality transparency and verification;
4. a cohesive desktop UX with stronger navigation, player, library, and Explore flows.

The existing Windows download, offline playback, artwork, export, authentication, and car-backup architecture is preserved unless a concrete regression proves it must change.
## 2. Product principles

- Do not imitate another player's visual design.
- Reuse interaction conventions that are already familiar to music-app users.
- Prefer obvious icons, stable control placement, short paths, visible state, and immediate feedback.
- Do not add social feeds, podcasts, statistics, badges, excessive themes, animation for its own sake, or other unrelated features.
- Keep mouse, keyboard, and future touch use in mind.
- Preserve the current Material 3 foundation rather than introducing a new UI framework.
- Prefer existing MetroList/InnerTube capabilities before adding dependencies or custom services.
- Do not add a new recommendation engine or AI dependency for Explore.
- Do not transcode downloaded audio merely to claim a larger bitrate.
- Do not modify the Android car application's behavior or database.

## 3. Scope boundaries

V2 includes:
- TXT/pasted-list playlist import;
- confidence-based YouTube Music song matching;
- local playlist persistence;
- optional creation/synchronization to YouTube Music;
- basic local playlist playback, download, search, remove, reorder, and sync actions;
- audio-format visibility and best-source verification;
- list/grid presentation where appropriate;
- expanded Now Playing;
- useful context menus and keyboard shortcuts;
- Explore/discovery using existing YouTube Music data.

V2 does not include:
- a general-purpose AI assistant;
- background bidirectional YouTube playlist sync;
- collaborative playlists;
- social features;
- lyrics;
- podcasts;
- equalizer/DSP;
- fake 320 kbps transcoding;
- Android multimedia APK work. The future car APK is only an architectural consideration in V2.
## 4. Playlist import input

The user can start an import from a clear `Create / Import Playlist` action.

Supported sources:
- UTF-8 TXT file;
- pasted multiline text.

The importer accepts common line forms without requiring a template:
- `Artist - Song`;
- `Song - Artist`;
- numbered lists;
- tab-separated artist/title;
- plain song-title lines.

Blank lines and obvious list numbering are ignored.

The importer preserves the original input line for review and future rematching.

A single import supports at least 1,000 lines. Processing uses bounded concurrency rather than launching one request per song simultaneously.

The import is resumable inside the app session and persisted once matching begins so closing the review screen does not discard completed work.
## 5. Matching model

Each normalized input line produces a set of YouTube Music song candidates through the existing MetroList/InnerTube search path.

Candidate scoring uses the metadata MetroList already exposes:
- title;
- artists;
- album;
- duration;
- explicit flag;
- video/music type;
- candidate title text for version markers.

The matcher normalizes punctuation, casing, common featuring syntax, bracket/parenthesis noise, and list-format artifacts without deleting meaningful version information.

Base confidence is driven primarily by title and artist agreement. Album and duration strengthen or weaken a match when available.

The importer must never select the first search result merely because it is first.
## 6. Version preference rules

The desired hierarchy is:

1. original studio/release recording, with the explicit variant preferred when explicit and clean equivalents both exist;
2. alternate release of the same original recording when necessary;
3. remaster/deluxe version when no reliable original is available;
4. acoustic/unplugged version when no reliable original is available;
5. review or unresolved.

Rules:
- Original always outranks acoustic, unplugged, remaster, deluxe, live, remix, cover, karaoke, sped-up, slowed, nightcore, or radio-edit variants.
- When the song has an explicit version, explicit outranks an otherwise equivalent clean candidate.
- A naturally non-explicit song is not penalized for lacking an explicit flag.
- Clean must not automatically replace an available explicit equivalent.
- Live is strongly penalized and never auto-selected over a studio match.
- Remix does not replace the original.
- Cover/karaoke/third-party performance is rejected from automatic acceptance when detectable.
- Sped-up, slowed, nightcore, and similar altered versions are rejected from automatic acceptance.
- Radio edit is penalized when a normal/original version is available.
- Remaster, deluxe, acoustic, and unplugged are acceptable fallbacks only when no reliable original candidate exists.
## 7. Confidence states

Every import row ends in one of three user-facing states:

### Ready
The matcher has a high-confidence candidate and no version-rule conflict. It may be accepted automatically into the draft playlist.

### Review
The matcher found plausible candidates but confidence is not high enough, or the top result carries a fallback/version penalty. The user must review it before the row becomes ready.

### Not found
No candidate meets the minimum confidence threshold. Nothing is added automatically.

Initial thresholds:
- Ready: score >= 0.90 and no blocking version conflict;
- Review: score >= 0.70 and < 0.90, or a fallback-version result;
- Not found: score < 0.70.

These thresholds are implementation defaults, not user settings. They should only be changed if test fixtures or real imports show systematic false positives/negatives.
## 8. Review experience

The import review screen is optimized for reviewing only the uncertain rows, not manually checking all 1,000 songs.

Header summary example:
`1000 tracks · 947 ready · 39 review · 14 not found`

Filters:
- All;
- Ready;
- Review;
- Not found.

Each uncertain row shows:
- original input;
- selected candidate;
- artwork;
- title;
- artist;
- album when available;
- duration;
- Explicit marker;
- detected version marker;
- confidence/state.

Opening a row shows several candidate choices. The user can pick the correct candidate, search again with an edited query, or leave the song unresolved.

The app never silently adds Review or Not found rows to the finalized playlist.
## 9. Match memory

Manual corrections are reusable.

Persist a lightweight mapping from normalized import identity to the user-approved YouTube video ID plus enough metadata to validate that the remembered target still represents the same song.

On a later import, a remembered mapping may become Ready without a fresh broad search when its validation still succeeds.

If the remembered target is unavailable or metadata no longer matches, fall back to normal matching.

This is a convenience cache, not an irreversible rule. The user can replace the selected candidate during review.
## 10. Local playlist model

Local playlists are a Windows-owned feature and must not be mixed into the existing offline-download database.

Create a separate versioned SQLite database under the normal MetroList Windows app-data directory, for example:
`%LOCALAPPDATA%\MetroListWindows\desktop\library.db`

The existing `offline.db` and its download/export behavior remain untouched.

The library database stores:
- local playlist ID;
- name;
- created/updated timestamps;
- ordered track membership;
- each track's YouTube video ID and display metadata;
- source import line when applicable;
- optional YouTube Music playlist ID;
- sync state;
- remembered import matches.

Schema migrations are explicit from version 1 onward.
## 11. Playlist destinations and sync

At finalization the user chooses:

### Local playlist
Exists only in MetroList Windows.

### YouTube Music playlist
The app creates the playlist through the existing MetroList/YouTube `createPlaylist` path and adds the resolved tracks through the existing playlist-edit path.

A local playlist exposes `Sync to YouTube Music`.

Synchronization:
- creates a YouTube Music playlist if one is not yet linked;
- uploads only resolved/ready tracks;
- records the returned YouTube playlist ID;
- shows progress and per-track failures;
- never deletes or changes an existing YouTube playlist without an explicit action.

V2 does not run continuous or automatic two-way synchronization. Sync is an explicit user action so local experiments do not unexpectedly modify the YouTube account.
## 12. Local playlist use

A local playlist supports the functions needed for normal listening:
- open;
- play;
- shuffle;
- download playlist;
- search within playlist;
- remove a track;
- reorder tracks;
- rename;
- delete with confirmation;
- sync to YouTube Music when desired.

The playlist header clearly indicates `Local` or `YouTube Music`/linked status.

The playback queue consumes the same `SongItem` identity used elsewhere so local playlists do not require a separate player architecture.

Download actions enqueue the same working offline pipeline used by V1.
## 13. Audio quality rule

The quality goal is `Best available source`, not an arbitrary 320 kbps label.

Windows continues to request `AudioQuality.HIGH` through InnerTubeX and stores the selected source bytes without recompression.

V2 must verify the selector behavior with representative format sets so the highest valid source offered to the authenticated session is selected according to InnerTubeX's intended ranking.

Do not convert lower-bitrate AAC/Opus to 320 kbps MP3 or another inflated format. Transcoding cannot recover source information and would waste space.

Persist and expose the actual selected format metadata already available in the offline record:
- codec;
- MIME/container;
- bitrate;
- sample rate;
- itag;
- content length.

If a song resolves below the user's expected bitrate, the UI should show the real source values rather than implying a higher quality.
## 14. Audio quality UX

The player and downloaded-song details can show a compact quality label such as:
`Opus · 256 kbps · 48 kHz`

Do not clutter every song row with technical audio data by default.

Quality details belong in:
- expanded Now Playing;
- download details/context menu;
- optional small quality badge when useful.

A diagnostic action may resolve available formats for a selected track during development or troubleshooting, but V2 should not expose a complex codec-selection UI unless real use proves it necessary.

The default behavior remains automatic best-quality selection.
## 15. Navigation architecture

Primary navigation becomes:
- Home;
- Explore;
- Search;
- Library;
- Downloads.

Home emphasizes immediate return-to-listening and account-relevant content.

Explore emphasizes discovery: mixes, recommended playlists, albums, artists, and other discovery sections already obtainable through MetroList/YouTube Music data.

Search remains direct and universal.

Library owns the user's playlists, songs, albums, and artists, including local playlists.

Downloads remains focused on offline state and export.

Do not create duplicate destinations for the same job.
## 16. Desktop UX principles

The UI should feel intentionally designed for Windows desktop rather than an enlarged mobile screen.

Use:
- clear iconography with text labels where ambiguity exists;
- predictable placement of common actions;
- hover states;
- right-click/context menus for secondary actions;
- visible keyboard focus;
- sensible pointer targets;
- confirmation only for destructive actions;
- immediate feedback for asynchronous actions;
- skeleton/loading/empty/error states that explain what is happening;
- responsive spacing instead of fixed assumptions about one window size.

Frequent actions should not be hidden inside `...` menus when there is room to expose them.

Secondary and destructive actions belong in contextual menus so primary screens remain calm.
## 17. List and grid behavior

List/grid switching is provided only where both representations are useful.

Appropriate for grid:
- playlists;
- albums;
- artist collections;
- Explore/Home card collections.

Songs remain list-first because title, artist, duration, explicit/download state, and quick actions are easier to scan in rows.

The selected Library presentation is remembered locally.

Grid cards prioritize artwork and title. List rows prioritize scan speed and actions.

Do not force a grid toggle onto screens where it adds no practical value.
## 18. Player experience

Keep the persistent mini-player at the bottom for fast control while browsing.

The mini-player exposes:
- artwork;
- title/artist;
- play/pause;
- previous;
- next;
- progress;
- volume;
- queue/expanded-player affordance.

Clicking the player opens an expanded Now Playing view.

Expanded Now Playing includes:
- large artwork;
- title;
- artist;
- album when known;
- seek/progress;
- previous/play-next;
- shuffle;
- repeat;
- volume;
- real audio-quality details;
- download/offline state;
- queue panel.

The expanded view uses the existing player and queue state. It is presentation, not a second playback implementation.
## 19. Queue and context actions

The queue should be visible and useful:
- current item clearly marked;
- upcoming items shown;
- drag/reorder where practical;
- remove an upcoming item;
- clear upcoming queue;
- jump to an item.

Context menus should expose actions appropriate to the item, for example:
- Play next;
- Add to queue;
- Download / Remove download;
- Add to local playlist;
- Open album;
- Open artist;
- Copy YouTube Music link;
- Remove from local playlist where relevant.

Menus should not show disabled or meaningless actions merely for completeness.
## 20. Keyboard efficiency

Desktop shortcuts should cover high-value actions without creating a large shortcut system.

Initial shortcuts:
- Space: play/pause when focus is not inside a text field;
- Left/Right: seek backward/forward when appropriate;
- Up/Down: volume when appropriate;
- Ctrl+F: focus search/filter for the active screen when available;
- Ctrl+L or another conventional shortcut may focus global search only if it does not conflict with existing behavior;
- Escape: close expanded player/dialog/detail layer where appropriate.

Shortcut hints may appear in tooltips or menus. They should not require a dedicated settings screen in V2.
## 21. Explore

Explore is a lightweight discovery surface backed by existing MetroList/YouTube Music endpoints.

It may contain:
- mixes/recommended playlists;
- albums;
- artists;
- mood/genre or discovery collections when already exposed by the source;
- contextual recommendations.

It does not train a local recommendation model and does not call an external AI service.

The implementation should first reuse data/endpoints already present in the original MetroList code. New YouTube endpoint work is added only when needed for a clearly useful Explore section.

If Explore would merely duplicate Home content, prefer fewer high-value sections over filler.
## 22. Future car multimedia app constraint

The future Android multimedia APK is not part of this V2 implementation.

V2 should nevertheless keep UI state and business logic separated so a landscape/touch presentation can reuse:
- playlist matching results;
- local playlist model;
- playback queue concepts;
- download state;
- Explore/catalog models.

Desktop-specific hover, right-click, window, and keyboard behavior stays in the desktop presentation layer.

No V2 code should assume that mouse hover is required to discover a primary action.
## 23. Error handling and progress

Long-running import and sync operations always expose progress.

Examples:
- `Matching 437 / 1000`;
- `947 ready · 39 review · 14 not found`;
- `Syncing 218 / 947`;
- per-track error detail when synchronization fails.

One failed song does not discard the rest of an import or synchronization run.

Network/session errors are actionable and do not erase completed matching work.

Cancelling matching stops new searches while preserving already processed rows.

No unresolved track is silently substituted during finalization.
## 24. Testing strategy

Use TDD for new non-trivial behavior.

Matcher tests cover:
- title/artist normalization;
- explicit beating equivalent clean;
- original beating live/remix/radio edit;
- original beating remaster/deluxe/acoustic when available;
- fallback versions becoming Review rather than automatic Ready when original is absent;
- duration and album tie-breaking;
- cover/karaoke/altered-version rejection;
- confidence thresholds;
- remembered-match reuse and invalidation.

Playlist persistence tests cover ordering, migration, local creation, rename/remove/reorder, and linked YouTube playlist identity.

Sync tests use a fake gateway around the existing create/add operations and verify partial failures without destructive behavior.
## 25. UI verification

UI tests focus on state and action wiring rather than pixel-copying another service.

Verify:
- list/grid preference;
- Local vs YouTube status;
- import state filters;
- expanded-player open/close;
- queue actions;
- destructive confirmations;
- context actions;
- visible download/sync feedback.

Manual product checks should use realistic large imports, including at least one list with hundreds of rows, because performance and review ergonomics matter more than a tiny happy-path demo.

Do not repeatedly rerun the full V1 download/export probe matrix after presentation-only changes. Run targeted regression checks and a final V1 smoke gate before packaging.
## 26. Implementation boundaries

Add new capabilities around the stable V1 seams:
- catalog/search gateway for matching;
- a dedicated playlist-import/matching unit;
- a dedicated local-library persistence unit;
- a YouTube-playlist sync adapter that delegates to existing MetroList operations;
- Compose presentation components for import/review/library/player/Explore.

Do not:
- rewrite `PersistentOfflineQueue`;
- replace the current media downloader;
- replace InnerTubeX;
- replace libmpv;
- change portable backup format;
- merge local-playlist persistence into `offline.db`;
- introduce a second YouTube API stack.

Keep files focused and reuse existing models/helpers where practical.
## 27. Suggested delivery order

Implement V2 in product-safe slices:

1. audio-quality audit and format visibility, because it is isolated and verifies the current source-quality promise;
2. local library database and local playlist model;
3. TXT/paste parser and matcher with tests;
4. import review UI;
5. local playlist playback/download integration;
6. explicit Sync to YouTube Music;
7. UX foundation improvements: icons, context actions, list/grid, keyboard/hover/focus;
8. expanded Now Playing and queue UX;
9. Explore;
10. final cohesive polish, package build, V1 regression smoke tests, then user acceptance.

Each visible slice should produce a testable app-image before moving to the next major slice.

Do not bump the application version as part of implementation unless the human explicitly requests it.
## 28. Acceptance criteria

V2 is ready for user acceptance when:

1. A TXT/pasted list of up to 1,000 songs can be parsed and matched without freezing the UI.
2. High-confidence originals are accepted while uncertain or undesirable versions go to review.
3. Explicit is preferred over equivalent clean when explicit exists.
4. Live/remix/cover/karaoke/sped/slowed/radio-edit variants do not silently replace originals.
5. Remaster/deluxe/acoustic/unplugged are usable only as fallbacks when the original is unavailable.
6. Review lets the user resolve ambiguous rows before playlist finalization.
7. The user can create the result as Local or YouTube Music.
8. A Local playlist can later be explicitly synced to YouTube Music.
9. Local playlist playback and downloads reuse the existing stable player/download architecture.
10. Actual selected audio codec/bitrate/sample-rate can be inspected and downloads use the best valid source returned by the existing quality path.
11. Library supports list/grid only where useful and remembers the choice.
12. The mini-player remains available while browsing and opens a useful expanded Now Playing screen.
13. Queue operations are discoverable and fast.
14. Icons/actions are consistent, common operations require fewer clicks, and every asynchronous action has visible feedback.
15. Explore provides useful discovery without a new recommendation engine or obvious filler.
16. Existing sign-in, browse, play, download, playlist-download, offline playback, export, and backup compatibility continue to work.
17. The Android project remains green and the car backup format remains unchanged.

## 29. Non-functional requirements

- No user-file deletion outside explicit playlist/download removal actions.
- No secrets in logs or playlist databases.
- No unbounded network concurrency for large imports.
- No UI thread blocking during matching, synchronization, artwork loading, or database work.
- No new heavyweight dependency when Kotlin/JVM, Compose, SQLite, or existing MetroList code already solves the problem.
- Preserve the current intentional uncommitted Windows diff.
- No reset/clean/push/merge.
- Documentation/spec changes are not committed without explicit human authorization.
## 30. Design summary

V2 keeps MetroList's identity and stable media pipeline while making the Windows application feel like a mature music player.

Professional UX here means fewer surprises, fewer unnecessary clicks, clearer states, familiar controls, useful shortcuts, and stronger information hierarchy. It does not mean copying another app's visual skin or importing its feature bloat.

The playlist importer is deliberately conservative: original versions first, explicit preferred when applicable, uncertain matches reviewed by the user, and local playlists never reach YouTube Music unless the user explicitly chooses synchronization.
