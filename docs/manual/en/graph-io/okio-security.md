# OkIO, compression, and file security

`graph-okio` connects graph formats to OkIO `Source`, `Sink`, `Path`, and `FileSystem`. It supports GZIP, DEFLATE, LZ4, SNAPPY, ZSTD, and BZIP2 through streaming compressors defined in [`Compressor.kt`](../../../../graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/Compressor.kt).

For single-stream NDJSON or GraphML, DAEAD chunk helpers authenticate and encrypt data. Compression is applied before encryption; import reverses the order. Associated data must match. Deterministic encryption leaks equality of identical chunks under the same key/context, so decide whether that property is acceptable. The exact chain and size limits are in [`GraphIoOkioPaths.kt`](../../../../graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/GraphIoOkioPaths.kt).

High-level DAEAD helpers reject CSV because CSV is a file pair. Use explicit low-level wrappers only after defining how both files share keys, associated data, naming, and atomic publication. See [`OkioRoundTripTest.kt`](../../../../graph-io/okio/src/test/kotlin/io/bluetape4k/graph/io/okio/OkioRoundTripTest.kt).

Test wrong associated data, truncated ciphertext, truncated compression streams, decompression limits, XXE rejection, source/sink ownership, and atomic-write cleanup. Release negative-path evidence: [`GraphIoOkioPathsTest.kt`](../../../../graph-io/okio/src/test/kotlin/io/bluetape4k/graph/io/okio/GraphIoOkioPathsTest.kt), [`NegativePathTest.kt`](../../../../graph-io/okio/src/test/kotlin/io/bluetape4k/graph/io/okio/NegativePathTest.kt).
