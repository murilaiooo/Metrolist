# MetroList Car 0.3.0 Acceptance

Date: 2026-09-28

Implementation base: `2c0e160829e598d68f95038607a872f829d4ed5e`

## Release candidate

- APK: `app/build/outputs/apk/car/debug/app-car-debug.apk`
- size: 51,708,050 bytes
- SHA-256: `cde5789d1d2684d3e20cefd0d2d48fb00cae6fef545f0cd90c5297e84a4682e7`
- applicationId: `com.metrolist.music.car.debug`
- versionName: `0.3.0`
- versionCode: `3000`
- exact APK install on `MetroList_Car_API29`: PASS via `adb install -r`
- installed package identity: PASS
- final CarMainActivity launch: PASS

## Automated gates

All final gates were run serially with a verified temporary Azul Zulu OpenJDK 21.0.12.1 runtime after the host Temurin JVM showed native crashes during Android dex work.

- Car: `:app:testCarDebugUnitTest :app:assembleCarDebug` — PASS, BUILD SUCCESSFUL.
- Foss: `:app:testFossDebugUnitTest :app:assembleFossDebug` — PASS, BUILD SUCCESSFUL.
- GMS: `:app:testGmsDebugUnitTest :app:assembleGmsDebug` — PASS, BUILD SUCCESSFUL.
- Izzy: `:app:testIzzyDebugUnitTest :app:assembleIzzyDebug` — PASS, BUILD SUCCESSFUL.

- Windows: `:desktop-core:test :desktop-export:test :desktop-app:test :desktop-app:createDistributable` — PASS, BUILD SUCCESSFUL.
- `git diff --check`: PASS.
- cancellable legacy import commit `a821e67c1`: `git show --check` PASS.

Infrastructure note: earlier closing attempts were interrupted by Temurin `EXCEPTION_ACCESS_VIOLATION` crashes and concurrent Gradle processes. Clean isolated Zulu runs above are the final release evidence; no product-code failure remained.

## AVD and UI acceptance

Primary AVD: `MetroList_Car_API29`, Android 10 / API 29 / x86_64, 2 GB RAM.

The latest Now Playing composition was already explicitly user-approved before release closeout and was not redesigned afterward. The Disliked review workflow had also received manual AVD validation before this closeout.

Final viewport smoke checks on the exact 0.3.0 candidate:
- 800x480: PASS, 44 UI nodes, max bounds 800x432, 0 overflow nodes. Screenshot: `.superpowers/final-0.3.0-800x480.png`.
- 1024x600: PASS, 44 UI nodes, max bounds 1024x552, 0 overflow nodes. Screenshot: `.superpowers/final-0.3.0-1024x600.png`.
- 1280x720: PASS, 44 UI nodes, max bounds 1280x672, 0 overflow nodes. Screenshot: `.superpowers/final-0.3.0-1280x720.png`.
- AVD returned to 1024x600 after the matrix.

## Legacy backup and cancellation

The 2.3 GB / 594-track legacy backup acceptance was user-confirmed PASS. The user explicitly requested that the successful large-backup flow not be redundantly repeated during final closeout.

The new Cancel import feature is implemented as a real service/job cancellation rather than a UI-only action:
- the Import button is disabled while running and Cancel import is shown;
- cancellation propagates as `CancellationException` instead of a normal import failure;
- validation and payload copy check cancellation cooperatively during ZIP reads;

- repository state is refreshed from the persisted journal after cancellation;
- an interrupted persisted import continues to expose Resume/Discard;
- the command mapping is covered by Car unit tests and the full Car gate is green.

A fresh manual backup-cancel run was not repeated in this final closeout because the user explicitly asked to stop repeating backup tests.

## Startup

Recorded 2 GB / 1024x600 instrumentation:
- cold TotalTime batch: 2106 ms, 2041 ms, 2045 ms; median 2045 ms;
- hot TotalTime batch: 2 ms, 3 ms, 6 ms; median 3 ms;
- warm target <800 ms: PASS;
- cold target <=1500 ms: NOT CONSISTENTLY MET.

The milestone traces show shell composition before Car Library readiness and player connection, so there is no evidence that Library/Player initialization blocks initial composition. No speculative Media3/Room rewrite was made.

The exact post-install 0.3.0 launch used for final package verification reported TotalTime 2236 ms, consistent with the documented cold-start variance.

## Review result and remaining limitation

Fresh focused review of the release delta and cancellable-import path found no unresolved Critical or Important product-code findings. The approved player layout was not changed during closeout.

Remaining limitation: cold startup on the debug AVD is variable and does not consistently meet the 1.5 s target. Physical head-unit validation of the final 0.3.0 candidate remains the next hardware acceptance step.

Release decision: AVD-approved MetroList Car 0.3.0 candidate is ready for packaging/hand-off.
