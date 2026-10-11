# MetroList Car Loudness Normalization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add non-blocking, resumable loudness normalization for mixed Car Library playback.

**Architecture:** Persist per-track loudness measurements in a dedicated SQLite store, analyze local Car Library media with one resumable background worker, and feed safe fixed gain into the existing Media3 normalization processor.

**Tech Stack:** Kotlin, Android MediaExtractor/MediaCodec, Media3, SQLiteOpenHelper, Compose/DataStore.

**Spec:** docs/superpowers/specs/2026-09-28-car-loudness-normalization-design.md

## Global Constraints

- Car flavor only for scanning/UI behavior.
- -16 LUFS reference; Low 50%, Medium 75%, High 100%.
- Peak ceiling -1.0 dBTP; no limiter/compressor/AGC/re-encode.
- Playback never waits for analysis.
- One analysis worker; completed work persists across shutdown.
- Preserve current uncommitted project changes.
- User explicitly requested to perform behavioral testing personally; no automated/device test cycle is required in this pass.

---
### Task 1: Loudness domain and persistence

**Files:** create playback/loudness store and calculator; modify preference keys.

- [ ] Define CarLoudnessMode and gain policy.
- [ ] Add SQLite-backed measurement store keyed by song id + fingerprint + analysis version.
- [ ] Add Car DataStore preference for Off/Low/Medium/High.

### Task 2: Local file analyzer and resumable coordinator

**Files:** create car/loudness analyzer and coordinator.

- [ ] Decode local content/file URIs without modifying media.
- [ ] Measure integrated K-weighted loudness using gated 400 ms blocks.
- [ ] Estimate inter-sample peak with oversampling and persist the measurement.
- [ ] Rebuild pending work from the active library, prioritize current/queued ids, and resume automatically.

### Task 3: Playback integration

**Files:** modify MusicService, VolumeNormalizationAudioProcessor, CarLibraryMediaResolver.

- [ ] Prefer the Car measurement when present; preserve existing fallback behavior.
- [ ] Cap positive gain by peak headroom.
- [ ] Keep gain constant per track and make gain transitions click-safe.
- [ ] Do not change gain merely because background analysis finishes mid-track.
### Task 4: Car controls and progress

**Files:** modify CarApp/CarMoreScreen; add car strings.

- [ ] Start one coordinator from the Car shell after the library is available.
- [ ] Feed current/queued media ids into priority ordering.
- [ ] Add Off/Low/Medium/High controls.
- [ ] Show analyzed/total/background status without blocking the user.

### Task 5: Static closeout

- [ ] Inspect the final diff for unintended cross-flavor changes.
- [ ] Run formatting/diff checks and a Car Kotlin compile only, not behavioral/device tests.
- [ ] Update the current handoff with the feature state and manual-test note.

## Review Focus

- Decoder format changes mid-stream must not corrupt channel/sample-rate accounting.
- Cancelling the process mid-analysis must leave the track pending, not falsely complete.
- Replacing a file with the same song id but different SHA must force re-analysis.
- A missing measurement must never delay or block playback.
- Positive gain must never exceed the stored peak-headroom allowance.

## Implementation status — 2026-09-28

Implemented:
- [x] CarLoudnessMode Off/Low/Medium/High with -16 LUFS reference and 0/50/75/100% correction.
- [x] Dedicated SQLite persistence keyed by song id, SHA-256 fingerprint and analysis version.
- [x] MediaCodec background analyzer with DeMan BS.1770 K-weighting, 400 ms / 75% overlap gating, and integrated LUFS.
- [x] 49-tap polyphase FIR true-peak estimator, 4x below 96 kHz and 2x below 192 kHz.
- [x] One low-priority, resumable worker with current/queued-track priority and transient codec retry.
- [x] Safe -1.0 dBTP positive-gain ceiling, no limiter/compressor/AGC/re-encoding.
- [x] Existing VolumeNormalizationAudioProcessor reused with a 15 ms gain ramp.
- [x] Car More screen controls and non-blocking analysis progress.
- [x] Car Library SHA-256 propagated into playback metadata so measurements invalidate per changed file.
- [x] Non-Car normalization behavior retained.

Verification:
- `gradlew.bat :app:compileCarDebugKotlin --no-daemon --max-workers=1 -Dkotlin.compiler.execution.strategy=in-process` -> BUILD SUCCESSFUL.
- `git diff --check` -> clean (line-ending warnings only from the pre-existing Windows working tree state).
- Behavioral/device playback testing intentionally left to the user per request.
