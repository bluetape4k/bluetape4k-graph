package io.bluetape4k.graph.algo.internal

import io.bluetape4k.graph.model.GraphElementId

/**
 * DFS 기반 단순 순환 탐지기 (JVM 폴백).
 *
 * 모든 정점에서 DFS 를 시작해 백엣지(back edge)를 탐지한다.
 * 동일 시작 정점에서 발생한 순환은 한 번만 보고된다.
 *
 * 반환되는 각 순환은 정점 ID 목록으로, 첫 번째와 마지막이 같다.
 *
 * ### 사용 예제
 * ```kotlin
 * val cycles = CycleDetector.findCycles(adjacency, maxDepth = 6, maxCycles = 50)
 * cycles.forEach { println("cycle: ${it.joinToString(" -> ")}") }
 * ```
 */
object CycleDetector {

    /**
     * @param adjacency 인접 리스트 (out-edges).
     * @param maxDepth 순환 경로 최대 길이 (간선 수).
     * @param maxCycles 반환할 최대 순환 수.
     * @return 정점 ID 목록의 목록. 각 항목은 first == last.
     */
    fun findCycles(
        adjacency: Map<GraphElementId, List<GraphElementId>>,
        maxDepth: Int,
        maxCycles: Int,
    ): List<List<GraphElementId>> {
        val result = ArrayList<List<GraphElementId>>()
        val seenSignatures = HashSet<List<GraphElementId>>()

        for (start in adjacency.keys) {
            if (result.size >= maxCycles) break
            dfs(
                current = start,
                start = start,
                stack = ArrayList<GraphElementId>().apply { add(start) },
                onStack = HashSet<GraphElementId>().apply { add(start) },
                adjacency = adjacency,
                maxDepth = maxDepth,
                maxCycles = maxCycles,
                result = result,
                seenSignatures = seenSignatures,
            )
        }
        return result
    }

    @Suppress("LongParameterList")
    private fun dfs(
        current: GraphElementId,
        start: GraphElementId,
        stack: MutableList<GraphElementId>,
        onStack: MutableSet<GraphElementId>,
        adjacency: Map<GraphElementId, List<GraphElementId>>,
        maxDepth: Int,
        maxCycles: Int,
        result: MutableList<List<GraphElementId>>,
        seenSignatures: MutableSet<List<GraphElementId>>,
    ) {
        if (stack.size - 1 >= maxDepth) return
        if (result.size >= maxCycles) return

        for (next in adjacency[current].orEmpty()) {
            if (result.size >= maxCycles) return

            if (next == start) {
                val cycle = ArrayList(stack).apply { add(start) }
                val signature = canonicalSignature(cycle)
                if (seenSignatures.add(signature)) {
                    result.add(cycle)
                }
                continue
            }
            if (next in onStack) continue

            stack.add(next)
            onStack.add(next)
            dfs(next, start, stack, onStack, adjacency, maxDepth, maxCycles, result, seenSignatures)
            stack.removeAt(stack.size - 1)
            onStack.remove(next)
        }
    }

    /** 회전 등가 순환을 동일 시그니처로 정규화 (가장 작은 회전 시작). */
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
