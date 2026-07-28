# Nightly Snapshot 갱신

## 맥락

Nightly restores Gradle caches and consumes mutable bluetape4k Central snapshot artifacts.
Stale snapshot metadata or simultaneous Central snapshot metadata requests can
make module jobs fail before tests execute.

## 결정

Pass `--refresh-dependencies` to Nightly Gradle invocations and stagger the
scheduled cron minute so snapshot metadata is rechecked without starting every
downstream repository at the same time.

## 결과

Nightly keeps cache reuse for build state, refreshes mutable metadata, and
reduces scheduled cross-repository Central snapshot contention.

## 검증

- `actionlint .github/workflows/nightly-tests.yml`
- `git diff --check`
