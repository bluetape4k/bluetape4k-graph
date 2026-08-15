package io.bluetape4k.graph.ktor

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.ktor.testing.shouldHaveStatus
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class GraphPluginTest {

    @Test
    fun `backend 미선택 시 install 시점에 IllegalArgumentException 이 발생한다`() = runSuspendIO {
        assertFailsWith<IllegalArgumentException> {
            testApplication {
                application {
                    install(GraphPlugin) {
                        // backend 의도적으로 미설정
                    }
                }
                startApplication()
            }
        }
    }

    @Test
    fun `정상 설치 시 Application extension 으로 operations 에 접근할 수 있다`() = runSuspendIO {
        testApplication {
            application {
                install(GraphPlugin) {
                    tinkerGraph()
                }

                graphPluginState().graphOperations.countVertices("City") shouldBeEqualTo 0L
                graphOperations().countVertices("City") shouldBeEqualTo 0L
                graphSuspendOperations().countVertices("City") shouldBeEqualTo 0L
            }
            startApplication()
        }
    }

    @Test
    fun `플러그인 미설치 상태에서 extension 호출 시 IllegalStateException`() = runSuspendIO {
        assertFailsWith<IllegalStateException> {
            testApplication {
                application {
                    graphOperations()
                }
                startApplication()
            }
        }
    }

    @Test
    fun `route handler 에서 ApplicationCall extension 으로 operations 에 접근할 수 있다`() = runSuspendIO {
        testApplication {
            application {
                install(GraphPlugin) {
                    tinkerGraph()
                }
                routing {
                    get("/sync-count") {
                        call.respondText(call.graphOperations().countVertices("City").toString())
                    }
                    get("/suspend-count") {
                        call.respondText(call.graphSuspendOperations().countVertices("City").toString())
                    }
                }
            }
            startApplication()

            val syncResponse = client.get("/sync-count")
            syncResponse shouldHaveStatus HttpStatusCode.OK
            syncResponse.bodyAsText() shouldBeEqualTo "0"

            val suspendResponse = client.get("/suspend-count")
            suspendResponse shouldHaveStatus HttpStatusCode.OK
            suspendResponse.bodyAsText() shouldBeEqualTo "0"
        }
    }

    @Test
    fun `close action 하나가 실패해도 나머지 close action 을 계속 실행한다`() = runSuspendIO {
        val syncClosed = AtomicBoolean(false)
        val suspendClosed = AtomicBoolean(false)

        val syncDelegate = TinkerGraphOperations()
        val suspendDelegate = TinkerGraphSuspendOperations(TinkerGraphOperations())
        val syncOps = ThrowingGraphOperations(syncDelegate, syncClosed)
        val suspendOps = CountingGraphSuspendOperations(suspendDelegate, suspendClosed)

        testApplication {
            application {
                install(GraphPlugin) {
                    operations(syncOps, suspendOps, closeOnStop = true)
                }
            }
            startApplication()
        }

        syncClosed.get().shouldBeTrue()
        suspendClosed.get().shouldBeTrue()
    }

    @Test
    fun `중복 backend 구성 실패 시 이미 생성한 resource close action 을 rollback 한다`() {
        val rollbackCount = AtomicInteger(0)
        val config = GraphPluginConfig().apply { tinkerGraph() }

        assertFailsWith<IllegalArgumentException> {
            config.configure(
                backendName = "managedNeo4j",
                graphOperationsFactory = { mockk() },
                graphSuspendOperationsFactory = { mockk() },
                closeActions = listOf(
                    GraphPluginCloseAction("managed resource") {
                        rollbackCount.incrementAndGet()
                    },
                ),
            )
        }

        rollbackCount.get() shouldBeEqualTo 1
    }

    @Test
    fun `GraphPluginState close 는 반복 호출해도 close action 을 한 번만 실행한다`() {
        val closeCount = AtomicInteger(0)
        val state = GraphPluginState(
            graphOperations = mockk(),
            graphSuspendOperations = mockk(),
            closeActions = listOf(
                GraphPluginCloseAction("managed resource") {
                    closeCount.incrementAndGet()
                },
            ),
        )

        state.close()
        state.close()

        closeCount.get() shouldBeEqualTo 1
    }

    @Test
    fun `backend helper 는 blank 입력을 fail fast 한다`() {
        assertFailsWith<IllegalArgumentException> {
            GraphPluginConfig().age(mockk(), graphName = " ")
        }

        assertFailsWith<IllegalArgumentException> {
            GraphPluginConfig().neo4j(mockk(), database = " ")
        }

        assertFailsWith<IllegalArgumentException> {
            GraphPluginConfig().memgraph(mockk(), database = " ")
        }

        assertFailsWith<IllegalArgumentException> {
            GraphPluginConfig().falkorDB(mockk(), graphName = " ")
        }
    }

    @Test
    fun `caller owned backend helper 는 close action 을 등록하지 않는다`() {
        GraphPluginConfig()
            .neo4j(mockk(), database = "neo4j")
            .closeActions.size shouldBeEqualTo 0

        GraphPluginConfig()
            .memgraph(mockk(), database = "memgraph")
            .closeActions.size shouldBeEqualTo 0

        GraphPluginConfig()
            .falkorDB(mockk(), graphName = "graph")
            .closeActions.size shouldBeEqualTo 0

        GraphPluginConfig()
            .age(mockk(), graphName = "graph")
            .closeActions.size shouldBeEqualTo 0
    }

    @Test
    fun `managed backend DSL 은 plugin owned close action 을 등록한다`() {
        val neo4jConfig = GraphPluginConfig().neo4j {
            uri = "bolt://localhost:7687"
        }
        neo4jConfig.closeActions.size shouldBeEqualTo 3
        neo4jConfig.resolveState().close()

        val memgraphConfig = GraphPluginConfig().memgraph {
            uri = "bolt://localhost:7687"
        }
        memgraphConfig.closeActions.size shouldBeEqualTo 3
        memgraphConfig.resolveState().close()
    }

    @Test
    fun `managed backend DSL 은 잘못된 property 를 fail fast 한다`() {
        assertFailsWith<IllegalArgumentException> {
            GraphPluginConfig().neo4j {
                uri = " "
            }
        }

        assertFailsWith<IllegalArgumentException> {
            GraphPluginConfig().memgraph {
                database = " "
            }
        }

        assertFailsWith<IllegalArgumentException> {
            GraphPluginConfig().falkorDB {
                host = " "
            }
        }

        assertFailsWith<IllegalArgumentException> {
            GraphPluginConfig().falkorDB {
                port = 0
            }
        }

        assertFailsWith<IllegalArgumentException> {
            GraphPluginConfig().ageDataSource {
                jdbcUrl = " "
            }
        }

        assertFailsWith<IllegalArgumentException> {
            GraphPluginConfig().ageDataSource {
                graphName = " "
            }
        }

        assertFailsWith<IllegalArgumentException> {
            GraphPluginConfig().ageDataSource {
                maximumPoolSize = 0
            }
        }
    }

    private class ThrowingGraphOperations(
        private val delegate: GraphOperations,
        private val closed: AtomicBoolean,
    ): GraphOperations by delegate {
        override fun close() {
            closed.set(true)
            throw IllegalStateException("expected close failure")
        }
    }

    private class CountingGraphSuspendOperations(
        private val delegate: GraphSuspendOperations,
        private val closed: AtomicBoolean,
    ): GraphSuspendOperations by delegate {
        override fun close() {
            closed.set(true)
            delegate.close()
        }
    }
}
