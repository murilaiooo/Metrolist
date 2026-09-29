# MetroList Car Release History

## 0.4.0 — 2026-09-28

Status: production-signed release APK built, signature-verified, installed, and AVD-smoked; source commit authorized for local `main`, with no push/merge authorization.

Changes:
- replaced the stock navigation rail presentation with the user-approved compact rounded-square automotive controls and refined active-state treatment;
- added the user-approved reactive seek bar with artwork-derived gradient, larger touch thumb, subtle glow, and audio-responsive energy;
- made audio signal metering opt-in and lifecycle-bound so per-sample tracking stops when playback pauses or Now Playing leaves composition;
- kept the approved Now Playing composition, playback/data contracts, and Windows interoperability unchanged.

Housekeeping:
- moved ignored JVM crash diagnostics out of the repository root into `.superpowers/diagnostics/jvm-crashes/` without deleting diagnostic evidence;
- preserved the local `.metrolistbackup` user file unchanged and did not import any backup or Car Library during acceptance;
- refreshed the versioning policy and established a new current handoff so historical 0.3.0/earlier handoffs are not mistaken for current state.

Verification:
- `git diff --check`: PASS;
- Car tests + assemble: PASS;
- Foss tests + assemble: PASS;
- GMS tests + assemble: PASS;
- Izzy first attempt: Gradle daemon/JVM native `EXCEPTION_ACCESS_VIOLATION` in `jvm.dll` GC thread before product gate; rerun in a fresh single-use Zulu JVM: PASS;
- Windows `desktop-core`, `desktop-export`, `desktop-app` tests + `createDistributable`: PASS;
- production `:app:assembleCarRelease` with R8/minify, resource shrink, and release signing: PASS;
- exact signed release APK install + launch on `MetroList_Car_API29`: PASS;
- release viewport checks: 800x480 = 0 overflow, 1024x600 = 0 overflow, 1280x720 = 0 overflow; AVD restored to 1024x600 @ 160 dpi;
- release package identity: applicationId `com.metrolist.music.car`, versionName `0.4.0`, versionCode `4000`;
- release APK size: 23,247,763 bytes;
- release APK SHA-256: `a9ac4c5a89d97a8cc4fc7229e47e4845e78cfdeae44d84880d75898069677b23`;
- APK Signature Scheme v2: PASS; signer certificate SHA-256 `1323debbc967612f305d55b8c7261d0b821b56ff02967ba18d07d1e403bd0e43`.

See `docs/superpowers/reviews/2026-09-28-metrolist-car-0.4.0-housekeeping-acceptance.md` for final evidence and `docs/superpowers/handoffs/2026-09-28-metrolist-car-0.4.0-current-handoff.md` for the current continuation point.

## 0.3.0 — 2026-09-28

Status: AVD acceptance complete; MetroList Car 0.3.0 packaged, identity-verified, and installed successfully on the primary 2 GB AVD.

Product:
- permanent dark automotive shell and shared driver-safe track rows;
- redesigned Mini Player and user-approved Now Playing layout with elapsed/total time;
- artwork swipe navigation with guarded distance/velocity thresholds;
- green Like and red Dislike actions with persistent dislike history;
- Disliked review with Undo, export, and confirmed bulk deletion;
- unified progress feedback for legacy backup import and Car Library operations;
- cancellable legacy import with safe interrupted-state recovery through Resume/Discard;
- startup milestone instrumentation without speculative Media3/Room rewrites.

Acceptance:
- Car unit tests + assemble: PASS;
- Foss, GMS, and Izzy unit tests + APK assemblies: PASS in final isolated Zulu JDK runs;
- Windows desktop-core/export/app tests + distributable: PASS;
- 2 GB AVD final APK install and launch: PASS;
- viewport smoke matrix at 800x480, 1024x600, and 1280x720: PASS with zero UI bounds overflow;
- 2.3 GB / 594-track legacy backup flow: user-confirmed PASS; the successful backup test was not redundantly repeated during release closeout;
- cancellable import path: Car tests and focused code review PASS; manual backup-cancel was not re-run during final closeout at the user's request;
- startup warm target: PASS, 3 ms median in the recorded stabilized batch;
- startup cold target: not consistently met on debug AVD; recorded median 2045 ms;
- final APK: 51,708,050 bytes, SHA-256 `cde5789d1d2684d3e20cefd0d2d48fb00cae6fef545f0cd90c5297e84a4682e7`, versionName 0.3.0, versionCode 3000.

See `docs/superpowers/reviews/2026-09-28-metrolist-car-0.3.0-acceptance.md` for exact evidence and limitations.


## 0.2.0 — 2026-09-27

Status: automated pre-physical review complete; packaged and ready for real head-unit acceptance.

Pente-fino changes:
- isolated the Car APK from phone launchers, deep links, widgets, recognition, alarms, and phone-only entry points;
- Car notifications return to CarMainActivity instead of the phone UI;
- removed direct API-31-only foreground-service exception typing from the API-26+ shared/Car runtime path;
- SAF directory contents are indexed/cached instead of repeatedly scanning thousands of files;
- Car Library file sizes are checked so truncated copies are rejected before playback;
- large queue resolution runs off the UI thread;
- unified logical Music catalog deduplicates Car Library, Car-local downloads, and legacy imports by canonical song id;
- playback precedence is Car Library local first, then local/legacy download fallback;
- Search uses the unified offline catalog;
- legacy imported artwork resolves from the local artwork store offline;
- queue UI is scrollable, highlights the current item, and supports direct track selection;
- Library no longer duplicates legacy-import management from More;
- viewport scaling handles 800x480 correctly and Car typography is normalized to physical viewport pixels;
- diagnostics expose logical catalog/source counts and active Car Library producer/revision.

Verification:
- desktop-core, desktop-export, and desktop-app regression tests passed;
- Car unit tests: 220 passed, 0 failures/errors;
- Foss/GMS/Izzy regression tests: 153 passed each, 0 failures/errors;
- full Car lint/assemble gate completed before the final shared MainActivity compatibility patch; its remaining NewApi finding was the direct API-31 exception type in MainActivity;
- after that patch, Car compilation passed and targeted source verification confirms no direct API-31 exception type usage remains;
- Car 0.2.0 identity/version test and final package gate passed.

Physical validation:
- intentionally pending; no installation or head-unit storage mutation was performed.


## 0.1.0 — 2026-09-27

Status: automated gates passed; APK packaged and ready for physical head-unit acceptance.

Product:
- dedicated MetroList Car flavor with independent version 0.1.0;
- touch-first 1280x720-oriented shell and Now Playing experience;
- system/hardware volume remains outside the app UI;
- persistent direct Car Library folder support from head-unit storage;
- offline Library browsing, playlists, local search, queue playback, seek, and Now Playing;
- high-quality local artwork is preferred;
- local Car Library playback bypasses network/cache routing;
- legacy .metrolistbackup import remains available with resume/discard compatibility;
- Downloads shows legacy imported/downloaded content;
- More includes import status and safe device/input diagnostics.

Car Library interoperability:
- formatVersion 1;
- generated by MetroList Windows 0.3.0;
- index-first open/refresh without duplicating the audio payload into app-private storage;
- incremental Windows export reuses unchanged media/artwork and publishes library.json last.

Automated verification:
- standard Android regression gate passed for Foss, GMS, and Izzy tests plus APK assemblies: BUILD SUCCESSFUL, 205 tasks;
- full Car unit-test gate passed;
- first Car assemble attempt hit a stale D8 intermediate-output directory; only that build intermediate was reset;
- rerun :app:assembleCarDebug passed: BUILD SUCCESSFUL;
- APK metadata: applicationId com.metrolist.music.car.debug, versionName 0.1.0, versionCode 1000;
- APK artifact: app/build/outputs/apk/car/debug/app-car-debug.apk;
- APK SHA-256: 2496616ab801de135fcd81e687a47029faa75f6e1848df00272e0a419b4b3f9a;
- Windows-generated final smoke Car Library validated format v1, one song, one playlist, direct media file, and 1400x1400 local artwork.

Physical acceptance:
- not performed yet;
- no APK installation or head-unit storage mutation was performed during automated release work;
- next step requires explicit authorization to install/test on the real multimedia unit.

Convenience artifact:
- D:ESTE COMPUTADORDesktopMetroList-Car-0.1.0-TEST.apk