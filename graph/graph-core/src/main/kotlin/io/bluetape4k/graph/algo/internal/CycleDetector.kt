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
        // Frame: (정점, 아직 방문하지 않은 이웃 목록)
        data class Frame(val vertex: GraphElementId, val remaining: ArrayDeque<GraphElementId>)

        val path = ArrayList<GraphElementId>()  // 현재 탐색 경로 (= 원래 recursive dfs의 stack)
        val onPath = HashSet<GraphElementId>()  // 경로 내 정점 집합 (빠른 조회용)

        path.add(start)
        onPath.add(start)
        val callStack = ArrayDeque<Frame>()
        callStack.addLast(Frame(start, ArrayDeque(adjacency[start].orEmpty())))

        while (callStack.isNotEmpty()) {
            if (result.size >= maxCycles) break

            val frame = callStack.last()
            // 경로 깊이(간선 수) = callStack.size - 1
            val depth = callStack.size - 1

            if (depth >= maxDepth || frame.remaining.isEmpty()) {
                // 이 프레임 종료 — path/onPath에서 현재 정점 제거
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

            // 새 프레임 push
            path.add(next)
            onPath.add(next)
            callStack.addLast(Frame(next, ArrayDeque(adjacency[next].orEmpty())))
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
