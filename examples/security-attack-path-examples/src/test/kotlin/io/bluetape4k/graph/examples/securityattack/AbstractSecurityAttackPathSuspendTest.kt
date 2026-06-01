package io.bluetape4k.graph.examples.securityattack

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.examples.securityattack.io.SecurityAttackPathSampleDatasetLoader
import io.bluetape4k.graph.examples.securityattack.schema.HostLabel
import io.bluetape4k.graph.examples.securityattack.service.SecurityAttackPathSuspendService
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import io.bluetape4k.logging.coroutines.KLoggingChannel
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractSecurityAttackPathSuspendTest {

    companion object: KLoggingChannel()

    protected abstract val ops: GraphSuspendOperations
    protected open val graphName: String = "security_attack_path_suspend_test"
    protected val service: SecurityAttackPathSuspendService by lazy {
        SecurityAttackPathSuspendService(ops, graphName)
    }

    @BeforeEach
    fun cleanGraph() = runSuspendIO {
        if (ops.graphExists(graphName)) {
            ops.dropGraph(graphName)
        }
        service.initialize()
        SecurityAttackPathSampleDatasetLoader.importCsvSuspending(ops).status shouldBeEqualTo GraphIoStatus.COMPLETED
    }

    @Test
    fun `finds suspend attack path and privilege escalation`() = runSuspendIO {
        val path = service.shortestAttackPath("internet", "customer-db")
        val escalation = service.privilegeEscalationPaths("web-service")
            .map { it.nodeIds }

        path?.nodeIds shouldBeEqualTo listOf(
            "internet",
            "web-edge",
            "vuln-web-rce",
            "web-service",
            "ci-admin-token",
            "domain-admin",
            "db-admin",
            "customer-db",
        )
        escalation shouldContain listOf("web-service", "ci-admin-token", "domain-admin")
    }

    @Test
    fun `finds suspend remediation impact and unreachable crown jewel`() = runSuspendIO {
        val blockedTargets = service.remediationImpact("edge-credential-admin")
            .map { it.properties[HostLabel.hostId.name] }
        val unreachable = service.unreachableCrownJewels("internet")
            .map { it.properties[HostLabel.hostId.name] }

        blockedTargets shouldBeEqualTo listOf("customer-db")
        unreachable shouldBeEqualTo listOf("backup-vault")
    }
}
