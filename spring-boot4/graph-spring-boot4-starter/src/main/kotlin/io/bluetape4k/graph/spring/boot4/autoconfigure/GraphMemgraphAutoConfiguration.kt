package io.bluetape4k.graph.spring.boot4.autoconfigure

import io.bluetape4k.graph.memgraph.MemgraphGraphOperations
import io.bluetape4k.graph.memgraph.MemgraphGraphSuspendOperations
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.graph.spring.boot4.properties.MemgraphGraphProperties
import io.bluetape4k.graph.vt.asVirtualThread
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.info
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Memgraph 백엔드 AutoConfiguration.
 *
 * `bluetape4k.graph.backend=memgraph` 일 때 활성화된다.
 * Memgraph는 Neo4j Bolt 프로토콜 호환이므로 Neo4j Driver를 재사용한다.
 */
@AutoConfiguration
@ConditionalOnClass(Driver::class, MemgraphGraphOperations::class)
@ConditionalOnProperty(prefix = "bluetape4k.graph", name = ["backend"], havingValue = "memgraph")
@EnableConfigurationProperties(MemgraphGraphProperties::class)
class GraphMemgraphAutoConfiguration {

    companion object : KLogging()

    /**
     * Memgraph Driver 빈. 이미 등록된 Driver 빈이 있으면 재사용한다.
     * Memgraph는 Neo4j Bolt 프로토콜 호환이므로 Neo4j `GraphDatabase.driver()`를 사용한다.
     */
    @Bean(name = ["memgraphDriver"], destroyMethod = "close")
    @ConditionalOnMissingBean(Driver::class)
    fun memgraphDriver(props: MemgraphGraphProperties): Driver {
        val auth = if (props.password.isBlank()) AuthTokens.none()
                   else AuthTokens.basic(props.username, props.password)
        log.info { "Creating Memgraph Driver: uri=${props.uri}" }
        return GraphDatabase.driver(props.uri, auth)
    }

    /**
     * Memgraph 기반 `GraphOperations` 빈.
     */
    @Bean
    @ConditionalOnMissingBean(GraphOperations::class)
    fun graphOperations(driver: Driver, props: MemgraphGraphProperties): GraphOperations =
        MemgraphGraphOperations(driver, props.database)

    /**
     * Memgraph 기반 `GraphSuspendOperations` 빈 (코루틴).
     */
    @Bean
    @ConditionalOnMissingBean(GraphSuspendOperations::class)
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.memgraph",
        name = ["register-suspend"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun graphSuspendOperations(driver: Driver, props: MemgraphGraphProperties): GraphSuspendOperations =
        MemgraphGraphSuspendOperations(driver, props.database)

    /**
     * Virtual Thread 기반 `GraphVirtualThreadOperations` 빈.
     */
    @Bean
    @ConditionalOnMissingBean(GraphVirtualThreadOperations::class)
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.memgraph",
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
        fun memgraphHealthIndicator(driver: Driver): org.springframework.boot.health.contributor.HealthIndicator =
            org.springframework.boot.health.contributor.HealthIndicator {
                try {
                    driver.verifyConnectivity()
                    org.springframework.boot.health.contributor.Health.up().withDetail("backend", "memgraph").build()
                } catch (e: Exception) {
                    org.springframework.boot.health.contributor.Health.down(e).build()
                }
            }
    }
}
