package io.bluetape4k.graph.spring.boot4.autoconfigure

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.graph.servers.MemgraphServer
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldNotBeNull
import org.assertj.core.api.Assertions.assertThatThrownBy
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
            "bluetape4k.graph.memgraph.uri=${MemgraphServer.boltUrl}",
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
    fun `register-suspend=false 이면 GraphSuspendOperations 빈 없음`() {
        runner.withPropertyValues(
            *memgraphProperties,
            "bluetape4k.graph.memgraph.register-suspend=false",
        ).run { ctx ->
            assertThatThrownBy { ctx.getBean(GraphSuspendOperations::class.java) }
                .isInstanceOf(NoSuchBeanDefinitionException::class.java)
        }
    }

    @Test
    fun `register-virtual-thread=false 이면 VirtualThreadOperations 빈 없음`() {
        runner.withPropertyValues(
            *memgraphProperties,
            "bluetape4k.graph.memgraph.register-virtual-thread=false",
        ).run { ctx ->
            assertThatThrownBy { ctx.getBean(GraphVirtualThreadOperations::class.java) }
                .isInstanceOf(NoSuchBeanDefinitionException::class.java)
        }
    }
}
