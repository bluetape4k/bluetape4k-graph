# Graph Write Ingestion Testcontainers Results - 2026-05-21

Run conditions: macOS arm64, GraalVM JDK 25.0.3, JMH 1.37, one fork, one warmup iteration, three one-second measurement iterations, real Testcontainers backends, and GC profiler enabled.

All latency values are `ms/op`; lower is better. Bold indicates the fastest backend in that row. `Repeated mixed batches` runs five mixed vertex+edge batches in one benchmark operation.

## Latency

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

## Allocation

All allocation values are `MiB/op`; lower is better. Allocation is from JMH `gc.alloc.rate.norm`.

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

## Interpretation

Memgraph is the fastest persistent backend across every latency row in this local sustained write run. Neo4j is usually the second-best persistent backend for 1,000-row mixed and repeated mixed profiles, which supports keeping it as the low-risk production default when operational maturity matters more than raw ingestion latency.

FalkorDB is competitive for vertex-only insertion but becomes impractical for edge-heavy and mixed sustained ingestion in this common contract benchmark. AGE remains a PostgreSQL consolidation choice; it does not win raw write latency once edges dominate.

The full 10,000-row backend matrix was intentionally split into follow-up issue [#201](https://github.com/bluetape4k/bluetape4k-graph/issues/201). FalkorDB already reached 17.286 s/op at the 1,000-row repeated mixed profile, so a full 10k all-backend run would be too slow for a routine local benchmark.

## Artifacts

- [Raw JMH JSON](graph-write-ingestion-testcontainers-2026-05-21.json)
- [Chart PNG](../images/readme-charts/graph-write-ingestion-testcontainers-latency-chart-01.png)
- [Chart SVG](../images/readme-charts/graph-write-ingestion-testcontainers-latency-chart-01.svg)
