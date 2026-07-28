package io.bluetape4k.graph.schema

import kotlin.reflect.KClass

/**
 * Graph property 정의.
 *
 * 이 type-safe metadata는 [VertexLabel]과 [EdgeLabel] DSL method가 반환한다.
 * graph property name과 Kotlin value type을 함께 추적한다.
 *
 * ```kotlin
 * val nameDef = PropertyDef<String>("name")      // inline factory
 * val ageDef  = PropertyDef("age", Int::class)   // explicit
 * println(nameDef.name)  // "name"
 * println(nameDef.type)  // class kotlin.String
 * ```
 *
 * @property name graph backend에 저장되는 property key.
 * @property type property value에 대응되는 Kotlin [KClass].
 */
data class PropertyDef<T: Any>(
    val name: String,
    val type: KClass<out T>,
)

/**
 * reified type parameter로 [PropertyDef]를 생성하는 inline factory.
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
