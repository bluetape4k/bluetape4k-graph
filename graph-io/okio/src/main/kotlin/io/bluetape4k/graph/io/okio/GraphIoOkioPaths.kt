package io.bluetape4k.graph.io.okio

import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.warn
import io.bluetape4k.okio.compress.Compressable
import io.bluetape4k.okio.tink.DEFAULT_DAEAD_CHUNK_SIZE
import io.bluetape4k.okio.tink.DEFAULT_DAEAD_MAX_CIPHERTEXT_LENGTH
import io.bluetape4k.okio.tink.asDaeadChunkDecryptSource
import io.bluetape4k.okio.tink.asDaeadChunkEncryptSink
import io.bluetape4k.support.requirePositiveNumber
import io.bluetape4k.tink.daead.TinkDeterministicAead
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.ForwardingSink
import okio.ForwardingSource
import okio.Path
import okio.Path.Companion.toPath
import okio.Sink
import okio.Source
import okio.buffer
import okio.sink
import okio.source
import java.io.IOException
import java.util.UUID

/**
 * Opens OkIO graph import sources and export sinks.
 *
 * This helper converts [OkioGraphImportSource] and [OkioGraphExportSink] into [BufferedSource] and
 * [BufferedSink] instances, then applies optional compression, DAEAD chunk encryption, and atomic writes.
 *
 * ### Compression
 * All compression paths use [io.bluetape4k.io.compressor.Compressors.Streaming] variants. Batch helpers such
 * as `Compressable.Sinks.gzip()` are intentionally avoided because large graph exports must not buffer the
 * full payload in heap memory.
 *
 * ### Close ownership
 * Callers must close the returned [BufferedSource] or [BufferedSink]. `PathSource` and `PathSink` are library
 * owned, so closing releases the opened file handle. Source-, sink-, input-stream-, and output-stream-backed
 * variants follow their `ownsXxx` flag.
 *
 * ### Atomic writes
 * `PathSink.atomicWrite=true` writes to a temporary path first, then atomically moves it into place on success.
 * Failed writes delete the temporary file and leave the target path untouched.
 *
 * ### Encryption
 * DAEAD chunk encryption delegates to `bluetape4k-okio`'s `DaeadChunkEncryptSink` and
 * `DaeadChunkDecryptSource`. The encrypted format is deterministic: repeated plaintext chunks encrypted with
 * the same key and associated data produce repeated ciphertext chunks.
 */
object GraphIoOkioPaths : KLogging() {

    /** Default decompression budget: 512 MiB. */
    const val DEFAULT_MAX_DECOMPRESSED_BYTES: Long = 512L * 1024 * 1024

    // ─── Source opening ──────────────────────────────────────────────────────

    /**
     * Opens [source] as a [BufferedSource].
     *
     * The caller must close the returned source.
     *
     * @throws IOException when the path cannot be opened for reading
     */
    @Throws(IOException::class)
    fun openSource(source: OkioGraphImportSource): BufferedSource = when (source) {
        is OkioGraphImportSource.PathSource ->
            source.fileSystem.source(source.path).buffer()

        is OkioGraphImportSource.SourceBased ->
            if (source.ownsSource) source.source.buffer()
            else nonClosingSource(source.source).buffer()

        is OkioGraphImportSource.InputStreamBased ->
            if (source.ownsStream) source.inputStream.source().buffer()
            else nonClosingSource(source.inputStream.source()).buffer()
    }

    // ─── Sink opening ────────────────────────────────────────────────────────

    /**
     * Opens [sink] as a [BufferedSink].
     *
     * `PathSink.atomicWrite=true` writes to a temporary path first and moves it atomically on success.
     * The caller must close the returned sink.
     *
     * @throws IOException when the path cannot be opened for writing
     */
    @Throws(IOException::class)
    fun openSink(sink: OkioGraphExportSink): BufferedSink =
        openSinkHandle(sink).sink

    private fun openSinkHandle(sink: OkioGraphExportSink): OpenedSink = when (sink) {
        is OkioGraphExportSink.PathSink -> openPathSink(sink)

        is OkioGraphExportSink.SinkBased ->
            OpenedSink(
                if (sink.ownsSink) sink.sink.buffer()
                else nonClosingSink(sink.sink).buffer()
            )

        is OkioGraphExportSink.OutputStreamBased -> {
            val rawSink = sink.outputStream.sink()
            OpenedSink(
                if (sink.ownsStream) rawSink.buffer()
                else nonClosingSink(rawSink).buffer()
            )
        }
    }

    // ─── Compression chaining ────────────────────────────────────────────────

    /**
     * Wraps [sink] with [compressor] and returns a compressed [BufferedSink].
     *
     * Optional compressors such as LZ4, Snappy, Zstd, and Bzip2 require their runtime dependency on the
     * classpath.
     *
     * @throws IOException when the compressed stream cannot be initialized
     * @throws IllegalStateException when an optional compressor dependency is missing
     */
    @Throws(IOException::class)
    fun openCompressedSink(sink: BufferedSink, compressor: Compressor): BufferedSink {
        val streaming = compressor.streamingCompressor()
        return Compressable.Sinks.compressableSink(sink, streaming).buffer()
    }

    /**
     * Wraps [source] with [compressor] decompression and a decompression budget guard.
     *
     * [maxDecompressedBytes] limits inflated bytes to reduce decompression-bomb risk.
     *
     * @throws IOException when decompression fails or the budget is exceeded
     * @throws IllegalStateException when an optional compressor dependency is missing
     */
    @Throws(IOException::class)
    fun openDecompressedSource(
        source: BufferedSource,
        compressor: Compressor,
        maxDecompressedBytes: Long = DEFAULT_MAX_DECOMPRESSED_BYTES,
    ): BufferedSource {
        maxDecompressedBytes.requirePositiveNumber("maxDecompressedBytes")
        val streaming = compressor.streamingCompressor()
        val decompressed = Compressable.Sources.decompressableSource(source, streaming)
        return BombGuardSource(decompressed, maxDecompressedBytes).buffer()
    }

    // ─── DAEAD chunk encryption chaining ─────────────────────────────────────

    /**
     * Wraps [sink] with DAEAD chunk encryption.
     *
     * The returned sink writes `[8-byte ciphertext length][ciphertext]` frames through
     * `bluetape4k-okio`'s deterministic DAEAD chunk format. This is not randomized streaming encryption:
     * repeated plaintext chunks encrypted with the same key and [associatedData] produce repeated ciphertext chunks.
     *
     * @param sink target sink receiving encrypted frames
     * @param daead deterministic AEAD primitive used for chunk encryption
     * @param chunkSize plaintext chunk size before encryption
     * @param associatedData authenticated associated data; it is not encrypted
     * @return buffered sink accepting plaintext bytes
     */
    @Throws(IOException::class)
    fun openDaeadEncryptedSink(
        sink: BufferedSink,
        daead: TinkDeterministicAead,
        chunkSize: Int = DEFAULT_DAEAD_CHUNK_SIZE,
        associatedData: ByteArray = ByteArray(0),
    ): BufferedSink =
        sink.asDaeadChunkEncryptSink(daead, chunkSize, associatedData).buffer()

    /**
     * Wraps [source] with DAEAD chunk decryption.
     *
     * [maxCiphertextLength] limits per-chunk allocation for untrusted input. Wrong keys, wrong associated
     * data, truncated chunks, or corrupted ciphertext fail loudly.
     *
     * @param source source containing DAEAD chunk frames
     * @param daead deterministic AEAD primitive used for chunk decryption
     * @param associatedData authenticated associated data used during encryption
     * @param maxCiphertextLength maximum accepted ciphertext frame length
     * @return buffered source yielding plaintext bytes
     */
    @Throws(IOException::class)
    fun openDaeadDecryptedSource(
        source: BufferedSource,
        daead: TinkDeterministicAead,
        associatedData: ByteArray = ByteArray(0),
        maxCiphertextLength: Long = DEFAULT_DAEAD_MAX_CIPHERTEXT_LENGTH,
    ): BufferedSource =
        source.asDaeadChunkDecryptSource(daead, associatedData, maxCiphertextLength).buffer()

    /**
     * Opens [sink] and wraps it with GZip compression.
     *
     * The underlying sink is closed if compression setup fails.
     */
    @Throws(IOException::class)
    fun openGzipSink(sink: OkioGraphExportSink): BufferedSink {
        val opened = openSinkHandle(sink)
        val bs = opened.sink
        return try {
            openCompressedSink(bs, Compressor.GZIP)
        } catch (e: Throwable) {
            opened.abort()
            try { bs.close() } catch (ce: Throwable) { log.warn(ce) { "Failed to close sink after gzip init failure" } }
            throw e
        }
    }

    /**
     * Opens [sink] and wraps it with DAEAD chunk encryption.
     *
     * The returned sink accepts plaintext bytes and writes encrypted DAEAD chunk frames to [sink].
     */
    @Throws(IOException::class)
    fun openDaeadEncryptedSink(
        sink: OkioGraphExportSink,
        daead: TinkDeterministicAead,
        chunkSize: Int = DEFAULT_DAEAD_CHUNK_SIZE,
        associatedData: ByteArray = ByteArray(0),
    ): BufferedSink {
        val opened = openSinkHandle(sink)
        val bs = opened.sink
        return try {
            openDaeadEncryptedSink(bs, daead, chunkSize, associatedData)
        } catch (e: Throwable) {
            opened.abort()
            try { bs.close() } catch (ce: Throwable) { log.warn(ce) { "Failed to close sink after DAEAD init failure" } }
            throw e
        }
    }

    /**
     * Opens [sink] as a compress-then-encrypt GZip + DAEAD chunk sink.
     *
     * Write plaintext graph bytes to the returned sink. The bytes are compressed first and then encrypted.
     */
    @Throws(IOException::class)
    fun openGzipDaeadEncryptedSink(
        sink: OkioGraphExportSink,
        daead: TinkDeterministicAead,
        chunkSize: Int = DEFAULT_DAEAD_CHUNK_SIZE,
        associatedData: ByteArray = ByteArray(0),
    ): BufferedSink {
        val opened = openSinkHandle(sink)
        val bs = opened.sink
        return try {
            val encrypted = openDaeadEncryptedSink(bs, daead, chunkSize, associatedData)
            openCompressedSink(encrypted, Compressor.GZIP)
        } catch (e: Throwable) {
            opened.abort()
            try { bs.close() } catch (ce: Throwable) { log.warn(ce) { "Failed to close sink after gzip+DAEAD init failure" } }
            throw e
        }
    }

    /**
     * Opens [source] and wraps it with GZip decompression.
     *
     * The underlying source is closed if decompression setup fails.
     *
     * @param maxDecompressedBytes inflated-byte budget used to reduce decompression-bomb risk
     */
    @Throws(IOException::class)
    fun openGzipSource(
        source: OkioGraphImportSource,
        maxDecompressedBytes: Long = DEFAULT_MAX_DECOMPRESSED_BYTES,
    ): BufferedSource {
        val bs = openSource(source)
        return try {
            openDecompressedSource(bs, Compressor.GZIP, maxDecompressedBytes)
        } catch (e: Throwable) {
            try { bs.close() } catch (ce: Throwable) { log.warn(ce) { "Failed to close source after gzip init failure" } }
            throw e
        }
    }

    /**
     * Opens [source] and wraps it with DAEAD chunk decryption.
     */
    @Throws(IOException::class)
    fun openDaeadDecryptedSource(
        source: OkioGraphImportSource,
        daead: TinkDeterministicAead,
        associatedData: ByteArray = ByteArray(0),
        maxCiphertextLength: Long = DEFAULT_DAEAD_MAX_CIPHERTEXT_LENGTH,
    ): BufferedSource {
        val bs = openSource(source)
        return try {
            openDaeadDecryptedSource(bs, daead, associatedData, maxCiphertextLength)
        } catch (e: Throwable) {
            try { bs.close() } catch (ce: Throwable) { log.warn(ce) { "Failed to close source after DAEAD init failure" } }
            throw e
        }
    }

    /**
     * Opens [source] as a decrypt-then-inflate DAEAD chunk + GZip source.
     *
     * The input must have been written by [openGzipDaeadEncryptedSink].
     */
    @Throws(IOException::class)
    fun openDaeadDecryptedGzipSource(
        source: OkioGraphImportSource,
        daead: TinkDeterministicAead,
        associatedData: ByteArray = ByteArray(0),
        maxCiphertextLength: Long = DEFAULT_DAEAD_MAX_CIPHERTEXT_LENGTH,
        maxDecompressedBytes: Long = DEFAULT_MAX_DECOMPRESSED_BYTES,
    ): BufferedSource {
        val bs = openSource(source)
        return try {
            val decrypted = openDaeadDecryptedSource(bs, daead, associatedData, maxCiphertextLength)
            openDecompressedSource(decrypted, Compressor.GZIP, maxDecompressedBytes)
        } catch (e: Throwable) {
            try { bs.close() } catch (ce: Throwable) { log.warn(ce) { "Failed to close source after DAEAD+gzip init failure" } }
            throw e
        }
    }

    // ─── Internal helpers ────────────────────────────────────────────────────

    /** Opens a [OkioGraphExportSink.PathSink] and applies the atomic-write strategy when requested. */
    @Throws(IOException::class)
    private fun openPathSink(sink: OkioGraphExportSink.PathSink): OpenedSink {
        val fs = sink.fileSystem
        val target = sink.path

        if (sink.mustExist) {
            check(fs.exists(target)) { "PathSink.mustExist=true but target path does not exist: $target" }
        }
        if (sink.mustCreate) {
            check(!fs.exists(target)) { "PathSink.mustCreate=true but target path already exists: $target" }
        }
        if (sink.createParentDirectories) {
            target.parent?.let { fs.createDirectories(it) }
        }

        if (!sink.atomicWrite) {
            return OpenedSink(fs.sink(target, mustCreate = false).buffer())
        }

        // atomicWrite=true: temporary path, then atomicMove.
        val tmpName = "${target.filename}.tmp.${UUID.randomUUID()}"
        val tmpPath = target.parent?.let { it / tmpName }
            ?: ("${target}.tmp.${UUID.randomUUID()}".toPath())

        val rawSink = fs.sink(tmpPath, mustCreate = false)
        val atomicSink = AtomicMoveSink(rawSink, tmpPath, target, fs)
        return OpenedSink(
            sink = atomicSink.buffer(),
            abort = atomicSink::abort,
        )
    }

    /** Wrapper that does not close the underlying caller-owned source. */
    private fun nonClosingSource(delegate: Source): Source = object : ForwardingSource(delegate) {
        override fun close() { /* caller owns the source — do not close */ }
    }

    /** Wrapper that does not close the underlying caller-owned sink. */
    private fun nonClosingSink(delegate: Sink): Sink = object : ForwardingSink(delegate) {
        override fun close() { /* caller owns the sink — do not close */ }
    }

    /**
     * Source wrapper that enforces a decompression budget.
     *
     * Throws [IOException] once reads exceed [maxBytes]. Intended for single-threaded reads.
     */
    private class BombGuardSource(
        delegate: Source,
        private val maxBytes: Long,
    ) : ForwardingSource(delegate) {
        @Volatile
        private var totalRead: Long = 0L

        override fun read(sink: Buffer, byteCount: Long): Long {
            val n = super.read(sink, byteCount)
            if (n > 0) {
                totalRead += n
                if (totalRead > maxBytes) {
                    throw IOException("decompression budget exceeded: limit=$maxBytes bytes")
                }
            }
            return n
        }
    }

    private data class OpenedSink(
        val sink: BufferedSink,
        private val abort: (() -> Unit)? = null,
    ) {
        fun abort() {
            abort?.invoke()
        }
    }

    /**
     * Sink wrapper that atomically moves a temporary file to the target path on close.
     *
     * If the write fails before close completes, the temporary path is deleted.
     */
    private class AtomicMoveSink(
        delegate: Sink,
        private val tmpPath: Path,
        private val targetPath: Path,
        private val fs: FileSystem,
    ) : ForwardingSink(delegate) {
        @Volatile
        private var failed = false

        fun abort() {
            failed = true
        }

        override fun write(source: Buffer, byteCount: Long) {
            try {
                super.write(source, byteCount)
            } catch (e: Exception) {
                failed = true
                throw e
            }
        }

        override fun close() {
            try {
                super.close()
                if (failed) {
                    try { fs.delete(tmpPath) } catch (e: IOException) {
                        GraphIoOkioPaths.log.warn(e) { "Failed to delete temp file after write failure: $tmpPath" }
                    }
                } else {
                    fs.atomicMove(tmpPath, targetPath)
                }
            } catch (e: Exception) {
                failed = true
                try { fs.delete(tmpPath) } catch (de: IOException) {
                    GraphIoOkioPaths.log.warn(de) { "Failed to delete temp file after close failure: $tmpPath" }
                }
                throw e
            }
        }
    }
}

// ─── okio.Path convenience extension ─────────────────────────────────────────
/** Last segment of an OkIO path. */
private val okio.Path.filename: String get() = segments.last()
