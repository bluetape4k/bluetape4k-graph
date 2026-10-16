package io.bluetape4k.graph.spring.boot.autoconfigure

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBe
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldNotBe
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.testcontainers.graphdb.Neo4jServer
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.neo4j.driver.Driver
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.getBean
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.boot.test.context.runner.ApplicationContextRunner

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GraphNeo4jAutoConfigurationTest {

    companion object: KLogging()

    private val runner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                GraphAutoConfiguration::class.java,
                GraphNeo4jAutoConfiguration::class.java,
            )
        )

    private val neo4jProperties
        get() = arrayOf(
            "bluetape4k.graph.backend=neo4j",
            "bluetape4k.graph.neo4j.uri=${Neo4jServer.Launcher.neo4j.boltUrl}",
            "bluetape4k.graph.neo4j.password=",
        )

    private val localNeo4jProperties = arrayOf(
        "bluetape4k.graph.backend=neo4j",
        "bluetape4k.graph.neo4j.uri=bolt://localhost:7687",
        "bluetape4k.graph.neo4j.password=",
    )

    @Test
    fun `backend=neo4j 이면 GraphOperations 빈 등록`() {
        runner.withPropertyValues(*neo4jProperties)
            .run { ctx ->
                ctx.getBean<GraphOperations>().shouldNotBeNull()
                ctx.getBean<GraphSuspendOperations>().shouldNotBeNull()
                ctx.getBean<GraphVirtualThreadOperations>().shouldNotBeNull()
            }
    }

    @Test
    fun `register-suspend=false 이면 GraphSuspendOperations 빈 없음`() {
        runner.withPropertyValues(
            *neo4jProperties,
            "bluetape4k.graph.neo4j.register-suspend=false",
        ).run { ctx ->
            assertFailsWith<NoSuchBeanDefinitionException> {
                ctx.getBean<GraphSuspendOperations>()
            }
        }
    }

    @Test
    fun `unrelated Driver bean 이 있어도 neo4jDriver 를 별도로 등록한다`() {
        val unrelatedDriver = mockk<Driver>(relaxed = true)

        runner
            .withBean("unrelatedDriver", Driver::class.java, { unrelatedDriver })
            .withPropertyValues(*localNeo4jProperties)
            .run { ctx ->
                ctx.getBean<Driver>("neo4jDriver").shouldNotBeNull()
                ctx.getBean<Driver>("neo4jDriver") shouldNotBe unrelatedDriver
                ctx.getBean<GraphOperations>().shouldNotBeNull()
                ctx.getBean<GraphSuspendOperations>().shouldNotBeNull()
            }
    }

    @Test
    fun `multiple unrelated Driver bean 이 있어도 neo4jDriver 를 생성한다`() {
        runner
            .withBean("firstUnrelatedDriver", Driver::class.java, { mockk<Driver>(relaxed = true) })
            .withBean("secondUnrelatedDriver", Driver::class.java, { mockk<Driver>(relaxed = true) })
            .withPropertyValues(*localNeo4jProperties)
            .run { ctx ->
                ctx.getBean<Driver>("neo4jDriver").shouldNotBeNull()
                ctx.getBean<GraphOperations>().shouldNotBeNull()
                ctx.getBean<GraphSuspendOperations>().shouldNotBeNull()
            }
    }

    @Test
    fun `명시한 neo4jDriver 만 operations suspend health 에 주입한다`() {
        val explicitDriver = mockk<Driver>(relaxed = true)
        every { explicitDriver.verifyConnectivity() } returns Unit

        runner
            .withBean("neo4jDriver", Driver::class.java, { explicitDriver })
            .withBean("unrelatedDriver", Driver::class.java, { mockk<Driver>(relaxed = true) })
            .withPropertyValues(*localNeo4jProperties)
            .run { ctx ->
                ctx.getBean<Driver>("neo4jDriver") shouldBe explicitDriver
                driverOf(ctx.getBean<GraphOperations>()) shouldBe explicitDriver
                driverOf(ctx.getBean<GraphSuspendOperations>()) shouldBe explicitDriver

                ctx.getBean<HealthIndicator>("neo4jHealthIndicator").health()

                verify { explicitDriver.verifyConnectivity() }
            }
    }

    @Test
    fun `register-virtual-thread=false 이면 VirtualThreadOperations 빈 없음`() {
        runner.withPropertyValues(
            *neo4jProperties,
            "bluetape4k.graph.neo4j.register-virtual-thread=false",
        ).run { ctx ->
            assertFailsWith<NoSuchBeanDefinitionException> {
                ctx.getBean<GraphVirtualThreadOperations>()
            }
        }
    }

    @Test
    fun `Neo4j health indicator reports UP when connectivity succeeds`() {
        val driver = mockk<Driver>()

        every { driver.verifyConnectivity() } returns Unit

        val health = GraphNeo4jAutoConfiguration.HealthConfig()
            .neo4jHealthIndicator(driver)
            .health()
            .shouldNotBeNull()

        log.debug { "health=$health" }
        health.status.code shouldBeEqualTo "UP"
        health.details["backend"] shouldBeEqualTo "neo4j"
        verify { driver.verifyConnectivity() }
    }

    @Test
    fun `Neo4j health indicator reports DOWN when connectivity fails`() {
        val driver = mockk<Driver>()

        every { driver.verifyConnectivity() } throws IllegalStateException("neo4j is unavailable")

        val health = GraphNeo4jAutoConfiguration.HealthConfig()
            .neo4jHealthIndicator(driver)
            .health()
            .shouldNotBeNull()

        log.debug { "health=$health" }
        health.status.code shouldBeEqualTo "DOWN"
        verify { driver.verifyConnectivity() }
    }

    private fun driverOf(bean: Any): Driver =
        bean.javaClass.getDeclaredField("driver").apply { isAccessible = true }.get(bean) as Driver
}
