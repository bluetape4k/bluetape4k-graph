package io.bluetape4k.graph.examples.knowledge

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotBeEmpty
import io.bluetape4k.graph.examples.knowledge.io.KnowledgeGraphSampleDatasetLoader
import io.bluetape4k.graph.examples.knowledge.schema.DocumentLabel
import io.bluetape4k.graph.examples.knowledge.schema.EntityLabel
import io.bluetape4k.graph.examples.knowledge.service.KnowledgeGraphService
import io.bluetape4k.graph.examples.knowledge.service.KnowledgeGraphSuspendService
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class KnowledgeGraphSampleDatasetLoaderTest {

    @Test
    fun `imports graph-io CSV sample dataset into TinkerGraph`() {
        val ops = TinkerGraphOperations()
        val service = KnowledgeGraphService(ops)
        service.initialize()

        val report = KnowledgeGraphSampleDatasetLoader.importCsv(ops)
        val document = ops.findVerticesByLabel(
            DocumentLabel.label,
            mapOf(DocumentLabel.documentId.name to "doc-graph"),
        ).single()
        val kotlin = ops.findVerticesByLabel(
            EntityLabel.label,
            mapOf(EntityLabel.entityId.name to "entity-kotlin"),
        ).single()
        val falkorDb = ops.findVerticesByLabel(
            EntityLabel.label,
            mapOf(EntityLabel.entityId.name to "entity-falkordb"),
        ).single()

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 5L
        report.edgesCreated shouldBeEqualTo 5L
        service.findMentionedEntities(document.id).map { it.properties[EntityLabel.entityId.name] } shouldContain "entity-kotlin"
        service.findRelatedEntities(kotlin.id, depth = 2).map { it.properties[EntityLabel.entityId.name] } shouldContain "entity-falkordb"
        service.inferRelationshipPaths(kotlin.id, falkorDb.id, maxDepth = 3).shouldNotBeEmpty()
    }

    @Test
    fun `imports graph-io CSV sample dataset into suspend TinkerGraph`() = runTest {
        val ops = TinkerGraphSuspendOperations()
        val service = KnowledgeGraphSuspendService(ops)
        service.initialize()

        val report = KnowledgeGraphSampleDatasetLoader.importCsvSuspending(ops)
        val document = ops.findVerticesByLabel(
            DocumentLabel.label,
            mapOf(DocumentLabel.documentId.name to "doc-graph"),
        ).toList().single()
        val kotlin = ops.findVerticesByLabel(
            EntityLabel.label,
            mapOf(EntityLabel.entityId.name to "entity-kotlin"),
        ).toList().single()
        val falkorDb = ops.findVerticesByLabel(
            EntityLabel.label,
            mapOf(EntityLabel.entityId.name to "entity-falkordb"),
        ).toList().single()

        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesCreated shouldBeEqualTo 5L
        report.edgesCreated shouldBeEqualTo 5L
        service.findMentionedEntities(document.id).toList().map { it.properties[EntityLabel.entityId.name] } shouldContain "entity-kotlin"
        service.findRelatedEntities(kotlin.id, depth = 2).toList().map { it.properties[EntityLabel.entityId.name] } shouldContain "entity-falkordb"
        service.inferRelationshipPaths(kotlin.id, falkorDb.id, maxDepth = 3).toList().shouldNotBeEmpty()
    }

    @Test
    fun `imports default resources when context class loader cannot see examples`() {
        withContextClassLoader(object : ClassLoader(null) {}) {
            val ops = TinkerGraphOperations()
            KnowledgeGraphService(ops).initialize()

            val report = KnowledgeGraphSampleDatasetLoader.importCsv(ops)

            report.status shouldBeEqualTo GraphIoStatus.COMPLETED
            report.verticesCreated shouldBeEqualTo 5L
            report.edgesCreated shouldBeEqualTo 5L
        }
    }

    @Test
    fun `throws IllegalArgumentException when sample resource is missing`() {
        assertFailsWith<IllegalArgumentException> {
            KnowledgeGraphSampleDatasetLoader.importCsv(
                TinkerGraphOperations(),
                verticesResource = "sample-data/knowledge/missing-vertices.csv",
            )
        }
    }

    private fun <T> withContextClassLoader(classLoader: ClassLoader, block: () -> T): T {
        val thread = Thread.currentThread()
        val previous = thread.contextClassLoader
        thread.contextClassLoader = classLoader

        return try {
            block()
        } finally {
            thread.contextClassLoader = previous
        }
    }
}
