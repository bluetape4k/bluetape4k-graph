package io.bluetape4k.graph.io.report

/** 벌크 I/O lifecycle과 누적 진행 상태를 표현하는 이벤트 종류. */
enum class GraphIoProgressEventType {
    STARTED,
    PROGRESS,
    PHASE_COMPLETED,
    COMPLETED,
    FAILED,
    CANCELLED,
}
