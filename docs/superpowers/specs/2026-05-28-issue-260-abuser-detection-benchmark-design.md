# Issue #260 Abuser Detection Benchmark Design

## Context

Epic #260 compares GraphDB adoption value for PostgreSQL-backed abuser detection. Child issues #261 through #265 split the work into a shared workload contract, three PostgreSQL implementations, and a published comparison report.

This work stays inside `benchmark/graph-benchmark` and uses the existing Gradle `kotlinx-benchmark` surface. It does not add benchmark code to production graph modules.

## Goals

- Define one deterministic abuser-detection fixture and metric contract shared by all candidates.
- Compare:
  - PostgreSQL + Apache AGE + Exposed through `graph-age`.
  - PostgreSQL + Exposed JDBC relational baseline.
  - PostgreSQL + JPA/Hibernate relational baseline.
- Report latency and detection quality with the same truth labels.
- Keep runs reproducible through `kotlinx-benchmark` JSON output and committed documentation.

## Non-Goals

- No production API changes.
- No standalone benchmark module unless `graph-benchmark` cannot host the slice.
- No direct DataSource ownership change in production AGE integration.
- No DB-specific tuning beyond minimal indexes needed for a fair baseline.

## Workload Model

The fixture models accounts connected by abuse signals:

- `Account`: stable account id, segment, prior-known-abusive flag.
- `AbuseEdge`: directed relation between accounts with a signal kind.
- Signal kinds:
  - `SHARED_DEVICE`
  - `SHARED_IP`
  - `SHARED_PAYMENT`
  - `TRANSFER`
  - `REPORT`

The fixture is deterministic for each size:

| Size | Purpose | Account Count |
|---|---:|---:|
| `smoke` | local compile/test confidence | 120 |
| `small` | quick benchmark run | 1,000 |
| `medium` | documented comparison run | 10,000 |

Truth labels are generated during fixture construction. Detection candidates are any account reached from a known abusive account by strong shared-signal paths, suspicious transfer paths, or report-heavy neighborhoods.

## Detection Contract

Each implementation implements:

```kotlin
interface AbuserDetectionEngine {
    val implementationName: String
    fun reset()
    fun load(fixture: AbuserDetectionFixture)
    fun detect(): AbuserDetectionResult
    fun close()
}
```

`detect()` returns predicted account ids plus quality metrics:

- true positives
- false positives
- false negatives
- precision
- recall
- F1

All engines must be evaluated with the same fixture instance and the same metric calculator.

## Implementation Shape

### AGE + Exposed

- Use `PostgreSQLAgeServer.Launcher.postgresqlAge`.
- Use `HikariDataSource` only in benchmark-owned setup and close it in teardown.
- Use `Database.connect(dataSource)` to match existing AGE benchmark setup.
- Store account vertices and `ABUSE_LINK` edges with a `kind` property.
- Use `GraphOperations` batch creation and traversal/repository methods.

### Exposed JDBC

- Use the same PostgreSQL Testcontainer.
- Create relational tables for accounts, edges, and labels.
- Use Exposed schema creation, batch insert, and SQL queries.

### JPA/Hibernate

- Use the same PostgreSQL Testcontainer.
- Use programmatic Hibernate/JPA setup inside the benchmark module.
- Use entities for accounts, edges, and labels.
- Native SQL is allowed for detection queries when it keeps the relational baseline equivalent and measurable.

## Benchmark Surface

Add `AbuserDetectionBenchmark` with `kotlinx.benchmark` annotations:

- `@Benchmark`
- `@State(Scope.Benchmark)`
- `@Param`
- `@Setup`
- `@TearDown`

Add Gradle configurations:

- `abuserDetectionSmoke`: `sizeName=smoke`, one short warmup/iteration.
- `abuserDetection`: `sizeName=small,medium`, JSON report.

The primary command is:

```bash
./gradlew :graph-benchmark:abuserDetectionBenchmark
```

If the generated task name differs, documentation must name the actual Gradle task verified locally.

## Documentation

Update benchmark documentation with:

- scenario overview
- architecture/data-flow explanation
- run command and run conditions
- result table with metric direction
- raw JSON artifact path when results are committed

README changes must update both `README.md` and `README.ko.md` if those files exist in the benchmark module or repo section being changed.

## Acceptance Criteria

- #261: shared fixture, result, metric, and engine contract exist with unit tests.
- #262: AGE + Exposed engine loads and detects the smoke fixture.
- #263: Exposed JDBC engine loads and detects the smoke fixture.
- #264: JPA/Hibernate engine loads and detects the smoke fixture.
- #265: docs publish the scenario, run command, and comparison result/evidence.
- `kotlinx-benchmark` is the documented execution surface.
- `git diff --check` passes.
- Targeted compile/tests pass or any environment blocker is recorded with evidence.

## Risks

- Testcontainers runtime can be slow; smoke tests should remain small and serial.
- Hibernate dependency must come from the existing dependency-management/BOM path where possible.
- AGE traversal semantics must remain comparable with relational baselines; all quality metrics come from shared contract code.
