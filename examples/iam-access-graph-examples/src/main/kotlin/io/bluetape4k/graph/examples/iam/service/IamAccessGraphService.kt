package io.bluetape4k.graph.examples.iam.service

import io.bluetape4k.graph.examples.iam.schema.AppliesToLabel
import io.bluetape4k.graph.examples.iam.schema.AttachedPolicyLabel
import io.bluetape4k.graph.examples.iam.schema.GrantsPermissionLabel
import io.bluetape4k.graph.examples.iam.schema.HasRoleLabel
import io.bluetape4k.graph.examples.iam.schema.HasTempGrantLabel
import io.bluetape4k.graph.examples.iam.schema.IamGroupLabel
import io.bluetape4k.graph.examples.iam.schema.IamPermissionLabel
import io.bluetape4k.graph.examples.iam.schema.IamPolicyLabel
import io.bluetape4k.graph.examples.iam.schema.IamResourceLabel
import io.bluetape4k.graph.examples.iam.schema.IamRoleLabel
import io.bluetape4k.graph.examples.iam.schema.IamSessionGrantLabel
import io.bluetape4k.graph.examples.iam.schema.IamUserLabel
import io.bluetape4k.graph.examples.iam.schema.MemberOfLabel
import io.bluetape4k.graph.examples.iam.schema.TemporaryPermissionLabel
import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.info
import io.bluetape4k.support.requireNotBlank
import java.io.Serializable

/**
 * Explains whether an IAM user can perform an action on a resource and which graph path grants or blocks it.
 */
data class IamAccessExplanation(
    val userId: String,
    val resourceId: String,
    val action: String,
    val allowed: Boolean,
    val path: List<String>,
    val reason: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * Describes an inherited privileged access chain that should be reviewed.
 */
data class IamPrivilegeChain(
    val userId: String,
    val roleId: String,
    val path: List<String>,
    val reason: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

/**
 * IAM access-path example service built on top of [GraphOperations].
 *
 * The service is intentionally not a full policy engine. It demonstrates identity, group, role, policy, permission,
 * resource, and temporary-grant reachability as backend-independent graph traversals.
 */
class IamAccessGraphService(
    private val ops: GraphOperations,
    private val graphName: String = "iam_access",
) {
    companion object: KLogging() {
        private const val EFFECT_ALLOW = "allow"
        private const val EFFECT_DENY = "deny"
        private const val MAX_GROUP_DEPTH = 4
    }

    /**
     * Creates the backing graph when it does not already exist.
     */
    fun initialize() {
        if (!ops.graphExists(graphName)) {
            ops.createGraph(graphName)
            log.info { "IAM access graph '$graphName' created" }
        }
    }

    fun addUser(userId: String, displayName: String, department: String): GraphVertex {
        userId.requireNotBlank("userId")
        displayName.requireNotBlank("displayName")
        department.requireNotBlank("department")
        return ops.createVertex(
            IamUserLabel.label,
            mapOf(
                IamUserLabel.userId.name to userId,
                IamUserLabel.displayName.name to displayName,
                IamUserLabel.department.name to department,
            )
        )
    }

    fun addGroup(groupId: String, name: String, riskTier: String = "standard"): GraphVertex {
        groupId.requireNotBlank("groupId")
        name.requireNotBlank("name")
        riskTier.requireNotBlank("riskTier")
        return ops.createVertex(
            IamGroupLabel.label,
            mapOf(
                IamGroupLabel.groupId.name to groupId,
                IamGroupLabel.name.name to name,
                IamGroupLabel.riskTier.name to riskTier,
            )
        )
    }

    fun addRole(roleId: String, name: String, privilege: String): GraphVertex {
        roleId.requireNotBlank("roleId")
        name.requireNotBlank("name")
        privilege.requireNotBlank("privilege")
        return ops.createVertex(
            IamRoleLabel.label,
            mapOf(
                IamRoleLabel.roleId.name to roleId,
                IamRoleLabel.name.name to name,
                IamRoleLabel.privilege.name to privilege,
            )
        )
    }

    fun addPolicy(policyId: String, name: String, effect: String): GraphVertex {
        policyId.requireNotBlank("policyId")
        name.requireNotBlank("name")
        effect.requireNotBlank("effect")
        return ops.createVertex(
            IamPolicyLabel.label,
            mapOf(
                IamPolicyLabel.policyId.name to policyId,
                IamPolicyLabel.name.name to name,
                IamPolicyLabel.effect.name to effect.lowercase(),
            )
        )
    }

    fun addPermission(permissionId: String, action: String): GraphVertex {
        permissionId.requireNotBlank("permissionId")
        action.requireNotBlank("action")
        return ops.createVertex(
            IamPermissionLabel.label,
            mapOf(
                IamPermissionLabel.permissionId.name to permissionId,
                IamPermissionLabel.action.name to action,
            )
        )
    }

    fun addResource(
        resourceId: String,
        name: String,
        resourceType: String,
        classification: String,
    ): GraphVertex {
        resourceId.requireNotBlank("resourceId")
        name.requireNotBlank("name")
        resourceType.requireNotBlank("resourceType")
        classification.requireNotBlank("classification")
        return ops.createVertex(
            IamResourceLabel.label,
            mapOf(
                IamResourceLabel.resourceId.name to resourceId,
                IamResourceLabel.name.name to name,
                IamResourceLabel.resourceType.name to resourceType,
                IamResourceLabel.classification.name to classification,
            )
        )
    }

    fun addSessionGrant(grantId: String, reason: String, expiresAt: String): GraphVertex {
        grantId.requireNotBlank("grantId")
        reason.requireNotBlank("reason")
        expiresAt.requireNotBlank("expiresAt")
        return ops.createVertex(
            IamSessionGrantLabel.label,
            mapOf(
                IamSessionGrantLabel.grantId.name to grantId,
                IamSessionGrantLabel.reason.name to reason,
                IamSessionGrantLabel.expiresAt.name to expiresAt,
            )
        )
    }

    fun addMembership(principalId: GraphElementId, groupId: GraphElementId, kind: String = "standing") {
        kind.requireNotBlank("kind")
        ops.createEdge(principalId, groupId, MemberOfLabel.label, mapOf(MemberOfLabel.kind.name to kind))
    }

    fun assignRole(principalId: GraphElementId, roleId: GraphElementId, source: String = "rbac") {
        source.requireNotBlank("source")
        ops.createEdge(principalId, roleId, HasRoleLabel.label, mapOf(HasRoleLabel.source.name to source))
    }

    fun attachPolicy(roleId: GraphElementId, policyId: GraphElementId, scope: String = "account") {
        scope.requireNotBlank("scope")
        ops.createEdge(roleId, policyId, AttachedPolicyLabel.label, mapOf(AttachedPolicyLabel.scope.name to scope))
    }

    fun grantPermission(policyId: GraphElementId, permissionId: GraphElementId, condition: String = "none") {
        condition.requireNotBlank("condition")
        ops.createEdge(
            policyId,
            permissionId,
            GrantsPermissionLabel.label,
            mapOf(GrantsPermissionLabel.condition.name to condition)
        )
    }

    fun applyPermission(permissionId: GraphElementId, resourceId: GraphElementId, scope: String = "resource") {
        scope.requireNotBlank("scope")
        ops.createEdge(permissionId, resourceId, AppliesToLabel.label, mapOf(AppliesToLabel.scope.name to scope))
    }

    fun grantTemporaryPermission(userId: GraphElementId, grantId: GraphElementId, permissionId: GraphElementId) {
        ops.createEdge(userId, grantId, HasTempGrantLabel.label, mapOf(HasTempGrantLabel.state.name to "active"))
        ops.createEdge(
            grantId,
            permissionId,
            TemporaryPermissionLabel.label,
            mapOf(TemporaryPermissionLabel.source.name to "break-glass")
        )
    }

    fun explainAccess(userId: String, resourceId: String, action: String): IamAccessExplanation {
        userId.requireNotBlank("userId")
        resourceId.requireNotBlank("resourceId")
        action.requireNotBlank("action")

        val user = userById(userId)
            ?: return denied(userId, resourceId, action, "User not found")

        denyPaths(user, resourceId, action).firstOrNull()?.let { path ->
            return IamAccessExplanation(
                userId = userId,
                resourceId = resourceId,
                action = action,
                allowed = false,
                path = path.map(::displayId),
                reason = "Denied by explicit policy path",
            )
        }

        allowPaths(user, resourceId, action).firstOrNull()?.let { path ->
            return IamAccessExplanation(
                userId = userId,
                resourceId = resourceId,
                action = action,
                allowed = true,
                path = path.map(::displayId),
                reason = "Granted by reachable IAM path",
            )
        }

        return denied(userId, resourceId, action, "No matching grant path")
    }

    fun riskyPrivilegeChains(userId: String): List<IamPrivilegeChain> {
        userId.requireNotBlank("userId")
        val user = userById(userId) ?: return emptyList()

        return principalPaths(user)
            .filter { path -> path.vertices.count { it.label == IamGroupLabel.label } >= 2 }
            .flatMap { principalPath ->
                outgoing(principalPath.vertex, HasRoleLabel.label, IamRoleLabel.label)
                    .filter { role -> role.properties[IamRoleLabel.privilege.name] == "admin" }
                    .map { role ->
                        IamPrivilegeChain(
                            userId = userId,
                            roleId = role.properties[IamRoleLabel.roleId.name].toString(),
                            path = (principalPath.vertices + role).map(::displayId),
                            reason = "Admin role inherited through nested groups",
                        )
                    }
            }
    }

    fun excessivePermissions(
        userId: String,
        approvedActionsByResource: Map<String, Set<String>>,
    ): List<IamAccessExplanation> {
        userId.requireNotBlank("userId")
        val user = userById(userId) ?: return emptyList()

        return allowPaths(user, resourceId = null, action = null)
            .map { path ->
                val permission = path.first { it.label == IamPermissionLabel.label }
                val resource = path.last()
                val resourceId = resource.properties[IamResourceLabel.resourceId.name].toString()
                val action = permission.properties[IamPermissionLabel.action.name].toString()
                IamAccessExplanation(
                    userId = userId,
                    resourceId = resourceId,
                    action = action,
                    allowed = true,
                    path = path.map(::displayId),
                    reason = "Granted action is outside the approved least-privilege set",
                )
            }
            .filter { explanation ->
                explanation.action !in approvedActionsByResource.orEmpty(explanation.resourceId)
            }
    }

    private fun allowPaths(user: GraphVertex, resourceId: String?, action: String?): List<List<GraphVertex>> =
        policyPaths(user, EFFECT_ALLOW, resourceId, action) + temporaryPaths(user, resourceId, action)

    private fun denyPaths(user: GraphVertex, resourceId: String, action: String): List<List<GraphVertex>> =
        policyPaths(user, EFFECT_DENY, resourceId, action)

    private fun policyPaths(
        user: GraphVertex,
        effect: String,
        resourceId: String?,
        action: String?,
    ): List<List<GraphVertex>> =
        principalPaths(user).flatMap { principalPath ->
            outgoing(principalPath.vertex, HasRoleLabel.label, IamRoleLabel.label).flatMap { role ->
                outgoing(role, AttachedPolicyLabel.label, IamPolicyLabel.label)
                    .filter { policy -> policy.properties[IamPolicyLabel.effect.name] == effect }
                    .flatMap { policy ->
                        outgoing(policy, GrantsPermissionLabel.label, IamPermissionLabel.label)
                            .filter { permission -> action == null || permission.properties[IamPermissionLabel.action.name] == action }
                            .flatMap { permission ->
                                matchingResources(permission, resourceId).map { resource ->
                                    principalPath.vertices + role + policy + permission + resource
                                }
                            }
                    }
            }
        }

    private fun temporaryPaths(user: GraphVertex, resourceId: String?, action: String?): List<List<GraphVertex>> =
        outgoing(user, HasTempGrantLabel.label, IamSessionGrantLabel.label).flatMap { grant ->
            outgoing(grant, TemporaryPermissionLabel.label, IamPermissionLabel.label)
                .filter { permission -> action == null || permission.properties[IamPermissionLabel.action.name] == action }
                .flatMap { permission ->
                    matchingResources(permission, resourceId).map { resource ->
                        listOf(user, grant, permission, resource)
                    }
                }
        }

    private fun principalPaths(user: GraphVertex): List<VertexPath> {
        val paths = mutableListOf(VertexPath(user, listOf(user)))
        val queue = ArrayDeque<VertexPath>()
        queue.add(VertexPath(user, listOf(user)))

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current.vertices.size > MAX_GROUP_DEPTH + 1) continue

            outgoing(current.vertex, MemberOfLabel.label, IamGroupLabel.label)
                .filterNot { group -> current.vertices.any { it.id == group.id } }
                .forEach { group ->
                    val next = VertexPath(group, current.vertices + group)
                    paths += next
                    queue.add(next)
                }
        }

        return paths
    }

    private fun matchingResources(permission: GraphVertex, resourceId: String?): List<GraphVertex> =
        outgoing(permission, AppliesToLabel.label, IamResourceLabel.label)
            .filter { resource -> resourceId == null || resource.properties[IamResourceLabel.resourceId.name] == resourceId }

    private fun outgoing(vertex: GraphVertex, edgeLabel: String, vertexLabel: String): List<GraphVertex> =
        ops.neighbors(
            vertex.id,
            NeighborOptions(edgeLabel = edgeLabel, direction = Direction.OUTGOING, maxDepth = 1)
        ).filter { it.label == vertexLabel }

    private fun userById(userId: String): GraphVertex? =
        ops.findVerticesByLabel(IamUserLabel.label, mapOf(IamUserLabel.userId.name to userId)).firstOrNull()

    private fun displayId(vertex: GraphVertex): String =
        when (vertex.label) {
            IamUserLabel.label         -> "user:${vertex.properties[IamUserLabel.userId.name]}"
            IamGroupLabel.label        -> "group:${vertex.properties[IamGroupLabel.groupId.name]}"
            IamRoleLabel.label         -> "role:${vertex.properties[IamRoleLabel.roleId.name]}"
            IamPolicyLabel.label       -> "policy:${vertex.properties[IamPolicyLabel.policyId.name]}"
            IamPermissionLabel.label   -> "permission:${vertex.properties[IamPermissionLabel.action.name]}"
            IamResourceLabel.label     -> "resource:${vertex.properties[IamResourceLabel.resourceId.name]}"
            IamSessionGrantLabel.label -> "grant:${vertex.properties[IamSessionGrantLabel.grantId.name]}"
            else                       -> "${vertex.label}:${vertex.id.value}"
        }

    private fun denied(userId: String, resourceId: String, action: String, reason: String): IamAccessExplanation =
        IamAccessExplanation(
            userId = userId,
            resourceId = resourceId,
            action = action,
            allowed = false,
            path = emptyList(),
            reason = reason,
        )

    private fun Map<String, Set<String>>.orEmpty(resourceId: String): Set<String> =
        this[resourceId] ?: emptySet()

    private class VertexPath(
        val vertex: GraphVertex,
        val vertices: List<GraphVertex>,
    )
}

/**
 * Seeds a compact IAM graph used by the README walkthrough and tests.
 */
object IamAccessSampleGraph {

    fun seed(service: IamAccessGraphService) {
        val alice = service.addUser("alice", "Alice Kim", "engineering")
        val bob = service.addUser("bob", "Bob Lee", "audit")
        val carol = service.addUser("carol", "Carol Park", "operations")
        val eve = service.addUser("eve", "Eve Contractor", "contractor")

        val engineering = service.addGroup("engineering", "Engineering")
        val platformAdmins = service.addGroup("platform-admins", "Platform Admins", riskTier = "privileged")

        val readOnly = service.addRole("readonly-role", "Read-only Analyst", "read")
        val deployer = service.addRole("deployer-role", "Staging Deployer", "write")
        val admin = service.addRole("prod-admin-role", "Production Admin", "admin")
        val contractor = service.addRole("contractor-role", "Contractor Guardrail", "restricted")

        val readPolicy = service.addPolicy("read-audit-policy", "Read audit dashboard", "allow")
        val deployPolicy = service.addPolicy("deploy-staging-policy", "Deploy staging service", "allow")
        val adminPolicy = service.addPolicy("prod-admin-policy", "Production administration", "allow")
        val denyDeletePolicy = service.addPolicy("deny-prod-delete-policy", "Deny production delete", "deny")

        val readDashboard = service.addPermission("read-audit-dashboard", "read")
        val deployStaging = service.addPermission("deploy-staging-service", "deploy")
        val deleteProd = service.addPermission("delete-prod-db", "delete")
        val readProd = service.addPermission("read-prod-db", "read")

        val auditDashboard = service.addResource("audit-dashboard", "Audit Dashboard", "dashboard", "internal")
        val stagingService = service.addResource("staging-service", "Staging Service", "service", "internal")
        val prodDb = service.addResource("prod-db", "Production Database", "database", "restricted")

        val emergencyGrant = service.addSessionGrant(
            "break-glass-1001",
            "temporary production read during incident",
            "2026-06-02T00:00:00Z",
        )

        service.addMembership(alice.id, engineering.id)
        service.addMembership(engineering.id, platformAdmins.id, kind = "nested")

        service.assignRole(bob.id, readOnly.id, source = "direct")
        service.assignRole(engineering.id, deployer.id, source = "group")
        service.assignRole(platformAdmins.id, admin.id, source = "nested-group")
        service.assignRole(eve.id, contractor.id, source = "contract")

        service.attachPolicy(readOnly.id, readPolicy.id)
        service.attachPolicy(deployer.id, deployPolicy.id)
        service.attachPolicy(admin.id, adminPolicy.id)
        service.attachPolicy(contractor.id, denyDeletePolicy.id)

        service.grantPermission(readPolicy.id, readDashboard.id)
        service.grantPermission(deployPolicy.id, deployStaging.id)
        service.grantPermission(adminPolicy.id, deleteProd.id)
        service.grantPermission(denyDeletePolicy.id, deleteProd.id)

        service.applyPermission(readDashboard.id, auditDashboard.id)
        service.applyPermission(deployStaging.id, stagingService.id)
        service.applyPermission(deleteProd.id, prodDb.id)
        service.applyPermission(readProd.id, prodDb.id)

        service.grantTemporaryPermission(carol.id, emergencyGrant.id, readProd.id)
    }
}
