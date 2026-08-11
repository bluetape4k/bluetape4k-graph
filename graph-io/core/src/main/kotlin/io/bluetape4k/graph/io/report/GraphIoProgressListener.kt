package io.bluetape4k.graph.io.report

/** 그래프 벌크 I/O 진행 이벤트 수신기. */
fun interface GraphIoProgressListener {

    /** 이벤트를 처리한다. callback은 작업 thread에서 동기 실행된다. */
    fun onEvent(event: GraphIoProgressEvent)

    companion object {
        /** listener가 필요하지만 관찰을 사용하지 않을 때의 no-op 구현. */
        val NOOP: GraphIoProgressListener = GraphIoProgressListener { }
    }
}
