package io.bluetape4k.graph.falkordb.internal

/**
 * FalkorDB 세션 구현에서 공유되는 내부 유틸리티 모음.
 *
 * Cypher 쿼리에 안전하게 삽입할 식별자(label, edge type 등)를 검증하는 헬퍼를 제공합니다.
 *
 * ```kotlin
 * "Person".requireSafeIdentifier("label")     // OK
 * "Person; DROP".requireSafeIdentifier("l")   // IllegalArgumentException
 * ```
 */
private val SAFE_IDENTIFIER = Regex("^[A-Za-z_][A-Za-z0-9_]*$")

/**
 * 문자열이 Cypher 식별자로 안전한지 검증합니다.
 *
 * `[A-Za-z_][A-Za-z0-9_]*` 패턴을 만족하지 않으면 [IllegalArgumentException]을 던집니다.
 * 이 검사는 문자열 보간으로 Cypher에 삽입되는 label/relationship type 인자에 적용됩니다.
 *
 * ```kotlin
 * val safe = userInput.requireSafeIdentifier("label")
 * ```
 *
 * @param paramName 예외 메시지에 표기할 파라미터 이름
 * @return 검증을 통과한 동일 문자열
 */
internal fun String.requireSafeIdentifier(paramName: String): String = apply {
    require(SAFE_IDENTIFIER.matches(this)) {
        "$paramName must be a valid identifier (alphanumeric/_): $this"
    }
}
