# MetroList Car Test Lab

Date: 2026-09-28

## Identity

IMPLEMENTATION_BASE_SHA: `2c0e160829e598d68f95038607a872f829d4ed5e`

AVD: `MetroList_Car_API29`

Android image: Android 10 / API 29 / Google APIs / x86_64.

The AVD is the primary iteration environment for MetroList Car. The physical head unit remains the final hardware acceptance target.

## Verified host acceleration

`emulator -accel-check` reports WHPX 10.0.26200 installed and usable.

## Primary acceptance profile

Current AVD configuration:
- landscape;
- 1024x600 default viewport;
- 160 dpi;
- 4 virtual CPU cores;
- `hw.ramSize=2G`;
- `disk.dataPartition.size=24G`;
- Android reports approximately 2 GB RAM;
- Android reports approximately 23 GB usable `/data` capacity.

The user explicitly chose 2 GB RAM as the primary performance and stability gate. This is intentionally stricter than the physical head unit's memory profile. A 6 GB run may be used only as a secondary parity comparison when diagnosing memory-sensitive behavior.

Do not wipe or delete the AVD.

## MetroList Car 0.2.0 baseline

Baseline APK:
`D:\ESTE COMPUTADOR\Desktop\MetroList-Car-0.2.0-FINAL-TEST.apk`

Package observed before parity-profile expansion:
- application ID: `com.metrolist.music.car.debug`;
- versionName: `0.2.0`;
- versionCode: `2000`.

Initial AVD before parity expansion:
- 1024x600;
- 160 dpi;
- approximately 2 GB RAM;
- approximately 10 GB `/data`.

The preliminary pre-expansion launch samples were not retained as the release baseline because resizing the AVD recreated its data partition and changed installed-app state.

Official reproducible 0.2.0 baseline after applying the 6 GB / 24 GB parity profile and reinstalling the exact baseline APK through `car-test-lab.ps1`:
- cold TotalTime: 2369 ms, 2132 ms, 2162 ms;
- cold median: **2162 ms**;
- hot TotalTime: 59 ms, 66 ms, 60 ms;
- hot median: **60 ms**.

These are emulator engineering baselines only. The physical head unit remains a separate performance target.

### 2 GB primary-profile baseline

After switching the same AVD to 2 GB RAM while retaining the 24 GB data partition:
- cold TotalTime: 2830 ms, 2329 ms, 2103 ms;
- cold median: **2329 ms**;
- hot TotalTime: 82 ms, 54 ms, 58 ms;
- hot median: **58 ms**.

Car 0.2.0 remained installable and launchable under this profile.

## Script

Run from the repository root:

```powershell
.\scripts\car\car-test-lab.ps1 start
.\scripts\car\car-test-lab.ps1 set-viewport -Viewport 1024x600
.\scripts\car\car-test-lab.ps1 install -Apk "D:\ESTE COMPUTADOR\Desktop\MetroList-Car-0.2.0-FINAL-TEST.apk"
.\scripts\car\car-test-lab.ps1 measure-startup
.\scripts\car\car-test-lab.ps1 screenshot -Output ".superpowers\car-1024x600.png"
```

Allowed viewport presets are exactly:
- `800x480`;
- `1024x600`;
- `1280x720`.

The script targets only `MetroList_Car_API29`. Its `start` action passes `-memory 2048` so the primary acceptance run is enforced at 2 GB even if the emulator rewrites AVD defaults. Guest `MemTotal` is the authority for the active profile. The script has no delete, wipe, format, or AVD-recreation action.

## Baseline test gate

Before Car 0.3.0 implementation:

```text
gradlew.bat --no-daemon --max-workers=1 :app:testCarDebugUnitTest
BUILD SUCCESSFUL in 3m 42s
62 actionable tasks: 32 executed, 30 up-to-date
```

Existing compiler/deprecation warnings are baseline warnings, not new failures.

## Car 0.3.0 startup instrumentation

Task 10 added explicit startup milestones using the stable log tag `MetroListCarStartup`:
- `ACTIVITY_CREATED`;
- `CONTENT_SET`;
- `SHELL_COMPOSED`;
- `PLAYER_CONNECTED`;
- `LIBRARY_REOPEN_STARTED`;
- `LIBRARY_READY`.

The shell does not await player connection or Car Library reopen. Both remain asynchronous.

A single diagnostic cold trace on the 2 GB / 1024x600 AVD recorded:
- process start: 16:07:50.049;
- `ACTIVITY_CREATED`: 16:07:50.377;
- `CONTENT_SET`: +15 ms from Activity creation;
- `SHELL_COMPOSED`: +205 ms;
- Android `Displayed`: **773 ms** total;
- `LIBRARY_REOPEN_STARTED`: +588 ms;
- `LIBRARY_READY`: +595 ms;
- `PLAYER_CONNECTED`: +778 ms.

A subsequent stabilized 3-run batch recorded:
- cold TotalTime: 2106 ms, 2041 ms, 2045 ms;
- cold median: **2045 ms**;
- hot TotalTime: 2 ms, 3 ms, 6 ms;
- hot median: **3 ms**.

In the slower cold samples, `SHELL_COMPOSED` still occurred around 636-640 ms, Car Library around 1.29-1.34 s, and player around 1.56-1.60 s. This means player connection and Car Library reopen are not blocking shell composition. The remaining variance is between composition and Android reporting the first displayed frame / process startup path.

Task 10 target status on this debug AVD:
- warm target <800 ms: **PASS**;
- cold target <=1500 ms: **NOT CONSISTENTLY MET**. One cold trace was 773 ms, but the 3-run median was 2045 ms.

No speculative Media3, Room, or playback-restoration rewrite was applied. The next performance pass should instrument first draw / pre-Activity process initialization before changing architecture.

## MetroList Car 0.3.0 final candidate

Final APK:
- `app/build/outputs/apk/car/debug/app-car-debug.apk`
- size: 51,708,050 bytes
- SHA-256: `cde5789d1d2684d3e20cefd0d2d48fb00cae6fef545f0cd90c5297e84a4682e7`
- application ID: `com.metrolist.music.car.debug`
- versionName: `0.3.0`
- versionCode: `3000`

Final AVD package verification:
- `adb install -r`: PASS;
- CarMainActivity launch: PASS;
- 800x480: 44 UI nodes, max bounds 800x432, 0 overflow;
- 1024x600: 44 UI nodes, max bounds 1024x552, 0 overflow;
- 1280x720: 44 UI nodes, max bounds 1280x672, 0 overflow;
- final AVD viewport restored to 1024x600.

Screenshots are retained under `.superpowers/final-0.3.0-*.png`.
See `docs/superpowers/reviews/2026-09-28-metrolist-car-0.3.0-acceptance.md` for complete release evidence and limitations.
