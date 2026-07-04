package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * Detected graph cycle.
 *
 * The first and last vertices in [path] are the same (`first == last`).
 * [length] is a computed property based on the number of edges in [path].
 *
 * @property path Cyclic path whose start and end are the same.
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
 * Converts this path to a [GraphCycle].
 *
 * Use this when the first and last vertices in the path are the same.
 * Verify the cycle before calling this function; it only wraps the path.
 *
 * ```kotlin
 * val cycle = detectedPath.toCycle()
 * println("cycle length = ${cycle.length}")
 * ```
 */
fun GraphPath.toCycle() = GraphCycle(this)
