# MetroList Car 0.4.2 Current Handoff

Date: 2026-09-28

## Current source of truth

Branch: `main`

This handoff supersedes the 0.4.0 current handoff. The 0.4.0 file is retained as a historical release snapshot.

Current paired versions:
- MetroList Car: `0.4.2`
- MetroList Windows: `0.3.2`

Do not push or merge unless the user explicitly authorizes it.

## Approved cumulative state

The user manually tested and approved the Car behavior developed in the preceding conversations. Do not repeat destructive/import validation unless specifically requested.

0.4.1 interoperability work:
- direct Car Library folder remains the primary fast workflow;
- SAF/local lookup resolves locator + size efficiently;
- manifest source modification fingerprints support fast incremental Windows exports;
- Windows 0.3.2 streams copy + SHA work and reuses unchanged payloads;
- legacy `.metrolistbackup` remains a slower compatibility path.

## 0.4.2 product delta

Storage/reset:
- More includes a confirmed Delete All Music action;
- app-managed downloads/imports, caches, artwork, import provenance, and import state are cleared;
- the active Car Library is disconnected;
- files inside the external Car Library folder are explicitly left untouched.

Volume normalization:
- modes: Off, Low, Medium, High;
- target: -16 LUFS integrated;
- correction strength: 0%, 50%, 75%, 100%;
- positive gain is limited by measured true-peak headroom to a -1.0 dBTP ceiling;
- no compressor, limiter, AGC, EQ rewrite, or audio re-encoding is introduced;
- gain changes use the existing playback processor with a 15 ms click-safe ramp;
- a track without local analysis starts immediately and never receives unsafe positive fallback gain.

## Background loudness analysis

- one low-priority analyzer runs for the selected Car Library;
- completed results persist in a dedicated SQLite store;
- results are keyed by song id + SHA-256 fingerprint + analysis version;
- changed files are individually invalidated;
- current/near-queue tracks are prioritized;
- interrupted tracks remain pending and resume on a later app/car session;
- transient codec contention retries instead of poisoning the track as permanently failed;
- the UI exposes analyzed/total/pending progress without blocking playback.

Measurement:
- BS.1770-style K-weighting using DeMan filters;
- 400 ms blocks with 75% overlap and loudness gating;
- 49-tap polyphase FIR true-peak estimation;
- 4x oversampling below 96 kHz, 2x below 192 kHz;
- decoder PCM support: 16-bit, packed 24-bit, 32-bit, and float.

## Project organization

Local user/test data is preserved but excluded from Git status through `.git/info/exclude`:
- `musicas teste 2/`
- `test-artifacts/`

Production signing credentials remain ignored under `.superpowers/signing/`; the production keystore remains ignored at `app/keystore/release.keystore`.

JVM/R8 crash diagnostics are retained under the ignored `.superpowers/diagnostics/jvm-crashes/` directory rather than left in the repository root.

## Verification policy for this closeout

Per user request, do not repeat functional/device playback tests. The user will validate the final APK in the physical car.

Required packaging checks before handoff:
- release build completes;
- package identity is `com.metrolist.music.car`, versionName `0.4.2`, versionCode `4002`;
- production signer certificate matches the existing MetroList Car certificate;
- SHA-256 and size are recorded;
- `git diff --check` passes;
- exact APK is copied to a convenient local path for the user.

## Release packaging status — end of 2026-09-28

The 0.4.2 source is implemented, but no 0.4.2 APK was delivered yet. Do not claim otherwise.

What passed:
- `:app:compileCarDebugKotlin` passed after the loudness implementation.
- KSP/Kotlin/Hilt/resources all passed in the later release attempts.
- The functional source remained intact; no music or Car Library files were deleted or modified.

What failed on the current host:
- Temurin/Adoptium JDK 21.0.12 repeatedly crashed natively during R8/D8. Crash logs included C1/C2 compiler-thread failures.
- Interrupted/concurrent builds produced misleading D8 protobuf/nestmate and KSP missing-generated-file errors.
- A major root cause was more than one Gradle build touching the same `app/build` tree. This is now forbidden by the release playbook.

Important discovery:
- A separate Amazon Corretto 21 toolchain already exists at:
  `I:\codex\metrolist-windows\.superpowers\toolchains\corretto-21\jdk21.0.12_12`
- A single `CarQaRelease` build using Corretto progressed through KSP, Kotlin, Hilt, manifests, resources, and `transformCarQaReleaseClassesWithAsm` without the Temurin native crash before the user requested handoff.
- That build was intentionally stopped for a clean overnight handoff. It did not produce an APK yet.

Clean shutdown performed:
- active Gradle sessions were terminated;
- Gradle/Kotlin daemons were stopped;
- temporary `qaRelease` / debug-pipeline changes were removed;
- `app/build.gradle.kts` was restored from an external clean backup;
- `git diff -- app/build.gradle.kts` is empty;
- current Car version remains `0.4.2`.

## First action tomorrow

Read this handoff and `docs/superpowers/releases/car-release-playbook.md` before building.

Preferred urgent QA APK path:
1. Verify zero MetroList Gradle/Kotlin build processes.
2. Remove only generated `app/build`.
3. Make an external byte-for-byte backup of `app/build.gradle.kts`.
4. Temporarily set the existing `release` build type to:
   - `isMinifyEnabled = false`
   - `isShrinkResources = false`
   Keep `isDebuggable = false` and production release signing unchanged.
5. Use Corretto 21 as `JAVA_HOME`.
6. Run exactly one build:
   `:app:assembleCarRelease --no-daemon --no-build-cache --no-configuration-cache --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process -Pksp.incremental=false -Pandroid.useFullClasspathForDexingTransform=true`
7. Restore `app/build.gradle.kts` from the external backup outside the Gradle process, even if Java crashes.

If that succeeds, validate before delivery:
- applicationId `com.metrolist.music.car`
- versionName `0.4.2`
- versionCode `4002`
- non-debuggable manifest
- production signer certificate SHA-256:
  `1323debbc967612f305d55b8c7261d0b821b56ff02967ba18d07d1e403bd0e43`
- APK SHA-256 and byte size
- copy to an easy local path named `MetroList-Car-0.4.2.apk`

Do not install or run functional playback tests unless the user asks; the user wants to test the final APK in the physical car.

After the urgent QA APK is delivered and manually approved, the remaining packaging cleanup is to produce the normal R8/minified release using Corretto in a single isolated build.


## 2026-09-28 late-night packaging handoff

### Current state

- Source/product work for Car 0.4.2 is preserved in the current working tree.
- `:app:compileCarDebugKotlin` passed after the loudness work.
- No final APK was produced tonight.
- No MetroList Gradle/Java build process is left running.
- `app/build.gradle.kts` is restored to the repository version; no temporary `qaRelease` or no-minify override remains.
- User music/test folders were not deleted.
- Do not rerun device/functional tests unless requested. The user wants to test the APK in the car.

### Important packaging lessons

1. The installed Temurin 21.0.12.101 JVM is not trustworthy for this packaging workload on this host. It reproduced native JVM crashes in both C1 and C2 compiler threads while D8/R8 were active.
2. Do not keep retrying Temurin with different JIT flags.
3. A portable Amazon Corretto 21 was downloaded successfully and is the preferred JDK for the next packaging attempt:
   `I:\codex\metrolist-windows\.superpowers\toolchains\corretto-21\jdk21.0.12_12`
4. Never allow two Gradle builds against this working tree at once. Parallel attempts caused contradictory dex/cache/asset failures.
5. The release script now has a file lock + process preflight:
   `.superpowers/signing/build-car-release.ps1`
6. After any aborted/crashed packaging attempt, remove `app/build` before retrying.
7. Use `--no-build-cache --no-configuration-cache` after a crash.
8. For the non-minified QA release path, use `-Pandroid.useFullClasspathForDexingTransform=true` to avoid protobuf nestmate/classpath failures.
9. `android.enableDexingArtifactTransform=false` is obsolete in the current AGP. Do not retry it.
10. Remote Desktop Commander long-running process sessions appear to terminate around the ~5 minute mark. The final Corretto QA build reached `desugarCarQaReleaseFileDependencies` and then the remote process session ended at ~311 seconds with no Gradle success/failure and no APK. Treat this as a tooling-session lifetime issue, not an app/build error.

### Correct next approach

Do NOT run another long Gradle build directly inside one Desktop Commander `start_process` session.

Instead:
1. read this handoff and `docs/superpowers/releases/car-release-playbook.md`;
2. confirm no MetroList Gradle processes are active;
3. remove `app/build`;
4. use Corretto 21 portable;
5. start the Gradle build as a truly detached Windows process with stdout/stderr redirected to a log file under `.superpowers/diagnostics/`;
6. return immediately from the Remote Desktop call;
7. poll the detached PID and log occasionally;
8. only after the process exits inspect the APK.

For the fastest user-test artifact, use a temporary signed `qaRelease` build type:
- init from `release`;
- `isMinifyEnabled = false`;
- `isShrinkResources = false`;
- `isDebuggable = false`;
- production `release` signing config;
- restore `app/build.gradle.kts` in a guaranteed cleanup step after packaging.

The QA artifact must still be verified before delivery:
- package `com.metrolist.music.car`;
- versionName `0.4.2`;
- versionCode `4002`;
- expected production signer;
- SHA-256 + size recorded.

### Last Corretto attempt

The Corretto QA build successfully progressed through:
- Kotlin/Javac compilation;
- Hilt;
- manifests/resources;
- ASM transform;
- `dexBuilderCarQaRelease`;
- `mergeCarQaReleaseStartupProfile`;
- `desugarCarQaReleaseFileDependencies`.

It produced no JVM crash before the Remote Desktop process session ended. This is the strongest evidence that Corretto + detached execution is the correct continuation path.

### Start prompt for the next conversation

`Continue MetroList Car 0.4.2 from I:\codex\metrolist-windows on main. First read docs/superpowers/handoffs/2026-09-28-metrolist-car-0.4.2-current-handoff.md and docs/superpowers/releases/car-release-playbook.md. Invoke Superpowers. Do not rerun product/device tests. The source changes are already approved. The only goal is to produce and verify the signed Car 0.4.2 APK. Use the portable Amazon Corretto 21 toolchain documented in the handoff, launch the Gradle packaging as a detached process so Remote Desktop Commander cannot kill it at ~5 minutes, prevent concurrent Gradle builds, clean app/build first, and use the signed non-debuggable qaRelease/no-minify path if needed for the user's car test. Verify package/version/signer/SHA-256/size, restore any temporary Gradle edits, and copy the APK to an easy-to-find local path.`


## 2026-09-29 packaging completion

- The signed non-debuggable Car 0.4.2 car-test APK was produced successfully with the portable Amazon Corretto 21 toolchain.
- Packaging ran as one detached `:app:assembleCarRelease` process so Remote Desktop Commander session lifetime could not terminate Gradle.
- Temporary release minify/resource-shrink disabling was restored byte-for-byte after the build.
- `git diff -- app/build.gradle.kts` is empty and `git diff --check` passes (line-ending warnings only).
- Verified applicationId: `com.metrolist.music.car`.
- Verified versionName/versionCode: `0.4.2` / `4002`.
- Verified manifest: `debuggable=false`.
- Verified production signer SHA-256: `1323debbc967612f305d55b8c7261d0b821b56ff02967ba18d07d1e403bd0e43`.
- APK size: 42,493,787 bytes.
- APK SHA-256: `1e042a5e576285cdc6a6eec282c4c09da0f55ed18f97086235f4506a0ef0249f`.
- Convenience copy: `D:\ESTE COMPUTADOR\Desktop\MetroList-Car-0.4.2.apk`.
- No device/playback/import tests were rerun; physical-car validation remains with the user.
- After physical approval, the remaining packaging cleanup is the normal Corretto R8/minified release.


## 2026-09-29 final R8 release completion

MetroList Car 0.4.2 now has a production-signed final R8 release artifact.

Release pipeline:
- portable Amazon Corretto 21;
- one build invocation: `:app:testCarDebugUnitTest :app:assembleCarRelease`;
- `--no-daemon --no-parallel --max-workers=1 --no-build-cache --no-configuration-cache`;
- Kotlin compiler in-process and KSP incremental disabled;
- release keeps `isMinifyEnabled=true`, `isShrinkResources=true`, `isDebuggable=false`;
- shared global `-dontobfuscate` was removed; FOSS/GMS/Izzy preserve reproducible naming with `app/proguard-reproducible-rules.pro`; Car uses full R8 obfuscation.

Evidence:
- Gradle: `BUILD SUCCESSFUL in 8m 28s`;
- Car unit tests: 283 tests, 0 failures, 0 errors, 1 skipped;
- `minifyCarReleaseWithR8`: PASS;
- `convertShrunkResourcesToBinaryCarRelease`: PASS;
- effective Car R8 `configuration.txt`: zero `-dontobfuscate`;
- R8 mapping/config analyzer/usage/resource reports generated;
- applicationId `com.metrolist.music.car`;
- versionName `0.4.2`, versionCode `4002`;
- `debuggable=false`;
- production certificate SHA-256 `1323debbc967612f305d55b8c7261d0b821b56ff02967ba18d07d1e403bd0e43`;
- final APK: 23,192,051 bytes;
- final SHA-256: `6e52102691daf1dbd932fe1a1c9fcab027d9761c3ab2f57796b6cc413ccb6f39`;
- delivery: `D:\ESTE COMPUTADOR\Desktop\MetroList-Car-0.4.2.apk` and `MetroList-Car-0.4.2-FINAL.apk`;
- previous non-minified QA artifact retained as `MetroList-Car-0.4.2-QA-no-minify.apk`.

The connected ADB target was identified as a physical Xiaomi M2007J20CG/surya, not an AVD, so no install or launch was performed on it.

A separate optional post-build Gradle task-discovery attempt later crashed its own JVM. It did not run the release build, did not modify the final APK, left no active MetroList Gradle process, and the final APK SHA-256 was revalidated unchanged. The crash log was moved under `.superpowers/diagnostics/jvm-crashes/`.


## 2026-09-29 final R8 release completion

This section supersedes the earlier no-minify QA packaging notes above.

- Final Car 0.4.2 uses the normal `release` build type with `isMinifyEnabled = true`, `isShrinkResources = true`, `proguard-android-optimize.txt`, production signing, and `debuggable=false`.
- Current Android guidance was checked before packaging. The shared `-dontobfuscate` directive was moved to a flavor-scoped reproducibility file for FOSS/GMS/Izzy; Car now receives full R8 obfuscation.
- Effective Car R8 configuration contains no `-dontobfuscate`; `mapping.txt` confirms real renaming/removal of Car loudness classes.
- Release JDK/pipeline: portable Amazon Corretto 21, one detached Gradle build, `--no-parallel`, `--max-workers=1`, in-process Kotlin, KSP incremental disabled, build/config caches disabled.
- `:app:testCarDebugUnitTest :app:assembleCarRelease`: BUILD SUCCESSFUL in 8m28s.
- Car unit suite: 283 tests, 0 failures, 0 errors, 1 expected assumption skip because the optional `METROLIST_CAR_BACKUP_PATH` real-backup fixture was not supplied.
- R8 minification, optimized resource shrinking, signing, and packaging all completed.
- Verified package/version: `com.metrolist.music.car`, `0.4.2`, `4002`; manifest `debuggable=false`.
- Verified production signer SHA-256: `1323debbc967612f305d55b8c7261d0b821b56ff02967ba18d07d1e403bd0e43`.
- Final APK: 23,192,051 bytes; SHA-256 `6e52102691daf1dbd932fe1a1c9fcab027d9761c3ab2f57796b6cc413ccb6f39`.
- Exact final APK installed and cold-launched on `MetroList_Car_API29`: PASS, 708 ms launch, process remained alive, no fatal exception or app ANR in the post-launch log.
- Connected physical Xiaomi device was not modified.
- Final desktop copies: `D:\ESTE COMPUTADOR\Desktop\MetroList-Car-0.4.2.apk` and `D:\ESTE COMPUTADOR\Desktop\MetroList-Car-0.4.2-FINAL.apk`.
- R8 mapping/configuration/usage/seeds/resource reports plus the exact APK are archived under `.superpowers/release-artifacts/car-0.4.2-final-r8/` for future crash deobfuscation.
