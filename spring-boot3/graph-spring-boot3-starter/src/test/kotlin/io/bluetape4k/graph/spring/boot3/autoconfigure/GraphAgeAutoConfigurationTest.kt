package io.bluetape4k.graph.spring.boot3.autoconfigure

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.graph.age.AgeGraphSuspendOperations
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.graph.vt.VirtualThreadOperationsAdapter
import io.bluetape4k.testcontainers.graphdb.PostgreSQLAgeServer
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeInstanceOf
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GraphAgeAutoConfigurationTest {

    companion object : KLogging()

    @Configuration(proxyBeanMethods = false)
    class DataSourceConfig {
        @Bean(destroyMethod = "close")
        fun dataSource(): DataSource {
            val cfg = HikariConfig().apply {
                jdbcUrl = PostgreSQLAgeServer.Launcher.postgresqlAge.jdbcUrl
                username = PostgreSQLAgeServer.Launcher.postgresqlAge.username
                password = PostgreSQLAgeServer.Launcher.postgresqlAge.password
                maximumPoolSize = 2
                connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
            }
            return HikariDataSource(cfg)
        }
    }

    private val runner = ApplicationContextRunner()
        .withUserConfiguration(DataSourceConfig::class.java)
        .withConfiguration(
            AutoConfigurations.of(
                GraphAutoConfiguration::class.java,
                GraphAgeAutoConfiguration::class.java,
            )
        )

    private val ageProperties
        get() = arrayOf(
            "bluetape4k.graph.backend=age",
            "bluetape4k.graph.age.graph-name=test_graph",
        )

    @Test
    fun `backend=age 이면 GraphOperations 빈 등록`() {
        runner.withPropertyValues(*ageProperties)
            .run { ctx ->
                ctx.getBean(GraphOperations::class.java).shouldBeInstanceOf<AgeGraphOperations>()
                ctx.getBean(GraphSuspendOperations::class.java).shouldBeInstanceOf<AgeGraphSuspendOperations>()
                ctx.getBean(GraphVirtualThreadOperations::class.java).shouldBeInstanceOf<VirtualThreadOperationsAdapter>()
            }
    }

    @Test
    fun `age backend not selected — no GraphOperations bean registered`() {
        runner
            .withPropertyValues("bluetape4k.graph.backend=tinkergraph")
            .run { ctx ->
                assertThatThrownBy { ctx.getBean(GraphOperations::class.java) }
                    .isInstanceOf(NoSuchBeanDefinitionException::class.java)
            }
    }

    @Test
    fun `register-suspend=false 이면 GraphSuspendOperations 빈 없음`() {
        runner.withPropertyValues(
            *ageProperties,
            "bluetape4k.graph.age.register-suspend=false",
        ).run { ctx ->
            assertThatThrownBy { ctx.getBean(GraphSuspendOperations::class.java) }
                .isInstanceOf(NoSuchBeanDefinitionException::class.java)
        }
    }

    @Test
    fun `register-virtual-thread=false 이면 VirtualThreadOperations 빈 없음`() {
        runner.withPropertyValues(
            *ageProperties,
            "bluetape4k.graph.age.register-virtual-thread=false",
        ).run { ctx ->
            assertThatThrownBy { ctx.getBean(GraphVirtualThreadOperations::class.java) }
                .isInstanceOf(NoSuchBeanDefinitionException::class.java)
        }
    }
}
