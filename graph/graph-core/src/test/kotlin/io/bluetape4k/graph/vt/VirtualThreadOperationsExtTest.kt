package io.bluetape4k.graph.vt

import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldNotBeNull
import io.bluetape4k.graph.AbstractGraphTest
import io.bluetape4k.graph.algo.VirtualThreadAlgorithmAdapter
import io.bluetape4k.graph.repository.GraphAlgorithmRepository
import io.bluetape4k.graph.repository.GraphVirtualThreadAlgorithmRepository
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.Test

class VirtualThreadOperationsExtTest: AbstractGraphTest() {

    companion object: KLogging()

    @Test
    fun `GraphOperations asVirtualThread returns GraphVirtualThreadOperations`() {
        TinkerGraphOperations().use { ops ->
            val vtOps = ops.asVirtualThread()
            vtOps.shouldNotBeNull()
            vtOps.shouldBeInstanceOf<GraphVirtualThreadOperations>()
        }
    }

    @Test
    fun `GraphAlgorithmRepository asVirtualThread returns GraphVirtualThreadAlgorithmRepository`() {
        TinkerGraphOperations().use { ops ->
            // 명시적으로 GraphAlgorithmRepository 전용 어댑터를 사용하려면 직접 생성자를 호출한다.
            val algoOnly = VirtualThreadAlgorithmAdapter(ops as GraphAlgorithmRepository)
            algoOnly.shouldNotBeNull()
            algoOnly.shouldBeInstanceOf<GraphVirtualThreadAlgorithmRepository>()
        }
    }
}
