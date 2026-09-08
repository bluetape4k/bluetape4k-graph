package io.bluetape4k.graph.examples.networktopology

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.graph.examples.networktopology.io.NetworkTopologySampleDatasetLoader
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors

class NetworkTopologySampleDatasetLoaderTest {

    @Test
    fun `imports packaged resources with null context class loader and restores it`() {
        val thread = Thread.currentThread()
        val previous = thread.contextClassLoader
        val report = withContextClassLoader(null) {
            TinkerGraphOperations().use { ops ->
                NetworkTopologySampleDatasetLoader.importCsv(ops)
            }
        }

        thread.contextClassLoader shouldBeEqualTo previous
        report.status shouldBeEqualTo GraphIoStatus.COMPLETED
        report.verticesRead shouldBeGreaterThan 0L
        report.verticesCreated shouldBeGreaterThan 0L
        report.edgesRead shouldBeGreaterThan 0L
        report.edgesCreated shouldBeGreaterThan 0L
    }

    @Test
    fun `suspending import uses fallback with null context class loader and restores it`() = runSuspendIO {
        Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { caller ->
            withContext(caller) {
                val thread = Thread.currentThread()
                val previous = thread.contextClassLoader
                val report = withSuspendingContextClassLoader(null) {
                    TinkerGraphSuspendOperations().use { ops ->
                        NetworkTopologySampleDatasetLoader.importCsvSuspending(ops)
                    }
                }

                thread.contextClassLoader shouldBeEqualTo previous
                report.status shouldBeEqualTo GraphIoStatus.COMPLETED
                report.verticesRead shouldBeGreaterThan 0L
                report.verticesCreated shouldBeGreaterThan 0L
                report.edgesRead shouldBeGreaterThan 0L
                report.edgesCreated shouldBeGreaterThan 0L
            }
        }
    }

    private fun <T> withContextClassLoader(classLoader: ClassLoader?, block: () -> T): T {
        val thread = Thread.currentThread()
        val previous = thread.contextClassLoader
        thread.contextClassLoader = classLoader

        return try {
            block()
        } finally {
            thread.contextClassLoader = previous
        }
    }

    private suspend fun <T> withSuspendingContextClassLoader(
        classLoader: ClassLoader?,
        block: suspend () -> T,
    ): T {
        val thread = Thread.currentThread()
        val previous = thread.contextClassLoader
        thread.contextClassLoader = classLoader

        return try {
            block()
        } finally {
            thread.contextClassLoader = previous
        }
    }
}
