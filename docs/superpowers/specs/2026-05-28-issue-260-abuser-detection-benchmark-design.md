# Issue #260 PostgreSQL Traversal Benchmark Design

## Context

Epic #260 compares GraphDB adoption value on PostgreSQL-backed variable-depth traversal workloads. Child issues #261 through #265 now cover shared traversal contracts, AGE/Cypher execution, PostgreSQL recursive CTE and iterative baselines, explicit ORM boundaries, and measured documentation.

The work stays inside `benchmark/graph-benchmark` and uses the existing Gradle `kotlinx-benchmark` surface. It does not add benchmark code to production graph modules.

## Goals

- Define deterministic traversal fixtures and metric contracts shared by all candidates.
- Make authorization inheritance the primary scenario: `user -> group -> role -> resource`, active edges, deny-overrides-allow semantics, public-resource filtering, and cycle-safe bounded traversal.
- Keep bounded fraud/abuser detection as a secondary scenario: time-windowed, risk-filtered, hop-limited money-flow traversal.
- Compare AGE/Cypher with PostgreSQL recursive CTE and iterative batched traversal where applicable.
- Report latency and correctness with committed JMH JSON, Markdown tables, and README chart assets.

## Non-Goals

- No production API changes.
- No standalone benchmark module unless `graph-benchmark` cannot host the slice.
- No production AGE managed `DataSource` ownership change.
- No speed-based GraphDB adoption claim unless measured results support it.

## Primary Workload: Authorization Inheritance

The fixture models access inheritance:

- `AuthzNode`: user, group, role, or resource.
- `AuthzEdge`: `MEMBER_OF`, `ASSIGNED_ROLE`, or `GRANTS`.
- Edge properties: active flag, grant effect, hop depth.
- Resource properties: public/private flag.

Scenarios:

| Scenario | Purpose |
|---|---|
| `shallow` | short user/group/role/resource inheritance paths |
| `deep-inheritance` | deeper inheritance chains with cycle edges |
| `deny-heavy` | many deny grant edges with deny-overrides-allow semantics |
| `wide-groups` | wider group membership fan-out |

Sizes:

| Size | Purpose |
|---|---|
| `smoke` | correctness and local confidence |
| `small` | quick benchmark comparison |
| `medium` | documented comparison run |
| `large` | local stress comparison |

## Secondary Workload: Bounded Fraud Detection

The fraud fixture avoids naive path explosion. It models account transfers with risk properties and detects suspicious upstream sources within bounded time windows and hop limits.

The comparison splits:

- AGE/Cypher set-based traversal.
- Exposed recursive CTE.
- Exposed iterative traversal.
- JPA recursive CTE.
- JPA iterative traversal.

## Benchmark Surface

Primary command:

```bash
./gradlew :graph-benchmark:authzInheritanceBenchmark --no-build-cache
```

Secondary command:

```bash
./gradlew :graph-benchmark:abuserDetectionBenchmark --no-build-cache
```

Smoke commands:

```bash
./gradlew :graph-benchmark:authzInheritanceSmokeBenchmark --no-build-cache
./gradlew :graph-benchmark:abuserDetectionSmokeBenchmark --no-build-cache
```

## Documentation

Benchmark documentation must include:

- scenario overview
- run command and run conditions
- correctness evidence
- latency table with metric direction
- raw JSON artifact path
- README chart PNG and SVG links
- explicit interpretation when AGE does not win latency

README changes must update both `README.md` and `README.ko.md`.

## Acceptance Criteria

- #261: shared fixture, result, metric, and engine contracts exist with tests.
- #262: AGE/Cypher traversal loads and resolves the smoke fixture.
- #263: PostgreSQL recursive CTE and iterative baselines are separate benchmark parameters.
- #264: ORM boundaries remain explicit; JPA is retained for bounded fraud and native SQL use is documented.
- #265: docs publish command, run conditions, comparison table, chart, and raw result evidence.
- `kotlinx-benchmark` is the documented execution surface.
- `git diff --check` passes.
- Targeted compile/tests pass or any environment blocker is recorded with evidence.

## Risks

- PostgreSQL AGE can express traversal more naturally without winning latency on the current fixture.
- Testcontainers runtime can be slow; smoke tests must remain small and serial.
- Recursive CTE and iterative traversal have different strengths by fan-out and depth; the report must not collapse them into one relational baseline.
