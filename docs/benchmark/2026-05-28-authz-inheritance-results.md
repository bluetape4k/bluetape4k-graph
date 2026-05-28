# Authorization Inheritance PostgreSQL Traversal Benchmark - 2026-05-28

## Command

```bash
./gradlew :graph-benchmark:authzInheritanceBenchmark --no-build-cache
```

## Run Conditions

- Host: macOS arm64
- JVM: Java HotSpot(TM) 64-Bit Server VM 21.0.11
- Harness: kotlinx-benchmark/JMH 1.37
- Forks: 1
- Warmup: 2 x 2 s
- Measurement: 3 x 2 s
- Database: PostgreSQL AGE Testcontainer for all targets
- Metric direction: lower `ms/op` is better
- Correctness: smoke tests verified F1 `1.0` for AGE/Cypher, PostgreSQL CTE, and PostgreSQL iterative traversal before this run

## Scenario Matrix

| Scenario | Shape |
|---|---|
| `shallow` | short user/group/role/resource inheritance paths |
| `deep-inheritance` | deeper inheritance chains with cycle edges |
| `deny-heavy` | many deny grant edges, deny-overrides-allow semantics |
| `wide-groups` | wider group membership fan-out |

## resolveResources Latency

| Scenario | Size | AGE/Cypher | PostgreSQL CTE | PostgreSQL iterative | Winner |
|---|---:|---:|---:|---:|---|
| `shallow` | `small` | 6.337 | 0.572 | 0.576 | **PostgreSQL CTE** |
| `shallow` | `medium` | 57.382 | 12.085 | 1.056 | **PostgreSQL iterative** |
| `deep-inheritance` | `small` | 93.848 | 0.726 | 1.449 | **PostgreSQL CTE** |
| `deep-inheritance` | `medium` | 604.833 | 9.385 | 2.102 | **PostgreSQL iterative** |
| `deny-heavy` | `small` | 67.670 | 0.993 | 1.803 | **PostgreSQL CTE** |
| `deny-heavy` | `medium` | 448.263 | 9.450 | 4.310 | **PostgreSQL iterative** |
| `wide-groups` | `small` | 29.445 | 0.551 | 1.801 | **PostgreSQL CTE** |
| `wide-groups` | `medium` | 250.083 | 1.521 | 3.658 | **PostgreSQL CTE** |

## resolveF1BasisPoints Latency

This method resolves the same resources and converts correctness to basis points. It is a guard benchmark for correctness-metric overhead, not a separate ranking axis.

| Scenario | Size | AGE/Cypher | PostgreSQL CTE | PostgreSQL iterative | Winner |
|---|---:|---:|---:|---:|---|
| `shallow` | `small` | 6.293 | 0.581 | 0.589 | **PostgreSQL CTE** |
| `shallow` | `medium` | 60.379 | 12.097 | 1.036 | **PostgreSQL iterative** |
| `deep-inheritance` | `small` | 94.778 | 0.735 | 1.452 | **PostgreSQL CTE** |
| `deep-inheritance` | `medium` | 607.715 | 9.436 | 2.100 | **PostgreSQL iterative** |
| `deny-heavy` | `small` | 70.490 | 1.007 | 1.803 | **PostgreSQL CTE** |
| `deny-heavy` | `medium` | 448.080 | 9.829 | 4.225 | **PostgreSQL iterative** |
| `wide-groups` | `small` | 29.850 | 0.559 | 1.847 | **PostgreSQL CTE** |
| `wide-groups` | `medium` | 245.779 | 1.528 | 3.486 | **PostgreSQL CTE** |

## Chart

![Authorization inheritance traversal latency](../images/readme-charts/authz-inheritance-postgresql-latency-chart-01.png)

- [Chart PNG](../images/readme-charts/authz-inheritance-postgresql-latency-chart-01.png)
- [Chart SVG](../images/readme-charts/authz-inheritance-postgresql-latency-chart-01.svg)
- [Raw JMH JSON](2026-05-28-authz-inheritance-main.json)

## Interpretation

AGE/Cypher did not win latency in this PostgreSQL AGE fixture. PostgreSQL recursive CTE and iterative batched traversal were faster across the measured `small` and `medium` authorization-inheritance scenarios. AGE still expresses variable-depth graph traversal more directly, but this result does not support a speed-based GraphDB adoption claim for the current implementation and dataset.

The result should drive the next optimization question: whether AGE can close the gap with better Cypher shape, indexes, or graph-specific modeling, or whether PostgreSQL CTE/iterative traversal is the correct implementation for this workload.
