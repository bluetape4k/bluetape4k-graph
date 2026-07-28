package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * Connected component 결과.
 *
 * 같은 [componentId]를 공유하는 vertex들을 표현한다.
 *
 * @property componentId component identifier. 값은 implementation-defined이며,
 * 같은 component의 vertex들이 공유한다.
 * @property vertices component에 속한 vertex 목록.
 *
 * ### Usage
 * ```kotlin
 * val components = ops.connectedComponents(ComponentOptions(weakly = true))
 * components.forEach { println("${it.componentId}: size=${it.size}") }
 * ```
 */
data class GraphComponent(
    val componentId: String,
    val vertices: List<GraphVertex>,
): Serializable {
    /** Number of vertices in the component. */
    val size: Int get() = vertices.size

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
