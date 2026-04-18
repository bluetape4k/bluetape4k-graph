package io.bluetape4k.graph.io.source

import java.io.InputStream
import java.io.Serializable
import java.nio.charset.Charset
import java.nio.file.Path

/** 그래프 임포트 데이터 소스. 파일 경로 또는 InputStream을 지원한다. */
sealed interface GraphImportSource {

    /** 파일 경로 기반 임포트 소스 */
    data class PathSource(
        val path: Path,
        val charset: Charset = Charsets.UTF_8,
    ) : GraphImportSource, Serializable {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    /** InputStream 기반 임포트 소스. `closeInput = true`이면 완료 후 스트림을 닫는다. */
    data class InputStreamSource(
        val input: InputStream,
        val charset: Charset = Charsets.UTF_8,
        val closeInput: Boolean = false,
    ) : GraphImportSource
}
