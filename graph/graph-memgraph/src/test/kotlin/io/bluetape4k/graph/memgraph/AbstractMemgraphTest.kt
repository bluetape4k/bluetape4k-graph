package io.bluetape4k.graph.memgraph

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.PathStep
import io.bluetape4k.graph.model.edgeOf
import io.bluetape4k.graph.model.toGraphElementId
import io.bluetape4k.graph.model.vertexOf
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.TestInstance
import org.neo4j.driver.Driver

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Suppress("UtilityClassWithPublicConstructor")
abstract class AbstractMemgraphTest {

    companion object: KLogging() {
        @JvmStatic
        protected fun getMemgraphDriver(): Driver = MemgraphTestSupport.getMemgraphDriver()


        @JvmStatic
        protected fun id(value: Any): GraphElementId = value.toGraphElementId()

        @JvmStatic
        protected fun vertex(
            id: String,
            label: String = "V",
            properties: Map<String, Any?> = emptyMap(),
        ): GraphVertex =
            vertexOf(id, label, properties)

        @JvmStatic
        protected fun edge(
            id: String,
            from: String,
            to: String,
            weight: Double? = 0.0,
            label: String = "ROAD",
        ): GraphEdge = edgeOf(
            id = id,
            label = label,
            startId = from,
            endId = to,
            properties = if (weight != null) mapOf("cost" to weight) else emptyMap(),
        )

        @JvmStatic
        protected fun GraphPath.vertexIds(): List<String> =
            steps.filterIsInstance<PathStep.VertexStep>().map { it.vertex.id.value }
    }
}
