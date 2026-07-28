package io.bluetape4k.graph.spring.boot.autoconfigure

import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.graph.age.AgeGraphSuspendOperations
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.graph.spring.boot.properties.AgeGraphProperties
import io.bluetape4k.graph.vt.asVirtualThread
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import io.bluetape4k.logging.info
import org.jetbrains.exposed.v1.jdbc.Database
import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import javax.sql.DataSource

/**
 * Apache AGE backend auto-configuration.
 *
 * `bluetape4k.graph.backend=age`일 때 활성화된다. 이 설정은 Spring Boot [DataSource]를 재사용하고
 * Exposed를 해당 DataSource에 연결한다.
 *
 * 예제:
 *
 * ```kotlin
 * import io.bluetape4k.graph.repository.GraphOperations
 * import org.springframework.boot.autoconfigure.SpringBootApplication
 * import org.springframework.boot.runApplication
 *
 * @SpringBootApplication
 * class GraphApplication
 *
 * val context = runApplication<GraphApplication>(
 *     "--bluetape4k.graph.backend=age",
 *     "--bluetape4k.graph.age.graph-name=tenant_graph",
 *     "--spring.datasource.hikari.connection-init-sql=LOAD 'age'; SET search_path = ag_catalog, public;",
 * )
 * val operations = context.getBean(GraphOperations::class.java)
 * ```
 */
@AutoConfiguration(afterName = ["org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"])
@ConditionalOnClass(
    name = [
        "io.bluetape4k.graph.age.AgeGraphOperations",
        "javax.sql.DataSource",
    ]
)
@ConditionalOnProperty(prefix = "bluetape4k.graph", name = ["backend"], havingValue = "age")
@ConditionalOnSingleCandidate(DataSource::class)
@EnableConfigurationProperties(AgeGraphProperties::class)
class GraphAgeAutoConfiguration {

    companion object : KLogging()

    /**
     * Connects an Exposed [Database] to the AGE-backed [DataSource].
     *
     * Application은 graph operations 실행 전에 AGE extension이 load되도록 Hikari connection initialization SQL을
     * 구성해야 한다:
     * ```yaml
     * spring:
     *   datasource:
     *     hikari:
     *       connection-init-sql: "LOAD 'age'; SET search_path = ag_catalog, \"$user\", public;"
     * ```
     * HikariCP seals pool configuration after startup, so this auto-configuration
     * documents the required setting instead of mutating the pool at runtime.
     */
    @Bean(name = ["ageExposedDatabase"])
    @DependsOn("dataSource")
    @ConditionalOnMissingBean(name = ["ageExposedDatabase"])
    fun ageExposedDatabase(dataSource: DataSource): Database {
        log.info { "Connecting Exposed Database to AGE DataSource" }
        return Database.connect(dataSource)
    }

    /**
     * Application이 자체 graph operations bean을 제공하지 않았을 때 AGE 기반 [GraphOperations]를 등록한다.
     */
    @Bean
    @ConditionalOnMissingBean(GraphOperations::class)
    @DependsOn("ageExposedDatabase")
    fun graphOperations(props: AgeGraphProperties): AgeGraphOperations {
        log.info { "Registering AgeGraphOperations (graphName=${props.graphName})" }
        return AgeGraphOperations(props.graphName)
    }

    /**
     * Auto-create가 활성화되면 `createGraph()`로 설정된 AGE graph를 초기화한다.
     */
    @Bean(name = ["ageGraphInitializer"])
    @DependsOn("graphOperations")
    @ConditionalOnBean(AgeGraphOperations::class)
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.age",
        name = ["auto-create-graph"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun ageGraphInitializer(ops: AgeGraphOperations, props: AgeGraphProperties): InitializingBean =
        InitializingBean {
            runCatching { ops.createGraph(props.graphName) }
                .onSuccess { log.info { "AGE graph '${props.graphName}' created" } }
                .onFailure { ex ->
                    if (ex.message?.contains("already exists", ignoreCase = true) == true) {
                        log.debug { "AGE graph '${props.graphName}' already exists — skipping" }
                    } else {
                        throw ex
                    }
                }
        }

    /**
     * Suspend 지원이 활성화되면 coroutine 친화적 AGE graph operations를 등록한다.
     */
    @Bean
    @ConditionalOnMissingBean(GraphSuspendOperations::class)
    @DependsOn("ageExposedDatabase")
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.age",
        name = ["register-suspend"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun graphSuspendOperations(props: AgeGraphProperties): GraphSuspendOperations {
        log.info { "Registering AgeGraphSuspendOperations (graphName=${props.graphName})" }
        return AgeGraphSuspendOperations(props.graphName)
    }

    /**
     * 동기 AGE operations를 기반으로 virtual-thread graph operations를 등록한다.
     */
    @Bean
    @ConditionalOnMissingBean(GraphVirtualThreadOperations::class)
    @DependsOn("ageExposedDatabase")
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.age",
        name = ["register-virtual-thread"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun graphVirtualThreadOperations(ops: GraphOperations): GraphVirtualThreadOperations =
        ops.asVirtualThread()

    /**
     * Actuator health indicator를 분리해 Actuator를 사용하지 않는 application에서 `NoClassDefFoundError`를 피한다.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["org.springframework.boot.health.contributor.HealthIndicator"])
    class HealthConfig {

        companion object : KLogging()

        @Bean
        @ConditionalOnMissingBean
        fun ageHealthIndicator(dataSource: DataSource): org.springframework.boot.health.contributor.HealthIndicator =
            org.springframework.boot.health.contributor.HealthIndicator {
                try {
                    dataSource.connection.use { conn ->
                        conn.createStatement().execute("SELECT 1")
                    }
                    org.springframework.boot.health.contributor.Health.up()
                        .withDetail("backend", "age")
                        .build()
                } catch (ex: Exception) {
                    org.springframework.boot.health.contributor.Health.down(ex).build()
                }
            }
    }
}
