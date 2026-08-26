package io.bluetape4k.graph.spring.boot.autoconfigure

import com.falkordb.Driver
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.graph.spring.boot.actuator.GraphManagementEndpoint
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class GraphManagementEndpointAutoConfigurationTest {

    private val runner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                GraphAutoConfiguration::class.java,
                GraphTinkerGraphAutoConfiguration::class.java,
                GraphManagementEndpointAutoConfiguration::class.java,
            )
        )

    private val falkordbRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                GraphAutoConfiguration::class.java,
                GraphFalkorDBAutoConfiguration::class.java,
                GraphManagementEndpointAutoConfiguration::class.java,
            )
        )

    @Test
    fun `endpoint is disabled unless explicitly enabled`() {
        runner.run { context ->
            context.containsBean("graphManagementEndpoint").shouldBeFalse()
        }
    }

    @Test
    fun `enabled endpoint reports tinkergraph bean and capabilities`() {
        runner
            .withPropertyValues("bluetape4k.graph.management.endpoint.enabled=true")
            .run { context ->
                val endpoint = context.getBean(GraphManagementEndpoint::class.java)
                val snapshot = endpoint.graph()

                snapshot.backend shouldBeEqualTo "tinkergraph"
                snapshot.graph shouldBeEqualTo "default"
                snapshot.database shouldBeEqualTo "default"
                snapshot.driverAvailable.shouldBeFalse()
                snapshot.sessionAvailable.shouldBeTrue()
                snapshot.capabilities["schema"].shouldBeTrue()
                snapshot.capabilities["graphIo"].shouldBeTrue()
            }
    }

    @Test
    fun `endpoint reports graphIo unavailable when graph-io contracts are absent`() {
        runner
            .withClassLoader(FilteredClassLoader("io.bluetape4k.graph.io.contract"))
            .withPropertyValues("bluetape4k.graph.management.endpoint.enabled=true")
            .run { context ->
                context.getBean(GraphManagementEndpoint::class.java)
                    .graph()
                    .capabilities["graphIo"]
                    .shouldBeFalse()
            }
    }

    @Test
    fun `endpoint binds backend properties even when backend auto configuration is absent`() {
        runner
            .withPropertyValues(
                "bluetape4k.graph.backend=neo4j",
                "bluetape4k.graph.neo4j.database=analytics",
                "bluetape4k.graph.management.endpoint.enabled=true",
            )
            .run { context ->
                val snapshot = context.getBean(GraphManagementEndpoint::class.java).graph()

                snapshot.backend shouldBeEqualTo "neo4j"
                snapshot.database shouldBeEqualTo "analytics"
                snapshot.sessionAvailable.shouldBeFalse()
                snapshot.driverAvailable.shouldBeFalse()
            }
    }

    @Test
    fun `endpoint detects a user named FalkorDB driver bean`() {
        falkordbRunner
            .withBean("customFalkorDriver", Driver::class.java, { mockk(relaxed = true) })
            .withPropertyValues(
                "bluetape4k.graph.backend=falkordb",
                "bluetape4k.graph.falkordb.graph-name=recommendations",
                "bluetape4k.graph.management.endpoint.enabled=true",
            )
            .run { context ->
                val snapshot = context.getBean(GraphManagementEndpoint::class.java).graph()

                snapshot.backend shouldBeEqualTo "falkordb"
                snapshot.graph shouldBeEqualTo "recommendations"
                snapshot.driverAvailable.shouldBeTrue()
                snapshot.sessionAvailable.shouldBeTrue()
            }
    }
}
