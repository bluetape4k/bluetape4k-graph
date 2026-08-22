package io.bluetape4k.graph.spring.boot.autoconfigure

import com.falkordb.Driver
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.graph.falkordb.FalkorDBServer
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.UUID

@SpringBootTest(
    classes = [FalkorDBSpringBootIntegrationTest.TestApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@EnabledIfEnvironmentVariable(named = "BLUETAPE4K_GRAPH_SPRING_FALKORDB_INTEGRATION", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FalkorDBSpringBootIntegrationTest {

    companion object {
        private val server = FalkorDBServer.Launcher.falkordb
        private val graphName = "spring_boot_${UUID.randomUUID().toString().replace("-", "").take(12)}"

        @JvmStatic
        @DynamicPropertySource
        fun graphProperties(registry: DynamicPropertyRegistry) {
            server.registerGraphDynamicProperties(
                registry,
                graphBackendDynamicPropertyMappings.getValue("falkordb"),
            )
            registry.add("bluetape4k.graph.backend") { "falkordb" }
            registry.add("bluetape4k.graph.falkordb.graph-name") { graphName }
        }
    }

    @Autowired
    private lateinit var driver: Driver

    @Autowired
    private lateinit var graphOperations: GraphOperations

    @Autowired
    private lateinit var graphSuspendOperations: GraphSuspendOperations

    @Autowired
    private lateinit var graphVirtualThreadOperations: GraphVirtualThreadOperations

    @Autowired
    @Qualifier("falkordbHealthIndicator")
    private lateinit var healthIndicator: HealthIndicator

    @AfterAll
    fun tearDown() {
        runCatching { driver.graph(graphName).use { it.deleteGraph() } }
    }

    @Test
    fun `Spring Boot context wires FalkorDB auto-configuration against live container`() {
        graphOperations.shouldNotBeNull()
        graphSuspendOperations.shouldNotBeNull()
        graphVirtualThreadOperations.shouldNotBeNull()

        val vertex = graphOperations.createVertex("Issue126", mapOf("source" to "spring-boot"))

        vertex.label shouldBeEqualTo "Issue126"
        vertex.properties["source"] shouldBeEqualTo "spring-boot"
        healthIndicator.health().shouldNotBeNull().status.code shouldBeEqualTo "UP"
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(
        excludeName = [
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
        ]
    )
    @Import(GraphAutoConfiguration::class)
    class TestApp
}
