# graph-okio

## What it adds

`graph-okio` adapts graph formats to OkIO `Source`, `Sink`, `Path`, and `FileSystem`. It adds compression chaining, atomic path writes, FakeFileSystem tests, and deterministic AEAD chunk encryption for single-stream formats. Choose it for OkIO pipelines; avoid adding it when plain NIO paths are sufficient. Source: [GraphIoOkioPaths.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/GraphIoOkioPaths.kt).

## Dependency and runnable pipeline

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-okio")
}
```

```kotlin
val path = "graph.ndjson.gz.daead".toPath()
val context = "tenant=acme;format=graph-0.5".encodeToByteArray()
val daead = TinkDaeads.AES256_SIV
val out = OkioGraphBulkExporter().exportGraphGzipDaead(
    OkioGraphExportSink.PathSink(path, FileSystem.SYSTEM, atomicWrite = true),
    GraphIoFormat.NDJSON_JACKSON3, daead, sourceOps, associatedData = context,
)
val input = OkioGraphBulkImporter().importGraphDaeadGzip(
    OkioGraphImportSource.PathSource(path, FileSystem.SYSTEM),
    GraphIoFormat.NDJSON_JACKSON3, daead, targetOps, associatedData = context,
)
check(out.verticesWritten == input.verticesCreated)
```

Expected chain: graph bytes → gzip → DAEAD chunks → temporary path → atomic move; import reverses it.

## Ownership, limits, and format boundary

Path variants are library-owned. `SourceBased`/`SinkBased` are caller-owned by default and close only when `ownsSource`/`ownsSink` is true. Compression wrappers close according to that outer ownership.

Associated data must match exactly. Deterministic AEAD can reveal equality under the same key/context. Set ciphertext and decompressed-byte limits. High-level DAEAD helpers reject CSV because CSV is a file pair; define a two-file key, naming, and publication policy before using low-level wrappers.

## Negative paths, Failure diagnosis, and operations

Test wrong associated data, truncated ciphertext, truncated compression, size limits, XXE for GraphML, sink failure, and temporary-file cleanup. Observe compressed/ciphertext/plain sizes, algorithm, key version, associated-data contract, atomic-move result, and report counts. Never log keys or plaintext.

```bash
./gradlew :bluetape4k-graph-okio:test --tests '*GraphIoOkioPathsTest' --tests '*NegativePathTest' --tests '*OkioRoundTripTest'
```

Expected: authentication fails before records are accepted, bounded reads fail without unbounded allocation, and atomic-write failure preserves the old target.

## Related pages and non-goals

See [OkIO security](../graph-io/okio-security.md), [formats](../graph-io/formats.md), and [operations](../guides/operations.md). This module does not manage keys, define an encrypted CSV bundle, make deterministic encryption randomized, or own caller-supplied streams by default.
