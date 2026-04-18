package io.bluetape4k.graph.benchmark

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.vt.VirtualThreadOperationsAdapter
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown

/**
 * 벤치마크 공유 상태: TinkerGraph 기반 그래프 데이터를 초기화하고 각 구현 어댑터를 노출한다.
 */
@State(Scope.Benchmark)
open class GraphBenchmarkState {

    lateinit var syncOps: GraphOperations
    lateinit var vtOps: VirtualThreadOperationsAdapter

    var aliceId: GraphElementId = GraphElementId("0")
    var bobId: GraphElementId = GraphElementId("0")
    var charlieId: GraphElementId = GraphElementId("0")
    var daveId: GraphElementId = GraphElementId("0")

    @Setup
    fun setup() {
        syncOps = TinkerGraphOperations()
        vtOps = VirtualThreadOperationsAdapter(syncOps)

        val alice = syncOps.createVertex("Person", mapOf("name" to "Alice", "age" to 30L))
        val bob = syncOps.createVertex("Person", mapOf("name" to "Bob", "age" to 25L))
        val charlie = syncOps.createVertex("Person", mapOf("name" to "Charlie", "age" to 28L))
        val dave = syncOps.createVertex("Person", mapOf("name" to "Dave", "age" to 35L))

        aliceId = alice.id
        bobId = bob.id
        charlieId = charlie.id
        daveId = dave.id

        syncOps.createEdge(aliceId, bobId, "KNOWS", mapOf("since" to 2020L))
        syncOps.createEdge(bobId, charlieId, "KNOWS", mapOf("since" to 2021L))
        syncOps.createEdge(charlieId, daveId, "KNOWS", mapOf("since" to 2022L))
        syncOps.createEdge(aliceId, charlieId, "FOLLOWS", mapOf("since" to 2019L))
    }

    @TearDown
    fun teardown() {
        syncOps.close()
    }

    fun allPersons(): List<GraphVertex> =
        syncOps.findVerticesByLabel("Person")
}
