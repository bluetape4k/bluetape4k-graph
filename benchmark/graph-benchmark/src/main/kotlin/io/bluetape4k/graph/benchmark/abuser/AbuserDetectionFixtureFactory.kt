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
        val riskySourceIds = accountIds.take(scenario.riskySourceCount.coerceAtMost(size.accountCount))
        val edges = buildEdges(accountIds, riskySourceIds, scenario)
        val expectedIds = expectedMuleIds(riskySourceIds, edges, scenario)

        val accounts = accountIds.mapIndexed { index, id ->
            AbuserAccount(
                accountId = id,
                segment = "segment-${index % 5}",
                knownAbusive = id in riskySourceIds,
                expectedAbusive = id in expectedIds,
                riskScore = if (id in riskySourceIds) 0.92 else 0.10 + (index % 7) * 0.03,
                accountAgeHours = if (id in riskySourceIds) 2 + index % 6 else 72 + index % 720,
                sharedDeviceCluster = "device-${index % (size.accountCount / 20).coerceAtLeast(3)}",
            )
        }

        return AbuserDetectionFixture(size, scenario, accounts, edges)
    }

    private fun buildEdges(
        accountIds: List<String>,
        riskySourceIds: List<String>,
        scenario: AbuserDetectionScenario,
    ): List<AbuseSignalEdge> {
        val edges = ArrayList<AbuseSignalEdge>(accountIds.size * scenario.noiseMultiplier)
        val maxIndex = accountIds.lastIndex
        val sourceCount = riskySourceIds.size.coerceAtLeast(1)
        val muleStart = sourceCount
        val muleIds = accountIds.drop(muleStart).take(scenario.muleCount)

        riskySourceIds.forEachIndexed { sourceIndex, sourceId ->
            val muleId = muleIds[sourceIndex % muleIds.size]
            var from = sourceId
            repeat(scenario.transferChainLength.coerceAtMost(scenario.hopLimit)) { depth ->
                val to = if (depth == scenario.transferChainLength.coerceAtMost(scenario.hopLimit) - 1) {
                    muleId
                } else {
                    val targetIndex = boundedIndex(
                        muleStart + scenario.muleCount + sourceIndex * scenario.hopLimit + depth,
                        maxIndex,
                    )
                    accountIds[targetIndex]
                }
                edges += AbuseSignalEdge(
                    fromAccountId = from,
                    toAccountId = to,
                    kind = AbuseSignalKind.TRANSFER,
                    weight = 0.90,
                    amount = 950.0 + (sourceIndex % 5) * 10.0,
                    createdAtMinute = 120 - depth * 5,
                )
                from = to
            }
        }

        if (scenario.highDegreeHub) {
            val hub = accountIds[boundedIndex(muleStart + scenario.muleCount + 1, maxIndex)]
            accountIds.drop(muleStart + scenario.muleCount + 2).take(sourceCount * 3).forEachIndexed { index, id ->
                edges += AbuseSignalEdge(
                    fromAccountId = hub,
                    toAccountId = id,
                    kind = AbuseSignalKind.TRANSFER,
                    weight = 0.20,
                    amount = 40.0 + index % 11,
                    createdAtMinute = 30,
                )
            }
        }

        val noiseStart = (scenario.riskySourceCount + scenario.muleCount + 64).coerceAtMost(maxIndex)
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
                edges += AbuseSignalEdge(
                    fromAccountId = from,
                    toAccountId = to,
                    kind = kind,
                    weight = 0.25,
                    amount = 25.0 + (index + offset) % 100,
                    createdAtMinute = if ((index + offset) % 3 == 0) 90 else 15,
                )
            }
        }

        return edges.distinct()
    }

    private fun expectedMuleIds(
        riskySourceIds: List<String>,
        edges: List<AbuseSignalEdge>,
        scenario: AbuserDetectionScenario,
    ): Set<String> {
        val riskySourceSet = riskySourceIds.toSet()
        val bySource = edges.groupBy { it.fromAccountId }
        val upstreamByDestination = linkedMapOf<String, MutableSet<String>>()

        riskySourceSet.forEach { sourceId ->
            var frontier = setOf(sourceId)
            repeat(scenario.hopLimit) {
                val next = linkedSetOf<String>()
                frontier.forEach { accountId ->
                    bySource[accountId].orEmpty()
                        .filter { edge ->
                            edge.kind == AbuseSignalKind.TRANSFER &&
                                edge.createdAtMinute >= scenario.windowStartMinute &&
                                edge.weight >= scenario.riskThreshold
                        }
                        .forEach { edge ->
                            upstreamByDestination.getOrPut(edge.toAccountId) { linkedSetOf() } += sourceId
                            next += edge.toAccountId
                        }
                }
                frontier = next
            }
        }

        return upstreamByDestination.asSequence()
            .filter { (_, upstream) -> upstream.size >= scenario.minDistinctUpstream }
            .map { (destination, _) -> destination }
            .toSet()
    }

    private fun boundedIndex(index: Int, maxIndex: Int): Int =
        index.coerceIn(0, maxIndex)

    private fun accountId(index: Int): String =
        "acct-%06d".format(index)

}
