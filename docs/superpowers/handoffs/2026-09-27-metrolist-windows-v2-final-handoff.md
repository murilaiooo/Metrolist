# MetroList Windows V2 — Final Handoff

Date: 2026-09-27
Branch: `feature/windows-v1`
Worktree: `I:\codex\metrolist-windows\.worktrees\windows-v1`
Current HEAD before V2 integration work: `8b5da69b0`

## Status
V2 Plans 01–05 are implemented and accepted.
The user manually approved Plan 04 Explore and, on 2026-09-27, confirmed the final Plan 05 smoke with “tudo certo”.
No commit, push, merge, reset, clean, version bump, or replacement MSI was performed during Plan 05.

## Final TEST build
Executable:
`I:\codex\metrolist-windows\.worktrees\windows-v1\desktop-app\build\compose\binaries\main\app\MetroListWindows\MetroListWindows.exe`

Desktop TEST shortcut remains the intended easy entry point.
Final app-image was launched after the final integration gates.

## Automated evidence
Windows acceptance gate:
`./gradlew.bat :desktop-core:test :desktop-export:test :desktop-app:test`
Result: BUILD SUCCESSFUL.

Windows final distributable gate:
`./gradlew.bat :desktop-core:test :desktop-export:test :desktop-app:test :desktop-app:createDistributable`
Result: BUILD SUCCESSFUL.

`git diff --check` passed; only normal LF/CRLF conversion warnings were emitted.

Android serial gate:
`./gradlew.bat --no-daemon --max-workers=1 :app:testFossDebugUnitTest :app:testGmsDebugUnitTest :app:testIzzyDebugUnitTest :app:assembleFossDebug :app:assembleGmsDebug :app:assembleIzzyDebug`
Result: BUILD SUCCESSFUL, 205 actionable tasks.

## Acceptance coverage
The 17 V2 acceptance criteria are mapped in:
`.superpowers\sdd\2026-09-26-metrolist-windows-v2-05-integration-acceptance\acceptance-checklist.md`

Direct tests cover the 1,000-line parser limit, bounded/resumable matching, cancellation persistence, explicit-vs-clean preference, rejection/review of undesirable versions, local-only playlist creation, explicit YouTube Music sync/retry, queue mutation, real audio-format display, list/grid persistence, async feedback, and Explore filtering/navigation.

The user’s final manual smoke confirmed the current build’s core navigation, playback/player flow, downloads, playlist/local-library behavior, export, and overall V2 acceptance.

## Data and packaging safety
Existing user data remained present after final build checks:
- `auth\session.bin`
- `desktop\offline.db`
- `desktop\library.db`
- existing media and artwork directories

Final app-image contains the packaged Auth Helper, WebView2 loader, and `libmpv-2.dll`.
`:desktop-export` is mapped in `settings.gradle.kts` to the `desktop-portable` directory, so its tests are the backup/portable-format gate.

## Product state to preserve
- Do not fake 320 kbps; expose only the actual selected stream metadata.
- Local playlists remain local unless the user explicitly chooses YouTube Music sync.
- Keep `library.db` separate from `offline.db`.
- Preserve the accepted Plan 03 shell/player/touch design.
- Expanded Now Playing hides the mini-player; artwork and the whole title/media region open the expanded player.
- Preserve the working V1 download/export architecture unless a concrete regression is reproduced.
- Explore is accepted as-is for V2; future visual refinement is optional and should not expand scope.

## Known limitation / deferred work
No blocker remains for V2 acceptance.
The installed MSI remains version `0.1.2`; the final V2 app-image has not been promoted through a new installer because version bump/install requires explicit user authorization.
The large intentional modified/untracked working tree remains intact.

## Next phase
Do not begin automatically.
When the user is ready, the next major product phase is the future landscape car APK / car-focused experience.
Before that phase, preserve backup compatibility with the current `.metrolistbackup` format and use this handoff as the Windows V2 baseline.
