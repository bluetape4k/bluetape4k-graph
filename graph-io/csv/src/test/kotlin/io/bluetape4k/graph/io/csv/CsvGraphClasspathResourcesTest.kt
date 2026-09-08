package io.bluetape4k.graph.io.csv

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeFalse
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.junit5.coroutines.runSuspendIO
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class CsvGraphClasspathResourcesTest {
    @Test
    fun `TCCL 우선으로 한 번씩 열고 callback 종료 후 역순으로 닫는다`() {
        val fixture = Fixture()
        val fallback = Loader { error("fallback must not run") }
        withLoader(fixture.loader) {
            withClasspathCsvGraphImportSource("vertices", "edges", fallback) { source ->
                (source.vertices as GraphImportSource.InputStreamSource).closeInput.shouldBeFalse()
                (source.edges as GraphImportSource.InputStreamSource).closeInput.shouldBeFalse()
                source.vertices.input.read() shouldBeEqualTo 65
                "done"
            } shouldBeEqualTo "done"
        }
        fixture.closed shouldBeEqualTo listOf("edges", "vertices")
        fixture.loader.requests shouldBeEqualTo listOf("vertices", "edges")
        fallback.requests.isEmpty().shouldBeTrue()
        fixture.vertices.closeCount shouldBeEqualTo 1
        fixture.edges.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `TCCL miss 또는 null은 nullable fallback을 사용한다`() {
        for (context in listOf(Loader { null }, null)) {
            val fixture = Fixture()
            withLoader(context) {
                withClasspathCsvGraphImportSource("vertices", "edges", fixture.loader) { "ok" } shouldBeEqualTo "ok"
            }
            fixture.closed shouldBeEqualTo listOf("edges", "vertices")
        }
        withLoader(null) {
            assertFailsWith<IllegalArgumentException> {
                withClasspathCsvGraphImportSource("absent", "edges", null, { "missing: $it" }) { error("callback") }
            }.message shouldBeEqualTo "missing: absent"
        }
    }

    @Test
    fun `선행 slash는 정규화하지 않고 missing resource로 처리한다`() {
        val fixture = Fixture()
        withLoader(null) {
            assertFailsWith<IllegalArgumentException> {
                withClasspathCsvGraphImportSource("/vertices", "edges", fixture.loader) { error("callback") }
            }.message shouldBeEqualTo "CSV classpath resource not found: /vertices"
        }
        fixture.vertices.closeCount shouldBeEqualTo 0
    }

    @Test
    fun `lookup 예외는 fallback 없이 그대로 전파한다`() {
        val denied = SecurityException("denied")
        val fallback = Loader { error("fallback must not run") }
        withLoader(Loader { throw denied }) {
            val actual = assertFailsWith<SecurityException> {
                withClasspathCsvGraphImportSource("vertices", "edges", fallback) { error("callback") }
            }
            (actual === denied).shouldBeTrue()
        }
        fallback.requests.isEmpty().shouldBeTrue()
    }

    @Test
    fun `callback 실패와 두 close 실패의 identity와 suppressed 순서를 보존한다`() {
        val primary = IllegalStateException("callback")
        val edgeClose = IOException("edge-close")
        val vertexClose = IOException("vertex-close")
        val fixture = Fixture(vertexClose, edgeClose)
        withLoader(fixture.loader) {
            val actual = assertFailsWith<IllegalStateException> {
                withClasspathCsvGraphImportSource("vertices", "edges", null) { throw primary }
            }
            (actual === primary).shouldBeTrue()
            actual.suppressed.toList() shouldBeEqualTo listOf(edgeClose, vertexClose)
        }
        fixture.closed shouldBeEqualTo listOf("edges", "vertices")
    }

    @Test
    fun `edge open 실패는 vertex를 닫고 close 실패를 suppressed로 보존한다`() {
        val primary = IOException("edge-open")
        val closeFailure = IOException("vertex-close")
        val fixture = Fixture(closeFailure)
        val loader = Loader { name -> if (name == "vertices") fixture.vertices else throw primary }
        withLoader(loader) {
            val actual = assertFailsWith<IOException> {
                withClasspathCsvGraphImportSource("vertices", "edges", null) { error("callback") }
            }
            (actual === primary).shouldBeTrue()
            actual.suppressed.toList() shouldBeEqualTo listOf(closeFailure)
        }
        fixture.closed shouldBeEqualTo listOf("vertices")
        fixture.vertices.closeCount shouldBeEqualTo 1
    }

    @Test
    fun `suspend callback은 caller context에서 실행하고 open close는 IO에서 실행한다`() = runSuspendIO {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { caller ->
            withContext(caller) {
                val callerThread = Thread.currentThread()
                val fixture = Fixture()
                val previous = callerThread.contextClassLoader
                callerThread.contextClassLoader = fixture.loader
                try {
                    withClasspathCsvGraphImportSourceSuspending("vertices", "edges", null) {
                        (Thread.currentThread() === callerThread).shouldBeTrue()
                        "done"
                    } shouldBeEqualTo "done"
                } finally {
                    callerThread.contextClassLoader = previous
                }
                fixture.loader.threads.all { it !== callerThread }.shouldBeTrue()
                fixture.closeThreads.all { it !== callerThread }.shouldBeTrue()
                fixture.closed shouldBeEqualTo listOf("edges", "vertices")
            }
        }
    }

    @Test
    fun `실제 취소는 두 stream을 한 번 닫고 close 실패를 취소에 보존한다`() = runSuspendIO {
        withTimeout(5_000) {
            val entered = CompletableDeferred<Unit>()
            val terminal = CompletableDeferred<Throwable?>()
            val cancelled = CancellationException("cancel-import")
            val edgeClose = IOException("edge-close")
            val vertexClose = IOException("vertex-close")
            val fixture = Fixture(vertexClose, edgeClose)
            val job = launch {
                withClasspathCsvGraphImportSourceSuspending("vertices", "edges", fixture.loader) {
                    entered.complete(Unit)
                    awaitCancellation()
                }
            }
            job.invokeOnCompletion { terminal.complete(it) }
            entered.await()
            job.cancel(cancelled)
            job.cancelAndJoin()
            val failure = terminal.await()
            (failure === cancelled).shouldBeTrue()
            failure?.suppressed?.toList() shouldBeEqualTo listOf(edgeClose, vertexClose)
            fixture.closed shouldBeEqualTo listOf("edges", "vertices")
            fixture.vertices.closeCount shouldBeEqualTo 1
            fixture.edges.closeCount shouldBeEqualTo 1
        }
    }

    @Test
    fun `활성 호출자의 callback 취소 예외에도 close 실패를 보존한다`() = runSuspendIO {
        val cancelled = CancellationException("callback-cancel")
        val edgeClose = IOException("edge-close")
        val vertexClose = IOException("vertex-close")
        val fixture = Fixture(vertexClose, edgeClose)
        val failure = assertFailsWith<CancellationException> {
            withClasspathCsvGraphImportSourceSuspending("vertices", "edges", fixture.loader) {
                throw cancelled
            }
        }
        (failure === cancelled).shouldBeTrue()
        failure.suppressed.toList() shouldBeEqualTo listOf(edgeClose, vertexClose)
        fixture.closed shouldBeEqualTo listOf("edges", "vertices")
    }

    @Test
    fun `callback 반환 후 close 중 취소도 닫기 실패를 잃지 않는다`() = runSuspendIO {
        withTimeout(5_000) {
            val closing = CompletableDeferred<Unit>()
            val release = CountDownLatch(1)
            val terminal = CompletableDeferred<Throwable?>()
            val cancelled = CancellationException("cancel-during-close")
            val edgeClose = IOException("edge-close")
            val vertexClose = IOException("vertex-close")
            val fixture = Fixture(vertexClose, edgeClose)
            fixture.edges.beforeClose = {
                closing.complete(Unit)
                check(release.await(5, TimeUnit.SECONDS))
            }
            val job = launch {
                withClasspathCsvGraphImportSourceSuspending("vertices", "edges", fixture.loader) { "done" }
            }
            job.invokeOnCompletion { terminal.complete(it) }
            try {
                closing.await()
                job.cancel(cancelled)
            } finally {
                release.countDown()
            }
            job.join()
            val failure = terminal.await()
            (failure === cancelled).shouldBeTrue()
            failure?.suppressed?.toList() shouldBeEqualTo listOf(edgeClose)
            edgeClose.suppressed.toList() shouldBeEqualTo listOf(vertexClose)
            fixture.closed shouldBeEqualTo listOf("edges", "vertices")
            fixture.vertices.closeCount shouldBeEqualTo 1
            fixture.edges.closeCount shouldBeEqualTo 1
        }
    }

    private fun <T> withLoader(loader: ClassLoader?, block: () -> T): T {
        val thread = Thread.currentThread()
        val previous = thread.contextClassLoader
        thread.contextClassLoader = loader
        return try { block() } finally { thread.contextClassLoader = previous }
    }

    private class Loader(private val resolve: (String) -> InputStream?): ClassLoader(null) {
        val requests = mutableListOf<String>()
        val threads = mutableListOf<Thread>()
        override fun getResourceAsStream(name: String): InputStream? {
            requests += name
            threads += Thread.currentThread()
            return resolve(name)
        }
    }

    private class Fixture(vertexFailure: IOException? = null, edgeFailure: IOException? = null) {
        val closed = mutableListOf<String>()
        val closeThreads = mutableListOf<Thread>()
        val vertices = Stream("vertices", closed, closeThreads, vertexFailure)
        val edges = Stream("edges", closed, closeThreads, edgeFailure)
        val loader = Loader { name -> when (name) { "vertices" -> vertices; "edges" -> edges; else -> null } }
    }

    private class Stream(
        private val name: String,
        private val closed: MutableList<String>,
        private val threads: MutableList<Thread>,
        private val failure: IOException?,
    ): InputStream() {
        var closeCount = 0
        var beforeClose: () -> Unit = {}
        override fun read(): Int {
            check(closeCount == 0) { "Stream closed" }
            return 65
        }
        override fun close() {
            beforeClose()
            closeCount++
            closed += name
            threads += Thread.currentThread()
            failure?.let { throw it }
        }
    }
}
