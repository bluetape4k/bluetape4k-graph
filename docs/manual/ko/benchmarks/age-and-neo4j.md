# AGE와 Neo4j 단독 벤치마크

AGE 프로젝트는 PostgreSQL JDBC, HikariCP, Exposed, AGE 컨테이너를 쓰고 Neo4j 프로젝트는 Java Driver와 Neo4j 컨테이너를 씁니다. 두 프로젝트는 작은 정점·탐색 작업을 같은 모양으로 측정합니다. [AGE 상태](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/benchmark/graph-age-benchmark/src/main/kotlin/io/bluetape4k/graph/age/benchmark/AgeBenchmarkState.kt), [Neo4j 상태](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/benchmark/graph-neo4j-benchmark/src/main/kotlin/io/bluetape4k/graph/neo4j/benchmark/Neo4jBenchmarkState.kt), [결과 래퍼](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/scripts/benchmark-neo4j-age.sh)를 확인하십시오.

JDK 21과 Docker가 필요하며 두 실행을 다른 컨테이너 벤치마크와 겹치면 안 됩니다.

```bash
./gradlew :graph-age-benchmark:benchmark --rerun-tasks --console=plain
./gradlew :graph-neo4j-benchmark:benchmark --rerun-tasks --console=plain
scripts/benchmark-neo4j-age.sh
```

각 프로젝트는 warmup 3회, 3초 측정 5회, JSON 형식을 사용하고 fork는 실행기의 기본값을 따릅니다. 보고서는 `benchmark/graph-{age,neo4j}-benchmark/build/reports/benchmarks/main/`에 생성됩니다. 래퍼는 원래 단위를 `us/op`로 맞추고 마지막 표준 출력 줄에 JSON 객체 하나를 씁니다. 지연 시간은 낮을수록 좋습니다.

통제된 단독 맞대결 결과 파일은 0.6.0에 커밋되어 있지 않으므로 숫자를 적지 않습니다. 같은 한가한 장비에서 직렬로 재현하고 두 원본 보고서와 요약을 보관하십시오. 공통 [small 실행](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/benchmark/graph-db-testcontainers-2026-05-21.json)은 조건이 같을 때만 대표 근거로 쓸 수 있습니다.

연산, 매개변수, 단위, 준비 실행, 반복, 포크, 고정 데이터, 장비가 같은 행만 비교하십시오. 서로 다른 연산의 점수를 단순 평균 내면 의미가 뒤섞입니다. 그 값으로 백엔드 순위를 매기지 마십시오. 종료 시 연산 객체, JDBC 풀·드라이버, 컨테이너가 닫혀야 합니다. 기동·확장 초기화·Bolt 준비·JSON 생성 실패는 점수가 아닙니다. 이 실행은 장애 복구, 고가용성, 운영 쿼리 계획, 비용을 증명하지 않습니다. [AGE](../backends/apache-age.md), [Neo4j](../backends/neo4j-and-memgraph.md), [개요](./overview.md)를 보십시오.

<!-- release-readme-diagrams:start -->
## 배포본 다이어그램 {#release-diagrams}

아래 그림은 `0.6.0` 배포본의 README 자산을 해당 배포 커밋에서 직접 불러옵니다. 이후 SNAPSHOT이 아니라 이 매뉴얼 버전의 구조와 실행 흐름을 보여 줍니다. 미리보기를 누르면 같은 배포 커밋의 SVG 원본이 열립니다.

### Bluetape4k Graph-age-benchmark 아키텍처

[![Bluetape4k Graph-age-benchmark 아키텍처](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/benchmark/graph-age-benchmark-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/benchmark/graph-age-benchmark-architecture-01.svg)

_배포본 README: [`benchmark/graph-age-benchmark/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/benchmark/graph-age-benchmark/README.ko.md)_

### Bluetape4k Graph-neo4j-benchmark 아키텍처

[![Bluetape4k Graph-neo4j-benchmark 아키텍처](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/benchmark/graph-neo4j-benchmark-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/benchmark/graph-neo4j-benchmark-architecture-01.svg)

_배포본 README: [`benchmark/graph-neo4j-benchmark/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/benchmark/graph-neo4j-benchmark/README.ko.md)_

<!-- release-readme-diagrams:end -->
