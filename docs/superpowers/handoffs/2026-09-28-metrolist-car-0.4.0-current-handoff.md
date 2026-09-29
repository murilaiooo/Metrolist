# MetroList Car 0.4.0 Current Handoff

Date: 2026-09-28

## Current source of truth

Branch: `main`

The user explicitly authorized the MetroList Car 0.4.0 state to be committed locally on `main` after final verification. This handoff is part of that release state. Use `git log -1 --oneline` for the resulting commit identity.

Push and merge remain unauthorized.

This file supersedes earlier handoffs as the current continuation point. Older handoffs, including the 0.3.0 release handoff and 2026-09-27 ecosystem/task pause handoffs, remain useful historical snapshots but do not describe the latest working tree.

## 0.4.0 product delta

Approved UI changes:
- left navigation is now a compact custom automotive rail with rounded-square controls, refined gradient/material treatment, and clear active state;
- Now Playing uses the approved reactive seek bar with artwork-derived color palette, larger thumb, subtle glow, and audio-responsive energy.

Performance hardening added during housekeeping:
- the audio meter is opt-in;
- per-sample signal work runs only while Now Playing is actively playing;
- tracking shuts off and clears its level when paused or when the Compose effect is cancelled;
- a regression test pins the shutdown behavior.

No Car Library/backup contract, Room schema, Media3 playback contract, or Windows export contract was changed.

## Version and APK

Car source version:
- `metrolistCarVersion=0.4.0`

Final verified production APK:
- `app/build/outputs/apk/car/release/app-car-release.apk`
- convenient local copy: `C:/Users/muril/Desktop/MetroList-Car-0.4.0.apk`
- size: 23,247,763 bytes
- SHA-256: `a9ac4c5a89d97a8cc4fc7229e47e4845e78cfdeae44d84880d75898069677b23`
- applicationId: `com.metrolist.music.car`
- versionName: `0.4.0`
- versionCode: `4000`
- signer certificate SHA-256: `1323debbc967612f305d55b8c7261d0b821b56ff02967ba18d07d1e403bd0e43`
- R8/minify + resource shrink: enabled
- APK Signature Scheme v2: verified

## Verification state

Green:
- `git diff --check`;
- Car tests + assemble;
- Foss tests + assemble;
- GMS tests + assemble;
- Izzy tests + assemble in a fresh single-use Zulu JVM;
- Windows desktop-core/export/app tests + createDistributable;
- production-signed `:app:assembleCarRelease` with R8/minify and resource shrink;
- exact signed 0.4.0 release APK install and launch on `MetroList_Car_API29`;
- release UI bounds smoke at 800x480, 1024x600, 1280x720 with zero overflow.

Infrastructure note:
- native Zulu/HotSpot `EXCEPTION_ACCESS_VIOLATION` crashes were observed in GC threads during one Izzy gate and the first R8 release attempt; neither represented a product-code failure. The successful production closeout used Eclipse Adoptium OpenJDK 21.0.12.101 in a single-use Gradle JVM with one worker and in-process Kotlin compilation. Crash evidence is retained under `.superpowers/diagnostics/jvm-crashes/`.

## Housekeeping state

- root crash logs are cleared into the ignored diagnostics archive;
- local `.metrolistbackup` user data remains untouched;
- no backup or Car Library import was performed;
- versioning policy now reflects the current Windows 0.3.x / Car 0.4.x lines;
- the production release keystore is ignored by Git and backed up locally outside the repository under `C:/Users/muril/.metrolist/signing/`;
- the large local `musicas teste/` library is ignored rather than committed or deleted;
- release history, signing notes, and acceptance evidence are current.

## Read first next time

1. `docs/superpowers/handoffs/2026-09-28-metrolist-car-0.4.0-current-handoff.md`
2. `docs/superpowers/reviews/2026-09-28-metrolist-car-0.4.0-housekeeping-acceptance.md`
3. `docs/superpowers/releases/car-release-history.md`
4. `docs/superpowers/releases/versioning-policy.md`

## Source-control state

The user authorized one local commit of the approved 0.4.0 release state after fresh verification. The production keystore, signing credentials, generated APKs, local backup, and JVM diagnostics must remain excluded from the commit.

Do not push or merge unless the user explicitly authorizes that separate action.
