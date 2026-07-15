# bluetape4k-graph-io-jackson2

## 선택 기준

Jackson 2를 쓰는 애플리케이션에서 release NDJSON envelope를 읽고 쓸 때 선택한다. Jackson 2용 mapper 확장이 이미 있다면 이 모듈이 맞다. Jackson 3 애플리케이션에서는 Jackson 3 모듈을 고르고, 특별한 호환 이유 없이 두 계열을 함께 넣지 않는다. 구현은 [Jackson2NdJsonBulkImporter.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph-io/jackson2/src/main/kotlin/io/bluetape4k/graph/io/jackson2/Jackson2NdJsonBulkImporter.kt)다.

## 의존성과 실행

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-io-jackson2")
}
```

```kotlin
val path = Path.of("graph.ndjson")
val out = Jackson2NdJsonBulkExporter().use {
    it.exportGraph(GraphExportSink.PathSink(path), sourceOps, GraphExportOptions())
}
val input = Jackson2NdJsonBulkImporter().use {
    it.importGraph(GraphImportSource.PathSource(path), targetOps, GraphImportOptions())
}
check(out.edgesWritten == input.edgesCreated)
```

예상 결과는 한 줄에 정점이나 간선 하나가 기록되고 외부 ID로 끝점이 다시 연결되는 것이다.

## 형식과 자원

각 줄에는 `type`, `id`, `label`, property, 간선의 `from`과 `to`가 있다. 간선은 참조 정점이 만들어질 때까지 제한된 buffer에 쌓인다. buffer 한도를 넘으면 쓰기 전에 실패한다. path stream은 library가 닫고, 외부 stream은 소유권 flag가 없으면 호출자가 닫는다. 뒤쪽 줄이 실패하면 앞서 기록한 batch는 남을 수 있다.

## 확인과 문제 해결

```bash
./gradlew :bluetape4k-graph-io-jackson2:test --tests '*Jackson2RoundTripTest' --tests '*Jackson2EdgeBufferOverflowTest' --tests '*NdJsonCompatibilityTest'
```

예상 결과는 왕복, Jackson 3 파일 호환, buffer 한도 검증이 통과하는 것이다. 줄 번호, envelope type, 중복 ID, 찾지 못한 끝점, property 변환, report phase를 확인한다.

## 관련 문서와 하지 않는 일

[파일 형식과 외부 ID](../graph-io/formats.md), [실행 방식](../graph-io/execution-model.md), [Jackson 3 모듈](bluetape4k-graph-io-jackson3.md)을 참고한다. 이 모듈은 임의 mapper 설정을 변환하거나 파일 전체를 하나의 transaction으로 만들지 않는다.
