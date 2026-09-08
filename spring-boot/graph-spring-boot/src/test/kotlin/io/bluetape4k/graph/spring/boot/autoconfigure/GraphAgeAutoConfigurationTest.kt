package io.bluetape4k.graph.spring.boot.autoconfigure

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.graph.age.AgeGraphSuspendOperations
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.graph.spring.boot.properties.AgeGraphProperties
import io.bluetape4k.logging.KLogging
import io.bluetape4k.testcontainers.graphdb.PostgreSQLAgeServer
import io.bluetape4k.assertions.assertFailsWith
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.jetbrains.exposed.v1.jdbc.Database
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.sql.Connection
import java.sql.Statement
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

    @Configuration(proxyBeanMethods = false)
    class NamedDataSourceConfig {
        @Bean(name = ["tenantDataSource"])
        fun tenantDataSource(): DataSource = mockk(relaxed = true)
    }

    private val runner = ApplicationContextRunner()
        .withUserConfiguration(DataSourceConfig::class.java)
        .withConfiguration(
            AutoConfigurations.of(
                GraphAutoConfiguration::class.java,
                GraphAgeAutoConfiguration::class.java,
            )
        )

    private val namedDataSourceRunner = ApplicationContextRunner()
        .withUserConfiguration(NamedDataSourceConfig::class.java)
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
                ctx.getBean(GraphOperations::class.java).shouldNotBeNull()
                ctx.getBean(GraphSuspendOperations::class.java).shouldNotBeNull()
                ctx.getBean(GraphVirtualThreadOperations::class.java).shouldNotBeNull()
            }
    }

    @Test
    fun `register-suspend=false 이면 GraphSuspendOperations 빈 없음`() {
        runner.withPropertyValues(
            *ageProperties,
            "bluetape4k.graph.age.register-suspend=false",
        ).run { ctx ->
            assertFailsWith<NoSuchBeanDefinitionException> {
                ctx.getBean(GraphSuspendOperations::class.java)
            }
        }
    }

    @Test
    fun `register-virtual-thread=false 이면 VirtualThreadOperations 빈 없음`() {
        runner.withPropertyValues(
            *ageProperties,
            "bluetape4k.graph.age.register-virtual-thread=false",
        ).run { ctx ->
            assertFailsWith<NoSuchBeanDefinitionException> {
                ctx.getBean(GraphVirtualThreadOperations::class.java)
            }
        }
    }

    @Test
    fun `유일한 DataSource 이름이 dataSource가 아니어도 AGE 자동 구성이 시작된다`() {
        namedDataSourceRunner.withPropertyValues(
            *ageProperties,
            "bluetape4k.graph.age.auto-create-graph=false",
        ).run { ctx ->
            ctx.startupFailure.shouldBeNull()
            ctx.getBean("ageExposedDatabase", Database::class.java).shouldNotBeNull()
            ctx.getBean(GraphOperations::class.java).shouldNotBeNull()
        }
    }

    @Test
    fun `AGE Database와 애플리케이션 Database가 공존하면 AGE Database를 명시적으로 사용한다`() {
        val ageDatabase = Database.connect(mockk<DataSource>(relaxed = true))
        val applicationDatabase = Database.connect(mockk<DataSource>(relaxed = true))

        namedDataSourceRunner
            .withBean("ageExposedDatabase", Database::class.java, java.util.function.Supplier { ageDatabase })
            .withBean("applicationDatabase", Database::class.java, java.util.function.Supplier { applicationDatabase })
            .withPropertyValues(
                *ageProperties,
                "bluetape4k.graph.age.auto-create-graph=false",
            ).run { ctx ->
                ctx.startupFailure.shouldBeNull()
                (databaseOf(ctx.getBean(AgeGraphOperations::class.java)) === ageDatabase).shouldBeTrue()
                (databaseOf(ctx.getBean(AgeGraphSuspendOperations::class.java)) === ageDatabase).shouldBeTrue()
            }
    }

    @Test
    fun `initializer rethrows generic failure even when message resembles duplicate`() {
        val operations = mockk<io.bluetape4k.graph.age.AgeGraphOperations>()
        every { operations.createGraph("test_graph") } throws
            IllegalStateException("graph already exists but the connection failed")

        val initializer = GraphAgeAutoConfiguration()
            .ageGraphInitializer(
                operations,
                AgeGraphProperties("test_graph"),
            )

        assertFailsWith<IllegalStateException> {
            initializer.afterPropertiesSet()
        }
        verify(exactly = 1) { operations.createGraph("test_graph") }
    }

    @Test
    fun `AGE health indicator reports UP when validation query succeeds`() {
        val dataSource = mockk<DataSource>()
        val connection = mockk<Connection>(relaxed = true)
        val statement = mockk<Statement>(relaxed = true)

        every { dataSource.connection } returns connection
        every { connection.createStatement() } returns statement
        every { statement.execute("SELECT 1") } returns true

        val health = GraphAgeAutoConfiguration.HealthConfig()
            .ageHealthIndicator(dataSource)
            .health()
            .shouldNotBeNull()

        health.status.code shouldBeEqualTo "UP"
        health.details["backend"] shouldBeEqualTo "age"
        verify {
            statement.execute("SELECT 1")
            connection.close()
        }
    }

    @Test
    fun `AGE health indicator reports DOWN when validation query fails`() {
        val dataSource = mockk<DataSource>()

        every { dataSource.connection } throws IllegalStateException("age is unavailable")

        val health = GraphAgeAutoConfiguration.HealthConfig()
            .ageHealthIndicator(dataSource)
            .health()
            .shouldNotBeNull()

        health.status.code shouldBeEqualTo "DOWN"
    }

    private fun databaseOf(bean: Any): Database =
        bean.javaClass.getDeclaredField("database").apply { isAccessible = true }.get(bean) as Database
}
