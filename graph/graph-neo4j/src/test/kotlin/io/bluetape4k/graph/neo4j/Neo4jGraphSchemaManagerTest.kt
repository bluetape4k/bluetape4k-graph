package io.bluetape4k.graph.neo4j

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.graph.model.GraphConstraintType
import io.bluetape4k.graph.model.GraphIndex
import io.bluetape4k.graph.schema.GraphSchemaDefinition
import io.bluetape4k.graph.schema.GraphSchemaPlanOptions
import io.bluetape4k.graph.schema.plan
import io.bluetape4k.graph.schema.schemaManager
import io.bluetape4k.testcontainers.graphdb.Neo4jServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import java.util.UUID

class Neo4jGraphSchemaManagerTest {

    private lateinit var driver: Driver
    private lateinit var ops: Neo4jGraphOperations

    @BeforeAll
    fun setup() {
        val server = Neo4jServer.Launcher.neo4j
        driver = GraphDatabase.driver(server.boltUrl, AuthTokens.none())
        ops = Neo4jGraphOperations(driver)
    }

    @AfterAll
    fun teardown() {
        driver.close()
    }

    @Test
    fun `creates lists and drops property index`() {
        val label = uniqueLabel()
        val manager = ops.schemaManager()

        manager.createIndex(label, "email")

        manager.listIndexes().any { it.label == label && it.property == "email" }.shouldBeTrue()

        manager.dropIndex(label, "email")
        manager.listIndexes().none { it.label == label && it.property == "email" }.shouldBeTrue()
    }

    @Test
    fun `creates and lists unique constraint`() {
        val label = uniqueLabel()
        val manager = ops.schemaManager()

        manager.createUniqueConstraint(label, "email")

        manager.listConstraints().any {
            it.label == label && it.property == "email" && it.type == GraphConstraintType.UNIQUE
        }.shouldBeTrue()
    }

    @Test
    fun `plans and applies missing index without destructive extras`() {
        val label = uniqueLabel()
        val manager = ops.schemaManager()
        val desired = GraphSchemaDefinition(
            indexes = setOf(GraphIndex("ignored", label, "email")),
        )

        val dryRun = manager.plan(desired)
        dryRun.items.filter { it.index?.label == label }.single().action.name shouldBeEqualTo "CREATE_INDEX"
        dryRun.apply(manager).applied shouldBeEqualTo emptyList()

        val applied = manager.plan(desired, GraphSchemaPlanOptions(dryRun = false)).apply(manager)
        applied.isSuccessful.shouldBeTrue()
        manager.listIndexes().any { it.label == label && it.property == "email" }.shouldBeTrue()

        manager.dropIndex(label, "email")
    }

    @Test
    fun `rejects unsafe identifiers before building DDL`() {
        assertFailsWith<IllegalArgumentException> {
            ops.schemaManager().createIndex("Person) MATCH (n", "email")
        }
    }

    private fun uniqueLabel(): String =
        "SchemaPerson${UUID.randomUUID().toString().replace("-", "").take(10)}"
}
