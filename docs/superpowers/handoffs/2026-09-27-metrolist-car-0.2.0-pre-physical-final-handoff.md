# MetroList Car 0.2.0 Pre-Physical Final Handoff

Date: 2026-09-27
Branch: main

MetroList Car 0.2.0 is the physical-test baseline after the full Windows/Car integration, performance, compatibility, touch, responsive-UI, and offline-cohesion audit.

Critical fixes completed:
- dedicated Car manifest isolated from phone launchers/features;
- notification navigation returns to CarMainActivity;
- API-26+ runtime path avoids direct API-31-only foreground-service exception typing;
- SAF media/artwork directory lookups are cached and size-aware;
- large queue resolution runs off the UI thread;
- unified logical catalog deduplicates sources and applies Car Library -> local/legacy fallback.

UX/offline fixes:
- Music is the single logical offline catalog;
- Search spans all offline sources;
- current track is visually highlighted;
- queue is scrollable and directly selectable;
- Library focuses on browse/configuration while legacy backup management stays in More;
- local legacy artwork is used offline;
- 800x480 and other landscape sizes use natural viewport scaling;
- typography is normalized against the physical viewport rather than trusting head-unit scaledDensity;
- More diagnostics show Car Library identity/revision and catalog source counts.

Windows 0.3.1:
- artwork cache requests native high-resolution CDN variants;
- existing cached low-resolution artwork can be upgraded without synthetic upscaling.

Automated acceptance:
- Car tests: 220 passed;
- Foss/GMS/Izzy: 153 passed each;
- Windows core/export/app regression: passed;
- Car compile/assemble: passed;
- direct API-31 exception type check: clean;
- final Car identity/package gate: passed.

Artifacts:
- Car: D:\ESTE COMPUTADOR\Desktop\MetroList-Car-0.2.0-FINAL-TEST.apk
- Windows: D:\ESTE COMPUTADOR\Desktop\MetroListWindows-0.3.1.msi
- Car SHA-256: 89538fa21e888373ffee4fa185d77f132d808d5ce407b58c860e2c6afdcb45d0
- Windows MSI SHA-256: 807f43acd593373c853f5b48f98f115799eb96afd2fcfaff9bac7d19b515546d

Stop boundary: do not install the APK or mutate the real multimedia unit until the user explicitly authorizes the physical test.