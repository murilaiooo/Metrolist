# MetroList Car Release History

## 0.1.0 — 2026-09-27

Status: built and automated gates verified; awaiting physical head-unit validation.

Changes:
- car-first landscape shell with touch-sized controls and system-owned volume;
- direct Car Library v1 folder access from persistent head-unit storage;
- no audio duplication/import required for the preferred Car Library path;
- offline playlists, tracks, local search, queue playback, seek, next/previous, and high-quality local artwork;
- remembered Car Library folder with refresh/recovery states;
- legacy .metrolistbackup import remains supported with resume/discard status;
- Downloads surface for legacy imported/downloaded tracks;
- More/Diagnostics surface with app version, viewport, storage, import status, download counts, and physical input observations.

Automated verification:
- Car contract/library/playback/artwork/diagnostics tests passed;
- Car APK assembly passed after clearing one stale generated Gradle desugar-dex intermediate;
- standard Android Foss/GMS/Izzy tests and assemblies passed: BUILD SUCCESSFUL, 205 tasks;
- packaged identity: com.metrolist.music.car.debug;
- versionName: 0.1.0;
- launcher: com.metrolist.music.CarMainActivity;
- requested orientation: landscape;
- legacy MainActivityAlias is disabled;
- Windows 0.3.0 exporter acceptance fixture contains one direct media file, one playlist, and preserved 1400×1400 local artwork.

Artifact:
app/build/outputs/apk/car/debug/app-car-debug.apk
SHA-256: 2496616ab801de135fcd81e687a47029faa75f6e1848df00272e0a419b4b3f9a

Physical validation:
- not yet performed;
- do not install or modify the head unit until separately authorized.