# MetroList — Windows 0.2.2 Final → Car APK Handoff

Date: 2026-09-27

## Source of truth

Repository:
`I:\codex\metrolist-windows`

Release branch during hotfix:
`hotfix/windows-0.2.2`

Target baseline after merge:
`main`

Windows product version:
`0.2.2`

Windows version source of truth:
`gradle.properties`
`metrolistWindowsVersion=0.2.2`

Versioning policy:
`docs/superpowers/releases/versioning-policy.md`

Release history:
`docs/superpowers/releases/windows-release-history.md`

## Windows 0.2.2 status

Windows 0.2.2 fixes high-volume download stream-resolution failures that appeared after a large batch of downloads.

Root cause:
- some YouTube Music tracks exhausted the direct extraction clients because the Windows resolver had no PO-token provider;
- diagnostics repeatedly showed `GVS PO-token provider unavailable`;
- a fallback response could be obtained through the extractor, but the Windows downloader intentionally does not consume SABR;
- transient `NO_PLAYABLE_STREAM` failures were being promoted to permanent FAILED state immediately.

Fix:
- WebView2/BotGuard PO-token generation was added to the existing Windows Auth Helper;
- DesktopInnerTubeXPlayer now has a PO-token-enabled fallback extractor used only for `NO_PLAYABLE_STREAM`;
- genuinely unavailable tracks do not use the fallback;
- MetroListOfflineWorker retries transient `NO_PLAYABLE_STREAM` and network resolver failures with backoff before failing;
- existing direct media transfer, resume, format metadata, offline DB, and backup architecture remain intact.

## Verification

Manual:
- user retried the original failed batch on 0.2.2 TEST;
- result: `Failed 0`;
- accepted state showed 1,788 offline tracks / 7.03 GB.

Windows automated:
- desktop core/export/app tests: BUILD SUCCESSFUL;
- 0.2.2 distributable + MSI: BUILD SUCCESSFUL;
- package verifier: `WINDOWS_PACKAGE_VERIFY_OK`;
- packaged executable: `APP_VERSION=0.2.2`.

Android regression gate:
`./gradlew.bat --no-daemon --max-workers=1 :app:testFossDebugUnitTest :app:testGmsDebugUnitTest :app:testIzzyDebugUnitTest :app:assembleFossDebug :app:assembleGmsDebug :app:assembleIzzyDebug`

Result:
BUILD SUCCESSFUL, 205 tasks.

## Compatibility baseline

Treat Windows 0.2.2 as the new compatibility baseline.

Preserve:
- `.metrolistbackup` compatibility;
- `library.db` separate from `offline.db`;
- existing session/offline media/artwork persistence;
- truthful stream codec/bitrate/sample-rate reporting;
- local playlists stay local unless explicitly synced;
- existing playback/download/export architecture;
- versioning policy for every shipped Windows or Car build.

## Car APK requirement

The Car APK must use its own semantic version source of truth.
Do not reuse Windows 0.2.2 or upstream Android 13.x as the Car product version.
Show the exact Car version discreetly in the car UI and include it in diagnostics.

## Suggested next-conversation prompt

Continue the MetroList project from `I:\codex\metrolist-windows` on `main`. First read `docs/superpowers/handoffs/2026-09-27-metrolist-windows-0.2.2-to-car-apk-final-handoff.md`, `docs/superpowers/releases/versioning-policy.md`, and `docs/superpowers/releases/windows-release-history.md`. Invoke Superpowers before acting. MetroList Windows 0.2.2 is the complete, installed, manually approved compatibility baseline. Start the Car APK phase with discovery and design: inspect the Android architecture, existing car/landscape code, Android import path, and the `.metrolistbackup` portable models/validator. Define the Car APK's own SemVer from the first build. Keep Windows and Android regression gates green and do not push or install APKs without explicit authorization.
