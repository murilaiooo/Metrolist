# MetroList Car 0.4.0 Housekeeping Acceptance

Date: 2026-09-28

## Scope

This review closes the approved post-0.3.0 Car UI changes and repository housekeeping without importing or mutating a legacy backup or Car Library.

Reviewed user-facing delta:
- compact rounded-square Car navigation controls;
- reactive seek bar with artwork-derived gradient and subtle audio-responsive glow;
- Car version bump to 0.4.0.

## Repository housekeeping

- Repository root JVM crash artifacts were moved to the ignored local diagnostics folder `.superpowers/diagnostics/jvm-crashes/`.
- 12 JVM crash diagnostics are retained there, including the native GC crash captured during the first production-release R8 attempt.
- No `hs_err_pid*.log` or `replay_pid*.log` remains in the repository root.
- The local user backup `Metrolist_Offline_Downloads_2026-09-27_06-58.metrolistbackup` remains present at 44,856,555 bytes with its existing timestamp.
- No backup or Car Library was imported during this acceptance.
- `docs/superpowers/releases/versioning-policy.md` now reflects Windows 0.3.x and Car 0.4.x as the current product lines.

## Reactive seek performance guard

A focused regression test was added for the audio meter lifecycle.

RED evidence:
- `AudioProcessorBufferReuseTest > disabling volume normalization signal tracking clears the exposed level` failed before the lifecycle fix.

GREEN behavior:
- signal tracking is explicitly enabled only while Now Playing is actively playing;
- reads of the current signal level are side-effect free;
- tracking is disabled on pause, connection/effect cancellation, or leaving the active effect;
- disabling tracking clears the exposed level;
- PCM peak tracking remains skipped entirely while tracking is disabled.

Focused regression test and subsequent Car gate passed.

## Automated gates

The debug-flavor Android regression gates used the verified Azul Zulu OpenJDK 21.0.12.1 runtime with one worker. The final production-release closeout used Eclipse Adoptium OpenJDK 21.0.12.101 with one worker after a native Zulu JVM crash during the first R8 attempt.

- `git diff --check`: PASS.
- Car: `:app:testCarDebugUnitTest :app:assembleCarDebug` — PASS, BUILD SUCCESSFUL.
- Foss: `:app:testFossDebugUnitTest :app:assembleFossDebug` — PASS, BUILD SUCCESSFUL.
- GMS: `:app:testGmsDebugUnitTest :app:assembleGmsDebug` — PASS, BUILD SUCCESSFUL.
- Izzy initial attempt: infrastructure failure, not a product test failure. The long-lived Gradle daemon crashed natively in the Zulu JVM GC thread with `EXCEPTION_ACCESS_VIOLATION` in `jvm.dll` before the product gate completed.
- Izzy isolated rerun: `:app:testIzzyDebugUnitTest :app:assembleIzzyDebug --no-daemon` — PASS, BUILD SUCCESSFUL.
- Windows: `:desktop-core:test :desktop-export:test :desktop-app:test :desktop-app:createDistributable --no-daemon` — PASS, BUILD SUCCESSFUL.
- Production Car release: `:app:testCarDebugUnitTest :app:assembleCarRelease` with release signing, R8/minify, and resource shrink — PASS, BUILD SUCCESSFUL.

The first production-release R8 attempt exposed a native Zulu JVM `EXCEPTION_ACCESS_VIOLATION` in a GC thread. The successful rerun used Eclipse Adoptium OpenJDK 21.0.12.101 in a single-use Gradle JVM with one worker and in-process Kotlin compilation. The native crash log is retained locally; it did not represent a product test failure.

## Final APK acceptance

Artifact:
- `app/build/outputs/apk/car/release/app-car-release.apk`
- production-signed, R8-minified, resource-shrunk, non-debug release
- size: 23,247,763 bytes
- SHA-256: `a9ac4c5a89d97a8cc4fc7229e47e4845e78cfdeae44d84880d75898069677b23`
- applicationId: `com.metrolist.music.car`
- versionName: `0.4.0`
- versionCode: `4000`
- APK Signature Scheme v2: PASS
- signer certificate SHA-256: `1323debbc967612f305d55b8c7261d0b821b56ff02967ba18d07d1e403bd0e43`

AVD:
- `MetroList_Car_API29`
- install with `adb install -r`: PASS
- launcher resolves to `com.metrolist.music.CarMainActivity`
- app process remains alive after launch: PASS

Viewport UI bounds:
- 800x480: 22 nodes, max bounds 800x432, 0 overflow
- 1024x600: 22 nodes, max bounds 1024x552, 0 overflow
- 1280x720: 22 nodes, max bounds 1280x672, 0 overflow

AVD restored to 1024x600 at 160 dpi.

## Review result

No unresolved Critical or Important product-code finding was identified in the 0.4.0 delta after the lifecycle fix and full regression gates.

The release compiler still reports pre-existing warning-level cleanup items in shared upstream-style code, including deprecated Compose `Slider` overloads and a few unnecessary safe calls. They do not fail compilation, R8, signing, installation, or runtime smoke and are outside the 0.4.0 Car delta.

The user explicitly authorized a local commit of the approved 0.4.0 state after this acceptance. Push/merge remains unauthorized. The production signing key and credentials are intentionally excluded from Git.
