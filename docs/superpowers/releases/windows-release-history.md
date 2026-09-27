# MetroList Windows Release History

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
