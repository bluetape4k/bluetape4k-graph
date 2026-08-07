# bluetape4k-graph-io-jackson2

## Before you run

This module reads and writes the release NDJSON envelope with Jackson 2. Choose it for applications standardized on Jackson 2 or for compatibility with existing Jackson 2 customization. Prefer Jackson 3 in a Jackson 3 application; avoid loading both stacks without a concrete compatibility need. Source: [Jackson2NdJsonBulkImporter.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/jackson2/src/main/kotlin/io/bluetape4k/graph/io/jackson2/Jackson2NdJsonBulkImporter.kt).


Execution mode: **release-fixture linked**. `sourceOps` and `targetOps` plus the temporary NDJSON path come from `Jackson2RoundTripTest`; the test seeds vertices/edges and closes operations and path resources.

## Run

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

## Expected result

Expected: one JSON object per line, vertices and edges share a stream, and external IDs reconnect endpoints.

## Format, buffering, and resources

Each line is an envelope with `type`, `id`, `label`, properties, and edge `from`/`to`. Edges are buffered until referenced vertices exist; the configured buffer is a memory and failure boundary. A malformed line fails that record phase, not an entire JSON array.

Path sources/sinks are library-owned for the operation. Caller-supplied streams remain caller-owned unless the explicit ownership flag says otherwise. Flushed backend batches remain after a later parse or edge failure.

## Operations checklist

- Compare report counts with durable graph counts.
- Record the format, batch size, policy, and failed phase.
- Set input size and buffering limits.
- Close library-owned paths and caller-owned streams at their documented boundary.

## Failure and recovery

Symptom: parsing stops at a line or the edge buffer overflows. Preserve the line number and envelope, increase the bound only after sizing the input, or reorder/fix records and rerun on a clean target.

Watch line number, envelope type, duplicate external ID, unresolved endpoint, edge-buffer overflow, property conversion, report status, and durable counts. Jackson 2 and 3 files are release-compatible, but custom mapper modules can still change accepted values.

```bash
./gradlew :bluetape4k-graph-io-jackson2:test --tests '*Jackson2RoundTripTest' --tests '*Jackson2EdgeBufferOverflowTest' --tests '*NdJsonCompatibilityTest'
```

Expected: round trip and cross-version envelope compatibility pass; overflow follows the bounded failure path.

## Complete release example

The pinned [Jackson2RoundTripTest](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/jackson2/src/test/kotlin/io/bluetape4k/graph/io/jackson2/Jackson2RoundTripTest.kt) defines every fixture variable and is the complete executable release example. Run:

```bash
./gradlew :bluetape4k-graph-io-jackson2:test --tests '*Jackson2RoundTripTest'
```

Expected: the round trip or negative-path assertions pass and all fixture-owned resources are closed.

## Non-goals and related guides

See [formats](../graph-io/formats.md), [execution model](../graph-io/execution-model.md), and the [Jackson 3 module](bluetape4k-graph-io-jackson3.md). This module does not translate arbitrary Jackson configuration, guarantee whole-file atomicity, or make backend IDs portable.
