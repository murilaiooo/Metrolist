# MetroList Car 0.3.0 Release Handoff

Date: 2026-09-28

## Current state

Branch: `main`

Release commit:
- `56ebebd92 release: package MetroList Car 0.3.0`

Immediately preceding feature commit:
- `a821e67c1 feat(car): add cancellable legacy import`

MetroList Car 0.3.0 is AVD-approved, packaged, identity-verified, and installed successfully on `MetroList_Car_API29`.

Final APK:
- `app/build/outputs/apk/car/debug/app-car-debug.apk`
- size: 51,708,050 bytes
- SHA-256: `cde5789d1d2684d3e20cefd0d2d48fb00cae6fef545f0cd90c5297e84a4682e7`
- applicationId: `com.metrolist.music.car.debug`
- versionName: `0.3.0`
- versionCode: `3000`

Do not redesign the user-approved Now Playing layout.

## Final gates

Green final gates:
- Car unit tests + assemble;
- Foss unit tests + assemble;
- GMS unit tests + assemble;
- Izzy unit tests + assemble;
- Windows desktop-core/export/app tests + createDistributable;
- exact final APK `adb install -r` on the 2 GB AVD;
- final app launch;
- viewport smoke matrix at 800x480, 1024x600, and 1280x720 with zero UI bounds overflow.

Final AVD viewport was restored to 1024x600.

The host Temurin 21 JVM crashed natively during earlier Android dex attempts. Final gates were completed successfully with the verified temporary Azul Zulu OpenJDK 21.0.12.1 under `.superpowers/jdk-temp`. Do not interpret the earlier JVM crashes as product failures.

## Product acceptance

The latest Now Playing layout is explicitly user-approved and must remain unchanged unless the user asks for a redesign.

The Disliked workflow was manually validated on the AVD before release closeout.

The 2.3 GB / 594-track legacy backup flow is user-confirmed PASS. The user explicitly asked not to repeat successful backup tests during final closeout.

Cancel import is implemented as real cooperative cancellation:
- visible only while import is running;
- stops the import job;
- checks cancellation during ZIP validation and payload reads;
- refreshes persisted journal state;
- exposes Resume/Discard when an interrupted journal exists;
- prevents starting another import while one is active.

Startup instrumentation remains in place. Recorded stabilized medians:
- cold: 2045 ms;
- hot: 3 ms.

Cold <=1500 ms is not consistently met on the debug AVD. Do not reopen Media3/Room architecture without new evidence.

## Release records

Read these first in the next conversation:
- `docs/superpowers/reviews/2026-09-28-metrolist-car-0.3.0-acceptance.md`
- `docs/superpowers/testing/metrolist-car-test-lab.md`
- `docs/superpowers/releases/car-release-history.md`

No push or merge was performed.

## Next step

The next meaningful step is physical head-unit acceptance of this exact 0.3.0 APK. Do not repeat the completed AVD/backup regression matrix unless a real-car regression appears.

Suggested next prompt:

`Continue MetroList Car from main. Read docs/superpowers/handoffs/2026-09-28-metrolist-car-0.3.0-release-handoff.md first. MetroList Car 0.3.0 is packaged and AVD-approved. Do not redesign the approved player and do not repeat successful backup tests. Start with physical head-unit installation/acceptance of the exact recorded APK, then debug only concrete real-car regressions.`
