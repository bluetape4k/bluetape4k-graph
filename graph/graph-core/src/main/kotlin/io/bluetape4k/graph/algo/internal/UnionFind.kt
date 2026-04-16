package io.bluetape4k.graph.algo.internal

/**
 * Path compression + union-by-rank Union-Find (Disjoint Set Union).
 *
 * Connected Components 폴백 알고리즘에서 사용한다.
 *
 * ### 사용 예제
 * ```kotlin
 * val uf = UnionFind(listOf("a", "b", "c"))
 * uf.union("a", "b")
 * uf.connected("a", "b")  // true
 * ```
 *
 * @param elements 초기 원소 컬렉션.
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

    /** 원소 [x] 가 속한 컴포넌트의 대표(루트) 원소를 반환한다. */
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

    /** 두 원소를 같은 컴포넌트로 병합한다. */
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

    /** 두 원소가 같은 컴포넌트인지 확인한다. */
    fun connected(x: T, y: T): Boolean = componentOf(x) == componentOf(y)

    /** 현재 컴포넌트 수. */
    fun componentCount(): Int = parent.keys.map { componentOf(it) }.toSet().size

    /** 대표 원소 → 컴포넌트 멤버 목록 매핑. */
    fun groups(): Map<T, List<T>> =
        parent.keys.groupBy { componentOf(it) }
}
