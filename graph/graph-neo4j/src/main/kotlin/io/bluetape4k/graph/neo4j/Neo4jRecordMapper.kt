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
 * Converts Neo4j Driver [Record] values to graph model objects.
 *
 * ```kotlin
 * // Record-level helpers
 * val vertex: GraphVertex = Neo4jRecordMapper.recordToVertex(record)          // key="n"
 * val edge: GraphEdge     = Neo4jRecordMapper.recordToEdge(record, key="r")
 * val path: GraphPath     = Neo4jRecordMapper.recordToPath(record, key="p")
 *
 * // Direct Node/Relationship helpers
 * val node: Node = record["n"].asNode()
 * val vertex = Neo4jRecordMapper.nodeToVertex(node)
 * ```
 */
object Neo4jRecordMapper: KLogging() {

    /**
     * Converts a Neo4j [Node] to a [GraphVertex].
	*
     * The first node label becomes [GraphVertex.label]; nodes without labels use `"Unknown"`.
     *
     * ```kotlin
     * val node = record["n"].asNode()
     * val vertex = Neo4jRecordMapper.nodeToVertex(node)
     * println(vertex.label)  // first node label
     * ```
     *
     * @param node Neo4j Driver node.
     * @return converted [GraphVertex].
     */
    fun nodeToVertex(node: Node): GraphVertex {
        val id = GraphElementId(node.elementId())
        val label = node.labels().firstOrNull() ?: "Unknown"
        val properties = node.asMap()
        return GraphVertex(id, label, properties)
    }

    /**
     * Converts a Neo4j [Relationship] to a [GraphEdge].
     *
     * ```kotlin
     * val rel = record["r"].asRelationship()
     * val edge = Neo4jRecordMapper.relationshipToEdge(rel)
     * println(edge.label)  // relationship type
     * ```
     *
     * @param rel Neo4j Driver relationship.
     * @return converted [GraphEdge].
     */
    fun relationshipToEdge(rel: Relationship): GraphEdge {
        val id = GraphElementId(rel.elementId())
        val startId = GraphElementId(rel.startNodeElementId())
        val endId = GraphElementId(rel.endNodeElementId())
        return GraphEdge(id, rel.type(), startId, endId, rel.asMap())
    }

    /**
     * Converts a Neo4j [Path] to a [GraphPath].
	*
     * Nodes and relationships are converted to alternating [PathStep.VertexStep] and [PathStep.EdgeStep] values.
     *
     * ```kotlin
     * val path = Neo4jRecordMapper.pathToGraphPath(record["p"].asPath())
     * println(path.length)  // number of path steps
     * ```
     *
     * @param path Neo4j Driver path.
     * @return converted [GraphPath].
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
     * Extracts a [GraphVertex] from a [Record].
     *
     * ```kotlin
     * val vertex = Neo4jRecordMapper.recordToVertex(record)        // key="n"
     * val vertex2 = Neo4jRecordMapper.recordToVertex(record, "node")
     * ```
     *
     * @param record Cypher query result record.
     * @param key key used to extract the node, defaulting to `"n"`.
     * @return converted [GraphVertex].
     */
    fun recordToVertex(record: Record, key: String = "n"): GraphVertex {
        key.requireNotBlank("key")
        return nodeToVertex(record[key].asNode())
    }


    /**
     * Extracts a [GraphEdge] from a [Record].
     *
     * ```kotlin
     * val edge = Neo4jRecordMapper.recordToEdge(record)        // key="r"
     * val edge2 = Neo4jRecordMapper.recordToEdge(record, "rel")
     * ```
     *
     * @param record Cypher query result record.
     * @param key key used to extract the relationship, defaulting to `"r"`.
     * @return converted [GraphEdge].
     */
    fun recordToEdge(record: Record, key: String = "r"): GraphEdge {
        key.requireNotBlank("key")
        return relationshipToEdge(record[key].asRelationship())
    }

    /**
     * Extracts a [GraphPath] from a [Record].
     *
     * ```kotlin
     * val path = Neo4jRecordMapper.recordToPath(record)        // key="p"
     * ```
     *
     * @param record Cypher query result record.
     * @param key key used to extract the path, defaulting to `"p"`.
     * @return converted [GraphPath].
     */
    fun recordToPath(record: Record, key: String = "p"): GraphPath {
        key.requireNotBlank("key")
        return pathToGraphPath(record[key].asPath())
    }
}
