package io.bluetape4k.graph.io.okio

import okio.FileSystem
import okio.Path
import okio.Source
import java.io.InputStream

/**
 * OkIO 기반 그래프 임포트 소스.
 *
 * 압축 파라미터는 이 인터페이스에 포함하지 않는다 — 압축 체이닝은 [GraphIoOkioPaths]가 담당한다.
 *
 * 소유권 규칙:
 * - [PathSource]: 라이브러리 소유 — 라이브러리가 open/close 모두 책임진다.
 * - [SourceBased]: `ownsSource=false`(기본) — 호출자 소유. 라이브러리는 import 완료 후 underlying source를 닫지 않는다.
 *   `ownsSource=true`로 명시한 경우에만 라이브러리가 소유권을 인계받아 닫는다.
 * - [InputStreamBased]: `ownsStream=false`(기본) — 호출자 소유. 동일 규칙 적용.
 */
sealed interface OkioGraphImportSource {

    /**
     * 파일 경로 기반 임포트 소스. 라이브러리가 직접 파일을 열고 닫는다.
     *
     * @param path OkIO [Path]
     * @param fileSystem 파일 시스템 (기본값: [FileSystem.SYSTEM])
     */
    data class PathSource(
        val path: Path,
        val fileSystem: FileSystem = FileSystem.SYSTEM,
    ) : OkioGraphImportSource {
        companion object {
            fun from(path: Path, fileSystem: FileSystem = FileSystem.SYSTEM): PathSource =
                PathSource(path, fileSystem)
        }
    }

    /**
     * OkIO [Source] 기반 임포트 소스.
     *
     * @param source OkIO [Source]
     * @param ownsSource `true`이면 import 완료 후 [source]를 닫는다. 기본값 `false` (호출자 소유).
     */
    data class SourceBased(
        val source: Source,
        val ownsSource: Boolean = false,
    ) : OkioGraphImportSource {
        companion object {
            fun from(source: Source, ownsSource: Boolean = false): SourceBased =
                SourceBased(source, ownsSource)
        }
    }

    /**
     * [InputStream] 기반 임포트 소스.
     *
     * @param inputStream [InputStream]
     * @param ownsStream `true`이면 import 완료 후 [inputStream]을 닫는다. 기본값 `false` (호출자 소유).
     */
    data class InputStreamBased(
        val inputStream: InputStream,
        val ownsStream: Boolean = false,
    ) : OkioGraphImportSource {
        companion object {
            fun from(inputStream: InputStream, ownsStream: Boolean = false): InputStreamBased =
                InputStreamBased(inputStream, ownsStream)
        }
    }
}
