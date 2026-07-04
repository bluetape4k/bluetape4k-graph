package io.bluetape4k.graph.schema

import kotlin.reflect.KClass

/**
 * Graph property definition.
 *
 * This type-safe metadata is returned by [VertexLabel] and [EdgeLabel] DSL methods.
 * It tracks the graph property name and Kotlin value type together.
 *
 * ```kotlin
 * val nameDef = PropertyDef<String>("name")      // inline factory
 * val ageDef  = PropertyDef("age", Int::class)   // explicit
 * println(nameDef.name)  // "name"
 * println(nameDef.type)  // class kotlin.String
 * ```
 *
 * @property name property key stored in the graph backend.
 * @property type Kotlin [KClass] for the property value.
 */
data class PropertyDef<T: Any>(
    val name: String,
    val type: KClass<out T>,
)

/**
 * Inline factory that creates a [PropertyDef] from a reified type parameter.
 *
 * ```kotlin
 * val nameDef: PropertyDef<String> = PropertyDef("name")       // inline factory
 * val ageDef: PropertyDef<Int>    = PropertyDef("age", Int::class)  // explicit
 * println(nameDef.name)  // "name"
 * println(nameDef.type)  // class kotlin.String
 * ```
 *
 * @param name property name.
 */
inline fun <reified T: Any> PropertyDef(name: String): PropertyDef<T> = PropertyDef(name, T::class)
