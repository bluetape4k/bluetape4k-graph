# OkIO, compression, and file security

![graph-io pipeline](../../assets/graph-io/graph-io-pipeline.png)

`graph-okio` connects graph formats to OkIO `Source`, `Sink`, `Path`, and `FileSystem`. It supports GZIP, DEFLATE, LZ4, SNAPPY, ZSTD, and BZIP2 through streaming compressors defined in [`Compressor.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/Compressor.kt).

For single-stream NDJSON or GraphML, DAEAD chunk helpers authenticate and encrypt data. Compression is applied before encryption; import reverses the order. Associated data must match. Deterministic encryption leaks equality of identical chunks under the same key/context, so decide whether that property is acceptable. The exact chain and size limits are in [`GraphIoOkioPaths.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/GraphIoOkioPaths.kt).

High-level DAEAD helpers reject CSV because CSV is a file pair. Use explicit low-level wrappers only after defining how both files share keys, associated data, naming, and atomic publication. See [`OkioRoundTripTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/okio/src/test/kotlin/io/bluetape4k/graph/io/okio/OkioRoundTripTest.kt).

Test wrong associated data, truncated ciphertext, truncated compression streams, decompression limits, XXE rejection, source/sink ownership, and atomic-write cleanup. Release negative-path evidence: [`GraphIoOkioPathsTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/okio/src/test/kotlin/io/bluetape4k/graph/io/okio/GraphIoOkioPathsTest.kt), [`NegativePathTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/okio/src/test/kotlin/io/bluetape4k/graph/io/okio/NegativePathTest.kt).

## Compress, authenticate, and import

```kotlin
val fs = FileSystem.SYSTEM
val path = "graph.ndjson.gz.daead".toPath()
val sink = OkioGraphExportSink.PathSink(path, fs, atomicWrite = true)
val source = OkioGraphImportSource.PathSource(path, fs)
val context = "tenant=acme;format=graph-0.5".encodeToByteArray()
val daead = TinkDaeads.AES256_SIV

val out = OkioGraphBulkExporter().exportGraphGzipDaead(
    sink, GraphIoFormat.NDJSON_JACKSON3, daead, sourceOps,
    GraphExportOptions(setOf("Person"), setOf("KNOWS")), associatedData = context,
)
val input = OkioGraphBulkImporter().importGraphDaeadGzip(
    source, GraphIoFormat.NDJSON_JACKSON3, daead, targetOps,
    associatedData = context, maxCiphertextLength = 64L * 1024 * 1024,
    maxDecompressedBytes = 256L * 1024 * 1024,
)
check(out.verticesWritten == input.verticesCreated)
```

The write chain is graph bytes → GZip → DAEAD chunks → atomic temporary file → move. Read reverses it. Retry with `associatedData = "wrong"...`: authentication must fail before graph records are accepted. Truncate ciphertext or gzip: expect an I/O/authentication failure. Set either maximum below the payload: expect bounded failure, not allocation growth. Inject a sink write error with `atomicWrite=true`; the old target must remain and the temporary file must be removed. If any check differs, inspect wrapper order and ownership first; do not retry corrupted input as a database failure.

## Run the negative-path evidence

```bash
./gradlew :bluetape4k-graph-okio:test --tests '*GraphIoOkioPathsTest' --tests '*NegativePathTest' --tests '*OkioRoundTripTest'
```
