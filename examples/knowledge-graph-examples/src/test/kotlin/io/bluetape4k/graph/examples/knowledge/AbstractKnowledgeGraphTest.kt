package io.bluetape4k.graph.examples.knowledge

import io.bluetape4k.assertions.shouldBeGreaterOrEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotBeEmpty
import io.bluetape4k.graph.examples.knowledge.service.KnowledgeGraphService
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractKnowledgeGraphTest {

    companion object : KLogging()

    protected abstract val ops: GraphOperations
    protected open val graphName: String = "knowledge_graph_test"
    protected val service: KnowledgeGraphService by lazy { KnowledgeGraphService(ops, graphName) }

    @BeforeEach
    fun cleanGraph() {
        if (ops.graphExists(graphName)) {
            ops.dropGraph(graphName)
        }
        service.initialize()
    }

    @Test
    fun `finds entities mentioned by a document`() {
        val document = service.addDocument("doc-1", "Graph API Guide", "docs")
        val kotlin = service.addEntity("entity-kotlin", "Kotlin", "Language")
        val neo4j = service.addEntity("entity-neo4j", "Neo4j", "Database")

        service.mention(document.id, kotlin.id, confidence = 95)
        service.mention(document.id, neo4j.id, confidence = 90)

        val entityIds = service.findMentionedEntities(document.id).map { it.properties["entityId"] }
        entityIds shouldContain "entity-kotlin"
        entityIds shouldContain "entity-neo4j"
    }

    @Test
    fun `finds related entities`() {
        val kotlin = service.addEntity("entity-kotlin", "Kotlin", "Language")
        val coroutines = service.addEntity("entity-coroutines", "Coroutines", "Library")

        service.relateEntities(kotlin.id, coroutines.id, relationType = "has-feature")

        val related = service.findRelatedEntities(kotlin.id)
        related.shouldNotBeEmpty()
        related.map { it.properties["entityId"] } shouldContain "entity-coroutines"
    }

    @Test
    fun `infers bounded relationship paths`() {
        val kotlin = service.addEntity("entity-kotlin", "Kotlin", "Language")
        val coroutines = service.addEntity("entity-coroutines", "Coroutines", "Library")
        val spring = service.addEntity("entity-spring", "Spring", "Framework")

        service.relateEntities(kotlin.id, coroutines.id, relationType = "has-feature")
        service.relateEntities(coroutines.id, spring.id, relationType = "integrates-with")

        val paths = service.inferRelationshipPaths(kotlin.id, spring.id, maxDepth = 3, maxPaths = 2)
        paths.shouldNotBeEmpty()
        paths.size shouldBeGreaterOrEqualTo 1
        paths.first().vertices.map { it.properties["entityId"] } shouldContain "entity-kotlin"
        paths.first().vertices.map { it.properties["entityId"] } shouldContain "entity-spring"
    }

    @Test
    fun `classifies entities under concepts`() {
        val kotlin = service.addEntity("entity-kotlin", "Kotlin", "Language")
        val language = service.addConcept("concept-language", "Programming Language", "software")

        service.classify(kotlin.id, language.id)

        val concepts = ops.neighbors(
            kotlin.id,
            io.bluetape4k.graph.model.NeighborOptions(
                edgeLabel = "IS_A",
                direction = io.bluetape4k.graph.model.Direction.OUTGOING,
                maxDepth = 1,
            )
        )
        concepts.shouldNotBeEmpty()
        concepts.map { it.properties["conceptId"] } shouldContain "concept-language"
    }
}
