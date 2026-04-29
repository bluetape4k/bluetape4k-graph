package io.bluetape4k.graph.io.okio.bridge

import io.bluetape4k.graph.io.okio.GraphIoOkioPaths
import io.bluetape4k.graph.io.okio.OkioGraphExportSink
import io.bluetape4k.graph.io.okio.OkioGraphImportSource
import okio.BufferedSink
import okio.BufferedSource
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.io.Writer
import java.nio.charset.Charset

/**
 * OkIO [BufferedSource]를 [InputStream]으로 변환한다.
 *
 * underlying [BufferedSource]는 닫지 않는다 (호출자 소유).
 */
fun BufferedSource.toInputStream(): InputStream = inputStream()

/**
 * OkIO [BufferedSink]를 [OutputStream]으로 변환한다.
 *
 * underlying [BufferedSink]는 닫지 않는다 (호출자 소유).
 */
fun BufferedSink.toOutputStream(): OutputStream = outputStream()

/**
 * OkIO [BufferedSink]를 **소유권 이전** [OutputStream]으로 변환한다.
 *
 * 반환된 [OutputStream]이 닫힐 때 underlying [BufferedSink]도 함께 닫힌다.
 * Jackson/StAX처럼 라이브러리가 직접 OutputStream을 close하는 경우에 사용한다.
 *
 * 소유권 규칙:
 * - [toOutputStream]: underlying sink 미닫힘 (호출자 소유)
 * - [asClosingOutputStream]: underlying sink까지 닫힘 (라이브러리 소유)
 */
fun BufferedSink.asClosingOutputStream(): OutputStream = object : OutputStream() {
    override fun write(b: Int) { this@asClosingOutputStream.writeByte(b) }
    override fun write(b: ByteArray, off: Int, len: Int) { this@asClosingOutputStream.write(b, off, len) }
    override fun flush() = this@asClosingOutputStream.flush()
    override fun close() = this@asClosingOutputStream.close()
}

/**
 * OkIO [BufferedSource]를 [Reader]로 변환한다. [charset] 기본값은 UTF-8.
 *
 * underlying [BufferedSource]는 닫지 않는다 (호출자 소유).
 */
fun BufferedSource.toReader(charset: Charset = Charsets.UTF_8): Reader =
    inputStream().reader(charset)

/**
 * OkIO [BufferedSink]를 [Writer]로 변환한다. [charset] 기본값은 UTF-8.
 *
 * underlying [BufferedSink]는 닫지 않는다 (호출자 소유).
 */
fun BufferedSink.toWriter(charset: Charset = Charsets.UTF_8): Writer =
    outputStream().writer(charset)

/**
 * [OkioGraphExportSink]를 열고 [OutputStream]으로 변환한 뒤 [block]을 실행한다.
 *
 * [block]이 완료(정상/예외 무관)되면 [OutputStream]과 underlying [BufferedSink] 모두 닫힌다.
 * `asClosingOutputStream()`을 경유하므로 Jackson/StAX close 체인이 자동으로 sink까지 전파된다.
 */
inline fun writeAsOutputStream(sink: OkioGraphExportSink, block: (OutputStream) -> Unit) {
    GraphIoOkioPaths.openSink(sink).use { bs ->
        bs.asClosingOutputStream().use { os ->
            block(os)
        }
    }
}

/**
 * [OkioGraphImportSource]를 열고 [InputStream]으로 변환한 뒤 [block]을 실행한다.
 *
 * [block]이 완료되면 [InputStream]과 underlying [BufferedSource] 모두 닫힌다.
 */
inline fun readAsInputStream(source: OkioGraphImportSource, block: (InputStream) -> Unit) {
    GraphIoOkioPaths.openSource(source).use { bs ->
        bs.toInputStream().use { is_ ->
            block(is_)
        }
    }
}

/**
 * [OkioGraphExportSink]를 열어 close 체인 패턴으로 쓰기를 수행하는 헬퍼.
 *
 * T9의 포맷 확장 함수들이 공통으로 재사용하는 close-chain 패턴 추출 (DRY).
 * [block]은 [BufferedSink]와 [OutputStream]을 모두 받아 어느 쪽으로든 쓸 수 있다.
 */
inline fun okioWriteTo(sink: OkioGraphExportSink, block: (BufferedSink, OutputStream) -> Unit) {
    GraphIoOkioPaths.openSink(sink).use { bs ->
        bs.asClosingOutputStream().use { os ->
            block(bs, os)
        }
    }
}

/**
 * [OkioGraphImportSource]를 열어 close 체인 패턴으로 읽기를 수행하는 헬퍼.
 *
 * T9의 포맷 확장 함수들이 공통으로 재사용하는 close-chain 패턴 추출 (DRY).
 */
inline fun okioReadFrom(source: OkioGraphImportSource, block: (BufferedSource, InputStream) -> Unit) {
    GraphIoOkioPaths.openSource(source).use { bs ->
        bs.toInputStream().use { is_ ->
            block(bs, is_)
        }
    }
}
