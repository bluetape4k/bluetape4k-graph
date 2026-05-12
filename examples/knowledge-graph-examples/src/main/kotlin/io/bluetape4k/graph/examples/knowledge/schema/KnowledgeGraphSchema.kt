package io.bluetape4k.graph.examples.knowledge.schema

import io.bluetape4k.graph.schema.EdgeLabel
import io.bluetape4k.graph.schema.VertexLabel

/**
 * Entity vertices such as people, organizations, places, or products.
 */
object EntityLabel : VertexLabel("Entity") {
    val entityId = string("entityId")
    val name = string("name")
    val entityType = string("entityType")
}

/**
 * Concept vertices used to normalize domain vocabulary.
 */
object ConceptLabel : VertexLabel("Concept") {
    val conceptId = string("conceptId")
    val name = string("name")
    val domain = string("domain")
}

/**
 * Document vertices that mention entities.
 */
object DocumentLabel : VertexLabel("Document") {
    val documentId = string("documentId")
    val title = string("title")
    val source = string("source")
}

/**
 * Document-to-entity mention edges.
 */
object MentionsLabel : EdgeLabel("MENTIONS", DocumentLabel, EntityLabel) {
    val confidence = integer("confidence")
}

/**
 * Entity-to-entity related edges.
 */
object RelatedToLabel : EdgeLabel("RELATED_TO", EntityLabel, EntityLabel) {
    val relationType = string("relationType")
}

/**
 * Entity-to-concept classification edges.
 */
object IsALabel : EdgeLabel("IS_A", EntityLabel, ConceptLabel)
