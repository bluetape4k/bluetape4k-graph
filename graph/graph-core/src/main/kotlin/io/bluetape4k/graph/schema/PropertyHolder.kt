package io.bluetape4k.graph.schema

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.reflect.KClass

/**
 * [VertexLabel]과 [EdgeLabel]이 공유하는 속성 DSL 기반 클래스.
 *
 * Exposed Table 스타일 DSL로 속성을 선언하며, 내부 목록을 통해 등록된 모든 [PropertyDef]를
 * [properties]로 노출한다.
 *
 * 주의: `object`로 상속되는 경우 JVM 클래스 로딩 시 thread-safe하게 초기화된다.
 * 비-`object` 다중 인스턴스 컨텍스트에서는 각 인스턴스가 독립적인 `_properties` 목록을 가진다.
 */
abstract class PropertyHolder {
    private val _properties = mutableListOf<PropertyDef<*>>()

    /**
     * 이 레이블에 정의된 모든 [PropertyDef]의 불변 스냅샷.
     *
     * 매 접근 시 내부 목록의 복사본을 반환하므로 외부에서 수정해도 원본에 영향이 없다.
     */
    val properties: List<PropertyDef<*>> get() = _properties.toList()

    /** 문자열 속성 [PropertyDef]를 정의한다. */
    fun string(name: String) = PropertyDef<String>(name).also { _properties.add(it) }

    /** Int 속성 [PropertyDef]를 정의한다. */
    fun integer(name: String) = PropertyDef<Int>(name).also { _properties.add(it) }

    /** Long 속성 [PropertyDef]를 정의한다. */
    fun long(name: String) = PropertyDef<Long>(name).also { _properties.add(it) }

    /** Boolean 속성 [PropertyDef]를 정의한다. */
    fun boolean(name: String) = PropertyDef<Boolean>(name).also { _properties.add(it) }

    /** List&lt;String&gt; 속성 [PropertyDef]를 정의한다. */
    fun stringList(name: String) = PropertyDef<List<String>>(name).also { _properties.add(it) }

    /** JSON Map 속성 [PropertyDef]를 정의한다. */
    fun json(name: String) = PropertyDef<Map<String, Any?>>(name).also { _properties.add(it) }

    /** LocalDate 속성 [PropertyDef]를 정의한다. */
    fun localDate(name: String) = PropertyDef<LocalDate>(name).also { _properties.add(it) }

    /** LocalDateTime 속성 [PropertyDef]를 정의한다. */
    fun localDateTime(name: String) = PropertyDef<LocalDateTime>(name).also { _properties.add(it) }

    /**
     * Enum 타입 속성을 선언한다.
     *
     * @param name 속성 이름.
     * @param type Enum 클래스의 [KClass].
     */
    fun <E : Enum<E>> enum(name: String, type: KClass<E>) = PropertyDef(name, type).also { _properties.add(it) }
}
