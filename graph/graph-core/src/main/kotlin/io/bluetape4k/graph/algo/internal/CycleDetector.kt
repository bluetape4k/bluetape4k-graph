package io.bluetape4k.graph.algo.internal

import io.bluetape4k.graph.model.GraphElementId

/**
 * DFS-based simple cycle detector used as a JVM fallback.
 *
 * It starts DFS from every vertex and detects back edges. Cycles from the same start
 * vertex are reported once.
 *
 * Each returned cycle is a vertex ID list where the first and last IDs are equal.
 *
 * ### Usage
 * ```kotlin
 * val cycles = CycleDetector.findCycles(adjacency, maxDepth = 6, maxCycles = 50)
 * cycles.forEach { println("cycle: ${it.joinToString(" -> ")}") }
 * ```
 */
object CycleDetector {

    /**
     * @param adjacency adjacency list of out-edges.
     * @param maxDepth maximum cycle path length in edges.
     * @param maxCycles maximum number of cycles to return.
     * @return lists of vertex IDs, each with `first == last`.
     */
    fun findCycles(
        adjacency: Map<GraphElementId, List<GraphElementId>>,
        maxDepth: Int,
        maxCycles: Int,
    ): List<List<GraphElementId>> {
        require(maxDepth > 0) { "maxDepth must be > 0, was $maxDepth" }
        require(maxCycles > 0) { "maxCycles must be > 0, was $maxCycles" }

        val result = ArrayList<List<GraphElementId>>()
        val seenSignatures = HashSet<List<GraphElementId>>()

        for (start in adjacency.keys) {
            if (result.size >= maxCycles) break
            dfsIterative(start, adjacency, maxDepth, maxCycles, result, seenSignatures)
        }
        return result
    }

    private fun dfsIterative(
        start: GraphElementId,
        adjacency: Map<GraphElementId, List<GraphElementId>>,
        maxDepth: Int,
        maxCycles: Int,
        result: MutableList<List<GraphElementId>>,
        seenSignatures: MutableSet<List<GraphElementId>>,
    ) {
        // Frame: current vertex and neighbors not yet visited from it.
        data class Frame(val vertex: GraphElementId, val remaining: ArrayDeque<GraphElementId>)

        val path = ArrayList<GraphElementId>()  // Current traversal path, equivalent to recursive DFS stack.
        val onPath = HashSet<GraphElementId>()  // Vertices on the current path for fast lookup.

        path.add(start)
        onPath.add(start)
        val callStack = ArrayDeque<Frame>()
        callStack.addLast(Frame(start, ArrayDeque(adjacency[start].orEmpty())))

        while (callStack.isNotEmpty()) {
            if (result.size >= maxCycles) break

            val frame = callStack.last()
            // Path depth in edges is callStack.size - 1.
            val depth = callStack.size - 1

            if (depth >= maxDepth || frame.remaining.isEmpty()) {
                // End this frame and remove the current vertex from path/onPath.
                callStack.removeLast()
                val popped = path.removeAt(path.size - 1)
                onPath.remove(popped)
                continue
            }

            val next = frame.remaining.removeFirst()

            if (result.size >= maxCycles) break

            if (next == start) {
                val cycle = ArrayList(path).apply { add(start) }
                val signature = canonicalSignature(cycle)
                if (seenSignatures.add(signature)) {
                    result.add(cycle)
                }
                continue
            }

            if (next in onPath) continue

            // Push the next frame.
            path.add(next)
            onPath.add(next)
            callStack.addLast(Frame(next, ArrayDeque(adjacency[next].orEmpty())))
        }
    }

    /** Normalizes rotationally equivalent cycles to the same signature by starting at the smallest rotation. */
    private fun canonicalSignature(cycle: List<GraphElementId>): List<GraphElementId> {
        // cycle has first == last; drop the trailing duplicate
        val core = cycle.dropLast(1)
        if (core.isEmpty()) return cycle
        var minIdx = 0
        for (i in 1 until core.size) {
            if (core[i].value < core[minIdx].value) minIdx = i
        }
        val rotated = ArrayList<GraphElementId>(core.size)
        for (i in core.indices) rotated.add(core[(minIdx + i) % core.size])
        rotated.add(rotated[0])
        return rotated
    }
}
