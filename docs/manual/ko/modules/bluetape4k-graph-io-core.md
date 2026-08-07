# bluetape4k-graph-io-core


실행 방식: **release test fixture 연계형**이다. snippet은 Jackson 3 구현과 fixture가 제공하는 `operations: GraphOperations`를 쓴다. 아래 왕복 테스트가 source/target operations와 임시 path를 만들고 모두 닫는다.

## 실행 전 준비

이 모듈은 형식에 독립적인 가져오기·내보내기 계약, 레코드, 옵션, 보고서, 진행 상태, 경로 기반 입출력, 외부 ID 매핑을 정의한다. 새 파일 형식을 구현하거나 공통 보고서 타입이 필요할 때 선택한다. 실제 파일을 읽으려면 CSV, Jackson, GraphML 중 하나를 함께 고른다. 근거는 [GraphBulkImporter.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphBulkImporter.kt)다.

## 실행

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-io-core")
    implementation("io.github.bluetape4k:bluetape4k-graph-io-jackson3") // executable format implementation
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

## 기대 결과

예상 결과는 외부 ID로 간선 끝점을 찾고, 생성 수와 실패 정보를 report로 받는 것이다.

## 동작과 자원

외부 문자열 ID는 교환 파일 안에서만 정점과 간선을 연결한다. 실제 `GraphElementId` 형식은 보장하지 않는다. mapping 근거는 [GraphIoExternalIdMap.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/support/GraphIoExternalIdMap.kt)다.

동기 API는 호출 thread를 막고, virtual thread API는 blocking 작업을 future로 감싸며, suspend API는 coroutine 취소를 전달한다. 어느 방식도 여러 batch를 하나의 트랜잭션으로 만들지 않는다. path 기반 stream은 library가 열고 닫는다.

## 운영 점검

- report 수와 실제 그래프 수를 비교한다.
- 파일 형식, batch 크기, 정책, 실패 단계를 기록한다.
- 입력 크기와 buffer 상한을 둔다.
- 문서에 정한 경계에서 library 소유 path와 호출자 소유 stream을 닫는다.

## 실패와 복구

증상: report가 `PARTIAL`/`FAILED`이거나 실제 수량이 다르다. 재시도를 멈추고 `failures.phase`를 확인한 뒤 대상 그래프를 복구하거나 비우고, 확인된 외부 ID 경계부터 다시 실행한다.

```bash
./gradlew :bluetape4k-graph-io-core:test --tests '*GraphIoExternalIdMapTest' --tests '*VirtualThreadGraphBulkAdapterTest'
```

예상 결과는 중복 ID·끝점 정책과 실행 adapter 검증이 통과하는 것이다. report의 읽은 수, 생성 수, 건너뛴 수, failure phase와 실제 그래프 수를 함께 비교한다.

## 완전한 release 예제

고정된 [Jackson3RoundTripTest](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/jackson3/src/test/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3RoundTripTest.kt)가 모든 fixture 변수를 정의한 완전한 실행 예제다. 다음 명령으로 확인한다.

```bash
./gradlew :bluetape4k-graph-io-jackson3:test --tests '*Jackson3RoundTripTest'
```

예상 결과는 왕복 또는 실패 경로 검증이 통과하고 fixture 소유 자원이 모두 닫히는 것이다.

## 하지 않는 일과 관련 문서

[실행 방식](../graph-io/execution-model.md), [파일 형식과 외부 ID](../graph-io/formats.md), [실패와 취소](../guides/failure-and-cancellation.md)를 참고한다. core는 파일 형식을 정하거나 이미 기록한 batch를 rollback하지 않는다.
