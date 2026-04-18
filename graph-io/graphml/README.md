# graph-io-graphml

GraphML (XML) bulk importer and exporter using StAX streaming parser for efficient memory usage and performance.

## Overview

The `graph-io-graphml` module provides three execution models for importing and exporting graph data in GraphML format:

1. **Synchronous API** - Blocking operations for simple use cases
2. **Coroutine Suspension API** - Async/await with `suspend` functions
3. **Virtual Thread API** - Thread-per-task execution using Java 21+ virtual threads

All implementations use StAX (Streaming API for XML) for memory-efficient parsing and writing of large GraphML files.

## Features

- **StAX-based streaming**: Memory-efficient parsing and serialization
- **GraphML 2.4 standard support**: Full compatibility with GraphML specification
- **Three execution models**: Sync, async, and virtual thread variants
- **Detailed import reports**: Comprehensive failure reporting with phase and severity tracking
- **Flexible configuration**: Customizable attribute names, default labels, and error handling policies
- **Bulk operations**: Optimized for large-scale graph import/export

## Installation

Add the dependency to your `build.gradle.kts`:

```kotlin
dependencies {
    implementation("io.bluetape4k:graph-io-graphml:$version")
}
```

## Usage Examples

### Synchronous Import

```kotlin
import io.bluetape4k.graph.io.graphml.GraphMlBulkImporter
import io.bluetape4k.graph.io.graphml.GraphMlImportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.repository.GraphOperations

val importer = GraphMlBulkImporter()
val source = GraphImportSource.fromFile("data.graphml")
val ops: GraphOperations = /* your graph operations instance */

val report = importer.importGraph(
    source = source,
    operations = ops,
    options = GraphImportOptions(),
    graphMlOptions = GraphMlImportOptions(
        labelAttrName = "label",
        defaultVertexLabel = "Vertex",
        defaultEdgeLabel = "EDGE"
    )
)

println("Import completed: ${report.verticesCreated}/${report.verticesRead} vertices, " +
        "${report.edgesCreated}/${report.edgesRead} edges")
println("Status: ${report.status}")
```

### Coroutine-based Import

```kotlin
import io.bluetape4k.graph.io.graphml.SuspendGraphMlBulkImporter
import io.bluetape4k.graph.io.graphml.GraphMlImportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.repository.GraphSuspendOperations

val importer = SuspendGraphMlBulkImporter()
val source = GraphImportSource.fromFile("data.graphml")
val ops: GraphSuspendOperations = /* your graph operations instance */

val report = importer.importGraphSuspending(
    source = source,
    operations = ops,
    options = GraphImportOptions(),
    graphMlOptions = GraphMlImportOptions()
)

println("Import status: ${report.status}")
if (report.failures.isNotEmpty()) {
    report.failures.forEach { failure ->
        println("${failure.phase}: ${failure.message} (severity: ${failure.severity})")
    }
}
```

### Virtual Thread Export

```kotlin
import io.bluetape4k.graph.io.graphml.GraphMlVirtualThreadBulkExporter
import io.bluetape4k.graph.io.graphml.GraphMlExportOptions
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.repository.GraphOperations

val exporter = GraphMlVirtualThreadBulkExporter()
val sink = GraphExportSink.toFile("output.graphml")
val ops: GraphOperations = /* your graph operations instance */

val report = exporter.exportGraph(
    sink = sink,
    operations = ops,
    options = GraphExportOptions(
        vertexLabels = listOf("Person", "Company"),
        edgeLabels = listOf("KNOWS", "WORKS_AT")
    ),
    graphMlOptions = GraphMlExportOptions()
)

println("Exported ${report.verticesWritten} vertices and ${report.edgesWritten} edges")
```

### Synchronous Export

```kotlin
import io.bluetape4k.graph.io.graphml.GraphMlBulkExporter

val exporter = GraphMlBulkExporter()
val report = exporter.exportGraph(
    sink = GraphExportSink.toFile("graph.graphml"),
    operations = ops,
    options = GraphExportOptions(
        vertexLabels = listOf("Person"),
        edgeLabels = listOf("KNOWS")
    )
)
```

## Configuration

### Import Options

`GraphMlImportOptions` allows customization of the import behavior:

```kotlin
data class GraphMlImportOptions(
    val labelAttrName: String = "label",                          // Attribute name for node/edge labels
    val unsupportedElementPolicy: UnsupportedGraphMlElementPolicy = UnsupportedGraphMlElementPolicy.SKIP,
    val defaultVertexLabel: String = "Vertex",                    // Default label for vertices without explicit label
    val defaultEdgeLabel: String = "EDGE"                         // Default label for edges without explicit label
)
```

### Export Options

`GraphMlExportOptions` is currently empty but provides extension point for future features:

```kotlin
data class GraphMlExportOptions : Serializable
```

## Performance Notes

### XMLFactory Caching (Critical)

`XMLInputFactory` and `XMLOutputFactory` instances are expensive to create. The module internally maintains singleton instances for optimal performance. **Do not create new instances for each operation.**

The `StaxGraphMlReader` and `StaxGraphMlWriter` classes use cached factories to avoid expensive initialization overhead.

### Memory Efficiency

The StAX streaming approach processes XML incrementally, making it suitable for large GraphML files that would not fit in memory with a DOM-based parser.

## Error Handling

Import operations return a detailed `GraphImportReport` containing:

- **Status**: COMPLETED, PARTIAL, or FAILED
- **Failures**: List of `GraphIoFailure` objects with:
  - Phase: READ_GRAPH, CREATE_VERTEX, CREATE_EDGE, READ_EDGE
  - Severity: INFO, WARN, ERROR
  - Message: Descriptive error message
  - RecordId: ID of the problematic record

Failures are collected and reported rather than failing fast, allowing partial imports to complete.

## Implementation Details

- `GraphMlBulkImporter` / `GraphMlBulkExporter`: Synchronous implementations
- `SuspendGraphMlBulkImporter` / `SuspendGraphMlBulkExporter`: Coroutine-based implementations with `Dispatchers.IO`
- `GraphMlVirtualThreadBulkImporter` / `GraphMlVirtualThreadBulkExporter`: Virtual thread implementations for Java 21+
- `StaxGraphMlReader` / `StaxGraphMlWriter`: Low-level streaming XML handling

## Dependencies

- `graph-io-core`: Core graph I/O interfaces and models
- `bluetape4k-coroutines`: Coroutine utilities
- `bluetape4k-virtualthread`: Virtual thread support for Java 21+
