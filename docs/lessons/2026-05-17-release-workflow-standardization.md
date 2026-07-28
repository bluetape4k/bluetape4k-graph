# Release workflow 표준화

맥락: The Central Portal release campaign uses `bluetape4k-projects` as the
canonical release workflow shape.

결정: Rename the Nightly workflow file to `nightly-tests.yml` while keeping
the workflow display name as `Nightly`.

결과: Release preparation scripts can rely on the same workflow file names
across bluetape4k repositories.

검증: `actionlint .github/workflows/nightly-tests.yml .github/workflows/publish-snapshot.yml .github/workflows/release.yml`.

향후 가드: Keep release workflow file names aligned with `bluetape4k-projects`
unless a repo-specific exception is documented in `AGENTS.md`.
