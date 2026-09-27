# MetroList Product Versioning Policy

Effective: 2026-09-27

## Product tracks

MetroList Windows and the future MetroList Car APK use independent semantic versions.

- Windows current line: `0.2.x`
- Car APK: starts its own version line when the first car build exists.
- Do not reuse the upstream Android app's `13.x` version as the Windows or Car product version.

## Semantic versioning

Use `MAJOR.MINOR.PATCH`.

- PATCH: hotfixes, regressions, small UX corrections, packaging/reliability fixes.
  Example: `0.2.0 -> 0.2.1`.
- MINOR: meaningful user-facing features or a coherent feature batch.
  Example: `0.2.x -> 0.3.0`.
- MAJOR: intentionally incompatible product/data contract change or a stable 1.0 milestone.

Every build intentionally installed or handed to the user as a new test/release build must have a new version.
Pure documentation/test-only work that is not packaged for the user does not require a bump.

## Windows source of truth

The Windows version lives in exactly one tracked source:

`gradle.properties`
`metrolistWindowsVersion=X.Y.Z`

The desktop Gradle package version and the version displayed inside the Windows UI must derive from this value.
The package verifier must fail if the packaged executable reports a different version.

Visible location:
- left navigation header: `Windows · vX.Y.Z`
- login screen: `vX.Y.Z`
- command-line diagnostics: `MetroListWindows.exe --version`

## Car APK requirement

When the car application is implemented:
- define one tracked Car version source of truth;
- use it for APK `versionName`, in-app display, and diagnostic/error reports;
- show the version in a discreet persistent/About/Settings location reachable from the car UI;
- preserve the existing Android behavior of including version information in diagnostic/error reports where reused;
- never infer the Car version from the Windows version.

## Release discipline

For every shipped hotfix/feature:
1. Decide PATCH vs MINOR before packaging.
2. Bump the product version source once.
3. Add a release-history entry with version, date, summary, and verification.
4. Run the product's automated gates.
5. Package it.
6. Verify the package reports the expected version.
7. Install/deploy only that verified package.
8. Commit the version bump, code, tests, and release note together or in an immediately adjacent release commit.
