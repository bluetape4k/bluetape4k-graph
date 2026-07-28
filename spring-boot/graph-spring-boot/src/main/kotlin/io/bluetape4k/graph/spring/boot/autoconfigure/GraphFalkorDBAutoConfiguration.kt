package io.bluetape4k.graph.spring.boot.autoconfigure

import com.falkordb.FalkorDB
import io.bluetape4k.graph.falkordb.FalkorDBGraphOperations
import io.bluetape4k.graph.falkordb.FalkorDBGraphSuspendOperations
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.graph.spring.boot.properties.FalkorDBGraphProperties
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
 * FalkorDB backend auto-configuration.
 *
 * `bluetape4k.graph.backend=falkordb`일 때 활성화된다. FalkorDB는 Redis module 기반 graph database이며
 * jfalkordb [com.falkordb.Driver]를 사용한다.
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
 *     "--bluetape4k.graph.backend=falkordb",
 *     "--bluetape4k.graph.falkordb.host=localhost",
 *     "--bluetape4k.graph.falkordb.graph-name=bluetape4k",
 * )
 * val operations = context.getBean(GraphOperations::class.java)
 * ```
 */
@AutoConfiguration
@ConditionalOnClass(
    name = [
        "com.falkordb.Driver",
        "io.bluetape4k.graph.falkordb.FalkorDBGraphOperations",
    ]
)
@ConditionalOnProperty(prefix = "bluetape4k.graph", name = ["backend"], havingValue = "falkordb")
@EnableConfigurationProperties(FalkorDBGraphProperties::class)
class GraphFalkorDBAutoConfiguration {

    companion object : KLogging()

    /**
     * FalkorDB driver bean을 생성한다.
     *
     * ## 동작 계약
     * - Application이 [com.falkordb.Driver] bean을 이미 제공하면 등록하지 않는다.
     * - Connects without authentication when [FalkorDBGraphProperties.username] is blank.
     * - Uses username/password authentication when a username is configured.
     * - Spring owns this auto-created driver and closes it through the bean destroy method.
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
     * 동기 FalkorDB [GraphOperations] bean을 생성한다.
     *
     * ## 동작 계약
     * - 다른 [GraphOperations] bean이 이미 있으면 등록하지 않는다.
     * - [FalkorDBGraphProperties.graphName]에 설정된 FalkorDB graph 이름을 사용한다.
     */
    @Bean
    @ConditionalOnMissingBean(GraphOperations::class)
    fun graphOperations(driver: com.falkordb.Driver, props: FalkorDBGraphProperties): GraphOperations =
        FalkorDBGraphOperations(driver, props.graphName)

    /**
     * Coroutine FalkorDB [GraphSuspendOperations] bean을 생성한다.
     *
     * ## 동작 계약
     * - 다른 [GraphSuspendOperations] bean이 이미 있으면 등록하지 않는다.
     * - 기본적으로 등록하며 `bluetape4k.graph.falkordb.register-suspend=false`로 비활성화할 수 있다.
     * - [FalkorDBGraphProperties.graphName]에 설정된 FalkorDB graph 이름을 사용한다.
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
     * Virtual-thread [GraphVirtualThreadOperations] adapter bean을 생성한다.
     *
     * ## 동작 계약
     * - 다른 [GraphVirtualThreadOperations] bean이 이미 있으면 등록하지 않는다.
     * - 기본적으로 등록하며 `bluetape4k.graph.falkordb.register-virtual-thread=false`로 비활성화할 수 있다.
     * - 이 adapter에 전달된 동기 [GraphOperations] bean에 의존한다.
     * - 하위 FalkorDB driver 소유권을 바꾸지 않고 동기 [GraphOperations] bean을 adapter로 감싼다.
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
     * FalkorDB backend health indicator 설정.
     *
     * ## 동작 계약
     * - Spring Boot Actuator의 `HealthIndicator` type이 있을 때만 load된다.
     * - Actuator 전용 bean signature를 Actuator를 사용하지 않는 application과 분리한다.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["org.springframework.boot.health.contributor.HealthIndicator"])
    class HealthConfig {

        companion object : KLogging()

        /**
         * FalkorDB health indicator bean을 생성한다.
         *
         * ## 동작 계약
         * - `falkordbHealthIndicator`라는 이름의 bean이 이미 있으면 등록하지 않는다.
         * - Driver 연결성을 확인하기 위해 `__health__` graph에 `RETURN 1`을 실행한다.
         * - 성공 시 `backend=falkordb` detail과 함께 `UP`을 보고하고, 실패 시 던져진 예외와 함께 `DOWN`을 보고한다.
         */
        @Bean
        @ConditionalOnMissingBean(name = ["falkordbHealthIndicator"])
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
