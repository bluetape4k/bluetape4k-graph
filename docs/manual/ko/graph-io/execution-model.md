# graph-io 실행 모델

![graph-io 처리 흐름](../../assets/graph-io/graph-io-pipeline.png)

graph-io는 자료 계약과 실행 방식을 분리한다. `GraphBulkImporter`/`GraphBulkExporter`는 동기 방식, `GraphVirtualThreadBulkImporter`/`Exporter`는 blocking 작업을 virtual thread에서 실행하는 방식, suspend 계열은 코루틴 범위에 맞춘 방식이다. 계약 소스: [`GraphBulkImporter.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphBulkImporter.kt), [`GraphSuspendBulkImporter.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphSuspendBulkImporter.kt), [`GraphVirtualThreadBulkImporter.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphVirtualThreadBulkImporter.kt).

범위가 분명한 blocking 작업에는 동기 방식을 쓴다. 서로 독립된 blocking 전송이 많으면 virtual thread를 검토하고, 취소를 코루틴 범위가 책임지면 suspend 방식을 고른다. 어떤 방식을 골라도 백엔드 제한이 사라지거나 codec이 저절로 non-blocking이 되지는 않는다.

`GraphImportOptions`와 `GraphExportOptions`로 batch와 label 범위를 정하고, report와 progress에서 실제 처리량과 시간을 읽는다. [`GraphImportOptions.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/options/GraphImportOptions.kt), [`GraphImportReport.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/report/GraphImportReport.kt)를 참고한다.

취소나 실패가 나면 report 수치와 백엔드의 실제 수를 비교해 부분 반영 여부를 확인한다. virtual thread 경계는 [`VirtualThreadGraphBulkAdapterTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/core/src/test/kotlin/io/bluetape4k/graph/io/support/VirtualThreadGraphBulkAdapterTest.kt)가 검증한다.

## 세 실행 경로를 같은 입력으로 돌린다

```kotlin
val source = GraphImportSource.PathSource(Paths.get("graph.ndjson"))
val options = GraphImportOptions(batchSize = 100)

val syncReport = Jackson3NdJsonBulkImporter().importGraph(source, syncOps, options)
val virtualReport = Jackson3NdJsonVirtualThreadBulkImporter()
    .importGraphAsync(source, syncOps, options).join()
val suspendReport = SuspendJackson3NdJsonBulkImporter()
    .importGraphSuspending(source, suspendOps, options)
```

정점 둘과 간선 하나가 든 파일이라면 `verticesCreated == 2`, `edgesCreated == 1`, `status == COMPLETED`가 나와야 한다. `verticesRead`, 건너뛴 수, `elapsed`, failure phase도 함께 기록한다. future가 정상 완료돼도 report가 `PARTIAL`일 수 있다.

```bash
./gradlew :bluetape4k-graph-io-jackson3:test --tests '*Jackson3RoundTripTest' --tests '*Jackson3VirtualThreadTest' --tests '*Jackson3SuspendTest'
```

## 취소 뒤 상태를 진단한다

첫 batch 뒤 코루틴이나 future를 취소하고 백엔드의 실제 개수와 report를 비교한다. 취소 자체는 트랜잭션이 아니므로 이미 flush한 batch가 남을 수 있다. `CancellationException`·`CompletionException`과 정상 반환된 `FAILED`·`PARTIAL` report를 나눠 본 뒤, 외부 ID로 이어서 가져올지 대상 graph를 복원할지 결정한다.
