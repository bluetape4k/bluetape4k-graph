package io.bluetape4k.graph.falkordb

import com.falkordb.Record
import com.falkordb.graph_entities.Edge
import com.falkordb.graph_entities.Node
import com.falkordb.graph_entities.Path
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.PathStep
import io.bluetape4k.logging.KLogging

/**
 * FalkorDB [Record]를 Graph 모델로 변환합니다.
 *
 * jfalkordb 0.7.0의 [Node], [Edge], [Path] API를 사용하여 그래프 모델로 변환합니다.
 * FalkorDB는 Redis 기반 그래프 DB이므로 Neo4j Driver가 아닌 jfalkordb 전용 API를 사용합니다.
 *
 * ```kotlin
 * val vertex: GraphVertex = FalkorDBRecordMapper.recordToVertex(record)      // key="n"
 * val edge: GraphEdge     = FalkorDBRecordMapper.recordToEdge(record, "r")
 * val path: GraphPath     = FalkorDBRecordMapper.recordToPath(record, "p")
 * ```
 */
object FalkorDBRecordMapper : KLogging() {

    /**
     * FalkorDB [Node]를 [GraphVertex]로 변환합니다.
     *
     * FalkorDB 노드의 첫 번째 레이블을 라벨로 사용하며, 레이블이 없으면 "Unknown"을 사용합니다.
     * 노드 ID는 `node.getId().toString()`으로 추출합니다.
     *
     * ```kotlin
     * val vertex = FalkorDBRecordMapper.nodeToVertex(node)
     * println(vertex.id.value)  // 정수 ID의 문자열 표현
     * ```
     *
     * @param node FalkorDB [Node] 객체
     * @return 변환된 [GraphVertex]
     */
    fun nodeToVertex(node: Node): GraphVertex {
        val id = GraphElementId(node.id.toString())
        val label = if (node.numberOfLabels > 0) node.getLabel(0) else "Unknown"
        val properties = node.entityPropertyNames.associateWith { name ->
            node.getProperty(name)?.value
        }
        return GraphVertex(id, label, properties)
    }

    /**
     * FalkorDB [Edge]를 [GraphEdge]로 변환합니다.
     *
     * ```kotlin
     * val edge = FalkorDBRecordMapper.edgeToGraphEdge(edge)
     * ```
     *
     * @param edge FalkorDB [Edge] 객체
     * @return 변환된 [GraphEdge]
     */
    fun edgeToGraphEdge(edge: Edge): GraphEdge {
        val id = GraphElementId(edge.id.toString())
        val startId = GraphElementId(edge.source.toString())
        val endId = GraphElementId(edge.destination.toString())
        val properties = edge.entityPropertyNames.associateWith { name ->
            edge.getProperty(name)?.value
        }
        return GraphEdge(id, edge.relationshipType, startId, endId, properties)
    }

    /**
     * FalkorDB [Path]를 [GraphPath]로 변환합니다.
     *
     * 경로는 노드와 엣지가 교대로 구성된 [PathStep] 리스트로 변환됩니다.
     *
     * ```kotlin
     * val path = FalkorDBRecordMapper.pathToGraphPath(path)
     * ```
     *
     * @param path FalkorDB [Path] 객체
     * @return 변환된 [GraphPath]
     */
    fun pathToGraphPath(path: Path): GraphPath {
        val steps = mutableListOf<PathStep>()
        val nodes = path.nodes
        val edges = path.edges

        nodes.forEachIndexed { index, node ->
            steps.add(PathStep.VertexStep(nodeToVertex(node)))
            if (index < edges.size) {
                steps.add(PathStep.EdgeStep(edgeToGraphEdge(edges[index])))
            }
        }
        return GraphPath(steps)
    }

    /**
     * [Record]에서 [GraphVertex]를 추출합니다.
     *
     * ```kotlin
     * val vertex = FalkorDBRecordMapper.recordToVertex(record)         // key="n"
     * val vertex2 = FalkorDBRecordMapper.recordToVertex(record, "neighbor")
     * ```
     *
     * @param record FalkorDB 쿼리 결과 레코드
     * @param key 레코드에서 노드를 추출할 키 (기본: "n")
     * @return 변환된 [GraphVertex]
     */
    fun recordToVertex(record: Record, key: String = "n"): GraphVertex =
        nodeToVertex(record.getValue(key))

    /**
     * [Record]에서 [GraphEdge]를 추출합니다.
     *
     * ```kotlin
     * val edge = FalkorDBRecordMapper.recordToEdge(record)        // key="r"
     * ```
     *
     * @param record FalkorDB 쿼리 결과 레코드
     * @param key 레코드에서 엣지를 추출할 키 (기본: "r")
     * @return 변환된 [GraphEdge]
     */
    fun recordToEdge(record: Record, key: String = "r"): GraphEdge =
        edgeToGraphEdge(record.getValue(key))

    /**
     * [Record]에서 [GraphPath]를 추출합니다.
     *
     * ```kotlin
     * val path = FalkorDBRecordMapper.recordToPath(record)        // key="p"
     * ```
     *
     * @param record FalkorDB 쿼리 결과 레코드
     * @param key 레코드에서 경로를 추출할 키 (기본: "p")
     * @return 변환된 [GraphPath]
     */
    fun recordToPath(record: Record, key: String = "p"): GraphPath =
        pathToGraphPath(record.getValue(key))
}
