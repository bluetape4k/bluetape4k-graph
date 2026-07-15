# bluetape4k-graph-io-graphml

## 선택 기준

GraphML 모듈은 StAX로 방향 property graph의 제한된 범위를 처리한다. node, 방향 edge, scalar key/data를 주고받는 도구와 연결할 때 선택한다. 무방향 graph, 중첩 graph, hyperedge, port, vendor XML 확장을 모두 보존해야 하면 피한다. 구현은 [GraphMlBulkImporter.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph-io/graphml/src/main/kotlin/io/bluetape4k/graph/io/graphml/GraphMlBulkImporter.kt)다.

## 의존성과 실행

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

예상 결과는 DOM을 만들지 않고 방향 graph와 scalar property를 왕복하는 것이다.

## 형식과 자원

node `id`는 외부 ID이고 edge의 `source`와 `target`이 이를 참조한다. strict 정책은 지원하지 않는 구조에서 실패하고, skip 정책은 경고를 남기며 일부 방향을 투영할 수 있다. 경고 없는 완전 보존으로 오해하면 안 된다. path 입력은 library가 닫는다. 뒤쪽 오류 전에 기록한 batch는 남을 수 있다.

## 확인과 문제 해결

```bash
./gradlew :bluetape4k-graph-io-graphml:test --tests '*GraphMlRoundTripTest' --tests '*StaxGraphMlReaderWriterTest' --tests '*CrossFormatGraphMlTest'
```

예상 결과는 왕복, XXE 차단, 지원하지 않는 fixture 정책, 다른 형식과의 수량 비교가 통과하는 것이다. DTD·외부 entity, 잘못된 XML, 중복 node ID, 끝점 누락, unknown key, 중첩 graph를 확인한다.

## 관련 문서와 하지 않는 일

[파일 형식과 외부 ID](../graph-io/formats.md), [OkIO 보안](../graph-io/okio-security.md), [실패와 취소](../guides/failure-and-cancellation.md)를 참고한다. 이 모듈은 모든 GraphML 확장을 보존하거나 무방향 edge를 임의로 두 개 만들지 않는다.
