package io.bluetape4k.graph.schema

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class VertexLabelTest {

    private object PersonLabel: VertexLabel("Person") {
        val name = string("name")
        val age = integer("age")
        val score = long("score")
        val active = boolean("active")
        val skills = stringList("skills")
        val meta = json("meta")
        val birthday = localDate("birthday")
        val lastLogin = localDateTime("lastLogin")
        val status = enum("status", Status::class)
    }

    private enum class Status { ACTIVE, INACTIVE }

    @Test
    fun `label 이름이 보존된다`() {
        PersonLabel.label shouldBeEqualTo "Person"
    }

    @Test
    fun `선언한 프로퍼티가 순서대로 수집된다`() {
        val props = PersonLabel.properties
        props.map { it.name } shouldBeEqualTo listOf(
            "name", "age", "score", "active",
            "skills", "meta", "birthday", "lastLogin", "status",
        )
    }

    @Test
    fun `각 DSL 함수가 올바른 Kotlin 타입을 매핑한다`() {
        PersonLabel.name.type shouldBeEqualTo String::class
        PersonLabel.age.type shouldBeEqualTo Int::class
        PersonLabel.score.type shouldBeEqualTo Long::class
        PersonLabel.active.type shouldBeEqualTo Boolean::class
        PersonLabel.skills.type shouldBeEqualTo List::class
        PersonLabel.meta.type shouldBeEqualTo Map::class
        PersonLabel.birthday.type shouldBeEqualTo LocalDate::class
        PersonLabel.lastLogin.type shouldBeEqualTo LocalDateTime::class
        PersonLabel.status.type shouldBeEqualTo Status::class
    }

    @Test
    fun `properties 리스트는 불변 스냅샷이다`() {
        val snapshot = PersonLabel.properties
        snapshot shouldHaveSize 9
        // 동일 레이블 재참조해도 프로퍼티가 중복 추가되지 않는다
        PersonLabel.properties shouldHaveSize 9
    }

    @Test
    fun `PropertyDef는 이름과 타입으로 정의된다`() {
        val def = PropertyDef("x", Int::class)
        def.name shouldBeEqualTo "x"
        def.type shouldBeEqualTo Int::class
    }

    @Test
    fun `VertexLabel 서브클래스는 각각 독립된 properties 상태를 가진다`() {
        val companyProps = TestCompanyLabel.properties.map { it.name }
        companyProps shouldContain "industry"
        companyProps.size shouldBeEqualTo 2
    }

    private object TestCompanyLabel: VertexLabel("Company") {
        val name = string("name")
        val industry = string("industry")
    }
}
