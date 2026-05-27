# Release changelog gate

## Context

The graph 0.4.2 release was published successfully, but `CHANGELOG.md` did not
contain a `0.4.2` section before the release tag was created. The GitHub Release
workflow therefore used fallback notes.

## Decision

Treat a current-version `CHANGELOG.md` section as a release preflight gate before
tagging or dispatching a stable release. If the section is missing, stop the
release flow and add it before creating the release tag.

## Outcome

The 0.4.2 changelog section and GitHub Release notes were backfilled after the
release. The tag was not moved because the published artifacts already use that
commit.

## Verification

- `CHANGELOG.md` now includes a `0.4.2` section.
- GitHub Release notes for `0.4.2` were updated to match the changelog summary.

## Future guard

Before any stable release tag push, verify:

- `CHANGELOG.md` has a section for the target version.
- The section includes the release date.
- The release notes summarize closed milestone issues and release workflow
  changes.
