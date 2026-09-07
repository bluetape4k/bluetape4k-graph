package io.bluetape4k.graph.ktor

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.ktor.core.ApplicationResourceCloseReport
import io.bluetape4k.ktor.core.ApplicationResourceClosePhase
import io.bluetape4k.ktor.core.ApplicationResourceRegistry
import io.bluetape4k.ktor.core.ApplicationResourceRegistryState
import io.bluetape4k.ktor.core.installApplicationResourceLifecycle
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
        lateinit var registry: ApplicationResourceRegistry

        testApplication {
            application {
                install(GraphPlugin) {
                    operations(syncOps, suspendOps, closeOnStop = true)
                }
                registry = installApplicationResourceLifecycle()
            }
            startApplication()
        }

        syncClosed.get().shouldBeTrue()
        suspendClosed.get().shouldBeTrue()
        registry.closeReport.state shouldBeEqualTo ApplicationResourceRegistryState.CLOSED
        registry.closeReport.attempted shouldBeEqualTo 1
        registry.closeReport.closed shouldBeEqualTo 0
        registry.closeReport.failures.size shouldBeEqualTo 1
        registry.closeReport.failures.single().phase shouldBeEqualTo ApplicationResourceClosePhase.SHUTDOWN
    }

    @Test
    fun `fatal Graph close 실패는 공통 report에 fatal로 보존한다`() {
        val laterActionClosed = AtomicBoolean(false)
        val state = GraphPluginState(
            graphOperations = mockk(),
            graphSuspendOperations = mockk(),
            closeActions = listOf(
                GraphPluginCloseAction("fatal") { throw AssertionError("sensitive detail") },
                GraphPluginCloseAction("later") { laterActionClosed.set(true) },
            ),
        )
        val registry = ApplicationResourceRegistry()
        state.registerCloseActions(registry)

        val marker = assertFailsWith<Error> {
            registry.close()
        }

        laterActionClosed.get().shouldBeTrue()
        marker.cause.shouldBeNull()
        registry.closeReport.failures.single().fatal.shouldBeTrue()
    }

    @Test
    fun `GraphPlugin 소유 close action 은 공통 application resource registry 에 등록된다`() = runSuspendIO {
        val closeCount = AtomicInteger(0)
        val syncOps = CountingGraphOperations(TinkerGraphOperations(), closeCount)
        val suspendOps = CountingGraphSuspendOperations(
            TinkerGraphSuspendOperations(TinkerGraphOperations()),
            AtomicBoolean(false),
            closeCount,
        )
        lateinit var registry: ApplicationResourceRegistry

        testApplication {
            application {
                install(GraphPlugin) {
                    operations(syncOps, suspendOps, closeOnStop = true)
                }
                registry = installApplicationResourceLifecycle()
            }
            startApplication()
        }

        closeCount.get() shouldBeEqualTo 2
        registry.closeReport shouldBeEqualTo ApplicationResourceCloseReport(
            state = ApplicationResourceRegistryState.CLOSED,
            attempted = 1,
            inFlight = 0,
            closed = 1,
            failures = emptyList(),
        )
    }

    @Test
    fun `caller owned operations 는 공통 application resource registry 에 등록하지 않는다`() = runSuspendIO {
        val closeCount = AtomicInteger(0)
        val suspendClosed = AtomicBoolean(false)
        val syncOps = CountingGraphOperations(TinkerGraphOperations(), closeCount)
        val suspendOps = CountingGraphSuspendOperations(
            TinkerGraphSuspendOperations(TinkerGraphOperations()),
            suspendClosed,
            closeCount,
        )
        lateinit var registry: ApplicationResourceRegistry

        testApplication {
            application {
                install(GraphPlugin) {
                    operations(syncOps, suspendOps)
                }
                registry = installApplicationResourceLifecycle()
            }
            startApplication()
        }

        closeCount.get() shouldBeEqualTo 0
        suspendClosed.get().shouldBeFalse()
        registry.closeReport shouldBeEqualTo ApplicationResourceCloseReport(
            state = ApplicationResourceRegistryState.CLOSED,
            attempted = 0,
            inFlight = 0,
            closed = 0,
            failures = emptyList(),
        )

        syncOps.close()
        suspendOps.close()
    }

    @Test
    fun `종료된 공통 registry 에 등록하면 기존 Graph close 순서로 즉시 닫는다`() {
        val closed = mutableListOf<String>()
        val state = GraphPluginState(
            graphOperations = mockk(),
            graphSuspendOperations = mockk(),
            closeActions = listOf(
                GraphPluginCloseAction("first") { closed += "first" },
                GraphPluginCloseAction("second") { closed += "second" },
            ),
        )
        val registry = ApplicationResourceRegistry().apply { close() }

        state.registerCloseActions(registry)

        closed shouldBeEqualTo listOf("first", "second")
        registry.closeReport shouldBeEqualTo ApplicationResourceCloseReport(
            state = ApplicationResourceRegistryState.CLOSED,
            attempted = 1,
            inFlight = 0,
            closed = 1,
            failures = emptyList(),
        )
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
    fun `실패한 close action 만 다음 close 에서 재시도한다`() {
        val retryAttempts = AtomicInteger(0)
        val successfulCloseCount = AtomicInteger(0)
        val state = GraphPluginState(
            graphOperations = mockk(),
            graphSuspendOperations = mockk(),
            closeActions = listOf(
                GraphPluginCloseAction("retryable resource") {
                    if (retryAttempts.incrementAndGet() == 1) {
                        throw IllegalStateException("first close failure")
                    }
                },
                GraphPluginCloseAction("successful resource") {
                    successfulCloseCount.incrementAndGet()
                },
            ),
        )

        state.close()
        state.close()

        retryAttempts.get() shouldBeEqualTo 2
        successfulCloseCount.get() shouldBeEqualTo 1
    }

    @Test
    fun `동시 close 는 action 을 중복 실행하지 않고 실패 후 재시도를 허용한다`() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val attempts = AtomicInteger(0)
        val inFlight = AtomicInteger(0)
        val maxInFlight = AtomicInteger(0)
        val closeAction = GraphPluginCloseAction("concurrent resource") {
            val attempt = attempts.incrementAndGet()
            val active = inFlight.incrementAndGet()
            maxInFlight.accumulateAndGet(active, ::maxOf)
            try {
                if (attempt == 1) {
                    entered.countDown()
                    release.await(5, TimeUnit.SECONDS).shouldBeTrue()
                    throw IllegalStateException("first close failure")
                }
            } finally {
                inFlight.decrementAndGet()
            }
        }

        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val first = executor.submit<Throwable?> {
                runCatching { closeAction.close() }.exceptionOrNull()
            }
            entered.await(5, TimeUnit.SECONDS).shouldBeTrue()
            val concurrent = executor.submit<Throwable?> {
                runCatching { closeAction.close() }.exceptionOrNull()
            }

            val concurrentError = concurrent.get()
            release.countDown()
            concurrentError shouldBeEqualTo null
            first.get()?.message shouldBeEqualTo "first close failure"
        }

        closeAction.close()

        attempts.get() shouldBeEqualTo 2
        maxInFlight.get() shouldBeEqualTo 1
    }

    @Test
    fun `동시 GraphPluginState close 는 하나의 action pass 로 합친다`() {
        val firstActionEntered = CountDownLatch(1)
        val releaseFirstAction = CountDownLatch(1)
        val secondActionCount = AtomicInteger(0)
        val state = GraphPluginState(
            graphOperations = mockk(),
            graphSuspendOperations = mockk(),
            closeActions = listOf(
                GraphPluginCloseAction("first resource") {
                    firstActionEntered.countDown()
                    releaseFirstAction.await(5, TimeUnit.SECONDS).shouldBeTrue()
                },
                GraphPluginCloseAction("second resource") {
                    secondActionCount.incrementAndGet()
                },
            ),
        )

        Executors.newVirtualThreadPerTaskExecutor().use { executor ->
            val first = executor.submit { state.close() }
            firstActionEntered.await(5, TimeUnit.SECONDS).shouldBeTrue()
            val concurrent = executor.submit { state.close() }

            concurrent.get(5, TimeUnit.SECONDS)
            val countBeforeRelease = secondActionCount.get()
            releaseFirstAction.countDown()
            first.get(5, TimeUnit.SECONDS)

            countBeforeRelease shouldBeEqualTo 0
        }

        secondActionCount.get() shouldBeEqualTo 1
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

    private class CountingGraphOperations(
        private val delegate: GraphOperations,
        private val closeCount: AtomicInteger,
    ): GraphOperations by delegate {
        override fun close() {
            closeCount.incrementAndGet()
            delegate.close()
        }
    }

    private class CountingGraphSuspendOperations(
        private val delegate: GraphSuspendOperations,
        private val closed: AtomicBoolean,
        private val closeCount: AtomicInteger? = null,
    ): GraphSuspendOperations by delegate {
        override fun close() {
            closed.set(true)
            closeCount?.incrementAndGet()
            delegate.close()
        }
    }
}
