package io.bluetape4k.graph.io.source

import java.io.OutputStream
import java.io.Serializable
import java.nio.charset.Charset
import java.nio.file.Path

/** 그래프 익스포트 데이터 싱크. 파일 경로 또는 OutputStream을 지원한다. */
sealed interface GraphExportSink {

    /** 파일 경로 기반 익스포트 싱크 */
    data class PathSink(
        val path: Path,
        val charset: Charset = Charsets.UTF_8,
        val append: Boolean = false,
    ) : GraphExportSink, Serializable {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    /** OutputStream 기반 익스포트 싱크. `closeOutput = true`이면 완료 후 스트림을 닫는다. */
    data class OutputStreamSink(
        val output: OutputStream,
        val charset: Charset = Charsets.UTF_8,
        val closeOutput: Boolean = false,
    ) : GraphExportSink
}
