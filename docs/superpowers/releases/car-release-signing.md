# MetroList Car Release Signing

Effective: 2026-09-28

## Production identity

MetroList Car production APKs use a dedicated local release key, not the Android debug key.

- applicationId: `com.metrolist.music.car`
- keystore path: `app/keystore/release.keystore`
- alias: `metrolist-car`
- key algorithm: RSA 4096
- certificate DN: `CN=MetroList Car, OU=MetroList, O=MetroList, L=Tucson, ST=Arizona, C=US`
- certificate SHA-256: `1323debbc967612f305d55b8c7261d0b821b56ff02967ba18d07d1e403bd0e43`

## Secret handling

The keystore is ignored by Git. Passwords are local secrets and must never be committed or copied into documentation.

On the current development machine, release-signing credentials are stored under the ignored `.superpowers/signing/` directory and injected as `STORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` only for the release build.

A local backup is also retained outside the repository under `C:/Users/muril/.metrolist/signing/`. No secret values are written to Git or documentation.

Preserve the keystore and credentials securely. Future APK updates for the installed production app must be signed with this same key.

## Gradle behavior

`app/build.gradle.kts` attaches the `release` signing config only when the keystore exists and all three required signing environment variables are present. A normal checkout therefore does not silently fall back to the debug key.

## Verified 0.4.0 release

Artifact: `app/build/outputs/apk/car/release/app-car-release.apk`

- versionName: `0.4.0`
- versionCode: `4000`
- size: 23,247,763 bytes
- APK SHA-256: `a9ac4c5a89d97a8cc4fc7229e47e4845e78cfdeae44d84880d75898069677b23`
- APK Signature Scheme v2: verified
- signer certificate SHA-256: `1323debbc967612f305d55b8c7261d0b821b56ff02967ba18d07d1e403bd0e43`
- `android:debuggable`: not present in release badging

The exact signed APK was installed and launched on `MetroList_Car_API29`, then checked at 800x480, 1024x600, and 1280x720 with zero UI-bounds overflow. No backup or Car Library import was performed.

## Build-environment note

The Windows build host has shown native Zulu/HotSpot crashes inside ParallelGC during long Gradle/R8 workloads. The successful 0.4.0 release closeout reran the build in a single-use Gradle JVM using Eclipse Adoptium OpenJDK 21.0.12.101 with one worker and in-process Kotlin compilation. This is a local build-host workaround, not a runtime requirement of the APK.
