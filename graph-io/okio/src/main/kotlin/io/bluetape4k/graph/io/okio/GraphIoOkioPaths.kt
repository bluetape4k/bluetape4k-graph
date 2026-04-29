package io.bluetape4k.graph.io.okio

import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.warn
import io.bluetape4k.okio.compress.Compressable
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
 * OkIO 기반 그래프 I/O 경로 헬퍼.
 *
 * [OkioGraphImportSource] / [OkioGraphExportSink]를 [BufferedSource] / [BufferedSink]로 열고,
 * 압축 체이닝 및 원자적 쓰기(atomicWrite)를 지원한다.
 *
 * ### 압축 규칙
 * 모든 압축 경로는 [io.bluetape4k.io.compressor.Compressors.Streaming] 변형을 사용한다.
 * `Compressable.Sinks.gzip()` 등 배치 변형은 대용량 그래프 export 시 메모리를 전량 버퍼링하므로 사용하지 않는다.
 *
 * ### close 책임
 * 반환된 [BufferedSource] / [BufferedSink]는 호출자가 닫아야 한다.
 * `PathSource` / `PathSink`는 라이브러리 소유 — close 시 파일 핸들이 해제된다.
 * `SourceBased` / `SinkBased` / `InputStreamBased` / `OutputStreamBased`는 `ownsXxx` 파라미터에 따라 결정된다.
 *
 * ### atomicWrite (PathSink)
 * `atomicWrite=true`(기본)이면 임시 파일에 기록 후 성공 시 atomic move. 실패 시 임시 파일 삭제.
 * 부분 기록으로 인한 대상 파일 손상을 방지한다.
 *
 * ### v2 예정
 * 암호화 지원 (bluetape4k-projects #240 완료 후) — `TinkEncryptSink` / `TinkDecryptSource` 체이닝 추가 예정.
 */
object GraphIoOkioPaths : KLogging() {

    /** 압축 해제 기본 최대 크기 (512 MiB). decompression bomb 방지. */
    const val DEFAULT_MAX_DECOMPRESSED_BYTES: Long = 512L * 1024 * 1024

    // ─── Source 열기 ───────────────────────────────────────────────────────────

    /**
     * [source]로부터 [BufferedSource]를 연다.
     *
     * 반환된 [BufferedSource]는 호출자가 닫아야 한다.
     *
     * @throws IOException 파일을 찾을 수 없거나 읽기 권한 없을 때
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

    // ─── Sink 열기 ─────────────────────────────────────────────────────────────

    /**
     * [sink]로부터 [BufferedSink]를 연다.
     *
     * `PathSink.atomicWrite=true`(기본)이면 임시 파일에 기록 후 성공 시 atomic move.
     * 반환된 [BufferedSink]는 호출자가 닫아야 한다.
     *
     * @throws IOException 파일 생성/열기 실패 시
     */
    @Throws(IOException::class)
    fun openSink(sink: OkioGraphExportSink): BufferedSink = when (sink) {
        is OkioGraphExportSink.PathSink -> openPathSink(sink)

        is OkioGraphExportSink.SinkBased ->
            if (sink.ownsSink) sink.sink.buffer()
            else nonClosingSink(sink.sink).buffer()

        is OkioGraphExportSink.OutputStreamBased -> {
            val rawSink = sink.outputStream.sink()
            if (sink.ownsStream) rawSink.buffer()
            else nonClosingSink(rawSink).buffer()
        }
    }

    // ─── 압축 체이닝 ───────────────────────────────────────────────────────────

    /**
     * [sink]에 [compressor] 압축을 체이닝한 [BufferedSink]를 반환한다.
     *
     * 스트리밍 압축기([io.bluetape4k.io.compressor.Compressors.Streaming])를 사용한다.
     * 선택 의존성(LZ4/Snappy/Zstd/Bzip2)이 클래스패스에 없으면 [IllegalStateException].
     *
     * @throws IOException 압축 스트림 초기화 실패 시
     * @throws IllegalStateException 선택 의존성 누락 시
     */
    @Throws(IOException::class)
    fun openCompressedSink(sink: BufferedSink, compressor: Compressor): BufferedSink {
        val streaming = compressor.streamingCompressor()
        return Compressable.Sinks.compressableSink(sink, streaming).buffer()
    }

    /**
     * [source]에 [compressor] 압축 해제를 체이닝한 [BufferedSource]를 반환한다.
     *
     * [maxDecompressedBytes]를 초과하면 [IOException]("decompression budget exceeded")을 던진다.
     * decompression bomb 공격 방지.
     *
     * @throws IOException 압축 해제 중 오류 또는 폭탄 탐지 시
     * @throws IllegalStateException 선택 의존성 누락 시
     */
    @Throws(IOException::class)
    fun openDecompressedSource(
        source: BufferedSource,
        compressor: Compressor,
        maxDecompressedBytes: Long = DEFAULT_MAX_DECOMPRESSED_BYTES,
    ): BufferedSource {
        require(maxDecompressedBytes > 0) { "maxDecompressedBytes must be positive: $maxDecompressedBytes" }
        val streaming = compressor.streamingCompressor()
        val decompressed = Compressable.Sources.decompressableSource(source, streaming)
        return BombGuardSource(decompressed, maxDecompressedBytes).buffer()
    }

    /**
     * GZip 압축 체이닝 편의 함수.
     *
     * 내부적으로 [Compressors.Streaming.GZip]을 사용한다.
     * 압축 초기화 실패 시 underlying sink를 닫고 예외를 재던진다.
     */
    @Throws(IOException::class)
    fun openGzipSink(sink: OkioGraphExportSink): BufferedSink {
        val bs = openSink(sink)
        return try {
            openCompressedSink(bs, Compressor.GZIP)
        } catch (e: Throwable) {
            try { bs.close() } catch (ce: Throwable) { log.warn(ce) { "Failed to close sink after gzip init failure" } }
            throw e
        }
    }

    /**
     * GZip 압축 해제 체이닝 편의 함수.
     *
     * 압축 초기화 실패 시 underlying source를 닫고 예외를 재던진다.
     *
     * @param maxDecompressedBytes decompression bomb 방지 한계 (기본 512 MiB)
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

    // ─── 내부 헬퍼 ─────────────────────────────────────────────────────────────

    /** PathSink: atomicWrite 전략 적용하여 BufferedSink를 연다. */
    @Throws(IOException::class)
    private fun openPathSink(sink: OkioGraphExportSink.PathSink): BufferedSink {
        val fs = sink.fileSystem
        val target = sink.path

        if (sink.mustExist) {
            check(fs.exists(target)) { "PathSink.mustExist=true 이지만 대상 파일이 없음: $target" }
        }
        if (sink.mustCreate) {
            check(!fs.exists(target)) { "PathSink.mustCreate=true 이지만 대상 파일이 이미 존재함: $target" }
        }
        if (sink.createParentDirectories) {
            target.parent?.let { fs.createDirectories(it) }
        }

        if (!sink.atomicWrite) {
            return fs.sink(target, mustCreate = false).buffer()
        }

        // atomicWrite=true: 임시파일 → atomicMove
        val tmpName = "${target.filename}.tmp.${UUID.randomUUID()}"
        val tmpPath = target.parent?.let { it / tmpName }
            ?: ("${target}.tmp.${UUID.randomUUID()}".toPath())

        val rawSink = fs.sink(tmpPath, mustCreate = false)
        return AtomicMoveSink(rawSink, tmpPath, target, fs).buffer()
    }

    /** close 시 underlying source를 닫지 않는 래퍼 (호출자 소유). */
    private fun nonClosingSource(delegate: Source): Source = object : ForwardingSource(delegate) {
        override fun close() { /* caller owns the source — do not close */ }
    }

    /** close 시 underlying sink를 닫지 않는 래퍼 (호출자 소유). */
    private fun nonClosingSink(delegate: Sink): Sink = object : ForwardingSink(delegate) {
        override fun close() { /* caller owns the sink — do not close */ }
    }

    /**
     * decompression bomb 방지용 Source 래퍼.
     *
     * [maxBytes]를 초과하면 [IOException]을 던진다. 단일 스레드 전용.
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

    /**
     * close 시 임시 파일을 target으로 atomic move하는 Sink 래퍼.
     *
     * close 전 예외 발생 시 tmpPath를 삭제한다.
     */
    private class AtomicMoveSink(
        delegate: Sink,
        private val tmpPath: Path,
        private val targetPath: Path,
        private val fs: FileSystem,
    ) : ForwardingSink(delegate) {
        @Volatile
        private var failed = false

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

// ─── okio.Path 편의 확장 ───────────────────────────────────────────────────────
/** OkIO Path의 파일명 부분 (마지막 세그먼트). */
private val okio.Path.filename: String get() = segments.last()
