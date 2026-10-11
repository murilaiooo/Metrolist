# MetroList Car Loudness Normalization Design

Date: 2026-09-28

## Goal

Normalize perceived track-to-track loudness in the Car flavor without blocking playback, re-encoding media, compressing dynamics, or requiring the user to wait for a library scan.

## Audio policy

- Reference loudness: -16 LUFS integrated.
- Modes: Off, Low 50%, Medium 75%, High 100% of the correction toward -16 LUFS.
- Positive gain is constrained by measured peak headroom and a -1.0 dBTP ceiling.
- No compressor, limiter, AGC, EQ, or destructive file modification is introduced.
- Gain stays constant for the track. Gain changes use a short click-safe ramp.
- Track normalization is used because the primary use is a mixed 2,000-track playlist.

## Background analysis

- Car Library tracks are available immediately whether analyzed or not.
- One low-priority worker analyzes pending tracks while the app process is alive.
- Work is resumable across process/car shutdown because completed measurements are persisted.
- Current and queued tracks are prioritized ahead of the rest of the library.
- If a track finishes analysis while already playing, its new result applies only on a later playback.
- A changed SHA-256 invalidates only that track's previous measurement.

## Persistence

A dedicated SQLite database stores song id, audio fingerprint, integrated LUFS, oversampled peak estimate, analysis version, and timestamp. This avoids a Room migration and keeps the feature isolated from the main music database.

## Integration

The existing VolumeNormalizationAudioProcessor remains the only gain stage in the playback path. Car-specific gain selection first uses a persisted local measurement. If none exists, existing non-Car/online loudness metadata behavior remains available as a fallback.

The Car Library manifest contract is not changed in this version. The Android analyzer reads the final file that is actually played.

## UI

The More screen exposes Off, Low, Medium, and High as a compact four-option control and shows analyzed/total progress. No blocking dialog or mandatory scan screen is added.

## Resource policy

Analysis uses one worker. Playback and UI have priority. The worker yields between tracks and never wakes the device solely to analyze audio.

## Failure behavior

Unsupported or unreadable files are marked failed for the current fingerprint and skipped without affecting playback. A future analysis-version bump can retry them. Database or decoder failures must never stop music playback.
