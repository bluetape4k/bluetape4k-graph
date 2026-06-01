package io.bluetape4k.graph.examples.iam.schema

import io.bluetape4k.graph.schema.EdgeLabel
import io.bluetape4k.graph.schema.VertexLabel

/**
 * Human identity vertices used by the IAM access-path example.
 */
object IamUserLabel: VertexLabel("IamUser") {
    val userId = string("userId")
    val displayName = string("displayName")
    val department = string("department")
}

/**
 * Group vertices that can contain users or other groups.
 */
object IamGroupLabel: VertexLabel("IamGroup") {
    val groupId = string("groupId")
    val name = string("name")
    val riskTier = string("riskTier")
}

/**
 * Role vertices assumed by users or groups.
 */
object IamRoleLabel: VertexLabel("IamRole") {
    val roleId = string("roleId")
    val name = string("name")
    val privilege = string("privilege")
}

/**
 * Policy vertices attached to roles.
 */
object IamPolicyLabel: VertexLabel("IamPolicy") {
    val policyId = string("policyId")
    val name = string("name")
    val effect = string("effect")
}

/**
 * Permission vertices that bind an action to one or more resources.
 */
object IamPermissionLabel: VertexLabel("IamPermission") {
    val permissionId = string("permissionId")
    val action = string("action")
}

/**
 * Protected resource vertices.
 */
object IamResourceLabel: VertexLabel("IamResource") {
    val resourceId = string("resourceId")
    val name = string("name")
    val resourceType = string("resourceType")
    val classification = string("classification")
}

/**
 * Temporary grant vertices such as break-glass or emergency access records.
 */
object IamSessionGrantLabel: VertexLabel("IamSessionGrant") {
    val grantId = string("grantId")
    val reason = string("reason")
    val expiresAt = string("expiresAt")
}

/**
 * User-to-group or group-to-group membership edges.
 */
object MemberOfLabel: EdgeLabel("MEMBER_OF", IamUserLabel, IamGroupLabel) {
    val kind = string("kind")
}

/**
 * Principal-to-role assignment edges.
 */
object HasRoleLabel: EdgeLabel("HAS_ROLE", IamUserLabel, IamRoleLabel) {
    val source = string("source")
}

/**
 * Role-to-policy attachment edges.
 */
object AttachedPolicyLabel: EdgeLabel("ATTACHED_POLICY", IamRoleLabel, IamPolicyLabel) {
    val scope = string("scope")
}

/**
 * Policy-to-permission grant edges.
 */
object GrantsPermissionLabel: EdgeLabel("GRANTS_PERMISSION", IamPolicyLabel, IamPermissionLabel) {
    val condition = string("condition")
}

/**
 * Permission-to-resource application edges.
 */
object AppliesToLabel: EdgeLabel("APPLIES_TO", IamPermissionLabel, IamResourceLabel) {
    val scope = string("scope")
}

/**
 * User-to-temporary-grant edges.
 */
object HasTempGrantLabel: EdgeLabel("HAS_TEMP_GRANT", IamUserLabel, IamSessionGrantLabel) {
    val state = string("state")
}

/**
 * Temporary-grant-to-permission edges.
 */
object TemporaryPermissionLabel: EdgeLabel("TEMPORARY_PERMISSION", IamSessionGrantLabel, IamPermissionLabel) {
    val source = string("source")
}
