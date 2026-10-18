package io.bluetape4k.graph.age.sql

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.assertions.shouldNotBeEmpty
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test

class AgeTypeParserTest {

    companion object: KLogging()

    // ── isVertex / isEdge / isPath ────────────────────────────────────────

    @Test
    fun `isVertex - vertex 접미사가 있으면 true를 반환한다`() {
        val agtype = """{"id": 1, "label": "Person", "properties": {}}::vertex"""
        AgeTypeParser.isVertex(agtype).shouldBeTrue()
    }

    @Test
    fun `isVertex - edge 접미사가 있으면 false를 반환한다`() {
        val agtype = """{"id": 1, "label": "KNOWS", "start_id": 1, "end_id": 2, "properties": {}}::edge"""
        AgeTypeParser.isVertex(agtype).shouldBeFalse()
    }

    @Test
    fun `isVertex - 빈 문자열은 false를 반환한다`() {
        AgeTypeParser.isVertex("").shouldBeFalse()
    }

    @Test
    fun `isEdge - edge 접미사가 있으면 true를 반환한다`() {
        val agtype = """{"id": 2, "label": "KNOWS", "start_id": 1, "end_id": 3, "properties": {}}::edge"""
        AgeTypeParser.isEdge(agtype).shouldBeTrue()
    }

    @Test
    fun `isEdge - vertex 접미사가 있으면 false를 반환한다`() {
        val agtype = """{"id": 1, "label": "Person", "properties": {}}::vertex"""
        AgeTypeParser.isEdge(agtype).shouldBeFalse()
    }

    @Test
    fun `isEdge - 빈 문자열은 false를 반환한다`() {
        AgeTypeParser.isEdge("").shouldBeFalse()
    }

    @Test
    fun `isPath - path 접미사가 있으면 true를 반환한다`() {
        val agtype = """[{"id": 1, "label": "Person", "properties": {}}::vertex]::path"""
        AgeTypeParser.isPath(agtype).shouldBeTrue()
    }

    @Test
    fun `isPath - vertex 접미사가 있으면 false를 반환한다`() {
        val agtype = """{"id": 1, "label": "Person", "properties": {}}::vertex"""
        AgeTypeParser.isPath(agtype).shouldBeFalse()
    }

    @Test
    fun `isPath - 빈 문자열은 false를 반환한다`() {
        AgeTypeParser.isPath("").shouldBeFalse()
    }

    // ── parseVertex ────────────────────────────────────────────────────────

    @Test
    fun `parseVertex - 정상적인 agtype 문자열을 GraphVertex로 파싱한다`() {
        val agtype = """{"id": 1, "label": "Person", "properties": {"name": "Alice", "age": 30}}::vertex"""
        val vertex = AgeTypeParser.parseVertex(agtype)

        vertex.id.value shouldBeEqualTo "1"
        vertex.label shouldBeEqualTo "Person"
        vertex.properties["name"] shouldBeEqualTo "Alice"
        vertex.properties["age"] shouldBeEqualTo 30L
    }

    @Test
    fun `parseVertex - properties가 비어 있어도 파싱된다`() {
        val agtype = """{"id": 42, "label": "Company", "properties": {}}::vertex"""
        val vertex = AgeTypeParser.parseVertex(agtype)

        vertex.id.value shouldBeEqualTo "42"
        vertex.label shouldBeEqualTo "Company"
        vertex.properties.shouldBeEmpty()
    }

    @Test
    fun `parseVertex - 필수 id와 label이 없으면 실패한다`() {
        val missingId = assertFailsWith<IllegalArgumentException> {
            AgeTypeParser.parseVertex("""{"label": "Person", "properties": {}}::vertex""")
        }
        missingId.message shouldContain "id"

        val missingLabel = assertFailsWith<IllegalArgumentException> {
            AgeTypeParser.parseVertex("""{"id": 1, "properties": {}}::vertex""")
        }
        missingLabel.message shouldContain "label"
    }

    @Test
    fun `parseVertex - id가 정수가 아니거나 Long 범위를 벗어나면 실패한다`() {
        val fractional = assertFailsWith<IllegalArgumentException> {
            AgeTypeParser.parseVertex("""{"id": 1.5, "label": "Person", "properties": {}}::vertex""")
        }
        fractional.message shouldContain "integer"

        val outOfRange = assertFailsWith<IllegalArgumentException> {
            AgeTypeParser.parseVertex(
                """{"id": 9223372036854775808, "label": "Person", "properties": {}}::vertex""",
            )
        }
        outOfRange.message shouldContain "integer"
    }

    // ── parseEdge ─────────────────────────────────────────────────────────

    @Test
    fun `parseEdge - 정상적인 agtype 문자열을 GraphEdge로 파싱한다`() {
        val agtype = """{"id": 2, "label": "KNOWS", "start_id": 1, "end_id": 3, "properties": {"since": 2023}}::edge"""
        val edge = AgeTypeParser.parseEdge(agtype)

        edge.id.value shouldBeEqualTo "2"
        edge.label shouldBeEqualTo "KNOWS"
        edge.startId.value shouldBeEqualTo "1"
        edge.endId.value shouldBeEqualTo "3"
        edge.properties["since"] shouldBeEqualTo 2023L
    }

    @Test
    fun `parseEdge - properties가 비어 있어도 파싱된다`() {
        val agtype = """{"id": 5, "label": "WORKS_AT", "start_id": 10, "end_id": 20, "properties": {}}::edge"""
        val edge = AgeTypeParser.parseEdge(agtype)

        edge.id.value shouldBeEqualTo "5"
        edge.label shouldBeEqualTo "WORKS_AT"
        edge.startId.value shouldBeEqualTo "10"
        edge.endId.value shouldBeEqualTo "20"
        edge.properties.shouldBeEmpty()
    }

    @Test
    fun `parseEdge - 필수 식별자가 없으면 실패한다`() {
        val missingStartId = assertFailsWith<IllegalArgumentException> {
            AgeTypeParser.parseEdge(
                """{"id": 2, "label": "KNOWS", "end_id": 3, "properties": {}}::edge""",
            )
        }
        missingStartId.message shouldContain "start_id"
    }

    // ── parsePath ─────────────────────────────────────────────────────────

    @Test
    fun `parsePath - 단일 정점으로 구성된 path를 파싱한다`() {
        val agtype = """[{"id": 1, "label": "Person", "properties": {}}::vertex]::path"""
        val path = AgeTypeParser.parsePath(agtype)

        path.steps shouldHaveSize 1
    }

    @Test
    fun `parsePath - 정점-간선-정점 형태의 path를 파싱한다`() {
        val agtype =
            """[{"id": 1, "label": "Person", "properties": {}}::vertex, {"id": 2, "label": "KNOWS", "start_id": 1, "end_id": 3, "properties": {}}::edge, {"id": 3, "label": "Person", "properties": {}}::vertex]::path"""
        val path = AgeTypeParser.parsePath(agtype).also { log.debug { "path: $it" } }
        path.steps shouldHaveSize 3
    }

    @Test
    fun `parsePath - 빈 path 배열을 파싱하면 steps가 비어 있다`() {
        val agtype = """[]::path"""
        val path = AgeTypeParser.parsePath(agtype).also { log.debug { "path: $it" } }

        path.steps.shouldBeEmpty()
    }

    // ── parseJsonObject ───────────────────────────────────────────────────

    @Test
    fun `parseJsonObject - 빈 JSON 객체를 파싱하면 빈 맵을 반환한다`() {
        val result = AgeTypeParser.parseJsonObject("{}")
        result.shouldBeEmpty()
    }

    @Test
    fun `parseJsonObject - 문자열과 숫자 값을 파싱한다`() {
        val json = """{"name": "Alice", "age": 30}"""
        val result = AgeTypeParser.parseJsonObject(json)

        result["name"] shouldBeEqualTo "Alice"
        result["age"] shouldBeEqualTo 30L
    }

    @Test
    fun `parseJsonObject - null 값을 파싱한다`() {
        val json = """{"nickname": null}"""
        val result = AgeTypeParser.parseJsonObject(json)

        result.keys shouldContain "nickname"
        result["nickname"].shouldBeNull()
    }

    @Test
    fun `parseJsonObject - boolean 값을 파싱한다`() {
        val json = """{"active": true, "deleted": false}"""
        val result = AgeTypeParser.parseJsonObject(json)

        result["active"] shouldBeEqualTo true
        result["deleted"] shouldBeEqualTo false
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `parseJsonObject - 중첩 객체를 파싱한다`() {
        val json = """{"id": 1, "properties": {"name": "Bob"}}"""
        val result = AgeTypeParser.parseJsonObject(json)

        result["id"] shouldBeEqualTo 1L

        val props = result["properties"] as Map<String, Any?>
        props["name"] shouldBeEqualTo "Bob"
    }

    @Test
    fun `parseJsonObject - 소수점 숫자를 파싱한다`() {
        val json = """{"score": 3.14}"""
        val result = AgeTypeParser.parseJsonObject(json)

        result["score"] shouldBeEqualTo 3.14
    }

    // ── parseJsonArray ────────────────────────────────────────────────────

    @Test
    fun `parseJsonArray - 빈 배열을 파싱하면 빈 리스트를 반환한다`() {
        val result = AgeTypeParser.parseJsonArray("[]")
        result.shouldBeEmpty()
    }

    @Test
    fun `parseJsonArray - 단일 문자열 요소를 파싱한다`() {
        val result = AgeTypeParser.parseJsonArray("""["hello"]""")

        result shouldHaveSize 1
        result[0] shouldBeEqualTo "hello"
    }

    @Test
    fun `parseJsonArray - 숫자 요소들을 파싱한다`() {
        val result = AgeTypeParser.parseJsonArray("[1, 2, 3]").also { log.debug { "result=$it" } }

        result shouldHaveSize 3
        result shouldContain 1L
        result shouldContain 2L
        result shouldContain 3L
    }

    @Test
    fun `parseJsonArray - 혼합 타입 요소들을 파싱한다`() {
        val result = AgeTypeParser.parseJsonArray("""["text", 42, true, null]""")

        result shouldHaveSize 4
        result[0] shouldBeEqualTo "text"
        result[1] shouldBeEqualTo 42L
        result[2] shouldBeEqualTo true
        result[3].shouldBeNull()
    }

    @Suppress("UNCHECKED_CAST")
    @Test
    fun `parseJsonArray - 중첩 객체를 포함한 배열을 파싱한다`() {
        val result = AgeTypeParser.parseJsonArray("""[{"key": "val"}]""")

        result shouldHaveSize 1
        result.shouldNotBeEmpty()

        val obj = result[0] as Map<String, Any?>
        obj["key"] shouldBeEqualTo "val"
    }
}
