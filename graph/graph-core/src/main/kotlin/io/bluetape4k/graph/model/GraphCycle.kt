package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * 탐지된 graph cycle.
 *
 * [path]의 첫 vertex와 마지막 vertex는 동일하다(`first == last`).
 * [length] is a computed property based on the number of edges in [path].
 *
 * @property path 시작과 끝이 같은 cyclic path.
 *
 * ### Usage
 * ```kotlin
 * val cycles = ops.detectCycles(CycleOptions(maxDepth = 5))
 * cycles.forEach { println("cycle length=${it.length}") }
 * ```
 */
data class GraphCycle(
    val path: GraphPath,
): Serializable {
    /** Number of edges in the cycle path. */
    val length: Int get() = path.edges.size

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * 이 path를 [GraphCycle]로 변환한다.
 *
 * path의 첫 vertex와 마지막 vertex가 같을 때 사용한다.
 * 이 함수는 path를 감싸기만 하므로 호출 전에 cycle 여부를 검증한다.
 *
 * ```kotlin
 * val cycle = detectedPath.toCycle()
 * println("cycle length = ${cycle.length}")
 * ```
 */
fun GraphPath.toCycle() = GraphCycle(this)
