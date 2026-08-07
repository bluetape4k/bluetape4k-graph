# 그래프 연산 벤치마크

`graph-benchmark`는 CRUD, 탐색, 도메인 그래프, 지속 적재, 동기·가상 스레드·코루틴 실행 방식을 측정합니다. [Gradle 설정](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/benchmark/graph-benchmark/build.gradle.kts), [도메인 벤치마크](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/benchmark/graph-benchmark/src/main/kotlin/io/bluetape4k/graph/benchmark/GraphDomainWorkloadBenchmark.kt), [상태 관리](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/benchmark/graph-benchmark/src/main/kotlin/io/bluetape4k/graph/benchmark/GraphBenchmarkState.kt)를 먼저 읽으십시오.

JDK 21과 Docker가 필요하며 다른 Testcontainers 작업과 겹치면 안 됩니다.

```bash
./gradlew :graph-benchmark:mainGraphDomainWorkloadBenchmark --rerun-tasks --console=plain
```

이 설정은 TinkerGraph, Neo4j, Memgraph 도메인 작업을 고르고 warmup 2회, 2초 측정 4회, JSON 출력을 사용합니다. 별도 지정이 없으면 JMH 기본 fork 수를 따릅니다. `avgt`의 `ms/op`은 낮을수록 좋습니다. 같은 도메인, 백엔드, 크기, 매개변수끼리만 비교하십시오. 대표 근거는 [2026-05-21 JSON](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/benchmark/graph-domain-workload-testcontainers-2026-05-21.json)이며 로컬 컨테이너 결과를 보편 순위로 해석하면 안 됩니다.

한가한 장비에서 세 번 반복하고 원본 JSON과 오차 구간을 보관하십시오. warmup과 fork로도 컨테이너, GC, 발열 편차는 사라지지 않습니다. 종료 단계가 연산 객체, 드라이버·풀, 컨테이너를 닫아야 합니다. 중단했다면 남은 컨테이너를 확인한 뒤 다시 실행하십시오.

JSON이 없거나 제한 시간이 끝났다면 점수를 만들지 말고 실패 기록을 보관하십시오. 이 실행은 군집 장애 전환, 내구성, 운영 인덱스, 권한, 비용을 증명하지 않습니다. [개요](./overview.md)와 [선택 가이드](../guides/benchmark-based-selection.md)를 함께 보십시오.

<!-- release-readme-diagrams:start -->
## 배포본 다이어그램 {#release-diagrams}

아래 그림은 `0.6.0` 배포본의 README 자산을 해당 배포 커밋에서 직접 불러옵니다. 이후 SNAPSHOT이 아니라 이 매뉴얼 버전의 구조와 실행 흐름을 보여 줍니다. 미리보기를 누르면 같은 배포 커밋의 SVG 원본이 열립니다.

### Bluetape4k Graph-benchmark 아키텍처

[![Bluetape4k Graph-benchmark 아키텍처](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/benchmark/graph-benchmark-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/benchmark/graph-benchmark-architecture-01.svg)

_배포본 README: [`benchmark/graph-benchmark/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/benchmark/graph-benchmark/README.ko.md)_

<!-- release-readme-diagrams:end -->
