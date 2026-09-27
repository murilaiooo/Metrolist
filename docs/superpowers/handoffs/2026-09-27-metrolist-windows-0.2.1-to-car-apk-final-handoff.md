# MetroList — Windows 0.2.1 Final → Car APK Handoff

Date: 2026-09-27

## Source of truth

Repository:
`I:\codex\metrolist-windows`

Branch:
`main`

Windows product version:
`0.2.1`

Windows version source of truth:
`gradle.properties`
`metrolistWindowsVersion=0.2.1`

Versioning policy:
`docs/superpowers/releases/versioning-policy.md`

Release history:
`docs/superpowers/releases/windows-release-history.md`

## Windows status

MetroList Windows is complete for the current phase and user-approved.
The installed 0.2.1 build was manually tested on 2026-09-27, including the song overflow / three-dot menu hotfix.

Installed executable:
`C:\Program Files\MetroListWindows\MetroListWindows.exe`

Final 0.2.1 MSI:
`I:\codex\metrolist-windows\desktop-app\build\compose\binaries\main\msi\MetroListWindows-0.2.1.msi`

Automated Windows verification:
- desktop core/export/app tests: BUILD SUCCESSFUL
- distributable + MSI packaging: BUILD SUCCESSFUL
- package verifier: `WINDOWS_PACKAGE_VERIFY_OK`
- packaged executable reports `APP_VERSION=0.2.1`
- pinned mpv hash verified
- Auth Helper and WebView2 runtime checks passed

Manual verification:
- final 0.2.1 installed over 0.2.0 successfully
- session, offline database, and library database remained intact
- user tested the three-dot overflow menu and confirmed “deu tudo certo”
- Desktop shortcut points to the installed Program Files build

## Windows behavior to preserve

Do not regress these accepted contracts:
- actual audio stream metadata only; never fake 320 kbps
- stable playback/download/export architecture
- `library.db` remains separate from `offline.db`
- local playlists do not sync to YouTube Music unless explicitly requested
- `.metrolistbackup` remains the Windows ↔ car compatibility boundary
- expanded Now Playing hides the mini-player
- artwork and the media/title region open expanded Now Playing
- song/catalog overflow menus must remain usable after the pointer leaves the row
- every packaged Windows hotfix/feature gets a new semantic version and release-history entry

Do not modify Windows merely to simplify the car implementation. Change Windows only for a concrete compatibility requirement or reproduced regression.

## Android baseline

The existing upstream Android app module currently uses:
- `versionName = 13.7.0`
- `versionCode = 153`
- visible version in its About screen
- version information in playback/crash diagnostics

The final Android regression gate after Windows 0.2.1 changes passed:
`./gradlew.bat --no-daemon --max-workers=1 :app:testFossDebugUnitTest :app:testGmsDebugUnitTest :app:testIzzyDebugUnitTest :app:assembleFossDebug :app:assembleGmsDebug :app:assembleIzzyDebug`
Result: BUILD SUCCESSFUL, 205 tasks.

## Car APK versioning requirement

The Car APK is a separate product surface and must have its own semantic version track.
Do not silently reuse Windows `0.2.1` or upstream Android `13.7.0` as the car version.

From the first car build:
- define one tracked Car version source of truth
- use it for APK `versionName`
- show it discreetly in the car UI
- include it in diagnostic/error reports
- bump it for every build intentionally handed to the user
- record each shipped hotfix/feature in a Car release history

Follow:
`docs/superpowers/releases/versioning-policy.md`

## First work in the next conversation

Do discovery/design before implementation:
1. Read this handoff and the Windows 0.2.1 release/versioning docs.
2. Invoke Superpowers before substantial work.
3. Inspect the existing Android architecture and identify the safest car-specific product boundary.
4. Inspect the current Android backup/import path.
5. Inspect `desktop-portable` models, validator, and the current `.metrolistbackup` contract.
6. Inspect any existing landscape / Android Auto / car-oriented code before designing a new surface.
7. Establish the target car hardware, Android version, display resolution/aspect ratio, touch behavior, storage/import mechanism, and offline expectations.
8. Write a focused Car APK design/spec and implementation plan before coding.
9. Keep the existing Android app and Windows 0.2.1 green throughout.

## Safety / repository guardrails

- Work from `I:\codex\metrolist-windows` on `main` unless a new feature branch/worktree is deliberately created.
- Do not reset, clean, delete user files, or touch unrelated PC directories.
- Reproduce/root-cause bugs before fixes.
- Use TDD for behavior changes.
- Preserve backup compatibility unless a versioned backward-compatible migration is designed.
- Do not push to remotes or install APKs without explicit user authorization.
- Keep changes scoped; avoid broad refactors without evidence they are necessary.

## Suggested opening prompt

Continue the MetroList project from `I:\codex\metrolist-windows` on `main`. First read `docs/superpowers/handoffs/2026-09-27-metrolist-windows-0.2.1-to-car-apk-final-handoff.md`, `docs/superpowers/releases/versioning-policy.md`, and `docs/superpowers/releases/windows-release-history.md`. Invoke Superpowers before acting. MetroList Windows 0.2.1 is complete, installed, manually approved, and is now the compatibility baseline; do not redesign it without a concrete regression or car-compatibility requirement. Start the Car APK phase with discovery and design: inspect the Android architecture, existing landscape/Android Auto/car-related code, the Android import path, and the `.metrolistbackup` portable models/validator. Establish the actual car hardware/display/storage constraints, then write a focused spec and implementation plan for a landscape, touch-first, offline-first car experience. The Car APK must have its own SemVer source of truth, visible in the car UI and diagnostics from the first build. Keep Windows and Android regression gates green and do not push/install without explicit authorization.
