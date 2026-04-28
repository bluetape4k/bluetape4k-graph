package io.bluetape4k.graph.spring.boot3.autoconfigure

import io.bluetape4k.graph.falkordb.FalkorDBGraphOperations
import io.bluetape4k.graph.falkordb.FalkorDBGraphSuspendOperations
import io.bluetape4k.graph.falkordb.FalkorDBServer
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.graph.vt.VirtualThreadOperationsAdapter
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeInstanceOf
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

/**
 * [GraphFalkorDBAutoConfiguration] 단위 테스트.
 *
 * [ApplicationContextRunner]를 사용하여 실제 Spring Boot 없이 AutoConfiguration을 검증한다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GraphFalkorDBAutoConfigurationTest {

    companion object : KLogging()

    private val runner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                GraphAutoConfiguration::class.java,
                GraphFalkorDBAutoConfiguration::class.java,
            )
        )

    private val falkordbProperties
        get() = arrayOf(
            "bluetape4k.graph.backend=falkordb",
            "bluetape4k.graph.falkordb.host=${FalkorDBServer.Launcher.falkordb.host}",
            "bluetape4k.graph.falkordb.port=${FalkorDBServer.Launcher.falkordb.port}",
        )

    @Test
    fun `backend=falkordb 이면 GraphOperations 빈 등록`() {
        runner.withPropertyValues(*falkordbProperties)
            .run { ctx ->
                ctx.getBean(GraphOperations::class.java).shouldBeInstanceOf<FalkorDBGraphOperations>()
                ctx.getBean(GraphSuspendOperations::class.java).shouldBeInstanceOf<FalkorDBGraphSuspendOperations>()
                ctx.getBean(GraphVirtualThreadOperations::class.java).shouldBeInstanceOf<VirtualThreadOperationsAdapter>()
            }
    }

    @Test
    fun `register-suspend=false 이면 GraphSuspendOperations 빈 없음`() {
        runner.withPropertyValues(
            *falkordbProperties,
            "bluetape4k.graph.falkordb.register-suspend=false",
        ).run { ctx ->
            assertThatThrownBy { ctx.getBean(GraphSuspendOperations::class.java) }
                .isInstanceOf(NoSuchBeanDefinitionException::class.java)
        }
    }

    @Test
    fun `register-virtual-thread=false 이면 VirtualThreadOperations 빈 없음`() {
        runner.withPropertyValues(
            *falkordbProperties,
            "bluetape4k.graph.falkordb.register-virtual-thread=false",
        ).run { ctx ->
            assertThatThrownBy { ctx.getBean(GraphVirtualThreadOperations::class.java) }
                .isInstanceOf(NoSuchBeanDefinitionException::class.java)
        }
    }

    @Test
    fun `backend=falkordb 아닐 때 빈 등록 안됨`() {
        runner.withPropertyValues("bluetape4k.graph.backend=neo4j")
            .run { ctx ->
                assertThatThrownBy { ctx.getBean(GraphOperations::class.java) }
                    .isInstanceOf(NoSuchBeanDefinitionException::class.java)
            }
    }
}
