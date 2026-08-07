# 저장소 지도

이 저장소는 드라이버 하나에 모든 책임을 넣지 않는다. 실패가 난 계층을 먼저 찾으면 진단 범위가 크게 줄어든다.

| 영역 | 여기서 배울 내용 | 근거 |
|---|---|---|
| `graph/graph-core` | 모델, 저장소 계약, 스키마, 알고리즘 | [`GraphOperations.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphOperations.kt) |
| `graph/graph-*` | 백엔드별 질의와 트랜잭션 의미 | [`Neo4jGraphOperations.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/src/main/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphOperations.kt) |
| `graph-io/*` | 레코드, 형식, 대량 전송 | [`GraphBulkImporter.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphBulkImporter.kt) |
| `ktor`, `spring-boot` | 애플리케이션 생명주기 연동 | [`GraphPlugin.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/ktor/graph-ktor/src/main/kotlin/io/bluetape4k/graph/ktor/GraphPlugin.kt) |
| `examples` | 도메인 문제와 백엔드 공통 테스트 | [`AbstractCodeGraphTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/AbstractCodeGraphTest.kt) |
| `benchmark` | 제한된 작업 부하의 측정 근거 | [`benchmark/README.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/benchmark/README.md) |

![저장소 학습 지도](../../assets/overview/repository-learning-map.png)

공통 계약을 읽고, 연산 하나를 골라 구현과 테스트까지 따라간다. 예제 모듈은 배포 대상이 아니다. 설계와 검증 방법은 참고해도 예제의 배포 전제를 그대로 가져오면 안 된다.

장애가 나면 모델 검증, repository capability, 백엔드 질의/트랜잭션, 형식 codec, 애플리케이션 생명주기 중 어디서 시작됐는지 가른다. 그래야 한 드라이버의 증상을 공통 계약으로 오해하지 않는다.

## 연산 하나를 끝까지 따라간다

```bash
rg -n 'fun shortestPath' graph/graph-core graph/graph-neo4j graph/graph-memgraph graph/graph-age graph/graph-tinkerpop graph/graph-falkordb
./gradlew :bluetape4k-graph-core:test --tests '*ShortestPathFallbackTest'
./gradlew :code-graph-examples:test --tests '*TinkerGraphCodeGraphTest'
```

core에는 계약이, 각 백엔드에는 구현이나 fallback이, 도메인 예제에는 구체 경로 검증이 보여야 한다. core는 통과하는데 예제가 실패하면 schema와 자료 준비를 본다. 한 백엔드만 실패하면 질의 변환과 capability 테스트를 확인한다. 구현은 모두 통과하는데 애플리케이션 경로만 실패하면 프레임워크 생명주기와 자원 소유권으로 진단 범위를 옮긴다.

<!-- release-readme-diagrams:start -->
## 배포본 다이어그램 {#release-diagrams}

아래 그림은 `0.6.0` 배포본의 README 자산을 해당 배포 커밋에서 직접 불러옵니다. 이후 SNAPSHOT이 아니라 이 매뉴얼 버전의 구조와 실행 흐름을 보여 줍니다. 미리보기를 누르면 같은 배포 커밋의 SVG 원본이 열립니다.

### Bluetape4k Graph 아키텍처

[![Bluetape4k Graph 아키텍처](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/bluetape4k-graph-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/bluetape4k-graph-architecture-01.svg)

_배포본 README: [`README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/README.ko.md)_

### Bluetape4k Graph 클래스 구조도 2

[![Bluetape4k Graph 클래스 구조도 2](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/bluetape4k-graph-class-02.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/bluetape4k-graph-class-02.svg)

_배포본 README: [`README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/README.ko.md)_

### 백엔드 기능 비교표

[![백엔드 기능 비교표](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/root-readme-backend-capability-matrix-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/root-readme-backend-capability-matrix-01.svg)

_배포본 README: [`README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/README.ko.md)_

### Bluetape4k Graph 개요

[![Bluetape4k Graph 개요](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/root-readme-overview-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/root-readme-overview-01.svg)

_배포본 README: [`README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/README.ko.md)_

<!-- release-readme-diagrams:end -->
