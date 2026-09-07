package io.bluetape4k.graph.io.options

import io.bluetape4k.support.requirePositiveNumber
import java.io.Serializable

/**
 * NDJSON처럼 한 줄이 하나의 record인 입력을 읽는 옵션.
 *
 * [maxLineChars]는 JSON codec 호출 전에 허용할 한 줄의 최대 UTF-16 code unit 수다.
 * 기본값은 기존 입력 호환성을 유지하기 위해 [Int.MAX_VALUE]다.
 */
data class NdJsonReadOptions(
    val maxLineChars: Int = UNLIMITED_MAX_LINE_CHARS,
) : Serializable {
    init {
        maxLineChars.requirePositiveNumber("maxLineChars")
    }

    companion object {
        private const val serialVersionUID: Long = 1L

        /** 기존 입력을 사실상 제한하지 않는 호환 기본값. */
        const val UNLIMITED_MAX_LINE_CHARS: Int = Int.MAX_VALUE
    }
}
