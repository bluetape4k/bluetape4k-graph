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
 * OkIO 그래프 임포트 소스와 익스포트 싱크를 연다.
 *
 * 이 helper는 [OkioGraphImportSource]와 [OkioGraphExportSink]를 [BufferedSource]와 [BufferedSink]로 변환한 뒤
 * 선택적 압축, DAEAD chunk 암호화, atomic write를 적용한다.
 *
 * ### 압축
 * 모든 압축 경로는 [io.bluetape4k.io.compressor.Compressors.Streaming] 변형을 사용한다.
 * `Compressable.Sinks.gzip()` 같은 배치 helper는 의도적으로 피한다. 큰 그래프 익스포트가 전체 payload를
 * heap 메모리에 버퍼링하면 안 되기 때문이다.
 *
 * ### close 소유권
 * 호출자는 반환된 [BufferedSource] 또는 [BufferedSink]를 닫아야 한다. `PathSource`와 `PathSink`는 라이브러리
 * 소유이므로 close 시 열린 파일 handle을 해제한다. source, sink, input-stream, output-stream 기반 변형은
 * 각자의 `ownsXxx` flag를 따른다.
 *
 * ### Atomic write
 * `PathSink.atomicWrite=true`이면 먼저 임시 경로에 쓰고, 성공 시 대상 경로로 atomic move 한다.
 * 쓰기에 실패하면 임시 파일을 삭제하고 대상 경로는 건드리지 않는다.
 *
 * ### 암호화
 * DAEAD chunk 암호화는 `bluetape4k-okio`의 `DaeadChunkEncryptSink`와 `DaeadChunkDecryptSource`에 위임한다.
 * 암호화 포맷은 deterministic이다. 같은 key와 associated data로 반복 plaintext chunk를 암호화하면 반복 ciphertext
 * chunk가 생성된다.
 */
object GraphIoOkioPaths : KLogging() {

    /** 기본 압축 해제 budget: 512 MiB. */
    const val DEFAULT_MAX_DECOMPRESSED_BYTES: Long = 512L * 1024 * 1024

    // ─── Source 열기 ─────────────────────────────────────────────────────────

    /**
     * [source]를 [BufferedSource]로 연다.
     *
     * 호출자는 반환된 source를 닫아야 한다.
     *
     * @throws IOException 경로를 읽기용으로 열 수 없는 경우.
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

    // ─── Sink 열기 ───────────────────────────────────────────────────────────

    /**
     * [sink]를 [BufferedSink]로 연다.
     *
     * `PathSink.atomicWrite=true`이면 먼저 임시 경로에 쓰고, 성공 시 atomic move 한다.
     * 호출자는 반환된 sink를 닫아야 한다.
     *
     * @throws IOException 경로를 쓰기용으로 열 수 없는 경우.
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

    // ─── 압축 체이닝 ──────────────────────────────────────────────────────────

    /**
     * [sink]를 [compressor]로 감싸 압축 [BufferedSink]를 반환한다.
     *
     * LZ4, Snappy, Zstd, Bzip2 같은 선택 압축기는 런타임 의존성이 classpath에 있어야 한다.
     *
     * @throws IOException 압축 스트림을 초기화할 수 없는 경우.
     * @throws IllegalStateException 선택 압축기 의존성이 누락된 경우.
     */
    @Throws(IOException::class)
    fun openCompressedSink(sink: BufferedSink, compressor: Compressor): BufferedSink {
        val streaming = compressor.streamingCompressor()
        return Compressable.Sinks.compressableSink(sink, streaming).buffer()
    }

    /**
     * [source]를 [compressor] 압축 해제와 압축 해제 budget guard로 감싼다.
     *
     * [maxDecompressedBytes]는 압축 해제된 byte 수를 제한해 decompression bomb 위험을 줄인다.
     *
     * @throws IOException 압축 해제에 실패하거나 budget을 초과한 경우.
     * @throws IllegalStateException 선택 압축기 의존성이 누락된 경우.
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

    // ─── DAEAD chunk 암호화 체이닝 ────────────────────────────────────────────

    /**
     * [sink]를 DAEAD chunk 암호화로 감싼다.
     *
     * 반환된 sink는 `bluetape4k-okio`의 deterministic DAEAD chunk 포맷으로
     * `[8-byte ciphertext length][ciphertext]` frame을 쓴다. 이는 randomized streaming encryption이 아니다.
     * 같은 key와 [associatedData]로 반복 plaintext chunk를 암호화하면 반복 ciphertext chunk가 생성된다.
     *
     * @param sink 암호화된 frame을 받을 대상 sink.
     * @param daead chunk 암호화에 사용할 deterministic AEAD primitive.
     * @param chunkSize 암호화 전 plaintext chunk 크기.
     * @param associatedData 인증에 포함되는 associated data. 암호화되지는 않는다.
     * @return plaintext byte를 받는 buffered sink.
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
     * [source]를 DAEAD chunk 복호화로 감싼다.
     *
     * [maxCiphertextLength]는 신뢰할 수 없는 입력에 대해 chunk별 allocation을 제한한다. 잘못된 key,
     * 잘못된 associated data, 잘린 chunk, 손상된 ciphertext는 명확히 실패한다.
     *
     * @param source DAEAD chunk frame을 포함한 source.
     * @param daead chunk 복호화에 사용할 deterministic AEAD primitive.
     * @param associatedData 암호화 시 인증에 사용한 associated data.
     * @param maxCiphertextLength 허용할 최대 ciphertext frame 길이.
     * @return plaintext byte를 제공하는 buffered source.
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
     * [sink]를 열고 GZip 압축으로 감싼다.
     *
     * 압축 설정에 실패하면 하위 sink를 닫는다.
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
     * [sink]를 열고 DAEAD chunk 암호화로 감싼다.
     *
     * 반환된 sink는 plaintext byte를 받아 암호화된 DAEAD chunk frame을 [sink]에 쓴다.
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
     * [sink]를 GZip 압축 후 DAEAD chunk 암호화(compress-then-encrypt) sink로 연다.
     *
     * 반환된 sink에는 plaintext graph byte를 쓴다. byte는 먼저 압축되고 그다음 암호화된다.
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
     * [source]를 열고 GZip 압축 해제로 감싼다.
     *
     * 압축 해제 설정에 실패하면 하위 source를 닫는다.
     *
     * @param maxDecompressedBytes decompression bomb 위험을 줄이기 위한 압축 해제 byte budget.
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
     * [source]를 열고 DAEAD chunk 복호화로 감싼다.
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
     * [source]를 DAEAD chunk 복호화 후 GZip 압축 해제(decrypt-then-inflate) source로 연다.
     *
     * 입력은 [openGzipDaeadEncryptedSink]로 기록된 데이터여야 한다.
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

    // ─── 내부 헬퍼 ────────────────────────────────────────────────────────────

    /** [OkioGraphExportSink.PathSink]를 열고 요청된 경우 atomic-write 전략을 적용한다. */
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

        // atomicWrite=true: 임시 경로에 쓴 뒤 atomicMove 한다.
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

    /** 호출자가 소유한 하위 source를 닫지 않는 wrapper. */
    private fun nonClosingSource(delegate: Source): Source = object : ForwardingSource(delegate) {
        override fun close() { /* 호출자가 source를 소유하므로 닫지 않는다 */ }
    }

    /** 호출자가 소유한 하위 sink를 닫지 않는 wrapper. */
    private fun nonClosingSink(delegate: Sink): Sink = object : ForwardingSink(delegate) {
        override fun close() { /* 호출자가 sink를 소유하므로 닫지 않는다 */ }
    }

    /**
     * 압축 해제 budget을 강제하는 source wrapper.
     *
     * 읽은 byte가 [maxBytes]를 초과하면 [IOException]을 던진다. 단일 thread 읽기를 전제로 한다.
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
     * close 시 임시 파일을 대상 경로로 atomic move 하는 sink wrapper.
     *
     * close가 완료되기 전에 쓰기가 실패하면 임시 경로를 삭제한다.
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

// ─── okio.Path 편의 확장 ─────────────────────────────────────────────────────
/** OkIO path의 마지막 segment. */
private val okio.Path.filename: String get() = segments.last()
