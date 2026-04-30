package io.bluetape4k.graph.schema

/**
 * 그래프 정점(Vertex) 스키마 정의. Exposed Table 스타일의 DSL.
 * 백엔드(AGE, Neo4j)에 무관하게 사용 가능.
 *
 * `object`로 상속하여 정점 타입별 스키마를 선언하고, 각 DSL 함수 호출 결과인
 * [PropertyDef]를 프로퍼티로 보유한다.
 *
 * ```kotlin
 * object PersonLabel : VertexLabel("Person") {
 *     val name = string("name")
 *     val age  = integer("age")
 * }
 * ```
 *
 * @property label 정점 레이블 이름 (예: `"Person"`, `"Company"`).
 */
abstract class VertexLabel(val label: String) : PropertyHolder()
