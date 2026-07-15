# bluetape4k-graph-io-jackson3

## Choose Jackson 3 for a Jackson 3 application

This module implements the same NDJSON envelope with Jackson 3. Choose it for a Jackson 3 dependency line and new integrations. Keep Jackson 2 when the application or mapper extensions still depend on that API. Source: [Jackson3NdJsonBulkImporter.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph-io/jackson3/src/main/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3NdJsonBulkImporter.kt).

## Dependency and quick start

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

Expected: a streaming NDJSON file imports without holding the whole document.

## Record boundary, compatibility, and resources

One line is one vertex or edge envelope. Edge `from` and `to` are external IDs; they are resolved after vertices and never become a backend ID contract. Release tests lock Jackson 2/3 file compatibility. Edge buffering remains bounded and can fail before edge writes.

Path-based streams are opened/closed by the library. External streams keep caller ownership by default. Cancellation or a malformed later line can leave earlier backend batches.

## Failure diagnosis and operations

Record line number, envelope type, mapper/property failure, duplicate ID, unresolved endpoint, buffer usage, report phase, and durable counts. Validate a file with the consumer's exact Jackson line before migrating.

```bash
./gradlew :bluetape4k-graph-io-jackson3:test --tests '*Jackson3RoundTripTest' --tests '*Jackson3EdgeBufferOverflowTest' --tests '*NdJsonCompatibilityTest'
```

Expected: local and Jackson 2 compatibility round trips pass, while overflow returns bounded failure evidence.

## Related pages and non-goals

See [formats](../graph-io/formats.md), [execution model](../graph-io/execution-model.md), and the [Jackson 2 module](bluetape4k-graph-io-jackson2.md). This module does not require Jackson 2, guarantee custom mapper equivalence, make import atomic, or preserve backend-native IDs.
