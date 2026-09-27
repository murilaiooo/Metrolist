# MetroList Windows V2 — Final Handoff

Date: 2026-09-27
Release baseline: `I:\codex\metrolist-windows`
Branch: `main`
V2 code baseline after promotion: `9d30d5109`

## Status
V2 Plans 01–05 are implemented and accepted.
The user manually approved Plan 04 Explore and, on 2026-09-27, confirmed the final Plan 05 smoke with “tudo certo”.
During Plan 05 itself no promotion was performed. After final user acceptance, the user explicitly authorized release promotion: V2 was committed, versioned as `0.2.0`, and fast-forwarded into local `main`. The post-release `0.2.1` hotfix adds action-menu reliability and formal app-version traceability. Nothing was pushed to the remote.

## Final release build
Executable:
`I:\codex\metrolist-windows\desktop-app\build\compose\binaries\main\app\MetroListWindows\MetroListWindows.exe`

MSI:
`I:\codex\metrolist-windows\desktop-app\build\compose\binaries\main\msi\MetroListWindows-0.2.1.msi`

The final app-image and MSI are built from `main`. The package verifier reports `WINDOWS_PACKAGE_VERIFY_OK` with the pinned mpv hash and packaged Auth Helper/WebView2 runtime.

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

## Release status / deferred work
No blocker remains for V2 acceptance.
The current Windows release line is `0.2.1`. Its MSI was generated, package-verified with `APP_VERSION=0.2.1`, and installed over `0.2.0` without changing the existing session/library/offline databases. Version policy and history live under `docs/superpowers/releases/`.
The former `feature/windows-v1` worktree is no longer the source of truth; `main` is the Windows V2 baseline.

## Next phase
Do not begin automatically.
When the user is ready, the next major product phase is the future landscape car APK / car-focused experience.
Before that phase, preserve backup compatibility with the current `.metrolistbackup` format and use this handoff as the Windows V2 baseline.
