package io.bluetape4k.graph.examples.fraud

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterOrEqualTo
import io.bluetape4k.assertions.shouldBeTrue
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotBeEmpty
import io.bluetape4k.graph.examples.fraud.service.FraudDetectionSuspendService
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.KLogging
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractFraudDetectionSuspendTest {

    companion object : KLogging()

    protected abstract val ops: GraphSuspendOperations
    protected open val graphName: String = "fraud_detection_suspend_test"
    protected val service: FraudDetectionSuspendService by lazy { FraudDetectionSuspendService(ops, graphName) }

    @BeforeEach
    fun cleanGraph() = runTest {
        if (ops.graphExists(graphName)) {
            ops.dropGraph(graphName)
        }
        service.initialize()
    }

    @Test
    fun `creates accounts and transfer edges`() = runTest {
        val alice = service.addAccount("acct-alice", "Alice", "standard")
        val bob = service.addAccount("acct-bob", "Bob", "watch")

        service.recordTransfer(alice.id, bob.id, amount = 100)

        val scores = service.rankHighRiskAccounts(limit = 10).toList()
        scores.shouldNotBeEmpty()
        scores.map { it.vertex.properties["accountId"] } shouldContain "acct-bob"
    }

    @Test
    fun `detects circular transfers`() = runTest {
        val a = service.addAccount("acct-a", "A")
        val b = service.addAccount("acct-b", "B")
        val c = service.addAccount("acct-c", "C")

        service.recordTransfer(a.id, b.id, amount = 100)
        service.recordTransfer(b.id, c.id, amount = 75)
        service.recordTransfer(c.id, a.id, amount = 50)

        val cycles = service.detectCircularTransfers(maxDepth = 5).toList()
        cycles.shouldNotBeEmpty()
        cycles.first().path.vertices.first().id shouldBeEqualTo cycles.first().path.vertices.last().id
    }

    @Test
    fun `detects suspicious transfer clusters`() = runTest {
        val a = service.addAccount("acct-a", "A")
        val b = service.addAccount("acct-b", "B")
        val c = service.addAccount("acct-c", "C")

        service.recordTransfer(a.id, b.id, amount = 100)
        service.recordTransfer(b.id, c.id, amount = 100)

        val clusters = service.detectSuspiciousClusters(minSize = 3).toList()
        clusters.shouldNotBeEmpty()
        clusters.any { component ->
            component.size >= 3 && component.vertices.map { it.properties["accountId"] }.containsAll(listOf("acct-a", "acct-b", "acct-c"))
        }.shouldBeTrue()
    }

    @Test
    fun `ranks high risk receiver inside top results`() = runTest {
        val sink = service.addAccount("acct-sink", "Sink")
        repeat(4) { index ->
            val source = service.addAccount("acct-source-$index", "Source $index")
            service.recordTransfer(source.id, sink.id, amount = 100L + index)
        }

        val accountIds = service.rankHighRiskAccounts(limit = 10).toList().map { it.vertex.properties["accountId"] }
        accountIds.size shouldBeGreaterOrEqualTo 1
        accountIds shouldContain "acct-sink"
    }
}
