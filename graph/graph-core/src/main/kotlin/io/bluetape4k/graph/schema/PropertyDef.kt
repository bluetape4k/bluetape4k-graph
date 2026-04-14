package io.bluetape4k.graph.schema

import kotlin.reflect.KClass

/**
 * 그래프 속성(Property) 정의.
 */
data class PropertyDef<T: Any>(
    val name: String,
    val type: KClass<out T>,
)

inline fun <reified T: Any> PropertyDef(name: String): PropertyDef<T> = PropertyDef(name, T::class)
