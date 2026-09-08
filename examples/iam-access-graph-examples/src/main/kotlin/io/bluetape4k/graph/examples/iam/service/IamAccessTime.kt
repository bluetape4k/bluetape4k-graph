package io.bluetape4k.graph.examples.iam.service

import java.time.DateTimeException
import java.time.Instant

/**
 * 임시 권한의 만료 시각을 파싱하고 평가 시각보다 뒤에 있는지 판정한다.
 *
 * 저장된 값이 없거나 ISO-8601 `Instant`로 파싱되지 않으면 안전하게 비활성 권한으로 취급한다.
 */
internal fun isTemporaryGrantActive(expiresAt: Any?, evaluatedAt: Instant): Boolean {
    val expiration = expiresAt?.toString()?.let { value ->
        try {
            Instant.parse(value)
        } catch (_: DateTimeException) {
            null
        }
    } ?: return false

    return expiration.isAfter(evaluatedAt)
}
