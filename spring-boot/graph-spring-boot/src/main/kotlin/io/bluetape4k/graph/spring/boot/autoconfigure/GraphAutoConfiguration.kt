package io.bluetape4k.graph.spring.boot.autoconfigure

import io.bluetape4k.graph.spring.boot.properties.GraphProperties
import io.bluetape4k.logging.KLogging
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties

/**
 * Root bluetape4k Graph auto-configuration.
 *
 * This class exposes [GraphProperties] and orders backend-specific
 * auto-configuration classes. It does not create graph beans by itself; backend
 * auto-configurations are registered separately in `AutoConfiguration.imports`.
 *
 * Example:
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
    before = [
        GraphTinkerGraphAutoConfiguration::class,
        GraphNeo4jAutoConfiguration::class,
        GraphMemgraphAutoConfiguration::class,
        GraphAgeAutoConfiguration::class,
        GraphFalkorDBAutoConfiguration::class,
    ],
)
@EnableConfigurationProperties(GraphProperties::class)
class GraphAutoConfiguration {
    companion object : KLogging()
}
