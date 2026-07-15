# graph-io 실행 모델

graph-io는 자료 계약과 실행 방식을 분리한다. `GraphBulkImporter`/`GraphBulkExporter`는 동기 방식, `GraphVirtualThreadBulkImporter`/`Exporter`는 blocking 작업을 virtual thread에서 실행하는 방식, suspend 계열은 코루틴 범위에 맞춘 방식이다. 계약 소스: [`GraphBulkImporter.kt`](../../../../graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphBulkImporter.kt), [`GraphSuspendBulkImporter.kt`](../../../../graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphSuspendBulkImporter.kt), [`GraphVirtualThreadBulkImporter.kt`](../../../../graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphVirtualThreadBulkImporter.kt).

범위가 분명한 blocking 작업에는 동기 방식을 쓴다. 서로 독립된 blocking 전송이 많으면 virtual thread를 검토하고, 취소를 코루틴 범위가 책임지면 suspend 방식을 고른다. 어떤 방식을 골라도 백엔드 제한이 사라지거나 codec이 저절로 non-blocking이 되지는 않는다.

`GraphImportOptions`와 `GraphExportOptions`로 batch와 label 범위를 정하고, report와 progress에서 실제 처리량과 시간을 읽는다. [`GraphImportOptions.kt`](../../../../graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/options/GraphImportOptions.kt), [`GraphImportReport.kt`](../../../../graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/report/GraphImportReport.kt)를 참고한다.

취소나 실패가 나면 report 수치와 백엔드의 실제 수를 비교해 부분 반영 여부를 확인한다. virtual thread 경계는 [`VirtualThreadGraphBulkAdapterTest.kt`](../../../../graph-io/core/src/test/kotlin/io/bluetape4k/graph/io/support/VirtualThreadGraphBulkAdapterTest.kt)가 검증한다.
