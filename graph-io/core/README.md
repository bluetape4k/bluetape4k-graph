# graph-io-core

Shared contracts, models, options, reports, and I/O helpers for the `graph-io` family of bulk importers and exporters.

## Overview

`graph-io-core` defines the abstract interfaces and data types that every `graph-io-*` format module (CSV, Jackson2 NDJSON, Jackson3 NDJSON, GraphML) depends on. It intentionally has **no format- or backend-specific code** — its only job is to let every format implement the same contracts across three execution models: synchronous, Kotlin coroutine `suspend`, and Java Virtual Thread-based `CompletableFuture`.

This module is not usually consumed directly — applications depend on one of the format modules (e.g. `graph-io-csv`, `graph-io-jackson3`) which transitively expose these types.

## What's Inside

### Execution Model Contracts (`io.bluetape4k.graph.io.contract`)

Seven interfaces — one exporter and one importer per execution model, plus a flow-based raw reader:

| Interface | Method | Returns |
|-----------|--------|---------|
| `GraphBulkExporter<T>` | `exportGraph(sink, ops, options)` | `GraphExportReport` |
| `GraphBulkImporter<S>` | `importGraph(source, ops, options)` | `GraphImportReport` |
| `GraphSuspendBulkExporter<T>` | `suspend exportGraphSuspending(sink, suspendOps, options)` | `GraphExportReport` |
| `GraphSuspendBulkImporter<S>` | `suspend importGraphSuspending(source, suspendOps, options)` | `GraphImportReport` |
| `GraphVirtualThreadBulkExporter<T>` | `exportGraphAsync(sink, ops, options)` | `CompletableFuture<GraphExportReport>` |
| `GraphVirtualThreadBulkImporter<S>` | `importGraphAsync(source, ops, options)` | `CompletableFuture<GraphImportReport>` |
| `GraphRecordFlowReader<S>` | `readVertices(source)` / `readEdges(source)` | `Flow<GraphIoVertexRecord>` / `Flow<GraphIoEdgeRecord>` |

`S` is the format-specific source type (e.g. `GraphImportSource`, `CsvGraphImportSource`) and `T` is the sink type (e.g. `GraphExportSink`, `CsvGraphExportSink`).

### Sources & Sinks (`io.bluetape4k.graph.io.source`)

Sealed interfaces that abstract over file paths and raw streams:

```kotlin
sealed interface GraphImportSource {
    data class PathSource(val path: Path, val charset: Charset = Charsets.UTF_8) : GraphImportSource
    data class InputStreamSource(val input: InputStream, val charset: Charset = Charsets.UTF_8, val closeInput: Boolean = false) : GraphImportSource
}

sealed interface GraphExportSink {
    data class PathSink(val path: Path, val charset: Charset = Charsets.UTF_8, val append: Boolean = false) : GraphExportSink
    data class OutputStreamSink(val output: OutputStream, val charset: Charset = Charsets.UTF_8, val closeOutput: Boolean = false) : GraphExportSink
}
```

### Records (`io.bluetape4k.graph.io.model`)

Intermediate records emitted by format parsers before the importer resolves external IDs to backend IDs:

- `GraphIoVertexRecord(externalId, label, properties)`
- `GraphIoEdgeRecord(externalId?, label, fromExternalId, toExternalId, properties)` — endpoints are **unresolved external IDs**; the importer resolves them against `GraphIoExternalIdMap`.

### Options (`io.bluetape4k.graph.io.options`)

```kotlin
data class GraphImportOptions(
    val batchSize: Int = 1_000,
    val maxEdgeBufferSize: Int = 100_000,
    val onDuplicateVertexId: DuplicateVertexPolicy = DuplicateVertexPolicy.FAIL,
    val onMissingEdgeEndpoint: MissingEndpointPolicy = MissingEndpointPolicy.FAIL,
    val defaultVertexLabel: String = "Vertex",
    val defaultEdgeLabel: String = "Edge",
    val preserveExternalIdProperty: String? = "_graphIoExternalId",
)

data class GraphExportOptions(
    val vertexLabels: Set<String> = emptySet(),  // empty = all labels
    val edgeLabels: Set<String> = emptySet(),    // empty = all labels
    val includeEmptyProperties: Boolean = true,
)

enum class DuplicateVertexPolicy { FAIL, SKIP }
enum class MissingEndpointPolicy { FAIL, SKIP_EDGE }
```

`requireNotBlank` is enforced on label fields and on every element of label sets.

### Reports (`io.bluetape4k.graph.io.report`)

```kotlin
data class GraphImportReport(
    val status: GraphIoStatus,                // COMPLETED | PARTIAL | FAILED
    val format: GraphIoFormat,                // CSV | NDJSON_JACKSON2 | NDJSON_JACKSON3 | GRAPHML
    val verticesRead: Long,
    val verticesCreated: Long,
    val edgesRead: Long,
    val edgesCreated: Long,
    val skippedVertices: Long,
    val skippedEdges: Long,
    val elapsed: Duration,
    val failures: List<GraphIoFailure> = emptyList(),
)

data class GraphExportReport(
    val status: GraphIoStatus,
    val format: GraphIoFormat,
    val verticesWritten: Long,
    val edgesWritten: Long,
    val skippedVertices: Long,
    val skippedEdges: Long,
    val elapsed: Duration,
    val failures: List<GraphIoFailure> = emptyList(),
)

data class GraphIoFailure(
    val phase: GraphIoPhase,                  // READ_VERTEX | READ_EDGE | WRITE_VERTEX | WRITE_EDGE | ...
    val severity: GraphIoFailureSeverity = GraphIoFailureSeverity.ERROR,
    val location: String? = null,
    val sourceName: String? = null,
    val fileRole: GraphIoFileRole? = null,
    val recordId: String? = null,
    val columnName: String? = null,
    val elementName: String? = null,
    val message: String,
)
```

### Support Helpers (`io.bluetape4k.graph.io.support`)

- **`GraphIoPaths`** — opens `BufferedReader`/`BufferedWriter`/`InputStream`/`OutputStream` for any `GraphImportSource`/`GraphExportSink`, auto-creates parent directories for `PathSink`, honours the `closeInput`/`closeOutput` flag for caller-owned streams.
- **`GraphIoExternalIdMap`** — tracks external ID → backend `GraphElementId` mappings during import and enforces `DuplicateVertexPolicy` (`FAIL` or `SKIP`).
- **`GraphIoStopwatch`** — millisecond-precision timer used by format importers/exporters to populate `report.elapsed`.
- **`VirtualThreadGraphBulkAdapter`** — wraps a sync `GraphBulkImporter`/`GraphBulkExporter` as a Virtual-Thread-backed async variant via `CompletableFuture`.

## Usage (Format Implementer's View)

Implementing a new format means depending on `graph-io-core` and providing the three executor variants:

```kotlin
class MyFormatBulkExporter : GraphBulkExporter<GraphExportSink> {
    override fun exportGraph(
        sink: GraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
    ): GraphExportReport {
        val sw = GraphIoStopwatch.start()
        val failures = mutableListOf<GraphIoFailure>()
        GraphIoPaths.openWriter(sink).use { writer ->
            // Stream vertices filtered by options.vertexLabels, then edges by options.edgeLabels
        }
        return GraphExportReport(
            status = if (failures.isEmpty()) GraphIoStatus.COMPLETED else GraphIoStatus.PARTIAL,
            format = GraphIoFormat.CSV,
            verticesWritten = 0, edgesWritten = 0,
            skippedVertices = 0, skippedEdges = 0,
            elapsed = sw.elapsed(),
            failures = failures,
        )
    }
}

class MyFormatVirtualThreadBulkExporter(
    private val sync: MyFormatBulkExporter = MyFormatBulkExporter(),
) : GraphVirtualThreadBulkExporter<GraphExportSink> {
    override fun exportGraphAsync(
        sink: GraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
    ): CompletableFuture<GraphExportReport> =
        VirtualThreadGraphBulkAdapter.wrapExporter(sync).exportGraphAsync(sink, operations, options)
}
```

## Usage (Consumer's View)

Application code rarely depends on `graph-io-core` directly; pick a format module instead:

```kotlin
// CSV example
import io.bluetape4k.graph.io.csv.CsvGraphBulkImporter
import io.bluetape4k.graph.io.csv.CsvGraphImportSource
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.source.GraphImportSource
import java.nio.file.Paths

val importer = CsvGraphBulkImporter()
val source = CsvGraphImportSource(
    vertices = GraphImportSource.PathSource(Paths.get("vertices.csv")),
    edges = GraphImportSource.PathSource(Paths.get("edges.csv")),
)
val report = importer.importGraph(source, graphOps, GraphImportOptions())
println("Imported ${report.verticesCreated} / ${report.verticesRead} vertices")
```

Every format follows the same pattern — `*BulkImporter` / `*BulkExporter` (sync), `Suspend*BulkImporter` / `Suspend*BulkExporter` (coroutines), `*VirtualThreadBulkImporter` / `*VirtualThreadBulkExporter` (VT).

## Design Principles

- **Streaming by default.** No parser loads the whole file into memory; edges are buffered (bounded by `maxEdgeBufferSize`) to ensure all referenced vertices exist before edges are created.
- **Caller-owned streams.** `InputStreamSource` / `OutputStreamSink` default to `closeInput = false` / `closeOutput = false`; flush happens on close but the caller's stream stays open.
- **Partial success over fail-fast.** Per-record problems are reported via `GraphIoFailure` — the overall `status` becomes `PARTIAL` rather than aborting the whole run (except when `onDuplicateVertexId` or `onMissingEdgeEndpoint` is `FAIL`).
- **External IDs stay visible.** If `preserveExternalIdProperty` is set (default: `"_graphIoExternalId"`), the importer writes the original external ID as a vertex property so round-trips remain lossless.

## Dependency

```kotlin
dependencies {
    api("io.bluetape4k:graph-io-core:$version")
}
```

Transitive dependencies: `bluetape4k-graph-core`, `bluetape4k-coroutines`, `bluetape4k-virtualthread`, `bluetape4k-logging`.

## Related Modules

- `graph-io-csv` — CSV (two files: vertices + edges)
- `graph-io-jackson2` — NDJSON with Jackson 2.x
- `graph-io-jackson3` — NDJSON with Jackson 3.x (`tools.jackson`)
- `graph-io-graphml` — GraphML 2.4 via StAX
