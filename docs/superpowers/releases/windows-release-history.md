# MetroList Windows Release History

## 0.3.0 — 2026-09-27

Status: built and automated gates verified; awaiting user installation/manual smoke.

Changes:
- added the Car Library v1 export for direct folder-based playback on the MetroList Car APK;
- preserves original/high-quality artwork sources when available;
- exports incrementally, reusing unchanged media and artwork instead of rebuilding the whole library;
- publishes library.json atomically after media/artwork are ready;
- keeps the legacy .metrolistbackup export as a compatibility fallback;
- added clear Car Library export progress and destination selection in the Windows UI.

Compatibility:
- MetroList Car 0.1.0 reads the exported folder directly from persistent head-unit storage;
- audio does not need to be imported or duplicated into app-private storage;
- legacy Windows backups remain supported separately.

Verification:
- desktop-core/export/app tests passed;
- Windows distributable + MSI packaging passed;
- package verifier: WINDOWS_PACKAGE_VERIFY_OK;
- packaged executable reports APP_VERSION=0.3.0;
- standard Android Foss/GMS/Izzy regression gate passed: BUILD SUCCESSFUL, 205 tasks.

Artifact:
desktop-app/build/compose/binaries/main/msi/MetroListWindows-0.3.0.msi
SHA-256: 9b6201f2574ac14a13f7b8503520c9b8f0afd90546f273d83ab1fbdd5e5b4507

## 0.2.3 — 2026-09-27

Status: approved by user after manual installed-login smoke.

Root cause:
- the packaged Auth Helper let WebView2 choose its default user-data directory beside the executable;
- in the installed app that directory is under `C:\Program Files\MetroListWindows\...`, where a normal user cannot write;
- WebView2 therefore failed before the YouTube Music login window could open, and the desktop app surfaced helper exit 1.

Changes:
- moved the Auth Helper WebView2 user-data folder to `%LOCALAPPDATA%\MetroListWindows\WebView2`;
- applied the same profile location to both interactive login and PO-token generation;
- added a regression self-test that rejects a WebView2 profile outside LocalAppData.

Manual verification:
- user installed the 0.2.3 TEST MSI and confirmed the YouTube Music sign-in flow works;
- the previous "Authentication helper failed with exit 1" failure no longer blocked the login window;
- the app was intentionally left running after approval while the user continued downloading music; that active download session was not interrupted for further testing.

Automated verification:
- RED observed before implementation: Auth Helper build failed because the new profile contract did not exist;
- focused Auth Helper build + self-test passed after the fix;
- Windows core/export/app tests and 0.2.3 distributable + MSI packaging: BUILD SUCCESSFUL;
- package verifier: `WINDOWS_PACKAGE_VERIFY_OK`, packaged executable reports `APP_VERSION=0.2.3`;
- packaged Auth Helper stayed alive with the YouTube Music login window responsive and created its profile under LocalAppData instead of Program Files.

## 0.2.2 — 2026-09-27

Status: approved by user after manual high-volume download recovery smoke.

Root cause:
- some YouTube Music tracks required a GVS PO-token-capable extraction path;
- Windows had no PO-token provider, so the extractor exhausted direct clients and returned `NO_PLAYABLE_STREAM`;
- transient resolver misses were also being promoted to permanent `FAILED` state on the first resolution failure.

Changes:
- added Windows WebView2/BotGuard PO-token generation through the existing Auth Helper;
- added a desktop `TokenProvider` bridge for InnerTubeX;
- added PO-token fallback only for `NO_PLAYABLE_STREAM` (not genuinely unavailable tracks);
- added resolver retry/backoff for transient `NO_PLAYABLE_STREAM` and network failures before marking a download failed;
- preserved the existing direct media download pipeline and SABR prohibition.

Manual verification:
- the original failed-download scenario was retried on 0.2.2 TEST;
- user confirmed success with `Failed 0`;
- library reached 1,788 offline tracks / 7.03 GB in the accepted smoke.

Automated verification:
- focused resolver fallback/retry/PO-token provider tests passed;
- Windows core/export/app tests: BUILD SUCCESSFUL;
- distributable + MSI 0.2.2 packaging: BUILD SUCCESSFUL;
- package verifier: `WINDOWS_PACKAGE_VERIFY_OK`;
- packaged executable reports `APP_VERSION=0.2.2`;
- Android Foss/GMS/Izzy tests and assemblies: BUILD SUCCESSFUL, 205 tasks.

## 0.2.1 — 2026-09-27

Status: approved and installed. Final manual overflow-menu smoke passed on 2026-09-27.

Changes:
- Fixed song/catalog overflow menu disappearing when pointer leaves the hovered row to enter the popup.
- Added visible in-app Windows version labels.
- Added `--version` diagnostic output.
- Centralized Windows version in `gradle.properties`.
- Package verifier now rejects executable/package version mismatches.
- Established product versioning policy for Windows and future Car APK.

Automated verification:
- focused RED test observed before implementation;
- focused GREEN tests passed;
- Windows core/export/app tests passed;
- Windows distributable + MSI packaging passed;
- `WINDOWS_PACKAGE_VERIFY_OK`;
- packaged executable reports `APP_VERSION=0.2.1`;
- Android Foss/GMS/Izzy tests and assemblies: BUILD SUCCESSFUL, 205 tasks.

Artifact:
`desktop-app/build/compose/binaries/main/msi/MetroListWindows-0.2.1.msi`

## 0.2.0 — 2026-09-27

Status: approved Windows V2 baseline.

Changes:
- Windows V2 Plans 01–05.
- Audio-quality truthfulness, playlist importer/sync, desktop UX/player/queue/touch overhaul, Explore, integration/acceptance.

Verification:
- final Windows gates passed;
- package verifier passed;
- Android Foss/GMS/Izzy gate passed;
- user manually approved the final V2 smoke.

Commit baseline:
`906379b0e docs(desktop): finalize Windows V2 release handoff`
