# bluetape4k-graph-io-graphml


실행 방식: **release test fixture 연계형**이다. `sourceOps`, `targetOps`, 임시 GraphML path는 `GraphMlRoundTripTest`가 만들고 닫는다. 이 fixture가 strict/skip 동작도 고정한다.

## 실행 전 준비

GraphML 모듈은 StAX로 방향 property 그래프의 제한된 범위를 처리한다. node, 방향 edge, scalar key/data를 주고받는 도구와 연결할 때 선택한다. 무방향 그래프, 중첩 그래프, hyperedge, port, vendor XML 확장을 모두 보존해야 하면 피한다. 구현은 [GraphMlBulkImporter.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/graphml/src/main/kotlin/io/bluetape4k/graph/io/graphml/GraphMlBulkImporter.kt)다.

## 실행

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-io-graphml")
}
```

```kotlin
val path = Path.of("graph.graphml")
val out = GraphMlBulkExporter().use {
    it.exportGraph(GraphExportSink.PathSink(path), sourceOps, GraphExportOptions(), GraphMlExportOptions())
}
val input = GraphMlBulkImporter().use {
    it.importGraph(
        GraphImportSource.PathSource(path), targetOps, GraphImportOptions(),
        GraphMlImportOptions(defaultVertexLabel = "Vertex", defaultEdgeLabel = "EDGE"),
    )
}
check(out.edgesWritten == input.edgesCreated)
```

## 기대 결과

예상 결과는 DOM을 만들지 않고 방향 그래프와 scalar property를 왕복하는 것이다.

## 형식과 자원

node `id`는 외부 ID이고 edge의 `source`와 `target`이 이를 참조한다. strict 정책은 지원하지 않는 구조에서 실패하고, skip 정책은 경고를 남기며 일부 방향을 투영할 수 있다. 경고 없는 완전 보존으로 오해하면 안 된다. path 입력은 library가 닫는다. 뒤쪽 오류 전에 기록한 batch는 남을 수 있다.

## 운영 점검

- report 수와 실제 그래프 수를 비교한다.
- 파일 형식, batch 크기, 정책, 실패 단계를 기록한다.
- 입력 크기와 buffer 상한을 둔다.
- 문서에 정한 경계에서 library 소유 path와 호출자 소유 stream을 닫는다.

## 실패와 복구

증상: XML parse 또는 strict 호환성 검사에서 쓰기 전에 실패한다. parser 위치와 정책을 보존하고 위험하거나 지원하지 않는 구조를 제거한다. skip 정책을 쓸 때는 경고를 검토한 뒤 빈 대상에서 다시 실행한다.

```bash
./gradlew :bluetape4k-graph-io-graphml:test --tests '*GraphMlRoundTripTest' --tests '*StaxGraphMlReaderWriterTest' --tests '*CrossFormatGraphMlTest'
```

예상 결과는 왕복, XXE 차단, 지원하지 않는 fixture 정책, 다른 형식과의 수량 비교가 통과하는 것이다. DTD·외부 entity, 잘못된 XML, 중복 node ID, 끝점 누락, unknown key, 중첩 그래프를 확인한다.

## 완전한 release 예제

고정된 [GraphMlRoundTripTest](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/graphml/src/test/kotlin/io/bluetape4k/graph/io/graphml/GraphMlRoundTripTest.kt)가 모든 fixture 변수를 정의한 완전한 실행 예제다. 다음 명령으로 확인한다.

```bash
./gradlew :bluetape4k-graph-io-graphml:test --tests '*GraphMlRoundTripTest'
```

예상 결과는 왕복 또는 실패 경로 검증이 통과하고 fixture 소유 자원이 모두 닫히는 것이다.

## 하지 않는 일과 관련 문서

[파일 형식과 외부 ID](../graph-io/formats.md), [OkIO 보안](../graph-io/okio-security.md), [실패와 취소](../guides/failure-and-cancellation.md)를 참고한다. 이 모듈은 모든 GraphML 확장을 보존하거나 무방향 edge를 임의로 두 개 만들지 않는다.
