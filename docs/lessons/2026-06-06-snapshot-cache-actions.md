# Snapshot Cache Actions

## Context

Nightly already uses a one-day changing-module cache TTL, but the workflow still
forced dependency refreshes and disabled Gradle dependency caching for jobs.

## Decision

Remove `--refresh-dependencies` and remove Nightly `cache-disabled: true`.

## Outcome

Nightly keeps its existing graph backend task structure, but regular dependency
resolution can use Gradle cache metadata instead of forcing Central snapshot
metadata requests on every job.

## Verification

- `actionlint .github/workflows/*.yml`
- `rg -n -- '--refresh-dependencies|cache-disabled: true' .github/workflows` -> no matches
- `./gradlew help --no-daemon`
- `git diff --check`

## Future Guidance

Use explicit dependency refresh only in dedicated post-publish freshness checks.
Ordinary CI, Nightly, and Examples workflows should rely on cached changing-module
metadata plus targeted warm-up when a test-only SNAPSHOT dependency needs it.
