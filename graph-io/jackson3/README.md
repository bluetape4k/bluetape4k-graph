# graph-io-jackson3

Jackson 3.x based NDJSON bulk importer and exporter for Bluetape4k graph operations.

## Overview

This module provides high-performance, flexible NDJSON (newline-delimited JSON) bulk import/export capabilities for graph data using **Jackson 3.x** (with `tools.jackson` package namespace). It supports multiple execution models to accommodate different concurrency patterns: synchronous, coroutine-based suspend, and Java Virtual Thread-based approaches.

## Key Features

### Three Execution Models

The module offers three distinct execution models to fit different runtime environments and concurrency requirements:

1. **Synchronous** (`Jackson3NdJsonBulkImporter`, `Jackson3NdJsonBulkExporter`)
   - Blocking I/O operations
   - Suitable for traditional blocking frameworks
   - Thread-per-request architecture

2. **Coroutine Suspend** (`SuspendJackson3NdJsonBulkImporter`, `SuspendJackson3NdJsonBulkExporter`)
   - Non-blocking, async/await style
   - Kotlin coroutines based
   - Optimal for high-concurrency, low-resource scenarios

3. **Virtual Thread** (`Jackson3NdJsonVirtualThreadBulkImporter`, `Jackson3NdJsonVirtualThreadBulkExporter`)
   - Java 21+ Virtual Thread support
   - Lightweight threading model
   - Provides blocking semantics with minimal overhead

### NDJSON Envelope Format

- Each line is a complete, standalone JSON object
- Vertices and edges are serialized in a unified stream (not split files)
- Type discrimination via envelope structure for efficient streaming

### Jackson2 File Format Compatibility

- Files exported from Jackson2 module can be imported by Jackson3
- Backward compatible envelope codec
- No migration tool required for switching implementations

### Comprehensive Error Handling

- Per-record failure tracking with detailed diagnostics
- Configurable missing endpoint policies (FAIL / SKIP_EDGE)
- Duplicate vertex ID handling strategies
- Status reporting: COMPLETED, PARTIAL, or FAILED

### Flexible Configuration

- Selective vertex/edge label filtering
- External ID preservation in properties
- Configurable edge buffer size
- Per-operation progress reporting

## Usage Examples

### Synchronous Export

```kotlin
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkExporter
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.source.GraphExportSink
import java.nio.file.Paths

val exporter = Jackson3NdJsonBulkExporter()
val options = GraphExportOptions(
    vertexLabels = setOf("Person", "Organization"),
    edgeLabels = setOf("WORKS_FOR", "KNOWS"),
)

val report = exporter.exportGraph(
    sink = GraphExportSink.PathSink(Paths.get("export.ndjson")),
    operations = graphOps,
    options = options,
)

println("Exported ${report.verticesWritten} vertices and ${report.edgesWritten} edges")
println("Status: ${report.status}, Elapsed: ${report.elapsed}")
```

### Synchronous Import

```kotlin
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkImporter
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.options.MissingEndpointPolicy
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.io.source.GraphImportSource
import java.nio.file.Paths

val importer = Jackson3NdJsonBulkImporter()
val options = GraphImportOptions(
    defaultVertexLabel = "Entity",
    defaultEdgeLabel = "RELATED_TO",
    onMissingEdgeEndpoint = MissingEndpointPolicy.SKIP_EDGE,
)

val report = importer.importGraph(
    source = GraphImportSource.PathSource(Paths.get("export.ndjson")),
    operations = graphOps,
    options = options,
)

println("Imported ${report.verticesCreated} vertices and ${report.edgesCreated} edges")
if (report.status == GraphIoStatus.FAILED) {
    println("Failures: ${report.failures.size}")
    report.failures.forEach { failure ->
        println("  ${failure.phase}: ${failure.message}")
    }
}
```

### Coroutine-Based Export

```kotlin
import io.bluetape4k.graph.io.jackson3.SuspendJackson3NdJsonBulkExporter
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.source.GraphExportSink
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths

val exporter = SuspendJackson3NdJsonBulkExporter()
val options = GraphExportOptions(vertexLabels = setOf("Person"))

runBlocking {
    val report = exporter.exportGraphSuspending(
        sink = GraphExportSink.PathSink(Paths.get("export.ndjson")),
        operations = suspendGraphOps,
        options = options,
    )
    println("Exported ${report.verticesWritten} vertices")
}
```

### Virtual Thread Export

```kotlin
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonVirtualThreadBulkExporter
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.source.GraphExportSink
import java.nio.file.Paths

val exporter = Jackson3NdJsonVirtualThreadBulkExporter()
val options = GraphExportOptions(vertexLabels = setOf("Person"))

val future = exporter.exportGraphAsync(
    sink = GraphExportSink.PathSink(Paths.get("export.ndjson")),
    operations = graphOps,
    options = options,
)
val report = future.join()
```

## NDJSON Format Specification

### Vertex Line

```json
{"v":{"id":"person_1","label":"Person","p":{"name":"Alice","age":30}}}
```

### Edge Line

```json
{"e":{"id":"edge_1","label":"KNOWS","from":"person_1","to":"person_2","p":{"since":2020}}}
```

**Fields:**
- `id`: Unique external identifier
- `label`: Vertex or edge label/type
- `p`: Properties object (flattened key-value pairs)
- `from`/`to`: Edge endpoint IDs (edges only)

## Configuration

### GraphImportOptions

- `defaultVertexLabel: String` - Applied when vertex has no explicit label
- `defaultEdgeLabel: String` - Applied when edge has no explicit label
- `onDuplicateVertexId: DuplicateVertexPolicy` - Handle ID collisions (FAIL, SKIP)
- `onMissingEdgeEndpoint: MissingEndpointPolicy` - Handle dangling edges (FAIL, SKIP_EDGE)
- `preserveExternalIdProperty: String?` - Store original external ID in properties (key name). `null` disables
- `maxEdgeBufferSize: Int` - Memory limit for buffered edges before flushing (default `100_000`)
- `batchSize: Int` - Progress-reporting/flush interval (default `1_000`)

### GraphExportOptions

- `vertexLabels: Set<String>` - Specific labels to export (empty = all)
- `edgeLabels: Set<String>` - Specific labels to export (empty = all)
- `includeEmptyProperties: Boolean` - Emit records even when properties are empty (default `true`)

## Dependencies

```gradle
dependencies {
    api("io.bluetape4k:graph-io-jackson3:latest")
}
```

### Direct Dependencies

- Jackson 3.x with Kotlin module support
- Bluetape4k graph-io-core (abstract types)
- Kotlin coroutines (optional, for suspend variants)
- Virtual Thread API (optional, for VirtualThread variants)

## Error Handling

All import/export operations return a detailed `Report` object:

```kotlin
data class GraphImportReport(
    val status: GraphIoStatus,                  // COMPLETED, PARTIAL, FAILED
    val format: GraphIoFormat,                  // NDJSON_JACKSON3
    val verticesRead: Long,
    val verticesCreated: Long,
    val edgesRead: Long,
    val edgesCreated: Long,
    val skippedVertices: Long,
    val skippedEdges: Long,
    val elapsed: Duration,
    val failures: List<GraphIoFailure>         // Detailed per-failure diagnostics
)
```

### Failure Details

Each failure includes:
- `phase`: READ_VERTEX, READ_EDGE, WRITE_VERTEX, WRITE_EDGE
- `severity`: ERROR, WARN
- `message`: Human-readable error description
- `recordId`: The specific record that failed
- `location`: File and line information (when available)

## Performance Characteristics

- **Memory**: Edge records buffered in memory (configurable limit)
- **I/O**: Streaming line-by-line for constant memory usage
- **Concurrency**: Model-dependent
  - Synchronous: Single thread
  - Suspend: Cooperative multitasking
  - Virtual Thread: Lightweight parallelism

## Compatibility

- Jackson 3.0+
- Java 11+ (Virtual Thread support requires Java 21+)
- Kotlin 1.9+
- All Bluetape4k graph backends (Neo4j, AGE, Memgraph, TinkerPop)

## Module Coordinates

```
Group: io.bluetape4k
Artifact: graph-io-jackson3
Module: graph-io/jackson3
```

## Related Modules

- `graph-io-core` - Abstract IO contracts and models
- `graph-io-jackson2` - Jackson 2.x NDJSON (legacy)
- `graph-neo4j`, `graph-age`, `graph-memgraph` - Graph backend implementations
