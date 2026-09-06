package io.bluetape4k.graph.io.graphml.internal

import io.bluetape4k.graph.io.graphml.GraphMlAttrType
import io.bluetape4k.graph.io.model.GraphIoEdgeRecord
import io.bluetape4k.graph.io.model.GraphIoVertexRecord

/** GraphML 헤더에 기록할 property 타입을 입력 레코드에서 결정한다. */
internal class GraphMlPropertyTypes {

    private val vertexTypes = linkedMapOf<String, ObservedPropertyType?>()
    private val edgeTypes = linkedMapOf<String, ObservedPropertyType?>()

    val vertices: Map<String, GraphMlAttrType>
        get() = vertexTypes.mapValues { (_, type) -> type?.graphMlType ?: GraphMlAttrType.STRING }

    val edges: Map<String, GraphMlAttrType>
        get() = edgeTypes.mapValues { (_, type) -> type?.graphMlType ?: GraphMlAttrType.STRING }

    fun observeVertices(records: Iterable<GraphIoVertexRecord>) {
        records.forEach { observe("node", it.properties, vertexTypes) }
    }

    fun observeEdges(records: Iterable<GraphIoEdgeRecord>) {
        records.forEach { observe("edge", it.properties, edgeTypes) }
    }

    private fun observe(
        element: String,
        properties: Map<String, Any?>,
        observedTypes: MutableMap<String, ObservedPropertyType?>,
    ) {
        properties.forEach { (name, value) ->
            if (!observedTypes.containsKey(name)) {
                observedTypes[name] = value?.let(::propertyType)
                return@forEach
            }
            if (value == null) return@forEach

            val current = observedTypes[name]
            val next = propertyType(value)
            if (current == null) {
                observedTypes[name] = next
            } else {
                require(current.runtimeType == next.runtimeType) {
                    "GraphML $element property '$name' mixes ${current.displayName} and ${next.displayName} values"
                }
            }
        }
    }
}

private data class ObservedPropertyType(
    val runtimeType: String,
    val displayName: String,
    val graphMlType: GraphMlAttrType,
)

private fun propertyType(value: Any): ObservedPropertyType {
    val graphMlType = when (value) {
        is Int -> GraphMlAttrType.INT
        is Long -> GraphMlAttrType.LONG
        is Float -> GraphMlAttrType.FLOAT
        is Double -> GraphMlAttrType.DOUBLE
        is Boolean -> GraphMlAttrType.BOOLEAN
        is String -> GraphMlAttrType.STRING
        else -> GraphMlAttrType.STRING
    }
    val type = value::class
    return ObservedPropertyType(
        runtimeType = type.qualifiedName ?: type.java.name,
        displayName = type.simpleName ?: type.java.simpleName,
        graphMlType = graphMlType,
    )
}
