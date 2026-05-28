# GraphDB Adoption Decision Report - 2026-05-28

## Executive Summary

GraphDB adoption is **not** justified by generic authorization, fraud, CRUD, short joins, or shallow traversal workloads.

The only positive adoption signal in this slice is a **long, selective, path-shaped traversal**:

| Workload | Decision |
|---|---|
| PostgreSQL AGE/Cypher | Not viable for this benchmark. Both large long-path scenarios timed out locally. |
| PostgreSQL recursive CTE | Strong baseline. Wins `deep-wide` and many small/medium scenarios. |
| PostgreSQL iterative traversal | Strong baseline. Wins several medium scenarios and remains predictable. |
| Neo4j Cypher | Viable candidate for long selective traversal. Wins `large + long-chain`. |
| Memgraph | Included as failed adoption evidence. Smoke parity passed, but local large load failed before reportable latency. |
| TinkerGraph | Excluded from this adoption decision only because it is in-memory. Existing in-memory benchmark tracks remain valid for API/contract work. |

Adoption recommendation:

> Do not adopt Apache AGE for the measured workload. If a persistent GraphDB is needed, continue with Neo4j-focused validation on long, selective traversal use cases. Keep PostgreSQL CTE/iterative traversal as the default relational baseline.

## Background

The original issue started from abuser/fraud detection:

- Compare AGE + Exposed vs Exposed vs JPA.
- Split Exposed/JPA relational baselines into recursive CTE and iterative traversal.
- Measure both latency and detection correctness.

During scenario review, the scope shifted because fraud detection can easily become a poor benchmark if it asks for all paths or unbounded traversal. The stronger adoption question became:

- Does GraphDB help when relationship depth is variable?
- Does it help when path shape itself is the query?
- Does the result change when data size and traversal length grow?

The primary scenario was therefore changed to authorization inheritance:

```text
user -> group -> group... -> role -> resource
```

with active edges, deny-overrides-allow, public-resource filtering, bounded traversal, and cycle-safe semantics.

## Work Completed

| Area | Completed Work |
|---|---|
| Shared contracts | Added deterministic authz fixture, result metrics, oracle, engine interface, and smoke parity tests. |
| PostgreSQL AGE | Added AGE/Cypher authz traversal and optimized the Cypher query shape to reduce obvious path explosion. |
| PostgreSQL baselines | Added separate recursive CTE and iterative traversal engines. |
| Native GraphDB baseline | Added Neo4j/Memgraph-compatible native Cypher engine and benchmark parameter. |
| Fraud benchmark | Tightened bounded fraud/abuser benchmark and kept CTE vs iterative relational split. |
| Long-path adoption surface | Added `long-chain` 10-hop and `deep-wide` 12-hop scenarios on `large` data. |
| TinkerGraph scope | Excluded TinkerGraph from the persistent adoption decision only; unrelated in-memory tracks remain intact. |
| Evidence | Committed raw JMH JSON, timeout/failure logs, README tables, and chart assets. |

## Benchmark Surface

Primary commands:

```bash
./gradlew :graph-benchmark:authzInheritanceSmokeBenchmark --no-build-cache
./gradlew :graph-benchmark:authzInheritanceBenchmark --no-build-cache
./gradlew :graph-benchmark:authzInheritanceAdoptionBenchmark --no-build-cache
```

Diagnostic JMH runs were also used to isolate backends after AGE/Memgraph blocked complete matrix execution.

## Scenarios

| Scenario | Size | Shape | Purpose |
|---|---:|---|---|
| `shallow` | small/medium | Short user/group/role/resource paths | Negative control for shallow traversal |
| `deep-inheritance` | small/medium | Deeper inheritance with cycles | Mid-depth variable traversal |
| `deny-heavy` | small/medium | Many deny grant edges | Correctness and deny-overrides-allow semantics |
| `wide-groups` | small/medium | Wider membership fan-out | Fan-out pressure |
| `long-chain` | large | Forced 10-hop target chain | Long selective traversal adoption probe |
| `deep-wide` | large | 12-hop traversal with wider fan-out/cycles | Long + wider traversal stress |

## Correctness

Smoke tests verified result-set parity and F1 `1.0` for the implemented engines before benchmark interpretation.

The correctness benchmark (`resolveF1BasisPoints`) resolves the same resources and converts F1 to basis points. It is a guard for correctness-metric overhead, not a separate ranking axis.

## Small/Medium PostgreSQL AGE Baseline

`resolveResources`, `ms/op`, lower is better:

| Scenario | Size | AGE/Cypher | PostgreSQL CTE | PostgreSQL iterative | Winner |
|---|---:|---:|---:|---:|---|
| `shallow` | `small` | 6.337 | **0.572** | 0.576 | PostgreSQL CTE |
| `shallow` | `medium` | 57.382 | 12.085 | **1.056** | PostgreSQL iterative |
| `deep-inheritance` | `small` | 93.848 | **0.726** | 1.449 | PostgreSQL CTE |
| `deep-inheritance` | `medium` | 604.833 | 9.385 | **2.102** | PostgreSQL iterative |
| `deny-heavy` | `small` | 67.670 | **0.993** | 1.803 | PostgreSQL CTE |
| `deny-heavy` | `medium` | 448.263 | 9.450 | **4.310** | PostgreSQL iterative |
| `wide-groups` | `small` | 29.445 | **0.551** | 1.801 | PostgreSQL CTE |
| `wide-groups` | `medium` | 250.083 | **1.521** | 3.658 | PostgreSQL CTE |

Conclusion:

- AGE/Cypher did not win any small/medium row.
- PostgreSQL recursive CTE and iterative traversal are both viable and should remain separate baselines.
- This result alone does not justify GraphDB adoption.

## Large Long-Path Adoption Probe

`resolveResources`, `large` fixture, `ms/op`, lower is better:

| Scenario | Neo4j Cypher | Memgraph Cypher | AGE/Cypher | PostgreSQL CTE | PostgreSQL iterative | Winner |
|---|---:|---:|---:|---:|---:|---|
| `long-chain` | **12.731** | load failure | timeout >75s | 55.364 | 47.568 | Neo4j Cypher |
| `deep-wide` | 56.467 | load failure | timeout >75s | **11.596** | 27.836 | PostgreSQL CTE |

![Authorization inheritance adoption latency](../images/readme-charts/authz-inheritance-adoption-latency-chart-01.png)

Interpretation:

- `long-chain` is the only measured positive GraphDB signal.
- Neo4j Cypher is 3.74x faster than PostgreSQL iterative and 4.35x faster than PostgreSQL recursive CTE on `large + long-chain`.
- `deep-wide` still favors PostgreSQL CTE, so GraphDB is not a blanket replacement.
- AGE/Cypher did not complete either `large + long-chain` or `large + deep-wide` within the 75-second local diagnostic timeout.
- Memgraph passed smoke parity, but the local large adoption diagnostic run terminated the Bolt connection during load, so it is included as failed adoption evidence rather than reportable latency.

## Why AGE Is Not Recommended

AGE looked attractive because it keeps PostgreSQL as the storage engine while adding Cypher syntax. The benchmark result does not support adopting it for this use case.

| Criterion | AGE Result |
|---|---|
| Expressiveness | Good. Cypher expresses variable-depth paths naturally. |
| Small/medium latency | Poor. Lost every measured row to PostgreSQL CTE or iterative traversal. |
| Large long-path latency | Not reportable. Timed out in both adoption scenarios. |
| Operational simplicity | Mixed. Avoids a second database, but adds AGE extension/query semantics and Exposed connection setup constraints. |
| Adoption verdict | Do not adopt for this benchmark. |

Practical conclusion:

> AGE gives Cypher syntax inside PostgreSQL, but this benchmark needs measurable traversal performance and predictable execution. PostgreSQL CTE/iterative traversal is safer than AGE, and Neo4j is the only measured persistent GraphDB candidate with a positive long-path signal.

## When GraphDB Still Makes Sense

GraphDB remains worth evaluating when all of these are true:

- Traversal depth is variable and frequently above fixed 2-3 joins.
- The path itself is the query result or the key filter.
- Queries are bounded, selective, and path-shaped.
- The workload cannot be reduced to simple joins, aggregate tables, or materialized projections.
- A native graph engine can be operated as production infrastructure.

Good candidates:

- Permission/organization/group inheritance with deep exception chains.
- Dependency or impact-radius analysis.
- Long selective recommendation paths with relationship-type filters.
- Network/topology reachability where path existence or shortest path matters.

Poor candidates:

- Simple ID lookup.
- 1-hop joins.
- Fixed 2-3 table joins.
- CRUD-heavy OLTP.
- Star-schema aggregation.
- Unbounded all-path search.

## Final Recommendation

| Decision | Recommendation |
|---|---|
| Default implementation | PostgreSQL recursive CTE or iterative traversal, selected per query shape. |
| AGE | Exclude from adoption candidates for this benchmark. |
| Neo4j | Continue as the persistent GraphDB candidate for long selective traversal. |
| Memgraph | Revisit only after resolving large fixture load stability. Current adoption evidence is a load failure, not latency. |
| TinkerGraph | Keep for in-memory API/contract benchmarks, not adoption evidence. |

Next benchmark direction:

1. Add a dependency/impact-radius workload, because it is naturally long-path and path-shaped.
2. Keep Neo4j vs PostgreSQL CTE/iterative as the primary decision table.
3. Treat AGE as excluded unless a new query shape or indexing strategy produces reportable latency.
4. Add larger data only after each backend can finish the current `large` probe reliably.

## Evidence Artifacts

| Artifact | Purpose |
|---|---|
| [2026-05-28-authz-inheritance-main.json](2026-05-28-authz-inheritance-main.json) | Small/medium AGE vs PostgreSQL raw JMH result |
| [2026-05-28-authz-inheritance-results.md](2026-05-28-authz-inheritance-results.md) | Small/medium result table and interpretation |
| [2026-05-28-authz-inheritance-adoption-neo4j.json](2026-05-28-authz-inheritance-adoption-neo4j.json) | Large Neo4j adoption probe |
| [2026-05-28-authz-inheritance-adoption-postgres.json](2026-05-28-authz-inheritance-adoption-postgres.json) | Large PostgreSQL CTE/iterative adoption probe |
| [2026-05-28-authz-inheritance-adoption-f1.json](2026-05-28-authz-inheritance-adoption-f1.json) | Correctness-metric benchmark probe |
| [2026-05-28-authz-inheritance-adoption-age-timeout.txt](2026-05-28-authz-inheritance-adoption-age-timeout.txt) | AGE `large + long-chain` timeout evidence |
| [2026-05-28-authz-inheritance-adoption-age-deep-wide-timeout.txt](2026-05-28-authz-inheritance-adoption-age-deep-wide-timeout.txt) | AGE `large + deep-wide` timeout evidence |
| [2026-05-28-authz-inheritance-adoption-memgraph-failure.txt](2026-05-28-authz-inheritance-adoption-memgraph-failure.txt) | Memgraph large load failure evidence |
| [authz-inheritance-adoption-latency-chart-01.png](../images/readme-charts/authz-inheritance-adoption-latency-chart-01.png) | Adoption chart PNG |
| [authz-inheritance-adoption-latency-chart-01.svg](../images/readme-charts/authz-inheritance-adoption-latency-chart-01.svg) | Adoption chart SVG source |

## DoD

| Item | Status | Evidence |
|---|---|---|
| Workload shift documented | Done | This report and issue #260 update |
| AGE included in comparison | Done | Timeout rows and timeout logs |
| TinkerGraph scope clarified | Done | Excluded only from persistent adoption decision |
| Native GraphDB candidate measured | Done | Neo4j adoption JSON and table |
| Memgraph adoption evidence included | Done | Smoke parity plus large load failure row and log |
| PostgreSQL baselines separated | Done | CTE and iterative rows remain distinct |
| Correctness captured | Done | Smoke parity tests and F1 benchmark artifact |
| Chart and raw evidence linked | Done | PNG/SVG and JSON/log artifacts |
| Recommendation stated | Done | AGE excluded, Neo4j retained for long selective traversal |
