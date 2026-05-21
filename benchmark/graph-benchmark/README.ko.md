# graph-benchmark

[English](README.md) | [한국어](README.ko.md)

그래프 성능 비교를 위한 JMH/kotlinx-benchmark 모듈입니다. 현재 다섯 가지 측정 축을 포함합니다.

- 기존 TinkerGraph Sync vs Virtual Thread 그래프 연산.
- 동일한 TinkerGraph fixture에서 Sync, Virtual Thread, Coroutine API model 비교.
- 공통 `GraphOperations` 계약을 통한 Graph DB backend 비교.
- 공통 `GraphOperations` 계약을 통한 sustained graph write와 batch ingestion 비교.
- 동일한 TinkerGraph 생성 데이터셋을 사용하는 graph-io 포맷 비교.

## Architecture

![graph-benchmark Architecture diagram](../../docs/images/readme-diagrams/benchmark/graph-benchmark-architecture-01.png)

## 측정 대상

- `GraphDbComparisonBenchmark`: `tinkergraph`, `neo4j`, `memgraph`, `age`, `falkordb` backend.
- `GraphWriteIngestionBenchmark`: 동일 backend matrix에서 vertex-only, edge-only, mixed, repeated mixed write batch.
- `GraphIoComparisonBenchmark`: `csv`, `jackson2`, `jackson3`, `graphml`, `okio-jackson3`, `okio-graphml`.
- `ApiModelBenchmark`: 동일한 in-memory TinkerGraph fixture에서 sync, virtual-thread, coroutine API overhead.
- 기존 operation benchmark: batch insert, shortest path, neighbors, traversal, algorithm, vertex operations.

컨테이너 기반 backend benchmark는 bluetape4k Testcontainers launcher 또는 wrapper를 사용합니다. 순차 실행해야 하며 초기 기동 시간이 더 깁니다.

## 실행

```bash
./gradlew :graph-benchmark:benchmark
```

kotlinx-benchmark는 JMH JSON을 `benchmark/graph-benchmark/build/reports/benchmarks/**/main.json` 아래에 기록합니다.

Graph DB backend matrix는 실제 Testcontainers 기반 JMH target으로 실행합니다.

```bash
java -jar benchmark/graph-benchmark/build/benchmarks/main/jars/graph-benchmark-main-jmh-*-JMH.jar \
  '.*GraphDbComparisonBenchmark.*' \
  -wi 1 -i 3 -r 1s -w 1s -f 1 \
  -p backend=tinkergraph,neo4j,memgraph,age,falkordb \
  -p sizeName=small \
  -rf json \
  -rff docs/benchmark/graph-db-testcontainers-2026-05-21.json
```

medium backend matrix는 다음처럼 실행합니다.

```bash
java -jar benchmark/graph-benchmark/build/benchmarks/main/jars/graph-benchmark-main-jmh-*-JMH.jar \
  '.*GraphDbComparisonBenchmark.*' \
  -wi 3 -i 5 -r 3s -w 2s -f 1 \
  -p backend=tinkergraph,neo4j,memgraph,age,falkordb \
  -p sizeName=medium \
  -rf json \
  -rff docs/benchmark/graph-db-medium-testcontainers-2026-05-21.json
```

sustained write ingestion profile은 다음처럼 실행합니다.

```bash
java -jar benchmark/graph-benchmark/build/benchmarks/main/jars/graph-benchmark-main-jmh-*-JMH.jar \
  '.*GraphWriteIngestionBenchmark.*' \
  -wi 1 -i 3 -w 1s -r 1s -f 1 \
  -p backend=tinkergraph,neo4j,memgraph,age,falkordb \
  -p batchSize=100,1000 \
  -p repeatBatches=5 \
  -prof gc \
  -rf json \
  -rff docs/benchmark/graph-write-ingestion-testcontainers-2026-05-21.json
```

Docker-free API model matrix는 다음처럼 실행합니다.

```bash
java -jar benchmark/graph-benchmark/build/benchmarks/main/jars/graph-benchmark-main-jmh-*-JMH.jar \
  '.*ApiModelBenchmark.*' \
  -wi 1 -i 3 -r 1s -w 1s -f 1 \
  -prof gc \
  -rf json \
  -rff docs/benchmark/2026-05-21-api-model-jmh.json
```

## 최신 API Model 결과

![API model benchmark](../../docs/images/readme-charts/graph-api-model-chart-01.png)

실행 조건: macOS arm64, GraalVM JDK 25.0.3, JMH 1.37, fork 1회, warmup 1회, 1초 measurement 3회, TinkerGraph fixture, 2026-05-21. 짧은 로컬 smoke run이므로 release-grade 주장에는 raw JSON 확인과 재측정이 필요합니다.

PageRank throughput은 `ops/s`이며 높을수록 좋습니다.

| API model | Score | Error | Allocation |
|---|---:|---:|---:|
| Sync | **138,943.484 ops/s** | ±40,362.146 | 28,451 B/op |
| Virtual Thread | 40,283.460 ops/s | ±9,678.720 | 29,456 B/op |
| Coroutine Flow | 36,879.554 ops/s | ±85,084.781 | 29,516 B/op |

BFS와 launch/create 행은 `us/op`이며 낮을수록 좋습니다.

| Scenario | API model | Score | Error | Allocation |
|---|---|---:|---:|---:|
| BFS depth=5 | Sync | **4.724 us/op** | ±3.022 | 21,990 B/op |
| BFS depth=5 | Virtual Thread | 18.668 us/op | ±8.229 | 23,152 B/op |
| BFS depth=5 | Coroutine Flow | 20.244 us/op | ±11.268 | 23,455 B/op |
| BFS 100-way | Virtual Thread | **240.903 us/op** | ±167.502 | 2,318,801 B/op |
| BFS 100-way | Coroutine async | 279.828 us/op | ±329.942 | 2,367,754 B/op |
| 100-way launch/create | Virtual Thread | 51.042 us/op | ±173.745 | 61,464 B/op |
| 100-way launch/create | Coroutine async | **5.916 us/op** | ±3.127 | 28,373 B/op |

결과 산출물:

- [Chart PNG](../../docs/images/readme-charts/graph-api-model-chart-01.png)
- [Chart SVG](../../docs/images/readme-charts/graph-api-model-chart-01.svg)
- [Raw JMH JSON](../../docs/benchmark/2026-05-21-api-model-jmh.json)
- [Markdown result table](../../docs/benchmark/2026-05-21-api-model-results.md)

## 최신 Testcontainers 결과

### Sustained write ingestion

![Graph write ingestion Testcontainers benchmark](../../docs/images/readme-charts/graph-write-ingestion-testcontainers-latency-chart-01.png)

실행 조건: macOS arm64, GraalVM JDK 25.0.3, JMH 1.37, fork 1회, warmup 1회, 1초 measurement 3회, 실제 Testcontainers backend, GC profiler 활성화, 2026-05-21. `Repeated mixed batches`는 benchmark operation 하나에서 mixed vertex+edge batch를 5회 반복합니다.

모든 값은 `ms/op`이며 낮을수록 좋습니다. 굵은 값은 이번 실행에서 가장 빠른 backend입니다.

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

해석: Memgraph는 모든 latency row에서 가장 빠른 persistent backend입니다. Neo4j는 raw ingestion latency보다 운영 성숙도가 더 중요할 때 낮은 risk의 production 기본값으로 유지합니다. FalkorDB는 vertex-only insert에서는 경쟁력이 있지만, edge-heavy와 repeated mixed write profile은 이 공통 contract benchmark에서 너무 느립니다. FalkorDB가 1,000-row repeated mixed profile에서 이미 17.286 s/op까지 올라갔으므로 전체 10k matrix는 [#201](https://github.com/bluetape4k/bluetape4k-graph/issues/201)에서 별도로 추적합니다.

결과 산출물:

- [Chart PNG](../../docs/images/readme-charts/graph-write-ingestion-testcontainers-latency-chart-01.png)
- [Chart SVG](../../docs/images/readme-charts/graph-write-ingestion-testcontainers-latency-chart-01.svg)
- [Raw JMH JSON](../../docs/benchmark/graph-write-ingestion-testcontainers-2026-05-21.json)
- [Markdown result table](../../docs/benchmark/2026-05-21-graph-write-ingestion-testcontainers-results.md)

### Medium dataset

![Graph DB medium Testcontainers benchmark](../../docs/images/readme-charts/graph-db-medium-testcontainers-latency-chart-01.png)

실행 조건: macOS arm64, GraalVM JDK 25.0.3, JMH 1.37, fork 1회, warmup 3회, 3초 measurement 5회, `medium` dataset, 2026-05-21. FalkorDB는 기본 `jfalkordb` timeout이 이 fixture에서 실패해서 benchmark driver에 60초 Jedis read timeout을 적용해 재측정했습니다.

| Operation | TinkerGraph | Neo4j | Memgraph | AGE | FalkorDB |
|---|---:|---:|---:|---:|---:|
| `batchInsertCycle` | 44.967 | 15.690 | **11.364** | 309.090 | 1929.180 |
| `countPersons` | 0.308 | 0.528 | 1.341 | 2.176 | **0.197** |
| `oneHopNeighbors` | **0.003** | 0.665 | 0.308 | 10.175 | 1.046 |
| `shortestPath` | **0.019** | 0.700 | 0.386 | 12.420 | 0.512 |

모든 값은 `ms/op`이며 낮을수록 좋습니다. 굵은 값은 이번 실행에서 가장 빠른 backend입니다.

결과 산출물:

- [Chart PNG](../../docs/images/readme-charts/graph-db-medium-testcontainers-latency-chart-01.png)
- [Chart SVG](../../docs/images/readme-charts/graph-db-medium-testcontainers-latency-chart-01.svg)
- [Raw JMH JSON](../../docs/benchmark/graph-db-medium-testcontainers-2026-05-21.json)
- [FalkorDB timeout 재측정 JSON](../../docs/benchmark/graph-db-medium-falkordb-testcontainers-2026-05-21.json)
- [Markdown result table](../../docs/benchmark/2026-05-21-graph-db-medium-testcontainers-results.md)

### Small dataset

![Graph DB Testcontainers benchmark](../../docs/images/readme-charts/graph-db-testcontainers-latency-chart-01.png)

실행 조건: macOS arm64, GraalVM JDK 25.0.3, JMH 1.37, fork 1회, warmup 1회, 1초 measurement 3회, `small` dataset, 2026-05-21.

| Operation | TinkerGraph | Neo4j | Memgraph | AGE | FalkorDB |
|---|---:|---:|---:|---:|---:|
| `batchInsertCycle` | 5.379 | 6.217 | **1.969** | 21.665 | 38.660 |
| `countPersons` | **0.032** | 0.809 | 0.402 | 0.610 | 0.193 |
| `oneHopNeighbors` | **0.003** | 0.811 | 0.334 | 0.932 | 0.708 |
| `shortestPath` | **0.018** | 0.806 | 0.331 | 1.320 | 0.280 |

모든 값은 `ms/op`이며 낮을수록 좋습니다. 굵은 값은 이번 실행에서 가장 빠른 backend입니다.

결과 산출물:

- [Chart PNG](../../docs/images/readme-charts/graph-db-testcontainers-latency-chart-01.png)
- [Chart SVG](../../docs/images/readme-charts/graph-db-testcontainers-latency-chart-01.svg)
- [Raw JMH JSON](../../docs/benchmark/graph-db-testcontainers-2026-05-21.json)
- [Normalized baseline JSON](../../docs/benchmark/graph-benchmark-baseline.json)
- [Markdown result table](../../docs/benchmark/2026-05-21-graph-db-testcontainers-results.md)

## 리포트

JMH JSON을 전/후 비교용 안정 스키마로 정규화합니다.

```bash
python3 benchmark/graph-benchmark/scripts/normalize_jmh_report.py \
  benchmark/graph-benchmark/build/reports/benchmarks/main/main.json \
  --markdown docs/benchmark/graph-benchmark-latest.md
```

baseline과 candidate를 비교할 때:

```bash
python3 benchmark/graph-benchmark/scripts/normalize_jmh_report.py candidate.json \
  --baseline baseline.json \
  --metric score \
  --direction lower_is_better \
  --markdown docs/benchmark/graph-benchmark-candidate.md
```

위 README 차트를 렌더링합니다.

```bash
python3 benchmark/graph-benchmark/scripts/render_graph_db_backend_chart.py \
  docs/benchmark/graph-db-testcontainers-2026-05-21.json
```

위 API model 차트를 렌더링합니다.

```bash
python3 benchmark/graph-benchmark/scripts/render_api_model_chart.py \
  docs/benchmark/2026-05-21-api-model-jmh.json
```

위 sustained write ingestion 차트를 렌더링합니다.

```bash
python3 benchmark/graph-benchmark/scripts/render_graph_write_ingestion_chart.py \
  docs/benchmark/graph-write-ingestion-testcontainers-2026-05-21.json
```

## Self-Improve Gate

`bluetape4k-self-improve`는 fresh baseline이 생긴 뒤 사용합니다. 최적화 round에서 봉인할 파일은 다음과 같습니다.

- `benchmark/graph-benchmark/src/main/kotlin/io/bluetape4k/graph/benchmark/GraphDbComparisonBenchmark.kt`
- `benchmark/graph-benchmark/src/main/kotlin/io/bluetape4k/graph/benchmark/GraphWriteIngestionBenchmark.kt`
- `benchmark/graph-benchmark/src/main/kotlin/io/bluetape4k/graph/benchmark/GraphIoComparisonBenchmark.kt`
- `benchmark/graph-benchmark/scripts/normalize_jmh_report.py`
- `benchmark/graph-benchmark/scripts/render_graph_write_ingestion_chart.py`
- `docs/benchmark/graph-benchmark-baseline.json`

candidate를 채택하기 전 sealed-file validator를 실행합니다.

```bash
scripts/validate-graph-benchmark-sealed.sh
```

## 참고

- Amazon Neptune은 신뢰 가능한 로컬/통합 테스트 가능성이 확보될 때까지 제외합니다.
- Graph DB benchmark는 vendor별 튜닝 쿼리가 아니라 공통 repository contract 성능을 비교합니다.
