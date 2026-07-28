package io.bluetape4k.graph.schema

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.reflect.KClass

/**
 * [VertexLabel]과 [EdgeLabel]이 공유하는 기본 property DSL.
 *
 * property는 Exposed Table-style DSL로 선언되고 [properties]를 통해 노출된다.
 *
 * `object` subclasses are initialized safely by JVM class loading. Non-object instances keep
 * 독립적인 property list를 가진다.
 */
abstract class PropertyHolder {
    private val _properties = mutableListOf<PropertyDef<*>>()

    /**
     * 이 label에 정의된 모든 [PropertyDef] value의 immutable snapshot.
	*
     * 접근할 때마다 copy를 반환하므로 외부 mutation이 source list에 영향을 주지 않는다.
	 */
    val properties: List<PropertyDef<*>> get() = _properties.toList()

    /** Defines a string property. */
    fun string(name: String) = PropertyDef<String>(name).also { _properties.add(it) }

    /** Defines an Int property. */
    fun integer(name: String) = PropertyDef<Int>(name).also { _properties.add(it) }

    /** Defines a Long property. */
    fun long(name: String) = PropertyDef<Long>(name).also { _properties.add(it) }

    /** Defines a Boolean property. */
    fun boolean(name: String) = PropertyDef<Boolean>(name).also { _properties.add(it) }

    /** Defines a List&lt;String&gt; property. */
    fun stringList(name: String) = PropertyDef<List<String>>(name).also { _properties.add(it) }

    /** Defines a JSON map property. */
    fun json(name: String) = PropertyDef<Map<String, Any?>>(name).also { _properties.add(it) }

    /** Defines a LocalDate property. */
    fun localDate(name: String) = PropertyDef<LocalDate>(name).also { _properties.add(it) }

    /** Defines a LocalDateTime property. */
    fun localDateTime(name: String) = PropertyDef<LocalDateTime>(name).also { _properties.add(it) }

    /**
     * enum property를 정의한다.
	*
     * @param name property name.
     * @param type enum [KClass].
     */
    fun <E : Enum<E>> enum(name: String, type: KClass<E>) = PropertyDef(name, type).also { _properties.add(it) }
}
