package io.bluetape4k.graph.spring.boot.autoconfigure

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.spring.boot.actuator.GraphManagementEndpoint
import io.bluetape4k.graph.spring.boot.properties.GraphProperties
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean

/**
 * Opt-in registration for the read-only `graph` Actuator endpoint.
 * It stays disabled unless `bluetape4k.graph.management.endpoint.enabled=true`.
 */
@AutoConfiguration(after = [GraphAutoConfiguration::class])
@ConditionalOnClass(name = ["org.springframework.boot.actuate.endpoint.annotation.Endpoint"])
@ConditionalOnProperty(
    prefix = "bluetape4k.graph.management.endpoint",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class GraphManagementEndpointAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun graphManagementEndpoint(
        graphProperties: GraphProperties,
        operations: org.springframework.beans.factory.ObjectProvider<GraphOperations>,
    ): GraphManagementEndpoint = GraphManagementEndpoint(graphProperties, operations)
}
