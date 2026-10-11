# MetroList Car Release Playbook

Updated: 2026-09-28

## Purpose

Make Car APK generation deterministic and avoid repeating failures already diagnosed on the current Windows host.

## Rules learned from the 2026-09-28 release incident

1. Never run more than one MetroList Android/Gradle build against the same working tree at the same time.
2. Before starting a release, confirm there are no stale gradle-wrapper / Gradle daemon descendants from a prior MetroList build.
3. After any JVM crash, aborted build, D8/R8 crash, or missing generated asset, do not reuse `app/build`. Delete the generated `app/build` tree before retrying.
4. Do not trust build/configuration cache after a JVM or dexing crash. Retry with `--no-build-cache --no-configuration-cache`.
5. This project can hit protobuf nestmate/classpath errors during non-minified dexing. Use `-Pandroid.useFullClasspathForDexingTransform=true` when that path is needed.
6. `android.enableDexingArtifactTransform=false` is removed in current AGP. Do not try it again.
7. The current Temurin 21.0.12 host JVM has reproduced native crashes while running R8/D8 (C1 and C2 compiler threads). If this recurs, switch toolchain or use `-Xint` for the build rather than cycling JIT flags repeatedly.
8. Do not open a second fallback build while the first build process is still alive. Verify process exit first.
9. Temporary release-build changes such as disabling minification require an external byte-for-byte backup of `app/build.gradle.kts` and an explicit restore after the build process exits. Do not rely only on try/finally inside the Gradle-launching process, because a JVM/native crash can bypass cleanup.
10. A release is not ready just because Gradle says BUILD SUCCESSFUL. Verify package id, versionName/versionCode, signing certificate, file size, and SHA-256 before delivery.
11. Keep the production signing key and environment unchanged. Car package remains `com.metrolist.music.car`.
12. User test folders and imported music are not release inputs. Keep them excluded from Git noise and never delete them as part of release cleanup.

## Fast preflight

- Confirm branch/worktree and intended version.
- Confirm signing env file exists.
- Confirm one or zero MetroList Gradle processes.
- Confirm `app/build.gradle.kts` has no temporary release overrides.
- If previous build crashed: remove `app/build` and disable build/config caches for the retry.
- Compile Car Kotlin before release packaging when code changed.
- For urgent user validation, a signed non-debuggable release without minification is an acceptable QA artifact if explicitly labeled as such. The project file must be restored after generating it.

## Delivery checklist

- APK exists.
- Package: `com.metrolist.music.car`.
- Version matches release history.
- Signed by the expected production certificate.
- SHA-256 recorded.
- Copy APK to an easy-to-find delivery location.
- Update release history/handoff with exact build mode and verification state.


## Remote execution rule learned 2026-09-28

When packaging through Remote Desktop Commander, do not keep Gradle attached to one long tool session. A Corretto build was cut off at about 311 seconds even though Gradle was still progressing and had not failed. Start Gradle as a detached Windows process, redirect output to a persistent log file, record the PID, and poll that PID/log in later tool calls. This prevents the remote tool session lifetime from terminating a valid long-running build.


## R8 release hardening — 2026-09-29

- The Car production release must use full R8 shrinking, optimization, and obfuscation together with resource shrinking.
- Do not place `-dontobfuscate` in the shared `app/proguard-rules.pro`. Reproducible non-Car variants that require stable names use the flavor-scoped `app/proguard-reproducible-rules.pro` instead.
- The effective Car R8 `configuration.txt` must contain zero `-dontobfuscate` directives before delivery.
- The normal Car release JDK on this host is the portable Amazon Corretto 21 toolchain documented above; do not revert the release script to the unstable Temurin host JDK.
- Run Car unit tests and the R8 release in the same single Gradle invocation when practical, with no parallel builds against the same tree.
- A successful R8 build already emits `configanalyzer.html`, `configuration.txt`, `mapping.txt`, `usage.txt`, and `resources.txt` under `app/build/outputs/mapping/carRelease`; inspect those outputs instead of starting redundant Gradle analysis jobs during release closeout.
