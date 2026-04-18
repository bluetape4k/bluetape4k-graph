package io.bluetape4k.graph.io.support

import java.time.Duration

/** 경과 시간을 측정하는 단순 스톱워치 */
class GraphIoStopwatch {
    private val started = System.nanoTime()
    fun elapsed(): Duration = Duration.ofNanos(System.nanoTime() - started)
}
