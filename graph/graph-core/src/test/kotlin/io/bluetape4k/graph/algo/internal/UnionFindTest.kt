package io.bluetape4k.graph.algo.internal

import org.amshove.kluent.shouldBeEqualTo
import org.junit.jupiter.api.Test

class UnionFindTest {

    @Test
    fun `single element components`() {
        val uf = UnionFind(listOf("a", "b", "c"))
        uf.componentCount() shouldBeEqualTo 3
        uf.componentOf("a") shouldBeEqualTo "a"
    }

    @Test
    fun `union merges components`() {
        val uf = UnionFind(listOf("a", "b", "c", "d"))
        uf.union("a", "b")
        uf.union("c", "d")
        uf.componentCount() shouldBeEqualTo 2
        uf.connected("a", "b") shouldBeEqualTo true
        uf.connected("a", "c") shouldBeEqualTo false
    }

    @Test
    fun `union chained merges into single component`() {
        val uf = UnionFind(listOf("a", "b", "c", "d"))
        uf.union("a", "b")
        uf.union("b", "c")
        uf.union("c", "d")
        uf.componentCount() shouldBeEqualTo 1
    }

    @Test
    fun `groups returns map of component representative to members`() {
        val uf = UnionFind(listOf("a", "b", "c", "d"))
        uf.union("a", "b")
        uf.union("c", "d")
        val groups = uf.groups()
        groups.size shouldBeEqualTo 2
        groups.values.map { it.size }.sorted() shouldBeEqualTo listOf(2, 2)
    }
}
