# bluetape4k-graph Benchmark 선택 가이드

[English](README.md) | [한국어](README.ko.md)

이 디렉터리는 graph backend, API model, graph-io format을 선택하기 위한 benchmark 모듈을 모아 둔 곳입니다. 아래 가이드는 지금까지의 측정값을 서비스 규모, 데이터 규모, 서비스 분야별 추천으로 정리합니다.

이 문서는 첫 선택을 빠르게 좁히기 위한 기준입니다. backend matrix는 이제 `small`과 `medium` 로컬 Testcontainers run을 모두 포함합니다. 실제 운영 도입 전에는 서비스 데이터 모델, 쿼리 비율, 배포 환경에 맞춘 benchmark를 다시 돌려야 합니다.

## 근거 자료

| 영역 | Source | 현재 범위 | 해석 방향 |
|---|---|---|---|
| Graph DB backend | `graph-benchmark` / `GraphDbComparisonBenchmark` | TinkerGraph, Neo4j, Memgraph, AGE, FalkorDB의 `small`, `medium` dataset 비교 | `ms/op` 낮을수록 좋음 |
| API model | `graph-benchmark` / `ApiModelBenchmark` | TinkerGraph에서 Sync, Virtual Thread, Coroutine Flow 비교 | PageRank `ops/s` 높을수록 좋음, latency `us/op` 낮을수록 좋음 |
| graph-io format | `graph-io-benchmark`, `graph-benchmark` | CSV, Jackson2/3 NDJSON, GraphML, Okio variants | `ms/op` 낮을수록 좋음 |

최신 raw artifact:

- `docs/benchmark/graph-db-testcontainers-2026-05-21.json`
- `docs/benchmark/graph-db-medium-testcontainers-2026-05-21.json`
- `docs/benchmark/2026-05-21-api-model-jmh.json`
- `docs/benchmark/2026-04-18-graph-io-bulk-results.md`

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

## 빠른 추천

| 상황 | 추천 솔루션 | 이유 |
|---|---|---|
| Unit test, example, local algorithm 실험 | TinkerGraph + Sync API | in-memory latency가 가장 낮고 외부 서비스가 필요 없습니다. 단, 영속성과 분산 운영 대상은 아닙니다. |
| 기본 production graph service | Neo4j + Sync 또는 Virtual Thread API | 성숙도, 운영 도구, ACID, 생태계가 가장 안정적입니다. benchmark latency가 항상 최저는 아니지만 일반 production risk가 가장 낮습니다. |
| Real-time low-latency graph analytics 또는 write-heavy ingestion | Memgraph + Virtual Thread API | `medium` batch insert가 persistent backend 중 가장 빠르고 traversal/path latency도 경쟁력이 있습니다. |
| PostgreSQL 중심 platform에서 보조 graph 기능 | Apache AGE + Sync API | PostgreSQL 운영, 백업, 권한, 거버넌스를 재사용할 수 있습니다. raw graph latency보다 플랫폼 통합을 우선할 때 선택합니다. |
| Redis/FalkorDB 기반 stack에서 단순 read-mostly graph query | FalkorDB + Sync 또는 Virtual Thread API | 현재 small run에서 shortest path가 좋지만 batch insert는 느립니다. write workload 검증 후 선택해야 합니다. |
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
| Write-heavy growing graph | Memgraph 후보 우선 | 현재 `small` run에서 persistent backend 중 batch insert가 가장 좋습니다. 실제 edge fan-out과 transaction size로 재검증해야 합니다. |
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
- [#198 Benchmark: sustained graph write and batch ingestion profiles](https://github.com/bluetape4k/bluetape4k-graph/issues/198)
- [#199 Benchmark: production-grade API model latency and allocation](https://github.com/bluetape4k/bluetape4k-graph/issues/199)

권장 next benchmark target:

- #197에서 social, fraud, knowledge graph의 fan-out별 domain workload를 추가합니다.
- #198에서 sustained write profile을 추가해 Memgraph의 짧은 batch lead가 stream pressure에서도 유지되는지 확인합니다.
- #199에서 API model latency와 allocation을 production-grade window로 재측정합니다.

```bash
java -jar benchmark/graph-benchmark/build/benchmarks/main/jars/graph-benchmark-main-jmh-*-JMH.jar \
  '.*ApiModelBenchmark.*' \
  -wi 3 -i 5 -r 3s -w 2s -f 1 \
  -prof gc \
  -rf json \
  -rff docs/benchmark/api-model-production-candidate.json
```

## 최종 선택 규칙

서비스 형태와 맞는 가장 낮은 risk의 backend부터 시작합니다.

1. TinkerGraph는 test, local analysis, prototype에만 사용합니다.
2. 일반 production graph DB 기본값은 Neo4j로 둡니다.
3. low-latency ingestion과 real-time graph analytics가 제품 핵심이면 Memgraph를 우선합니다.
4. PostgreSQL 통합이 graph-native latency보다 중요하면 AGE를 사용합니다.
5. FalkorDB는 정확한 read/write mix를 검증한 뒤 선택합니다.

그다음 runtime에 맞춰 API model을 선택합니다. 단순 직접 호출은 Sync, blocking driver 동시성은 Virtual Thread, coroutine-native composition은 Coroutine입니다.
