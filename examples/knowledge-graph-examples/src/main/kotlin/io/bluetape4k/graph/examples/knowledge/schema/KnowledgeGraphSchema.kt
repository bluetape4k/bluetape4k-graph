package io.bluetape4k.graph.examples.knowledge.schema

import io.bluetape4k.graph.schema.EdgeLabel
import io.bluetape4k.graph.schema.VertexLabel

/**
 * 사람, 조직, 장소, 제품 같은 entity vertex이다.
 */
object EntityLabel : VertexLabel("Entity") {
    val entityId = string("entityId")
    val name = string("name")
    val entityType = string("entityType")
}

/**
 * Domain vocabulary를 normalize하는 데 사용하는 concept vertex이다.
 */
object ConceptLabel : VertexLabel("Concept") {
    val conceptId = string("conceptId")
    val name = string("name")
    val domain = string("domain")
}

/**
 * Entity를 mention하는 document vertex이다.
 */
object DocumentLabel : VertexLabel("Document") {
    val documentId = string("documentId")
    val title = string("title")
    val source = string("source")
}

/**
 * Document에서 entity로 이어지는 mention edge이다.
 */
object MentionsLabel : EdgeLabel("MENTIONS", DocumentLabel, EntityLabel) {
    val confidence = integer("confidence")
}

/**
 * Entity 사이의 related edge이다.
 */
object RelatedToLabel : EdgeLabel("RELATED_TO", EntityLabel, EntityLabel) {
    val relationType = string("relationType")
}

/**
 * Entity에서 concept으로 이어지는 classification edge이다.
 */
object IsALabel : EdgeLabel("IS_A", EntityLabel, ConceptLabel)
