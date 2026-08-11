package io.bluetape4k.graph.io.report

/**
 * 순차 reader가 입력을 해석하지 못했음을 나타내는 안전한 예외.
 *
 * 예외 메시지는 단계와 위치만 노출한다. 원본 payload, source 경로, 외부 ID,
 * codec 예외 메시지는 public cause나 message로 전달하지 않는다.
 */
class GraphIoReadException(
    val failure: GraphIoFailure,
) : RuntimeException(
    buildString {
        append("Graph IO read failed")
        append(" phase=").append(failure.phase)
        append(" location=").append(failure.location ?: "unknown")
    },
) {

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
