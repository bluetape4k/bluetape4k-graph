package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.graph.support.requireSafeIdentifier
import io.bluetape4k.support.requireNotBlank

/**
 * Shared input validation rules for backend batch insert APIs.
 *
 * Backend implementations should call this validator before building query strings
 * or Gremlin traversals.
 */
object GraphBatchValidation {

    /**
     * Validates vertex batch input.
     *
     * @return the validated [propertiesList] unchanged.
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
     * Validates edge batch input.
     *
     * @return the validated [edges] unchanged.
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
