package io.bluetape4k.graph.examples.securityattack.schema

import io.bluetape4k.graph.schema.EdgeLabel
import io.bluetape4k.graph.schema.VertexLabel

/**
 * Attack path를 시작할 수 있는 external 또는 internal entry point이다.
 */
object EntryAssetLabel: VertexLabel("EntryAsset") {
    private fun readResolve(): Any = EntryAssetLabel
    val assetId = string("assetId")
    val name = string("name")
    val exposure = string("exposure")
    val status = string("status")
}

/**
 * Attack path에 참여할 수 있는 host, workload, data store이다.
 */
object HostLabel: VertexLabel("Host") {
    private fun readResolve(): Any = HostLabel
    val hostId = string("hostId")
    val name = string("name")
    val exposure = string("exposure")
    val criticality = string("criticality")
    val status = string("status")
}

/**
 * Human, service, assumed identity이다.
 */
object PrincipalLabel: VertexLabel("Principal") {
    private fun readResolve(): Any = PrincipalLabel
    val principalId = string("principalId")
    val name = string("name")
    val kind = string("kind")
    val privilege = string("privilege")
    val status = string("status")
}

/**
 * 다른 identity를 unlock할 수 있는 credential 또는 token material이다.
 */
object CredentialLabel: VertexLabel("Credential") {
    private fun readResolve(): Any = CredentialLabel
    val credentialId = string("credentialId")
    val name = string("name")
    val kind = string("kind")
    val status = string("status")
}

/**
 * Educational risk scoring에만 사용하는 vulnerability signal이다.
 */
object VulnerabilityLabel: VertexLabel("Vulnerability") {
    private fun readResolve(): Any = VulnerabilityLabel
    val vulnerabilityId = string("vulnerabilityId")
    val name = string("name")
    val severity = string("severity")
    val status = string("status")
}

/**
 * Principal에 attach된 permission 또는 resource capability이다.
 */
object PermissionLabel: VertexLabel("Permission") {
    private fun readResolve(): Any = PermissionLabel
    val permissionId = string("permissionId")
    val name = string("name")
    val privilege = string("privilege")
    val status = string("status")
}

/**
 * Reachability 또는 lateral-movement edge이다.
 */
object CanReachLabel: EdgeLabel("CAN_REACH", EntryAssetLabel, HostLabel) {
    private fun readResolve(): Any = CanReachLabel
    val edgeId = string("edgeId")
    val kind = string("kind")
    val status = string("status")
}

/**
 * Attacker-controlled node에서 vulnerability로 이어지는 exploit relationship이다.
 */
object ExploitsLabel: EdgeLabel("EXPLOITS", HostLabel, VulnerabilityLabel) {
    private fun readResolve(): Any = ExploitsLabel
    val edgeId = string("edgeId")
    val technique = string("technique")
    val status = string("status")
}

/**
 * Vulnerability에서 host로 이어지는 compromise relationship이다.
 */
object CompromisesLabel: EdgeLabel("COMPROMISES", VulnerabilityLabel, PrincipalLabel) {
    private fun readResolve(): Any = CompromisesLabel
    val edgeId = string("edgeId")
    val impact = string("impact")
    val status = string("status")
}

/**
 * Host에서 principal로 이어지는 execution context이다.
 */
object RunsAsLabel: EdgeLabel("RUNS_AS", HostLabel, PrincipalLabel) {
    private fun readResolve(): Any = RunsAsLabel
    val edgeId = string("edgeId")
    val kind = string("kind")
    val status = string("status")
}

/**
 * Credential discovery edge이다.
 */
object HasCredentialLabel: EdgeLabel("HAS_CREDENTIAL", PrincipalLabel, CredentialLabel) {
    private fun readResolve(): Any = HasCredentialLabel
    val edgeId = string("edgeId")
    val location = string("location")
    val status = string("status")
}

/**
 * Credential에서 principal로 이어지는 access edge이다.
 */
object GrantsAccessLabel: EdgeLabel("GRANTS_ACCESS", CredentialLabel, PrincipalLabel) {
    private fun readResolve(): Any = GrantsAccessLabel
    val edgeId = string("edgeId")
    val method = string("method")
    val status = string("status")
}

/**
 * Principal에서 permission으로 이어지는 edge이다.
 */
object HasPermissionLabel: EdgeLabel("HAS_PERMISSION", PrincipalLabel, PermissionLabel) {
    private fun readResolve(): Any = HasPermissionLabel
    val edgeId = string("edgeId")
    val scope = string("scope")
    val status = string("status")
}

/**
 * Permission에서 host로 이어지는 asset-control edge이다.
 */
object ControlsAssetLabel: EdgeLabel("CONTROLS_ASSET", PermissionLabel, HostLabel) {
    private fun readResolve(): Any = ControlsAssetLabel
    val edgeId = string("edgeId")
    val scope = string("scope")
    val status = string("status")
}
