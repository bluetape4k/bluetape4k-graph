# bluetape4k-graph-io-jackson3

## 선택 기준

Jackson 3 의존성 계열에서 NDJSON graph 파일을 읽고 쓸 때 선택한다. 새 Jackson 3 연동에는 이 모듈이 알맞다. 애플리케이션과 mapper 확장이 Jackson 2에 남아 있으면 Jackson 2 모듈을 유지한다. 구현은 [Jackson3NdJsonBulkImporter.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph-io/jackson3/src/main/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3NdJsonBulkImporter.kt)다.

## 의존성과 실행

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-io-jackson3")
}
```

```kotlin
val path = Path.of("graph.ndjson")
val out = Jackson3NdJsonBulkExporter().use {
    it.exportGraph(GraphExportSink.PathSink(path), sourceOps, GraphExportOptions())
}
val input = Jackson3NdJsonBulkImporter().use {
    it.importGraph(GraphImportSource.PathSource(path), targetOps, GraphImportOptions())
}
check(out.verticesWritten == input.verticesCreated)
```

예상 결과는 문서 전체를 메모리에 올리지 않고 NDJSON을 순서대로 처리하는 것이다.

## 형식과 자원

한 줄은 정점 또는 간선 envelope 하나다. `from`과 `to`는 외부 ID이며 실제 graph ID 형식을 보장하지 않는다. 0.5.1 테스트는 Jackson 2/3 파일 호환을 고정한다. 간선 buffer에는 한도가 있다. path stream은 library가 닫고, 외부 stream은 기본적으로 호출자가 닫는다. 취소나 뒤쪽 parse 실패 뒤에는 앞선 batch가 남을 수 있다.

## 확인과 문제 해결

```bash
./gradlew :bluetape4k-graph-io-jackson3:test --tests '*Jackson3RoundTripTest' --tests '*Jackson3EdgeBufferOverflowTest' --tests '*NdJsonCompatibilityTest'
```

예상 결과는 자체 왕복과 Jackson 2 호환이 통과하고 buffer 초과가 제한된 실패로 보고되는 것이다. 줄 번호, envelope type, mapper/property 오류, 중복 ID, 끝점, report phase를 기록한다.

## 관련 문서와 하지 않는 일

[파일 형식과 외부 ID](../graph-io/formats.md), [실행 방식](../graph-io/execution-model.md), [Jackson 2 모듈](bluetape4k-graph-io-jackson2.md)을 참고한다. 이 모듈은 사용자 mapper의 의미까지 같다고 보장하거나 import를 원자적으로 만들지 않는다.
