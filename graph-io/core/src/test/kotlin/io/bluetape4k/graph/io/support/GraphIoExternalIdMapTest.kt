package io.bluetape4k.graph.io.support

import io.bluetape4k.graph.io.options.DuplicateVertexPolicy
import io.bluetape4k.graph.model.GraphElementId
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldThrow
import org.junit.jupiter.api.Test

class GraphIoExternalIdMapTest {

    @Test
    fun `put then get resolves backend id`() {
        val map = GraphIoExternalIdMap(DuplicateVertexPolicy.FAIL)
        val id = GraphElementId("backend-1")
        map.putFirstOrFail("v1", id) shouldBeEqualTo GraphIoExternalIdMap.PutResult.CREATED
        map.resolve("v1") shouldBeEqualTo id
    }

    @Test
    fun `duplicate under FAIL policy throws`() {
        val map = GraphIoExternalIdMap(DuplicateVertexPolicy.FAIL)
        map.putFirstOrFail("v1", GraphElementId("a"))
        val action = { map.putFirstOrFail("v1", GraphElementId("b")) }
        action shouldThrow IllegalStateException::class
    }

    @Test
    fun `duplicate under SKIP returns SKIPPED and preserves first mapping`() {
        val map = GraphIoExternalIdMap(DuplicateVertexPolicy.SKIP)
        val first = GraphElementId("a")
        map.putFirstOrFail("v1", first)
        map.putFirstOrFail("v1", GraphElementId("b")) shouldBeEqualTo GraphIoExternalIdMap.PutResult.SKIPPED
        map.resolve("v1") shouldBeEqualTo first
    }

    @Test
    fun `put overwrites existing backend id`() {
        val map = GraphIoExternalIdMap(DuplicateVertexPolicy.FAIL)
        map.putFirstOrFail("v1", GraphElementId("temp"))
        map.put("v1", GraphElementId("real"))
        map.resolve("v1") shouldBeEqualTo GraphElementId("real")
    }

    @Test
    fun `contains returns true after putFirstOrFail`() {
        val map = GraphIoExternalIdMap(DuplicateVertexPolicy.FAIL)
        map.putFirstOrFail("v1", GraphElementId("a"))
        map.contains("v1") shouldBeEqualTo true
        map.contains("v2") shouldBeEqualTo false
    }

    @Test
    fun `size reflects number of distinct entries`() {
        val map = GraphIoExternalIdMap(DuplicateVertexPolicy.FAIL)
        map.size shouldBeEqualTo 0
        map.putFirstOrFail("v1", GraphElementId("a"))
        map.putFirstOrFail("v2", GraphElementId("b"))
        map.size shouldBeEqualTo 2
        map.put("v1", GraphElementId("a2"))
        map.size shouldBeEqualTo 2
    }

    @Test
    fun `resolve returns null for unknown externalId`() {
        val map = GraphIoExternalIdMap(DuplicateVertexPolicy.FAIL)
        map.resolve("nonexistent") shouldBeEqualTo null
    }
}
