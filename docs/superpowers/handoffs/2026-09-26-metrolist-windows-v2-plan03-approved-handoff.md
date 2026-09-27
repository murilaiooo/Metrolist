# MetroList Windows V2 — Plan 03 Approved Handoff — 2026-09-26

## Start here

Continue the MetroList Windows project from:

`I:\codex\metrolist-windows\.worktrees\windows-v1`

Read this handoff first. Then read, as needed:

- `docs/superpowers/specs/2026-09-26-metrolist-windows-v2-design.md`
- `docs/superpowers/plans/2026-09-26-metrolist-windows-v2-01-audio-quality.md`
- `docs/superpowers/plans/2026-09-26-metrolist-windows-v2-02-playlist-import-sync.md`
- `docs/superpowers/plans/2026-09-26-metrolist-windows-v2-03-desktop-ux-player.md`
- `docs/superpowers/plans/2026-09-26-metrolist-windows-v2-04-explore.md`
- `docs/superpowers/plans/2026-09-26-metrolist-windows-v2-05-integration-acceptance.md`

Invoke Superpowers as applicable, especially using-superpowers, test-driven-development,
systematic-debugging for reproduced failures, and verification-before-completion.

Do not reset, clean, discard, or rewrite the intentional large uncommitted diff.
Do not push, merge, version-bump, install a replacement MSI, or commit without explicit user authorization.
Do not delete user files or touch unrelated PC files.

## Executive state

V2 Plans 01, 02, and 03 are implemented.

- Plan 01 — real audio-quality selection/telemetry: implementation complete and verified.
- Plan 02 — playlist TXT/paste import, local playlists, matching/review, explicit sync to YouTube Music:
  implementation complete and manually accepted by the user.
- Plan 03 — desktop UX/UI + professional player/queue/touch refinements:
  implementation complete and visually accepted by the user.

The user's final visual response to the current Now Playing was:

> "agora ficou maneiro"

That acceptance came after several visual iterations. Preserve the current visual direction.
Do not revert toward the earlier V1-style shell or reintroduce duplicated player surfaces.

The next planned product slice is:

**Plan 04 — Explore**

After Explore is accepted, execute:

**Plan 05 — Integration and Acceptance**

Do not begin the future car APK yet. It remains a later phase after the Windows app is fully complete.

## Worktree / Git state

Worktree:

`I:\codex\metrolist-windows\.worktrees\windows-v1`

Branch:

`feature/windows-v1`

Current HEAD:

`8b5da69b0 fix(desktop): include HTTP runtime module`

The worktree contains a very large intentional modified + untracked V1/V2 diff.
This is expected.

Important Git guardrails:

- never `git reset`
- never `git clean`
- never discard the user's diff
- never `git add .`
- if staging is ever explicitly authorized, stage only exact intended files
- no push/merge without explicit authorization

Current V2 SDD evidence is under:

`.superpowers\sdd\2026-09-26-metrolist-windows-v2-01-audio-quality`
`.superpowers\sdd\2026-09-26-metrolist-windows-v2-02-playlist-import-sync`
`.superpowers\sdd\2026-09-26-metrolist-windows-v2-03-desktop-ux-player`

## Latest verification — exact accepted build

After the final touchscreen and duplicate-player cleanup, a fresh Windows gate was run:

`./gradlew.bat :desktop-core:test :desktop-app:test :desktop-app:createDistributable`

Result:

**BUILD SUCCESSFUL**

Evidence:

`.superpowers\sdd\2026-09-26-metrolist-windows-v2-03-desktop-ux-player\handoff-final-windows-gate.txt`

Latest `git diff --check` returned no whitespace errors.
Only the normal Windows LF -> CRLF warnings were emitted.

Earlier in Plan 03, after the substantial UX/player work, the full Android serial regression gate was also run:

`./gradlew.bat --no-daemon --max-workers=1 :app:testFossDebugUnitTest :app:testGmsDebugUnitTest :app:testIzzyDebugUnitTest :app:assembleFossDebug :app:assembleGmsDebug :app:assembleIzzyDebug`

Result:

**BUILD SUCCESSFUL**, 205 actionable tasks.

The final touchscreen-only refinements touched desktop UI only; Plan 05 must rerun the Android serial gate.

## Current TEST build

Current app-image executable:

`I:\codex\metrolist-windows\.worktrees\windows-v1\desktop-app\build\compose\binaries\main\app\MetroListWindows\MetroListWindows.exe`

Desktop development shortcut:

`MetroList Windows TESTE.lnk`

The helper used to refresh the shortcut is:

`.superpowers\sdd\2026-09-26-metrolist-windows-v2-03-desktop-ux-player\refresh-test-shortcut.ps1`

The helper used to restart only the worktree TEST build is:

`.superpowers\sdd\2026-09-26-metrolist-windows-v2-03-desktop-ux-player\restart-test-build.ps1`

Do not confuse the TEST app-image with the installed V1 MSI app.

Installed V1 package remains version **0.1.2**.
Do not bump or replace it until Plan 05's explicit installer authorization gate.

## Plan 01 — audio-quality state

Production stream resolution intentionally remains on:

`AudioQuality.HIGH`

Live authenticated probes showed HIGH produced larger/equal real payload than AUTO on tested tracks.

Important rule:

**Do not fake 320 kbps.**

The downloader/player preserves the actual selected source stream.
The UI reports real codec / effective bitrate / sample rate.

Effective average bitrate is computed from content length and duration when possible because provider-reported
bitrate metadata proved inconsistent in live probes.

Typical displayed values may be AAC or Opus around 120–180+ kbps depending on the real source.
That is not itself a regression.

Do not transcode lower source audio to 320 kbps merely to display a larger number.

## Plan 02 — importer and local playlist state

The playlist importer supports:

- TXT file input
- pasted multiline input
- large imports
- confidence-based matching
- persisted import jobs / resume
- candidate review
- remembered manual corrections
- local-only playlist creation
- explicit Sync to YouTube Music

Matching policy approved by the user:

- original version always wins
- explicit is preferred when an equivalent explicit version exists
- clean loses to explicit
- live is strongly penalized and never silently replaces studio/original
- remix does not replace original
- covers/karaoke/sped-up/slowed/nightcore are rejected when identifiable
- acoustic/unplugged/remaster/deluxe are acceptable fallbacks only when original is unavailable
- uncertain matches go to review instead of being blindly accepted

Local playlists are intentionally separate from YouTube Music playlists.

A local playlist must never auto-sync in the background.

YouTube Music sync is only reachable from:

- explicit YouTube destination finalization, or
- explicit "Sync to YouTube Music" user action

`library.db` is separate from `offline.db`.

The user manually tested a 300-line import and reported it worked perfectly.
Plan 02 is accepted and closed.

One full-suite `PlaylistImportUiTest` timed out once at a 2-second wait during the later visual redesign.
The exact test was rerun in isolation three times with `--rerun-tasks` and passed all three times.
No accepted importer logic was changed because the failure was not reproducible.

## Plan 03 — approved UX direction

Critical product principle from the user:

Do not copy Spotify/Apple Music visually.

Instead, incorporate the mature UX lessons of large desktop music players:
clear hierarchy, obvious interactions, high information density where useful, predictable placement,
professional iconography, contextual actions, minimal friction, and touch-friendly targets.

Before the major shell redesign, references were benchmarked from mature desktop music players
including Spotify, Apple Music, YouTube Music, Deezer/TIDAL-style patterns.

The user rejected the first Plan 03 visual pass because it felt like "V1 plus new screens".
The shell was then redesigned end-to-end rather than merely adding more screens.

Preserve the redesigned shell described below.

## Current approved shell

The inactive full-width "Download activity" banner is gone.

Download activity now appears only when something needs attention:
downloading, queued, or failed work.

Transient feedback uses snackbar behavior rather than permanently occupying the top of the window.

Sidebar:

- narrower and visually quieter
- MetroList identity remains purple
- Home / Search / Library / Downloads are compact primary navigation
- account/sign-out are secondary at the bottom
- no old bulky NavigationDrawerItem treatment

Global shape radii were reduced to avoid the earlier overly-rounded "card everywhere" look.

## Current approved Home / Search / Library behavior

Home:

- artwork-led horizontal sections
- media cards are the primary interaction
- no persistent "Open" buttons
- hover provides subtle desktop feedback

Search:

- one clean search field
- search icon
- Enter / IME search action
- no separate large Search button

Library:

- professional title/subtitle header
- compact Create / Import action
- refresh icon
- lighter text-style category tabs
- List/Grid toggle via icons
- Songs remain list-only
- Playlists / Albums / Artists may use grid
- per-tab presentation choice persists

Rows/cards are themselves the primary interaction.
Secondary actions live in hover/context menus rather than permanent Play/Open/Download buttons.

## Current approved mini-player

The bottom mini-player is a low, tripartite desktop bar:

Left:
- artwork
- title
- artist

Center:
- Previous
- Play/Pause
- Next
- timeline / current time / duration

Right:
- real quality text
- volume
- queue button

Touchscreen refinement is important.

The entire left media block — artwork + title + artist + the surrounding block height —
is one clickable/touchable rectangle that opens Expanded Now Playing.

The old small chevron/open-player icon was removed at the user's request.

Transport and queue hit targets are approximately 44–48 dp.
Seek and volume sliders have increased vertical touch area.
The mini-player height/spacing were increased slightly to make touch use comfortable without looking oversized.

## Current approved Expanded Now Playing

Expanded Now Playing uses the main content canvas.
It must not look like a giant card placed inside another app surface.

It includes:

- large centered artwork
- title
- artist
- album
- real codec / effective bitrate / sample-rate badge
- full seek bar
- shuffle
- previous
- play/pause/replay
- next
- repeat
- volume
- secondary download status/action
- optional queue panel

The user specifically noticed duplicated controls when Expanded Now Playing and the mini-player were both visible.

Final approved rule:

**When Expanded Now Playing is open, the bottom mini-player is completely hidden.**

Closing Expanded Now Playing restores the mini-player.

This is covered by `PlayerUiTest.shouldShowMiniPlayer` behavior.
Do not reintroduce both player surfaces simultaneously.

## Current approved queue behavior

Queue mutations are real player operations, not UI-only decoration:

- Play Next
- Add to Queue
- move upcoming entry
- remove upcoming entry
- clear upcoming

Queue mutations preserve the currently playing entry correctly, including duplicate song IDs.

Mutating the queue does not unnecessarily reload the current mpv track.

"Play next" remains literally next even when shuffle is enabled.

Expanded queue UI is denser than the first Plan 03 iteration:

- current track highlighted
- "Up next" shown only where useful
- secondary move/remove/play controls appear on hover
- no permanent wall of 4 icons on every queue row

Core queue mutation tests and MpvPlayer integration tests are green.

## Keyboard / desktop interaction

Global shortcuts implemented:

- Space: play/pause
- Left/Right: seek -/+ 5 seconds
- Up/Down: volume -/+ 5
- Ctrl+F: focus the relevant search/filter
- Escape: close transient/expanded surfaces

Important focus rule:

Text fields suppress media shortcuts while the user is typing.
Escape still gets priority for closing surfaces.

The handler uses bubbling `onKeyEvent`, not preview interception, so focused buttons/sliders retain their own
keyboard behavior before global player shortcuts run.

Context menus and right-click share the same action availability model.

## Local playlist UX after redesign

Local playlist pages now use the same visual system as the rest of the app.

- back is an icon, not a text button
- Play is primary
- Shuffle / Download all / Sync are secondary
- search uses the shared rounded search-field language
- rename/delete are compact
- song rows are single-layer rows
- reorder arrows appear on hover
- remove stays in contextual actions

Do not restore the previous pattern of:
song row + divider + separate Up / Down / Remove button strip under every track.

## Downloads UX after redesign

Downloads remains feature-rich but should not look like an admin panel.

Current hierarchy:

- title + concise state summary
- Export backup as the clear primary action
- retry/cancel/remove-offline as secondary text actions
- one search field with search icon
- compact filters
- detailed per-download progress remains available

Do not restore the old full-width permanent top banner or large maintenance-button rows.

All validated V1 download/export architecture is still considered stable.
Do not rewrite it without a concrete reproduced regression.

## Important files added / heavily involved in Plan 03

Desktop app UI:

- `App.kt`
- `ChromeUi.kt`
- `CatalogScreens.kt`
- `CatalogRows.kt`
- `CatalogGrid.kt`
- `CatalogContextMenu.kt`
- `DownloadsScreen.kt`
- `LocalPlaylistUi.kt`
- `PlaylistImportUi.kt`
- `NowPlayingUi.kt`
- `PlayerUi.kt`
- `DesktopShortcuts.kt`
- `UiPreferences.kt`
- `UiTheme.kt`
- `UiUtils.kt`

Player/core:

- `desktop-core/.../player/DesktopPlayer.kt`
- `desktop-core/.../player/MpvPlayer.kt`
- `desktop-core/.../player/QueueMutation.kt`
- `desktop-core/.../player/PlaybackAudioFormat.kt`

Important tests include:

- `CatalogActionsTest.kt`
- `CatalogPresentationTest.kt`
- `DesktopShortcutsTest.kt`
- `DownloadActivityPresentationTest.kt`
- `PlayerUiTest.kt`
- `UiPreferencesTest.kt`
- `QueueMutationTest.kt`
- `MpvPlayerTest.kt`

Plan 03 SDD ledger:

`.superpowers\sdd\2026-09-26-metrolist-windows-v2-03-desktop-ux-player\progress.md`

It records each visual acceptance iteration and the exact verification logs.

## Next step — Plan 04 Explore

Execute:

`docs/superpowers/plans/2026-09-26-metrolist-windows-v2-04-explore.md`

Primary goals:

- add `AppDestination.EXPLORE`
- use existing YouTube Music / MetroList discovery data
- reuse `YouTube.explore()` and existing mood/genre browse endpoints
- new releases should be artwork-led
- moods/genres should be compact and useful
- do not duplicate Home merely to fill the screen
- no external AI recommendation service
- no filler sections

Navigation order in the Plan 04 design is:

Home, Explore, Search, Library, Downloads.

Explore must adopt the approved Plan 03 visual language rather than introducing a new style.

## After Explore — Plan 05 acceptance

Run the complete integration/acceptance plan.

Important gates include:

- full Windows automated suite
- V1 playback/download/export regression smoke
- large importer acceptance, ultimately around 1,000 tracks
- explicit/clean/live/remix/remaster/deluxe/acoustic matching spot checks
- local-only playlist persistence check
- explicit YouTube sync check
- responsive/touch/keyboard UX check
- Explore usefulness check
- Android serial Foss/GMS/Izzy gate
- final Windows distributable gate

Do not version-bump or install a new MSI until the user explicitly authorizes the installer step.

The final physical car import remains a separate real-world acceptance gate where applicable.

## Future car APK

Do not begin this now.

After the Windows app is fully complete, the user wants a separate APK for a car multimedia system.

Known direction:

- landscape
- touch-first
- visually/interaction-wise related to the Windows app
- reuse shared domain/business logic where practical
- user will provide hardware/display details later

The desktop UI work intentionally moved toward touch-friendly hit targets and responsive layout so this future phase
has a cleaner conceptual foundation.

Do not compromise the current Windows desktop experience merely to pre-build the car APK.

## Continuation prompt for a new conversation

Use this:

> Continue the MetroList Windows V2 project from
> `I:\codex\metrolist-windows\.worktrees\windows-v1`.
> First read
> `docs/superpowers/handoffs/2026-09-26-metrolist-windows-v2-plan03-approved-handoff.md`.
> Plans 01–03 are complete; Plan 02 and Plan 03 have user acceptance.
> Preserve the currently approved UX/player shell and the stable V1 download/export architecture.
> Invoke Superpowers as applicable and execute
> `docs/superpowers/plans/2026-09-26-metrolist-windows-v2-04-explore.md`
> task-by-task with TDD and verification.
> Do not reset/clean/delete user files, do not touch unrelated PC files, do not push/merge,
> do not version-bump/install a new MSI, and do not commit unless explicitly authorized.
> Keep Android green. After Explore user acceptance, proceed to Plan 05 integration/acceptance.

## Final note

The last user-visible accepted state is the redesigned Expanded Now Playing with no duplicated bottom mini-player.

The mini-player itself is touch-refined:
- artwork + title + artist form one large clickable/touchable media target
- this target opens Expanded Now Playing
- the old dedicated open-player chevron is removed
- transport/queue hit targets are enlarged for touch

The user said the final result looked good:

> "agora ficou maneiro"

Treat this as the visual baseline going forward.
