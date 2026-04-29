package io.bluetape4k.graph.io.okio

import io.bluetape4k.io.compressor.Compressors
import io.bluetape4k.io.compressor.StreamingCompressor

/**
 * graph-io-okio 모듈에서 지원하는 압축 알고리즘 목록.
 *
 * v1 지원 범위:
 * - GZIP/DEFLATE: JDK 내장 — 항상 사용 가능
 * - LZ4/SNAPPY/ZSTD/BZIP2: 각각 `compileOnly` 의존성 — 런타임에 클래스패스 가드 검사
 *
 * v1 미포함 항목:
 * - NONE: 압축 미사용 시 [GraphIoOkioPaths.openSource]/[GraphIoOkioPaths.openSink] 직접 호출 (체이닝 생략)
 * - 암호화: bluetape4k-projects #240 완료 후 v2에서 추가 예정
 */
enum class Compressor(
    /** 클래스패스 가드용 클래스 이름. JDK 내장 압축기는 null. */
    val requiredClassName: String?,
    /** 해당 의존성 추가 방법 안내. 항상 사용 가능한 압축기는 빈 문자열. */
    val installHint: String,
) {
    /** JDK 내장 GZip 압축. 항상 사용 가능. */
    GZIP(
        requiredClassName = null,
        installHint = "",
    ),
    /** JDK 내장 Deflate 압축. 항상 사용 가능. */
    DEFLATE(
        requiredClassName = null,
        installHint = "",
    ),
    /**
     * LZ4 고속 압축. `lz4-java` 의존성 필요.
     *
     * ```kotlin
     * implementation("org.lz4:lz4-java:<version>")
     * ```
     */
    LZ4(
        requiredClassName = "net.jpountz.lz4.LZ4Factory",
        installHint = """
            LZ4 압축을 사용하려면 다음 의존성을 추가하세요:
              // build.gradle.kts
              implementation("org.lz4:lz4-java:<version>")
        """.trimIndent(),
    ),
    /**
     * Snappy 고속 압축. `snappy-java` 의존성 필요.
     *
     * ```kotlin
     * implementation("org.xerial.snappy:snappy-java:<version>")
     * ```
     */
    SNAPPY(
        requiredClassName = "org.xerial.snappy.Snappy",
        installHint = """
            Snappy 압축을 사용하려면 다음 의존성을 추가하세요:
              // build.gradle.kts
              implementation("org.xerial.snappy:snappy-java:<version>")
        """.trimIndent(),
    ),
    /**
     * Zstd 고압축률 압축. `zstd-jni` 의존성 필요.
     *
     * ```kotlin
     * implementation("com.github.luben:zstd-jni:<version>")
     * ```
     */
    ZSTD(
        requiredClassName = "com.github.luben.zstd.ZstdInputStream",
        installHint = """
            Zstd 압축을 사용하려면 다음 의존성을 추가하세요:
              // build.gradle.kts
              implementation("com.github.luben:zstd-jni:<version>")
        """.trimIndent(),
    ),
    /**
     * BZip2 압축. `commons-compress` 의존성 필요.
     *
     * ```kotlin
     * implementation("org.apache.commons:commons-compress:<version>")
     * ```
     */
    BZIP2(
        requiredClassName = "org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream",
        installHint = """
            BZip2 압축을 사용하려면 다음 의존성을 추가하세요:
              // build.gradle.kts
              implementation("org.apache.commons:commons-compress:<version>")
        """.trimIndent(),
    );

    /** 해당 압축기의 스트리밍 변형을 반환한다. 선택 의존성 클래스가 없으면 [IllegalStateException]을 던진다. */
    fun streamingCompressor(): StreamingCompressor {
        requireOnClasspath()
        return when (this) {
            GZIP -> Compressors.Streaming.GZip
            DEFLATE -> Compressors.Streaming.Deflate
            LZ4 -> Compressors.Streaming.LZ4
            SNAPPY -> Compressors.Streaming.Snappy
            ZSTD -> Compressors.Streaming.Zstd
            BZIP2 -> Compressors.Streaming.BZip2
        }
    }

    /**
     * 해당 압축기에 필요한 클래스가 클래스패스에 존재하는지 확인한다.
     * 없으면 [installHint]를 포함한 [IllegalStateException]을 던진다.
     */
    fun requireOnClasspath() {
        requiredClassName ?: return
        try {
            Class.forName(requiredClassName)
        } catch (_: ClassNotFoundException) {
            error(
                "$name 압축기에 필요한 클래스 '$requiredClassName' 가 클래스패스에 없습니다.\n$installHint"
            )
        }
    }

    /** 해당 압축기에 필요한 클래스가 클래스패스에 있는지 여부를 반환한다. */
    fun isAvailable(): Boolean = requiredClassName == null || runCatching {
        Class.forName(requiredClassName)
    }.isSuccess
}
