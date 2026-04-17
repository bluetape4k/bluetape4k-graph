package io.bluetape4k.graph.spring.boot4.autoconfigure

import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.graph.age.AgeGraphSuspendOperations
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.graph.spring.boot4.properties.AgeGraphProperties
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
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import javax.sql.DataSource

/**
 * Apache AGE 백엔드 AutoConfiguration.
 *
 * `bluetape4k.graph.backend=age` 일 때 활성화된다.
 * Spring Boot 기본 `DataSource`를 재사용하며, HikariCP가 있으면 AGE extension을 자동 설정한다.
 */
@AutoConfiguration(after = [DataSourceAutoConfiguration::class])
@ConditionalOnClass(AgeGraphOperations::class, DataSource::class)
@ConditionalOnProperty(prefix = "bluetape4k.graph", name = ["backend"], havingValue = "age")
@ConditionalOnSingleCandidate(DataSource::class)
@EnableConfigurationProperties(AgeGraphProperties::class)
class GraphAgeAutoConfiguration {

    companion object : KLogging()

    /**
     * Exposed Database 빈 — AGE DataSource에 연결.
     *
     * **필수**: `application.yml`에 아래 설정을 추가해야 AGE extension이 올바르게 로드된다.
     * ```yaml
     * spring:
     *   datasource:
     *     hikari:
     *       connection-init-sql: "LOAD 'age'; SET search_path = ag_catalog, \"$user\", public;"
     * ```
     * HikariCP 풀은 시작 후 설정이 봉인(seal)되므로 AutoConfiguration에서 직접 설정할 수 없다.
     */
    @Bean(name = ["ageExposedDatabase"])
    @DependsOn("dataSource")
    @ConditionalOnMissingBean(name = ["ageExposedDatabase"])
    fun ageExposedDatabase(dataSource: DataSource): Database {
        log.info { "Connecting Exposed Database to AGE DataSource" }
        return Database.connect(dataSource)
    }

    /**
     * AGE 기반 `GraphOperations` 빈. 구체 타입 `AgeGraphOperations`를 반환한다.
     *
     * 사용자가 `GraphOperations` 빈을 직접 등록하면 이 빈은 생성되지 않는다.
     */
    @Bean
    @ConditionalOnMissingBean(GraphOperations::class)
    @DependsOn("ageExposedDatabase")
    fun graphOperations(props: AgeGraphProperties): AgeGraphOperations {
        log.info { "Registering AgeGraphOperations (graphName=${props.graphName})" }
        return AgeGraphOperations(props.graphName)
    }

    /**
     * AGE 그래프 초기화 빈 — `createGraph()` API로 그래프를 생성한다.
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
                .onSuccess { log.info { "AGE graph '${props.graphName}' created / verified" } }
                .onFailure { log.debug { "AGE graph '${props.graphName}' already exists: ${it.message}" } }
        }

    /**
     * AGE 기반 `GraphSuspendOperations` 빈 (코루틴).
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
     * Virtual Thread 기반 `GraphVirtualThreadOperations` 빈.
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
     * Actuator HealthIndicator — nested class로 격리.
     * Actuator 미사용 앱에서 `NoClassDefFoundError` 방지.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["org.springframework.boot.health.contributor.HealthIndicator"])
    class HealthConfig {
        @Bean
        @ConditionalOnMissingBean
        fun ageHealthIndicator(props: AgeGraphProperties): org.springframework.boot.health.contributor.HealthIndicator =
            org.springframework.boot.health.contributor.HealthIndicator {
                org.springframework.boot.health.contributor.Health.up()
                    .withDetail("backend", "age")
                    .withDetail("graphName", props.graphName)
                    .build()
            }
    }
}
