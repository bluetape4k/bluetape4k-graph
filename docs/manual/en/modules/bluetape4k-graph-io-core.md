# bluetape4k-graph-io-core

## Before you run

This module defines format-neutral import/export contracts, record models, options, reports, progress, path sources/sinks, and external-ID mapping. Use it to implement a format or to depend on shared report types. Choose a concrete format module for actual files. Contracts: [GraphBulkImporter.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphBulkImporter.kt) and [GraphBulkExporter.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphBulkExporter.kt).


Execution mode: **release-fixture linked**. The snippet uses the Jackson 3 implementation and a fixture-provided `operations: GraphOperations`; the linked round-trip test constructs source/target operations, seeds records, owns the temporary path, and closes every resource.

## Run

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-io-core")
    implementation("io.github.bluetape4k:bluetape4k-graph-io-jackson3") // executable format implementation
}
```

`GraphIoVertexRecord` and `GraphIoEdgeRecord` carry external string IDs. Importers map them to backend `GraphElementId` values with [GraphIoExternalIdMap.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/support/GraphIoExternalIdMap.kt). They are interchange identities, not promises about backend IDs.

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

## Expected result

Expected: the concrete importer reads records, resolves edge endpoints through external IDs, and returns counts plus failures.

## Execution, records, and ownership

Sync contracts block. Virtual-thread contracts return futures around blocking work. Suspend contracts preserve coroutine cancellation. None makes backend writes transactional. A failed later batch can leave earlier batches durable.

`PathSource` and `PathSink` are opened and closed by the format implementation. Stream-based sources/sinks follow their explicit ownership flag; callers must not assume closure. Import/export objects are `AutoCloseable`.

## Operations checklist

- Compare report counts with durable graph counts.
- Record the format, batch size, policy, and failed phase.
- Set input size and buffering limits.
- Close library-owned paths and caller-owned streams at their documented boundary.

## Failure and recovery

Symptom: the report is `PARTIAL`/`FAILED` or durable counts differ. Stop retries, inspect `failures.phase`, restore or clear the target graph, then rerun from a known external-ID boundary.

Duplicate external IDs, missing endpoints, malformed records, unsupported property values, cancellation, and backend write failures are separate phases in the report. Compare `verticesRead`/`Created`, `edgesRead`/`Created`, skipped counts, status, failures, and durable backend counts.

```bash
./gradlew :bluetape4k-graph-io-core:test --tests '*GraphIoExternalIdMapTest' --tests '*VirtualThreadGraphBulkAdapterTest'
```

Expected: external-ID policy and execution adapters pass without a concrete file format. If a format-only test fails, diagnose its codec rather than core.

## Complete release example

The pinned [Jackson3RoundTripTest](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/jackson3/src/test/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3RoundTripTest.kt) defines every fixture variable and is the complete executable release example. Run:

```bash
./gradlew :bluetape4k-graph-io-jackson3:test --tests '*Jackson3RoundTripTest'
```

Expected: the round trip or negative-path assertions pass and all fixture-owned resources are closed.

## Non-goals and related guides

See [execution model](../graph-io/execution-model.md), [formats](../graph-io/formats.md), and [failure/cancellation](../guides/failure-and-cancellation.md). Core does not define a wire format, infer resumability, own a database, or roll back previously flushed batches.
