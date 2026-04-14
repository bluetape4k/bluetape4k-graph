package io.bluetape4k.graph.schema

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.reflect.KClass

/**
 * 그래프 정점(Vertex) 스키마 정의. Exposed Table 스타일의 DSL.
 * 백엔드(AGE, Neo4j)에 무관하게 사용 가능.
 */
abstract class VertexLabel(val label: String) {
    private val _properties = mutableListOf<PropertyDef<*>>()
    val properties: List<PropertyDef<*>> get() = _properties.toList()

    fun string(name: String) = PropertyDef<String>(name).also { _properties.add(it) }
    fun integer(name: String) = PropertyDef<Int>(name).also { _properties.add(it) }
    fun long(name: String) = PropertyDef<Long>(name).also { _properties.add(it) }
    fun boolean(name: String) = PropertyDef<Boolean>(name).also { _properties.add(it) }

    fun stringList(name: String) = PropertyDef<List<String>>(name).also { _properties.add(it) }

    fun json(name: String) = PropertyDef<Map<String, Any?>>(name).also { _properties.add(it) }

    fun localDate(name: String) = PropertyDef<LocalDate>(name).also { _properties.add(it) }
    fun localDateTime(name: String) = PropertyDef<LocalDateTime>(name).also { _properties.add(it) }

    fun <E: Enum<E>> enum(name: String, type: KClass<E>) = PropertyDef(name, type).also { _properties.add(it) }
}
