# bluetape4k-graph-io-graphml

## Before you run

GraphML uses StAX streaming for directed property graphs. Choose it for interchange with tools that emit nodes, directed edges, scalar keys, and scalar data. Avoid assuming full GraphML: undirected graphs, nested graphs, hyperedges, ports, and vendor XML extensions have explicit strict/skip behavior. Source: [GraphMlBulkImporter.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/graphml/src/main/kotlin/io/bluetape4k/graph/io/graphml/GraphMlBulkImporter.kt).


Execution mode: **release-fixture linked**. `sourceOps`, `targetOps`, and the temporary GraphML path are created and closed by `GraphMlRoundTripTest`; its fixture also locks strict/skip behavior.

## Run

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-io-graphml")
}
```

```kotlin
val path = Path.of("graph.graphml")
val out = GraphMlBulkExporter().use {
    it.exportGraph(GraphExportSink.PathSink(path), sourceOps, GraphExportOptions(), GraphMlExportOptions())
}
val input = GraphMlBulkImporter().use {
    it.importGraph(
        GraphImportSource.PathSource(path), targetOps, GraphImportOptions(),
        GraphMlImportOptions(defaultVertexLabel = "Vertex", defaultEdgeLabel = "EDGE"),
    )
}
check(out.edgesWritten == input.edgesCreated)
```

## Expected result

Expected: StAX reads/writes the directed scalar property-graph subset without building a DOM.

## Boundary, external IDs, and ownership

Node `id` values are external IDs; edge `source`/`target` resolve through them. Keys define scalar property mapping. Path input is library-owned. A parse, policy, or backend failure after flushed batches can leave partial data.

Strict mode fails before writes for unsupported graph-level constructs where the reader can decide early. Skip mode records warnings and may project an edge direction; review warnings because projection is not faithful preservation.

## Operations checklist

- Compare report counts with durable graph counts.
- Record the format, batch size, policy, and failed phase.
- Set input size and buffering limits.
- Close library-owned paths and caller-owned streams at their documented boundary.

## Failure and recovery

Symptom: XML parsing or strict compatibility fails before writes. Preserve parser location and policy, reject unsafe/unsupported constructs or switch to an explicitly reviewed skip policy, clear partial data, and rerun.

Reject DTD/external entities, malformed XML, duplicate node IDs, missing endpoints, unknown keys, nested graphs, hyperedges, and decompression bombs before trusting input. Observe parser location, policy, warning count, report phase, and durable counts. Reuse cached XML factories as implemented; do not add unsafe per-record parser configuration.

```bash
./gradlew :bluetape4k-graph-io-graphml:test --tests '*GraphMlRoundTripTest' --tests '*StaxGraphMlReaderWriterTest' --tests '*CrossFormatGraphMlTest'
```

Expected: round trip passes, XXE/unsupported fixtures follow policy, and cross-format counts agree.

## Complete release example

The pinned [GraphMlRoundTripTest](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/graphml/src/test/kotlin/io/bluetape4k/graph/io/graphml/GraphMlRoundTripTest.kt) defines every fixture variable and is the complete executable release example. Run:

```bash
./gradlew :bluetape4k-graph-io-graphml:test --tests '*GraphMlRoundTripTest'
```

Expected: the round trip or negative-path assertions pass and all fixture-owned resources are closed.

## Non-goals and related guides

See [formats](../graph-io/formats.md), [OkIO security](../graph-io/okio-security.md), and [failure/cancellation](../guides/failure-and-cancellation.md). The module does not preserve every GraphML extension, invent reverse edges for undirected data, or make XML input safe without limits.
