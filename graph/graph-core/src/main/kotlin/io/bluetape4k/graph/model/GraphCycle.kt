package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * 탐지된 그래프 순환(Cycle).
 *
 * [path] 의 첫 번째 정점과 마지막 정점은 동일하다 (first == last 보장).
 * [length] 는 [path] 의 간선 수로 계산되는 computed property.
 *
 * @property path 순환 경로. 시작과 끝이 같은 [GraphPath].
 *
 * ### 사용 예제
 * ```kotlin
 * val cycles = ops.detectCycles(CycleOptions(maxDepth = 5))
 * cycles.forEach { println("cycle length=${it.length}") }
 * ```
 */
data class GraphCycle(
    val path: GraphPath,
): Serializable {
    /** 순환 경로의 간선 수. */
    val length: Int get() = path.edges.size

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * 이 경로를 [GraphCycle]로 변환한다.
 *
 * 경로의 첫 번째 정점과 마지막 정점이 동일한 순환 경로를 표현할 때 사용한다.
 * 호출 전에 순환 여부를 직접 검증해야 한다 — 이 함수는 단순 래핑만 수행한다.
 *
 * ```kotlin
 * val cycle = detectedPath.toCycle()
 * println("cycle length = ${cycle.length}")
 * ```
 */
fun GraphPath.toCycle() = GraphCycle(this)
