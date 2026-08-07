# bluetape4k-graph-io-csv


실행 방식: **release test fixture 연계형**이다. `sourceOps`와 `targetOps`는 `CsvRoundTripTest`가 따로 만든 operations다. 이 테스트가 정점·간선 임시 path를 만들고 두 그래프를 모두 닫는다.

## 실행 전 준비

CSV는 정점 파일과 간선 파일을 한 쌍으로 다룬다. 표 형태 교환, 사람이 직접 확인하는 자료, 고정 column schema에 알맞다. 한 파일로 원자적으로 전달해야 하거나 중첩 property를 그대로 보존해야 하면 다른 형식을 고른다. 구현은 [CsvGraphBulkImporter.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/csv/src/main/kotlin/io/bluetape4k/graph/io/csv/CsvGraphBulkImporter.kt)다.

## 실행

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-io-csv")
}
```

```kotlin
val sink = CsvGraphExportSink(
    GraphExportSink.PathSink(Path.of("vertices.csv")),
    GraphExportSink.PathSink(Path.of("edges.csv")),
)
val out = CsvGraphBulkExporter().use {
    it.exportGraph(sink, sourceOps, GraphExportOptions(setOf("Person"), setOf("KNOWS")))
}
val source = CsvGraphImportSource(
    GraphImportSource.PathSource(Path.of("vertices.csv")),
    GraphImportSource.PathSource(Path.of("edges.csv")),
)
val input = CsvGraphBulkImporter().use {
    it.importGraph(source, targetOps, GraphImportOptions())
}
check(out.verticesWritten == input.verticesCreated)
check(out.edgesWritten == input.edgesCreated)
```

## 기대 결과

예상 결과는 두 파일을 함께 읽어 같은 정점·간선 수를 만드는 것이다.

## 형식과 자원

정점 행의 외부 ID를 간선 행이 참조한다. 두 파일은 함께 공개하고 보관해야 한다. column 이름, delimiter, charset, quoting, property mode는 [CsvGraphIoOptions.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/csv/src/main/kotlin/io/bluetape4k/graph/io/csv/CsvGraphIoOptions.kt)의 계약이다. path 파일은 library가 열고 닫는다. 정점 import 뒤 간선 파일이 실패하면 정점은 남을 수 있다.

## 운영 점검

- report 수와 실제 그래프 수를 비교한다.
- 파일 형식, batch 크기, 정책, 실패 단계를 기록한다.
- 입력 크기와 buffer 상한을 둔다.
- 문서에 정한 경계에서 library 소유 path와 호출자 소유 stream을 닫는다.

## 실패와 복구

증상: 정점은 들어갔지만 간선이 실패한다. 두 파일을 함께 보존하고 처음 누락된 끝점이나 잘못된 행을 찾은 뒤, 일부 기록된 대상을 비우고 파일 쌍을 고쳐 다시 실행한다.

```bash
./gradlew :bluetape4k-graph-io-csv:test --tests '*CsvRoundTripTest' --tests '*CsvEdgeCaseTest' --tests '*CsvImportErrorTest'
```

예상 결과는 왕복 검증이 통과하고 잘못된 header·quoting·끝점이 설정한 정책대로 처리되는 것이다. 두 파일 누락, 중복 ID, charset, delimiter 충돌, report phase를 순서대로 확인한다. 한 파일만 암호화하지 않는다.

## 완전한 release 예제

고정된 [CsvRoundTripTest](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/csv/src/test/kotlin/io/bluetape4k/graph/io/csv/CsvRoundTripTest.kt)가 모든 fixture 변수를 정의한 완전한 실행 예제다. 다음 명령으로 확인한다.

```bash
./gradlew :bluetape4k-graph-io-csv:test --tests '*CsvRoundTripTest'
```

예상 결과는 왕복 또는 실패 경로 검증이 통과하고 fixture 소유 자원이 모두 닫히는 것이다.

## 하지 않는 일과 관련 문서

[파일 형식과 외부 ID](../graph-io/formats.md), [실행 방식](../graph-io/execution-model.md), [OkIO 보안](../graph-io/okio-security.md)을 참고한다. CSV는 두 파일 공개를 원자적으로 만들거나 임의의 중첩 값을 자동 보존하지 않는다.
