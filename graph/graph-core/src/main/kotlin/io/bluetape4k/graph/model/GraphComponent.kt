package io.bluetape4k.graph.model

import java.io.Serializable

/**
 * 연결 컴포넌트(Connected Component) 결과.
 *
 * 동일 [componentId] 를 갖는 정점 집합을 표현한다.
 *
 * @property componentId 컴포넌트 식별자 (구현체별 임의 값, 동일 컴포넌트는 동일 ID).
 * @property vertices 컴포넌트에 속한 정점 목록.
 *
 * ### 사용 예제
 * ```kotlin
 * val components = ops.connectedComponents(ComponentOptions(weakly = true))
 * components.forEach { println("${it.componentId}: size=${it.size}") }
 * ```
 */
data class GraphComponent(
    val componentId: String,
    val vertices: List<GraphVertex>,
): Serializable {
    /** 컴포넌트 내 정점 수. */
    val size: Int get() = vertices.size

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
