# 벤치마크 목록과 선택 절차

## 0.5.1에 포함된 실행 프로젝트

| 프로젝트 | 작업 부하 | 실행 환경 | 지표 |
|---|---|---|---|
| `graph-benchmark` | CRUD, 탐색, 도메인, 적재, API 실행 방식 | JMH, TinkerGraph와 직렬 Testcontainers | 평균 시간은 낮을수록 좋고 일부 처리량은 높을수록 좋음 |
| `graph-io-benchmark` | CSV, NDJSON, GraphML, OkIO 왕복 | JMH, 임시 파일, TinkerGraph | `ms/op`, 낮을수록 좋음 |
| `graph-age-benchmark` | AGE 정점·배치·이웃·경로 | AGE 컨테이너, JDBC, HikariCP | `ms/op`, 낮을수록 좋음 |
| `graph-neo4j-benchmark` | Neo4j 정점·배치·이웃·경로 | Neo4j 컨테이너, Java Driver | `ms/op`, 낮을수록 좋음 |

JDK 21, 커밋 `3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907`, 컨테이너 실행에는 Docker가 필요합니다. 컨테이너 벤치마크는 직렬로 실행하십시오.

```bash
./gradlew :graph-benchmark:mainGraphDomainWorkloadBenchmark
./gradlew :graph-io-benchmark:smokeBenchmark
./gradlew :graph-age-benchmark:benchmark
./gradlew :graph-neo4j-benchmark:benchmark
```

각 작업은 JMH 반복 결과와 모듈의 `build/reports/benchmarks/` 아래 JSON을 남겨야 합니다. smoke 결과로 성능을 판단하면 안 됩니다.

## 선택 절차와 한계

쿼리 모양과 데이터 크기가 맞는 작업 부하를 고르고, 같은 실행에서 fixture, 매개변수, 장비, fork, warmup, 반복, 단위가 같은 행만 비교하십시오. 단일 점수보다 구간과 반복 실행을 보고, 운영 데이터 모양으로 다시 측정해야 합니다.

근거는 [백엔드 결과](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/docs/benchmark/2026-05-21-graph-db-testcontainers-results.md), [graph-io 결과](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/docs/benchmark/2026-04-18-graph-io-bulk-results.md), [API 실행 방식 JSON](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/docs/benchmark/2026-05-21-api-model-jmh.json)입니다. 서로 다른 조건의 결과로 백엔드 순위를 만들 수 없습니다. 이 결과는 수평 확장, 장애 전환, 내구성, 비용을 증명하지 않습니다.

[그래프 연산](./graph-operations.md), [그래프 입출력](./graph-io.md), [AGE와 Neo4j](./age-and-neo4j.md)로 이어가십시오. 라이브러리 사용자는 `bluetape4k-graph-bom:0.5.1`을 가져오고 모듈별 버전을 생략합니다.
