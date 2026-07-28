# 이슈 243 - Workflow permissions hardening

## 맥락

GitHub CodeQL Actions analysis reported `actions/missing-workflow-permissions`
alerts on CI, Nightly, Examples, Benchmark, Snapshot publish, and Release
workflows before the 0.4.2 release.

## 결정

Declare workflow-level `contents: read` as the default token permission and add
job-local permissions only where a job needs more than repository read access.
The CI path detection job keeps `pull-requests: read`, and the release creation
job keeps its existing `contents: write` override.

## 결과

The workflow token defaults are now explicit and least-privilege oriented without
changing build, test, benchmark, or publish commands.

## 검증

- `actionlint .github/workflows/ci.yml .github/workflows/nightly-tests.yml .github/workflows/examples.yml .github/workflows/release.yml .github/workflows/benchmark.yml .github/workflows/publish-snapshot.yml`
- `yq '.permissions'` confirms read-only defaults on each touched workflow.

## 향후 가드

For new or edited GitHub Actions workflows, add explicit top-level or job-level
`permissions:` in the first PR rather than waiting for CodeQL code-scanning
alerts.
