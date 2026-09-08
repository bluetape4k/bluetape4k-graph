package io.bluetape4k.graph.examples.iam

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotBeEmpty
import io.bluetape4k.graph.examples.iam.service.IamAccessGraphSuspendService
import io.bluetape4k.graph.examples.iam.service.IamAccessSuspendSampleGraph
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractIamAccessGraphSuspendTest {

    companion object: KLoggingChannel()

    protected abstract val ops: GraphSuspendOperations
    protected open val graphName: String = "iam_access_suspend_test"
    protected open val clock: Clock = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC)
    protected val service: IamAccessGraphSuspendService by lazy {
        IamAccessGraphSuspendService(ops = ops, graphName = graphName, clock = clock)
    }

    @BeforeEach
    fun cleanGraph() = runSuspendIO {
        if (ops.graphExists(graphName)) {
            ops.dropGraph(graphName)
        }
        service.initialize()
        IamAccessSuspendSampleGraph.seed(service)
    }

    @Test
    fun `explains suspend inherited group grant`() = runSuspendIO {
        val explanation = service.explainAccess("alice", "staging-service", "deploy")

        explanation.allowed shouldBeEqualTo true
        explanation.path shouldContain "group:engineering"
        explanation.path shouldContain "role:deployer-role"
    }

    @Test
    fun `explains suspend temporary break glass grant`() = runSuspendIO {
        val explanation = service.explainAccess("carol", "prod-db", "read")

        explanation.allowed shouldBeEqualTo true
        explanation.path shouldContain "grant:break-glass-1001"
    }

    @Test
    fun `detects suspend risky admin chain`() = runSuspendIO {
        val chains = service.riskyPrivilegeChains("alice")

        chains.shouldNotBeEmpty()
        chains.single().roleId shouldBeEqualTo "prod-admin-role"
    }
}
