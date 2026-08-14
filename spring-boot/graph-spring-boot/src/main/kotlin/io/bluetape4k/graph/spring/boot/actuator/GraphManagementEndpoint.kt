package io.bluetape4k.graph.spring.boot.actuator

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.spring.boot.properties.GraphProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation

/**
 * Read-only graph management diagnostic payload.
 *
 * The endpoint deliberately exposes capability and availability metadata only;
 * it never executes a user-supplied query or returns connection credentials.
 */
@Endpoint(id = "graph")
class GraphManagementEndpoint(
    private val graphProperties: GraphProperties,
    private val operations: ObjectProvider<GraphOperations>,
) {
    @ReadOperation
    fun graph(): GraphManagementSnapshot {
        val backend = graphProperties.backend?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: "tinkergraph"
        val available = operations.ifAvailable != null
        return GraphManagementSnapshot(
            backend = backend,
            graph = "default",
            database = "default",
            driverAvailable = available,
            sessionAvailable = available,
            capabilities = mapOf(
                "schema" to available,
                "graphIo" to true,
            ),
        )
    }
}

data class GraphManagementSnapshot(
    val backend: String,
    val graph: String,
    val database: String,
    val driverAvailable: Boolean,
    val sessionAvailable: Boolean,
    val capabilities: Map<String, Boolean>,
)
