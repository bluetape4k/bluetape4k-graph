# bluetape4k-graph-io-core

## What it provides and when to use it

This module defines format-neutral import/export contracts, record models, options, reports, progress, path sources/sinks, and external-ID mapping. Use it to implement a format or to depend on shared report types. Choose a concrete format module for actual files. Contracts: [GraphBulkImporter.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphBulkImporter.kt) and [GraphBulkExporter.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphBulkExporter.kt).

## Dependency and API

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-io-core")
}
```

`GraphIoVertexRecord` and `GraphIoEdgeRecord` carry external string IDs. Importers map them to backend `GraphElementId` values with [GraphIoExternalIdMap.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/support/GraphIoExternalIdMap.kt). They are interchange identities, not promises about backend IDs.

```kotlin
val options = GraphImportOptions(
    batchSize = 500,
    onDuplicateVertexId = DuplicateVertexPolicy.FAIL,
    onMissingEdgeEndpoint = MissingEndpointPolicy.FAIL,
)
val report = Jackson3NdJsonBulkImporter().use {
    it.importGraph(GraphImportSource.PathSource(Path.of("graph.ndjson")), operations, options)
}
check(report.status == GraphIoStatus.COMPLETED)
```

Expected: the concrete importer reads records, resolves edge endpoints through external IDs, and returns counts plus failures.

## Execution, records, and ownership

Sync contracts block. Virtual-thread contracts return futures around blocking work. Suspend contracts preserve coroutine cancellation. None makes backend writes transactional. A failed later batch can leave earlier batches durable.

`PathSource` and `PathSink` are opened and closed by the format implementation. Stream-based sources/sinks follow their explicit ownership flag; callers must not assume closure. Import/export objects are `AutoCloseable`.

## Failures and operations

Duplicate external IDs, missing endpoints, malformed records, unsupported property values, cancellation, and backend write failures are separate phases in the report. Compare `verticesRead`/`Created`, `edgesRead`/`Created`, skipped counts, status, failures, and durable backend counts.

```bash
./gradlew :bluetape4k-graph-io-core:test --tests '*GraphIoExternalIdMapTest' --tests '*VirtualThreadGraphBulkAdapterTest'
```

Expected: external-ID policy and execution adapters pass without a concrete file format. If a format-only test fails, diagnose its codec rather than core.

## Related pages and non-goals

See [execution model](../graph-io/execution-model.md), [formats](../graph-io/formats.md), and [failure/cancellation](../guides/failure-and-cancellation.md). Core does not define a wire format, infer resumability, own a database, or roll back previously flushed batches.
