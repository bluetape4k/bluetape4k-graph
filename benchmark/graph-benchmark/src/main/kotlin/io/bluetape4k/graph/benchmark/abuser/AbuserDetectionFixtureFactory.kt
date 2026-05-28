package io.bluetape4k.graph.benchmark.abuser

/**
 * Builds deterministic fixtures for the PostgreSQL abuser-detection benchmark.
 */
object AbuserDetectionFixtureFactory {

    fun create(
        size: AbuserDetectionSize,
        scenario: AbuserDetectionScenario = AbuserDetectionScenario.SHARED,
    ): AbuserDetectionFixture {
        val accountIds = (0 until size.accountCount).map { index -> accountId(index) }
        val knownIds = accountIds.take(scenario.knownAbuserCount.coerceAtMost(size.accountCount))
        val edges = buildEdges(accountIds, knownIds, scenario)
        val expectedIds = expectedAbusiveIds(knownIds, edges)

        val accounts = accountIds.mapIndexed { index, id ->
            AbuserAccount(
                accountId = id,
                segment = "segment-${index % 5}",
                knownAbusive = id in knownIds,
                expectedAbusive = id in expectedIds,
            )
        }

        return AbuserDetectionFixture(size, scenario, accounts, edges)
    }

    private fun buildEdges(
        accountIds: List<String>,
        knownIds: List<String>,
        scenario: AbuserDetectionScenario,
    ): List<AbuseSignalEdge> {
        val edges = ArrayList<AbuseSignalEdge>(accountIds.size * scenario.noiseMultiplier)
        val maxIndex = accountIds.lastIndex
        val knownCount = knownIds.size.coerceAtLeast(1)

        knownIds.forEachIndexed { knownIndex, knownId ->
            repeat(scenario.sharedSignalFanout) { offset ->
                val targetIndex = boundedIndex(knownCount + knownIndex * scenario.sharedSignalFanout + offset, maxIndex)
                edges += AbuseSignalEdge(
                    fromAccountId = knownId,
                    toAccountId = accountIds[targetIndex],
                    kind = SHARED_SIGNAL_KINDS[offset % SHARED_SIGNAL_KINDS.size],
                    weight = 0.85 + (offset % 3) * 0.05,
                )
            }

            repeat(scenario.transferChainCount) { chain ->
                var from = knownId
                repeat(scenario.transferChainLength) { depth ->
                    val targetIndex = boundedIndex(
                        knownCount + 64 + knownIndex * 31 + chain * scenario.transferChainLength + depth,
                        maxIndex,
                    )
                    val to = accountIds[targetIndex]
                    edges += AbuseSignalEdge(from, to, AbuseSignalKind.TRANSFER, 0.70)
                    from = to
                }
            }
        }

        val noiseStart = (scenario.knownAbuserCount * 24).coerceAtMost(maxIndex)
        for (index in noiseStart until maxIndex) {
            repeat(scenario.noiseMultiplier) { offset ->
                val from = accountIds[index]
                val to = accountIds[boundedIndex(index + 7 + offset * 13, maxIndex)]
                val kind = when ((index + offset) % 5) {
                    0 -> AbuseSignalKind.REPORT
                    1 -> AbuseSignalKind.SHARED_DEVICE
                    2 -> AbuseSignalKind.TRANSFER
                    3 -> AbuseSignalKind.SHARED_IP
                    else -> AbuseSignalKind.SHARED_PAYMENT
                }
                edges += AbuseSignalEdge(from, to, kind, 0.25)
            }
        }

        return edges.distinct()
    }

    private fun expectedAbusiveIds(
        knownIds: List<String>,
        edges: List<AbuseSignalEdge>,
    ): Set<String> {
        val knownSet = knownIds.toSet()
        val bySource = edges.groupBy { it.fromAccountId }
        val expected = linkedSetOf<String>()
        var frontier = knownSet

        repeat(DETECTION_DEPTH) {
            val next = linkedSetOf<String>()
            frontier.forEach { accountId ->
                bySource[accountId].orEmpty().forEach { edge ->
                    if (edge.toAccountId !in knownSet) {
                        expected += edge.toAccountId
                    }
                    next += edge.toAccountId
                }
            }
            frontier = next
        }

        return expected
    }

    private fun boundedIndex(index: Int, maxIndex: Int): Int =
        index.coerceIn(0, maxIndex)

    private fun accountId(index: Int): String =
        "acct-%06d".format(index)

    private val SHARED_SIGNAL_KINDS = listOf(
        AbuseSignalKind.SHARED_DEVICE,
        AbuseSignalKind.SHARED_IP,
        AbuseSignalKind.SHARED_PAYMENT,
        AbuseSignalKind.REPORT,
    )
    private const val DETECTION_DEPTH = 2
}
