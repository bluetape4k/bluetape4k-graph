package io.bluetape4k.graph.algo.internal

/**
 * Path compression + union-by-rank Union-Find (Disjoint Set Union).
 *
 * Union-find structure used by connected-components fallback algorithms.
 *
 * ### Usage
 * ```kotlin
 * val uf = UnionFind(listOf("a", "b", "c"))
 * uf.union("a", "b")
 * uf.connected("a", "b")  // true
 * ```
 *
 * @param elements initial element collection.
 */
class UnionFind<T>(elements: Iterable<T>) {

    private val parent: MutableMap<T, T> = HashMap()
    private val rank: MutableMap<T, Int> = HashMap()

    init {
        elements.forEach {
            parent[it] = it
            rank[it] = 0
        }
    }

    /** Returns the representative root element for the component containing [x]. */
    fun componentOf(x: T): T {
        var root = x
        while (parent[root] != root) {
            root = parent[root] ?: error("Element not in UnionFind: $root")
        }
        // path compression
        var cur = x
        while (parent[cur] != root) {
            val next = parent[cur] ?: error("Element not in UnionFind: $cur")
            parent[cur] = root
            cur = next
        }
        return root
    }

    /** Merges two elements into the same component. */
    fun union(x: T, y: T) {
        val rx = componentOf(x)
        val ry = componentOf(y)
        if (rx == ry) return

        val rankX = rank.getOrDefault(rx, 0)
        val rankY = rank.getOrDefault(ry, 0)
        when {
            rankX < rankY -> parent[rx] = ry
            rankX > rankY -> parent[ry] = rx
            else -> {
                parent[ry] = rx
                rank[rx] = rankX + 1
            }
        }
    }

    /** Returns whether two elements belong to the same component. */
    fun connected(x: T, y: T): Boolean = componentOf(x) == componentOf(y)

    /** Current component count. */
    fun componentCount(): Int = parent.keys.map { componentOf(it) }.toSet().size

    /** Mapping from representative root element to component members. */
    fun groups(): Map<T, List<T>> =
        parent.keys.groupBy { componentOf(it) }
}
