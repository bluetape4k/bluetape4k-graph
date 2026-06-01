package io.bluetape4k.graph.examples.securityattack.service

import io.bluetape4k.graph.examples.securityattack.schema.CanReachLabel
import io.bluetape4k.graph.examples.securityattack.schema.CompromisesLabel
import io.bluetape4k.graph.examples.securityattack.schema.ControlsAssetLabel
import io.bluetape4k.graph.examples.securityattack.schema.CredentialLabel
import io.bluetape4k.graph.examples.securityattack.schema.EntryAssetLabel
import io.bluetape4k.graph.examples.securityattack.schema.ExploitsLabel
import io.bluetape4k.graph.examples.securityattack.schema.GrantsAccessLabel
import io.bluetape4k.graph.examples.securityattack.schema.HasCredentialLabel
import io.bluetape4k.graph.examples.securityattack.schema.HasPermissionLabel
import io.bluetape4k.graph.examples.securityattack.schema.HostLabel
import io.bluetape4k.graph.examples.securityattack.schema.PermissionLabel
import io.bluetape4k.graph.examples.securityattack.schema.PrincipalLabel
import io.bluetape4k.graph.examples.securityattack.schema.RunsAsLabel
import io.bluetape4k.graph.examples.securityattack.schema.VulnerabilityLabel
import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.logging.info
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.flow.toList

/**
 * Coroutine version of [SecurityAttackPathService].
 */
class SecurityAttackPathSuspendService(
    private val ops: GraphSuspendOperations,
    private val graphName: String = "security_attack_path",
) {
    companion object: KLoggingChannel()

    suspend fun initialize() {
        if (!ops.graphExists(graphName)) {
            ops.createGraph(graphName)
            log.info { "Security attack-path graph '$graphName' created" }
        }
    }

    suspend fun shortestAttackPath(
        sourceAssetId: String,
        targetHostId: String,
        blockedNodeIds: Set<String> = emptySet(),
        blockedEdgeIds: Set<String> = emptySet(),
    ): SecurityAttackPath? {
        sourceAssetId.requireNotBlank("sourceAssetId")
        targetHostId.requireNotBlank("targetHostId")

        val source = entryAssetById(sourceAssetId) ?: return null
        val target = hostById(targetHostId) ?: return null
        return shortestPath(source, target, blockedNodeIds, blockedEdgeIds)
            ?.toAttackPath("shortest active attack path")
    }

    suspend fun rankedAttackPaths(
        sourceAssetId: String,
        targetHostId: String,
        maxDepth: Int = 8,
    ): List<SecurityAttackPath> {
        sourceAssetId.requireNotBlank("sourceAssetId")
        targetHostId.requireNotBlank("targetHostId")
        require(maxDepth > 0) { "maxDepth must be > 0, was $maxDepth" }

        val source = entryAssetById(sourceAssetId) ?: return emptyList()
        val target = hostById(targetHostId) ?: return emptyList()
        val paths = mutableListOf<List<GraphVertex>>()
        val queue = ArrayDeque<List<GraphVertex>>()
        queue += listOf(source)

        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val tail = path.last()
            if (path.size > maxDepth + 1) {
                continue
            }
            if (tail.id == target.id) {
                paths += path
                continue
            }
            activeOutgoingNeighbors(tail)
                .filterNot { next -> path.any { it.id == next.id } }
                .forEach { next -> queue += path + next }
        }

        return paths
            .distinctBy { path -> path.map { it.id } }
            .map { it.toAttackPath("ranked attack path candidate") }
            .sortedWith(compareByDescending<SecurityAttackPath> { it.riskScore }.thenBy { it.nodeIds.size })
    }

    suspend fun privilegeEscalationPaths(
        startPrincipalId: String,
        targetPrivilege: String = "admin",
        maxDepth: Int = 5,
    ): List<SecurityAttackPath> {
        startPrincipalId.requireNotBlank("startPrincipalId")
        targetPrivilege.requireNotBlank("targetPrivilege")
        require(maxDepth > 0) { "maxDepth must be > 0, was $maxDepth" }

        val source = principalById(startPrincipalId) ?: return emptyList()
        val paths = mutableListOf<List<GraphVertex>>()
        val queue = ArrayDeque<List<GraphVertex>>()
        queue += listOf(source)

        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val tail = path.last()
            if (path.size > maxDepth + 1) {
                continue
            }
            if (tail !== source && hasPrivilege(tail, targetPrivilege)) {
                paths += path
                continue
            }
            activeOutgoingNeighbors(
                tail,
                labels = listOf(HasCredentialLabel.label, GrantsAccessLabel.label, HasPermissionLabel.label),
            )
                .filterNot { next -> path.any { it.id == next.id } }
                .forEach { next -> queue += path + next }
        }

        return paths
            .distinctBy { path -> path.map { it.id } }
            .map { it.toAttackPath("privilege escalation path") }
            .sortedBy { it.nodeIds.size }
    }

    suspend fun unreachableCrownJewels(sourceAssetId: String): List<GraphVertex> {
        sourceAssetId.requireNotBlank("sourceAssetId")
        val source = entryAssetById(sourceAssetId) ?: return emptyList()
        return crownJewelHosts()
            .filter { target -> shortestPath(source, target) == null }
            .distinctBy { it.id }
    }

    suspend fun remediationImpact(
        blockedEdgeId: String,
        sourceAssetId: String = "internet",
    ): List<GraphVertex> {
        blockedEdgeId.requireNotBlank("blockedEdgeId")
        sourceAssetId.requireNotBlank("sourceAssetId")

        val source = entryAssetById(sourceAssetId) ?: return emptyList()
        return crownJewelHosts()
            .filter { target ->
                shortestPath(source, target) != null &&
                    shortestPath(source, target, blockedEdgeIds = setOf(blockedEdgeId)) == null
            }
            .distinctBy { it.id }
    }

    private suspend fun shortestPath(
        source: GraphVertex,
        target: GraphVertex,
        blockedNodeIds: Set<String> = emptySet(),
        blockedEdgeIds: Set<String> = emptySet(),
    ): List<GraphVertex>? {
        if (businessId(source) in blockedNodeIds || businessId(target) in blockedNodeIds) {
            return null
        }

        val visited = mutableSetOf(source.id)
        val queue = ArrayDeque<List<GraphVertex>>()
        queue += listOf(source)

        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val tail = path.last()
            if (tail.id == target.id) {
                return path
            }
            activeOutgoingNeighbors(tail, blockedEdgeIds = blockedEdgeIds)
                .filterNot { next -> next.id in visited || businessId(next) in blockedNodeIds }
                .forEach { next ->
                    visited += next.id
                    queue += path + next
                }
        }

        return null
    }

    private suspend fun activeOutgoingNeighbors(
        vertex: GraphVertex,
        labels: List<String> = attackEdgeLabels,
        blockedEdgeIds: Set<String> = emptySet(),
    ): List<GraphVertex> =
        labels.flatMap { label -> ops.findEdgesByLabel(label).toList().filter { it.startId == vertex.id } }
            .filter { edge -> edge.properties["status"] == "active" && edge.edgeBusinessId() !in blockedEdgeIds }
            .mapNotNull { edge -> ops.findVertexById(edge.endId) }
            .filter { it.properties["status"] == "active" }
            .distinctBy { it.id }

    private fun List<GraphVertex>.toAttackPath(reason: String): SecurityAttackPath =
        SecurityAttackPath(
            nodeIds = map(::businessId),
            riskScore = sumOf(::riskContribution),
            reason = reason,
        )

    private fun hasPrivilege(vertex: GraphVertex, targetPrivilege: String): Boolean =
        when (vertex.label) {
            PrincipalLabel.label  -> vertex.properties[PrincipalLabel.privilege.name] == targetPrivilege
            PermissionLabel.label -> vertex.properties[PermissionLabel.privilege.name] == targetPrivilege
            else                  -> false
        }

    private suspend fun crownJewelHosts(): List<GraphVertex> =
        ops.findVerticesByLabel(HostLabel.label)
            .toList()
            .filter { it.properties[HostLabel.criticality.name] == "crown_jewel" }

    private suspend fun entryAssetById(assetId: String): GraphVertex? =
        ops.findVerticesByLabel(EntryAssetLabel.label, mapOf(EntryAssetLabel.assetId.name to assetId))
            .toList()
            .firstOrNull()

    private suspend fun hostById(hostId: String): GraphVertex? =
        ops.findVerticesByLabel(HostLabel.label, mapOf(HostLabel.hostId.name to hostId))
            .toList()
            .firstOrNull()

    private suspend fun principalById(principalId: String): GraphVertex? =
        ops.findVerticesByLabel(PrincipalLabel.label, mapOf(PrincipalLabel.principalId.name to principalId))
            .toList()
            .firstOrNull()

    private fun businessId(vertex: GraphVertex): String =
        when (vertex.label) {
            EntryAssetLabel.label    -> vertex.properties[EntryAssetLabel.assetId.name].toString()
            HostLabel.label          -> vertex.properties[HostLabel.hostId.name].toString()
            PrincipalLabel.label     -> vertex.properties[PrincipalLabel.principalId.name].toString()
            CredentialLabel.label    -> vertex.properties[CredentialLabel.credentialId.name].toString()
            VulnerabilityLabel.label -> vertex.properties[VulnerabilityLabel.vulnerabilityId.name].toString()
            PermissionLabel.label    -> vertex.properties[PermissionLabel.permissionId.name].toString()
            else                     -> vertex.id.value
        }

    private fun riskContribution(vertex: GraphVertex): Int =
        when (vertex.label) {
            VulnerabilityLabel.label -> vertex.properties[VulnerabilityLabel.severity.name].toString().toIntOrNull() ?: 0
            HostLabel.label          -> if (vertex.properties[HostLabel.criticality.name] == "crown_jewel") 5 else 1
            PrincipalLabel.label     -> if (vertex.properties[PrincipalLabel.privilege.name] == "admin") 4 else 1
            PermissionLabel.label    -> if (vertex.properties[PermissionLabel.privilege.name] == "admin") 3 else 1
            else                     -> 1
        }

    private fun GraphEdge.edgeBusinessId(): String =
        properties["edgeId"]?.toString() ?: id.value
}

private val attackEdgeLabels = listOf(
    ExploitsLabel.label,
    CanReachLabel.label,
    CompromisesLabel.label,
    RunsAsLabel.label,
    HasCredentialLabel.label,
    GrantsAccessLabel.label,
    HasPermissionLabel.label,
    ControlsAssetLabel.label,
)
