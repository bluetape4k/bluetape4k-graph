package io.bluetape4k.graph.benchmark

import io.bluetape4k.concurrent.virtualthread.virtualFutureOf
import io.bluetape4k.graph.algo.VirtualThreadAlgorithmAdapter
import io.bluetape4k.graph.model.BfsDfsOptions
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.PageRankOptions
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Fork
import org.openjdk.jmh.annotations.Measurement
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.Warmup
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Compares the sync, virtual-thread, and coroutine API models on the same TinkerGraph fixture.
 *
 * PageRank benchmarks use throughput (`ops/s`, higher is better). BFS, 100-way request,
 * and 100-way launch benchmarks use average latency (`us/op`, lower is better). The fixture
 * is intentionally in-memory so the numbers expose API-model overhead without Docker or
 * network I/O noise.
 */
@Fork(1)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
open class ApiModelBenchmark : GraphBenchmarkState() {

    private val tinkerOps: TinkerGraphOperations
        get() = syncOps as TinkerGraphOperations

    private val vtAlgoOps: VirtualThreadAlgorithmAdapter by lazy {
        VirtualThreadAlgorithmAdapter(tinkerOps)
    }

    private val suspendOps: TinkerGraphSuspendOperations by lazy {
        TinkerGraphSuspendOperations(tinkerOps)
    }

    private val pageRankOptions = PageRankOptions(vertexLabel = PERSON_LABEL, topK = 4)
    private val bfsOptions = BfsDfsOptions(maxDepth = 5)

    private val concurrentStartIds: List<GraphElementId> by lazy {
        val seeds = listOf(aliceId, bobId, charlieId, daveId)
        List(MAX_CONCURRENT_REQUESTS) { index -> seeds[index % seeds.size] }
    }

    private val launchPayload: List<Int> =
        List(MAX_CONCURRENT_REQUESTS) { it }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    fun pageRankSyncThroughput(): Int =
        tinkerOps.pageRank(pageRankOptions).size

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    fun pageRankVirtualThreadThroughput(): Int =
        vtAlgoOps.pageRankAsync(pageRankOptions).join().size

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    fun pageRankCoroutineThroughput(): Int =
        runBlocking {
            suspendOps.pageRank(pageRankOptions).toList().size
        }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    fun bfsSyncLatency(): Int =
        tinkerOps.bfs(aliceId, bfsOptions).size

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    fun bfsVirtualThreadLatency(): Int =
        vtAlgoOps.bfsAsync(aliceId, bfsOptions).join().size

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    fun bfsCoroutineLatency(): Int =
        runBlocking {
            suspendOps.bfs(aliceId, bfsOptions).toList().size
        }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    fun bfs100wayVirtualThreadLatency(): Int {
        val futures = concurrentStartIds(CONCURRENT_REQUESTS).map { startId ->
            vtAlgoOps.bfsAsync(startId, bfsOptions)
        }
        CompletableFuture.allOf(*futures.toTypedArray()).join()
        return futures.sumOf { it.join().size }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    fun bfs100wayCoroutineLatency(): Int =
        runBlocking {
            concurrentStartIds(CONCURRENT_REQUESTS)
                .map { startId -> async { suspendOps.bfs(startId, bfsOptions).toList().size } }
                .awaitAll()
                .sum()
        }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    fun virtualThread100wayCreationCost(): Int {
        val futures = launchPayload(CONCURRENT_REQUESTS).map { value ->
            virtualFutureOf { value }
        }
        CompletableFuture.allOf(*futures.toTypedArray()).join()
        return futures.sumOf { it.join() }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    fun coroutine100wayLaunchCost(): Int =
        runBlocking {
            launchPayload(CONCURRENT_REQUESTS)
                .map { value -> async { value } }
                .awaitAll()
                .sum()
        }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    fun bfsConcurrentVirtualThreadLatency(state: ApiModelConcurrencyState): Int {
        val futures = concurrentStartIds(state.concurrency).map { startId ->
            vtAlgoOps.bfsAsync(startId, bfsOptions)
        }
        CompletableFuture.allOf(*futures.toTypedArray()).join()
        return futures.sumOf { it.join().size }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    fun bfsConcurrentCoroutineLatency(state: ApiModelConcurrencyState): Int =
        runBlocking {
            concurrentStartIds(state.concurrency)
                .map { startId -> async { suspendOps.bfs(startId, bfsOptions).toList().size } }
                .awaitAll()
                .sum()
        }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    fun virtualThreadConcurrentCreationCost(state: ApiModelConcurrencyState): Int {
        val futures = launchPayload(state.concurrency).map { value ->
            virtualFutureOf { value }
        }
        CompletableFuture.allOf(*futures.toTypedArray()).join()
        return futures.sumOf { it.join() }
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    fun coroutineConcurrentLaunchCost(state: ApiModelConcurrencyState): Int =
        runBlocking {
            launchPayload(state.concurrency)
                .map { value -> async { value } }
                .awaitAll()
                .sum()
        }

    private fun concurrentStartIds(count: Int): List<GraphElementId> =
        concurrentStartIds.subList(0, count)

    private fun launchPayload(count: Int): List<Int> =
        launchPayload.subList(0, count)

    private companion object {
        private const val PERSON_LABEL = "Person"
        private const val CONCURRENT_REQUESTS = 100
        private const val MAX_CONCURRENT_REQUESTS = 1_000
    }
}

@State(Scope.Thread)
open class ApiModelConcurrencyState {

    @Param("10", "100", "1000")
    var concurrency: Int = 100
}
