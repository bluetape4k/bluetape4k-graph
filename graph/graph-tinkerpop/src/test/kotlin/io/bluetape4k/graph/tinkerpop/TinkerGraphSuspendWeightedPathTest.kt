package io.bluetape4k.graph.tinkerpop

import io.bluetape4k.assertions.shouldBeNear
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.graph.conformance.WeightedPathDepthConformance
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.MissingWeightPolicy
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.io.Serializable

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TinkerGraphSuspendWeightedPathTest: AbstractTinkerGraphTest() {

    companion object: KLoggingChannel()

    @Test
    fun `Dijkstra 최단 경로 A에서 C까지는 A-B-C이다`() = runSuspendIO {
        val (a, _, c) = createWeightedRoadGraph()

        val path = suspendOps.shortestPath(
            a.id,
            c.id,
            PathOptions(weightProperty = "cost", edgeLabel = "ROAD"),
        ).shouldNotBeNull()

        path.totalWeight.shouldBeNear(3.0)
    }

    @Test
    fun `suspend weighted path가 maxDepth 경계를 준수한다`() = runSuspendIO {
        WeightedPathDepthConformance.assertSuspend(suspendOps)
    }

    @Test
    fun `Skip 정책에서 weight 없는 간선은 경로에서 제외한다`() = runSuspendIO {
        val a = suspendOps.createVertex("City", mapOf("name" to "A"))
        val b = suspendOps.createVertex("City", mapOf("name" to "B"))
        val c = suspendOps.createVertex("City", mapOf("name" to "C"))

        suspendOps.createEdge(a.id, b.id, "ROAD")
        suspendOps.createEdge(b.id, c.id, "ROAD", mapOf("cost" to 2.0))

        suspendOps.shortestPath(
            a.id,
            c.id,
            PathOptions(
                weightProperty = "cost",
                edgeLabel = "ROAD",
                missingWeightPolicy = MissingWeightPolicy.Skip,
            ),
        ).shouldBeNull()
    }

    @Test
    fun `연결되지 않으면 가중치 경로는 null이다`() = runSuspendIO {
        val a = suspendOps.createVertex("City", mapOf("name" to "A"))
        val isolated = suspendOps.createVertex("City", mapOf("name" to "Z"))

        suspendOps.shortestPath(
            a.id,
            isolated.id,
            PathOptions(weightProperty = "cost", edgeLabel = "ROAD"),
        ).shouldBeNull()
    }

    @Test
    fun `AStar 최단 경로 A에서 C까지는 A-B-C이다`() = runSuspendIO {
        val (a, _, c) = createWeightedRoadGraph()

        val path = suspendOps.aStarPath(
            a.id,
            c.id,
            PathOptions(weightProperty = "cost", edgeLabel = "ROAD"),
        ) { _ -> 0.0 }.shouldNotBeNull()

        log.debug { "path=$path" }
        path.totalWeight.shouldBeNear(3.0)
    }

    private suspend fun createWeightedRoadGraph(): WeightedRoadGraph {
        val a = suspendOps.createVertex("City", mapOf("name" to "A"))
        val b = suspendOps.createVertex("City", mapOf("name" to "B"))
        val c = suspendOps.createVertex("City", mapOf("name" to "C"))

        suspendOps.createEdge(a.id, b.id, "ROAD", mapOf("cost" to 1.0))
        suspendOps.createEdge(b.id, c.id, "ROAD", mapOf("cost" to 2.0))
        suspendOps.createEdge(a.id, c.id, "ROAD", mapOf("cost" to 5.0))

        return WeightedRoadGraph(a, b, c)
    }

    private data class WeightedRoadGraph(
        val a: GraphVertex,
        val b: GraphVertex,
        val c: GraphVertex,
    ): Serializable {
        private companion object {
            private const val serialVersionUID: Long = 1L
        }
    }
}
