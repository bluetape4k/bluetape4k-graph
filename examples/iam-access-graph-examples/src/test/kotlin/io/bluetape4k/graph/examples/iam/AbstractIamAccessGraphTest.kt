package io.bluetape4k.graph.examples.iam

import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.assertions.shouldNotBeEmpty
import io.bluetape4k.graph.examples.iam.service.IamAccessGraphService
import io.bluetape4k.graph.examples.iam.service.IamAccessSampleGraph
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractIamAccessGraphTest {

    companion object: KLogging()

    protected abstract val ops: GraphOperations
    protected open val graphName: String = "iam_access_test"
    protected val service: IamAccessGraphService by lazy { IamAccessGraphService(ops, graphName) }

    @BeforeEach
    fun cleanGraph() {
        if (ops.graphExists(graphName)) {
            ops.dropGraph(graphName)
        }
        service.initialize()
        IamAccessSampleGraph.seed(service)
    }

    @Test
    fun `explains direct role grant`() {
        val explanation = service.explainAccess("bob", "audit-dashboard", "read")

        explanation.allowed shouldBeEqualTo true
        explanation.path shouldContain "user:bob"
        explanation.path shouldContain "role:readonly-role"
        explanation.path shouldContain "resource:audit-dashboard"
    }

    @Test
    fun `explains inherited group grant`() {
        val explanation = service.explainAccess("alice", "staging-service", "deploy")

        explanation.allowed shouldBeEqualTo true
        explanation.path shouldContain "group:engineering"
        explanation.path shouldContain "role:deployer-role"
        explanation.path shouldContain "resource:staging-service"
    }

    @Test
    fun `returns denied and absent access decisions`() {
        val denied = service.explainAccess("eve", "prod-db", "delete")
        val absent = service.explainAccess("bob", "prod-db", "delete")

        denied.allowed shouldBeEqualTo false
        denied.reason shouldBeEqualTo "Denied by explicit policy path"
        denied.path shouldContain "policy:deny-prod-delete-policy"

        absent.allowed shouldBeEqualTo false
        absent.path.shouldBeEmpty()
        absent.reason shouldBeEqualTo "No matching grant path"
    }

    @Test
    fun `detects risky admin access through nested groups`() {
        val chains = service.riskyPrivilegeChains("alice")

        chains.shouldNotBeEmpty()
        chains.single().roleId shouldBeEqualTo "prod-admin-role"
        chains.single().path shouldContain "group:engineering"
        chains.single().path shouldContain "group:platform-admins"
    }

    @Test
    fun `finds least privilege drift`() {
        val findings = service.excessivePermissions(
            "alice",
            mapOf(
                "staging-service" to setOf("deploy"),
                "prod-db" to setOf("read"),
            )
        )

        findings.map { it.resourceId } shouldContain "prod-db"
        findings.map { it.action } shouldContain "delete"
    }

    @Test
    fun `explains temporary break glass grant`() {
        val explanation = service.explainAccess("carol", "prod-db", "read")

        explanation.allowed shouldBeEqualTo true
        explanation.path shouldContain "grant:break-glass-1001"
        explanation.path shouldContain "permission:read"
    }
}
