package io.bluetape4k.graph.io.report

/**
 * 순차 reader가 입력을 해석하지 못했음을 나타내는 안전한 예외.
 *
 * 예외 메시지는 단계와 위치만 노출한다. 원본 payload, source 경로, 외부 ID,
 * codec 예외 메시지는 public cause나 message로 전달하지 않는다.
 */
class GraphIoReadException(
    failure: GraphIoFailure,
) : RuntimeException(
    buildMessage(failure),
) {

    /** 원본 payload/식별자를 제거한 public 진단 정보. */
    val failure: GraphIoFailure = failure.redacted()

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

private fun buildMessage(failure: GraphIoFailure): String = buildString {
    append("Graph IO read failed")
    append(" phase=").append(failure.phase)
    append(" location=").append(failure.safeLocation() ?: "unknown")
}

private fun GraphIoFailure.redacted(): GraphIoFailure = copy(
    location = safeLocation(),
    sourceName = null,
    recordId = null,
    columnName = null,
    elementName = null,
    message = "Graph IO read failed",
)

private fun GraphIoFailure.safeLocation(): String? = location
    ?.takeIf { it.matches(Regex("(?:line|row|edge-buffer):\\d+")) }
