package io.bluetape4k.graph.benchmark.abuser

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldNotBeEmpty
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AbuserDetectionContractTest {

    @Test
    fun `fixture is deterministic for the same size`() {
        val first = AbuserDetectionFixtureFactory.create(AbuserDetectionSize.SMOKE, AbuserDetectionScenario.SHARED)
        val second = AbuserDetectionFixtureFactory.create(AbuserDetectionSize.SMOKE, AbuserDetectionScenario.SHARED)

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

        (dense.edges.size > shared.edges.size) shouldBeEqualTo true
        (dense.expectedAbusiveAccountIds.size > shared.expectedAbusiveAccountIds.size) shouldBeEqualTo true
    }

    @Test
    fun `metrics calculate precision recall and f1`() {
        val metrics = AbuserDetectionMetrics.from(
            expected = setOf("a", "b", "c"),
            predicted = setOf("a", "b", "x"),
        )

        metrics.truePositives shouldBeEqualTo 2
        metrics.falsePositives shouldBeEqualTo 1
        metrics.falseNegatives shouldBeEqualTo 1
        metrics.precision shouldBeEqualTo 2.0 / 3.0
        metrics.recall shouldBeEqualTo 2.0 / 3.0
        metrics.f1 shouldBeEqualTo 2.0 / 3.0
    }
}
