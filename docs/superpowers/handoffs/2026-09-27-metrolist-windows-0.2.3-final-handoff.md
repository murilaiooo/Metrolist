# MetroList — Windows 0.2.3 Final Handoff

Date: 2026-09-27

## Source of truth

Repository:
`I:\codex\metrolist-windows`

Hotfix worktree:
`I:\codex\metrolist-windows\.worktrees\windows-0.2.3-hotfix`

Hotfix branch:
`hotfix/windows-0.2.3`

Windows product version:
`0.2.3`

Version source:
`gradle.properties`
`metrolistWindowsVersion=0.2.3`

## User-reported failure

After Windows 0.2.2, clicking "Sign in with YouTube Music" immediately produced:
`Authentication helper failed with exit 1`.

The Google/YouTube login window did not open.

## Root cause

The packaged WebView2 control used its default user-data folder beside the Auth Helper executable.

Installed path:
`C:\Program Files\MetroListWindows\app\resources\auth-helper\Metrolist.AuthHelper.exe.WebView2`

Normal Windows users have no write permission there. WebView2 therefore could not create its profile/cache/session data and the helper exited before authentication could start.

## Fix

- Added `WebView2Profile`.
- WebView2 data now lives under `%LOCALAPPDATA%\MetroListWindows\WebView2`.
- Both interactive YouTube Music login and PO-token/BotGuard generation use this writable profile.
- Added a self-test guard requiring the profile to resolve under LocalAppData.
- Existing authentication/session persistence, download pipeline, PO-token fallback, playback, databases, and portable backup format were not redesigned.

## Verification

TDD RED:
- Auth Helper build failed before implementation because the new `WebView2Profile` contract did not exist.

Automated GREEN:
- Auth Helper Release build: succeeded.
- Auth Helper self-test: `SELF_TEST_OK`.
- Windows core/export/app tests: succeeded.
- Windows 0.2.3 distributable + MSI: `BUILD SUCCESSFUL`.
- Package verifier: `WINDOWS_PACKAGE_VERIFY_OK`.
- Packaged executable: `APP_VERSION=0.2.3`.
- Packaged Auth Helper opened a responsive YouTube Music login window.
- LocalAppData WebView2 profile was created successfully.

Manual:
- user installed `MetroListWindows-0.2.3-TEST.msi`;
- user confirmed sign-in works;
- the previous exit-1 blocker is no longer present in the approved manual login smoke;
- after approval, the user kept MetroList open and continued downloading music;
- do not close or restart that running app merely for additional verification.

## Compatibility baseline

Treat Windows 0.2.3 as the current Windows compatibility baseline after this hotfix is integrated.

Preserve:
- `.metrolistbackup` compatibility;
- `library.db` separate from `offline.db`;
- existing media/artwork/session persistence;
- Windows 0.2.2 stream-resolution and retry behavior;
- Car APK independent SemVer.

## Important continuation note

The hotfix was developed in an isolated worktree so the paused Car APK work on `main` was not disturbed.

Before future Windows release work, make sure the 0.2.3 hotfix commit and this final documentation are integrated into `main`.

Do not delete or modify the user's active downloads as part of repository cleanup.

## Suggested next prompt

Continue MetroList from `I:\codex\metrolist-windows`. First read `docs/superpowers/handoffs/2026-09-27-metrolist-windows-0.2.3-final-handoff.md`, the Car paused handoff, the versioning policy, and Windows release history. Invoke Superpowers before acting. Windows 0.2.3 has passed the manual installed-login smoke and is the current Windows baseline. Preserve the active user's download data and resume the Car APK work without regressing Windows authentication, PO-token generation, downloads, or portable backup compatibility.
