package io.bluetape4k.graph.spring.boot.actuator

import io.bluetape4k.graph.spring.boot.properties.GraphProperties
import io.bluetape4k.graph.spring.boot.properties.AgeGraphProperties
import io.bluetape4k.graph.spring.boot.properties.Neo4jGraphProperties
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider

class GraphManagementEndpointTest {
    @Test
    fun `snapshot exposes configured graph and operation capabilities`() {
        val operations = TinkerGraphOperations()
        val provider = mockk<ObjectProvider<GraphOperations>>()
        io.mockk.every { provider.ifAvailable } returns operations

        try {
            val snapshot = GraphManagementEndpoint.configured(
                graphProperties = GraphProperties(backend = " AGE "),
                operations = provider,
                neo4jProperties = null,
                memgraphProperties = null,
                ageProperties = provider(AgeGraphProperties(graphName = "tenant_graph")),
                falkordbProperties = null,
                applicationContext = null,
                classLoader = GraphManagementEndpoint::class.java.classLoader,
            ).graph()

            snapshot.backend shouldBeEqualTo "age"
            snapshot.graph shouldBeEqualTo "tenant_graph"
            snapshot.database shouldBeEqualTo "default"
            snapshot.driverAvailable.shouldBeTrue()
            snapshot.sessionAvailable.shouldBeTrue()
            snapshot.capabilities["schema"].shouldBeTrue()
            snapshot.capabilities["graphIo"].shouldBeTrue()
        } finally {
            operations.close()
        }
    }

    @Test
    fun `snapshot reports unavailable operations without inventing schema support`() {
        val provider = mockk<ObjectProvider<GraphOperations>>()
        io.mockk.every { provider.ifAvailable } returns null

        val snapshot = GraphManagementEndpoint(
            graphProperties = GraphProperties(backend = " Neo4j "),
            operations = provider,
        ).graph()

        snapshot.backend shouldBeEqualTo "neo4j"
        snapshot.graph shouldBeEqualTo "default"
        snapshot.database shouldBeEqualTo "neo4j"
        snapshot.driverAvailable.shouldBeFalse()
        snapshot.sessionAvailable.shouldBeFalse()
        snapshot.capabilities["schema"].shouldBeFalse()
        snapshot.capabilities["graphIo"].shouldBeFalse()
    }

    @Test
    fun `snapshot uses configured Neo4j database`() {
        val provider = mockk<ObjectProvider<GraphOperations>>()
        io.mockk.every { provider.ifAvailable } returns null

        val snapshot = GraphManagementEndpoint.configured(
            graphProperties = GraphProperties(backend = "neo4j"),
            operations = provider,
            neo4jProperties = provider(Neo4jGraphProperties(database = "analytics")),
            memgraphProperties = null,
            ageProperties = null,
            falkordbProperties = null,
            applicationContext = null,
            classLoader = GraphManagementEndpoint::class.java.classLoader,
        ).graph()

        snapshot.graph shouldBeEqualTo "default"
        snapshot.database shouldBeEqualTo "analytics"
        snapshot.sessionAvailable.shouldBeFalse()
    }

    private inline fun <reified T : Any> provider(value: T): ObjectProvider<T> =
        mockk<ObjectProvider<T>>().also { provider ->
            io.mockk.every { provider.ifAvailable } returns value
        }
}
