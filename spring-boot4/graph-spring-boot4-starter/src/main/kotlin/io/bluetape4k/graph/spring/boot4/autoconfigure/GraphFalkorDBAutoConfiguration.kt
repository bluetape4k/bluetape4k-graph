package io.bluetape4k.graph.spring.boot4.autoconfigure

import com.falkordb.FalkorDB
import io.bluetape4k.graph.falkordb.FalkorDBGraphOperations
import io.bluetape4k.graph.falkordb.FalkorDBGraphSuspendOperations
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.graph.spring.boot4.properties.FalkorDBGraphProperties
import io.bluetape4k.graph.vt.asVirtualThread
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.info
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * FalkorDB 백엔드 AutoConfiguration.
 *
 * `bluetape4k.graph.backend=falkordb` 일 때 활성화된다.
 * FalkorDB는 Redis 모듈 기반의 그래프 데이터베이스이며, jfalkordb 드라이버([com.falkordb.Driver])를 사용한다.
 */
@AutoConfiguration
@ConditionalOnClass(com.falkordb.Driver::class, FalkorDBGraphOperations::class)
@ConditionalOnProperty(prefix = "bluetape4k.graph", name = ["backend"], havingValue = "falkordb")
@EnableConfigurationProperties(FalkorDBGraphProperties::class)
class GraphFalkorDBAutoConfiguration {

    companion object : KLogging()

    /**
     * FalkorDB Driver 빈. 이미 등록된 Driver 빈이 있으면 재사용한다.
     *
     * username이 비어있으면 인증 없이 접속하고, 아니면 username/password 인증을 사용한다.
     */
    @Bean(name = ["falkordbDriver"], destroyMethod = "close")
    @ConditionalOnMissingBean(com.falkordb.Driver::class)
    fun falkordbDriver(props: FalkorDBGraphProperties): com.falkordb.Driver {
        log.info { "Creating FalkorDB Driver: host=${props.host}, port=${props.port}" }
        return if (props.username.isBlank()) {
            FalkorDB.driver(props.host, props.port)
        } else {
            FalkorDB.driver(props.host, props.port, props.username, props.password)
        }
    }

    /**
     * FalkorDB 기반 [GraphOperations] 빈.
     */
    @Bean
    @ConditionalOnMissingBean(GraphOperations::class)
    fun graphOperations(driver: com.falkordb.Driver, props: FalkorDBGraphProperties): GraphOperations =
        FalkorDBGraphOperations(driver, props.graphName)

    /**
     * FalkorDB 기반 [GraphSuspendOperations] 빈 (코루틴).
     */
    @Bean
    @ConditionalOnMissingBean(GraphSuspendOperations::class)
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.falkordb",
        name = ["register-suspend"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun graphSuspendOperations(driver: com.falkordb.Driver, props: FalkorDBGraphProperties): GraphSuspendOperations =
        FalkorDBGraphSuspendOperations(driver, props.graphName)

    /**
     * Virtual Thread 기반 [GraphVirtualThreadOperations] 빈.
     */
    @Bean
    @ConditionalOnMissingBean(GraphVirtualThreadOperations::class)
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.falkordb",
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

        companion object : KLogging()

        /**
         * FalkorDB 연결 상태를 확인하는 HealthIndicator 빈.
         *
         * `__health__` 그래프에 `RETURN 1` 쿼리를 실행하여 연결 상태를 확인한다.
         */
        @Bean
        @ConditionalOnMissingBean
        fun falkordbHealthIndicator(driver: com.falkordb.Driver): org.springframework.boot.health.contributor.HealthIndicator =
            org.springframework.boot.health.contributor.HealthIndicator {
                try {
                    driver.graph("__health__").use { it.query("RETURN 1") }
                    org.springframework.boot.health.contributor.Health.up().withDetail("backend", "falkordb").build()
                } catch (e: Exception) {
                    org.springframework.boot.health.contributor.Health.down(e).build()
                }
            }
    }
}
