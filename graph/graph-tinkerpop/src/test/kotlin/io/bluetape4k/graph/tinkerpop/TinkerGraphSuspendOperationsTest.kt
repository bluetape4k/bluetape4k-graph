package io.bluetape4k.graph.tinkerpop

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeGreaterOrEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldContainAll
import io.bluetape4k.assertions.shouldHaveSize
import io.bluetape4k.assertions.shouldNotBeEmpty
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.coroutines.flow.extensions.log
import io.bluetape4k.coroutines.support.log
import io.bluetape4k.graph.GraphException
import io.bluetape4k.graph.GraphQueryException
import io.bluetape4k.graph.model.DegreeOptions
import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.model.TreeTraversalOptions
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.debug
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class TinkerGraphSuspendOperationsTest: AbstractTinkerGraphTest() {

    companion object: KLoggingChannel()

    @BeforeEach
    override fun beforeEach() = runSuspendIO {
        suspendOps.createGraph("default")
        suspendOps.dropGraph("default")
    }

    // ----- 그래프 초기화 -----

    @Test
    @Order(10)
    fun `기본 current graph가 존재하면 true 반환`() = runSuspendIO {
        suspendOps.graphExists("default").shouldBeTrue()
    }

    @Test
    @Order(12)
    fun `다른 graph name으로 dropGraph하면 현재 graph를 삭제하지 않는다`() = runSuspendIO {
        suspendOps.createGraph("current")
        suspendOps.createVertex("Person", mapOf("name" to "Alice"))
        suspendOps.graphExists("current").shouldBeTrue()
        suspendOps.graphExists("other").shouldBeFalse()

        val ex = assertFailsWith<GraphQueryException> {
            suspendOps.dropGraph("other")
        }

        ex.message shouldContain "current"
        suspendOps.countVertices("Person") shouldBeEqualTo 1L
    }

    @Test
    @Order(11)
    fun `dropGraph로 전체 데이터를 삭제한다`() = runSuspendIO {
        suspendOps.createVertex("Person", mapOf("name" to "Alice"))
        suspendOps.countVertices("Person") shouldBeGreaterOrEqualTo 1L

        suspendOps.dropGraph("default")

        suspendOps.countVertices("Person") shouldBeEqualTo 0L
    }

    @Test
    @Order(253)
    fun `chunk Flow는 take 조기 종료와 iterator 예외에서 cursor를 닫는다`() = runSuspendIO {
        val alice = suspendOps.createVertex("Person", mapOf("name" to "Alice"))
        val bob = suspendOps.createVertex("Person", mapOf("name" to "Bob"))

        suspendOps.findVerticesByLabelChunked("Person", chunkSize = 1)
            .log("vertex")
            .take(1)
            .toList()
            .map { it.size } shouldBeEqualTo listOf(1)

        suspendOps.createEdge(alice.id, bob.id, "KNOWS")
        suspendOps.findEdgesByLabelChunked("KNOWS", chunkSize = 1).take(1)
            .log("edge")
            .toList()
            .map { it.size } shouldBeEqualTo listOf(1)

        var takeClosed = false
        closeAwareChunkFlow {
            closeableChunkSequence {
                closeableValues(listOf(listOf(1), listOf(2))) { takeClosed = true }
            }
        }
            .log("Chunk Flow")
            .take(1)
            .toList()

        takeClosed.shouldBeTrue()

        var cancellationClosed = false
        assertFailsWith<TimeoutCancellationException> {
            withTimeout(100.milliseconds) {
                closeAwareChunkFlow {
                    closeableChunkSequence<List<Int>> {
                        closeableValues(listOf(listOf(1), listOf(2))) { cancellationClosed = true }
                    }
                }
                    .log("Timeout")
                    .onEach { delay(1.seconds) }
                    .collect()
            }
        }

        cancellationClosed.shouldBeTrue()

        var failureClosed = false
        assertFailsWith<IllegalStateException> {
            closeAwareChunkFlow {
                closeableChunkSequence<List<Int>> {
                    closeableFailure(IllegalStateException("chunk failure")) { failureClosed = true }
                }
            }.log("ChunkFlow").toList()
        }

        failureClosed.shouldBeTrue()
    }

    // ----- 정점(Vertex) CRUD -----

    @Test
    @Order(20)
    fun `정점을 생성하면 elementId가 부여된다`() = runSuspendIO {
        val vertex = suspendOps.createVertex("Person")

        log.debug { "vertex=$vertex" }
        vertex.id.value.shouldNotBeEmpty()
        vertex.label shouldBeEqualTo "Person"
    }

    @Test
    @Order(21)
    fun `label과 properties로 정점을 생성한다`() = runSuspendIO {
        val props = mapOf("name" to "Alice", "age" to 30L)
        val vertex = suspendOps.createVertex("Person", props)

        log.debug { "vertex=$vertex" }
        vertex.label shouldBeEqualTo "Person"
        vertex.properties["name"] shouldBeEqualTo "Alice"
        vertex.properties["age"] shouldBeEqualTo 30L
    }

    @Test
    @Order(22)
    fun `id로 정점을 조회한다`() = runSuspendIO {
        val created = suspendOps.createVertex("Person", mapOf("name" to "Bob"))
        val found = suspendOps.findVertexById("Person", created.id)

        log.debug { "found=$found" }
        found.shouldNotBeNull()
        found.id shouldBeEqualTo created.id
        found.properties["name"] shouldBeEqualTo "Bob"
    }

    @Test
    @Order(23)
    fun `존재하지 않는 id로 조회하면 null 반환`() = runSuspendIO {
        val fakeId = GraphElementId.of("99999999")
        val result = suspendOps.findVertexById("Person", fakeId)
        result.shouldBeNull()
    }

    @Test
    @Order(231)
    fun `malformed id는 suspend repository와 traversal에서 GraphQueryException으로 거부한다`() = runSuspendIO {
        val malformedId = GraphElementId.of("not-a-number")
        val missingId = GraphElementId.of("99999999")
        val operations: List<suspend () -> Any?> = listOf(
            { suspendOps.findVertexById("Person", malformedId) },
            { suspendOps.findVertexById(malformedId) },
            { suspendOps.updateVertex("Person", malformedId, emptyMap()) },
            { suspendOps.deleteVertex("Person", malformedId) },
            { suspendOps.findEdgesByStartId(malformedId).toList() },
            { suspendOps.findEdgesByEndId(malformedId).toList() },
            { suspendOps.deleteEdge("KNOWS", malformedId) },
            { suspendOps.neighbors(malformedId, NeighborOptions()).toList() },
            { suspendOps.shortestPath(malformedId, missingId, PathOptions()) },
            { suspendOps.shortestPath(malformedId, missingId, PathOptions(weightProperty = "weight")) },
            { suspendOps.aStarPath(malformedId, missingId, PathOptions(weightProperty = "weight")) { 0.0 } },
            { suspendOps.allPaths(malformedId, missingId, PathOptions()).toList() },
            { suspendOps.degreeCentrality(malformedId, DegreeOptions()) },
            { suspendOps.bfs(malformedId, TreeTraversalOptions()).toList() },
            { suspendOps.dfs(malformedId, TreeTraversalOptions()).toList() },
        )

        operations.forEach { operation ->
            val failure = assertFailsWith<GraphQueryException> {
                operation()
            }
            failure.message shouldContain "numeric ID"
        }
    }

    @Test
    @Order(232)
    fun `valid but missing numeric id는 suspend absence semantics를 유지한다`() = runSuspendIO {
        val missingId = GraphElementId.of("99999999")

        suspendOps.findVertexById("Person", missingId).shouldBeNull()
        suspendOps.updateVertex("Person", missingId, emptyMap()).shouldBeNull()
        suspendOps.deleteVertex("Person", missingId).shouldBeFalse()
        suspendOps.findEdgesByStartId(missingId).log("byStartId").toList().shouldBeEmpty()
        suspendOps.findEdgesByEndId(missingId).log("byEndId").toList().shouldBeEmpty()
        suspendOps.deleteEdge("KNOWS", missingId).shouldBeFalse()
        suspendOps.neighbors(missingId, NeighborOptions()).log("neighbors").toList().shouldBeEmpty()
        suspendOps.shortestPath(missingId, missingId, PathOptions()).shouldBeNull()
        suspendOps.allPaths(missingId, missingId, PathOptions()).log("all").toList().shouldBeEmpty()
    }

    @Test
    @Order(24)
    fun `label로 정점 목록을 조회한다`() = runSuspendIO {
        suspendOps.createVertex("Person", mapOf("name" to "Alice"))
        suspendOps.createVertex("Person", mapOf("name" to "Bob"))
        suspendOps.createVertex("Car", mapOf("model" to "Tesla"))

        val persons = suspendOps.findVerticesByLabel("Person").log("ByLabel").toList()
        persons shouldHaveSize 2
        persons.all { it.label == "Person" }.shouldBeTrue()
    }

    @Test
    @Order(25)
    fun `filter 조건으로 정점을 조회한다`() = runSuspendIO {
        suspendOps.createVertex("Person", mapOf("name" to "Alice", "city" to "Seoul"))
        suspendOps.createVertex("Person", mapOf("name" to "Bob", "city" to "Busan"))

        val result = suspendOps.findVerticesByLabel("Person", mapOf("city" to "Seoul")).log("byLabel").toList()
        result shouldHaveSize 1
        result.single().properties["name"] shouldBeEqualTo "Alice"
    }

    @Test
    @Order(26)
    fun `정점의 properties를 업데이트한다`() = runSuspendIO {
        val vertex = suspendOps.createVertex("Person", mapOf("name" to "Charlie", "age" to 25L))
        val updated = suspendOps.updateVertex("Person", vertex.id, mapOf("age" to 26L))

        log.debug { "updated=$updated" }
        updated.shouldNotBeNull()
        updated.id shouldBeEqualTo vertex.id
        updated.properties["age"] shouldBeEqualTo 26L
    }

    @Test
    @Order(27)
    fun `정점을 삭제한다`() = runSuspendIO {
        val vertex = suspendOps.createVertex("Person", mapOf("name" to "Dave"))
        suspendOps.deleteVertex("Person", vertex.id).shouldBeTrue()
        suspendOps.findVertexById("Person", vertex.id).shouldBeNull()
    }

    @Test
    @Order(28)
    fun `정점 개수를 조회한다`() = runSuspendIO {
        suspendOps.createVertex("Person", mapOf("name" to "Alice"))
        suspendOps.createVertex("Person", mapOf("name" to "Bob"))

        suspendOps.countVertices("Person") shouldBeEqualTo 2L
    }

    @Test
    @Order(29)
    fun `suspendTransaction은 성공 시 생성한 정점과 간선을 commit한다`() = runSuspendIO {
        val edge = suspendOps.suspendTransaction {
            val alice = createVertex("Person", mapOf("name" to "Alice"))
            val bob = createVertex("Person", mapOf("name" to "Bob"))
            createEdge(alice.id, bob.id, "KNOWS")
        }
        log.debug { "edge=$edge" }
        edge.label shouldBeEqualTo "KNOWS"

        suspendOps.countVertices("Person") shouldBeEqualTo 2L
        suspendOps.findEdgesByLabel("KNOWS").log("KNOWS").toList() shouldHaveSize 1
    }

    @Test
    @Order(30)
    fun `suspendTransaction은 실패 시 생성한 정점과 간선을 rollback한다`() = runSuspendIO {
        val existing = suspendOps.createVertex("Person", mapOf("name" to "Existing"))

        assertFailsWith<IllegalStateException> {
            suspendOps.suspendTransaction {
                val alice = createVertex("Person", mapOf("name" to "Alice"))
                val bob = createVertex("Person", mapOf("name" to "Bob"))
                createEdge(alice.id, bob.id, "KNOWS")
                error("rollback")
            }
        }

        suspendOps.findVertexById("Person", existing.id)?.properties?.get("name") shouldBeEqualTo "Existing"
        suspendOps.countVertices("Person") shouldBeEqualTo 1L
        suspendOps.findEdgesByLabel("KNOWS").log("KNOWS").toList().shouldBeEmpty()
    }

    @Test
    @Order(31)
    fun `suspendTransaction은 취소 시 빠르게 반환하고 rollback한다`() = runSuspendIO {
        val existing = suspendOps.createVertex("Person", mapOf("name" to "Existing"))

        assertFailsWith<TimeoutCancellationException> {
            withTimeout(500.milliseconds) {
                suspendOps.suspendTransaction {
                    createVertex("Person", mapOf("name" to "Cancelled"))
                    awaitCancellation()
                }
            }
        }

        suspendOps.findVertexById("Person", existing.id)?.properties?.get("name") shouldBeEqualTo "Existing"
        suspendOps.countVertices("Person") shouldBeEqualTo 1L

        val afterCancellation = suspendOps.createVertex("Person", mapOf("name" to "AfterCancellation"))
        log.debug { "afterCancellation=$afterCancellation" }
        afterCancellation.properties["name"] shouldBeEqualTo "AfterCancellation"

        suspendOps.countVertices("Person") shouldBeEqualTo 2L
    }

    @Test
    @Order(32)
    fun `suspendTransaction은 반환된 Flow를 commit 전에 materialize한다`() = runSuspendIO {
        val people = suspendOps.suspendTransaction {
            createVertex("Person", mapOf("name" to "Alice"))
            findVerticesByLabel("Person")
        }

        val names = people.log("Person").toList().map { it.properties["name"] }
        log.debug { "names=$names" }
        names shouldContain "Alice"
    }

    @Test
    @Order(330)
    fun `suspendTransaction은 nested Flow 반환을 명시적으로 거부한다`() = runSuspendIO {
        val ex = assertFailsWith<GraphException> {
            suspendOps.suspendTransaction<Pair<String, Flow<*>>> {
                createVertex("Person", mapOf("name" to "Alice"))
                "Person" to findVerticesByLabel("Person")
            }
        }
        log.debug { "ex=$ex" }
        ex.message shouldContain "nested Flow"
        ex.message shouldContain "result.second"
        suspendOps.countVertices("Person") shouldBeEqualTo 0L
    }

    @Test
    @Order(33)
    fun `suspendTransaction은 동기 transaction과 rollback snapshot을 직렬화한다`() = runSuspendIO {
        val delegate = TinkerGraphOperations()
        val suspendOps = TinkerGraphSuspendOperations(delegate)
        try {
            withTimeout(5.seconds) {
                coroutineScope {
                    val started = CompletableDeferred<Unit>()
                    val release = CompletableDeferred<Unit>()
                    val suspendTx = async(Dispatchers.IO) {
                        assertFailsWith<IllegalStateException> {
                            suspendOps.suspendTransaction {
                                createVertex("Person", mapOf("name" to "Suspending"))
                                started.complete(Unit)
                                release.await()
                                error("rollback")
                            }
                        }
                    }

                    withTimeout(1.seconds) { started.await() }

                    val syncTx = async(Dispatchers.IO) {
                        delegate.transaction {
                            createVertex("Person", mapOf("name" to "Sync"))
                        }
                    }.log("Tx")

                    delay(100.milliseconds)
                    release.complete(Unit)
                    suspendTx.await()
                    syncTx.await()
                }
            }

            val names = delegate.findVerticesByLabel("Person").map { it.properties["name"] }
            names shouldContain "Sync"
            delegate.countVertices("Person") shouldBeEqualTo 1L
        } finally {
            suspendOps.close()
        }
    }

    @Test
    @Order(332)
    fun `suspendTransaction은 transaction 외부의 graph와 schema mutation을 거부한다`() = runSuspendIO {
        val delegate = TinkerGraphOperations()
        val suspendOps = TinkerGraphSuspendOperations(delegate)
        try {
            withTimeout(5.seconds) {
                coroutineScope {
                    val transactionStarted = CompletableDeferred<Unit>()
                    val releaseTransaction = CompletableDeferred<Unit>()
                    val transaction = async(Dispatchers.IO) {
                        assertFailsWith<IllegalStateException> {
                            suspendOps.suspendTransaction {
                                createVertex("Person", mapOf("name" to "transaction"))
                                transactionStarted.complete(Unit)
                                releaseTransaction.await()
                                error("rollback")
                            }
                        }
                    }.log("Tx")

                    withTimeout(1.seconds) {
                        transactionStarted.await()
                    }
                    val externalMutation = async(Dispatchers.IO) {
                        assertFailsWith<IllegalStateException> {
                            delegate.createVertex("Person", mapOf("name" to "external"))
                        }
                    }.log("External Mutation")

                    val externalFailure = withTimeout(1.seconds) {
                        externalMutation.await()
                    }
                    externalFailure.message shouldContain "external mutations"

                    val externalSchemaMutation = async(Dispatchers.IO) {
                        assertFailsWith<IllegalStateException> {
                            delegate.schemaManager().createIndex("Person", "email")
                        }
                    }.log("Extenal Schema Mutation")

                    val schemaFailure = withTimeout(1.seconds) {
                        externalSchemaMutation.await()
                    }
                    log.debug { "schemaFailure=$schemaFailure" }
                    schemaFailure.message shouldContain "external mutations"

                    releaseTransaction.complete(Unit)
                    transaction.await()

                    val externalVertex = delegate.createVertex("Person", mapOf("name" to "external"))
                    delegate.findVertexById("Person", externalVertex.id)
                        ?.properties
                        ?.get("name") shouldBeEqualTo "external"
                    delegate.countVertices("Person") shouldBeEqualTo 1L
                    delegate.schemaManager().createIndex("Person", "email")
                    delegate.schemaManager().dropIndex("Person", "email")
                }
            }
        } finally {
            suspendOps.close()
        }
    }

    @Test
    @Order(333)
    fun `nested suspendTransaction reuses the active transaction`() = runSuspendIO {
        withTimeout(5.seconds) {
            suspendOps.suspendTransaction {
                createVertex("Person", mapOf("name" to "Outer"))
                suspendOps.suspendTransaction {
                    createVertex("Person", mapOf("name" to "Inner"))
                }
            }
        }

        suspendOps.findVerticesByLabel("Person").log("Person").toList() shouldHaveSize 2
    }

    // ----- 간선(Edge) CRUD -----

    @Test
    @Order(34)
    fun `두 정점 사이에 간선을 생성한다`() = runSuspendIO {
        val alice = suspendOps.createVertex("Person", mapOf("name" to "Alice"))
        val bob = suspendOps.createVertex("Person", mapOf("name" to "Bob"))

        val edge = suspendOps.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to 2020L))

        log.debug { "edge=$edge" }
        edge.label shouldBeEqualTo "KNOWS"
        edge.startId shouldBeEqualTo alice.id
        edge.endId shouldBeEqualTo bob.id
        edge.properties["since"] shouldBeEqualTo 2020L
    }

    @Test
    @Order(35)
    fun `label로 간선 목록을 조회한다`() = runSuspendIO {
        val a = suspendOps.createVertex("Person", mapOf("name" to "A"))
        val b = suspendOps.createVertex("Person", mapOf("name" to "B"))
        val c = suspendOps.createVertex("Person", mapOf("name" to "C"))

        suspendOps.createEdge(a.id, b.id, "KNOWS")
        suspendOps.createEdge(b.id, c.id, "KNOWS")
        suspendOps.createEdge(a.id, c.id, "FOLLOWS")

        val knowsEdges = suspendOps.findEdgesByLabel("KNOWS").log("KNOWS").toList()
        knowsEdges shouldHaveSize 2
        knowsEdges.all { it.label == "KNOWS" }.shouldBeTrue()
    }

    @Test
    @Order(36)
    fun `간선을 삭제한다`() = runSuspendIO {
        val a = suspendOps.createVertex("Person", mapOf("name" to "A"))
        val b = suspendOps.createVertex("Person", mapOf("name" to "B"))
        val edge = suspendOps.createEdge(a.id, b.id, "KNOWS")

        val deleted = suspendOps.deleteEdge("KNOWS", edge.id)
        deleted.shouldBeTrue()

        suspendOps.findEdgesByLabel("KNOWS").log("KNOWS").toList().shouldBeEmpty()
    }

    // ----- 그래프 탐색 (Traversal) -----

    @Test
    @Order(40)
    fun `이웃 정점을 조회한다 - OUTGOING`() = runSuspendIO {
        val alice = suspendOps.createVertex("Person", mapOf("name" to "Alice"))
        val bob = suspendOps.createVertex("Person", mapOf("name" to "Bob"))
        val carol = suspendOps.createVertex("Person", mapOf("name" to "Carol"))

        suspendOps.createEdge(alice.id, bob.id, "KNOWS")
        suspendOps.createEdge(alice.id, carol.id, "KNOWS")

        val neighbors = suspendOps
            .neighbors(
                alice.id,
                NeighborOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING)
            )
            .log("Neighbors")
            .toList()
        neighbors shouldHaveSize 2

        val names = neighbors.map { it.properties["name"] }
        log.debug { "names=$names" }
        names shouldContainAll listOf("Bob", "Carol")
    }

    @Test
    @Order(41)
    fun `이웃 정점을 조회한다 - INCOMING`() = runSuspendIO {
        val alice = suspendOps.createVertex("Person", mapOf("name" to "Alice"))
        val bob = suspendOps.createVertex("Person", mapOf("name" to "Bob"))
        val carol = suspendOps.createVertex("Person", mapOf("name" to "Carol"))

        suspendOps.createEdge(bob.id, alice.id, "KNOWS")
        suspendOps.createEdge(carol.id, alice.id, "KNOWS")

        val neighbors = suspendOps
            .neighbors(
                alice.id,
                NeighborOptions(edgeLabel = "KNOWS", direction = Direction.INCOMING)
            )
            .log("Neighbors")
            .toList()
        neighbors shouldHaveSize 2

        val names = neighbors.map { it.properties["name"] }
        log.debug { "names=$names" }
        names shouldContainAll listOf("Bob", "Carol")
    }

    @Test
    @Order(42)
    fun `이웃 정점을 조회한다 - BOTH`() = runSuspendIO {
        val alice = suspendOps.createVertex("Person", mapOf("name" to "Alice"))
        val bob = suspendOps.createVertex("Person", mapOf("name" to "Bob"))
        val carol = suspendOps.createVertex("Person", mapOf("name" to "Carol"))

        suspendOps.createEdge(alice.id, bob.id, "KNOWS")
        suspendOps.createEdge(carol.id, alice.id, "KNOWS")

        val neighbors = suspendOps
            .neighbors(
                alice.id,
                NeighborOptions(edgeLabel = "KNOWS", direction = Direction.BOTH)
            )
            .log("Neighbors")
            .toList()
        neighbors shouldHaveSize 2

        val names = neighbors.map { it.properties["name"] }
        log.debug { "names=$names" }
        names shouldContainAll listOf("Bob", "Carol")
    }

    @Test
    @Order(43)
    fun `depth=2로 2단계 이웃을 조회한다`() = runSuspendIO {
        val a = suspendOps.createVertex("Person", mapOf("name" to "A"))
        val b = suspendOps.createVertex("Person", mapOf("name" to "B"))
        val c = suspendOps.createVertex("Person", mapOf("name" to "C"))

        suspendOps.createEdge(a.id, b.id, "KNOWS")
        suspendOps.createEdge(b.id, c.id, "KNOWS")

        val neighbors = suspendOps
            .neighbors(
                a.id,
                NeighborOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING, maxDepth = 2)
            )
            .log("Neighbors")
            .toList()
        neighbors.shouldNotBeEmpty()

        val names = neighbors.map { it.properties["name"] }
        log.debug { "names=$names" }
        names shouldContainAll listOf("B", "C")
    }

    @Test
    @Order(50)
    fun `최단 경로를 탐색한다`() = runSuspendIO {
        val a = suspendOps.createVertex("Person", mapOf("name" to "A"))
        val b = suspendOps.createVertex("Person", mapOf("name" to "B"))
        val c = suspendOps.createVertex("Person", mapOf("name" to "C"))

        suspendOps.createEdge(a.id, b.id, "KNOWS")
        suspendOps.createEdge(b.id, c.id, "KNOWS")

        val path = suspendOps.shortestPath(a.id, c.id, PathOptions(edgeLabel = "KNOWS"))

        log.debug { "path=$path" }
        path.shouldNotBeNull()
        path.vertices.shouldNotBeEmpty()
        path.edges shouldHaveSize 2
        path.length shouldBeEqualTo 2
    }

    @Test
    @Order(51)
    fun `연결되지 않은 경우 shortestPath는 null 반환`() = runSuspendIO {
        val a = suspendOps.createVertex("Person", mapOf("name" to "A"))
        val b = suspendOps.createVertex("Person", mapOf("name" to "B"))

        // 간선 없음
        val path = suspendOps.shortestPath(a.id, b.id, PathOptions(edgeLabel = "KNOWS"))
        path.shouldBeNull()
    }

    @Test
    @Order(52)
    fun `모든 경로를 탐색한다`() = runSuspendIO {
        val a = suspendOps.createVertex("Person", mapOf("name" to "A"))
        val b = suspendOps.createVertex("Person", mapOf("name" to "B"))
        val c = suspendOps.createVertex("Person", mapOf("name" to "C"))

        suspendOps.createEdge(a.id, b.id, "KNOWS")
        suspendOps.createEdge(b.id, c.id, "KNOWS")
        suspendOps.createEdge(a.id, c.id, "KNOWS")

        val paths = suspendOps
            .allPaths(
                a.id,
                c.id,
                PathOptions(edgeLabel = "KNOWS")
            )
            .log("Paths")
            .toList()

        paths.shouldNotBeEmpty()
        paths.size shouldBeGreaterOrEqualTo 2
    }
}

private fun <T> closeableValues(
    values: List<T>,
    onClose: () -> Unit,
): CloseableChunkIterator<T> = object: CloseableChunkIterator<T> {
    private val delegate = values.iterator()
    private var closed = false

    override fun hasNext(): Boolean = !closed && delegate.hasNext()

    override fun next(): T = delegate.next()

    override fun close() {
        if (!closed) {
            closed = true
            onClose()
        }
    }
}

private fun <T> closeableFailure(
    failure: Throwable,
    onClose: () -> Unit,
): CloseableChunkIterator<T> = object: CloseableChunkIterator<T> {
    private var closed = false

    override fun hasNext(): Boolean {
        if (closed) return false
        throw failure
    }

    override fun next(): T = throw failure

    override fun close() {
        if (!closed) {
            closed = true
            onClose()
        }
    }
}
