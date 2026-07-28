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
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.info
import io.bluetape4k.support.requireNotBlank

/**
 * [GraphOperations] 위에 구성한 knowledge graph service이다.
 *
 * 이 service는 entity, concept, document를 모델링한 뒤 document mention을 통한 entity lookup,
 * related-entity traversal, bounded relationship path inference를 보여준다.
 *
 * ```kotlin
 * val service = KnowledgeGraphService(ops)
 * service.initialize()
 * val paper = service.addDocument("doc-1", "Graph APIs")
 * val kotlin = service.addEntity("entity-kotlin", "Kotlin", "Language")
 * service.mention(paper.id, kotlin.id, confidence = 95)
 * ```
 */
class KnowledgeGraphService(
    private val ops: GraphOperations,
    private val graphName: String = "knowledge_graph",
) {
    companion object : KLogging()

    /**
     * Backing graph가 아직 없으면 생성한다.
     */
    fun initialize() {
        if (!ops.graphExists(graphName)) {
            ops.createGraph(graphName)
            log.info { "Knowledge graph '$graphName' created" }
        }
    }

    /**
     * Entity vertex를 추가한다.
     */
    fun addEntity(entityId: String, name: String, entityType: String): GraphVertex {
        entityId.requireNotBlank("entityId")
        name.requireNotBlank("name")
        entityType.requireNotBlank("entityType")
        return ops.createVertex(
            EntityLabel.label,
            mapOf(EntityLabel.entityId.name to entityId, EntityLabel.name.name to name, EntityLabel.entityType.name to entityType)
        )
    }

    /**
     * Concept vertex를 추가한다.
     */
    fun addConcept(conceptId: String, name: String, domain: String = ""): GraphVertex {
        conceptId.requireNotBlank("conceptId")
        name.requireNotBlank("name")
        return ops.createVertex(
            ConceptLabel.label,
            mapOf(ConceptLabel.conceptId.name to conceptId, ConceptLabel.name.name to name, ConceptLabel.domain.name to domain)
        )
    }

    /**
     * Document vertex를 추가한다.
     */
    fun addDocument(documentId: String, title: String, source: String = ""): GraphVertex {
        documentId.requireNotBlank("documentId")
        title.requireNotBlank("title")
        return ops.createVertex(
            DocumentLabel.label,
            mapOf(DocumentLabel.documentId.name to documentId, DocumentLabel.title.name to title, DocumentLabel.source.name to source)
        )
    }

    /**
     * Document를 그 안에서 mention한 entity에 연결한다.
     */
    fun mention(documentId: GraphElementId, entityId: GraphElementId, confidence: Int = 100) {
        require(confidence in 0..100) { "confidence must be in 0..100, was $confidence" }
        ops.createEdge(documentId, entityId, MentionsLabel.label, mapOf(MentionsLabel.confidence.name to confidence))
    }

    /**
     * 두 entity를 typed relationship으로 연결한다.
     */
    fun relateEntities(fromEntityId: GraphElementId, toEntityId: GraphElementId, relationType: String = "related") {
        relationType.requireNotBlank("relationType")
        ops.createEdge(
            fromEntityId,
            toEntityId,
            RelatedToLabel.label,
            mapOf(RelatedToLabel.relationType.name to relationType)
        )
    }

    /**
     * Entity를 concept 아래로 classify한다.
     */
    fun classify(entityId: GraphElementId, conceptId: GraphElementId) {
        ops.createEdge(entityId, conceptId, IsALabel.label, emptyMap())
    }

    /**
     * Document가 mention한 entity를 찾는다.
     */
    fun findMentionedEntities(documentId: GraphElementId): List<GraphVertex> =
        ops.neighbors(documentId, NeighborOptions(edgeLabel = MentionsLabel.label, direction = Direction.OUTGOING, maxDepth = 1))

    /**
     * Source entity와 related된 entity를 찾는다.
     */
    fun findRelatedEntities(entityId: GraphElementId, depth: Int = 1): List<GraphVertex> =
        ops.neighbors(entityId, NeighborOptions(edgeLabel = RelatedToLabel.label, direction = Direction.OUTGOING, maxDepth = depth))

    /**
     * 두 entity 사이의 relationship path를 찾고 service-side result bound를 적용한다.
     */
    fun inferRelationshipPaths(
        fromEntityId: GraphElementId,
        toEntityId: GraphElementId,
        maxDepth: Int = 3,
        maxPaths: Int = 10,
    ): List<GraphPath> {
        require(maxPaths > 0) { "maxPaths must be > 0, was $maxPaths" }
        return ops.allPaths(
            fromEntityId,
            toEntityId,
            PathOptions(edgeLabel = RelatedToLabel.label, maxDepth = maxDepth)
        ).take(maxPaths)
    }
}
