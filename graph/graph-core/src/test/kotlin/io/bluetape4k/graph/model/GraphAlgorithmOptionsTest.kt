package io.bluetape4k.graph.model

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldContain
import org.junit.jupiter.api.Test

class GraphAlgorithmOptionsTest {

    @Test
    fun `PageRankOptions 기본값은 전체 정점과 간선을 대상으로 한다`() {
        val opts = PageRankOptions()

        opts.vertexLabel.shouldBeNull()
        opts.edgeLabel.shouldBeNull()
        opts.iterations shouldBeEqualTo 20
        opts.dampingFactor shouldBeEqualTo 0.85
        opts.tolerance shouldBeEqualTo 1e-4
        opts.topK shouldBeEqualTo Int.MAX_VALUE
    }

    @Test
    fun `PageRankOptions Default 상수는 기본 생성자와 동일하다`() {
        PageRankOptions.Default shouldBeEqualTo PageRankOptions()
    }

    @Test
    fun `PageRankOptions는 모든 필드를 명시해서 생성할 수 있다`() {
        val opts = PageRankOptions(
            vertexLabel = "Account",
            edgeLabel = "TRANSFERRED_TO",
            iterations = 50,
            dampingFactor = 0.9,
            tolerance = 1e-6,
            topK = 25,
        )

        opts.vertexLabel shouldBeEqualTo "Account"
        opts.edgeLabel shouldBeEqualTo "TRANSFERRED_TO"
        opts.iterations shouldBeEqualTo 50
        opts.dampingFactor shouldBeEqualTo 0.9
        opts.tolerance shouldBeEqualTo 1e-6
        opts.topK shouldBeEqualTo 25
    }

    @Test
    fun `PageRankOptions copy로 일부 필드만 변경한다`() {
        val base = PageRankOptions(vertexLabel = "Person", topK = 10)
        val updated = base.copy(edgeLabel = "KNOWS", iterations = 30)

        updated.vertexLabel shouldBeEqualTo "Person"
        updated.edgeLabel shouldBeEqualTo "KNOWS"
        updated.iterations shouldBeEqualTo 30
        updated.topK shouldBeEqualTo 10
    }

    @Test
    fun `PageRankOptions는 0 이하 iterations를 거부한다`() {
        listOf(0, -1).forEach { iterations ->
            val ex = assertFailsWith<IllegalArgumentException> {
                PageRankOptions(iterations = iterations)
            }

            ex.message shouldContain "iterations"
        }
    }

    @Test
    fun `PageRankOptions는 0 이하 topK를 거부한다`() {
        listOf(0, -1).forEach { topK ->
            val ex = assertFailsWith<IllegalArgumentException> {
                PageRankOptions(topK = topK)
            }

            ex.message shouldContain "topK"
        }
    }

    @Test
    fun `PageRankOptions는 범위를 벗어나거나 비유한 dampingFactor를 거부한다`() {
        listOf(-0.1, 1.1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY).forEach { dampingFactor ->
            val ex = assertFailsWith<IllegalArgumentException> {
                PageRankOptions(dampingFactor = dampingFactor)
            }

            ex.message shouldContain "dampingFactor"
        }
    }

    @Test
    fun `PageRankOptions는 dampingFactor의 양 끝 경계를 허용한다`() {
        listOf(0.0, 1.0).forEach { dampingFactor ->
            PageRankOptions(dampingFactor = dampingFactor).dampingFactor shouldBeEqualTo dampingFactor
        }
    }

    @Test
    fun `PageRankOptions는 0 이하 또는 비유한 tolerance를 거부한다`() {
        listOf(0.0, -1e-4, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY).forEach { tolerance ->
            val ex = assertFailsWith<IllegalArgumentException> {
                PageRankOptions(tolerance = tolerance)
            }

            ex.message shouldContain "tolerance"
        }
    }

    @Test
    fun `PageRankOptions는 음수 또는 비유한 tolerance를 거부한다`() {
        val negative = assertFailsWith<IllegalArgumentException> {
            PageRankOptions(tolerance = -1e-4)
        }
        negative.message shouldContain "tolerance"

        val nonFinite = assertFailsWith<IllegalArgumentException> {
            PageRankOptions(tolerance = Double.NaN)
        }
        nonFinite.message shouldContain "tolerance"
    }

    @Test
    fun `DegreeOptions 기본값은 모든 간선과 양방향이다`() {
        val opts = DegreeOptions()

        opts.edgeLabel.shouldBeNull()
        opts.direction shouldBeEqualTo Direction.BOTH
    }

    @Test
    fun `DegreeOptions Default 상수는 기본 생성자와 동일하다`() {
        DegreeOptions.Default shouldBeEqualTo DegreeOptions()
    }

    @Test
    fun `DegreeOptions는 모든 필드를 명시해서 생성할 수 있다`() {
        val opts = DegreeOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING)

        opts.edgeLabel shouldBeEqualTo "KNOWS"
        opts.direction shouldBeEqualTo Direction.OUTGOING
    }

    @Test
    fun `ComponentOptions 기본값은 weak component 전체 반환이다`() {
        val opts = ComponentOptions()

        opts.vertexLabel.shouldBeNull()
        opts.edgeLabel.shouldBeNull()
        opts.weakly shouldBeEqualTo true
        opts.minSize shouldBeEqualTo 1
    }

    @Test
    fun `ComponentOptions Default 상수는 기본 생성자와 동일하다`() {
        ComponentOptions.Default shouldBeEqualTo ComponentOptions()
    }

    @Test
    fun `ComponentOptions는 모든 필드를 명시해서 생성할 수 있다`() {
        val opts = ComponentOptions(
            vertexLabel = "Account",
            edgeLabel = "TRANSFERRED_TO",
            weakly = false,
            minSize = 3,
        )

        opts.vertexLabel shouldBeEqualTo "Account"
        opts.edgeLabel shouldBeEqualTo "TRANSFERRED_TO"
        opts.weakly shouldBeEqualTo false
        opts.minSize shouldBeEqualTo 3
    }

    @Test
    fun `ComponentOptions는 0 이하 minSize를 거부한다`() {
        listOf(0, -1).forEach { minSize ->
            val ex = assertFailsWith<IllegalArgumentException> {
                ComponentOptions(minSize = minSize)
            }

            ex.message shouldContain "minSize"
        }
    }

    @Test
    fun `algorithm option들은 sealed base type이며 Serializable이다`() {
        val pageRank: GraphAlgorithmOptions = PageRankOptions()
        val degree: GraphAlgorithmOptions = DegreeOptions()
        val component: GraphAlgorithmOptions = ComponentOptions()

        pageRank shouldBeInstanceOf PageRankOptions::class
        degree shouldBeInstanceOf DegreeOptions::class
        component shouldBeInstanceOf ComponentOptions::class
        pageRank shouldBeInstanceOf java.io.Serializable::class
        degree shouldBeInstanceOf java.io.Serializable::class
        component shouldBeInstanceOf java.io.Serializable::class
    }
}
