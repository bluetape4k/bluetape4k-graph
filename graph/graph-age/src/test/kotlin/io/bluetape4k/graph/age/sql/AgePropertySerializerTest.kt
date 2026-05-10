package io.bluetape4k.graph.age.sql

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import org.junit.jupiter.api.Test

class AgePropertySerializerTest {

    @Test
    fun `string values are escaped for AGE Cypher literals`() {
        AgePropertySerializer.toCypherValue("a\\b") shouldBeEqualTo "'a\\\\b'"
        AgePropertySerializer.toCypherValue("it's") shouldBeEqualTo "'it\\'s'"
        AgePropertySerializer.toCypherValue("a\nb\rc\td") shouldBeEqualTo "'a\\nb\\rc\\td'"
    }

    @Test
    fun `primitive values are rendered as Cypher literals`() {
        AgePropertySerializer.toCypherValue(null) shouldBeEqualTo "null"
        AgePropertySerializer.toCypherValue(true) shouldBeEqualTo "true"
        AgePropertySerializer.toCypherValue(false) shouldBeEqualTo "false"
        AgePropertySerializer.toCypherValue(42) shouldBeEqualTo "42"
        AgePropertySerializer.toCypherValue(3.14) shouldBeEqualTo "3.14"
    }

    @Test
    fun `list values are rendered recursively`() {
        AgePropertySerializer.toCypherValue(listOf("a", 1, true, null)) shouldBeEqualTo "['a', 1, true, null]"
    }

    @Test
    fun `nested map values validate keys and render recursively`() {
        val value = mapOf(
            "profile" to mapOf(
                "displayName" to "Alice",
                "tags" to listOf("kotlin", "graph"),
            )
        )

        AgePropertySerializer.toCypherValue(value) shouldBeEqualTo
                "{profile: {displayName: 'Alice', tags: ['kotlin', 'graph']}}"
    }

    @Test
    fun `toCypherProps validates top-level property keys`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            AgePropertySerializer.toCypherProps(mapOf("name) DELETE n" to "Alice"))
        }

        ex.message shouldContain "valid identifier"
    }

    @Test
    fun `toCypherValue validates nested map keys`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            AgePropertySerializer.toCypherValue(mapOf("profile" to mapOf("display-name" to "Alice")))
        }

        ex.message shouldContain "valid identifier"
    }

    @Test
    fun `toCypherValue rejects non-string nested map keys`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            AgePropertySerializer.toCypherValue(mapOf(1 to "one"))
        }

        ex.message shouldContain "must be a String"
    }

    @Test
    fun `toCypherAssignments validates variable and property keys`() {
        AgePropertySerializer.toCypherAssignments("v", mapOf("name" to "Alice", "age" to 30)) shouldBeEqualTo
                "v.name = 'Alice', v.age = 30"

        val ex = assertFailsWith<IllegalArgumentException> {
            AgePropertySerializer.toCypherAssignments("v) DELETE n", mapOf("name" to "Alice"))
        }
        ex.message shouldContain "valid identifier"
    }
}
