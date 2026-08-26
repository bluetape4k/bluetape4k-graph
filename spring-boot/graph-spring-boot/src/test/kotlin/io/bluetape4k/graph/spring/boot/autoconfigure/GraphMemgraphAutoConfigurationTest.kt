package io.bluetape4k.graph.spring.boot.autoconfigure

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.testcontainers.graphdb.MemgraphServer
import io.bluetape4k.assertions.assertFailsWith
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.neo4j.driver.Driver
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GraphMemgraphAutoConfigurationTest {

    companion object : KLogging()

    private val runner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                GraphAutoConfiguration::class.java,
                GraphMemgraphAutoConfiguration::class.java,
            )
        )

    private val memgraphProperties
        get() = arrayOf(
            "bluetape4k.graph.backend=memgraph",
            "bluetape4k.graph.memgraph.uri=${MemgraphServer.Launcher.memgraph.boltUrl}",
            "bluetape4k.graph.memgraph.username=",
            "bluetape4k.graph.memgraph.password=",
        )

    private val localMemgraphProperties = arrayOf(
        "bluetape4k.graph.backend=memgraph",
        "bluetape4k.graph.memgraph.uri=bolt://localhost:7687",
        "bluetape4k.graph.memgraph.username=",
        "bluetape4k.graph.memgraph.password=",
    )

    @Test
    fun `backend=memgraph 이면 GraphOperations 빈 등록`() {
        runner.withPropertyValues(*memgraphProperties)
            .run { ctx ->
                ctx.getBean(GraphOperations::class.java).shouldNotBeNull()
                ctx.getBean(GraphSuspendOperations::class.java).shouldNotBeNull()
                ctx.getBean(GraphVirtualThreadOperations::class.java).shouldNotBeNull()
            }
    }

    @Test
    fun `unrelated Driver bean 이 있어도 memgraphDriver 를 별도로 등록한다`() {
        runner
            .withBean("neo4jDriver", Driver::class.java, { mockk(relaxed = true) })
            .withPropertyValues(*localMemgraphProperties)
            .run { ctx ->
                ctx.getBean("neo4jDriver", Driver::class.java).shouldNotBeNull()
                ctx.getBean("memgraphDriver", Driver::class.java).shouldNotBeNull()
                ctx.getBean(GraphOperations::class.java).shouldNotBeNull()
                ctx.getBean(GraphSuspendOperations::class.java).shouldNotBeNull()
            }
    }

    @Test
    fun `register-suspend=false 이면 GraphSuspendOperations 빈 없음`() {
        runner.withPropertyValues(
            *memgraphProperties,
            "bluetape4k.graph.memgraph.register-suspend=false",
        ).run { ctx ->
            assertFailsWith<NoSuchBeanDefinitionException> {
                ctx.getBean(GraphSuspendOperations::class.java)
            }
        }
    }

    @Test
    fun `register-virtual-thread=false 이면 VirtualThreadOperations 빈 없음`() {
        runner.withPropertyValues(
            *memgraphProperties,
            "bluetape4k.graph.memgraph.register-virtual-thread=false",
        ).run { ctx ->
            assertFailsWith<NoSuchBeanDefinitionException> {
                ctx.getBean(GraphVirtualThreadOperations::class.java)
            }
        }
    }

    @Test
    fun `Memgraph health indicator reports UP when connectivity succeeds`() {
        val driver = mockk<Driver>()

        every { driver.verifyConnectivity() } returns Unit

        val health = GraphMemgraphAutoConfiguration.HealthConfig()
            .memgraphHealthIndicator(driver)
            .health()
            .shouldNotBeNull()

        health.status.code shouldBeEqualTo "UP"
        health.details["backend"] shouldBeEqualTo "memgraph"
        verify { driver.verifyConnectivity() }
    }

    @Test
    fun `Memgraph health indicator reports DOWN when connectivity fails`() {
        val driver = mockk<Driver>()

        every { driver.verifyConnectivity() } throws IllegalStateException("memgraph is unavailable")

        val health = GraphMemgraphAutoConfiguration.HealthConfig()
            .memgraphHealthIndicator(driver)
            .health()
            .shouldNotBeNull()

        health.status.code shouldBeEqualTo "DOWN"
        verify { driver.verifyConnectivity() }
    }
}
