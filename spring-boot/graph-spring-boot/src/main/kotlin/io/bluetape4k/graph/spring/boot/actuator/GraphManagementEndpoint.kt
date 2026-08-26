package io.bluetape4k.graph.spring.boot.actuator

import io.bluetape4k.graph.repository.GraphCapability
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.capabilities
import io.bluetape4k.graph.spring.boot.properties.AgeGraphProperties
import io.bluetape4k.graph.spring.boot.properties.FalkorDBGraphProperties
import io.bluetape4k.graph.spring.boot.properties.GraphProperties
import io.bluetape4k.graph.spring.boot.properties.MemgraphGraphProperties
import io.bluetape4k.graph.spring.boot.properties.Neo4jGraphProperties
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.actuate.endpoint.annotation.Endpoint
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation
import org.springframework.core.ResolvableType
import org.springframework.context.ApplicationContext
import org.springframework.util.ClassUtils
import java.util.Locale

private const val GRAPH_IO_IMPORTER_CLASS = "io.bluetape4k.graph.io.contract.GraphBulkImporter"
private const val GRAPH_IO_EXPORTER_CLASS = "io.bluetape4k.graph.io.contract.GraphBulkExporter"
private const val NEO4J_DRIVER_CLASS = "org.neo4j.driver.Driver"
private const val FALKORDB_DRIVER_CLASS = "com.falkordb.Driver"
private const val EXPOSED_DATABASE_CLASS = "org.jetbrains.exposed.v1.jdbc.Database"

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
    private var neo4jProperties: ObjectProvider<Neo4jGraphProperties>? = null
    private var memgraphProperties: ObjectProvider<MemgraphGraphProperties>? = null
    private var ageProperties: ObjectProvider<AgeGraphProperties>? = null
    private var falkordbProperties: ObjectProvider<FalkorDBGraphProperties>? = null
    private var applicationContext: ApplicationContext? = null
    private var classLoader: ClassLoader? = GraphManagementEndpoint::class.java.classLoader

    /** Spring auto-configuration 전용 state 주입 경로. 기존 2-인자 생성자는 유지한다. */
    private constructor(
        graphProperties: GraphProperties,
        operations: ObjectProvider<GraphOperations>,
        neo4jProperties: ObjectProvider<Neo4jGraphProperties>?,
        memgraphProperties: ObjectProvider<MemgraphGraphProperties>?,
        ageProperties: ObjectProvider<AgeGraphProperties>?,
        falkordbProperties: ObjectProvider<FalkorDBGraphProperties>?,
        applicationContext: ApplicationContext?,
        classLoader: ClassLoader?,
    ) : this(
        graphProperties = graphProperties,
        operations = operations,
    ) {
        this.neo4jProperties = neo4jProperties
        this.memgraphProperties = memgraphProperties
        this.ageProperties = ageProperties
        this.falkordbProperties = falkordbProperties
        this.applicationContext = applicationContext
        this.classLoader = classLoader
    }

    companion object {
        /** Spring auto-configuration이 optional backend state를 연결하는 내부 factory. */
        @JvmSynthetic
        internal fun configured(
            graphProperties: GraphProperties,
            operations: ObjectProvider<GraphOperations>,
            neo4jProperties: ObjectProvider<Neo4jGraphProperties>?,
            memgraphProperties: ObjectProvider<MemgraphGraphProperties>?,
            ageProperties: ObjectProvider<AgeGraphProperties>?,
            falkordbProperties: ObjectProvider<FalkorDBGraphProperties>?,
            applicationContext: ApplicationContext?,
            classLoader: ClassLoader?,
        ): GraphManagementEndpoint = GraphManagementEndpoint(
            graphProperties = graphProperties,
            operations = operations,
            neo4jProperties = neo4jProperties,
            memgraphProperties = memgraphProperties,
            ageProperties = ageProperties,
            falkordbProperties = falkordbProperties,
            applicationContext = applicationContext,
            classLoader = classLoader,
        )
    }

    @ReadOperation
    fun graph(): GraphManagementSnapshot {
        val backend = graphProperties.backend
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotEmpty() }
            ?: "tinkergraph"
        val operation = operations.ifAvailable
        val sessionAvailable = operation != null
        val (graph, database) = configuredNames(backend)
        val driverAvailable = driverAvailable(backend, sessionAvailable)
        val graphIoAvailable = sessionAvailable && graphIoOnClasspath()
        val capabilities = operation?.capabilities()

        return GraphManagementSnapshot(
            backend = backend,
            graph = graph,
            database = database,
            driverAvailable = driverAvailable,
            sessionAvailable = sessionAvailable,
            capabilities = mapOf(
                "schema" to (capabilities?.supports(GraphCapability.SCHEMA) == true),
                "graphIo" to graphIoAvailable,
            ),
        )
    }

    private fun configuredNames(backend: String): Pair<String, String> = when (backend) {
        "neo4j" -> "default" to (neo4jProperties?.ifAvailable?.database ?: "neo4j")
        "memgraph" -> "default" to (memgraphProperties?.ifAvailable?.database ?: "memgraph")
        "age" -> (ageProperties?.ifAvailable?.graphName ?: "bluetape4k_graph") to "default"
        "falkordb" -> (falkordbProperties?.ifAvailable?.graphName ?: "bluetape4k") to "default"
        else -> "default" to "default"
    }

    private fun driverAvailable(backend: String, sessionAvailable: Boolean): Boolean =
        if (!sessionAvailable) {
            false
        } else {
            applicationContext?.let { context ->
                when (backend) {
                    "neo4j", "memgraph" -> hasBeanOfType(context, NEO4J_DRIVER_CLASS)
                    "falkordb" -> hasBeanOfType(context, FALKORDB_DRIVER_CLASS)
                    "age" -> hasBeanOfType(context, EXPOSED_DATABASE_CLASS)
                    else -> false
                }
            } ?: true
        }

    private fun hasBeanOfType(context: ApplicationContext, className: String): Boolean =
        classForNameOrNull(className)?.let { type ->
            context.getBeanNamesForType(ResolvableType.forClass(type), true, false).isNotEmpty()
        } ?: false

    private fun classForNameOrNull(className: String): Class<*>? = try {
        ClassUtils.forName(className, classLoader)
    } catch (_: ClassNotFoundException) {
        null
    } catch (_: LinkageError) {
        null
    }

    private fun graphIoOnClasspath(): Boolean =
        ClassUtils.isPresent(GRAPH_IO_IMPORTER_CLASS, classLoader) &&
            ClassUtils.isPresent(GRAPH_IO_EXPORTER_CLASS, classLoader)
}

data class GraphManagementSnapshot(
    val backend: String,
    val graph: String,
    val database: String,
    val driverAvailable: Boolean,
    val sessionAvailable: Boolean,
    val capabilities: Map<String, Boolean>,
)
