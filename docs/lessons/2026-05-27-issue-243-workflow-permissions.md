# Issue 243 - Workflow permissions hardening

## Context

GitHub CodeQL Actions analysis reported `actions/missing-workflow-permissions`
alerts on CI, Nightly, Examples, Benchmark, Snapshot publish, and Release
workflows before the 0.4.2 release.

## Decision

Declare workflow-level `contents: read` as the default token permission and add
job-local permissions only where a job needs more than repository read access.
The CI path detection job keeps `pull-requests: read`, and the release creation
job keeps its existing `contents: write` override.

## Outcome

The workflow token defaults are now explicit and least-privilege oriented without
changing build, test, benchmark, or publish commands.

## Verification

- `actionlint .github/workflows/ci.yml .github/workflows/nightly-tests.yml .github/workflows/examples.yml .github/workflows/release.yml .github/workflows/benchmark.yml .github/workflows/publish-snapshot.yml`
- `yq '.permissions'` confirms read-only defaults on each touched workflow.

## Future guard

For new or edited GitHub Actions workflows, add explicit top-level or job-level
`permissions:` in the first PR rather than waiting for CodeQL code-scanning
alerts.
