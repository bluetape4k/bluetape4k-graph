## Context
Graph post-merge CI failed after tests passed because a Kover report step hit a
Central Portal snapshot metadata HTTP 403 for `bluetape4k-junit5`.

## Decision
Apply bounded retry and `--no-configuration-cache` to CI coverage report gates,
matching the successful Neo4j follow-up pattern.

## Outcome
Coverage generation now tolerates short Central metadata outages and avoids
configuration-cache serialization failures when snapshot classpaths are
unresolved.

## Verification
- `git diff --check`
- `actionlint .github/workflows/ci.yml .github/workflows/nightly-tests.yml`

## Future Guidance
If tests pass but coverage fails while resolving bluetape4k snapshots, treat the
coverage Gradle invocation as a snapshot-dependent gate and give it the same
retry/no-configuration-cache hardening as test gates.
