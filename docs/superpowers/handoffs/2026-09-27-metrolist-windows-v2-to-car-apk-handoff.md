# MetroList — Handoff Windows V2 → Car APK

Date: 2026-09-27

## Starting point
Continue from:
`I:\codex\metrolist-windows\.worktrees\windows-v1`

Branch:
`feature/windows-v1`

Current committed HEAD:
`8b5da69b0 fix(desktop): include HTTP runtime module`

Important: the V2 Windows work is intentionally still present as a large modified/untracked diff in this worktree. Do not reset, clean, discard, merge, push, or overwrite it.

## Windows V2 status
Windows V2 Plans 01–05 are complete and user-approved.
Final acceptance handoff:
`docs/superpowers/handoffs/2026-09-27-metrolist-windows-v2-final-handoff.md`

The user manually approved the final TEST build on 2026-09-27 with “tudo certo”.
The Windows V2 state is now the approved baseline and should not be redesigned unless a concrete regression is reproduced.

## Verified gates
Windows:
`./gradlew.bat :desktop-core:test :desktop-export:test :desktop-app:test :desktop-app:createDistributable`
Result: BUILD SUCCESSFUL.

Android:
`./gradlew.bat --no-daemon --max-workers=1 :app:testFossDebugUnitTest :app:testGmsDebugUnitTest :app:testIzzyDebugUnitTest :app:assembleFossDebug :app:assembleGmsDebug :app:assembleIzzyDebug`
Result: BUILD SUCCESSFUL, 205 actionable tasks.

`git diff --check` passed; only normal CRLF warnings were present.

No new MSI was installed. `packageVersion` remains `0.1.2`.
No commit, push, merge, reset, clean, or worktree removal was performed after final acceptance.

## Windows behavior that must be preserved
- Do not fake 320 kbps; audio quality must reflect the actual selected stream.
- Preserve the current stable playback/download/export architecture unless a reproduced regression proves a change is needed.
- Preserve `session.bin`, `offline.db`, `library.db`, downloaded media, and artwork.
- `library.db` must remain separate from `offline.db`.
- Local playlists remain local unless the user explicitly requests YouTube Music sync.
- Preserve the accepted Plan 03 desktop shell/player/touch behavior.
- Expanded Now Playing hides the mini-player.
- Clicking the artwork or the whole title/media area opens expanded Now Playing.
- Explore is accepted for V2; visual refinement can happen later and is not a prerequisite for the car phase.

## Backup / car contract
The existing `.metrolistbackup` format is the compatibility boundary for the future car app.
The Windows export/backup path is already working and user-confirmed.
`:desktop-export` maps to the `desktop-portable` source directory in `settings.gradle.kts`.

Do not change the backup format casually.
If the car app needs additional data, first inspect the current portable models/validator/import expectations and design a versioned, backward-compatible path.

## Next phase goal
Design and implement the future car-focused Android APK as a separate product surface, optimized for a landscape in-car display and offline playback/import from MetroList backup.

The next conversation should begin with discovery/design, not immediate coding:
1. Read this handoff and the final V2 handoff.
2. Inspect the current Android app architecture and any existing car/import code.
3. Inspect the portable backup models/validator and actual Android import path.
4. Establish the target car hardware/display constraints before changing UI architecture.

## Guardrails for the next conversation
- Invoke Superpowers before substantial implementation.
- Prefer a new spec + implementation plan for the car phase before coding.
- Keep Android green throughout.
- Do not modify the approved Windows V2 simply to make the car app easier.
- Do not delete user files or touch unrelated directories on the PC.
- Avoid broad refactors without a concrete need.
- Reproduce and root-cause failures before fixing them.
- Use TDD for new behavior and regressions.
- Do not install APKs/MSIs, push, merge, or commit unless explicitly authorized by the user.
- Keep Remote Desktop Commander operations careful and reasonably sized; avoid both giant fragile commands and excessive tiny calls.

## Suggested opening prompt for the next conversation

Continue the MetroList project from `I:\codex\metrolist-windows\.worktrees\windows-v1`. First read `docs/superpowers/handoffs/2026-09-27-metrolist-windows-v2-to-car-apk-handoff.md` and `docs/superpowers/handoffs/2026-09-27-metrolist-windows-v2-final-handoff.md`. Invoke Superpowers before acting. The Windows V2 is complete, user-approved, and must be preserved as the baseline. Do not reset/clean/discard the large intentional diff and do not push, merge, commit, install or version-bump without explicit authorization. Start the car APK phase with discovery and design: inspect the existing Android architecture, current backup/import path, portable backup models/validator, and any existing car-oriented code. Then write a focused design/spec and implementation plan for a landscape, touch-first, offline-first car experience that imports the existing `.metrolistbackup` format without regressing Android or Windows compatibility. Do not start implementation until the design/plan is coherent and grounded in the existing code.

## Final note
Option 3 from the branch-finishing step was chosen: keep `feature/windows-v1` and the worktree as-is for later handling.
