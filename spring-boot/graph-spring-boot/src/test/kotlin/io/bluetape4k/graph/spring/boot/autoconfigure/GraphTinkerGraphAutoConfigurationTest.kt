package io.bluetape4k.graph.spring.boot.autoconfigure

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.logging.KLogging
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class GraphTinkerGraphAutoConfigurationTest {

    companion object : KLogging()

    private val runner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                GraphAutoConfiguration::class.java,
                GraphTinkerGraphAutoConfiguration::class.java,
            )
        )

    @Test
    fun `backend=tinkergraph 이면 GraphOperations 빈 등록`() {
        runner.withPropertyValues("bluetape4k.graph.backend=tinkergraph")
            .run { ctx ->
                ctx.getBean(GraphOperations::class.java).shouldNotBeNull()
                ctx.getBean(GraphSuspendOperations::class.java).shouldNotBeNull()
                ctx.getBean(GraphVirtualThreadOperations::class.java).shouldNotBeNull()
            }
    }

    @Test
    fun `backend 미설정 시 tinkergraph matchIfMissing 으로 빈 등록`() {
        runner.run { ctx ->
            ctx.getBean(GraphOperations::class.java).shouldNotBeNull()
        }
    }

    @Test
    fun `custom GraphOperations만 제공해도 기본 suspend factory가 startup failure를 만들지 않는다`() {
        val customOperations = mockk<GraphOperations>(relaxed = true)

        runner
            .withBean(GraphOperations::class.java, { customOperations })
            .run { ctx ->
                ctx.startupFailure.shouldBeNull()
                (ctx.getBean(GraphOperations::class.java) === customOperations).shouldBeTrue()
                assertFailsWith<NoSuchBeanDefinitionException> {
                    ctx.getBean(GraphSuspendOperations::class.java)
                }
                ctx.getBean(GraphVirtualThreadOperations::class.java).shouldNotBeNull()
            }
    }

    @Test
    fun `custom sync와 suspend pair를 제공하면 두 bean을 그대로 사용한다`() {
        val customOperations = mockk<GraphOperations>(relaxed = true)
        val customSuspendOperations = mockk<GraphSuspendOperations>(relaxed = true)

        runner
            .withBean(GraphOperations::class.java, { customOperations })
            .withBean(GraphSuspendOperations::class.java, { customSuspendOperations })
            .run { ctx ->
                ctx.startupFailure.shouldBeNull()
                (ctx.getBean(GraphOperations::class.java) === customOperations).shouldBeTrue()
                (ctx.getBean(GraphSuspendOperations::class.java) === customSuspendOperations).shouldBeTrue()
                ctx.getBean(GraphVirtualThreadOperations::class.java).shouldNotBeNull()
            }
    }

    @Test
    fun `custom sync와 suspend 비활성화를 함께 사용하면 startup failure가 없다`() {
        val customOperations = mockk<GraphOperations>(relaxed = true)

        runner
            .withBean(GraphOperations::class.java, { customOperations })
            .withPropertyValues(
                "bluetape4k.graph.tinkergraph.register-suspend=false",
            ).run { ctx ->
                ctx.startupFailure.shouldBeNull()
                assertFailsWith<NoSuchBeanDefinitionException> {
                    ctx.getBean(GraphSuspendOperations::class.java)
                }
                ctx.getBean(GraphVirtualThreadOperations::class.java).shouldNotBeNull()
            }
    }

    @Test
    fun `backend=neo4j 이면 tinkergraph 빈 없음`() {
        runner.withPropertyValues("bluetape4k.graph.backend=neo4j")
            .run { ctx ->
                ctx.containsBean("graphOperations").shouldBeFalse()
            }
    }

    @Test
    fun `register-suspend=false 이면 GraphSuspendOperations 빈 없음`() {
        runner.withPropertyValues(
            "bluetape4k.graph.backend=tinkergraph",
            "bluetape4k.graph.tinkergraph.register-suspend=false",
        ).run { ctx ->
            assertFailsWith<NoSuchBeanDefinitionException> {
                ctx.getBean(GraphSuspendOperations::class.java)
            }
        }
    }

    @Test
    fun `register-virtual-thread=false 이면 VirtualThreadOperations 빈 없음`() {
        runner.withPropertyValues(
            "bluetape4k.graph.backend=tinkergraph",
            "bluetape4k.graph.tinkergraph.register-virtual-thread=false",
        ).run { ctx ->
            assertFailsWith<NoSuchBeanDefinitionException> {
                ctx.getBean(GraphVirtualThreadOperations::class.java)
            }
        }
    }

    @Test
    fun `TinkerGraph health indicator reports UP`() {
        val health = GraphTinkerGraphAutoConfiguration.HealthConfig()
            .tinkerGraphHealthIndicator()
            .health()
            .shouldNotBeNull()

        health.status.code shouldBeEqualTo "UP"
        health.details["backend"] shouldBeEqualTo "tinkergraph"
    }
}
