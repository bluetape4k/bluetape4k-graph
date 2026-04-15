package io.bluetape4k.graph.schema

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.reflect.KClass

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
) {
    private val _properties = mutableListOf<PropertyDef<*>>()

    /**
     * 이 레이블에 정의된 모든 [PropertyDef]의 불변 스냅샷.
     *
     * 매 접근 시 내부 목록의 복사본을 반환하므로 외부에서 수정해도 원본에 영향이 없다.
     */
    val properties: List<PropertyDef<*>> get() = _properties.toList()

    /**
     * 문자열 속성 [PropertyDef]를 정의한다.
     *
     * ```kotlin
     * object PersonLabel : VertexLabel("Person") {
     *     val name = string("name")  // PropertyDef<String>
     * }
     * ```
     */
    fun string(name: String) = PropertyDef<String>(name).also { _properties.add(it) }

    /**
     * Int 속성 [PropertyDef]를 정의한다.
     *
     * ```kotlin
     * val age = integer("age")  // PropertyDef<Int>
     * ```
     */
    fun integer(name: String) = PropertyDef<Int>(name).also { _properties.add(it) }

    /**
     * Long 속성 [PropertyDef]를 정의한다.
     *
     * ```kotlin
     * val count = long("count")  // PropertyDef<Long>
     * ```
     */
    fun long(name: String) = PropertyDef<Long>(name).also { _properties.add(it) }

    /**
     * Boolean 속성 [PropertyDef]를 정의한다.
     *
     * ```kotlin
     * val isActive = boolean("isActive")  // PropertyDef<Boolean>
     * ```
     */
    fun boolean(name: String) = PropertyDef<Boolean>(name).also { _properties.add(it) }

    /**
     * List&lt;String&gt; 속성 [PropertyDef]를 정의한다.
     *
     * ```kotlin
     * val tags = stringList("tags")  // PropertyDef<List<String>>
     * ```
     */
    @Suppress("UNCHECKED_CAST")
    fun stringList(name: String) = PropertyDef<List<String>>(name).also { _properties.add(it) }

    /**
     * JSON Map 속성 [PropertyDef]를 정의한다.
     *
     * ```kotlin
     * val meta = json("meta")  // PropertyDef<Map<String, Any?>>
     * ```
     */
    @Suppress("UNCHECKED_CAST")
    fun json(name: String) = PropertyDef<Map<String, Any?>>(name).also { _properties.add(it) }

    /**
     * LocalDate 속성 [PropertyDef]를 정의한다.
     *
     * ```kotlin
     * val birthday = localDate("birthday")  // PropertyDef<LocalDate>
     * ```
     */
    fun localDate(name: String) = PropertyDef<LocalDate>(name).also { _properties.add(it) }

    /**
     * LocalDateTime 속성 [PropertyDef]를 정의한다.
     *
     * ```kotlin
     * val createdAt = localDateTime("createdAt")  // PropertyDef<LocalDateTime>
     * ```
     */
    fun localDateTime(name: String) = PropertyDef<LocalDateTime>(name).also { _properties.add(it) }

    /**
     * Enum 타입 속성을 선언한다.
     *
     * ```kotlin
     * enum class Status { ACTIVE, INACTIVE }
     * val status = enum("status", Status::class)
     * ```
     *
     * @param name 속성 이름.
     * @param type Enum 클래스의 [KClass].
     */
    fun <E : Enum<E>> enum(name: String, type: KClass<E>) = PropertyDef(name, type).also { _properties.add(it) }
}
