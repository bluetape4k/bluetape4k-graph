package io.bluetape4k.graph.io.support

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.graph.io.options.DuplicateVertexPolicy
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.toGraphElementId
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.Test

class GraphIoExternalIdMapTest {

    companion object: KLogging()

    @Test
    fun `put then get resolves backend id`() {
        val map = GraphIoExternalIdMap(DuplicateVertexPolicy.FAIL)
        val id = "backend-1".toGraphElementId()
        map.putFirstOrFail("v1", id) shouldBeEqualTo GraphIoExternalIdMap.PutResult.CREATED
        map.resolve("v1") shouldBeEqualTo id
    }

    @Test
    fun `duplicate under FAIL policy throws`() {
        val map = GraphIoExternalIdMap(DuplicateVertexPolicy.FAIL)
        map.putFirstOrFail("v1", "a".toGraphElementId())

        assertFailsWith<IllegalStateException> {
            map.putFirstOrFail("v1", GraphElementId("b"))
        }
    }

    @Test
    fun `duplicate under SKIP returns SKIPPED and preserves first mapping`() {
        val map = GraphIoExternalIdMap(DuplicateVertexPolicy.SKIP)
        val first = "a".toGraphElementId()

        map.putFirstOrFail("v1", first)
        map.putFirstOrFail("v1", "b".toGraphElementId()) shouldBeEqualTo GraphIoExternalIdMap.PutResult.SKIPPED
        map.resolve("v1") shouldBeEqualTo first
    }

    @Test
    fun `put overwrites existing backend id`() {
        val map = GraphIoExternalIdMap(DuplicateVertexPolicy.FAIL)
        map.putFirstOrFail("v1", "temp".toGraphElementId())
        map.put("v1", "real".toGraphElementId())
        map.resolve("v1") shouldBeEqualTo "real".toGraphElementId()
    }

    @Test
    fun `contains returns true after putFirstOrFail`() {
        val map = GraphIoExternalIdMap(DuplicateVertexPolicy.FAIL)
        map.putFirstOrFail("v1", "a".toGraphElementId())
        map.contains("v1").shouldBeTrue()
        map.contains("v2").shouldBeFalse()
    }

    @Test
    fun `size reflects number of distinct entries`() {
        val map = GraphIoExternalIdMap(DuplicateVertexPolicy.FAIL)
        map.size shouldBeEqualTo 0
        map.putFirstOrFail("v1", "a".toGraphElementId())
        map.putFirstOrFail("v2", "b".toGraphElementId())
        map.size shouldBeEqualTo 2
        map.put("v1", "a2".toGraphElementId())
        map.size shouldBeEqualTo 2
    }

    @Test
    fun `resolve returns null for unknown externalId`() {
        val map = GraphIoExternalIdMap(DuplicateVertexPolicy.FAIL)
        map.resolve("nonexistent").shouldBeNull()
    }

    @Test
    fun `put without putFirstOrFail throws IllegalStateException`() {
        val map = GraphIoExternalIdMap(DuplicateVertexPolicy.FAIL)
        assertFailsWith<IllegalStateException> {
            map.put("never-registered", "real".toGraphElementId())
        }
    }

    @Test
    fun `put after SKIPPED entry still allowed because mapping retains the first id`() {
        val map = GraphIoExternalIdMap(DuplicateVertexPolicy.SKIP)
        map.putFirstOrFail("v1", "first".toGraphElementId())

        // SKIPPED 호출은 매핑을 변경하지 않지만 v1은 putFirstOrFail로 이미 등록됨
        map.putFirstOrFail("v1", "ignored".toGraphElementId()) shouldBeEqualTo GraphIoExternalIdMap.PutResult.SKIPPED

        // put은 v1이 등록되어 있으므로 안전
        map.put("v1", "backend".toGraphElementId())
        map.resolve("v1") shouldBeEqualTo "backend".toGraphElementId()
    }
}
