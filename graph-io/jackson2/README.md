# graph-io-jackson2

Jackson 2.x NDJSON (Newline-Delimited JSON) bulk importer and exporter for graph database operations.

## Overview

`graph-io-jackson2` provides efficient, high-performance graph data import/export using NDJSON format. Each line contains a complete JSON object representing either a vertex or edge, enabling streaming processing of large graphs without loading the entire dataset into memory.

## Features

### Three Execution Models

1. **Synchronous API** (`Jackson2NdJsonBulkImporter`, `Jackson2NdJsonBulkExporter`)
   - Blocking I/O operations
   - Straightforward sequential processing
   - Best for small to medium graphs or when simplicity is preferred

2. **Virtual Thread API** (`Jackson2NdJsonVirtualThreadBulkImporter`, `Jackson2NdJsonVirtualThreadBulkExporter`)
   - Java 21+ Virtual Threads for lightweight concurrency
   - Scales to thousands of concurrent I/O operations
   - Ideal for CPU-constrained systems with high I/O workloads

3. **Coroutine Suspend API** (`SuspendJackson2NdJsonBulkImporter`, `SuspendJackson2NdJsonBulkExporter`)
   - Kotlin coroutines for non-blocking operations
   - Integrates seamlessly with async/await code
   - Best for applications already using coroutines

### NDJSON Envelope Format

The module uses a structured JSON envelope format for both vertices and edges:

```json
{"type": "vertex", "id": "v1", "label": "Person", "properties": {"name": "Alice", "age": 30}}
{"type": "edge", "id": "e1", "label": "KNOWS", "from": "v1", "to": "v2", "properties": {"since": 2020}}
```

**Envelope Fields:**
- `type` (string) - "vertex" or "edge"
- `id` (string) - Unique identifier within the import/export
- `label` (string) - Vertex or edge label
- `from` (string) - For edges: source vertex external ID
- `to` (string) - For edges: target vertex external ID
- `properties` (object) - Key-value properties (optional, defaults to empty)

### Edge Buffering

Edges are buffered during import to ensure all referenced vertices are created first:
- Vertices are processed and created immediately
- Edges are accumulated in a buffer (default max size: 10,000)
- All vertices are committed before edges are created
- Improves consistency and allows atomic vertex graph construction

## Usage Examples

### Synchronous Import

```kotlin
import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonBulkImporter
import io.bluetape4k.graph.io.options.DuplicateVertexPolicy
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.source.GraphImportSource
import java.nio.file.Paths

val importer = Jackson2NdJsonBulkImporter()
val source = GraphImportSource.PathSource(Paths.get("graph-data.ndjson"))
val options = GraphImportOptions(
    defaultVertexLabel = "Node",
    defaultEdgeLabel = "Link",
    maxEdgeBufferSize = 10_000,
    onDuplicateVertexId = DuplicateVertexPolicy.SKIP,
)

val report = importer.importGraph(source, operations, options)
println("Imported ${report.verticesCreated} vertices, ${report.edgesCreated} edges")
println("Status: ${report.status}")
```

### Synchronous Export

```kotlin
import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonBulkExporter
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.source.GraphExportSink
import java.nio.file.Paths

val exporter = Jackson2NdJsonBulkExporter()
val sink = GraphExportSink.PathSink(Paths.get("output.ndjson"))
val options = GraphExportOptions(
    vertexLabels = setOf("Person", "Company"),
    edgeLabels = setOf("KNOWS", "WORKS_AT"),
)

val report = exporter.exportGraph(sink, operations, options)
println("Exported ${report.verticesWritten} vertices, ${report.edgesWritten} edges")
```

### Coroutine-Based Import

```kotlin
import io.bluetape4k.graph.io.jackson2.SuspendJackson2NdJsonBulkImporter
import io.bluetape4k.graph.io.source.GraphImportSource
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths

val importer = SuspendJackson2NdJsonBulkImporter()
val source = GraphImportSource.PathSource(Paths.get("graph-data.ndjson"))

val report = runBlocking {
    importer.importGraphSuspending(source, suspendOperations, options)
}
println("Import completed: ${report.status}")
```

### Virtual Thread Import

```kotlin
import io.bluetape4k.graph.io.jackson2.Jackson2NdJsonVirtualThreadBulkImporter

val importer = Jackson2NdJsonVirtualThreadBulkImporter()
val future = importer.importGraphAsync(source, operations, options)
val report = future.join()  // Leverages Virtual Threads for efficient concurrent I/O
```

## Error Handling

The module provides comprehensive error reporting through `GraphImportReport` and `GraphExportReport`:

```kotlin
val report = importer.importGraph(source, operations, options)

if (report.status == GraphIoStatus.FAILED) {
    report.failures.forEach { failure ->
        println("${failure.phase}: ${failure.message}")
        println("  Location: ${failure.location}")
        println("  Severity: ${failure.severity}")
    }
}
```

## Dependency

Add to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.bluetape4k:graph-io-jackson2:1.0.0")
}
```

## Requirements

- **Kotlin** 2.0+
- **Java** 21+
- Jackson 2.17+
- Coroutines (for suspend API)

## Related Modules

- `graph-io-core` - Core I/O abstractions and interfaces
- `graph-neo4j` - Neo4j graph operations
- `graph-tinkerpop` - TinkerPop/Gremlin support
- `graph-age` - Apache AGE support
