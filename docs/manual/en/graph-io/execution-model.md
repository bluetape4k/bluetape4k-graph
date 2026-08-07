# graph-io execution model

![graph-io pipeline](../../assets/graph-io/graph-io-pipeline.png)

graph-io separates the data contract from execution. `GraphBulkImporter`/`GraphBulkExporter` are synchronous; `GraphVirtualThreadBulkImporter`/`Exporter` isolate blocking work on virtual threads; suspend variants fit coroutine scopes. Contracts: [`GraphBulkImporter.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphBulkImporter.kt), [`GraphSuspendBulkImporter.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphSuspendBulkImporter.kt), [`GraphVirtualThreadBulkImporter.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphVirtualThreadBulkImporter.kt).

Choose sync for a bounded blocking job, virtual threads for many independent blocking transfers, and suspend for coroutine-owned cancellation. None removes backend limits or makes a codec nonblocking.

`GraphImportOptions` and `GraphExportOptions` control batch/label behavior; reports and progress objects expose observed counts and timing. Inspect [`GraphImportOptions.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/options/GraphImportOptions.kt) and [`GraphImportReport.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/report/GraphImportReport.kt).

On cancellation or failure, compare report counts with backend counts and inspect partial writes. The virtual-thread adapter contract is exercised by [`VirtualThreadGraphBulkAdapterTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/core/src/test/kotlin/io/bluetape4k/graph/io/support/VirtualThreadGraphBulkAdapterTest.kt).

## Invoke each execution path

All three paths use the same `GraphImportOptions`; only ownership and completion differ.

```kotlin
val source = GraphImportSource.PathSource(Paths.get("graph.ndjson"))
val options = GraphImportOptions(batchSize = 100)

val syncReport = Jackson3NdJsonBulkImporter()
    .importGraph(source, syncOps, options)

val future = Jackson3NdJsonVirtualThreadBulkImporter()
    .importGraphAsync(source, syncOps, options)
val virtualReport = future.join()

val suspendReport = SuspendJackson3NdJsonBulkImporter()
    .importGraphSuspending(source, suspendOps, options)
```

For a file containing two vertices and one edge, expect `verticesCreated == 2`, `edgesCreated == 1`, and `status == COMPLETED`. Log `verticesRead`, `skippedVertices`, `skippedEdges`, `elapsed`, and each failure phase; a completed future does not imply a completed report.

```bash
./gradlew :bluetape4k-graph-io-jackson3:test --tests '*Jackson3RoundTripTest' --tests '*Jackson3VirtualThreadTest' --tests '*Jackson3SuspendTest'
```

## Diagnose cancellation and partial writes

Cancel the coroutine or future after the first batch and compare durable backend counts with the report. Cancellation is not a transaction: already flushed batches may remain. Diagnose `CancellationException`/`CompletionException` separately from a `FAILED` or `PARTIAL` report, then decide whether to resume with external IDs or restore the target graph.
