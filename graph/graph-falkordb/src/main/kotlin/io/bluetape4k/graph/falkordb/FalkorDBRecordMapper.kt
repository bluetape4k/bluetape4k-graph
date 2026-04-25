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

/**
 * FalkorDB [Node]를 [GraphVertex]로 변환합니다.
 *
 * FalkorDB 노드의 첫 번째 레이블을 라벨로 사용하며, 레이블이 없으면 "Unknown"을 사용합니다.
 * 노드 ID는 `node.getId().toString()`으로 추출합니다.
 *
 * ```kotlin
 * val vertex = node.toGraphVertex()
 * println(vertex.id.value)  // 정수 ID의 문자열 표현
 * ```
 */
internal fun Node.toGraphVertex(): GraphVertex {
    val graphId = GraphElementId(this.id.toString())
    val label = if (numberOfLabels > 0) getLabel(0) else "Unknown"
    val properties = entityPropertyNames.associateWith { name -> getProperty(name)?.value }
    return GraphVertex(graphId, label, properties)
}

/**
 * FalkorDB [Edge]를 [GraphEdge]로 변환합니다.
 *
 * ```kotlin
 * val edge = falkorEdge.toGraphEdge()
 * ```
 */
internal fun Edge.toGraphEdge(): GraphEdge {
    val graphId = GraphElementId(this.id.toString())
    val startId = GraphElementId(source.toString())
    val endId = GraphElementId(destination.toString())
    val properties = entityPropertyNames.associateWith { name -> getProperty(name)?.value }
    return GraphEdge(graphId, relationshipType, startId, endId, properties)
}

/**
 * FalkorDB [Path]를 [GraphPath]로 변환합니다.
 *
 * 경로는 노드와 엣지가 교대로 구성된 [PathStep] 리스트로 변환됩니다.
 *
 * ```kotlin
 * val path = falkorPath.toGraphPath()
 * ```
 */
internal fun Path.toGraphPath(): GraphPath {
    val steps = mutableListOf<PathStep>()
    val nodeList = nodes
    val edgeList = edges
    nodeList.forEachIndexed { index, node ->
        steps.add(PathStep.VertexStep(node.toGraphVertex()))
        if (index < edgeList.size) {
            steps.add(PathStep.EdgeStep(edgeList[index].toGraphEdge()))
        }
    }
    return GraphPath(steps)
}

/**
 * [Record]에서 [GraphVertex]를 추출합니다.
 *
 * ```kotlin
 * val vertex  = record.toVertex()              // key="n"
 * val vertex2 = record.toVertex("neighbor")
 * ```
 *
 * @param key 레코드에서 노드를 추출할 키 (기본: "n")
 */
internal fun Record.toVertex(key: String = "n"): GraphVertex =
    getValue<Node>(key).toGraphVertex()

/**
 * [Record]에서 [GraphEdge]를 추출합니다.
 *
 * ```kotlin
 * val edge = record.toEdge()       // key="r"
 * ```
 *
 * @param key 레코드에서 엣지를 추출할 키 (기본: "r")
 */
internal fun Record.toEdge(key: String = "r"): GraphEdge =
    getValue<Edge>(key).toGraphEdge()

/**
 * [Record]에서 [GraphPath]를 추출합니다.
 *
 * ```kotlin
 * val path = record.toPath()       // key="p"
 * ```
 *
 * @param key 레코드에서 경로를 추출할 키 (기본: "p")
 */
internal fun Record.toPath(key: String = "p"): GraphPath =
    getValue<Path>(key).toGraphPath()
