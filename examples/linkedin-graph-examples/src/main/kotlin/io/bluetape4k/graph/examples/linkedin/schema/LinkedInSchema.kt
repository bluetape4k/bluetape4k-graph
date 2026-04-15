package io.bluetape4k.graph.examples.linkedin.schema

import io.bluetape4k.graph.schema.EdgeLabel
import io.bluetape4k.graph.schema.VertexLabel

/**
 * 사람 정점 레이블. LinkedIn 사용자를 표현한다.
 *
 * ```kotlin
 * val person = ops.createVertex(PersonLabel.label,
 *     mapOf(PersonLabel.name.name to "Alice",
 *           PersonLabel.title.name to "Engineer"))
 * ```
 */
object PersonLabel : VertexLabel("Person") {
    val name = string("name")
    val title = string("title")
    val company = string("company")
    val location = string("location")
    val skills = stringList("skills")
    val connectionCount = integer("connectionCount")
}

/**
 * 회사 정점 레이블.
 *
 * ```kotlin
 * val company = ops.createVertex(CompanyLabel.label,
 *     mapOf(CompanyLabel.name.name to "Bluetape4k",
 *           CompanyLabel.industry.name to "Software"))
 * ```
 */
object CompanyLabel : VertexLabel("Company") {
    val name = string("name")
    val industry = string("industry")
    val size = string("size")   // "startup", "small", "medium", "large", "enterprise"
    val location = string("location")
}

/**
 * 스킬 정점 레이블.
 *
 * ```kotlin
 * val skill = ops.createVertex(SkillLabel.label,
 *     mapOf(SkillLabel.name.name to "Kotlin",
 *           SkillLabel.category.name to "programming"))
 * ```
 */
object SkillLabel : VertexLabel("Skill") {
    val name = string("name")
    val category = string("category")   // "programming", "management", "design", etc.
}

/**
 * 인맥 연결 간선 레이블. 양방향으로 두 번 생성하여 양방향 연결을 표현한다.
 *
 * ```kotlin
 * ops.createEdge(alice.id, bob.id, KnowsLabel.label,
 *     mapOf(KnowsLabel.since.name to "2020-01-01", KnowsLabel.strength.name to 8))
 * ```
 */
object KnowsLabel : EdgeLabel("KNOWS", PersonLabel, PersonLabel) {
    val since = string("since")         // ISO date string
    val strength = integer("strength")  // 1-10
}

/**
 * 재직 정보 간선 레이블. 사람과 회사 사이의 고용 관계를 표현한다.
 *
 * ```kotlin
 * ops.createEdge(alice.id, company.id, WorksAtLabel.label,
 *     mapOf(WorksAtLabel.role.name to "Engineer", WorksAtLabel.isCurrent.name to true))
 * ```
 */
object WorksAtLabel : EdgeLabel("WORKS_AT", PersonLabel, CompanyLabel) {
    val role = string("role")
    val startDate = string("startDate")
    val isCurrent = boolean("isCurrent")
}

/**
 * 팔로우 관계 간선 레이블. 단방향.
 *
 * ```kotlin
 * ops.createEdge(alice.id, bob.id, FollowsLabel.label)
 * ```
 */
object FollowsLabel : EdgeLabel("FOLLOWS", PersonLabel, PersonLabel)

/**
 * 스킬 보유 간선 레이블.
 *
 * ```kotlin
 * ops.createEdge(alice.id, kotlinSkill.id, HasSkillLabel.label,
 *     mapOf(HasSkillLabel.level.name to "expert"))
 * ```
 */
object HasSkillLabel : EdgeLabel("HAS_SKILL", PersonLabel, SkillLabel) {
    val level = string("level")  // "beginner", "intermediate", "expert"
}

/**
 * 스킬 추천(endorsement) 간선 레이블.
 *
 * ```kotlin
 * ops.createEdge(alice.id, bob.id, EndorsesLabel.label,
 *     mapOf(EndorsesLabel.skillName.name to "Kotlin"))
 * ```
 */
object EndorsesLabel : EdgeLabel("ENDORSES", PersonLabel, PersonLabel) {
    val skillName = string("skillName")
}
