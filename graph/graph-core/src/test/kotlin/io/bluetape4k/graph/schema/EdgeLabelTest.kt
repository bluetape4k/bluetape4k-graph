package io.bluetape4k.graph.schema

import io.bluetape4k.assertions.shouldBe
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.graph.AbstractGraphTest
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class EdgeLabelTest: AbstractGraphTest() {

    companion object: KLogging()

    private object PersonLabel: VertexLabel("Person") {
        private fun readResolve(): Any = PersonLabel
    }

    private object CompanyLabel: VertexLabel("Company") {
        private fun readResolve(): Any = CompanyLabel
    }

    private object WorksAtLabel: EdgeLabel("WORKS_AT", PersonLabel, CompanyLabel) {
        private fun readResolve(): Any = WorksAtLabel
        val role = string("role")
        val since = localDate("since")
        val updatedAt = localDateTime("updatedAt")
        val level = integer("level")
        val tenure = long("tenure")
        val current = boolean("current")
        val tags = stringList("tags")
        val meta = json("meta")
        val status = enum("status", Status::class)
    }

    private enum class Status {
        ACTIVE,
        TERMINATED
    }

    @Test
    fun `label과 양 끝 정점 라벨이 유지된다`() {
        WorksAtLabel.label shouldBeEqualTo "WORKS_AT"
        WorksAtLabel.from shouldBe PersonLabel
        WorksAtLabel.to shouldBe CompanyLabel
    }

    @Test
    fun `선언한 프로퍼티가 순서대로 수집된다`() {
        val names = WorksAtLabel.properties.map { it.name }
        log.debug { "names=$names" }
        
        names shouldBeEqualTo listOf(
            "role", "since", "updatedAt", "level", "tenure", "current",
            "tags", "meta", "status",
        )
    }

    @Test
    fun `각 DSL 함수가 올바른 타입을 매핑한다`() {
        WorksAtLabel.role.type shouldBeEqualTo String::class
        WorksAtLabel.since.type shouldBeEqualTo LocalDate::class
        WorksAtLabel.updatedAt.type shouldBeEqualTo LocalDateTime::class
        WorksAtLabel.level.type shouldBeEqualTo Int::class
        WorksAtLabel.tenure.type shouldBeEqualTo Long::class
        WorksAtLabel.current.type shouldBeEqualTo Boolean::class
        WorksAtLabel.tags.type shouldBeEqualTo List::class
        WorksAtLabel.meta.type shouldBeEqualTo Map::class
        WorksAtLabel.status.type shouldBeEqualTo Status::class
    }

    @Test
    fun `정점 라벨이 같아도 양방향 간선 라벨을 각각 정의할 수 있다`() {
        val a = object: EdgeLabel("A", PersonLabel, PersonLabel) {}
        val b = object: EdgeLabel("B", PersonLabel, PersonLabel) {}

        log.debug { "a=$a" }
        log.debug { "b=$b" }

        a.label shouldBeEqualTo "A"
        b.label shouldBeEqualTo "B"
        a.from shouldBe PersonLabel
    }

    @Test
    fun `properties 리스트는 불변 스냅샷이다`() {
        WorksAtLabel.properties.map { it.name } shouldContain "role"
        WorksAtLabel.properties shouldHaveSize 9
    }

    @Test
    fun `프로퍼티가 없는 EdgeLabel도 허용된다`() {
        val empty = object: EdgeLabel("RELATED", PersonLabel, CompanyLabel) {}
        log.debug { "empty=$empty" }

        empty.label shouldBeEqualTo "RELATED"
        empty.properties shouldHaveSize 0
    }

    @Test
    fun `EdgeLabel 서브클래스는 각각 독립된 properties 상태를 가진다`() {
        val otherLabel = object: EdgeLabel("OTHER", PersonLabel, PersonLabel) {
            val score = integer("score")
        }
        log.debug { "otherLabel=$otherLabel" }
        otherLabel.properties shouldHaveSize 1
        WorksAtLabel.properties shouldHaveSize 9
    }

    @Test
    fun `from과 to가 같은 정점 타입이어도 허용된다`() {
        val selfRelation = object: EdgeLabel("KNOWS", PersonLabel, PersonLabel) {}
        log.debug { "selfRelation=$selfRelation" }

        selfRelation.from shouldBe PersonLabel
        selfRelation.to shouldBe PersonLabel
    }
}
