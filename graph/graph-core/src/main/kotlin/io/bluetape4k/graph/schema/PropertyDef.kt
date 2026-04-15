package io.bluetape4k.graph.schema

import kotlin.reflect.KClass

/**
 * 그래프 속성(Property) 정의.
 *
 * [VertexLabel] 또는 [EdgeLabel]의 DSL 메서드가 반환하는 타입 안전 속성 메타데이터이다.
 * 컴파일 타임에 속성 이름과 Kotlin 타입을 함께 추적한다.
 *
 * @property name 속성 이름 (그래프 백엔드에 저장되는 키).
 * @property type 속성 값의 Kotlin [KClass].
 */
data class PropertyDef<T: Any>(
    val name: String,
    val type: KClass<out T>,
)

/**
 * reified 타입 파라미터를 사용하여 [PropertyDef]를 생성하는 인라인 팩토리 함수.
 *
 * ```kotlin
 * val nameDef: PropertyDef<String> = PropertyDef("name")       // inline factory
 * val ageDef: PropertyDef<Int>    = PropertyDef("age", Int::class)  // 명시적
 * println(nameDef.name)  // "name"
 * println(nameDef.type)  // class kotlin.String
 * ```
 *
 * @param name 속성 이름.
 */
inline fun <reified T: Any> PropertyDef(name: String): PropertyDef<T> = PropertyDef(name, T::class)
