# MetroList Windows V1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a focused Windows MetroList client that reuses MetroList YouTube/InnerTube behavior, downloads highest-quality audio for offline use, and exports every completed download as a car-compatible .metrolistbackup.

**Architecture:** Keep Android untouched. Add desktop-core, desktop-export, and desktop-app JVM modules. Reuse innertube/src/main/kotlin directly. Use innertubex-desktop for streams, official WebView2 via a tiny C# auth helper, SQLite for desktop state, libmpv for playback, and a separately tested backup writer/validator.

**Tech Stack:** Kotlin 2.4.20, JDK 21, Compose Multiplatform 1.12.1, InnerTubeX v0.7.0, Ktor 3.6.0, SQLite JDBC 3.53.4.0, JNA 5.19.1, libmpv, .NET 10, Microsoft.Web.WebView2 1.0.4191.47, Windows 11 x64.

**Spec:** docs/superpowers/specs/2026-09-26-metrolist-windows-v1-design.md

## Global constraints

- Never modify the MetroList app in the car.
- Preserve upstream app/ and innertube/ behavior; no Android DB schema or version changes.
- Reuse existing MetroList source before copying or rewriting it.
- No yt-dlp, no alternate YouTube downloader stack, no transcoding.
- Downloads always resolve with AudioQuality.HIGH.
- Reference backup is read-only and never committed.
- Backup profile stays format v1, source 13.6.0, DB 38, media3_simple_cache_spans.
- No final backup file exists until validation passes.
- Secrets, media, caches, generated backups, and natives stay out of Git.
- Run Android assembleFossDebug after repository/build changes.
- No push, merge, or PR unless the user explicitly requests it.

## Review Focus

1. 403/410/416 during resume must re-resolve the stream without corrupting audio.
2. Truncated files must never become COMPLETED or enter export.
3. Missing SAPISID/VISITOR_DATA must never persist a half-valid session.
4. Export and validation must use Long sizes and support Zip64 >4 GiB.
5. Missing artwork or optional metadata must not block valid audio export.

## Task 1: Safe Git bootstrap and desktop build baseline

Files:
- Modify settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml, .gitignore.
- Create desktop-core/build.gradle.kts, desktop-export/build.gradle.kts, desktop-app/build.gradle.kts.
- Create desktop-app/src/main/kotlin/com/metrolist/desktop/Main.kt.

Steps:
- [ ] Verify the sample backup and docs exist before Git operations.
- [ ] Initialize the existing I:\codex\metrolist-windows folder as Git without deleting/moving local files.
- [ ] Add upstream MetrolistGroup/Metrolist, fetch latest main, and create murilaiooo/Metrolist fork with gh if absent.
- [ ] Set origin to the fork; keep upstream separate.
- [ ] Ignore *.metrolistbackup, *.part, .worktrees, runtime media/artwork/secrets/native outputs before staging.
- [ ] Ensure JDK 21 exists; install Temurin 21 only if still absent.
- [ ] Add the three desktop Gradle modules and Compose Multiplatform 1.12.1.
- [ ] Verify ./gradlew :app:assembleFossDebug passes.
- [ ] Verify desktop modules compile/tests run.
- [ ] Local commit: chore(desktop): bootstrap Windows modules.

Deliverable: clean upstream baseline plus compiling empty Windows application, with the backup protected.

## Task 2: Reuse MetroList InnerTube source directly on JVM

Files:
- Modify desktop-core/build.gradle.kts.
- Create desktop-core/src/main/kotlin/timber/log/Timber.kt.
- Create desktop-core/src/test/kotlin/com/metrolist/desktop/core/InnerTubeSourceCompatibilityTest.kt.
- Create one minimal PlayerResponse JSON fixture.

Interfaces:
- desktop-core compiles rootProject/innertube/src/main/kotlin unchanged.
- Existing com.metrolist.innertube.YouTube/models/pages remain the API on Windows.

TDD:
- [ ] Write a failing test that imports YouTube and decodes PlayerResponse.
- [ ] Run the test and confirm missing desktop source wiring.
- [ ] Add the upstream source directory to desktop-core.
- [ ] Add only required Ktor/serialization/innertubex-desktop dependencies.
- [ ] Add a tiny java.util.logging-backed Timber shim for methods actually used.
- [ ] Re-run test; expected PASS.
- [ ] Re-run :app:assembleFossDebug; expected PASS.
- [ ] Local commit: feat(desktop): reuse MetroList InnerTube sources.

Deliverable: Windows compiles the same InnerTube parsing/request code as Android rather than a copied fork.

## Task 3: WebView2 login helper and protected session storage

Files:
- Create desktop-auth-helper/Metrolist.AuthHelper.csproj, Program.cs, AuthWindow.cs, AuthResult.cs.
- Create desktop-core auth classes: AuthSession, AuthHelperLauncher, DpapiSessionStore, AdvancedSessionParser.
- Create parser and DPAPI tests.

Interfaces:
- AuthSession(cookie, visitorData, dataSyncId, authUser, account display fields).
- AuthHelperLauncher.login(): AuthSession.
- DpapiSessionStore.save/load/clear.
- AdvancedSessionParser.parse(text): AuthSession.

TDD and checks:
- [ ] Test valid advanced token import, missing SAPISID, missing VISITOR_DATA, malformed data, DPAPI round-trip, and redacted errors.
- [ ] Implement DPAPI with JNA Platform Crypt32Util, current-user scope only.
- [ ] Build a net10.0-windows WinForms helper using stable Microsoft.Web.WebView2 1.0.4191.47.
- [ ] Use CoreWebView2.CookieManager.GetCookiesAsync so HttpOnly SAPISID is captured; do not rely on document.cookie.
- [ ] Execute page script for VISITOR_DATA, DATASYNC_ID, SESSION_INDEX.
- [ ] Emit one JSON result on stdout; all diagnostics go to stderr with secrets redacted.
- [ ] Add --self-test mode; expected output SELF_TEST_OK.
- [ ] Kotlin launcher validates returned session through existing YouTube.accountInfo/accountsList before saving.
- [ ] If multiple channels exist, persist only after the selected channel is finalized.
- [ ] Manual live login checkpoint on the intended account.
- [ ] Local commit: feat(desktop): add YouTube Music authentication.

Deliverable: MetroList-equivalent YouTube Music session data available to the JVM app without exposing credentials.

## Task 4: Desktop InnerTubeX HIGH-quality stream resolver

Files:
- Create DesktopInnerTubeXPlayer.kt, FilePlayerConfigRepository.kt, ResolvedAudio.kt.
- Create HighQualitySelectionTest.kt and DesktopInnerTubeXPlayerTest.kt.

Core signature:
- DesktopInnerTubeXPlayer.resolve(videoId, contentHints): ResolvedAudio.
- ResolvedAudio carries URL, headers, client, expiry/range rules, itag, MIME, codecs, bitrate, sample rate, length, loudness.

TDD and checks:
- [ ] Test InnerTubeX HIGH selection with WebM/Opus and M4A/AAC fixtures at different bitrates.
- [ ] Test mapping, TTL caching, invalidation, and failure-client tracking.
- [ ] Mirror Android InnerTubeXPlayer cipher/config/client flow, replacing SharedPreferences with a file-backed config store.
- [ ] Always pass InnerTubeX AudioQuality.HIGH; no ConnectivityManager/AUTO mode.
- [ ] Use desktop InnerTubeX capabilities first; do not invent PoToken code preemptively.
- [ ] Add an opt-in live test using METROLIST_LIVE_VIDEO_ID and the saved session.
- [ ] Live probe must return a playable URL plus format metadata.
- [ ] If live failure proves PoToken is mandatory, stop and implement only the narrowest compatible provider before continuing.
- [ ] Local commit: feat(desktop): resolve high quality YouTube audio.

Deliverable: one authenticated YouTube song can be resolved through the same MetroList/InnerTubeX family of logic at highest available quality.

## Task 5: SQLite offline store and one-song downloader

Files:
- Create DesktopPaths.kt, DesktopDatabase.kt.
- Create DownloadRecord.kt, DownloadStore.kt, ArtworkCache.kt, SongDownloader.kt.
- Create DownloadStoreTest.kt and SongDownloaderTest.kt.

Interfaces:
- DownloadState = QUEUED, DOWNLOADING, COMPLETED, FAILED.
- DownloadStore: upsert/get/listAll/listCompleted/remove.
- SongDownloader.download(song): DownloadRecord.
- ArtworkCache.fetch(url): Path?.

TDD:
- [ ] Temporary-DB tests: schema creation, update, restart persistence, completed filtering, stale DOWNLOADING recovery.
- [ ] JDK HttpServer tests: normal 200, 206 resume, ignored Range/200 restart, first 403 then re-resolve, content-length mismatch, missing artwork.
- [ ] Use minimal SQLite tables downloads, artists, song_artists; secrets never enter SQLite.
- [ ] Use JDK HttpClient; write videoId.part and atomically promote only after byte-count validation.
- [ ] On 403/410/416 invalidate/resolve a fresh stream and safely resume/restart.
- [ ] Artwork filename = SHA-256(thumbnail URL) + .img; artwork failure is non-fatal.
- [ ] COMPLETED is written only after the final audio file exists and validates.
- [ ] Live checkpoint: download one representative song at HIGH and verify itag/bitrate/length.
- [ ] Local commit: feat(desktop): persist offline song downloads.

Deliverable: a durable, restart-safe single-song offline download that contains the original selected YouTube audio bytes.

## Task 6: Offline Downloads backup writer, validator, and car gate

Files:
- Create BackupProfile.kt, BackupModels.kt, SpanPlanner.kt, OfflineBackupWriter.kt, OfflineBackupValidator.kt.
- Create ExportProbeMain.kt and focused export/validator tests.

Interfaces:
- BackupProfileV1 contains exact constants from the approved spec.
- planSpans(contentLength, chunkSize=5_242_880): List<SpanPlan>.
- OfflineBackupWriter.write(downloads, destination, now): Path.
- OfflineBackupValidator.validate(path): ValidationResult.

TDD:
- [ ] Test span boundaries including 5,847,658 bytes -> 0_5242880_0.span + 5242880_604778_1.span.
- [ ] Test WcHpojJz8Bc -> f9688284ac58d7e4 and artwork URL SHA-256 naming.
- [ ] Test exact V1 JSON fields; albums and song_album_map remain empty arrays.
- [ ] Test multi-span reconstruction, manifest/file hashes, missing entry, one-byte tamper, and truncated source exclusion.
- [ ] Implement a two-pass writer: precompute hashes/metadata, then write manifest first, metadata, spans, artwork to .part.
- [ ] Use ZipOutputStream level 0 and Long sizes; allow automatic Zip64.
- [ ] Reopen and fully validate before atomic rename to final .metrolistbackup.
- [ ] Probe the supplied 1.84 GB backup read-only; expect profile v1 and 410 songs/downloads.
- [ ] Generate a validated real backup containing only 1-3 Task 5 downloads.
- [ ] Run desktop-export tests; expected PASS.

HARD CAR GATE:
- [ ] STOP and give the 1-3 song backup to the user.
- [ ] User clears car offline library, imports it, disconnects internet, confirms every test track appears and plays with acceptable metadata/artwork.
- [ ] Do not begin Task 7 until the user explicitly confirms this gate passed.
- [ ] Local commit after gate: feat(desktop): export MetroList offline backups.

Deliverable: proven backup compatibility with the unchanged car app before the project grows.

## Task 7: Persistent queue and playlist download

Files:
- Create DownloadManager.kt, PlaylistDownload.kt.
- Create DownloadManagerTest.kt, PlaylistDownloadTest.kt.

Interface:
- DownloadManager.enqueue(song), enqueuePlaylist(playlistId), retry(videoId), remove(videoId).
- DownloadManager.downloads: StateFlow<List<DownloadRecord>>.
- Maximum active transfers = 3.

TDD and checks:
- [ ] Test max 3 concurrency, sibling isolation after failure, targeted retry, cancellation/removal, restart recovery.
- [ ] Test playlist dedupe by video ID and skip already-COMPLETED songs.
- [ ] Implement with coroutines + Semaphore(3); no background-job framework.
- [ ] Fetch playlists through existing MetroList YouTube.playlist and continuation helpers.
- [ ] Playlist membership is input only; export remains all completed offline songs.
- [ ] Queue a small live playlist, restart during transfer, confirm recovery completes it.
- [ ] Run desktop-core tests; expected PASS.
- [ ] Local commit: feat(desktop): add persistent playlist downloads.

Deliverable: reliable song/playlist downloads without introducing extra infrastructure.

## Task 8: libmpv desktop audio player

Files:
- Create DesktopPlayer.kt, MpvLibrary.kt, MpvPlayer.kt, PlayerState.kt.
- Create PlayerSourceSelectionTest.kt.
- Create scripts/mpv/fetch-windows-runtime.ps1 and runtime notes.

Interface:
- DesktopPlayer exposes state, play, pause/resume, seek, volume, next/previous, shuffle, repeat, close.
- Source rule: valid COMPLETED local file first; otherwise a freshly resolved online stream.

TDD and checks:
- [ ] Test local-first selection, corrupt/missing-local fallback, and offline error for network-only tracks.
- [ ] Map only libmpv client API needed for audio via JNA; no video rendering.
- [ ] Pin one x64 libmpv runtime asset and hard-code SHA-256 verification in the fetch script.
- [ ] Never download native binaries at app runtime.
- [ ] Observe duration, position, pause, idle/end, errors into PlayerState.
- [ ] Smoke test generated WAV, one downloaded WebM/Opus track, and M4A/AAC if available.
- [ ] Verify play/pause/seek/volume without conversion.
- [ ] Run player source tests; expected PASS.
- [ ] Local commit: feat(desktop): add offline and online audio playback.

Deliverable: focused audio playback that does not alter stream/download/export bytes.

## Task 9: Focused Compose Desktop UI

Files:
- Create App.kt, AppState.kt.
- Create HomeScreen.kt, SearchScreen.kt, LibraryScreen.kt, OnlinePlaylistScreen.kt.
- Create DownloadsScreen.kt, LoginScreen.kt, PlayerBar.kt, ExportDialog.kt.
- Create AppStateTest.kt.

UI scope:
- Primary destinations: Home, Search, Library, Downloads.
- Account: login/logout plus advanced session fallback.
- Song/playlist actions: play, download song, download playlist.
- Downloads: queued/downloading/completed/failed, progress, retry, remove, Export all offline downloads.

TDD and checks:
- [ ] Test navigation state, signed-out routing, download count, and export enabled only with COMPLETED items.
- [ ] Build the smallest Material 3 shell that follows MetroList information architecture.
- [ ] Wire login/logout to Task 3.
- [ ] Use shared MetroList YouTube models/pages for Home, search, library, albums/artists/playlists.
- [ ] Wire Task 7 download state/actions.
- [ ] Wire Task 8 player controls.
- [ ] Export always uses DownloadStore.listCompleted(), never current playlist membership.
- [ ] Manual flow: login -> Home -> search -> playlist -> play -> download -> restart -> offline play -> remove -> export.
- [ ] Run desktop-app tests and :app:assembleFossDebug; expected PASS.
- [ ] Local commit: feat(desktop): add focused MetroList Windows UI.

Deliverable: the small Windows app the user actually needs, not a full port of secondary MetroList features.

## Task 10: Packaging, recovery, large-backup probe, final verification

Files:
- Modify desktop-app/build.gradle.kts.
- Create StartupChecks.kt, StartupChecksTest.kt.
- Create scripts/verify-windows-package.ps1.

Checks:
- Startup report covers WebView2 Runtime, auth helper, writable app-data path, DB health, libmpv natives.
- Package Windows x64 MSI with bundled JVM, self-contained auth helper, and verified libmpv runtime.
- Target machine must not need global Java, .NET runtime, mpv PATH, or developer tools.

Steps:
- [ ] Test missing helper/native/unwritable data dir/healthy install.
- [ ] Configure Compose Desktop native distribution and include required runtime assets.
- [ ] Add package verification script; --startup-check must return nonzero on missing required component.
- [ ] Perform one explicit >4 GiB export probe, validate Zip64 with validator plus NanaZip/7z, then delete temp data.
- [ ] Full Gradle regression: clean, desktop-core:test, desktop-export:test, desktop-app:test, desktop-app:packageMsi, app:assembleFossDebug.
- [ ] Run auth-helper --self-test.
- [ ] Install packaged MSI and test complete user flow.
- [ ] Export a normal larger library and do final clean-car import spot check.
- [ ] Invoke superpowers:verification-before-completion before any success claim.
- [ ] Local commit: build(desktop): package MetroList for Windows.
- [ ] Do not push or merge unless the user asks.

Deliverable: self-contained Windows installer and verified car-compatible export flow.

## Execution discipline

- Use superpowers:using-git-worktrees before implementation starts.
- Use superpowers:test-driven-development before each non-trivial task.
- Work exactly one task at a time; do not begin the next while required checks are red.
- On test/build failure, use superpowers:systematic-debugging instead of repeated speculative patches.
- After every task, record only: files changed, checks run, result, next task.
- Never redo a completed task unless a later failing test proves it is the root cause.
- Keep the upstream Android app build green throughout.
- Task 6 car-import gate is the only planned human stop before final verification.
- If a new requirement would expand V1 scope, defer it rather than quietly adding it.


