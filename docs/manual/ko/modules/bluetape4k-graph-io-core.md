# bluetape4k-graph-io-core

## 선택 기준

이 모듈은 형식에 독립적인 importer/exporter 계약, record, option, report, progress, path source/sink, 외부 ID mapping을 정의한다. 새 파일 형식을 구현하거나 공통 report 타입이 필요할 때 선택한다. 실제 파일을 읽으려면 CSV, Jackson, GraphML 중 하나를 함께 고른다. 근거는 [GraphBulkImporter.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphBulkImporter.kt)다.

## 의존성과 실행

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-io-core")
}
```

```kotlin
val options = GraphImportOptions(
    batchSize = 500,
    onDuplicateVertexId = DuplicateVertexPolicy.FAIL,
    onMissingEdgeEndpoint = MissingEndpointPolicy.FAIL,
)
val report = Jackson3NdJsonBulkImporter().use {
    it.importGraph(GraphImportSource.PathSource(Path.of("graph.ndjson")), operations, options)
}
check(report.status == GraphIoStatus.COMPLETED)
```

예상 결과는 외부 ID로 간선 끝점을 찾고, 생성 수와 실패 정보를 report로 받는 것이다.

## 동작과 자원

외부 문자열 ID는 교환 파일 안에서만 정점과 간선을 연결한다. 실제 `GraphElementId` 형식은 보장하지 않는다. mapping 근거는 [GraphIoExternalIdMap.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/support/GraphIoExternalIdMap.kt)다.

동기 API는 호출 thread를 막고, virtual thread API는 blocking 작업을 future로 감싸며, suspend API는 coroutine 취소를 전달한다. 어느 방식도 여러 batch를 하나의 transaction으로 만들지 않는다. path 기반 stream은 library가 열고 닫는다.

## 확인과 문제 해결

```bash
./gradlew :bluetape4k-graph-io-core:test --tests '*GraphIoExternalIdMapTest' --tests '*VirtualThreadGraphBulkAdapterTest'
```

예상 결과는 중복 ID·끝점 정책과 실행 adapter 검증이 통과하는 것이다. report의 읽은 수, 생성 수, 건너뛴 수, failure phase와 실제 graph 수를 함께 비교한다.

## 관련 문서와 하지 않는 일

[실행 방식](../graph-io/execution-model.md), [파일 형식과 외부 ID](../graph-io/formats.md), [실패와 취소](../guides/failure-and-cancellation.md)를 참고한다. core는 파일 형식을 정하거나 이미 기록한 batch를 rollback하지 않는다.
