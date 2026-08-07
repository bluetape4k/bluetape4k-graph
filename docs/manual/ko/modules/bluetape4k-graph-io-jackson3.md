# bluetape4k-graph-io-jackson3


실행 방식: **release test fixture 연계형**이다. `sourceOps`, `targetOps`, 임시 NDJSON path는 `Jackson3RoundTripTest`가 소유하며 두 그래프의 데이터 준비와 종료까지 맡는다.

## 실행 전 준비

Jackson 3 의존성 계열에서 NDJSON 그래프 파일을 읽고 쓸 때 선택한다. 새 Jackson 3 연동에는 이 모듈이 알맞다. 애플리케이션과 mapper 확장이 Jackson 2에 남아 있으면 Jackson 2 모듈을 유지한다. 구현은 [Jackson3NdJsonBulkImporter.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/jackson3/src/main/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3NdJsonBulkImporter.kt)다.

## 실행

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

## 기대 결과

예상 결과는 문서 전체를 메모리에 올리지 않고 NDJSON을 순서대로 처리하는 것이다.

## 형식과 자원

한 줄은 정점 또는 간선 envelope 하나다. `from`과 `to`는 외부 ID이며 실제 그래프 ID 형식을 보장하지 않는다. 0.6.0 테스트는 Jackson 2/3 파일 호환을 고정한다. 간선 buffer에는 한도가 있다. path stream은 library가 닫고, 외부 stream은 기본적으로 호출자가 닫는다. 취소나 뒤쪽 parse 실패 뒤에는 앞선 batch가 남을 수 있다.

## 운영 점검

- report 수와 실제 그래프 수를 비교한다.
- 파일 형식, batch 크기, 정책, 실패 단계를 기록한다.
- 입력 크기와 buffer 상한을 둔다.
- 문서에 정한 경계에서 library 소유 path와 호출자 소유 stream을 닫는다.

## 실패와 복구

증상: mapper/property 오류나 찾지 못한 끝점 때문에 일부만 처리된다. 문제가 난 줄을 보존하고 대상 그래프를 비운 뒤 envelope 또는 mapper 호환성을 고쳐 다시 실행한다.

```bash
./gradlew :bluetape4k-graph-io-jackson3:test --tests '*Jackson3RoundTripTest' --tests '*Jackson3EdgeBufferOverflowTest' --tests '*NdJsonCompatibilityTest'
```

예상 결과는 자체 왕복과 Jackson 2 호환이 통과하고 buffer 초과가 제한된 실패로 보고되는 것이다. 줄 번호, envelope type, mapper/property 오류, 중복 ID, 끝점, report phase를 기록한다.

## 완전한 release 예제

고정된 [Jackson3RoundTripTest](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/jackson3/src/test/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3RoundTripTest.kt)가 모든 fixture 변수를 정의한 완전한 실행 예제다. 다음 명령으로 확인한다.

```bash
./gradlew :bluetape4k-graph-io-jackson3:test --tests '*Jackson3RoundTripTest'
```

예상 결과는 왕복 또는 실패 경로 검증이 통과하고 fixture 소유 자원이 모두 닫히는 것이다.

## 하지 않는 일과 관련 문서

[파일 형식과 외부 ID](../graph-io/formats.md), [실행 방식](../graph-io/execution-model.md), [Jackson 2 모듈](bluetape4k-graph-io-jackson2.md)을 참고한다. 이 모듈은 사용자 mapper의 의미까지 같다고 보장하거나 import를 원자적으로 만들지 않는다.
