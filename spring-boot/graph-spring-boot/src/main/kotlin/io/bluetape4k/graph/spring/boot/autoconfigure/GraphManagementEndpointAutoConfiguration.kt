package io.bluetape4k.graph.spring.boot.autoconfigure

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.spring.boot.actuator.GraphManagementEndpoint
import io.bluetape4k.graph.spring.boot.properties.AgeGraphProperties
import io.bluetape4k.graph.spring.boot.properties.FalkorDBGraphProperties
import io.bluetape4k.graph.spring.boot.properties.GraphProperties
import io.bluetape4k.graph.spring.boot.properties.MemgraphGraphProperties
import io.bluetape4k.graph.spring.boot.properties.Neo4jGraphProperties
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.ApplicationContext
import org.springframework.beans.factory.ObjectProvider

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
@EnableConfigurationProperties(
    Neo4jGraphProperties::class,
    MemgraphGraphProperties::class,
    AgeGraphProperties::class,
    FalkorDBGraphProperties::class,
)
class GraphManagementEndpointAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    fun graphManagementEndpoint(
        graphProperties: GraphProperties,
        operations: ObjectProvider<GraphOperations>,
        neo4jProperties: ObjectProvider<Neo4jGraphProperties>,
        memgraphProperties: ObjectProvider<MemgraphGraphProperties>,
        ageProperties: ObjectProvider<AgeGraphProperties>,
        falkordbProperties: ObjectProvider<FalkorDBGraphProperties>,
        applicationContext: ApplicationContext,
    ): GraphManagementEndpoint = GraphManagementEndpoint.configured(
        graphProperties = graphProperties,
        operations = operations,
        neo4jProperties = neo4jProperties,
        memgraphProperties = memgraphProperties,
        ageProperties = ageProperties,
        falkordbProperties = falkordbProperties,
        applicationContext = applicationContext,
        classLoader = applicationContext.classLoader ?: GraphManagementEndpoint::class.java.classLoader,
    )

    /** 기존 직접 호출자의 source·binary 호환성을 유지하는 legacy factory. */
    fun graphManagementEndpoint(
        graphProperties: GraphProperties,
        operations: ObjectProvider<GraphOperations>,
    ): GraphManagementEndpoint = GraphManagementEndpoint(graphProperties, operations)
}
