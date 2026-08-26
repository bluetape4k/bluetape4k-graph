package io.bluetape4k.graph.model

import io.bluetape4k.support.requirePositiveNumber
import io.bluetape4k.support.requireZeroOrPositiveNumber
import java.io.InvalidObjectException
import java.io.ObjectInputStream
import java.io.Serializable

private fun validateDeserializedOption(condition: Boolean, message: String) {
    if (!condition) {
        throw InvalidObjectException(message)
    }
}

private fun validateDeserializedOptionNotNull(value: Any?, name: String) {
    validateDeserializedOption(value != null, "$name must not be null")
}

/**
 * Base sealed class for graph traversal options.
 *
 * ### Usage
 * ```kotlin
 * // Find OUTGOING neighbors one hop away.
 * val neighborOpts = NeighborOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING)
 *
 * // Find shortest or all paths up to five hops.
 * val pathOpts = PathOptions(edgeLabel = "KNOWS", maxDepth = 5)
 * ```
 *
 * Java deserialization rechecks each concrete option's invariants and throws
 * [InvalidObjectException] for a malformed payload.
 */
sealed class GraphTraversalOptions: Serializable {
    /**
     * Maximum traversal depth. Defaults vary by subclass: [NeighborOptions] uses `1`, [PathOptions] uses `10`.
     *
     * ```kotlin
     * val opts = NeighborOptions(maxDepth = 3)  // Traverse up to 3 hops.
     * ```
     */
    abstract val maxDepth: Int
}

/**
 * Options for `GraphTraversalRepository.neighbors`.
 *
 * ```kotlin
 * val opts = NeighborOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING, maxDepth = 2)
 * val friends = ops.neighbors(alice.id, opts)
 * ```
 *
 * @param edgeLabel Edge label to traverse. `null` traverses all labels.
 * @param direction Traversal direction: `OUTGOING`, `INCOMING`, or `BOTH`.
 * @param maxDepth 최대 순회 깊이. 0이면 이웃을 확장하지 않으며 기본값은 `1`이다.
 * @throws IllegalArgumentException [maxDepth]가 음수이면 발생한다.
 */
data class NeighborOptions(
    val edgeLabel: String? = null,
    val direction: Direction = Direction.OUTGOING,
    override val maxDepth: Int = 1,
): GraphTraversalOptions() {
    init {
        maxDepth.requireZeroOrPositiveNumber("maxDepth")
    }

    companion object {
        private const val serialVersionUID: Long = 1L
        val Default = NeighborOptions()
    }

    private fun readObject(input: ObjectInputStream) {
        input.defaultReadObject()
        validateDeserializedOption(maxDepth >= 0, "maxDepth must be >= 0, was $maxDepth")
        validateDeserializedOptionNotNull(direction, "direction")
    }
}

/**
 * Options for `GraphTraversalRepository.shortestPath` and `GraphTraversalRepository.allPaths`.
 *
 * When [weightProperty] is set, weighted shortest-path search uses Dijkstra/A*.
 * When it is `null` (default), backend-native BFS shortest-path search is used.
 *
 * ```kotlin
 * // Unweighted shortest path (default).
 * val opts = PathOptions(edgeLabel = "KNOWS", maxDepth = 5)
 *
 * // Weighted shortest path (Dijkstra).
 * val opts = PathOptions(
 *     edgeLabel = "ROAD",
 *     weightProperty = "distance",
 *     missingWeightPolicy = MissingWeightPolicy.UseDefault(1.0),
 *     direction = Direction.BOTH,
 *     maxVisited = 50_000,
 * )
 * ```
 *
 * @param edgeLabel Edge label to traverse. `null` traverses all labels.
 * @param maxDepth 최대 순회 깊이. 0이면 source와 target이 같은 vertex-only path만 허용하며
 * 기본값은 `10`이다.
 * @param weightProperty Edge weight property key. `null` uses unweighted traversal.
 * @param missingWeightPolicy Policy for edges missing [weightProperty]. Defaults to [MissingWeightPolicy.Fail].
 * @param direction Traversal direction. Applies only when [weightProperty] is set. Defaults to [Direction.OUTGOING].
 * @param maxVisited weighted traversal에서 방문할 정점 수의 상한. 무한 그래프 확장을
 * 막기 위해 양수여야 하며 기본값은 `100_000`이다.
 * @throws IllegalArgumentException [maxDepth]가 음수이거나 [maxVisited]가 양수가 아니면 발생한다.
 */
data class PathOptions(
    val edgeLabel: String? = null,
    override val maxDepth: Int = 10,
    val weightProperty: String? = null,
    val missingWeightPolicy: MissingWeightPolicy = MissingWeightPolicy.Fail,
    val direction: Direction = Direction.OUTGOING,
    val maxVisited: Int = 100_000,
): GraphTraversalOptions() {
    init {
        maxDepth.requireZeroOrPositiveNumber("maxDepth")
        maxVisited.requirePositiveNumber("maxVisited")
    }
    companion object {
        private const val serialVersionUID: Long = 1L
        val Default = PathOptions()
    }

    private fun readObject(input: ObjectInputStream) {
        input.defaultReadObject()
        validateDeserializedOption(maxDepth >= 0, "maxDepth must be >= 0, was $maxDepth")
        validateDeserializedOption(maxVisited > 0, "maxVisited must be > 0, was $maxVisited")
        validateDeserializedOptionNotNull(direction, "direction")
        validateDeserializedOptionNotNull(missingWeightPolicy, "missingWeightPolicy")
    }
}

/**
 * Options for `GraphTraversalRepository.bfs` and `GraphTraversalRepository.dfs`.
 *
 * ### Usage
 * ```kotlin
 * val opts = BfsDfsOptions(edgeLabel = "KNOWS", maxDepth = 3, maxVertices = 1_000)
 * val visits = ops.bfs(alice.id, opts)
 * ```
 *
 * `maxDepth`가 `0`이면 시작 정점만 반환한다.
 *
 * @param edgeLabel 순회할 간선 레이블. `null`이면 모든 레이블을 순회한다.
 * @param direction 순회 방향: `OUTGOING`, `INCOMING`, `BOTH`.
 * @param maxDepth 최대 순회 깊이. 0이면 시작 정점만 반환한다.
 * @param maxVertices 반환할 정점 수의 양수 상한. 기본값은 `10_000`이다.
 * @throws IllegalArgumentException [maxDepth]가 음수이거나 [maxVertices]가 양수가 아니면 발생한다.
 */
data class BfsDfsOptions(
    val edgeLabel: String? = null,
    val direction: Direction = Direction.OUTGOING,
    override val maxDepth: Int = 5,
    val maxVertices: Int = 10_000,
): GraphTraversalOptions() {
    init {
        maxDepth.requireZeroOrPositiveNumber("maxDepth")
        maxVertices.requirePositiveNumber("maxVertices")
    }
    companion object {
        private const val serialVersionUID: Long = 1L
        val Default = BfsDfsOptions()
    }

    private fun readObject(input: ObjectInputStream) {
        input.defaultReadObject()
        validateDeserializedOption(maxDepth >= 0, "maxDepth must be >= 0, was $maxDepth")
        validateDeserializedOption(maxVertices > 0, "maxVertices must be > 0, was $maxVertices")
        validateDeserializedOptionNotNull(direction, "direction")
    }
}

/**
 * Options for `GraphTraversalRepository.detectCycles`.
 *
 * @param vertexLabel Vertex label to traverse. `null` traverses all labels.
 * @param edgeLabel Edge label to traverse. `null` traverses all labels.
 * @param maxDepth 최대 순회 깊이. 양수여야 하며 기본값은 `10`이다.
 * @param maxCycles 반환할 순환 수의 양수 상한. 기본값은 `100`이다.
 * @throws IllegalArgumentException [maxDepth] 또는 [maxCycles]가 양수가 아니면 발생한다.
 */
data class CycleOptions(
    val vertexLabel: String? = null,
    val edgeLabel: String? = null,
    override val maxDepth: Int = 10,
    val maxCycles: Int = 100,
): GraphTraversalOptions() {
    init {
        maxDepth.requirePositiveNumber("maxDepth")
        maxCycles.requirePositiveNumber("maxCycles")
    }
    companion object {
        private const val serialVersionUID: Long = 1L
        val Default = CycleOptions()
    }

    private fun readObject(input: ObjectInputStream) {
        input.defaultReadObject()
        validateDeserializedOption(maxDepth > 0, "maxDepth must be > 0, was $maxDepth")
        validateDeserializedOption(maxCycles > 0, "maxCycles must be > 0, was $maxCycles")
    }
}
