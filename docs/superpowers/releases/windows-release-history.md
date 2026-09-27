# MetroList Windows Release History

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
