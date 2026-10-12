package io.bluetape4k.graph.algo.internal

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.graph.AbstractGraphTest
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test

class UnionFindTest: AbstractGraphTest() {

    companion object: KLogging()

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
        uf.connected("a", "b").shouldBeTrue()
        uf.connected("a", "c").shouldBeFalse()
    }

    @Test
    fun `union chained merges into single component`() {
        val uf = UnionFind(listOf("a", "b", "c", "d"))
        uf.union("a", "b")
        uf.union("b", "c")
        uf.union("c", "d")

        log.debug { "uf=$uf" }
        uf.componentCount() shouldBeEqualTo 1
    }

    @Test
    fun `groups returns map of component representative to members`() {
        val uf = UnionFind(listOf("a", "b", "c", "d"))
        uf.union("a", "b")
        uf.union("c", "d")

        val groups = uf.groups()

        log.debug { "groups=$groups" }
        groups.size shouldBeEqualTo 2
        groups.values.map { it.size }.sorted() shouldBeEqualTo listOf(2, 2)
    }
}
