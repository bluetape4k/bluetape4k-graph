package io.bluetape4k.graph.io.support

import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.io.source.GraphImportSource
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.FilterInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.file.Files
import java.nio.file.StandardOpenOption

/**
 * 임포트 소스/익스포트 싱크에서 BufferedReader/Writer/InputStream/OutputStream을 열고,
 * 부모 디렉터리를 자동 생성하는 헬퍼.
 *
 * 모든 `close*` 플래그가 `false`인 경우, 반환된 스트림/리더/라이터를 닫아도
 * underlying 스트림이 닫히지 않는다. 스트림 소유권은 호출자가 유지한다.
 */
object GraphIoPaths {

    /**
     * [source]로부터 [BufferedReader]를 열어 반환한다.
     *
     * @param source 입력 소스. [GraphImportSource.InputStreamSource.closeInput]이 `false`이면
     *               반환된 reader를 닫아도 underlying stream이 닫히지 않는다.
     * @return 내용을 읽을 수 있는 [BufferedReader].
     * @throws java.io.IOException 파일이 없거나 읽기 권한이 없을 때.
     */
    fun openReader(source: GraphImportSource): BufferedReader = when (source) {
        is GraphImportSource.PathSource ->
            Files.newBufferedReader(source.path, source.charset)
        is GraphImportSource.InputStreamSource -> {
            val isr = InputStreamReader(source.input, source.charset)
            if (source.closeInput) BufferedReader(isr)
            else object : BufferedReader(isr) {
                override fun close() { /* caller owns the stream */ }
            }
        }
    }

    /**
     * [sink]에 쓸 수 있는 [BufferedWriter]를 열어 반환한다.
     *
     * @param sink 출력 대상. [GraphExportSink.PathSink]는 부모 디렉터리를 자동 생성한다.
     *             [GraphExportSink.OutputStreamSink.closeOutput]이 `false`이면
     *             writer를 닫아도 underlying stream이 닫히지 않고 flush만 실행된다.
     * @return 내용을 쓸 수 있는 [BufferedWriter].
     * @throws java.io.IOException 파일을 생성하거나 쓸 수 없을 때.
     */
    fun openWriter(sink: GraphExportSink): BufferedWriter = when (sink) {
        is GraphExportSink.PathSink -> {
            sink.path.parent?.let { Files.createDirectories(it) }
            val opts = if (sink.append)
                arrayOf(StandardOpenOption.CREATE, StandardOpenOption.APPEND)
            else
                arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
            Files.newBufferedWriter(sink.path, sink.charset, *opts)
        }
        is GraphExportSink.OutputStreamSink -> {
            val osw = OutputStreamWriter(sink.output, sink.charset)
            if (sink.closeOutput) BufferedWriter(osw)
            else object : BufferedWriter(osw) {
                override fun close() { flush() /* caller owns the stream */ }
            }
        }
    }

    /**
     * [source]로부터 바이너리 [InputStream]을 열어 반환한다.
     *
     * [GraphImportSource.InputStreamSource.closeInput]이 `false`이면 반환된 스트림을 닫아도
     * underlying stream이 닫히지 않는다. [GraphImportSource.PathSource]는 항상 버퍼링된 스트림을 반환한다.
     *
     * @param source 입력 소스.
     * @return 바이너리 데이터를 읽을 수 있는 [InputStream].
     * @throws java.io.IOException 파일이 없거나 읽기 권한이 없을 때.
     */
    fun openInputStream(source: GraphImportSource): InputStream = when (source) {
        is GraphImportSource.PathSource -> BufferedInputStream(Files.newInputStream(source.path))
        is GraphImportSource.InputStreamSource ->
            if (source.closeInput) source.input
            // FilterInputStream이 skip/mark/reset/available 등을 자동 위임하므로
            // close()만 억제하면 된다.
            else object : FilterInputStream(source.input) {
                override fun close() { /* caller owns the stream */ }
            }
    }

    /**
     * [sink]에 쓸 수 있는 버퍼링된 [OutputStream]을 열어 반환한다.
     *
     * [GraphExportSink.PathSink]는 부모 디렉터리를 자동 생성하고 항상 버퍼링된 스트림을 반환한다.
     * [GraphExportSink.OutputStreamSink.closeOutput]이 `false`이면 스트림을 닫아도
     * underlying stream이 닫히지 않고 flush만 실행된다.
     *
     * @param sink 출력 대상.
     * @return 버퍼링된 바이너리 [OutputStream].
     * @throws java.io.IOException 파일을 생성하거나 쓸 수 없을 때.
     */
    fun openOutputStream(sink: GraphExportSink): OutputStream = when (sink) {
        is GraphExportSink.PathSink -> {
            sink.path.parent?.let { Files.createDirectories(it) }
            val opts = if (sink.append)
                arrayOf(StandardOpenOption.CREATE, StandardOpenOption.APPEND)
            else
                arrayOf(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)
            BufferedOutputStream(Files.newOutputStream(sink.path, *opts))
        }
        is GraphExportSink.OutputStreamSink -> {
            if (sink.closeOutput) BufferedOutputStream(sink.output)
            else object : OutputStream() {
                private val buf = BufferedOutputStream(sink.output)
                override fun write(b: Int) = buf.write(b)
                override fun write(b: ByteArray, off: Int, len: Int) = buf.write(b, off, len)
                override fun flush() = buf.flush()
                override fun close() { flush() /* caller owns the stream */ }
            }
        }
    }

    /**
     * [source]의 경로 설명 문자열을 반환한다. [GraphImportSource.InputStreamSource]는 `null`을 반환한다.
     *
     * @param source 입력 소스.
     * @return 경로 문자열, 또는 경로를 알 수 없는 경우 `null`.
     */
    fun describeSource(source: GraphImportSource): String? = when (source) {
        is GraphImportSource.PathSource -> source.path.toString()
        is GraphImportSource.InputStreamSource -> null
    }
}
