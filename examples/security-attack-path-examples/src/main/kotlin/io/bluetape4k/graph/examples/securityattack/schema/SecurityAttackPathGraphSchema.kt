package io.bluetape4k.graph.examples.securityattack.schema

import io.bluetape4k.graph.schema.EdgeLabel
import io.bluetape4k.graph.schema.VertexLabel

/**
 * External or internal entry point that can start an attack path.
 */
object EntryAssetLabel: VertexLabel("EntryAsset") {
    val assetId = string("assetId")
    val name = string("name")
    val exposure = string("exposure")
    val status = string("status")
}

/**
 * Host, workload, or data store that can participate in an attack path.
 */
object HostLabel: VertexLabel("Host") {
    val hostId = string("hostId")
    val name = string("name")
    val exposure = string("exposure")
    val criticality = string("criticality")
    val status = string("status")
}

/**
 * Human, service, or assumed identity.
 */
object PrincipalLabel: VertexLabel("Principal") {
    val principalId = string("principalId")
    val name = string("name")
    val kind = string("kind")
    val privilege = string("privilege")
    val status = string("status")
}

/**
 * Credential or token material that may unlock another identity.
 */
object CredentialLabel: VertexLabel("Credential") {
    val credentialId = string("credentialId")
    val name = string("name")
    val kind = string("kind")
    val status = string("status")
}

/**
 * Vulnerability signal used only for educational risk scoring.
 */
object VulnerabilityLabel: VertexLabel("Vulnerability") {
    val vulnerabilityId = string("vulnerabilityId")
    val name = string("name")
    val severity = string("severity")
    val status = string("status")
}

/**
 * Permission or resource capability attached to a principal.
 */
object PermissionLabel: VertexLabel("Permission") {
    val permissionId = string("permissionId")
    val name = string("name")
    val privilege = string("privilege")
    val status = string("status")
}

/**
 * Reachability or lateral-movement edge.
 */
object CanReachLabel: EdgeLabel("CAN_REACH", EntryAssetLabel, HostLabel) {
    val edgeId = string("edgeId")
    val kind = string("kind")
    val status = string("status")
}

/**
 * Exploit relationship from an attacker-controlled node to a vulnerability.
 */
object ExploitsLabel: EdgeLabel("EXPLOITS", HostLabel, VulnerabilityLabel) {
    val edgeId = string("edgeId")
    val technique = string("technique")
    val status = string("status")
}

/**
 * Vulnerability-to-host compromise relationship.
 */
object CompromisesLabel: EdgeLabel("COMPROMISES", VulnerabilityLabel, PrincipalLabel) {
    val edgeId = string("edgeId")
    val impact = string("impact")
    val status = string("status")
}

/**
 * Host-to-principal execution context.
 */
object RunsAsLabel: EdgeLabel("RUNS_AS", HostLabel, PrincipalLabel) {
    val edgeId = string("edgeId")
    val kind = string("kind")
    val status = string("status")
}

/**
 * Credential discovery edge.
 */
object HasCredentialLabel: EdgeLabel("HAS_CREDENTIAL", PrincipalLabel, CredentialLabel) {
    val edgeId = string("edgeId")
    val location = string("location")
    val status = string("status")
}

/**
 * Credential-to-principal access edge.
 */
object GrantsAccessLabel: EdgeLabel("GRANTS_ACCESS", CredentialLabel, PrincipalLabel) {
    val edgeId = string("edgeId")
    val method = string("method")
    val status = string("status")
}

/**
 * Principal-to-permission edge.
 */
object HasPermissionLabel: EdgeLabel("HAS_PERMISSION", PrincipalLabel, PermissionLabel) {
    val edgeId = string("edgeId")
    val scope = string("scope")
    val status = string("status")
}

/**
 * Permission-to-host asset-control edge.
 */
object ControlsAssetLabel: EdgeLabel("CONTROLS_ASSET", PermissionLabel, HostLabel) {
    val edgeId = string("edgeId")
    val scope = string("scope")
    val status = string("status")
}
