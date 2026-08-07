# graph-okio

## Before you run

`graph-okio` adapts graph formats to OkIO `Source`, `Sink`, `Path`, and `FileSystem`. It adds compression chaining, atomic path writes, FakeFileSystem tests, and deterministic AEAD chunk encryption for single-stream formats. Choose it for OkIO pipelines; avoid adding it when plain NIO paths are sufficient. Source: [GraphIoOkioPaths.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/GraphIoOkioPaths.kt).


Execution mode: **release-fixture linked**. `sourceOps`, `targetOps`, the temporary OkIO path, and test key material are fixture-owned; `NegativePathTest` and `OkioRoundTripTest` close them and verify cleanup.

## Run

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

## Operations checklist

- Compare report counts with durable graph counts.
- Record the format, batch size, policy, and failed phase.
- Set input size and buffering limits.
- Close library-owned paths and caller-owned streams at their documented boundary.

## Failure and recovery

Symptom: authentication, decompression, or atomic move fails. Do not retry corrupted input; preserve the old target, remove verified temp files, correct key/context or limits, and restart from a clean source.

Test wrong associated data, truncated ciphertext, truncated compression, size limits, XXE for GraphML, sink failure, and temporary-file cleanup. Observe compressed/ciphertext/plain sizes, algorithm, key version, associated-data contract, atomic-move result, and report counts. Never log keys or plaintext.

```bash
./gradlew :bluetape4k-graph-okio:test --tests '*GraphIoOkioPathsTest' --tests '*NegativePathTest' --tests '*OkioRoundTripTest'
```

## Expected result

Expected: authentication fails before records are accepted, bounded reads fail without unbounded allocation, and atomic-write failure preserves the old target.

## Complete release example

The pinned [NegativePathTest](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/okio/src/test/kotlin/io/bluetape4k/graph/io/okio/NegativePathTest.kt) defines every fixture variable and is the complete executable release example. Run:

```bash
./gradlew :bluetape4k-graph-okio:test --tests '*NegativePathTest'
```

Expected: the round trip or negative-path assertions pass and all fixture-owned resources are closed.

## Non-goals and related guides

See [OkIO security](../graph-io/okio-security.md), [formats](../graph-io/formats.md), and [operations](../guides/operations.md). This module does not manage keys, define an encrypted CSV bundle, make deterministic encryption randomized, or own caller-supplied streams by default.
