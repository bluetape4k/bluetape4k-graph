package io.bluetape4k.graph.benchmark.abuser

import java.io.Serializable

/**
 * PostgreSQL abuser-detection benchmark backend contract.
 *
 * Implementations load the same deterministic [AbuserDetectionFixture] and return comparable detection metrics.
 */
interface AbuserDetectionEngine: AutoCloseable {
    val implementationName: String

    fun reset()

    fun load(fixture: AbuserDetectionFixture)

    fun detect(): AbuserDetectionResult

    override fun close()
}

data class AbuserAccount(
    val accountId: String,
    val segment: String,
    val knownAbusive: Boolean,
    val expectedAbusive: Boolean,
    val riskScore: Double,
    val accountAgeHours: Int,
    val sharedDeviceCluster: String,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

data class AbuseSignalEdge(
    val fromAccountId: String,
    val toAccountId: String,
    val kind: AbuseSignalKind,
    val weight: Double = 1.0,
    val amount: Double,
    val createdAtMinute: Int,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

enum class AbuseSignalKind {
    SHARED_DEVICE,
    SHARED_IP,
    SHARED_PAYMENT,
    TRANSFER,
    REPORT,
}

data class AbuserDetectionFixture(
    val size: AbuserDetectionSize,
    val scenario: AbuserDetectionScenario,
    val accounts: List<AbuserAccount>,
    val edges: List<AbuseSignalEdge>,
): Serializable {
    val expectedAbusiveAccountIds: Set<String> =
        accounts.asSequence()
            .filter { it.expectedAbusive }
            .map { it.accountId }
            .toSet()

    val knownAbusiveAccountIds: Set<String> =
        accounts.asSequence()
            .filter { it.knownAbusive }
            .map { it.accountId }
            .toSet()

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

enum class AbuserDetectionSize(val accountCount: Int) {
    SMOKE(120),
    SMALL(1_000),
    MEDIUM(10_000),
    LARGE(50_000),
    ;

    companion object {
        fun fromName(name: String): AbuserDetectionSize =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
                ?: error("Unsupported abuser detection fixture size: $name")
    }
}

enum class AbuserDetectionScenario(
    val displayName: String,
    val riskySourceCount: Int,
    val muleCount: Int,
    val minDistinctUpstream: Int,
    val hopLimit: Int,
    val windowStartMinute: Int,
    val riskThreshold: Double,
    val transferChainLength: Int,
    val noiseMultiplier: Int,
    val highDegreeHub: Boolean,
) {
    SHARED(
        displayName = "shared",
        riskySourceCount = 12,
        muleCount = 3,
        minDistinctUpstream = 3,
        hopLimit = 2,
        windowStartMinute = 60,
        riskThreshold = 0.80,
        transferChainLength = 2,
        noiseMultiplier = 1,
        highDegreeHub = false,
    ),
    TRANSFER(
        displayName = "transfer",
        riskySourceCount = 24,
        muleCount = 4,
        minDistinctUpstream = 4,
        hopLimit = 3,
        windowStartMinute = 60,
        riskThreshold = 0.80,
        transferChainLength = 4,
        noiseMultiplier = 2,
        highDegreeHub = false,
    ),
    NOISY_DENSE(
        displayName = "noisy-dense",
        riskySourceCount = 40,
        muleCount = 5,
        minDistinctUpstream = 5,
        hopLimit = 3,
        windowStartMinute = 60,
        riskThreshold = 0.80,
        transferChainLength = 3,
        noiseMultiplier = 8,
        highDegreeHub = true,
    ),
    WIDE_FANOUT(
        displayName = "wide-fanout",
        riskySourceCount = 48,
        muleCount = 4,
        minDistinctUpstream = 6,
        hopLimit = 3,
        windowStartMinute = 60,
        riskThreshold = 0.80,
        transferChainLength = 2,
        noiseMultiplier = 4,
        highDegreeHub = true,
    ),
    ;

    companion object {
        fun fromName(name: String): AbuserDetectionScenario =
            entries.firstOrNull { it.displayName.equals(name, ignoreCase = true) }
                ?: error("Unsupported abuser detection scenario: $name")
    }
}

data class AbuserDetectionResult(
    val implementationName: String,
    val predictedAbusiveAccountIds: Set<String>,
    val expectedAbusiveAccountIds: Set<String>,
): Serializable {
    val metrics: AbuserDetectionMetrics =
        AbuserDetectionMetrics.from(expectedAbusiveAccountIds, predictedAbusiveAccountIds)

    val candidateCount: Int get() = predictedAbusiveAccountIds.size

    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

data class AbuserDetectionMetrics(
    val truePositives: Int,
    val falsePositives: Int,
    val falseNegatives: Int,
    val precision: Double,
    val recall: Double,
    val f1: Double,
): Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L

        fun from(expected: Set<String>, predicted: Set<String>): AbuserDetectionMetrics {
            val truePositives = predicted.count { it in expected }
            val falsePositives = predicted.count { it !in expected }
            val falseNegatives = expected.count { it !in predicted }
            val precision = truePositives.ratio(truePositives + falsePositives)
            val recall = truePositives.ratio(truePositives + falseNegatives)
            val f1 = if (precision + recall == 0.0) {
                0.0
            } else {
                2.0 * precision * recall / (precision + recall)
            }

            return AbuserDetectionMetrics(
                truePositives = truePositives,
                falsePositives = falsePositives,
                falseNegatives = falseNegatives,
                precision = precision,
                recall = recall,
                f1 = f1,
            )
        }

        private fun Int.ratio(denominator: Int): Double =
            if (denominator == 0) 1.0 else this.toDouble() / denominator.toDouble()
    }
}
