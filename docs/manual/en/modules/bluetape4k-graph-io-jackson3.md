# bluetape4k-graph-io-jackson3

## Before you run

This module implements the same NDJSON envelope with Jackson 3. Choose it for a Jackson 3 dependency line and new integrations. Keep Jackson 2 when the application or mapper extensions still depend on that API. Source: [Jackson3NdJsonBulkImporter.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/jackson3/src/main/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3NdJsonBulkImporter.kt).


Execution mode: **release-fixture linked**. `sourceOps`, `targetOps`, and the temporary NDJSON path are owned by `Jackson3RoundTripTest`, which seeds and closes both graphs.

## Run

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-io-jackson3")
}
```

```kotlin
val path = Path.of("graph.ndjson")
val out = Jackson3NdJsonBulkExporter().use {
    it.exportGraph(GraphExportSink.PathSink(path), sourceOps, GraphExportOptions())
}
val input = Jackson3NdJsonBulkImporter().use {
    it.importGraph(GraphImportSource.PathSource(path), targetOps, GraphImportOptions())
}
check(out.verticesWritten == input.verticesCreated)
```

## Expected result

Expected: a streaming NDJSON file imports without holding the whole document.

## Record boundary, compatibility, and resources

One line is one vertex or edge envelope. Edge `from` and `to` are external IDs; they are resolved after vertices and never become a backend ID contract. Release tests lock Jackson 2/3 file compatibility. Edge buffering remains bounded and can fail before edge writes.

Path-based streams are opened/closed by the library. External streams keep caller ownership by default. Cancellation or a malformed later line can leave earlier backend batches.

## Operations checklist

- Compare report counts with durable graph counts.
- Record the format, batch size, policy, and failed phase.
- Set input size and buffering limits.
- Close library-owned paths and caller-owned streams at their documented boundary.

## Failure and recovery

Symptom: a mapper/property error or unresolved endpoint produces a partial report. Preserve the offending line, clear the partial target, fix the envelope or mapper compatibility, and rerun.

Record line number, envelope type, mapper/property failure, duplicate ID, unresolved endpoint, buffer usage, report phase, and durable counts. Validate a file with the consumer's exact Jackson line before migrating.

```bash
./gradlew :bluetape4k-graph-io-jackson3:test --tests '*Jackson3RoundTripTest' --tests '*Jackson3EdgeBufferOverflowTest' --tests '*NdJsonCompatibilityTest'
```

Expected: local and Jackson 2 compatibility round trips pass, while overflow returns bounded failure evidence.

## Complete release example

The pinned [Jackson3RoundTripTest](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/jackson3/src/test/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3RoundTripTest.kt) defines every fixture variable and is the complete executable release example. Run:

```bash
./gradlew :bluetape4k-graph-io-jackson3:test --tests '*Jackson3RoundTripTest'
```

Expected: the round trip or negative-path assertions pass and all fixture-owned resources are closed.

## Non-goals and related guides

See [formats](../graph-io/formats.md), [execution model](../graph-io/execution-model.md), and the [Jackson 2 module](bluetape4k-graph-io-jackson2.md). This module does not require Jackson 2, guarantee custom mapper equivalence, make import atomic, or preserve backend-native IDs.
