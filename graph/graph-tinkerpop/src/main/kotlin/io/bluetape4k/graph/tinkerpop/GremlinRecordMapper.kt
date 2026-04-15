package io.bluetape4k.graph.tinkerpop

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.PathStep
import io.bluetape4k.logging.KLogging
import org.apache.tinkerpop.gremlin.structure.Edge
import org.apache.tinkerpop.gremlin.structure.Vertex
import org.apache.tinkerpop.gremlin.process.traversal.Path as GremlinPath

/**
 * TinkerPop Gremlin 구조체를 Graph 모델로 변환합니다.
 *
 * ```kotlin
 * val g = TinkerGraph.open().traversal()
 * val v = g.addV("Person").property("name", "Alice").next()
 *
 * val vertex: GraphVertex = GremlinRecordMapper.vertexToGraphVertex(v)
 * println(vertex.label)              // "Person"
 * println(vertex.properties["name"]) // "Alice"
 * ```
 */
object GremlinRecordMapper : KLogging() {

    /**
     * Gremlin [Vertex]를 [GraphVertex]로 변환합니다.
     *
     * 정점 ID는 `v.id().toString()`으로 변환하고, 모든 속성을 Map으로 수집합니다.
     *
     * @param v TinkerPop Gremlin Vertex 객체.
     * @return 변환된 [GraphVertex].
     */
    fun vertexToGraphVertex(v: Vertex): GraphVertex {
        val id = GraphElementId(v.id().toString())
        val label = v.label()
        val properties = mutableMapOf<String, Any?>()
        v.properties<Any>().forEachRemaining { vp ->
            properties[vp.key()] = vp.value()
        }
        return GraphVertex(id, label, properties)
    }

    /**
     * Gremlin [Edge]를 [GraphEdge]로 변환합니다.
     *
     * 간선의 outVertex(시작 정점)와 inVertex(종료 정점) ID를 [GraphEdge.startId], [GraphEdge.endId]에 매핑합니다.
     *
     * @param e TinkerPop Gremlin Edge 객체.
     * @return 변환된 [GraphEdge].
     */
    fun edgeToGraphEdge(e: Edge): GraphEdge {
        val id = GraphElementId(e.id().toString())
        val startId = GraphElementId(e.outVertex().id().toString())
        val endId = GraphElementId(e.inVertex().id().toString())
        val properties = mutableMapOf<String, Any?>()
        e.properties<Any>().forEachRemaining { ep ->
            properties[ep.key()] = ep.value()
        }
        return GraphEdge(id, e.label(), startId, endId, properties)
    }

    /**
     * Gremlin [GremlinPath]를 [GraphPath]로 변환합니다.
     *
     * 경로 객체의 각 요소를 순서대로 [PathStep.VertexStep] 또는 [PathStep.EdgeStep]으로 변환합니다.
     *
     * @param p TinkerPop Gremlin Path 객체.
     * @return 변환된 [GraphPath].
     */
    fun pathToGraphPath(p: GremlinPath): GraphPath {
        val steps = mutableListOf<PathStep>()
        p.objects().forEach { obj ->
            when (obj) {
                is Vertex -> steps.add(PathStep.VertexStep(vertexToGraphVertex(obj)))
                is Edge -> steps.add(PathStep.EdgeStep(edgeToGraphEdge(obj)))
            }
        }
        return GraphPath(steps)
    }
}
