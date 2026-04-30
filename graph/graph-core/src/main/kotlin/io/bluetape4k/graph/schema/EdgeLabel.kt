package io.bluetape4k.graph.schema

/**
 * 그래프 간선(Edge) 스키마 정의.
 *
 * [VertexLabel]과 동일한 DSL 스타일로 간선 타입의 스키마를 선언한다.
 * 시작 정점([from])과 종료 정점([to])의 타입을 명시하여 관계의 방향성과 도메인 제약을 표현한다.
 *
 * ```kotlin
 * object WorksAtLabel : EdgeLabel("WORKS_AT", PersonLabel, CompanyLabel) {
 *     val role  = string("role")
 *     val since = localDate("since")
 * }
 * ```
 *
 * @property label 간선 레이블 이름 (예: `"KNOWS"`, `"WORKS_AT"`).
 * @property from 간선의 시작 정점 레이블.
 * @property to 간선의 종료 정점 레이블.
 */
abstract class EdgeLabel(
    val label: String,
    val from: VertexLabel,
    val to: VertexLabel,
) : PropertyHolder()
