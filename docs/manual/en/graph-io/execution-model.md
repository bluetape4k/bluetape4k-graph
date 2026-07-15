# graph-io execution model

graph-io separates the data contract from execution. `GraphBulkImporter`/`GraphBulkExporter` are synchronous; `GraphVirtualThreadBulkImporter`/`Exporter` isolate blocking work on virtual threads; suspend variants fit coroutine scopes. Contracts: [`GraphBulkImporter.kt`](../../../../graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphBulkImporter.kt), [`GraphSuspendBulkImporter.kt`](../../../../graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphSuspendBulkImporter.kt), [`GraphVirtualThreadBulkImporter.kt`](../../../../graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphVirtualThreadBulkImporter.kt).

Choose sync for a bounded blocking job, virtual threads for many independent blocking transfers, and suspend for coroutine-owned cancellation. None removes backend limits or makes a codec nonblocking.

`GraphImportOptions` and `GraphExportOptions` control batch/label behavior; reports and progress objects expose observed counts and timing. Inspect [`GraphImportOptions.kt`](../../../../graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/options/GraphImportOptions.kt) and [`GraphImportReport.kt`](../../../../graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/report/GraphImportReport.kt).

On cancellation or failure, compare report counts with backend counts and inspect partial writes. The virtual-thread adapter contract is exercised by [`VirtualThreadGraphBulkAdapterTest.kt`](../../../../graph-io/core/src/test/kotlin/io/bluetape4k/graph/io/support/VirtualThreadGraphBulkAdapterTest.kt).
