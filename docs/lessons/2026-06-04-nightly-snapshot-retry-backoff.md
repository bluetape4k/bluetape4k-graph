## 맥락

Nightly and CI matrix jobs failed intermittently while resolving upstream `1.11.0-SNAPSHOT` artifacts from Central snapshots. The local Central metadata checks returned HTTP 200, while GitHub-hosted runners intermittently received HTTP 403.

## 결정

Use the same retry posture across CI, Nightly, and example workflow Gradle steps: five attempts with a 30 second wait between attempts.

## 결과

The workflow now gives transient Central snapshot metadata failures more time to recover before marking module tests failed.

## 검증

- `git diff --check`
- `actionlint .github/workflows/*.yml`

## 향후 지침

When a downstream bluetape4k repo consumes unreleased upstream snapshots, stabilize upstream first, then rerun downstream Nightly after the upstream CI and Nightly gates are green.
