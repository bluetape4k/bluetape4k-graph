# Authorization Inheritance PostgreSQL Traversal Benchmark - 2026-05-28

## 명령

```bash
./gradlew :graph-benchmark:authzInheritanceBenchmark --no-build-cache
```

## 실행 조건

- Host: macOS arm64
- JVM: Java HotSpot(TM) 64-Bit Server VM 21.0.11
- Harness: kotlinx-benchmark/JMH 1.37
- Forks: 1
- Warmup: 2 x 2 s
- Measurement: 3 x 2 s
- Database: PostgreSQL AGE Testcontainer for all targets
- Metric direction: 낮은 `ms/op`가 더 좋다
- 정확성: 이 실행 전에 smoke test가 AGE/Cypher, PostgreSQL CTE, PostgreSQL iterative traversal의 F1 `1.0`을 검증했다

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

이 method는 같은 resource를 resolve하고 correctness를 basis point로 변환한다. 별도 ranking axis가 아니라 correctness-metric overhead를 지키는 guard benchmark다.

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

## 차트

![Authorization inheritance traversal latency](../images/readme-charts/authz-inheritance-postgresql-latency-chart-01.png)

- [Chart PNG](../images/readme-charts/authz-inheritance-postgresql-latency-chart-01.png)
- [Chart SVG](../images/readme-charts/authz-inheritance-postgresql-latency-chart-01.svg)
- [Raw JMH JSON](2026-05-28-authz-inheritance-main.json)

## 해석

이 PostgreSQL AGE fixture에서 AGE/Cypher는 latency 기준으로 이기지 못했다. 측정된 `small` 및 `medium` authorization-inheritance scenario 전반에서 PostgreSQL recursive CTE와 iterative batched traversal이 더 빨랐다. AGE는 variable-depth graph traversal을 더 직접적으로 표현하지만, 이 결과는 현재 구현과 dataset에 대해 속도 기반 GraphDB adoption 주장을 뒷받침하지 않는다.

이 결과는 다음 최적화 질문으로 이어져야 한다. 더 나은 Cypher shape, index, graph-specific modeling으로 AGE가 격차를 줄일 수 있는지, 아니면 PostgreSQL CTE/iterative traversal이 이 workload에 맞는 구현인지 판단해야 한다.

TinkerGraph는 in-memory이므로 이 GraphDB adoption benchmark에서만 제외한다. 기존 TinkerGraph API/contract benchmark track은 별도로 유지하며 persistent database adoption evidence로 사용하지 않는다.
