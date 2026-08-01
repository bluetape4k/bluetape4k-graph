package io.bluetape4k.graph.spring.boot.autoconfigure

import io.bluetape4k.graph.spring.boot.properties.GraphProperties
import io.bluetape4k.logging.KLogging
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties

/**
 * bluetape4k Graph root auto-configuration.
 *
 * 이 class는 [GraphProperties]를 노출하고 backend별 auto-configuration class의 순서를 정한다.
 * 자체적으로 graph bean을 만들지는 않으며, backend auto-configuration은
 * `AutoConfiguration.imports`에 별도로 등록된다.
 *
 * 예제:
 *
 * ```kotlin
 * import org.springframework.boot.autoconfigure.SpringBootApplication
 * import org.springframework.boot.runApplication
 *
 * @SpringBootApplication
 * class GraphApplication
 *
 * fun main(args: Array<String>) {
 *     runApplication<GraphApplication>(*args) {
 *         setDefaultProperties(mapOf("bluetape4k.graph.backend" to "tinkergraph"))
 *     }
 * }
 * ```
 */
@AutoConfiguration(
    beforeName = [
        "io.bluetape4k.graph.spring.boot.autoconfigure.GraphTinkerGraphAutoConfiguration",
        "io.bluetape4k.graph.spring.boot.autoconfigure.GraphNeo4jAutoConfiguration",
        "io.bluetape4k.graph.spring.boot.autoconfigure.GraphMemgraphAutoConfiguration",
        "io.bluetape4k.graph.spring.boot.autoconfigure.GraphAgeAutoConfiguration",
        "io.bluetape4k.graph.spring.boot.autoconfigure.GraphFalkorDBAutoConfiguration",
    ],
)
@EnableConfigurationProperties(GraphProperties::class)
class GraphAutoConfiguration {
    companion object : KLogging()
}
