package io.bluetape4k.graph

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphPath
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.PathStep
import io.bluetape4k.graph.model.edgeOf
import io.bluetape4k.graph.model.toGraphElementId
import io.bluetape4k.graph.model.vertexOf
import io.bluetape4k.logging.KLogging
import java.nio.file.Files
import java.nio.file.Path

abstract class AbstractGraphTest {

    companion object: KLogging() {
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

    protected inline fun <T> withTemporaryDirectory(
        prefix: String = "graph-abi-",
        block: (Path) -> T,
    ): T {
        val root = Files.createTempDirectory(prefix)
        return try {
            block(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
