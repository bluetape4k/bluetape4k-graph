# bluetape4k-graph-core


실행 방식: **메모리에서 바로 실행**한다. `bluetape4k-graph-tinkerpop`은 실행 가능한 `GraphOperations`를 제공한다. core API만 쓰는 모듈은 배포 환경에 맞는 실제 그래프 모듈을 선택해야 한다.

## 실행 전 준비

core는 `GraphVertex`, `GraphEdge`, `GraphPath`, 동기·virtual thread·coroutine 저장소, merge, schema DSL, 트랜잭션 scope, 공통 알고리즘을 정의한다. 공통 API로 애플리케이션을 작성하거나 새 그래프 구현을 만들 때 선택한다. 저장 엔진이 필요하면 core만 넣지 말고 실제 그래프 모듈을 고른다.

근거 API는 [GraphOperations.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphOperations.kt)와 [GraphTraversalRepository.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphTraversalRepository.kt)다.

## 실행

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-core")
    implementation("io.github.bluetape4k:bluetape4k-graph-tinkerpop") // executable implementation
}
```

## 핵심 API와 실행

실행 가능한 최소 예제에는 TinkerGraph 구현을 함께 쓴다.

```kotlin
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations

TinkerGraphOperations().use { ops ->
    val alice = ops.createVertex("Person", mapOf("email" to "a@example.com"))
    val bob = ops.mergeVertex("Person", mapOf("email" to "b@example.com"), mapOf("name" to "Bob"))
    ops.createEdge(alice.id, bob.id, "KNOWS")
    check(ops.neighbors(alice.id, NeighborOptions(edgeLabel = "KNOWS")).single().id == bob.id)
}
```

## 기대 결과

예상 결과는 정점 2개, 방향 간선 1개, Alice의 이웃 1개다.

## 동작과 자원

공통 진입점은 session, vertex, edge, traversal 저장소를 묶는다. `transaction { }`은 구현체가 `GraphTransactionalOperations`를 제공할 때만 동작한다. 지원하지 않는 기능을 원자적인 것처럼 흉내 내지 않는다. `GraphElementId`는 불투명 값이므로 숫자나 문자열 구조를 해석하지 않는다.

core는 서버 자원을 소유하지 않는다. 연산 객체, Driver, DataSource의 종료 책임은 실제 구현과 연동 계층 설정이 정한다.

## 운영 점검

- `GraphElementId`를 불투명 값으로 다룬다.
- 테스트 결과에 실제 그래프 구현을 함께 기록한다.
- 선택 기능 전에 트랜잭션과 schema 지원 여부를 확인한다.
- 탐색 깊이와 batch 크기에 상한을 둔다.

## 실패와 복구

증상: 확장 함수가 지원하지 않는 기능이라는 예외를 던진다. 필요한 interface를 제공하는 구현을 선택하거나 호출을 제거하고, 성공처럼 처리하지 않는다.

```bash
./gradlew :bluetape4k-graph-core:test --tests '*GraphMergeOperationsTest' --tests '*GraphTransactionExtensionsTest'
```

예상 결과는 merge helper와 트랜잭션 capability 검사가 통과하는 것이다. 특정 그래프 모듈만 실패하면 그 구현의 query 변환과 트랜잭션 경계를 확인한다. traversal 비용과 native 알고리즘 지원 여부도 구현마다 다르다.

## 완전한 release 예제

고정된 [TinkerGraphOperationsTest](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphOperationsTest.kt)가 실제로 실행되는 release 근거다. 다음 명령으로 확인한다.

```bash
./gradlew :bluetape4k-graph-tinkerpop:test --tests '*TinkerGraphOperationsTest'
```

예상 결과는 위 자원 소유권과 기능 경계를 검증하면서 release 테스트 또는 build가 끝나는 것이다.

## 하지 않는 일과 관련 문서

[core model](../architecture/core-model.md), [짝을 이루는 API](../architecture/paired-apis.md), [schema와 트랜잭션](../architecture/schema-and-transactions.md)을 참고한다. core는 모든 그래프 제품의 기능을 하나로 합치거나 데이터베이스를 설치하지 않는다.

<!-- release-readme-diagrams:start -->
## 배포본 다이어그램 {#release-diagrams}

아래 그림은 `0.6.0` 배포본의 README 자산을 해당 배포 커밋에서 직접 불러옵니다. 이후 SNAPSHOT이 아니라 이 매뉴얼 버전의 구조와 실행 흐름을 보여 줍니다. 미리보기를 누르면 같은 배포 커밋의 SVG 원본이 열립니다.

### Bluetape4k Graph core 아키텍처

[![Bluetape4k Graph core 아키텍처](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-architecture-01.svg)

_배포본 README: [`graph/graph-core/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/README.ko.md)_




### CRUD 다이어그램

[![CRUD 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-architecture-13.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-architecture-13.svg)

_배포본 README: [`graph/graph-core/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/README.ko.md)_

### Bluetape4k Graph core 아키텍처 14 다이어그램

[![Bluetape4k Graph core 아키텍처 14 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-architecture-14.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-architecture-14.svg)

_배포본 README: [`graph/graph-core/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/README.ko.md)_

### : GraphElementId, GraphVertex, GraphEdge 다이어그램

[![: GraphElementId, GraphVertex, GraphEdge 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-class-02.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-class-02.svg)

_배포본 README: [`graph/graph-core/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/README.ko.md)_

### PathStep GraphPath 다이어그램

[![PathStep GraphPath 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-class-03.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-class-03.svg)

_배포본 README: [`graph/graph-core/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/README.ko.md)_

### Repository 다이어그램

[![Repository 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-class-04.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-class-04.svg)

_배포본 README: [`graph/graph-core/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/README.ko.md)_


### createVertex 다이어그램

[![createVertex 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-sequence-06.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-sequence-06.svg)

_배포본 README: [`graph/graph-core/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/README.ko.md)_

### shortestPath 다이어그램

[![shortestPath 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-sequence-07.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-sequence-07.svg)

_배포본 README: [`graph/graph-core/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/README.ko.md)_

### neighbors 다이어그램

[![neighbors 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-sequence-08.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-sequence-08.svg)

_배포본 README: [`graph/graph-core/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/README.ko.md)_

### createEdge 다이어그램

[![createEdge 다이어그램](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-sequence-09.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/graph-graph-core-sequence-09.svg)

_배포본 README: [`graph/graph-core/README.ko.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/README.ko.md)_

<!-- release-readme-diagrams:end -->
