package io.bluetape4k.graph.schema

import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class EdgeLabelTest {

    private object PersonLabel: VertexLabel("Person")
    private object CompanyLabel: VertexLabel("Company")

    private object WorksAtLabel: EdgeLabel("WORKS_AT", PersonLabel, CompanyLabel) {
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

        a.label shouldBeEqualTo "A"
        b.label shouldBeEqualTo "B"
        a.from shouldBe PersonLabel
    }

    @Test
    fun `properties 리스트는 불변 스냅샷이다`() {
        WorksAtLabel.properties.map { it.name } shouldContain "role"
        WorksAtLabel.properties shouldHaveSize 9
    }
}
