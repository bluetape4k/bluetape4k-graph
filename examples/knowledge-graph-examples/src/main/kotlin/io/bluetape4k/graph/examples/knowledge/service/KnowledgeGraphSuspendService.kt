package io.bluetape4k.graph.examples.knowledge.service

import io.bluetape4k.graph.examples.knowledge.schema.ConceptLabel
import io.bluetape4k.graph.examples.knowledge.schema.DocumentLabel
import io.bluetape4k.graph.examples.knowledge.schema.EntityLabel
import io.bluetape4k.graph.examples.knowledge.schema.IsALabel
import io.bluetape4k.graph.examples.knowledge.schema.MentionsLabel
import io.bluetape4k.graph.examples.knowledge.schema.RelatedToLabel
import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.info
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.take

/**
 * Coroutine and Flow version of [KnowledgeGraphService].
 */
class KnowledgeGraphSuspendService(
    private val ops: GraphSuspendOperations,
    private val graphName: String = "knowledge_graph",
) {
    companion object : KLoggingChannel()

    /**
     * Creates the backing graph when it does not already exist.
     */
    suspend fun initialize() {
        if (!ops.graphExists(graphName)) {
            ops.createGraph(graphName)
            log.info { "Knowledge graph '$graphName' created" }
        }
    }

    /**
     * Adds an entity vertex.
     */
    suspend fun addEntity(entityId: String, name: String, entityType: String): GraphVertex {
        entityId.requireNotBlank("entityId")
        name.requireNotBlank("name")
        entityType.requireNotBlank("entityType")
        return ops.createVertex(
            EntityLabel.label,
            mapOf(EntityLabel.entityId.name to entityId, EntityLabel.name.name to name, EntityLabel.entityType.name to entityType)
        )
    }

    /**
     * Adds a concept vertex.
     */
    suspend fun addConcept(conceptId: String, name: String, domain: String = ""): GraphVertex {
        conceptId.requireNotBlank("conceptId")
        name.requireNotBlank("name")
        return ops.createVertex(
            ConceptLabel.label,
            mapOf(ConceptLabel.conceptId.name to conceptId, ConceptLabel.name.name to name, ConceptLabel.domain.name to domain)
        )
    }

    /**
     * Adds a document vertex.
     */
    suspend fun addDocument(documentId: String, title: String, source: String = ""): GraphVertex {
        documentId.requireNotBlank("documentId")
        title.requireNotBlank("title")
        return ops.createVertex(
            DocumentLabel.label,
            mapOf(DocumentLabel.documentId.name to documentId, DocumentLabel.title.name to title, DocumentLabel.source.name to source)
        )
    }

    /**
     * Connects a document to an entity that it mentions.
     */
    suspend fun mention(documentId: GraphElementId, entityId: GraphElementId, confidence: Int = 100) {
        require(confidence in 0..100) { "confidence must be in 0..100, was $confidence" }
        ops.createEdge(documentId, entityId, MentionsLabel.label, mapOf(MentionsLabel.confidence.name to confidence))
    }

    /**
     * Connects two entities with a typed relationship.
     */
    suspend fun relateEntities(fromEntityId: GraphElementId, toEntityId: GraphElementId, relationType: String = "related") {
        relationType.requireNotBlank("relationType")
        ops.createEdge(
            fromEntityId,
            toEntityId,
            RelatedToLabel.label,
            mapOf(RelatedToLabel.relationType.name to relationType)
        )
    }

    /**
     * Classifies an entity under a concept.
     */
    suspend fun classify(entityId: GraphElementId, conceptId: GraphElementId) {
        ops.createEdge(entityId, conceptId, IsALabel.label, emptyMap())
    }

    /**
     * Finds entities mentioned by a document.
     */
    fun findMentionedEntities(documentId: GraphElementId): Flow<GraphVertex> =
        ops.neighbors(documentId, NeighborOptions(edgeLabel = MentionsLabel.label, direction = Direction.OUTGOING, maxDepth = 1))

    /**
     * Finds entities related to the source entity.
     */
    fun findRelatedEntities(entityId: GraphElementId, depth: Int = 1): Flow<GraphVertex> =
        ops.neighbors(entityId, NeighborOptions(edgeLabel = RelatedToLabel.label, direction = Direction.OUTGOING, maxDepth = depth))

    /**
     * Finds relationship paths between two entities and applies a service-side result bound.
     */
    fun inferRelationshipPaths(
        fromEntityId: GraphElementId,
        toEntityId: GraphElementId,
        maxDepth: Int = 3,
        maxPaths: Int = 10,
    ): Flow<GraphPath> {
        require(maxPaths > 0) { "maxPaths must be > 0, was $maxPaths" }
        return ops.allPaths(
            fromEntityId,
            toEntityId,
            PathOptions(edgeLabel = RelatedToLabel.label, maxDepth = maxDepth)
        ).take(maxPaths)
    }
}
