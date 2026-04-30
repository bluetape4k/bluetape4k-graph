package io.bluetape4k.graph.io.report

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContain
import org.junit.jupiter.api.Test

class GraphIoFileRoleTest {

    @Test
    fun `GraphIoFileRole has three entries`() {
        GraphIoFileRole.entries.size shouldBeEqualTo 3
    }

    @Test
    fun `GraphIoFileRole entries contain all expected values`() {
        val entries = GraphIoFileRole.entries
        entries shouldContain GraphIoFileRole.VERTICES
        entries shouldContain GraphIoFileRole.EDGES
        entries shouldContain GraphIoFileRole.UNIFIED
    }

    @Test
    fun `GraphIoFileRole valueOf returns correct enum constant`() {
        GraphIoFileRole.valueOf("VERTICES") shouldBeEqualTo GraphIoFileRole.VERTICES
        GraphIoFileRole.valueOf("EDGES") shouldBeEqualTo GraphIoFileRole.EDGES
        GraphIoFileRole.valueOf("UNIFIED") shouldBeEqualTo GraphIoFileRole.UNIFIED
    }

    @Test
    fun `GraphIoFileRole ordinal is stable`() {
        GraphIoFileRole.VERTICES.ordinal shouldBeEqualTo 0
        GraphIoFileRole.EDGES.ordinal shouldBeEqualTo 1
        GraphIoFileRole.UNIFIED.ordinal shouldBeEqualTo 2
    }
}
