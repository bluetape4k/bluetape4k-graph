package io.bluetape4k.graph.benchmark.authz

import java.io.Serializable

interface AuthzInheritanceEngine: AutoCloseable {
    val implementationName: String

    fun reset()

    fun load(fixture: AuthzInheritanceFixture)

    fun resolve(): AuthzInheritanceResult

    override fun close()
}

data class AuthzNode(
    val nodeId: String,
    val kind: AuthzNodeKind,
    val publicApi: Boolean,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

data class AuthzEdge(
    val fromNodeId: String,
    val toNodeId: String,
    val kind: AuthzEdgeKind,
    val effect: AuthzEffect,
    val active: Boolean,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

enum class AuthzNodeKind {
    USER,
    GROUP,
    ROLE,
    RESOURCE,
}

enum class AuthzEdgeKind {
    MEMBER_OF,
    ASSIGNED_ROLE,
    GRANTS,
}

enum class AuthzEffect {
    ALLOW,
    DENY,
}

data class AuthzInheritanceFixture(
    val size: AuthzInheritanceSize,
    val scenario: AuthzInheritanceScenario,
    val targetUserId: String,
    val nodes: List<AuthzNode>,
    val edges: List<AuthzEdge>,
): Serializable {
    val expectedResourceIds: Set<String> =
        AuthzInheritanceOracle.resolve(this)

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

enum class AuthzInheritanceSize(
    val userCount: Int,
    val groupCount: Int,
    val roleCount: Int,
    val resourceCount: Int,
) {
    SMOKE(20, 30, 20, 60),
    SMALL(200, 500, 300, 1_000),
    MEDIUM(2_000, 5_000, 2_000, 10_000),
    LARGE(10_000, 25_000, 10_000, 50_000),
    XLARGE(25_000, 75_000, 25_000, 150_000),
    ;

    companion object {
        fun fromName(name: String): AuthzInheritanceSize =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: error("Unsupported authorization fixture size: $name")
    }
}

enum class AuthzInheritanceScenario(
    val displayName: String,
    val hopLimit: Int,
    val groupFanout: Int,
    val roleFanout: Int,
    val resourceFanout: Int,
    val targetChainLength: Int,
    val backgroundGroupStride: Int,
    val denyEvery: Int,
    val inactiveEvery: Int,
    val cycleEvery: Int,
) {
    SHALLOW(displayName = "shallow", hopLimit = 3, groupFanout = 3, roleFanout = 2, resourceFanout = 4, targetChainLength = 0, backgroundGroupStride = 3, denyEvery = 11, inactiveEvery = 17, cycleEvery = 0),
    DEEP_INHERITANCE(displayName = "deep-inheritance", hopLimit = 6, groupFanout = 3, roleFanout = 2, resourceFanout = 3, targetChainLength = 3, backgroundGroupStride = 3, denyEvery = 13, inactiveEvery = 19, cycleEvery = 7),
    DENY_HEAVY(displayName = "deny-heavy", hopLimit = 5, groupFanout = 4, roleFanout = 3, resourceFanout = 3, targetChainLength = 2, backgroundGroupStride = 3, denyEvery = 4, inactiveEvery = 23, cycleEvery = 11),
    WIDE_GROUPS(displayName = "wide-groups", hopLimit = 4, groupFanout = 12, roleFanout = 2, resourceFanout = 2, targetChainLength = 1, backgroundGroupStride = 3, denyEvery = 17, inactiveEvery = 29, cycleEvery = 9),
    LONG_CHAIN(displayName = "long-chain", hopLimit = 10, groupFanout = 2, roleFanout = 2, resourceFanout = 3, targetChainLength = 7, backgroundGroupStride = 5, denyEvery = 19, inactiveEvery = 31, cycleEvery = 13),
    DEEP_WIDE(displayName = "deep-wide", hopLimit = 12, groupFanout = 8, roleFanout = 4, resourceFanout = 4, targetChainLength = 9, backgroundGroupStride = 4, denyEvery = 17, inactiveEvery = 37, cycleEvery = 11),
    ;

    companion object {
        fun fromName(name: String): AuthzInheritanceScenario =
            entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) }
                ?: error("Unsupported authorization scenario: $name")
    }
}

data class AuthzInheritanceResult(
    val implementationName: String,
    val resourceIds: Set<String>,
    val expectedResourceIds: Set<String>,
): Serializable {
    val metrics: AuthzInheritanceMetrics =
        AuthzInheritanceMetrics.from(expectedResourceIds, resourceIds)

    val resourceCount: Int get() = resourceIds.size

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

data class AuthzInheritanceMetrics(
    val truePositives: Int,
    val falsePositives: Int,
    val falseNegatives: Int,
    val f1: Double,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L

        fun from(expected: Set<String>, actual: Set<String>): AuthzInheritanceMetrics {
            val truePositives = actual.count { it in expected }
            val falsePositives = actual.count { it !in expected }
            val falseNegatives = expected.count { it !in actual }
            val precision = truePositives.ratio(truePositives + falsePositives)
            val recall = truePositives.ratio(truePositives + falseNegatives)
            val f1 = if (precision + recall == 0.0) 0.0 else 2.0 * precision * recall / (precision + recall)
            return AuthzInheritanceMetrics(truePositives, falsePositives, falseNegatives, f1)
        }

        private fun Int.ratio(denominator: Int): Double =
            if (denominator == 0) 1.0 else toDouble() / denominator.toDouble()
    }
}
