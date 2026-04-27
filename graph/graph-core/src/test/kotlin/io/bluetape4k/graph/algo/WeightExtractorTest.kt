package io.bluetape4k.graph.algo

import io.bluetape4k.graph.model.GraphEdge
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.MissingWeightException
import io.bluetape4k.graph.model.MissingWeightPolicy
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeNear
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeNull
import org.amshove.kluent.shouldThrow
import org.junit.jupiter.api.Test

class WeightExtractorTest {

    companion object : KLogging()

    private fun edge(vararg props: Pair<String, Any?>) =
        GraphEdge(GraphElementId.of("e1"), "ROAD", GraphElementId.of("v1"), GraphElementId.of("v2"), mapOf(*props))

    // ─── Fail 정책 ───────────────────────────────────────────────────────────

    @Test
    fun `Fail 정책 - 속성 없으면 MissingWeightException 발생`() {
        val extractor = WeightExtractor("cost", MissingWeightPolicy.Fail)
        val block = { extractor.extract(edge()) }
        block shouldThrow MissingWeightException::class
    }

    @Test
    fun `Fail 정책 - 속성 있으면 double 반환`() {
        val extractor = WeightExtractor("cost", MissingWeightPolicy.Fail)
        extractor.extract(edge("cost" to 5.0)).shouldNotBeNull().shouldBeNear(5.0, 0.001)
    }

    // ─── Skip 정책 ───────────────────────────────────────────────────────────

    @Test
    fun `Skip 정책 - 속성 없으면 null 반환`() {
        val extractor = WeightExtractor("cost", MissingWeightPolicy.Skip)
        extractor.extract(edge()).shouldBeNull()
    }

    @Test
    fun `Skip 정책 - 속성 있으면 double 반환`() {
        val extractor = WeightExtractor("cost", MissingWeightPolicy.Skip)
        extractor.extract(edge("cost" to 3.5f)).shouldNotBeNull().shouldBeNear(3.5, 0.001)
    }

    // ─── UseDefault 정책 ─────────────────────────────────────────────────────

    @Test
    fun `UseDefault 정책 - 속성 없으면 기본값 반환`() {
        val extractor = WeightExtractor("cost", MissingWeightPolicy.UseDefault(1.0))
        extractor.extract(edge()).shouldNotBeNull().shouldBeNear(1.0, 0.001)
    }

    @Test
    fun `UseDefault 정책 - 속성 있으면 실제 값 반환`() {
        val extractor = WeightExtractor("cost", MissingWeightPolicy.UseDefault(1.0))
        extractor.extract(edge("cost" to 7)).shouldNotBeNull().shouldBeNear(7.0, 0.001)
    }

    // ─── 타입 변환 ─────────────────────────────────────────────────────────────

    @Test
    fun `Int 속성을 double로 변환`() {
        val extractor = WeightExtractor("w", MissingWeightPolicy.Fail)
        extractor.extract(edge("w" to 10)).shouldNotBeNull().shouldBeNear(10.0, 0.001)
    }

    @Test
    fun `Long 속성을 double로 변환`() {
        val extractor = WeightExtractor("w", MissingWeightPolicy.Fail)
        extractor.extract(edge("w" to 42L)).shouldNotBeNull().shouldBeNear(42.0, 0.001)
    }

    @Test
    fun `Float 속성을 double로 변환`() {
        val extractor = WeightExtractor("w", MissingWeightPolicy.Fail)
        extractor.extract(edge("w" to 2.5f)).shouldNotBeNull().shouldBeNear(2.5, 0.001)
    }

    @Test
    fun `String 숫자 속성을 double로 변환`() {
        val extractor = WeightExtractor("w", MissingWeightPolicy.Fail)
        extractor.extract(edge("w" to "3.14")).shouldNotBeNull().shouldBeNear(3.14, 0.001)
    }

    @Test
    fun `BigDecimal 속성을 double로 변환`() {
        val extractor = WeightExtractor("w", MissingWeightPolicy.Fail)
        extractor.extract(edge("w" to java.math.BigDecimal("9.99"))).shouldNotBeNull().shouldBeNear(9.99, 0.001)
    }

    // ─── 유효성 검사 ──────────────────────────────────────────────────────────

    @Test
    fun `NaN weight은 IllegalArgumentException 발생`() {
        val extractor = WeightExtractor("w", MissingWeightPolicy.Fail)
        val block = { extractor.extract(edge("w" to Double.NaN)) }
        block shouldThrow IllegalArgumentException::class
    }

    @Test
    fun `양의 무한대 weight은 IllegalArgumentException 발생`() {
        val extractor = WeightExtractor("w", MissingWeightPolicy.Fail)
        val block = { extractor.extract(edge("w" to Double.POSITIVE_INFINITY)) }
        block shouldThrow IllegalArgumentException::class
    }

    @Test
    fun `음수 weight은 IllegalArgumentException 발생`() {
        val extractor = WeightExtractor("w", MissingWeightPolicy.Fail)
        val block = { extractor.extract(edge("w" to -1.0)) }
        block shouldThrow IllegalArgumentException::class
    }

    @Test
    fun `0 weight은 IllegalArgumentException 발생`() {
        val extractor = WeightExtractor("w", MissingWeightPolicy.Fail)
        val block = { extractor.extract(edge("w" to 0.0)) }
        block shouldThrow IllegalArgumentException::class
    }

    @Test
    fun `String 비숫자 속성은 IllegalArgumentException 발생`() {
        val extractor = WeightExtractor("w", MissingWeightPolicy.Fail)
        val block = { extractor.extract(edge("w" to "abc")) }
        block shouldThrow IllegalArgumentException::class
    }

    // ─── MissingWeightPolicy 생성 ─────────────────────────────────────────────

    @Test
    fun `UseDefault value 0은 IllegalArgumentException`() {
        val block = { MissingWeightPolicy.UseDefault(0.0) }
        block shouldThrow IllegalArgumentException::class
    }

    @Test
    fun `UseDefault value 음수는 IllegalArgumentException`() {
        val block = { MissingWeightPolicy.UseDefault(-1.0) }
        block shouldThrow IllegalArgumentException::class
    }

    @Test
    fun `UseDefault Infinity는 IllegalArgumentException`() {
        val block = { MissingWeightPolicy.UseDefault(Double.POSITIVE_INFINITY) }
        block shouldThrow IllegalArgumentException::class
    }
}
