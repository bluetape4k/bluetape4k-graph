package io.bluetape4k.graph.tinkerpop

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.BatchEdge
import io.bluetape4k.junit5.coroutines.runSuspendIO
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TinkerGraphMutationIsolationTest {
    @ParameterizedTest
    @ValueSource(booleans = [false, true])
    fun `sync와 suspend transaction은 외부 mutation 전체를 거부한다`(suspending: Boolean) = runSuspendIO {
        val delegate = TinkerGraphOperations()
        val ops = TinkerGraphSuspendOperations(delegate)
        val a = delegate.createVertex("Person", mapOf("name" to "A"))
        val b = delegate.createVertex("Person", mapOf("name" to "B"))
        val edge = delegate.createEdge(a.id, b.id, "KNOWS")
        val release = CountDownLatch(1)
        try {
            withTimeout(10_000) {
                coroutineScope {
                    val started = CompletableDeferred<Unit>()
                    val transaction = async(Dispatchers.IO) {
                        assertFailsWith<IllegalStateException> {
                            if (suspending) {
                                ops.suspendTransaction {
                                    started.complete(Unit)
                                    release.await(5, TimeUnit.SECONDS).shouldBeTrue()
                                    error("rollback")
                                }
                            } else {
                                delegate.transaction {
                                    started.complete(Unit)
                                    release.await(5, TimeUnit.SECONDS).shouldBeTrue()
                                    error("rollback")
                                }
                            }
                        }
                    }
                    try {
                        started.await()
                        val mutations = mutations(delegate, a.id, b.id, edge.id)
                        mutations.forEach { mutation ->
                            assertFailsWith<IllegalStateException> { mutation() }
                                .message.shouldContain("external mutations")
                        }
                        assertFailsWith<IllegalStateException> { ops.createVertex("Person") }
                            .message.shouldContain("external mutations")
                    } finally {
                        release.countDown()
                    }
                    transaction.await()
                }
            }
            delegate.findVertexById("Person", a.id)?.properties?.get("name") shouldBeEqualTo "A"
            delegate.findEdgesByLabel("KNOWS").size shouldBeEqualTo 1
            delegate.createVertices("Person", listOf(mapOf("name" to "after")))
            delegate.countVertices("Person") shouldBeEqualTo 3L
        } finally {
            release.countDown()
            ops.close()
        }
    }

    private fun mutations(
        delegate: TinkerGraphOperations,
        fromId: GraphElementId,
        toId: GraphElementId,
        edgeId: GraphElementId,
    ): List<() -> Any?> =
        listOf<() -> Any?>(
            { delegate.createVertex("Person") },
            { delegate.createVertices("Person", listOf(mapOf("name" to "C"))) },
            { delegate.updateVertex("Person", fromId, mapOf("name" to "changed")) },
            { delegate.deleteVertex("Person", fromId) },
            { delegate.mergeVertex("Person", mapOf("name" to "A")) },
            { delegate.createEdge(fromId, toId, "KNOWS") },
            { delegate.createEdges("KNOWS", listOf(BatchEdge(fromId, toId))) },
            { delegate.deleteEdge("KNOWS", edgeId) },
            { delegate.mergeEdge(fromId, toId, "KNOWS", mapOf("key" to "value")) },
            { delegate.createGraph("other") },
            { delegate.dropGraph("tinkergraph") },
            { delegate.schemaManager().createIndex("Person", "name") },
            { delegate.close() },
        )
}
