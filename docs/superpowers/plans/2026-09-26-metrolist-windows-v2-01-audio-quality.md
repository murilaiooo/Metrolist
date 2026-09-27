# MetroList Windows V2 — Audio Quality Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Windows app verifiably use the best source returned by the existing HIGH-quality resolver and expose the real codec/bitrate/sample-rate to the user without transcoding.

**Architecture:** Keep InnerTubeX as the only stream resolver. Add a small playback-format value object, propagate metadata from remote or completed-local sources into `PlayerState`, and render it through the existing player UI. Use the existing authenticated `streamProbe` for live verification rather than adding another downloader.

**Tech Stack:** Kotlin/JVM 21, InnerTubeX desktop, SQLite/JDBC, Compose Desktop, kotlin.test.

**Spec:** `docs/superpowers/specs/2026-09-26-metrolist-windows-v2-design.md`

## Global Constraints
- Preserve the working V1 download/export/player architecture.
- Continue requesting `AudioQuality.HIGH`; no MP3 conversion or bitrate inflation.
- Do not replace InnerTubeX, libmpv, or the media downloader.
- Do not bump application version.
- Do not reset/clean/push/merge; no commits without explicit human authorization.
- Keep Android behavior/schema unchanged.

## Review Focus
- Completed local download has format metadata missing: playback still works and quality UI shows no false value.
- Online resolution has nullable codec/sample rate: label remains truthful and readable.
- Switching from a high-bitrate track to a lower one updates the displayed format instead of retaining stale state.
- Playback-resolution failure clears format metadata rather than showing the previous song's quality.
- Values below 200 kbps are displayed exactly as returned, not rounded upward or labeled as 320 kbps.

---
### Task 1: Playback audio-format metadata

**Files:**
- Create: `desktop-core/src/main/kotlin/com/metrolist/desktop/player/PlaybackAudioFormat.kt`
- Modify: `desktop-core/src/main/kotlin/com/metrolist/desktop/player/DesktopPlayer.kt`
- Modify: `desktop-core/src/main/kotlin/com/metrolist/desktop/player/PlayerSourceSelector.kt`
- Modify: `desktop-core/src/main/kotlin/com/metrolist/desktop/player/MpvPlayer.kt`
- Test: `desktop-core/src/test/kotlin/com/metrolist/desktop/player/PlayerSourceSelectorTest.kt`
- Test: `desktop-core/src/test/kotlin/com/metrolist/desktop/player/MpvPlayerTest.kt`

**Interfaces:**
- Produces: `data class PlaybackAudioFormat(val itag: Int?, val mimeType: String?, val codecs: String?, val bitrate: Int?, val sampleRate: Int?)`
- Produces: `PlayerState.audioFormat: PlaybackAudioFormat?`
- `PlayerSource.Local` carries the completed `FormatSnapshot?`; `PlayerSource.Remote` continues carrying `ResolvedAudio`.

- [ ] **Step 1: Write failing source-selection tests**
Assert that a valid completed local record returns `PlayerSource.Local(path, format)`, while remote selection carries the resolved format fields.

- [ ] **Step 2: Run the focused tests and verify RED**
Run: `./gradlew.bat :desktop-core:test --tests "com.metrolist.desktop.player.PlayerSourceSelectorTest"`
Expected: FAIL because Local does not yet expose format metadata.

- [ ] **Step 3: Implement `PlaybackAudioFormat` conversions and source metadata**
Add conversion helpers from `FormatSnapshot` and `ResolvedAudio`; do not duplicate resolver logic.

- [ ] **Step 4: Write failing player-state tests**
Cover online format, local format, track switch, and failed load clearing stale `audioFormat`.

- [ ] **Step 5: Run `MpvPlayerTest` and verify RED, then implement minimal state plumbing**
Run before and after: `./gradlew.bat :desktop-core:test --tests "com.metrolist.desktop.player.MpvPlayerTest"`
Expected final result: PASS.

- [ ] **Step 6: Checkpoint**
Run both focused player tests together. Do not commit.
### Task 2: Truthful quality label in the UI

**Files:**
- Modify: `desktop-app/src/main/kotlin/com/metrolist/desktop/PlayerUi.kt`
- Test: `desktop-app/src/test/kotlin/com/metrolist/desktop/PlayerUiTest.kt`

**Interfaces:**
- Consumes: `PlayerState.audioFormat`
- Produces: `internal fun formatAudioQuality(format: PlaybackAudioFormat?): String?`

- [ ] **Step 1: Write failing label tests**
Pin examples such as `opus + 256000 + 48000 -> "Opus · 256 kbps · 48 kHz"`, AAC equivalents, partial metadata, and null.

- [ ] **Step 2: Verify RED**
Run: `./gradlew.bat :desktop-app:test --tests "com.metrolist.desktop.PlayerUiTest"`

- [ ] **Step 3: Implement the formatter and a compact player quality line**
Use real values only; omit unknown segments instead of inventing defaults.

- [ ] **Step 4: Verify GREEN**
Run the same focused test plus `:desktop-app:test`.

### Task 3: Live quality audit gate

**Files:**
- Modify only if necessary: `desktop-core/src/main/kotlin/com/metrolist/desktop/stream/StreamProbeMain.kt`
- Test: `desktop-core/src/test/kotlin/com/metrolist/desktop/stream/DesktopInnerTubeXPlayerTest.kt`

**Interfaces:**
- Existing `DesktopInnerTubeXPlayer.resolve(videoId)` remains unchanged for consumers.

- [ ] **Step 1: Strengthen the resolver contract test**
Assert the extractor receives `AudioQuality.HIGH` and that selected itag/mime/codecs/bitrate/sample-rate map unchanged into `ResolvedAudio`.

- [ ] **Step 2: Verify focused tests**
Run: `./gradlew.bat :desktop-core:test --tests "com.metrolist.desktop.stream.DesktopInnerTubeXPlayerTest"`

- [ ] **Step 3: Run authenticated `streamProbe` on representative tracks**
Use at least one currently downloaded track below 200 kbps and two ordinary tracks. Record the exact returned itag, codec, bitrate, sample rate, client, and profile. Do not print credentials or URLs.

- [ ] **Step 4: Decision gate**
If the live probe shows HIGH is being honored, do not alter resolver selection. If a demonstrably higher valid source is available but not selected, stop and invoke systematic-debugging before changing the resolver.

- [ ] **Step 5: Slice verification**
Run: `./gradlew.bat :desktop-core:test :desktop-app:test :desktop-app:createDistributable`
Expected: BUILD SUCCESSFUL. Hand the app-image to the user for a visual/quality check before Plan 02.
