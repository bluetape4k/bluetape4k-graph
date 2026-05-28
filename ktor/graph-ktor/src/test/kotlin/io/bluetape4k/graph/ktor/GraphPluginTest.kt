package io.bluetape4k.graph.ktor

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
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
            syncResponse.status shouldBeEqualTo HttpStatusCode.OK
            syncResponse.bodyAsText() shouldBeEqualTo "0"

            val suspendResponse = client.get("/suspend-count")
            suspendResponse.status shouldBeEqualTo HttpStatusCode.OK
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
    fun `backend helper 는 blank 입력을 fail fast 한다`() {
        assertFailsWith<IllegalArgumentException> {
            GraphPluginConfig().age(" ")
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
