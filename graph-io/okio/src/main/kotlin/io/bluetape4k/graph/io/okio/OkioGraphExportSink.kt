package io.bluetape4k.graph.io.okio

import okio.FileSystem
import okio.Path
import okio.Sink
import java.io.OutputStream

/**
 * OkIO 기반 그래프 익스포트 싱크.
 *
 * 압축 파라미터는 이 인터페이스에 포함하지 않는다 — 압축 체이닝은 [GraphIoOkioPaths]가 담당한다.
 *
 * 소유권 규칙:
 * - [PathSink]: 라이브러리 소유 — 라이브러리가 open/close 모두 책임진다.
 * - [SinkBased]: `ownsSink=false`(기본) — 호출자 소유. 라이브러리는 export 완료 후 underlying sink를 닫지 않는다.
 *   `ownsSink=true`로 명시한 경우에만 라이브러리가 소유권을 인계받아 닫는다.
 * - [OutputStreamBased]: `ownsStream=false`(기본) — 호출자 소유. 동일 규칙 적용.
 */
sealed interface OkioGraphExportSink {

    /**
     * 파일 경로 기반 익스포트 싱크. 라이브러리가 직접 파일을 열고 닫는다.
     *
     * [atomicWrite] = `true`(기본)이면 임시 파일(`<target>.tmp.<random>`)에 기록하고 성공 시
     * [FileSystem.atomicMove]로 교체한다. 실패 시 임시 파일을 삭제한다. 부분 기록으로 인한 대상 파일 손상을 방지한다.
     *
     * @param path OkIO [Path]
     * @param fileSystem 파일 시스템 (기본값: [FileSystem.SYSTEM])
     * @param mustCreate `true`이면 파일이 이미 존재하면 예외를 던진다.
     * @param mustExist `true`이면 파일이 존재하지 않으면 예외를 던진다.
     * @param createParentDirectories `true`(기본)이면 부모 디렉토리가 없을 경우 자동 생성한다.
     * @param atomicWrite `true`(기본)이면 임시 파일에 쓰고 성공 시 atomic move.
     */
    data class PathSink(
        val path: Path,
        val fileSystem: FileSystem = FileSystem.SYSTEM,
        val mustCreate: Boolean = false,
        val mustExist: Boolean = false,
        val createParentDirectories: Boolean = true,
        val atomicWrite: Boolean = true,
    ) : OkioGraphExportSink {
        companion object {
            /**
             * [PathSink]를 생성하는 팩토리 함수.
             *
             * @param path 쓰기 대상 OkIO [Path]
             * @param fileSystem 파일 시스템 (기본값: [FileSystem.SYSTEM])
             * @param mustCreate `true`이면 파일 이미 존재 시 예외
             * @param mustExist `true`이면 파일 없을 시 예외
             * @param createParentDirectories `true`(기본)이면 부모 디렉토리 자동 생성
             * @param atomicWrite `true`(기본)이면 임시 파일에 쓰고 성공 시 atomic move
             * @return 설정된 [PathSink] 인스턴스
             */
            fun from(
                path: Path,
                fileSystem: FileSystem = FileSystem.SYSTEM,
                mustCreate: Boolean = false,
                mustExist: Boolean = false,
                createParentDirectories: Boolean = true,
                atomicWrite: Boolean = true,
            ): PathSink = PathSink(path, fileSystem, mustCreate, mustExist, createParentDirectories, atomicWrite)
        }
    }

    /**
     * OkIO [Sink] 기반 익스포트 싱크.
     *
     * @param sink OkIO [Sink]
     * @param ownsSink `true`이면 export 완료 후 [sink]를 닫는다. 기본값 `false` (호출자 소유).
     */
    data class SinkBased(
        val sink: Sink,
        val ownsSink: Boolean = false,
    ) : OkioGraphExportSink {
        companion object {
            /**
             * [SinkBased]를 생성하는 팩토리 함수.
             *
             * @param sink OkIO [Sink]
             * @param ownsSink `true`이면 완료 후 [sink] 닫음 (기본: `false`)
             * @return 설정된 [SinkBased] 인스턴스
             */
            fun from(sink: Sink, ownsSink: Boolean = false): SinkBased =
                SinkBased(sink, ownsSink)
        }
    }

    /**
     * [OutputStream] 기반 익스포트 싱크.
     *
     * @param outputStream [OutputStream]
     * @param ownsStream `true`이면 export 완료 후 [outputStream]을 닫는다. 기본값 `false` (호출자 소유).
     */
    data class OutputStreamBased(
        val outputStream: OutputStream,
        val ownsStream: Boolean = false,
    ) : OkioGraphExportSink {
        companion object {
            /**
             * [OutputStreamBased]를 생성하는 팩토리 함수.
             *
             * @param outputStream 대상 [OutputStream]
             * @param ownsStream `true`이면 완료 후 [outputStream] 닫음 (기본: `false`)
             * @return 설정된 [OutputStreamBased] 인스턴스
             */
            fun from(outputStream: OutputStream, ownsStream: Boolean = false): OutputStreamBased =
                OutputStreamBased(outputStream, ownsStream)
        }
    }
}
