# graph-io-csv

English | [한국어](README.ko.md)

CSV format bulk importer/exporter for **bluetape4k-graph**. Seamlessly export graph vertices and edges to CSV files, with support for three execution models: synchronous, virtual thread-based, and Kotlin coroutine-based suspend operations.

## Features

- **Flexible Execution Models**
  - **Sync (`CsvGraphBulkExporter`)**: Blocking I/O, suitable for simple scripts and batch jobs
  - **Virtual Thread (`CsvGraphVirtualThreadBulkExporter`)**: Async via Java virtual threads, lightweight concurrency
  - **Suspend (`SuspendCsvGraphBulkExporter`)**: Kotlin coroutine-based, structured concurrency with `suspend` functions

- **Property Handling Modes**
  - `PrefixedColumns`: Store properties as separate columns with a prefix (e.g., `prop.name`, `prop.age`)
  - `RawJsonColumn`: Use one configured column for a JSON property payload
  - `None`: Exclude properties entirely

- **Automatic Schema Union**: Header generation automatically discovers all property keys across records

- **Comprehensive Reporting**: Export reports include vertex/edge counts, execution time, and detailed failure tracking

## Dependency

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.bluetape4k:graph-io-csv:$version")
}
```

## Architecture

![graph-io-csv architecture](../../docs/images/readme-diagrams/graph-io-csv-architecture-01.png)

CSV keeps graph data in a paired file contract:

- `vertices.csv` stores `id`, `label`, and optional property columns.
- `edges.csv` stores `id`, `label`, `from`, `to`, and optional property columns.
- `CsvRecordCodec` owns union-header generation and property extraction for both import and export.
- Import runs in two passes: vertices build the external-id map first, then edges resolve `from`/`to`.
- Sync, virtual-thread, and suspend APIs reuse the same CSV contract with different execution models.

## Usage

### Synchronous Export

Export a graph to CSV files using blocking I/O:

```kotlin
import io.bluetape4k.graph.io.csv.CsvGraphBulkExporter
import io.bluetape4k.graph.io.csv.CsvGraphExportSink
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.repository.GraphOperations
import java.nio.file.Paths

val exporter = CsvGraphBulkExporter()

val sink = CsvGraphExportSink(
    vertices = GraphExportSink.PathSink(Paths.get("vertices.csv")),
    edges = GraphExportSink.PathSink(Paths.get("edges.csv")),
)

val options = GraphExportOptions(
    vertexLabels = setOf("Person", "Company"),
    edgeLabels = setOf("works_for", "knows"),
)

val report = exporter.exportGraph(sink, graphOps, options)
println("Exported ${report.verticesWritten} vertices and ${report.edgesWritten} edges in ${report.elapsed.toMillis()}ms")
```

### Virtual Thread-Based Export

Export asynchronously using Java virtual threads:

```kotlin
import io.bluetape4k.graph.io.csv.CsvGraphVirtualThreadBulkExporter
import io.bluetape4k.graph.io.csv.CsvGraphExportSink
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.source.GraphExportSink
import java.nio.file.Paths

val exporter = CsvGraphVirtualThreadBulkExporter()

val sink = CsvGraphExportSink(
    vertices = GraphExportSink.PathSink(Paths.get("vertices.csv")),
    edges = GraphExportSink.PathSink(Paths.get("edges.csv")),
)

val options = GraphExportOptions(
    vertexLabels = setOf("Person"),
    edgeLabels = setOf("knows"),
)

val future = exporter.exportGraphAsync(sink, graphOps, options)
val report = future.join()  // Wait for completion
println("Exported ${report.verticesWritten} vertices")
```

### Coroutine-Based Export (Suspend)

Export using Kotlin coroutines for structured concurrency:

```kotlin
import io.bluetape4k.graph.io.csv.SuspendCsvGraphBulkExporter
import io.bluetape4k.graph.io.csv.CsvGraphExportSink
import io.bluetape4k.graph.io.csv.CsvGraphIoOptions
import io.bluetape4k.graph.io.csv.CsvPropertyMode
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.source.GraphExportSink
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths

val exporter = SuspendCsvGraphBulkExporter()

val sink = CsvGraphExportSink(
    vertices = GraphExportSink.PathSink(Paths.get("vertices.csv")),
    edges = GraphExportSink.PathSink(Paths.get("edges.csv")),
)

val options = GraphExportOptions(
    vertexLabels = setOf("Person", "Company"),
    edgeLabels = setOf("works_for"),
)

val csvOptions = CsvGraphIoOptions(
    propertyMode = CsvPropertyMode.PrefixedColumns(prefix = "attr."),
)

val report = runBlocking {
    exporter.exportGraphSuspending(sink, suspendGraphOps, options, csvOptions)
}
println("Exported ${report.verticesWritten} vertices and ${report.edgesWritten} edges")
```

## Import

Import a graph from CSV files. Vertices and edges are provided as separate CSV files.

### Synchronous Import

```kotlin
import io.bluetape4k.graph.io.csv.CsvGraphBulkImporter
import io.bluetape4k.graph.io.csv.CsvGraphImportSource
import io.bluetape4k.graph.io.options.DuplicateVertexPolicy
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.options.MissingEndpointPolicy
import io.bluetape4k.graph.io.source.GraphImportSource
import java.nio.file.Paths

val importer = CsvGraphBulkImporter()

val source = CsvGraphImportSource(
    vertices = GraphImportSource.PathSource(Paths.get("vertices.csv")),
    edges    = GraphImportSource.PathSource(Paths.get("edges.csv")),
)

val options = GraphImportOptions(
    defaultVertexLabel    = "Node",
    onDuplicateVertexId   = DuplicateVertexPolicy.SKIP,      // skip duplicate vertices
    onMissingEdgeEndpoint = MissingEndpointPolicy.SKIP_EDGE,  // skip edges with missing endpoints
)

val report = importer.importGraph(source, graphOps, options)
println("Imported ${report.verticesCreated}/${report.verticesRead} vertices, " +
        "${report.edgesCreated}/${report.edgesRead} edges — ${report.status}")
```

### Import CSV File Format

Vertex CSV file:

```csv
id,label,prop.name,prop.age
v1,Person,Alice,30
v2,Person,Bob,25
```

Edge CSV file:

```csv
id,label,from,to,prop.since
,KNOWS,v1,v2,2024
```

### Import Options

| Option | Type | Default | Description |
|------|------|--------|------|
| `defaultVertexLabel` | `String` | `"Vertex"` | Default label when the `label` column is blank |
| `defaultEdgeLabel` | `String` | `"Edge"` | Default label when the `label` column is blank |
| `onDuplicateVertexId` | `DuplicateVertexPolicy` | `FAIL` | Duplicate vertex handling: `FAIL` immediately or `SKIP` the duplicate |
| `onMissingEdgeEndpoint` | `MissingEndpointPolicy` | `FAIL` | Missing edge endpoint handling: `FAIL` immediately or `SKIP_EDGE` |
| `preserveExternalIdProperty` | `String?` | `null` | Property key used to preserve external IDs, such as `"_externalId"` |

### Import Report

Inspect the import report for counts and failures:

```kotlin
val report = importer.importGraph(source, graphOps, options)

println("Status: ${report.status}")              // COMPLETED, PARTIAL, FAILED
println("Vertices: ${report.verticesCreated}/${report.verticesRead}")
println("Edges: ${report.edgesCreated}/${report.edgesRead}")
println("Skipped vertices: ${report.skippedVertices}")
println("Skipped edges: ${report.skippedEdges}")
println("Duration: ${report.elapsed.toMillis()}ms")

report.failures.forEach { failure ->
    println("[${failure.severity}][${failure.phase}] ${failure.message}")
}
```

### Virtual Thread Import

```kotlin
val importer = CsvGraphVirtualThreadBulkImporter()
val future = importer.importGraphAsync(source, graphOps, options)
val report = future.get()
```

### Coroutine-Based Import (Suspend)

```kotlin
val importer = SuspendCsvGraphBulkImporter()
val report = coroutineScope {
    importer.importGraphSuspending(source, suspendGraphOps, options)
}
```

## Configuration

### Property Modes

Configure how graph properties are serialized in CSV:

#### Prefixed Columns (Default)

Properties appear as separate columns with a configurable prefix:

```kotlin
val options = CsvGraphIoOptions(
    propertyMode = CsvPropertyMode.PrefixedColumns(prefix = "prop.")
)
// Columns: id, label, prop.name, prop.age, prop.email, ...
```

#### Raw JSON Column

Use one configured column for the JSON property payload:

```kotlin
val options = CsvGraphIoOptions(
    propertyMode = CsvPropertyMode.RawJsonColumn(columnName = "attributes")
)
// Columns: id, label, attributes (with JSON value)
```

#### None

Exclude properties entirely:

```kotlin
val options = CsvGraphIoOptions(
    propertyMode = CsvPropertyMode.None
)
// Columns: id, label only
```

## Export Report

After exporting, inspect the report for summary statistics and error details:

```kotlin
val report = exporter.exportGraph(sink, graphOps, options)

println("Status: ${report.status}")  // COMPLETED, PARTIAL, FAILED
println("Vertices: ${report.verticesWritten}")
println("Edges: ${report.edgesWritten}")
println("Duration: ${report.elapsed.toMillis()}ms")

if (report.failures.isNotEmpty()) {
    report.failures.forEach { failure ->
        println("Error[${failure.phase}]: ${failure.message}")
    }
}
```

## Performance Considerations

- **Sync**: Best for small datasets or when simplicity is preferred
- **Virtual Thread**: Ideal for moderate concurrency with minimal threading overhead
- **Suspend**: Optimal for large-scale operations with non-blocking I/O and structured concurrency

Choose based on your workload:
- **Small datasets** (<100K records): Use sync
- **Medium to large** (100K–1M records): Use virtual threads or suspend
- **High-concurrency** environments: Use suspend with coroutine supervisors

CSV export reads each selected vertex and edge label through
`findVerticesByLabelChunked` / `findEdgesByLabelChunked`. When the backend
overrides the chunk-aware repository API (or provides a cursor-backed
implementation), records are written once to the shared `GraphIoRecordSpool`
and replayed for header discovery and row output. The spool avoids
exporter-side whole-list materialization and a live second backend pass; the
compatibility list/Flow fallback may still materialize a label before the
exporter receives it. Active replay streams are closed during spool cleanup,
and cleanup failures are suppressed behind the original source, sink, or
cancellation failure. The temporary spool is removed on success, failure, and
coroutine cancellation.

The CSV bounded-chunk TCK asserts the requested chunk size, one lookup per
selected label, and preservation of stage-time values when the backend mutates
after the first chunk. It does not claim bounded source execution for the
compatibility fallback, whose full label lookup can occur before the first
chunk is yielded.

## Streaming reader contract

`CsvGraphRecordFlowReader` emits vertex and edge records as a cold, sequential `Flow` and preserves
the input order. `GraphImportOptions.batchSize` controls backend write flushing only; it does not
change reader buffering or source close ownership. Each CSV import uses the vertex/edge pair, and
path or explicitly owned streams are closed by the library while caller-owned streams remain open.
