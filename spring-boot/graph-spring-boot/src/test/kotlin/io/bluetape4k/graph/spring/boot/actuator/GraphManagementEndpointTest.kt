package io.bluetape4k.graph.spring.boot.actuator

import io.bluetape4k.graph.spring.boot.properties.GraphProperties
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider

class GraphManagementEndpointTest {
    @Test
    fun `snapshot exposes sanitized diagnostic metadata only`() {
        val provider = mockk<ObjectProvider<GraphOperations>>()
        io.mockk.every { provider.ifAvailable } returns null

        val snapshot = GraphManagementEndpoint(
            GraphProperties(backend = " Neo4j "),
            provider,
        ).graph()

        snapshot.backend shouldBeEqualTo "neo4j"
        snapshot.graph shouldBeEqualTo "default"
        snapshot.database shouldBeEqualTo "default"
        snapshot.driverAvailable.shouldBeFalse()
        snapshot.sessionAvailable.shouldBeFalse()
        snapshot.capabilities["graphIo"] shouldBeEqualTo true
    }
}
