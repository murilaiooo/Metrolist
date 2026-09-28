# MetroList Car 0.1.0 Final Handoff

Date: 2026-09-27
Branch: main

## Release state

MetroList Windows 0.3.0 and MetroList Car 0.1.0 are packaged and automated gates are green. Physical head-unit acceptance is the only remaining release step.

Do not rewrite the Windows download/export architecture or the Car Library path without a concrete regression.

## Windows 0.3.0

The user-facing feature is direct Car Library export:
- exports a normal folder containing library.json, media/, and artwork/;
- preserves high-quality artwork sources;
- performs incremental copy/reuse;
- publishes the manifest last;
- keeps .metrolistbackup as a secondary compatibility export.

Verified package:
- desktop-app/build/compose/binaries/main/msi/MetroListWindows-0.3.0.msi
- package verifier: WINDOWS_PACKAGE_VERIFY_OK
- APP_VERSION=0.3.0
- convenience copy: D:ESTE COMPUTADORDesktopMetroListWindows-0.3.0.msi

## Car 0.1.0

Car Library is the preferred everyday path:
- user copies the exported folder to persistent internal storage on the Android head unit;
- user selects that folder once with Open Car Library Folder;
- persisted SAF access reopens it after restart;
- app reads/indexes library.json and plays the referenced files in place;
- no second import and no duplication of the audio GB into app-private storage;
- Refresh Library picks up a newly exported revision;
- unavailable individual tracks do not invalidate the rest of the library.

UI completed:
- Library management, playlists, tracks, refresh, unavailable/invalid states;
- direct local playback into existing MediaSession/PlayerConnection;
- high-quality local artwork preference;
- offline local Search;
- Downloads for legacy imported/downloaded tracks;
- More with legacy import status, resume/discard, device/storage/input diagnostics;
- no app volume control; hardware/system volume remains authoritative.

Verified APK:
- app/build/outputs/apk/car/debug/app-car-debug.apk
- applicationId: com.metrolist.music.car.debug
- versionName: 0.1.0
- versionCode: 1000
- SHA-256: 2496616ab801de135fcd81e687a47029faa75f6e1848df00272e0a419b4b3f9a
- convenience copy: D:ESTE COMPUTADORDesktopMetroList-Car-0.1.0-TEST.apk

## Automated gates

- Windows core/export/app tests: passed.
- Windows distributable + MSI: passed.
- Windows package verification: passed.
- Android Foss/GMS/Izzy tests + assemblies: BUILD SUCCESSFUL, 205 tasks.
- Car unit tests: passed.
- Car assemble: passed after resetting one stale app/build dex intermediate.
- Final Windows exporter smoke: format v1, producer MetroList Windows 0.3.0, one song, one playlist, direct media present, local artwork present at 1400x1400.

## Remaining physical acceptance

Stop here unless the user explicitly authorizes installation/testing on the real head unit.

After authorization:
1. install MetroList-Car-0.1.0-TEST.apk on the head unit;
2. copy a small MetroList Car Library folder into persistent internal storage;
3. select the folder in Library;
4. confirm fast catalog load;
5. play/seek/next/previous and verify queue;
6. verify 1400x1400-or-better artwork in Now Playing;
7. restart the app/head unit and verify the folder reopens;
8. replace/update the Windows export and verify Refresh Library;
9. verify one legacy .metrolistbackup import;
10. only then perform the large real-library acceptance with thousands of tracks.

Do not perform a second audio import for Car Library. The files should stay in the multimedia unit's normal persistent storage and be played in place.