package io.bluetape4k.graph.spring.boot4.autoconfigure

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.testcontainers.graphdb.Neo4jServer
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldNotBeNull
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GraphNeo4jAutoConfigurationTest {

    companion object : KLogging()

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

    @Test
    fun `backend=neo4j 이면 GraphOperations 빈 등록`() {
        runner.withPropertyValues(*neo4jProperties)
            .run { ctx ->
                ctx.getBean(GraphOperations::class.java).shouldNotBeNull()
                ctx.getBean(GraphSuspendOperations::class.java).shouldNotBeNull()
                ctx.getBean(GraphVirtualThreadOperations::class.java).shouldNotBeNull()
            }
    }

    @Test
    fun `register-suspend=false 이면 GraphSuspendOperations 빈 없음`() {
        runner.withPropertyValues(
            *neo4jProperties,
            "bluetape4k.graph.neo4j.register-suspend=false",
        ).run { ctx ->
            assertThatThrownBy { ctx.getBean(GraphSuspendOperations::class.java) }
                .isInstanceOf(NoSuchBeanDefinitionException::class.java)
        }
    }

    @Test
    fun `register-virtual-thread=false 이면 VirtualThreadOperations 빈 없음`() {
        runner.withPropertyValues(
            *neo4jProperties,
            "bluetape4k.graph.neo4j.register-virtual-thread=false",
        ).run { ctx ->
            assertThatThrownBy { ctx.getBean(GraphVirtualThreadOperations::class.java) }
                .isInstanceOf(NoSuchBeanDefinitionException::class.java)
        }
    }
}
