package io.bluetape4k.graph.benchmark.abuser

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeGreaterThan
import io.bluetape4k.assertions.shouldNotBeEmpty
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AbuserDetectionContractTest {

    companion object: KLogging()

    @Test
    fun `fixture is deterministic for the same size`() {
        val first = AbuserDetectionFixtureFactory.create(AbuserDetectionSize.SMOKE, AbuserDetectionScenario.SHARED)
        val second = AbuserDetectionFixtureFactory.create(AbuserDetectionSize.SMOKE, AbuserDetectionScenario.SHARED)

        log.debug { "first=$first" }
        first shouldBeEqualTo second
        first.accounts.size shouldBeEqualTo AbuserDetectionSize.SMOKE.accountCount
        first.edges.shouldNotBeEmpty()
        first.expectedAbusiveAccountIds.shouldNotBeEmpty()
        first.knownAbusiveAccountIds.size shouldBeEqualTo AbuserDetectionScenario.SHARED.riskySourceCount
    }

    @Test
    fun `dense scenario creates more inspection edges than shared scenario`() {
        val shared = AbuserDetectionFixtureFactory.create(AbuserDetectionSize.SMOKE, AbuserDetectionScenario.SHARED)
        val dense = AbuserDetectionFixtureFactory.create(AbuserDetectionSize.SMOKE, AbuserDetectionScenario.NOISY_DENSE)

        log.debug { "shared=$shared" }
        log.debug { "dense=$dense" }

        dense.edges.size shouldBeGreaterThan shared.edges.size
        dense.expectedAbusiveAccountIds.size shouldBeGreaterThan shared.expectedAbusiveAccountIds.size
    }

    @Test
    fun `metrics calculate precision recall and f1`() {
        val metrics = AbuserDetectionMetrics.from(
            expected = setOf("a", "b", "c"),
            predicted = setOf("a", "b", "x"),
        )

        log.debug { "metrics=$metrics" }
        metrics.truePositives shouldBeEqualTo 2
        metrics.falsePositives shouldBeEqualTo 1
        metrics.falseNegatives shouldBeEqualTo 1
        metrics.precision shouldBeEqualTo 2.0 / 3.0
        metrics.recall shouldBeEqualTo 2.0 / 3.0
        metrics.f1 shouldBeEqualTo 2.0 / 3.0
    }
}
