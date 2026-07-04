package io.bluetape4k.graph.schema

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.reflect.KClass

/**
 * Base property DSL shared by [VertexLabel] and [EdgeLabel].
 *
 * Properties are declared in an Exposed Table-style DSL and exposed through [properties].
 *
 * `object` subclasses are initialized safely by JVM class loading. Non-object instances keep
 * independent property lists.
 */
abstract class PropertyHolder {
    private val _properties = mutableListOf<PropertyDef<*>>()

    /**
     * Immutable snapshot of all [PropertyDef] values defined on this label.
	*
     * Each access returns a copy, so external mutation cannot affect the source list.
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
     * Defines an enum property.
	*
     * @param name property name.
     * @param type enum [KClass].
     */
    fun <E : Enum<E>> enum(name: String, type: KClass<E>) = PropertyDef(name, type).also { _properties.add(it) }
}
