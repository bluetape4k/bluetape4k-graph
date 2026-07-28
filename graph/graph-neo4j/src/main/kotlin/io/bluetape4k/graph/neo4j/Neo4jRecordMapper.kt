package io.bluetape4k.graph.neo4j

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.PathStep
import io.bluetape4k.logging.KLogging
import io.bluetape4k.support.requireNotBlank
import org.neo4j.driver.Record
import org.neo4j.driver.types.Node
import org.neo4j.driver.types.Path
import org.neo4j.driver.types.Relationship

/**
 * Neo4j Driver [Record] 값을 graph model 객체로 변환한다.
 *
 * ```kotlin
 * // Record 수준 helper
 * val vertex: GraphVertex = Neo4jRecordMapper.recordToVertex(record)          // key="n"
 * val edge: GraphEdge     = Neo4jRecordMapper.recordToEdge(record, key="r")
 * val path: GraphPath     = Neo4jRecordMapper.recordToPath(record, key="p")
 *
 * // 직접 Node/Relationship helper
 * val node: Node = record["n"].asNode()
 * val vertex = Neo4jRecordMapper.nodeToVertex(node)
 * ```
 */
object Neo4jRecordMapper: KLogging() {

    /**
     * Neo4j [Node]를 [GraphVertex]로 변환한다.
	*
     * 첫 번째 node label을 [GraphVertex.label]로 사용한다. label이 없는 node는 `"Unknown"`을 사용한다.
     *
     * ```kotlin
     * val node = record["n"].asNode()
     * val vertex = Neo4jRecordMapper.nodeToVertex(node)
     * println(vertex.label)  // 첫 번째 node label
     * ```
     *
     * @param node Neo4j Driver node.
     * @return 변환된 [GraphVertex].
     */
    fun nodeToVertex(node: Node): GraphVertex {
        val id = GraphElementId(node.elementId())
        val label = node.labels().firstOrNull() ?: "Unknown"
        val properties = node.asMap()
        return GraphVertex(id, label, properties)
    }

    /**
     * Neo4j [Relationship]을 [GraphEdge]로 변환한다.
     *
     * ```kotlin
     * val rel = record["r"].asRelationship()
     * val edge = Neo4jRecordMapper.relationshipToEdge(rel)
     * println(edge.label)  // relationship type
     * ```
     *
     * @param rel Neo4j Driver relationship.
     * @return 변환된 [GraphEdge].
     */
    fun relationshipToEdge(rel: Relationship): GraphEdge {
        val id = GraphElementId(rel.elementId())
        val startId = GraphElementId(rel.startNodeElementId())
        val endId = GraphElementId(rel.endNodeElementId())
        return GraphEdge(id, rel.type(), startId, endId, rel.asMap())
    }

    /**
     * Neo4j [Path]를 [GraphPath]로 변환한다.
	*
     * Node와 relationship을 [PathStep.VertexStep]과 [PathStep.EdgeStep]이 교차하는 값으로 변환한다.
     *
     * ```kotlin
     * val path = Neo4jRecordMapper.pathToGraphPath(record["p"].asPath())
     * println(path.length)  // path step 수
     * ```
     *
     * @param path Neo4j Driver path.
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
     * [Record]에서 [GraphVertex]를 추출한다.
     *
     * ```kotlin
     * val vertex = Neo4jRecordMapper.recordToVertex(record)        // key="n"
     * val vertex2 = Neo4jRecordMapper.recordToVertex(record, "node")
     * ```
     *
     * @param record Cypher query 결과 record.
     * @param key node를 추출할 key. 기본값은 `"n"`.
     * @return 변환된 [GraphVertex].
     */
    fun recordToVertex(record: Record, key: String = "n"): GraphVertex {
        key.requireNotBlank("key")
        return nodeToVertex(record[key].asNode())
    }


    /**
     * [Record]에서 [GraphEdge]를 추출한다.
     *
     * ```kotlin
     * val edge = Neo4jRecordMapper.recordToEdge(record)        // key="r"
     * val edge2 = Neo4jRecordMapper.recordToEdge(record, "rel")
     * ```
     *
     * @param record Cypher query 결과 record.
     * @param key relationship을 추출할 key. 기본값은 `"r"`.
     * @return 변환된 [GraphEdge].
     */
    fun recordToEdge(record: Record, key: String = "r"): GraphEdge {
        key.requireNotBlank("key")
        return relationshipToEdge(record[key].asRelationship())
    }

    /**
     * [Record]에서 [GraphPath]를 추출한다.
     *
     * ```kotlin
     * val path = Neo4jRecordMapper.recordToPath(record)        // key="p"
     * ```
     *
     * @param record Cypher query 결과 record.
     * @param key path를 추출할 key. 기본값은 `"p"`.
     * @return 변환된 [GraphPath].
     */
    fun recordToPath(record: Record, key: String = "p"): GraphPath {
        key.requireNotBlank("key")
        return pathToGraphPath(record[key].asPath())
    }
}
