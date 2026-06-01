# bluetape4k-graph Benchmark 선택 가이드

[English](README.md) | [한국어](README.ko.md)

이 디렉터리는 graph backend, API model, graph-io format을 선택하기 위한 benchmark 모듈을 모아 둔 곳입니다. 아래 가이드는 지금까지의 측정값을 서비스 규모, 데이터 규모, 서비스 분야별 추천으로 정리합니다.

이 문서는 첫 선택을 빠르게 좁히기 위한 기준입니다. backend matrix는 이제 `small`, `medium`, sustained write ingestion 로컬 Testcontainers run을 포함합니다. 실제 운영 도입 전에는 서비스 데이터 모델, 쿼리 비율, 배포 환경에 맞춘 benchmark를 다시 돌려야 합니다.

## 근거 자료

| 영역 | Source | 현재 범위 | 해석 방향 |
|---|---|---|---|
| Graph DB backend | `graph-benchmark` / `GraphDbComparisonBenchmark` | TinkerGraph, Neo4j, Memgraph, AGE, FalkorDB의 `small`, `medium` dataset 비교 | `ms/op` 낮을수록 좋음 |
| Domain graph workloads | `graph-benchmark` / `GraphDomainWorkloadBenchmark` | TinkerGraph, Neo4j, Memgraph의 social, IAM, fraud, code graph workload | `ms/op` 낮을수록 좋음 |
| Sustained graph writes | `graph-benchmark` / `GraphWriteIngestionBenchmark` | 100, 1,000, selective 10,000 row의 vertex-only, edge-only, mixed, repeated mixed write batch | `ms/op` 낮을수록 좋음 |
| API model | `graph-benchmark` / `ApiModelBenchmark` | TinkerGraph에서 Sync, Virtual Thread, Coroutine Flow와 동시성 10, 100, 1,000 production run 비교 | PageRank `ops/s` 높을수록 좋음, latency `us/op` 낮을수록 좋음 |
| graph-io format | `graph-io-benchmark`, `graph-benchmark` | CSV, Jackson2/3 NDJSON, GraphML, Okio variants | `ms/op` 낮을수록 좋음 |

최신 raw artifact:

- `docs/benchmark/graph-db-testcontainers-2026-05-21.json`
- `docs/benchmark/graph-db-medium-testcontainers-2026-05-21.json`
- `docs/benchmark/graph-write-ingestion-testcontainers-2026-05-21.json`
- `docs/benchmark/graph-db-small-gradle-testcontainers-2026-05-21.json`
- `docs/benchmark/graph-domain-workload-testcontainers-2026-05-21.json`
- `docs/benchmark/graph-write-ingestion-10k-testcontainers-2026-05-21.json`
- `docs/benchmark/api-model-production-gradle-2026-05-21.json`
- `docs/benchmark/2026-05-21-api-model-jmh.json`
- `docs/benchmark/2026-04-18-graph-io-bulk-results.md`

## AGE + Neo4j Summary Wrapper

standalone AGE와 Neo4j benchmark module의 결과를 자동화에서 하나의 machine-readable summary로 읽어야 할 때 `scripts/benchmark-neo4j-age.sh`를 사용합니다.

```bash
scripts/benchmark-neo4j-age.sh
```

wrapper는 `:graph-age-benchmark:benchmark`와 `:graph-neo4j-benchmark:benchmark`를 실행하고, 생성된 kotlinx-benchmark/JMH JSON report를 읽은 뒤 마지막 stdout line에 JSON object 하나만 출력합니다. Gradle benchmark log는 stderr로 흘려 stdout parser가 summary만 읽을 수 있게 합니다.

안정 summary schema:

```json
{
  "schema": "bluetape4k.graph.backend-benchmark-summary.v1",
  "primary": 1500.0,
  "unit": "us/op",
  "direction": "lower_is_better",
  "sources": {
    "age": "benchmark/graph-age-benchmark/build/reports/benchmarks/main/main.json",
    "neo4j": "benchmark/graph-neo4j-benchmark/build/reports/benchmarks/main/main.json"
  },
  "sub_scores": {
    "age_createVertex": 1000.0,
    "neo4j_createVertex": 2000.0
  },
  "benchmarks": [
    {
      "backend": "age",
      "key": "age_createVertex",
      "benchmark": "pkg.CreateVertexBenchmark.createVertex",
      "operation": "createVertex",
      "params": {},
      "score": 1000.0,
      "unit": "us/op",
      "sourceScore": 1.0,
      "sourceUnit": "ms/op",
      "source": "benchmark/graph-age-benchmark/build/reports/benchmarks/main/main.json"
    }
  ]
}
```

`primary`와 모든 `sub_scores` 값은 `us/op`로 정규화되며 낮을수록 좋습니다. `sub_scores`는 ranking script가 쓰는 compact contract이고, `benchmarks`는 source path, original unit, parameter를 담아 진단에 사용합니다.

contract test나 report-only parsing에서는 Gradle 실행을 건너뛰고 기존 report root를 지정할 수 있습니다.

```bash
BENCHMARK_SKIP_RUN=true \
BENCHMARK_AGE_REPORT_ROOT=benchmark/graph-age-benchmark/build/reports/benchmarks/main \
BENCHMARK_NEO4J_REPORT_ROOT=benchmark/graph-neo4j-benchmark/build/reports/benchmarks/main \
scripts/benchmark-neo4j-age.sh
```

report 생성에 실패하면 wrapper는 non-zero로 종료하고 검색한 root, 기대하는 file shape, 복구 hint를 stderr에 씁니다. malformed JMH JSON도 backend name과 file path를 포함해 실패합니다.

## Medium Backend 결과

![Graph DB medium Testcontainers benchmark](../docs/images/readme-charts/graph-db-medium-testcontainers-latency-chart-01.png)

실행 조건: macOS arm64, GraalVM JDK 25.0.3, JMH 1.37, fork 1회, warmup 3회, 3초 measurement 5회, `medium` dataset, 2026-05-21. 모든 값은 `ms/op`이며 낮을수록 좋습니다.

| Operation | TinkerGraph | Neo4j | Memgraph | AGE | FalkorDB |
|---|---:|---:|---:|---:|---:|
| `batchInsertCycle` | 44.967 | 15.690 | **11.364** | 309.090 | 1929.180 |
| `countPersons` | 0.308 | 0.528 | 1.341 | 2.176 | **0.197** |
| `oneHopNeighbors` | **0.003** | 0.665 | 0.308 | 10.175 | 1.046 |
| `shortestPath` | **0.019** | 0.700 | 0.386 | 12.420 | 0.512 |

Medium scale 해석: Memgraph는 write-heavy insert에서 가장 좋은 persistent backend이고 traversal/path도 경쟁력이 있습니다. Neo4j는 이 로컬 benchmark에서 Memgraph보다 느리지만 운영 성숙도 때문에 기본 production 추천으로 유지합니다. FalkorDB는 `countPersons`, `shortestPath` read path가 경쟁력 있지만 medium batch insert가 너무 느려 write-heavy workload에는 맞지 않습니다. AGE는 raw latency가 아니라 PostgreSQL 통합이 핵심일 때 선택합니다.

## Sustained Write Ingestion 결과

![Graph write ingestion Testcontainers benchmark](../docs/images/readme-charts/graph-write-ingestion-testcontainers-latency-chart-01.png)

실행 조건: macOS arm64, GraalVM JDK 25.0.3, JMH 1.37, fork 1회, warmup 1회, 1초 measurement 3회, 실제 Testcontainers backend, GC profiler 활성화, 2026-05-21. 모든 값은 `ms/op`이며 낮을수록 좋습니다. `Repeated mixed batches`는 benchmark operation 하나에서 mixed vertex+edge batch를 5회 반복합니다.

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

Sustained-write 해석: ingestion-heavy service의 1순위 후보는 Memgraph입니다. 운영 성숙도, 도구, 장기 지원이 raw ingestion latency보다 중요하면 Neo4j가 더 안전한 기본값입니다. FalkorDB는 edge-heavy ingestion을 선택하기 전에 전용 workload 증거가 필요합니다. AGE는 raw write latency가 아니라 PostgreSQL 통합으로 정당화하는 선택입니다.

## Domain Workload 결과

![Graph domain workload Testcontainers benchmark](../docs/images/readme-charts/graph-domain-workload-testcontainers-latency-chart-01.png)

실행 조건: macOS arm64, GraalVM JDK 25.0.3, kotlinx-benchmark/JMH 1.37, fork 1회, warmup 2회, 2초 measurement 4회, 실제 Neo4j/Memgraph Testcontainers와 in-memory TinkerGraph, 2026-05-21. 모든 값은 `ms/op`이며 낮을수록 좋습니다.

| Workload | TinkerGraph | Neo4j | Memgraph |
|---|---:|---:|---:|
| Code dependency traversal | **0.009** | 0.530 | 0.306 |
| Code reverse dependency lookup | **0.006** | 0.579 | 0.348 |
| Fraud high-degree neighborhood | **0.038** | 1.119 | 0.620 |
| Fraud suspicious path exists | 1.000 | 0.509 | **0.405** |
| IAM permission reachability | **0.033** | 0.508 | 0.300 |
| Social high fan-out expansion | **0.030** | 0.991 | 0.549 |
| Social two-hop candidate lookup | **0.164** | 2.756 | 1.755 |

Domain-workload 해석: TinkerGraph는 local/CI analysis fixture에서 가장 적합합니다. Persistent 후보 중에서는 Memgraph가 이 local domain matrix에서 더 빠릅니다. Neo4j는 운영 성숙도, tooling, maintainability가 decision driver일 때 기본 production 추천으로 유지합니다.

## 10k Sustained Write 결과

![Graph write ingestion 10k Testcontainers benchmark](../docs/images/readme-charts/graph-write-ingestion-10k-testcontainers-latency-chart-01.png)

실행 조건: macOS arm64, GraalVM JDK 25.0.3, kotlinx-benchmark/JMH 1.37, fork 1회, warmup 1회, 1초 measurement 3회, 10,000-row write batch, 실제 Neo4j/Memgraph Testcontainers와 in-memory TinkerGraph, 2026-05-21. 모든 값은 `ms/op`이며 낮을수록 좋습니다.

| Scenario | TinkerGraph | Neo4j | Memgraph |
|---|---:|---:|---:|
| Vertex-only batch insert | 71.152 | 67.135 | **53.518** |
| Edge-only batch insert | 161.907 | 108.949 | **76.105** |
| Mixed vertex+edge insert | 272.236 | 203.669 | **134.994** |
| Repeated mixed batches (5x) | 1243.229 | 919.678 | **647.574** |

10k-write 해석: 더 큰 local batch size의 sustained ingestion에서도 Memgraph가 latency 기준 1순위 후보입니다. Neo4j는 느리지만 운영 성숙도가 peak ingestion speed보다 중요할 때 여전히 유효합니다.

## 빠른 추천

| 상황 | 추천 솔루션 | 이유 |
|---|---|---|
| Unit test, example, local algorithm 실험 | TinkerGraph + Sync API | in-memory latency가 가장 낮고 외부 서비스가 필요 없습니다. 단, 영속성과 분산 운영 대상은 아닙니다. |
| 기본 production graph service | Neo4j + Sync 또는 Virtual Thread API | 성숙도, 운영 도구, ACID, 생태계가 가장 안정적입니다. benchmark latency가 항상 최저는 아니지만 일반 production risk가 가장 낮습니다. |
| Real-time low-latency graph analytics 또는 write-heavy ingestion | Memgraph + Virtual Thread API | `medium` batch insert와 sustained write ingestion row가 persistent backend 중 가장 빠릅니다. |
| PostgreSQL 중심 platform에서 보조 graph 기능 | Apache AGE + Sync API | PostgreSQL 운영, 백업, 권한, 거버넌스를 재사용할 수 있습니다. raw graph latency보다 플랫폼 통합을 우선할 때 선택합니다. |
| Redis/FalkorDB 기반 stack에서 단순 read-mostly graph query | FalkorDB + Sync 또는 Virtual Thread API | 현재 small run에서 shortest path가 좋지만 edge-heavy sustained write는 느립니다. write workload 검증 후 선택해야 합니다. |
| Kotlin coroutine app에서 graph 호출이 suspend pipeline 일부 | Coroutine API | 기존 coroutine 구조와 가장 잘 맞습니다. 다만 in-memory graph 작업이 자동으로 더 빨라지는 것은 아닙니다. |
| File interchange 또는 backup | Jackson3 NDJSON 또는 CSV | 빠르고 stream-friendly입니다. GraphML은 표준 tool 호환성이 필요할 때만 우선합니다. |

## 서비스 규모별 선택

| 규모 | 전형적 형태 | Backend | API model | graph-io |
|---|---|---|---|---|
| Prototype / demo | 단일 process, 폐기 가능한 데이터, CI example | TinkerGraph | Sync | CSV 또는 Jackson3 NDJSON |
| Small internal service | 수만에서 low millions nodes, 작은 운영 팀 | 영속성이 필요하면 Neo4j, ephemeral이면 TinkerGraph | 단순 blocking service는 Sync, blocking fan-out은 Virtual Thread | Jackson3 NDJSON |
| Low-latency operational service | 사용자 facing read, path lookup, recommendation candidate 생성 | 안정성 우선 Neo4j, latency/ingestion 우선 Memgraph | blocking driver 호출에는 Virtual Thread, coroutine-native stack은 Coroutine | Jackson3 NDJSON 또는 CSV |
| Real-time analytics / streaming | 빈번한 insert, event 기반 relationship, online exploration | Memgraph | Virtual Thread | append/stream 형태는 NDJSON |
| PostgreSQL-centered enterprise | 기존 PG backup, 접근 제어, monitoring, DBA ownership | AGE | Sync | CSV 또는 Jackson3 NDJSON |
| Large regulated production | 엄격한 backup, audit, access control, 장기 운영 | Neo4j 우선, PG 통합이 필수면 AGE | Sync 또는 Virtual Thread | Jackson3 NDJSON + domain export 검증 |

## 데이터 규모별 선택

| 데이터 규모 | 추천 | 참고 |
|---|---|---|
| Test에서 약 10k nodes 이하 | TinkerGraph | 현재 benchmark fixture가 직접 커버하는 범위입니다. |
| 10k에서 low millions | Neo4j 또는 Memgraph | `medium` run은 운영 risk 우선이면 Neo4j, write latency 우선이면 Memgraph라는 결론을 지지합니다. production 최종 선택 전 domain query를 추가하세요. |
| Write-heavy growing graph | Memgraph 후보 우선 | sustained write run에서 100, 1,000-row latency row 모두 Memgraph가 가장 빨랐습니다. 실제 edge fan-out과 transaction size로 재검증해야 합니다. |
| PostgreSQL-owned data의 보조 graph index | AGE | graph-native latency보다 운영 통합을 선택하는 경우입니다. |
| 매우 큰 graph 또는 sharding 필요 | 현재 benchmark만으로 결정 불가 | horizontal scale 동작은 지금 모듈이 증명하지 않습니다. 별도 production-shaped benchmark가 필요합니다. |

## 서비스 분야별 선택

| 분야 | 초기 후보 | 이유 |
|---|---|---|
| Social graph, follow graph, recommendation candidate | 일반 production은 Neo4j, real-time update는 Memgraph | Neo4j는 traversal/query ecosystem, Memgraph는 low-latency ingestion에 강점이 있습니다. |
| Fraud detection, transaction relationship exploration | Neo4j 또는 Memgraph | 운영 안정성과 ecosystem이면 Neo4j, streaming/low-latency 분석이면 Memgraph입니다. |
| IAM, RBAC, organization graph | Neo4j | 작은 latency 차이보다 correctness, audit, maintainability가 중요합니다. |
| Knowledge graph, entity linking | Neo4j | query tooling과 ecosystem이 중요합니다. PostgreSQL co-location이 필요하면 AGE를 검증합니다. |
| Code graph, architecture analysis, local agent tools | local 분석은 TinkerGraph, 공유 영속 service는 Neo4j | local run은 in-memory 속도가 유리하고, 공유 service는 persistence와 query tooling이 필요합니다. |
| PostgreSQL product feature의 graph submodule | AGE | graph 부하가 중간 이하라면 별도 DB 도입을 피할 수 있습니다. |
| Redis 중심 운영의 cache-like graph lookup | FalkorDB 후보 | 현재 batch insert 결과가 약하므로 read/write mix를 반드시 검증해야 합니다. |

## API Model 선택

| API model | 선택할 때 | 피할 때 |
|---|---|---|
| Sync | 요청 하나가 graph operation 하나를 직접 수행하고 thread-per-request 또는 batch-oriented app일 때 | platform thread에서 high fan-out blocking call을 많이 만들 때 |
| Virtual Thread | blocking DB driver를 높은 동시성에서 사용할 때 | 순수 CPU-bound in-memory 작업처럼 launch overhead가 지배적일 때 |
| Coroutine | 주변 app이 이미 coroutine-native이고 graph 호출을 다른 suspend 작업과 합성할 때 | coroutine wrapper만으로 synchronous in-memory graph call이 더 빨라질 것이라 기대할 때 |

현재 `ApiModelBenchmark` 결과도 같은 방향입니다. 단일 in-memory PageRank/BFS는 Sync가 가장 빨랐고, 100-way BFS는 Virtual Thread가 Coroutine보다 약간 낮은 평균 지연을 보였으며, 순수 100-way launch cost는 Coroutine이 더 낮았습니다.

Production concurrency rerun은 warmup 5회와 3초 measurement 10회로 측정했습니다.

![API model production benchmark](../docs/images/readme-charts/graph-api-model-production-chart-01.png)

| Scenario | 동시성 10 | 동시성 100 | 동시성 1,000 |
|---|---:|---:|---:|
| BFS with Virtual Threads | **31.151 us/op** | **142.730 us/op** | **921.873 us/op** |
| BFS with Coroutines | 38.795 us/op | 157.676 us/op | 1151.154 us/op |
| Virtual Thread creation | 8.867 us/op | 28.225 us/op | 200.810 us/op |
| Coroutine launch | **0.586 us/op** | **4.826 us/op** | **47.657 us/op** |

따라서 추천은 유지합니다. Blocking graph driver 동시성에는 Virtual Thread, coroutine-native composition과 structured concurrency가 중요하면 Coroutine을 선택합니다.

## graph-io Format 선택

| Format | 적합한 용도 | 주의 |
|---|---|---|
| CSV | 빠른 단순 export/import, spreadsheet-friendly inspection | 중첩 property와 schema evolution 표현력이 약합니다. |
| Jackson3 NDJSON | 기본 service interchange format | streaming과 buffering을 유지해야 합니다. |
| Jackson2 NDJSON | Jackson2 stack 호환성 | 새 module은 가능하면 Jackson3 우선입니다. |
| GraphML | GraphML을 요구하는 외부 graph tool 호환 | XML overhead가 큽니다. factory caching과 buffered stream이 필수입니다. |
| Okio variants | Kotlin/Okio 중심 pipeline | 실제 target sink/source로 다시 검증하세요. |

## 추가 Benchmark가 필요한 경우

다음 중 하나라도 해당하면 결정 전에 benchmark를 추가하세요.

- graph가 low millions 이상이거나 edge fan-out이 높습니다.
- 서비스 핵심 쿼리가 현재 fixture보다 깊은 multi-hop traversal입니다.
- write가 큰 transaction이나 sustained stream으로 들어옵니다.
- backend가 엄격한 P99 latency, recovery, memory limit을 만족해야 합니다.
- 결정이 infrastructure cost나 운영 ownership에 영향을 줍니다.

추적용 follow-up benchmark issue:

- [#197 Benchmark: graph workload shapes by domain and fan-out](https://github.com/bluetape4k/bluetape4k-graph/issues/197)
- [#199 Benchmark: production-grade API model latency and allocation](https://github.com/bluetape4k/bluetape4k-graph/issues/199)
- [#201 Benchmark: selective 10k sustained graph ingestion profiles](https://github.com/bluetape4k/bluetape4k-graph/issues/201)

권장 next benchmark target:

- #197에서 social, fraud, knowledge graph의 fan-out별 domain workload를 추가합니다.
- #199에서 API model latency와 allocation을 production-grade window로 재측정합니다.
- #201에서 useful local window 안에 끝낼 수 있는 backend subset 중심으로 10k sustained ingestion profile을 추가합니다.

기본 실행 경로는 기존 Gradle benchmark task입니다.

```bash
./gradlew :graph-benchmark:mainGraphDomainWorkloadBenchmark
./gradlew :graph-benchmark:mainApiModelProductionBenchmark
./gradlew :graph-benchmark:mainGraphWriteIngestion10kBenchmark
```

## 최종 선택 규칙

서비스 형태와 맞는 가장 낮은 risk의 backend부터 시작합니다.

1. TinkerGraph는 test, local analysis, prototype에만 사용합니다.
2. 일반 production graph DB 기본값은 Neo4j로 둡니다.
3. low-latency ingestion과 real-time graph analytics가 제품 핵심이면 Memgraph를 우선합니다.
4. PostgreSQL 통합이 graph-native latency보다 중요하면 AGE를 사용합니다.
5. FalkorDB는 정확한 read/write mix를 검증한 뒤 선택합니다.

그다음 runtime에 맞춰 API model을 선택합니다. 단순 직접 호출은 Sync, blocking driver 동시성은 Virtual Thread, coroutine-native composition은 Coroutine입니다.
