# bluetape4k-graph-io-graphml

## Select the supported GraphML subset

GraphML uses StAX streaming for directed property graphs. Choose it for interchange with tools that emit nodes, directed edges, scalar keys, and scalar data. Avoid assuming full GraphML: undirected graphs, nested graphs, hyperedges, ports, and vendor XML extensions have explicit strict/skip behavior. Source: [GraphMlBulkImporter.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph-io/graphml/src/main/kotlin/io/bluetape4k/graph/io/graphml/GraphMlBulkImporter.kt).

## Dependency and quick start

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

Expected: StAX reads/writes the directed scalar property-graph subset without building a DOM.

## Boundary, external IDs, and ownership

Node `id` values are external IDs; edge `source`/`target` resolve through them. Keys define scalar property mapping. Path input is library-owned. A parse, policy, or backend failure after flushed batches can leave partial data.

Strict mode fails before writes for unsupported graph-level constructs where the reader can decide early. Skip mode records warnings and may project an edge direction; review warnings because projection is not faithful preservation.

## Security and Failure diagnosis

Reject DTD/external entities, malformed XML, duplicate node IDs, missing endpoints, unknown keys, nested graphs, hyperedges, and decompression bombs before trusting input. Observe parser location, policy, warning count, report phase, and durable counts. Reuse cached XML factories as implemented; do not add unsafe per-record parser configuration.

```bash
./gradlew :bluetape4k-graph-io-graphml:test --tests '*GraphMlRoundTripTest' --tests '*StaxGraphMlReaderWriterTest' --tests '*CrossFormatGraphMlTest'
```

Expected: round trip passes, XXE/unsupported fixtures follow policy, and cross-format counts agree.

## Related pages and non-goals

See [formats](../graph-io/formats.md), [OkIO security](../graph-io/okio-security.md), and [failure/cancellation](../guides/failure-and-cancellation.md). The module does not preserve every GraphML extension, invent reverse edges for undirected data, or make XML input safe without limits.
