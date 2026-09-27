# MetroList Ecosystem Audit — Current Handoff

Date: 2026-09-27
Repository: `I:\codex\metrolist-windows`
Branch: `main`

## Purpose

This is the current resume point after a cross-product audit of MetroList Windows + MetroList Car.

Do not resume implementation from the older Task 6 pause handoff.

Primary audit:
`docs/superpowers/reviews/2026-09-27-metrolist-ecosystem-integration-audit.md`

Current Car design:
`docs/superpowers/specs/2026-09-27-metrolist-car-apk-design.md`

Original Car plan:
`docs/superpowers/plans/2026-09-27-metrolist-car-apk.md`

Direct-library planning draft:
`docs/superpowers/plans/2026-09-27-metrolist-car-library.md`

## Proven Windows baseline

Windows 0.2.2 remains the approved compatibility baseline.

Preserve:
- working authentication;
- download/resume architecture;
- truthful audio metadata;
- `offline.db` and `library.db` separation;
- local-playlist behavior;
- existing `.metrolistbackup` compatibility.
## Committed Car state

Current committed Car work reaches:
`ee8fc297b feat(car): import portable media and metadata`.

Committed functional areas:
- independent Car flavor/version;
- landscape Car launcher;
- scheduler isolation;
- playback restoration;
- physical-key diagnostics/router;
- car-first player shell;
- legacy backup validator;
- real Media3/Room/artwork legacy import targets.

The Car Downloads/Library/Search/More product screens are not complete; several are still placeholders.

## Working-tree state

There is uncommitted follow-on legacy-import Task 7 work.

Preserve it. Do not reset/clean/delete it.

The working tree also contains documentation changes from the ecosystem audit.

Before any future implementation:
- run `git status --short --branch`;
- inspect existing uncommitted importer files;
- do not assume the old pause handoff reflects HEAD.

## Architectural finding

Windows and Car are not yet fully governed as one ecosystem.

The legacy offline transfer path is well connected, but the following boundaries are incomplete:
- direct local Car Library in head-unit storage vs Room/Media3 downloaded state;
- unified Car catalog/search;
- authentication/account ownership;
- playlist ownership/edit semantics;
- favorites/likes/history ownership;
- duplicate/source precedence;
- Car-local downloads vs Windows-produced library;
- data-contract version compatibility;
- incremental deletion semantics;
- diagnostics for active library/source.
## Development gate

Do not continue product implementation merely from either existing plan.

The ecosystem contract spec now exists:
`docs/superpowers/specs/2026-09-27-metrolist-ecosystem-contract-design.md`

It is awaiting user review/approval. That approval is required before implementation planning resumes.

It defines:
- ownership classes for cross-product data;
- canonical media identity;
- unified Car source/catalog model;
- search behavior;
- playlist directionality;
- account/auth behavior;
- favorites/history/queue classification;
- Car Library schema/versioning;
- add/change/remove semantics;
- source fallback/precedence;
- diagnostics;
- golden contract tests.

After approval, reconcile both Car plans into an executable sequence using Superpowers writing-plans.

## Recommended ownership framework

Every new feature should be classified before coding as:
1. shared portable contract;
2. platform-local state;
3. online-account state;
4. derived/cache state.

This is the project-wide rule needed to make Windows and Car “think about each other” without forcing every piece of state to sync.

## Safety

- Do not push.
- Do not install an APK without explicit authorization.
- Do not package a new Windows release without explicit authorization.
- Do not delete/reset user files or the current uncommitted Car work.
- Do not rewrite the proven Windows 0.2.2 download pipeline for convenience.
- Keep legacy backup compatibility unless a versioned compatibility decision explicitly changes it.
