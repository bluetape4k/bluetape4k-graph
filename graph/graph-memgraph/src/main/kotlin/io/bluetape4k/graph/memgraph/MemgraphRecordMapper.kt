package io.bluetape4k.graph.memgraph

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.PathStep
import io.bluetape4k.logging.KLogging
import org.neo4j.driver.Record
import org.neo4j.driver.types.Node
import org.neo4j.driver.types.Path
import org.neo4j.driver.types.Relationship

/**
 * Neo4j Driver [Record]를 Graph 모델로 변환합니다 (Memgraph 용).
 *
 * Memgraph는 `elementId()` 대신 정수형 `id()`를 사용한다.
 * [GraphElementId.value]에는 정수 ID의 문자열 표현을 저장한다.
 *
 * ```kotlin
 * val vertex: GraphVertex = MemgraphRecordMapper.recordToVertex(record)      // key="n"
 * val edge: GraphEdge     = MemgraphRecordMapper.recordToEdge(record, key="r")
 * val path: GraphPath     = MemgraphRecordMapper.recordToPath(record, key="p")
 * ```
 */
@Suppress("DEPRECATION")
object MemgraphRecordMapper : KLogging() {

    /**
     * Memgraph [Node]를 [GraphVertex]로 변환합니다.
     *
     * Memgraph는 `elementId()` 대신 정수형 `id()`를 사용하므로 `node.id().toString()`으로 ID를 추출합니다.
     *
     * ```kotlin
     * val vertex = MemgraphRecordMapper.nodeToVertex(node)
     * println(vertex.id.value)  // 정수 ID의 문자열 표현
     * ```
     *
     * @param node Neo4j Driver Node 객체 (Memgraph에서 반환).
     * @return 변환된 [GraphVertex].
     */
    fun nodeToVertex(node: Node): GraphVertex {
        val id = GraphElementId(node.id().toString())
        val label = node.labels().firstOrNull() ?: "Unknown"
        val properties = node.asMap()
        return GraphVertex(id, label, properties)
    }

    /**
     * Memgraph [Relationship]을 [GraphEdge]로 변환합니다.
     *
     * ```kotlin
     * val edge = MemgraphRecordMapper.relationshipToEdge(rel)
     * ```
     *
     * @param rel Neo4j Driver Relationship 객체 (Memgraph에서 반환).
     * @return 변환된 [GraphEdge].
     */
    fun relationshipToEdge(rel: Relationship): GraphEdge {
        val id = GraphElementId(rel.id().toString())
        val startId = GraphElementId(rel.startNodeId().toString())
        val endId = GraphElementId(rel.endNodeId().toString())
        return GraphEdge(id, rel.type(), startId, endId, rel.asMap())
    }

    /**
     * Neo4j Driver [Path]를 [GraphPath]로 변환합니다.
     *
     * ```kotlin
     * val path = MemgraphRecordMapper.pathToGraphPath(path)
     * ```
     *
     * @param path Neo4j Driver Path 객체.
     * @return 변환된 [GraphPath].
     */
    fun pathToGraphPath(path: Path): GraphPath {
        val steps = mutableListOf<PathStep>()
        val nodes = path.nodes().toList()
        val rels = path.relationships().toList()

        nodes.forEachIndexed { index, node ->
            steps.add(PathStep.VertexStep(nodeToVertex(node)))
            if (index < rels.size) {
                steps.add(PathStep.EdgeStep(relationshipToEdge(rels[index])))
            }
        }
        return GraphPath(steps)
    }

    /**
     * [Record]에서 [GraphVertex]를 추출합니다.
     *
     * ```kotlin
     * val vertex = MemgraphRecordMapper.recordToVertex(record)         // key="n"
     * val vertex2 = MemgraphRecordMapper.recordToVertex(record, "neighbor")
     * ```
     *
     * @param record Cypher 쿼리 결과 레코드.
     * @param key 레코드에서 노드를 추출할 키 (기본: "n").
     * @return 변환된 [GraphVertex].
     */
    fun recordToVertex(record: Record, key: String = "n"): GraphVertex =
        nodeToVertex(record[key].asNode())

    /**
     * [Record]에서 [GraphEdge]를 추출합니다.
     *
     * ```kotlin
     * val edge = MemgraphRecordMapper.recordToEdge(record)        // key="r"
     * ```
     *
     * @param record Cypher 쿼리 결과 레코드.
     * @param key 레코드에서 관계를 추출할 키 (기본: "r").
     * @return 변환된 [GraphEdge].
     */
    fun recordToEdge(record: Record, key: String = "r"): GraphEdge =
        relationshipToEdge(record[key].asRelationship())

    /**
     * [Record]에서 [GraphPath]를 추출합니다.
     *
     * ```kotlin
     * val path = MemgraphRecordMapper.recordToPath(record)        // key="p"
     * ```
     *
     * @param record Cypher 쿼리 결과 레코드.
     * @param key 레코드에서 경로를 추출할 키 (기본: "p").
     * @return 변환된 [GraphPath].
     */
    fun recordToPath(record: Record, key: String = "p"): GraphPath =
        pathToGraphPath(record[key].asPath())
}
