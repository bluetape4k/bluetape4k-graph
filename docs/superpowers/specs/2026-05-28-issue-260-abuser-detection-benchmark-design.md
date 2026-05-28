# Issue #260 PostgreSQL Traversal Benchmark Design

## Context

Epic #260 compares GraphDB adoption value on PostgreSQL-backed variable-depth traversal workloads. Child issues #261 through #265 now cover shared traversal contracts, AGE/Cypher execution, PostgreSQL recursive CTE and iterative baselines, explicit ORM boundaries, and measured documentation.

The work stays inside `benchmark/graph-benchmark` and uses the existing Gradle `kotlinx-benchmark` surface. It does not add benchmark code to production graph modules.

## Goals

- Define deterministic traversal fixtures and metric contracts shared by all candidates.
- Make authorization inheritance the primary scenario: `user -> group -> role -> resource`, active edges, deny-overrides-allow semantics, public-resource filtering, and cycle-safe bounded traversal.
- Include large-data, long-path adoption scenarios with 10-12 hop traversal so GraphDB adoption is not judged from shallow paths.
- Keep bounded fraud/abuser detection as a secondary scenario: time-windowed, risk-filtered, hop-limited money-flow traversal.
- Compare native Neo4j Cypher and AGE/Cypher with PostgreSQL recursive CTE and iterative batched traversal where applicable.
- Report latency and correctness with committed JMH JSON, Markdown tables, and README chart assets.

## Non-Goals

- No production API changes.
- No standalone benchmark module unless `graph-benchmark` cannot host the slice.
- No production AGE managed `DataSource` ownership change.
- No speed-based GraphDB adoption claim unless measured results support it.
- No TinkerGraph in this GraphDB adoption benchmark; existing in-memory TinkerGraph API/contract benchmark tracks remain separate.

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
| `long-chain` | forced target chain that requires 10-hop traversal |
| `deep-wide` | 12-hop traversal with wider fan-out and cycle edges |

Sizes:

| Size | Purpose |
|---|---|
| `smoke` | correctness and local confidence |
| `small` | quick benchmark comparison |
| `medium` | documented comparison run |
| `large` | local stress comparison |
| `xlarge` | manual stress comparison when local runtime allows it |

## Secondary Workload: Bounded Fraud Detection

The fraud fixture avoids naive path explosion. It models account transfers with risk properties and detects suspicious upstream sources within bounded time windows and hop limits.

The comparison splits:

- AGE/Cypher set-based traversal.
- Neo4j native Cypher traversal for the persistent GraphDB adoption decision surface.
- Exposed recursive CTE.
- Exposed iterative traversal.
- JPA recursive CTE.
- JPA iterative traversal.

## Benchmark Surface

Primary command:

```bash
./gradlew :graph-benchmark:authzInheritanceBenchmark --no-build-cache
./gradlew :graph-benchmark:authzInheritanceAdoptionBenchmark --no-build-cache
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
- explicit interpretation when native GraphDB wins only on long, selective path traversal

README changes must update both `README.md` and `README.ko.md`.

## Acceptance Criteria

- #261: shared fixture, result, metric, and engine contracts exist with tests.
- #262: AGE/Cypher traversal loads and resolves the smoke fixture.
- #263: PostgreSQL recursive CTE and iterative baselines are separate benchmark parameters.
- Native Neo4j Cypher is included in the large adoption decision benchmark.
- #264: ORM boundaries remain explicit; JPA is retained for bounded fraud and native SQL use is documented.
- #265: docs publish command, run conditions, comparison table, chart, and raw result evidence.
- `kotlinx-benchmark` is the documented execution surface.
- `git diff --check` passes.
- Targeted compile/tests pass or any environment blocker is recorded with evidence.

## Risks

- PostgreSQL AGE can express traversal more naturally without winning latency on the current fixture.
- A native GraphDB can win one path-shaped scenario and still lose a wider scenario; the report must identify the shape, not claim a blanket GraphDB win.
- Testcontainers runtime can be slow; smoke tests must remain small and serial.
- Recursive CTE and iterative traversal have different strengths by fan-out and depth; the report must not collapse them into one relational baseline.
