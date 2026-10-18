package io.bluetape4k.graph.examples.securityattack

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.examples.securityattack.io.SecurityAttackPathSampleDatasetLoader
import io.bluetape4k.graph.examples.securityattack.schema.HostLabel
import io.bluetape4k.graph.examples.securityattack.service.SecurityAttackPathService
import io.bluetape4k.graph.io.report.GraphIoStatus
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractSecurityAttackPathTest {

    companion object: KLogging()

    protected abstract val ops: GraphOperations
    protected open val graphName: String = "security_attack_path_test"
    protected val service: SecurityAttackPathService by lazy { SecurityAttackPathService(ops, graphName) }

    @BeforeEach
    fun cleanGraph() {
        if (ops.graphExists(graphName)) {
            ops.dropGraph(graphName)
        }
        service.initialize()

        val importReport = SecurityAttackPathSampleDatasetLoader.importCsv(ops)
        log.debug { "Imported $importReport" }
        importReport.status shouldBeEqualTo GraphIoStatus.COMPLETED
    }

    fun `finds reachable and unreachable crown jewel attack paths`() {
        val path = service.shortestAttackPath("internet", "customer-db")

        log.debug { "Found path=$path" }
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

        val unreachable = service.unreachableCrownJewels("internet")
            .onEach { log.debug { "unreachable vertex=$it" } }
            .map { it.properties[HostLabel.hostId.name] }

        unreachable shouldBeEqualTo listOf("backup-vault")
    }

    @Test
    fun `ranks attack paths by risk signals`() {
        val paths = service.rankedAttackPaths("internet", "customer-db")
            .onEach { log.debug { "attach path=$it" } }
            .map { it.nodeIds }

        paths shouldContain listOf(
            "internet",
            "web-edge",
            "vuln-web-rce",
            "web-service",
            "ci-admin-token",
            "domain-admin",
            "db-admin",
            "customer-db",
        )
        paths shouldContain listOf(
            "internet",
            "web-edge",
            "app-api",
            "vuln-app-secret",
            "ci-admin-token",
            "domain-admin",
            "db-admin",
            "customer-db",
        )
    }

    @Test
    fun `finds credential based privilege escalation path`() {
        val paths = service.privilegeEscalationPaths("web-service")
            .onEach { log.debug { "attach path=$it" } }
            .map { it.nodeIds }

        paths shouldContain listOf("web-service", "ci-admin-token", "domain-admin")
    }

    @Test
    fun `explains remediation impact by cutting one edge`() {
        val blockedTargets = service.remediationImpact("edge-credential-admin")
            .onEach { log.debug { "remediation impact vertex=$it" } }
            .map { it.properties[HostLabel.hostId.name] }
        blockedTargets shouldBeEqualTo listOf("customer-db")

        val blockedPath = service.shortestAttackPath(
            sourceAssetId = "internet",
            targetHostId = "customer-db",
            blockedEdgeIds = setOf("edge-credential-admin"),
        )
        blockedPath.shouldBeNull()
    }
}
