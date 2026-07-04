package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * Connected component result.
 *
 * Represents vertices that share the same [componentId].
 *
 * @property componentId Component identifier. The value is implementation-defined;
 * vertices in the same component share it.
 * @property vertices Vertices in the component.
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
