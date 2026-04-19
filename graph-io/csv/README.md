# graph-io-csv

CSV format bulk importer/exporter for **bluetape4k-graph**. Seamlessly export graph vertices and edges to CSV files, with support for three execution models: synchronous, virtual thread-based, and Kotlin coroutine-based suspend operations.

## Features

- **Flexible Execution Models**
  - **Sync (`CsvGraphBulkExporter`)**: Blocking I/O, suitable for simple scripts and batch jobs
  - **Virtual Thread (`CsvGraphVirtualThreadBulkExporter`)**: Async via Java virtual threads, lightweight concurrency
  - **Suspend (`SuspendCsvGraphBulkExporter`)**: Kotlin coroutine-based, structured concurrency with `suspend` functions

- **Property Handling Modes**
  - `PrefixedColumns`: Store properties as separate columns with a prefix (e.g., `prop.name`, `prop.age`)
  - `RawJsonColumn`: Serialize all properties to a single JSON column
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

All properties serialized as a single JSON column:

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

## Architecture

The module follows **bluetape4k-graph**'s dual API pattern:

- **Synchronous**: Direct blocking operations via `GraphOperations`
- **Virtual Thread**: Async via `CompletableFuture<T>` and virtual thread pools
- **Suspend**: Coroutine-based via `GraphSuspendOperations` and `suspend` functions

All exporters implement a common contract interface and delegate to the same internal codec (`CsvRecordCodec`), ensuring consistency across execution models.

## Performance Considerations

- **Sync**: Best for small datasets or when simplicity is preferred
- **Virtual Thread**: Ideal for moderate concurrency with minimal threading overhead
- **Suspend**: Optimal for large-scale operations with non-blocking I/O and structured concurrency

Choose based on your workload:
- **Small datasets** (<100K records): Use sync
- **Medium to large** (100K–1M records): Use virtual threads or suspend
- **High-concurrency** environments: Use suspend with coroutine supervisors
