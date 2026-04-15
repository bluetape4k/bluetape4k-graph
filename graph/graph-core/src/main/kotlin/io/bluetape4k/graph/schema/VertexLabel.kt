package io.bluetape4k.graph.schema

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.reflect.KClass

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
abstract class VertexLabel(val label: String) {
    private val _properties = mutableListOf<PropertyDef<*>>()

    /**
     * 이 레이블에 정의된 모든 [PropertyDef]의 불변 스냅샷.
     *
     * 매 접근 시 내부 목록의 복사본을 반환하므로 외부에서 수정해도 원본에 영향이 없다.
     */
    val properties: List<PropertyDef<*>> get() = _properties.toList()

    /** `String` 타입 속성을 선언한다. @param name 속성 이름. */
    fun string(name: String) = PropertyDef<String>(name).also { _properties.add(it) }

    /** `Int` 타입 속성을 선언한다. @param name 속성 이름. */
    fun integer(name: String) = PropertyDef<Int>(name).also { _properties.add(it) }

    /** `Long` 타입 속성을 선언한다. @param name 속성 이름. */
    fun long(name: String) = PropertyDef<Long>(name).also { _properties.add(it) }

    /** `Boolean` 타입 속성을 선언한다. @param name 속성 이름. */
    fun boolean(name: String) = PropertyDef<Boolean>(name).also { _properties.add(it) }

    /** `List<String>` 타입 속성을 선언한다. @param name 속성 이름. */
    fun stringList(name: String) = PropertyDef<List<String>>(name).also { _properties.add(it) }

    /** `Map<String, Any?>` 타입의 JSON 속성을 선언한다. @param name 속성 이름. */
    fun json(name: String) = PropertyDef<Map<String, Any?>>(name).also { _properties.add(it) }

    /** [LocalDate] 타입 속성을 선언한다. @param name 속성 이름. */
    fun localDate(name: String) = PropertyDef<LocalDate>(name).also { _properties.add(it) }

    /** [LocalDateTime] 타입 속성을 선언한다. @param name 속성 이름. */
    fun localDateTime(name: String) = PropertyDef<LocalDateTime>(name).also { _properties.add(it) }

    /**
     * Enum 타입 속성을 선언한다.
     *
     * @param name 속성 이름.
     * @param type Enum 클래스의 [KClass].
     */
    fun <E: Enum<E>> enum(name: String, type: KClass<E>) = PropertyDef(name, type).also { _properties.add(it) }
}
