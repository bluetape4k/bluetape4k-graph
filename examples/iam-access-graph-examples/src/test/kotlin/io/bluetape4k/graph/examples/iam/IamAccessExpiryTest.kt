package io.bluetape4k.graph.examples.iam

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeEmpty
import io.bluetape4k.assertions.shouldContain
import io.bluetape4k.graph.examples.iam.service.IamAccessExplanation
import io.bluetape4k.graph.examples.iam.service.IamAccessGraphService
import io.bluetape4k.graph.examples.iam.service.IamAccessGraphSuspendService
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.junit5.coroutines.runSuspendIO
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class IamAccessExpiryTest {

    @Test
    fun `sync temporary grant is available before expiry and denied at or after expiry`() {
        val expiresAt = Instant.parse("2026-09-08T00:00:00Z")

        syncAccess(expiresAt = expiresAt, evaluatedAt = expiresAt.minusSeconds(1)).assertGranted()
        syncAccess(expiresAt = expiresAt, evaluatedAt = expiresAt).assertExpired()
        syncAccess(expiresAt = expiresAt, evaluatedAt = expiresAt.plusSeconds(1)).assertExpired()
    }

    @Test
    fun `suspend temporary grant is available before expiry and denied at or after expiry`() = runSuspendIO {
        val expiresAt = Instant.parse("2026-09-08T00:00:00Z")

        suspendAccess(expiresAt = expiresAt, evaluatedAt = expiresAt.minusSeconds(1)).assertGranted()
        suspendAccess(expiresAt = expiresAt, evaluatedAt = expiresAt).assertExpired()
        suspendAccess(expiresAt = expiresAt, evaluatedAt = expiresAt.plusSeconds(1)).assertExpired()
    }

    @Test
    fun `malformed expiresAt never grants access in sync or suspend services`() {
        syncAccess("not-an-instant", Instant.parse("2026-09-08T00:00:00Z")).assertExpired()

        runSuspendIO {
            suspendAccess("not-an-instant", Instant.parse("2026-09-08T00:00:00Z")).assertExpired()
        }
    }

    @Test
    fun `explicit deny takes precedence over an active temporary grant`() {
        val now = Instant.parse("2026-09-07T23:59:00Z")
        val expiresAt = Instant.parse("2026-09-08T00:00:00Z")

        syncAccess(expiresAt.toString(), now, explicitDeny = true).assertDeniedByPolicy()

        runSuspendIO {
            suspendAccess(expiresAt.toString(), now, explicitDeny = true).assertDeniedByPolicy()
        }
    }

    private fun syncAccess(
        expiresAt: Instant,
        evaluatedAt: Instant,
        explicitDeny: Boolean = false,
    ): IamAccessExplanation = syncAccess(expiresAt.toString(), evaluatedAt, explicitDeny)

    private fun syncAccess(
        expiresAt: String,
        evaluatedAt: Instant,
        explicitDeny: Boolean = false,
    ): IamAccessExplanation {
        val ops = TinkerGraphOperations()
        return try {
            val service = IamAccessGraphService(
                ops = ops,
                graphName = "iam_expiry_sync_test",
                clock = Clock.fixed(evaluatedAt, ZoneOffset.UTC),
            )
            seedSyncScenario(service, expiresAt, explicitDeny)
            service.explainAccess("carol", "prod-db", "read")
        } finally {
            ops.close()
        }
    }

    private suspend fun suspendAccess(
        expiresAt: Instant,
        evaluatedAt: Instant,
        explicitDeny: Boolean = false,
    ): IamAccessExplanation = suspendAccess(expiresAt.toString(), evaluatedAt, explicitDeny)

    private suspend fun suspendAccess(
        expiresAt: String,
        evaluatedAt: Instant,
        explicitDeny: Boolean = false,
    ): IamAccessExplanation {
        val ops = TinkerGraphSuspendOperations()
        return try {
            val service = IamAccessGraphSuspendService(
                ops = ops,
                graphName = "iam_expiry_suspend_test",
                clock = Clock.fixed(evaluatedAt, ZoneOffset.UTC),
            )
            seedSuspendScenario(service, expiresAt, explicitDeny)
            service.explainAccess("carol", "prod-db", "read")
        } finally {
            ops.close()
        }
    }

    private fun seedSyncScenario(service: IamAccessGraphService, expiresAt: String, explicitDeny: Boolean) {
        service.initialize()

        val user = service.addUser("carol", "Carol Park", "operations")
        val grant = service.addSessionGrant("break-glass-1001", "incident", expiresAt)
        val temporaryPermission = service.addPermission("temporary-read-prod", "read")
        val resource = service.addResource("prod-db", "Production Database", "database", "restricted")

        service.grantTemporaryPermission(user.id, grant.id, temporaryPermission.id)
        service.applyPermission(temporaryPermission.id, resource.id)

        if (explicitDeny) {
            val role = service.addRole("deny-role", "Production deny", "restricted")
            val policy = service.addPolicy("deny-prod-read", "Deny production read", "deny")
            val denyPermission = service.addPermission("deny-read-prod", "read")

            service.assignRole(user.id, role.id, source = "explicit")
            service.attachPolicy(role.id, policy.id)
            service.grantPermission(policy.id, denyPermission.id)
            service.applyPermission(denyPermission.id, resource.id)
        }
    }

    private suspend fun seedSuspendScenario(
        service: IamAccessGraphSuspendService,
        expiresAt: String,
        explicitDeny: Boolean,
    ) {
        service.initialize()

        val user = service.addUser("carol", "Carol Park", "operations")
        val grant = service.addSessionGrant("break-glass-1001", "incident", expiresAt)
        val temporaryPermission = service.addPermission("temporary-read-prod", "read")
        val resource = service.addResource("prod-db", "Production Database", "database", "restricted")

        service.grantTemporaryPermission(user.id, grant.id, temporaryPermission.id)
        service.applyPermission(temporaryPermission.id, resource.id)

        if (explicitDeny) {
            val role = service.addRole("deny-role", "Production deny", "restricted")
            val policy = service.addPolicy("deny-prod-read", "Deny production read", "deny")
            val denyPermission = service.addPermission("deny-read-prod", "read")

            service.assignRole(user.id, role.id, source = "explicit")
            service.attachPolicy(role.id, policy.id)
            service.grantPermission(policy.id, denyPermission.id)
            service.applyPermission(denyPermission.id, resource.id)
        }
    }

    private fun IamAccessExplanation.assertGranted() {
        allowed shouldBeEqualTo true
        path shouldContain "grant:break-glass-1001"
        reason shouldBeEqualTo "Granted by reachable IAM path"
    }

    private fun IamAccessExplanation.assertExpired() {
        allowed shouldBeEqualTo false
        path.shouldBeEmpty()
        reason shouldBeEqualTo "No matching grant path"
    }

    private fun IamAccessExplanation.assertDeniedByPolicy() {
        allowed shouldBeEqualTo false
        path shouldContain "policy:deny-prod-read"
        reason shouldBeEqualTo "Denied by explicit policy path"
    }
}
