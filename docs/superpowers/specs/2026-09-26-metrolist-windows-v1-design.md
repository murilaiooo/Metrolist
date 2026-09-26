# MetroList Windows V1 Design

**Date:** 2026-09-26
**Status:** Design approved in conversation; written spec awaiting user review.
**Project path:** `I:\codex\metrolist-windows`
**Reference app:** `MetrolistGroup/Metrolist`
**Reference backup:** `Metrolist_Offline_Downloads_2026-07-30_20-50.metrolistbackup`

## 1. Goal

Build a private Windows fork/companion of MetroList that preserves the MetroList approach to YouTube Music instead of replacing it with a new downloader stack.

The V1 user flow is:
1. Sign in to YouTube Music.
2. Browse Home, search, library, albums, artists, and playlists.
3. Play music.
4. Download individual songs or complete playlists for offline use.
5. Always request the highest audio quality MetroList/InnerTubeX can resolve.
6. View and remove completed offline downloads.
7. Export every completed offline download, regardless of source playlist, as a MetroList Offline Downloads `.metrolistbackup`.
8. Copy that file to the car and import it with the existing MetroList installation.

The Android app in the car is a fixed compatibility target. It will not be modified.

## 2. V1 non-goals

V1 does not include lyrics, Last.fm, Discord RPC, Listen Together, Cast, Android Auto, widgets, alarms, sleep timer, Wrapped, AI translation, equalizer, advanced themes, podcasts, statistics, upload-to-YouTube, or advanced local playlist editing.

V1 does not use yt-dlp or the official YouTube Data API as the primary playback/download implementation.

V1 does not transcode downloaded audio to MP3 or another format.

V1 does not attempt to merge with an existing offline library in the car. The normal user workflow is to clear offline songs in the car before importing a new full backup.

## 3. Reference-first rule

For YouTube Music behavior, MetroList Android is the source of truth.

Port the existing request models, parsing logic, cookies/session semantics, account selection behavior, stream extraction behavior, retry/fallback behavior, and metadata rules wherever they are platform-neutral.

When Android-specific APIs prevent a direct port, create the smallest Windows adapter needed and keep the MetroList-facing interface equivalent.

Do not replace a working MetroList mechanism with a simpler third-party mechanism merely because it is easier on Windows.

Keep the upstream Android modules intact so their code remains available as a behavioral reference throughout development.

## 4. Repository architecture

The fork keeps the existing Android project and adds Windows-focused JVM modules.

Planned modules:
- `app/`: upstream Android application, kept intact except for unavoidable shared-build wiring.
- `innertube/`: upstream Android module, kept as reference.
- `desktop-core/`: JVM port of the minimum MetroList YouTube/domain logic needed by V1.
- `desktop-app/`: Compose Desktop UI, navigation, player controls, download UI, settings, and application lifecycle.
- `desktop-export/`: portable MetroList Offline Downloads backup writer and validator.

The Windows implementation must depend on `innertubex-desktop` rather than attempting to load Android libraries on JVM desktop.

The existing `innertube` module is Android-only. Required `YouTube`, `InnerTube`, model, page, and parsing code will be ported narrowly into `desktop-core`, preserving names and semantics where practical.

Cross-platform boundaries must be explicit interfaces so Windows-specific code does not leak into YouTube/domain logic.

## 5. Authentication

Reproduce the MetroList login semantics:
- open Google/YouTube Music sign-in in an embedded Windows browser surface;
- obtain cookies for `https://music.youtube.com`;
- require an authenticated cookie containing `SAPISID`;
- read `VISITOR_DATA`, `DATASYNC_ID`, and `SESSION_INDEX`/auth user from the page;
- call the same account-info/accounts-list logic used by MetroList to validate the session and support channel selection.

The Windows browser adapter is `AuthBrowser`. The first implementation targets Microsoft WebView2 because the target Windows 11 machine already has the WebView2 Runtime.

The browser implementation is isolated behind the interface so the rest of the app does not depend on WebView2 details.

Persist the resulting cookie, visitor data, data sync ID, auth user, account name, email, and channel handle locally.

Provide the MetroList-style advanced token/session import as a fallback path. This is important if Google changes or blocks embedded sign-in.

Credentials remain local to the user's Windows profile. Store session secrets encrypted with Windows DPAPI scoped to the current user; non-secret account display metadata may remain in SQLite. Do not log raw cookies, SAPISID values, auth headers, or session tokens.

## 6. YouTube and stream extraction

Use the MetroList/InnerTubeX request path, including client selection, cipher/deobfuscation, request headers, expiring stream URLs, client fallback, and PoToken support where the desktop stack can provide it.

Create a desktop equivalent of the Android `InnerTubeXPlayer` boundary rather than bypassing it.

Stream URLs are ephemeral and must never be treated as permanent download identifiers.

The stable media/cache key is the YouTube video ID, matching MetroList.

For V1 downloads, request `AudioQuality.HIGH` unconditionally.

InnerTubeX `HIGH` selects the highest-bitrate valid format, then uses channels, sample rate, and container as tie-breakers. Premium high-quality streams should therefore be used when the authenticated account and YouTube response make them available.

Save the original selected audio bytes. No transcoding or recompression.

## 7. Offline download subsystem

A completed download record stores at least:
- YouTube video ID;
- title, artists, album identity/name when present;
- duration and explicit flag;
- thumbnail URL and cached artwork path;
- selected itag, MIME type, codecs, bitrate, sample rate, content length, loudness values when available;
- download start/update/completion timestamps;
- local audio path, downloaded byte count, expected byte count, state, and last error.

Use the video ID as the stable download ID and cache key.

Playlist download expands the playlist into individual song downloads and preserves no playlist-specific requirement for export.

Limit concurrent download preparation/transfers initially to 3, matching MetroList's current download-manager behavior.

Incomplete, failed, cancelled, or size-mismatched downloads are not `Available offline` and must not enter an export.

Downloads should resume safely when the resolved stream supports it. If a stream URL expires or returns 403/410/416, invalidate the resolved stream and resolve a fresh one using the same MetroList/InnerTubeX path.

Write downloads to temporary files and atomically promote them after byte-count/integrity checks.

## 8. Desktop playback

Android Media3/ExoPlayer cannot be used as the Windows playback engine.

Keep playback transport behind a `DesktopPlayer` interface so this platform substitution does not alter YouTube resolution or download logic.

The V1 player must handle the formats returned by InnerTubeX, especially WebM/Opus and M4A/AAC, without transcoding.

Required controls: play/pause, previous, next, seek, volume, queue, shuffle, repeat, artwork, title, and artist.

Online playback and offline playback use the same metadata identity. If a complete local download exists, prefer it for playback.

Use libmpv as the concrete Windows playback engine, accessed through a narrow JNA-backed adapter and packaged with the app. The adapter must expose only the `DesktopPlayer` contract; libmpv must never participate in YouTube URL resolution, downloading, transcoding, or backup generation.

## 9. Offline backup compatibility profile

V1 targets the exact known-good Offline Downloads backup profile represented by the supplied file.

Fixed compatibility values:
- `appName = "Metrolist"`
- `backupFormatVersion = 1`
- `sourceAppVersion = "13.6.0"`
- `sourceDatabaseVersion = 38`
- `type = "offline_downloads"`
- `cacheStrategy = "media3_simple_cache_spans"`

These values remain a named compatibility profile rather than being derived from the Windows app version.

The archive is ZIP and must support Zip64 so exports larger than 4 GiB remain valid.

Match the reference archive's behavior: ZIP entries use Deflate with compression level 0. Audio is already compressed, and the known-good backup also stores JSON/artwork this way.

The archive contains:
- `manifest.json`
- `metadata/songs.json`
- `metadata/artists.json`
- `metadata/albums.json`
- `metadata/song_artist_map.json`
- `metadata/song_album_map.json`
- `metadata/formats.json`
- `metadata/downloads.json`
- `media3_cache/files/.../*.span`
- `artwork/*.img`

Metadata files use the reference wrapper objects such as `{"songs":[...]}`, `{"formats":[...]}`, `{"downloads":[...]}`, and `{"maps":[...]}`.

Reference-compatible song records contain `id`, `title`, `duration`, `thumbnailUrl`, `thumbnailArtworkPath`, `albumId`, `albumName`, `explicit`, `year`, `date`, `dateModified`, `dateDownload`, `isVideo`, and `isEpisode`.

Format records contain `id`, `itag`, `mimeType`, `codecs`, `bitrate`, `sampleRate`, `contentLength`, `loudnessDb`, and `perceptualLoudnessDb`.

Download records contain `requestId`, `uri`, `customCacheKey`, `title`, `contentLength`, `startTimeMs`, `updateTimeMs`, and `bytesDownloaded`.

Artist records use the verified subset `id`, `name`, `thumbnailUrl`, `channelId`, `lastUpdateTime`, and `isPodcastChannel`; `song_artist_map` records use `songId`, `artistId`, and `position`.

Because the known-good backup leaves `albums.json` and `song_album_map.json` empty even when songs carry album identity, compatibility profile v1 also emits those arrays empty and relies on each song's `albumId`/`albumName` fields. Do not invent an unverified album serialization in V1.

The manifest's `totalBackupBytes` remains null in compatibility profile v1, matching the supplied backup. `totalCacheBytes` and `totalArtworkBytes` are computed from exported content.

## 10. Cache-span encoding

Each exported song uses its YouTube video ID as `songId`, `cacheKey`, `requestId`, `uri`, and `customCacheKey`.

The portable backup directory for a cache key is:
`SHA-256(UTF-8(cacheKey)).hex().take(16)`

Example verified from the supplied backup:
- ID: `WcHpojJz8Bc`
- SHA-256 prefix: `f9688284ac58d7e4`
- directory: `media3_cache/files/f9688284ac58d7e4/`

Export audio as sequential spans with a normal target chunk size of 5 MiB (5,242,880 bytes).

Span filename:
`<position>_<length>_<index>.span`

Each manifest span records `relativePath`, `cacheKey`, `position`, `length`, and SHA-256 of the exact span bytes.

Each exported cache resource records `songId`, `cacheKey`, full `contentLength`, and its ordered spans.

## 11. Artwork encoding

Download/cache song artwork locally when available.

Artwork filename is the full lowercase SHA-256 of the thumbnail URL encoded as UTF-8, plus `.img`.

This naming rule was verified against the supplied backup.

A song's `thumbnailArtworkPath` points to the corresponding `artwork/<hash>.img` entry when artwork was successfully cached.

Missing artwork does not invalidate a song export. Keep the original `thumbnailUrl` and set the local artwork path to null.

Every exported artwork file has a manifest `files` record with relative path, byte size, SHA-256, and `kind = "artwork"`.

## 12. Export safety

Export only completed offline downloads whose local audio file still exists and whose size matches the stored content length.

Before writing, calculate required source bytes and verify that the destination volume has enough free space with a safety margin.

Write to a temporary `.metrolistbackup.part` file in the destination directory.

Generate every metadata/cache/artwork entry, hashes, file sizes, and manifest values deterministically from the local database and files.

After closing the ZIP, reopen it with the backup validator, verify required entries, JSON schemas, counts, every manifest SHA-256, span coverage, and archive readability, then atomically rename to the final `.metrolistbackup` name.

Cancellation or failure must remove the temporary export and leave existing backups untouched.

Default filename:
`Metrolist_Offline_Downloads_YYYY-MM-DD_HH-mm.metrolistbackup`

## 13. Compatibility gate before full UI

Backup compatibility is a first-class milestone, not an end-of-project test.

Before building the complete Windows UI, create a small exporter/validator harness capable of producing a backup from 1-3 known downloaded audio fixtures and metadata.

Compare its structure against the supplied known-good backup.

Then perform a manual import of that small generated backup into a clean MetroList installation in the car.

Success criteria:
- MetroList accepts the file without restore/import errors;
- all test songs appear in Offline/Downloads;
- each song plays in airplane/offline conditions;
- title, artist, duration, artwork, and format metadata are acceptable;
- no modification to the car app is required.

Do not scale the exporter or declare the format solved until this gate passes.

## 14. Windows UI scope

Primary navigation: Home, Search, Library, Downloads.

Account access exposes sign in/out and the advanced-session fallback.

Song/album/playlist screens expose play and download actions. Playlist screens expose download-all and remove-downloaded actions where relevant.

Downloads shows completed, active, queued, and failed jobs with progress and retry/remove controls.

The primary export action is labeled around the concept `Export all offline downloads` and always exports the entire completed offline library, independent of playlist membership.

Show an export summary before writing: song count, estimated audio/artwork size, and destination.

## 15. Local persistence and app data

Do not store runtime data inside the Git repository.

Use a versioned application-data directory under the user's Windows profile for:
- settings/session data;
- application database;
- downloaded audio;
- artwork;
- temporary downloads;
- logs with secrets redacted.

Database schema is Windows-owned and optimized for the V1 needs. It does not need to duplicate Room's Android database file because the car consumes the portable Offline Downloads backup, not the Windows database.

Migrations must be explicit from the first released schema onward.

## 16. Failure handling

Surface actionable errors for sign-in failure, account validation failure, unavailable/region-blocked songs, stream-resolution failure, expired URLs, network interruption, disk-full conditions, corrupted local files, and export validation failure.

A failure of one song in a playlist download must not silently mark the whole playlist complete. Track per-song status and provide retry failed.

A single missing artwork file must not block download or export.

No failure may produce a final backup filename unless the validator has passed.

## 17. Packaging

Target Windows 11 x64 first.

The installed application must include the required JVM/runtime so the user does not need a global Java installation.

The login component may rely on the installed WebView2 Runtime, with a clear startup diagnostic if the runtime is unavailable.

Package external/native playback components with the app or provide a deterministic first-run/runtime check. Do not require manual PATH configuration.

## 18. Testing strategy

Unit tests cover:
- session parsing and persistence with secrets redacted;
- MetroList model/page parsing ported to desktop;
- HIGH-quality format selection contract;
- stream retry/invalidation behavior;
- download state transitions and atomic completion;
- SHA-256 key/artwork naming;
- 5 MiB span splitting and exact reconstruction;
- all metadata JSON schemas;
- manifest counts/totals/hashes;
- Zip64-capable archive writing and level-0 Deflate;
- validator rejection of missing, truncated, or hash-mismatched entries.

Integration tests cover authenticated browse/search using opt-in live tests, local offline playback fixtures, playlist download orchestration, and export of a multi-span song.

The known-good 1.84 GB backup is a read-only local compatibility fixture. Tests may inspect it but must never modify it. It, generated backups, downloaded audio, artwork caches, credentials, and other runtime data must be excluded from Git.

## 19. Acceptance criteria for V1

V1 is successful when:
1. The user can sign into the intended YouTube Music account/channel and remain signed in across app restarts.
2. Home, search, library, albums/artists, and playlists needed for navigation load through the MetroList/InnerTube logic.
3. Streaming playback works for representative songs.
4. Individual and playlist downloads complete reliably at `AudioQuality.HIGH`.
5. Completed downloads play with networking disabled.
6. The Downloads screen accurately reflects local state and allows remove/retry.
7. Export includes every and only completed offline song.
8. The generated backup passes the local validator.
9. A generated backup substantially larger than 4 GiB is structurally valid as Zip64.
10. The clean MetroList app in the car imports the generated backup and plays all imported songs offline.
11. The car app requires no code or configuration changes.

## 20. Implementation order

The implementation must proceed in risk order:
1. Fork/bootstrap Windows build without changing Android behavior.
2. Port the minimum YouTube/domain layer and prove authenticated calls.
3. Build desktop stream extraction at `HIGH` quality.
4. Prove one-song download to stable local storage.
5. Build backup writer/validator and pass the car compatibility gate with 1-3 songs.
6. Add persistent download manager and playlist download orchestration.
7. Add desktop playback.
8. Build the focused Compose Desktop UI.
9. Run full-library export, large-archive, restart/recovery, and clean-install tests.
10. Package a self-contained Windows build.

## 21. Known risks and decisions

Google sign-in behavior in embedded Windows browsers can change. The advanced session import fallback is therefore part of V1, not a future enhancement.

PoToken generation is Android-WebView-specific in current MetroList glue code. Desktop extraction must use the desktop capabilities of InnerTubeX and add the narrowest compatible token provider only if live tests prove it necessary.

The Offline Downloads exporter/importer source that created the supplied file is not present in the current upstream main branch. The supplied known-good backup is therefore the compatibility contract, and the early car-import gate is mandatory.

The desktop playback engine is a platform adapter, not part of the YouTube/download protocol. Its selection must not alter downloaded bytes or backup output.

No implementation should begin until this written specification has been reviewed by the user, per the Superpowers architectural workflow.

