# Graph Write Ingestion Testcontainers 결과 - 2026-05-21

실행 조건: macOS arm64, GraalVM JDK 25.0.3, JMH 1.37, fork 1회, warmup iteration 1회, 1초 measurement iteration 3회, 실제 Testcontainers backend, GC profiler 활성화.

모든 latency 값은 `ms/op`이며 낮을수록 좋다. 굵은 값은 해당 row의 가장 빠른 backend를 뜻한다. `Repeated mixed batches`는 하나의 benchmark operation에서 vertex+edge mixed batch를 5회 실행한다.

## 지연 시간

![Graph write ingestion Testcontainers benchmark](../images/readme-charts/graph-write-ingestion-testcontainers-latency-chart-01.png)

| Scenario | Batch | TinkerGraph | Neo4j | Memgraph | AGE | FalkorDB |
|---|---:|---:|---:|---:|---:|---:|
| Vertex-only batch insert | 100 | 2.606 | 2.740 | **0.965** | 1.347 | 1.467 |
| Vertex-only batch insert | 1,000 | 11.902 | 8.499 | **5.429** | 9.100 | 6.210 |
| Edge-only batch insert | 100 | 3.298 | 3.788 | **1.190** | 9.436 | 32.347 |
| Edge-only batch insert | 1,000 | 18.971 | 13.762 | **7.572** | 260.430 | 3026.397 |
| Mixed vertex+edge insert | 100 | 7.102 | 7.352 | **2.199** | 27.426 | 51.437 |
| Mixed vertex+edge insert | 1,000 | 30.819 | 23.510 | **13.336** | 279.704 | 3354.140 |
| Repeated mixed batches (5x) | 100 | 32.221 | 35.181 | **11.456** | 149.935 | 263.976 |
| Repeated mixed batches (5x) | 1,000 | 154.891 | 113.940 | **66.612** | 1428.239 | 17285.768 |

## 할당량

모든 allocation 값은 `MiB/op`이며 낮을수록 좋다. Allocation은 JMH `gc.alloc.rate.norm`에서 온 값이다.

| Scenario | Batch | TinkerGraph | Neo4j | Memgraph | AGE | FalkorDB |
|---|---:|---:|---:|---:|---:|---:|
| Vertex-only batch insert | 100 | 17.94 | 0.89 | 0.82 | 0.62 | **0.46** |
| Vertex-only batch insert | 1,000 | 63.19 | 9.79 | 8.94 | 6.94 | **5.52** |
| Edge-only batch insert | 100 | 17.86 | 1.12 | 0.87 | 0.84 | **0.83** |
| Edge-only batch insert | 1,000 | 78.78 | 12.83 | **9.78** | 38.36 | 101.57 |
| Mixed vertex+edge insert | 100 | 33.26 | 2.05 | 1.69 | 1.61 | **1.46** |
| Mixed vertex+edge insert | 1,000 | 143.46 | 22.48 | **18.74** | 44.86 | 105.14 |
| Repeated mixed batches (5x) | 100 | 179.34 | 10.19 | 8.47 | 8.05 | **6.87** |
| Repeated mixed batches (5x) | 1,000 | 726.65 | 110.11 | **93.36** | 195.60 | 143.96 |

## 해석

이 로컬 sustained write run의 모든 latency row에서 Memgraph가 가장 빠른 persistent backend다. Neo4j는 1,000-row mixed 및 repeated mixed profile에서 대체로 두 번째로 빠른 persistent backend이며, raw ingestion latency보다 운영 성숙도가 더 중요한 경우 low-risk production default로 유지할 근거가 된다.

FalkorDB는 vertex-only insertion에서는 경쟁력이 있지만, 이 common contract benchmark에서는 edge-heavy 및 mixed sustained ingestion에서 비현실적으로 느려진다. AGE는 PostgreSQL consolidation 선택지로 남지만, edge가 지배적인 순간 raw write latency에서는 이기지 못한다.

전체 10,000-row backend matrix는 의도적으로 후속 issue [#201](https://github.com/bluetape4k/bluetape4k-graph/issues/201)로 분리했다. FalkorDB는 1,000-row repeated mixed profile에서 이미 17.286 s/op에 도달했으므로, full 10k all-backend run은 routine local benchmark로는 너무 느리다.

## 산출물

- [Raw JMH JSON](graph-write-ingestion-testcontainers-2026-05-21.json)
- [Chart PNG](../images/readme-charts/graph-write-ingestion-testcontainers-latency-chart-01.png)
- [Chart SVG](../images/readme-charts/graph-write-ingestion-testcontainers-latency-chart-01.svg)
