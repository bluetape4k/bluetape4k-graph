# bluetape4k-graph-io-jackson2


실행 방식: **release test fixture 연계형**이다. `sourceOps`, `targetOps`, 임시 NDJSON path는 `Jackson2RoundTripTest`가 만들며, 정점·간선을 넣고 operations와 path 자원을 닫는다.

## 실행 전 준비

Jackson 2를 쓰는 애플리케이션에서 release NDJSON envelope를 읽고 쓸 때 선택한다. Jackson 2용 mapper 확장이 이미 있다면 이 모듈이 맞다. Jackson 3 애플리케이션에서는 Jackson 3 모듈을 고르고, 특별한 호환 이유 없이 두 계열을 함께 넣지 않는다. 구현은 [Jackson2NdJsonBulkImporter.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/jackson2/src/main/kotlin/io/bluetape4k/graph/io/jackson2/Jackson2NdJsonBulkImporter.kt)다.

## 실행

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

## 기대 결과

예상 결과는 한 줄에 정점이나 간선 하나가 기록되고 외부 ID로 끝점이 다시 연결되는 것이다.

## 형식과 자원

각 줄에는 `type`, `id`, `label`, property, 간선의 `from`과 `to`가 있다. 간선은 참조 정점이 만들어질 때까지 제한된 buffer에 쌓인다. buffer 한도를 넘으면 쓰기 전에 실패한다. path stream은 library가 닫고, 외부 stream은 소유권 flag가 없으면 호출자가 닫는다. 뒤쪽 줄이 실패하면 앞서 기록한 batch는 남을 수 있다.

## 운영 점검

- report 수와 실제 그래프 수를 비교한다.
- 파일 형식, batch 크기, 정책, 실패 단계를 기록한다.
- 입력 크기와 buffer 상한을 둔다.
- 문서에 정한 경계에서 library 소유 path와 호출자 소유 stream을 닫는다.

## 실패와 복구

증상: 특정 줄에서 parse가 멈추거나 edge buffer가 넘친다. 줄 번호와 envelope를 보존하고 입력 크기를 확인한 뒤에만 상한을 조정한다. 레코드를 고친 다음 빈 대상에서 다시 실행한다.

```bash
./gradlew :bluetape4k-graph-io-jackson2:test --tests '*Jackson2RoundTripTest' --tests '*Jackson2EdgeBufferOverflowTest' --tests '*NdJsonCompatibilityTest'
```

예상 결과는 왕복, Jackson 3 파일 호환, buffer 한도 검증이 통과하는 것이다. 줄 번호, envelope type, 중복 ID, 찾지 못한 끝점, property 변환, report phase를 확인한다.

## 완전한 release 예제

고정된 [Jackson2RoundTripTest](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/jackson2/src/test/kotlin/io/bluetape4k/graph/io/jackson2/Jackson2RoundTripTest.kt)가 모든 fixture 변수를 정의한 완전한 실행 예제다. 다음 명령으로 확인한다.

```bash
./gradlew :bluetape4k-graph-io-jackson2:test --tests '*Jackson2RoundTripTest'
```

예상 결과는 왕복 또는 실패 경로 검증이 통과하고 fixture 소유 자원이 모두 닫히는 것이다.

## 하지 않는 일과 관련 문서

[파일 형식과 외부 ID](../graph-io/formats.md), [실행 방식](../graph-io/execution-model.md), [Jackson 3 모듈](bluetape4k-graph-io-jackson3.md)을 참고한다. 이 모듈은 임의 mapper 설정을 변환하거나 파일 전체를 하나의 트랜잭션으로 만들지 않는다.
