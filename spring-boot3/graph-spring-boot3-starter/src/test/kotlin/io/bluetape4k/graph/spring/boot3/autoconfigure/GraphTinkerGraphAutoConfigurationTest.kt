package io.bluetape4k.graph.spring.boot3.autoconfigure

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldNotBeNull
import org.assertj.core.api.Assertions.assertThatThrownBy
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
            assertThatThrownBy { ctx.getBean(GraphSuspendOperations::class.java) }
                .isInstanceOf(NoSuchBeanDefinitionException::class.java)
        }
    }

    @Test
    fun `register-virtual-thread=false 이면 VirtualThreadOperations 빈 없음`() {
        runner.withPropertyValues(
            "bluetape4k.graph.backend=tinkergraph",
            "bluetape4k.graph.tinkergraph.register-virtual-thread=false",
        ).run { ctx ->
            assertThatThrownBy { ctx.getBean(GraphVirtualThreadOperations::class.java) }
                .isInstanceOf(NoSuchBeanDefinitionException::class.java)
        }
    }
}
