# 2026-06-04 Issue 286 Nightly Gradle Cache

## 맥락

Nightly builds across bluetape4k repositories intermittently resolved managed dependencies as `group:artifact:.` on GitHub runners.

## 결정

Disable `gradle/actions/setup-gradle` cache restore/write for Nightly jobs so scheduled runs do not reuse stale dependency-management state.

## 결과

Every Nightly `setup-gradle` block now sets `cache-disabled: true` while keeping explicit Gradle dependency refresh.

## 검증

- Audited `.github/workflows/nightly-tests.yml`: setup-gradle blocks match cache-disabled blocks.
- Planned validation: `actionlint`, `git diff --check`.

## 향후 규칙

When a Nightly workflow uses snapshot or BOM-managed bluetape4k dependencies, keep Gradle action cache disabled unless a fresh CI proof shows cache restore cannot replay stale metadata.
