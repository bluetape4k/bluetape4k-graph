package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.support.requireSafeIdentifier
import io.bluetape4k.support.requireNotBlank

/**
 * 배치 insert API가 모든 백엔드에서 공유하는 입력 검증 규칙.
 *
 * 백엔드 구현체는 쿼리 문자열이나 Gremlin traversal을 만들기 전에 이 검증을 먼저 호출해야 한다.
 */
object GraphBatchValidation {

    /**
     * 정점 배치 입력을 검증한다.
     *
     * @return 검증된 입력 그대로의 [propertiesList].
     */
    fun validateVertexBatch(
        label: String,
        propertiesList: List<Map<String, Any?>>,
    ): List<Map<String, Any?>> {
        validateLabel(label)
        propertiesList.forEach { properties -> validatePropertyKeys(properties) }
        return propertiesList
    }

    /**
     * 간선 배치 입력을 검증한다.
     *
     * @return 검증된 입력 그대로의 [edges].
     */
    fun validateEdgeBatch(
        label: String,
        edges: List<BatchEdge>,
    ): List<BatchEdge> {
        validateLabel(label)
        edges.forEach { edge ->
            edge.fromId.value.requireNotBlank("fromId.value")
            edge.toId.value.requireNotBlank("toId.value")
            validatePropertyKeys(edge.properties)
        }
        return edges
    }

    private fun validateLabel(label: String) {
        label.requireNotBlank("label").requireSafeIdentifier("label")
    }

    private fun validatePropertyKeys(properties: Map<String, Any?>) {
        properties.keys.forEach { key ->
            key.requireNotBlank("property key").requireSafeIdentifier("property key")
        }
    }
}
