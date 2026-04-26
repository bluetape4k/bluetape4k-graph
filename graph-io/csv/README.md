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

## Import

CSV 파일에서 그래프를 임포트합니다. 정점과 간선 CSV 파일은 각각 별도의 파일로 제공해야 합니다.

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
    onDuplicateVertexId   = DuplicateVertexPolicy.SKIP,   // 중복 정점 건너뜀
    onMissingEdgeEndpoint = MissingEndpointPolicy.SKIP_EDGE, // 끝점 없는 간선 건너뜀
)

val report = importer.importGraph(source, graphOps, options)
println("Imported ${report.verticesCreated}/${report.verticesRead} vertices, " +
        "${report.edgesCreated}/${report.edgesRead} edges — ${report.status}")
```

### Import CSV File Format

정점 CSV 파일:

```csv
id,label,prop.name,prop.age
v1,Person,Alice,30
v2,Person,Bob,25
```

간선 CSV 파일:

```csv
id,label,from,to,prop.since
,KNOWS,v1,v2,2024
```

### Import Options

| 옵션 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `defaultVertexLabel` | `String` | `"Vertex"` | `label` 컬럼이 비어있을 때 사용하는 기본 레이블 |
| `defaultEdgeLabel` | `String` | `"Edge"` | `label` 컬럼이 비어있을 때 사용하는 기본 레이블 |
| `onDuplicateVertexId` | `DuplicateVertexPolicy` | `FAIL` | 중복 정점 ID 처리: `FAIL` (즉시 실패) 또는 `SKIP` (건너뜀) |
| `onMissingEdgeEndpoint` | `MissingEndpointPolicy` | `FAIL` | 끝점 없는 간선 처리: `FAIL` (즉시 실패) 또는 `SKIP_EDGE` (건너뜀) |
| `preserveExternalIdProperty` | `String?` | `null` | 외부 ID를 속성으로 보존할 키 이름 (예: `"_externalId"`) |

### Import Report

임포트 결과에서 통계와 실패 목록을 확인할 수 있습니다:

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
