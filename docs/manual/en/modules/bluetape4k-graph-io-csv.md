# bluetape4k-graph-io-csv

## Select CSV deliberately

CSV exports a vertex file and an edge file. Choose it for tabular interchange, inspection, and systems that already own a column schema. Avoid it when one atomic stream, nested property fidelity, or authenticated single-file transport is required. Implementation: [CsvGraphBulkImporter.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph-io/csv/src/main/kotlin/io/bluetape4k/graph/io/csv/CsvGraphBulkImporter.kt) and [CsvGraphBulkExporter.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph-io/csv/src/main/kotlin/io/bluetape4k/graph/io/csv/CsvGraphBulkExporter.kt).

## Dependency and quick start

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-io-csv")
}
```

```kotlin
val sink = CsvGraphExportSink(
    vertices = GraphExportSink.PathSink(Path.of("vertices.csv")),
    edges = GraphExportSink.PathSink(Path.of("edges.csv")),
)
val exported = CsvGraphBulkExporter().use {
    it.exportGraph(sink, sourceOps, GraphExportOptions(setOf("Person"), setOf("KNOWS")))
}
val source = CsvGraphImportSource(
    vertices = GraphImportSource.PathSource(Path.of("vertices.csv")),
    edges = GraphImportSource.PathSource(Path.of("edges.csv")),
)
val imported = CsvGraphBulkImporter().use {
    it.importGraph(source, targetOps, GraphImportOptions())
}
check(exported.verticesWritten == imported.verticesCreated)
check(exported.edgesWritten == imported.edgesCreated)
```

Expected: two CSV files form one logical transfer and counts match.

## Record boundary, IDs, and ownership

Vertex rows establish external IDs; edge rows refer to those IDs. Publish and retain the pair together. Property modes are defined by [CsvGraphIoOptions.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph-io/csv/src/main/kotlin/io/bluetape4k/graph/io/csv/CsvGraphIoOptions.kt); column names, delimiter, charset, quoting, and property mode are part of the contract.

Path-based files are library-opened and closed. A successful vertex file followed by a broken edge file can leave vertices imported. CSV is not an atomic database transaction.

## Negative paths, Failure diagnosis, and operations

Check duplicate headers/IDs, missing columns, unknown endpoints, malformed quoting, charset mismatch, partial pairs, and delimiter collisions. Strict policies should fail with phase evidence; skip policies must increase skipped counts. Do not encrypt only one member of the pair.

```bash
./gradlew :bluetape4k-graph-io-csv:test --tests '*CsvRoundTripTest' --tests '*CsvEdgeCaseTest' --tests '*CsvImportErrorTest'
```

Expected: round trip passes and malformed/missing endpoint cases follow configured policy. If counts drift, inspect the exact two files and report phases before the backend.

## Related pages and non-goals

See [formats and external IDs](../graph-io/formats.md), [execution model](../graph-io/execution-model.md), and [OkIO security](../graph-io/okio-security.md). CSV does not preserve arbitrary nested values automatically, provide a single-file boundary, or make a two-file publication atomic.
