package io.bluetape4k.graph.examples.iam.schema

import io.bluetape4k.graph.schema.EdgeLabel
import io.bluetape4k.graph.schema.VertexLabel

/**
 * IAM access-path example에서 사용하는 human identity vertex이다.
 */
object IamUserLabel: VertexLabel("IamUser") {
    private fun readResolve(): Any = IamUserLabel
    val userId = string("userId")
    val displayName = string("displayName")
    val department = string("department")
}

/**
 * User 또는 다른 group을 포함할 수 있는 group vertex이다.
 */
object IamGroupLabel: VertexLabel("IamGroup") {
    private fun readResolve(): Any = IamGroupLabel
    val groupId = string("groupId")
    val name = string("name")
    val riskTier = string("riskTier")
}

/**
 * User 또는 group이 assume하는 role vertex이다.
 */
object IamRoleLabel: VertexLabel("IamRole") {
    private fun readResolve(): Any = IamRoleLabel
    val roleId = string("roleId")
    val name = string("name")
    val privilege = string("privilege")
}

/**
 * Role에 attach되는 policy vertex이다.
 */
object IamPolicyLabel: VertexLabel("IamPolicy") {
    private fun readResolve(): Any = IamPolicyLabel
    val policyId = string("policyId")
    val name = string("name")
    val effect = string("effect")
}

/**
 * Action을 하나 이상의 resource에 bind하는 permission vertex이다.
 */
object IamPermissionLabel: VertexLabel("IamPermission") {
    private fun readResolve(): Any = IamPermissionLabel
    val permissionId = string("permissionId")
    val action = string("action")
}

/**
 * Protected resource vertex이다.
 */
object IamResourceLabel: VertexLabel("IamResource") {
    private fun readResolve(): Any = IamResourceLabel
    val resourceId = string("resourceId")
    val name = string("name")
    val resourceType = string("resourceType")
    val classification = string("classification")
}

/**
 * Break-glass 또는 emergency access record 같은 temporary grant vertex이다.
 */
object IamSessionGrantLabel: VertexLabel("IamSessionGrant") {
    private fun readResolve(): Any = IamSessionGrantLabel
    val grantId = string("grantId")
    val reason = string("reason")
    val expiresAt = string("expiresAt")
}

/**
 * User에서 group 또는 group에서 group으로 이어지는 membership edge이다.
 */
object MemberOfLabel: EdgeLabel("MEMBER_OF", IamUserLabel, IamGroupLabel) {
    private fun readResolve(): Any = MemberOfLabel
    val kind = string("kind")
}

/**
 * Principal에서 role로 이어지는 assignment edge이다.
 */
object HasRoleLabel: EdgeLabel("HAS_ROLE", IamUserLabel, IamRoleLabel) {
    private fun readResolve(): Any = HasRoleLabel
    val source = string("source")
}

/**
 * Role에서 policy로 이어지는 attachment edge이다.
 */
object AttachedPolicyLabel: EdgeLabel("ATTACHED_POLICY", IamRoleLabel, IamPolicyLabel) {
    private fun readResolve(): Any = AttachedPolicyLabel
    val scope = string("scope")
}

/**
 * Policy에서 permission으로 이어지는 grant edge이다.
 */
object GrantsPermissionLabel: EdgeLabel("GRANTS_PERMISSION", IamPolicyLabel, IamPermissionLabel) {
    private fun readResolve(): Any = GrantsPermissionLabel
    val condition = string("condition")
}

/**
 * Permission에서 resource로 이어지는 application edge이다.
 */
object AppliesToLabel: EdgeLabel("APPLIES_TO", IamPermissionLabel, IamResourceLabel) {
    private fun readResolve(): Any = AppliesToLabel
    val scope = string("scope")
}

/**
 * User에서 temporary grant로 이어지는 edge이다.
 */
object HasTempGrantLabel: EdgeLabel("HAS_TEMP_GRANT", IamUserLabel, IamSessionGrantLabel) {
    private fun readResolve(): Any = HasTempGrantLabel
    val state = string("state")
}

/**
 * Temporary grant에서 permission으로 이어지는 edge이다.
 */
object TemporaryPermissionLabel: EdgeLabel("TEMPORARY_PERMISSION", IamSessionGrantLabel, IamPermissionLabel) {
    private fun readResolve(): Any = TemporaryPermissionLabel
    val source = string("source")
}
