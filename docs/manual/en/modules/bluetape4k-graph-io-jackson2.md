# bluetape4k-graph-io-jackson2

## Choose Jackson 2 when it is already your JSON line

This module reads and writes the release NDJSON envelope with Jackson 2. Choose it for applications standardized on Jackson 2 or for compatibility with existing Jackson 2 customization. Prefer Jackson 3 in a Jackson 3 application; avoid loading both stacks without a concrete compatibility need. Source: [Jackson2NdJsonBulkImporter.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph-io/jackson2/src/main/kotlin/io/bluetape4k/graph/io/jackson2/Jackson2NdJsonBulkImporter.kt).

## Dependency and quick start

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-io-jackson2")
}
```

```kotlin
val sink = GraphExportSink.PathSink(Path.of("graph.ndjson"))
val source = GraphImportSource.PathSource(Path.of("graph.ndjson"))
val out = Jackson2NdJsonBulkExporter().use {
    it.exportGraph(sink, sourceOps, GraphExportOptions(setOf("Person"), setOf("KNOWS")))
}
val input = Jackson2NdJsonBulkImporter().use {
    it.importGraph(source, targetOps, GraphImportOptions())
}
check(out.edgesWritten == input.edgesCreated)
```

Expected: one JSON object per line, vertices and edges share a stream, and external IDs reconnect endpoints.

## Format, buffering, and resources

Each line is an envelope with `type`, `id`, `label`, properties, and edge `from`/`to`. Edges are buffered until referenced vertices exist; the configured buffer is a memory and failure boundary. A malformed line fails that record phase, not an entire JSON array.

Path sources/sinks are library-owned for the operation. Caller-supplied streams remain caller-owned unless the explicit ownership flag says otherwise. Flushed backend batches remain after a later parse or edge failure.

## Failure diagnosis and operations

Watch line number, envelope type, duplicate external ID, unresolved endpoint, edge-buffer overflow, property conversion, report status, and durable counts. Jackson 2 and 3 files are release-compatible, but custom mapper modules can still change accepted values.

```bash
./gradlew :bluetape4k-graph-io-jackson2:test --tests '*Jackson2RoundTripTest' --tests '*Jackson2EdgeBufferOverflowTest' --tests '*NdJsonCompatibilityTest'
```

Expected: round trip and cross-version envelope compatibility pass; overflow follows the bounded failure path.

## Related pages and non-goals

See [formats](../graph-io/formats.md), [execution model](../graph-io/execution-model.md), and the [Jackson 3 module](bluetape4k-graph-io-jackson3.md). This module does not translate arbitrary Jackson configuration, guarantee whole-file atomicity, or make backend IDs portable.
