package io.bluetape4k.graph.examples.linkedin.schema

import io.bluetape4k.graph.schema.EdgeLabel
import io.bluetape4k.graph.schema.VertexLabel

/** 사람 정점 레이블. LinkedIn 사용자를 표현한다. */
object PersonLabel : VertexLabel("Person") {
    val name = string("name")
    val title = string("title")
    val company = string("company")
    val location = string("location")
    val skills = stringList("skills")
    val connectionCount = integer("connectionCount")
}

/** 회사 정점 레이블. */
object CompanyLabel : VertexLabel("Company") {
    val name = string("name")
    val industry = string("industry")
    val size = string("size")   // "startup", "small", "medium", "large", "enterprise"
    val location = string("location")
}

/** 스킬 정점 레이블. */
object SkillLabel : VertexLabel("Skill") {
    val name = string("name")
    val category = string("category")   // "programming", "management", "design", etc.
}

/** 인맥 연결 간선 레이블. 양방향으로 두 번 생성하여 양방향 연결을 표현한다. */
object KnowsLabel : EdgeLabel("KNOWS", PersonLabel, PersonLabel) {
    val since = string("since")         // ISO date string
    val strength = integer("strength")  // 1-10
}

/** 재직 정보 간선 레이블. 사람과 회사 사이의 고용 관계를 표현한다. */
object WorksAtLabel : EdgeLabel("WORKS_AT", PersonLabel, CompanyLabel) {
    val role = string("role")
    val startDate = string("startDate")
    val isCurrent = boolean("isCurrent")
}

/** 팔로우 관계 간선 레이블. 단방향. */
object FollowsLabel : EdgeLabel("FOLLOWS", PersonLabel, PersonLabel)

/** 스킬 보유 간선 레이블. */
object HasSkillLabel : EdgeLabel("HAS_SKILL", PersonLabel, SkillLabel) {
    val level = string("level")  // "beginner", "intermediate", "expert"
}

/** 스킬 추천(endorsement) 간선 레이블. */
object EndorsesLabel : EdgeLabel("ENDORSES", PersonLabel, PersonLabel) {
    val skillName = string("skillName")
}
