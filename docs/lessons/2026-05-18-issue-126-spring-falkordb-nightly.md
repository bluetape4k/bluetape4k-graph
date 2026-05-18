# Issue 126 Spring Boot FalkorDB Nightly Coverage

## Context

Issue #126 asked whether `graph-spring-boot` covered the FalkorDB
auto-configuration path in nightly CI with a live FalkorDB Testcontainer.

## Decision

Keep the normal Spring Boot test job focused on the existing TinkerGraph smoke
path, and add a dedicated `scope=full` nightly job for the FalkorDB Spring Boot
auto-configuration path. Gate the new live-container `@SpringBootTest` behind an
environment variable so routine module tests compile it but do not start Docker.

## Outcome

Nightly now has `Test / Spring Boot FalkorDB (Testcontainers)`, which runs
`FalkorDBSpringBootIntegrationTest` against `FalkorDBServer.Launcher.falkordb`.
The workflow forces that filtered test to execute with `--rerun-tasks` so a
cached skipped result from routine Spring Boot tests cannot satisfy the
full-nightly gate. The test verifies the Boot context registers graph
operations, suspend and virtual-thread operations, and the FalkorDB health
indicator against a live container. `falkordbHealthIndicator` now uses a
bean-name missing-bean condition so other Actuator health indicators do not
block the backend-specific health indicator.

## Verification

- `BLUETAPE4K_GRAPH_SPRING_FALKORDB_INTEGRATION=true ./gradlew :bluetape4k-graph-spring-boot:test --tests "*.FalkorDBSpringBootIntegrationTest" --rerun-tasks --no-daemon --continue`
- `./gradlew :bluetape4k-graph-spring-boot:test --no-daemon --continue`
- `./gradlew detekt --no-daemon`
- `actionlint .github/workflows/nightly-tests.yml`
- `git diff --check`

IDE diagnostics could not run because the graph worktree is not open in the
IntelliJ MCP project list; Gradle compile/test and Detekt were used as fallback.

## Future Guidance

For expensive Spring Boot + Testcontainers coverage, prefer a gated test plus a
dedicated full-nightly job. Add `--rerun-tasks` to the dedicated job when the
same test class is normally compiled but skipped, because Gradle build cache can
otherwise reuse a skipped test result. This keeps smoke tests cheap while still
proving the real auto-configuration path before release.
For backend-specific health indicators, use name-based `@ConditionalOnMissingBean`
guards instead of a broad `HealthIndicator` type guard.
