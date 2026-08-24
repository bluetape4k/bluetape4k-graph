package io.bluetape4k.graph.model

import io.bluetape4k.assertions.assertFailsWith
import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldBeNull
import io.bluetape4k.assertions.shouldContain
import org.junit.jupiter.api.Test

class GraphTraversalOptionsTest {

    @Test
    fun `NeighborOptions 기본값은 OUTGOING-depth 1-edgeLabel null이다`() {
        val opts = NeighborOptions()

        opts.edgeLabel.shouldBeNull()
        opts.direction shouldBeEqualTo Direction.OUTGOING
        opts.maxDepth shouldBeEqualTo 1
    }

    @Test
    fun `NeighborOptions Default 상수는 기본 생성자와 동일하다`() {
        NeighborOptions.Default shouldBeEqualTo NeighborOptions()
    }

    @Test
    fun `NeighborOptions copy로 일부 필드만 변경한다`() {
        val base = NeighborOptions()
        val updated = base.copy(edgeLabel = "KNOWS", direction = Direction.BOTH, maxDepth = 3)

        updated.edgeLabel shouldBeEqualTo "KNOWS"
        updated.direction shouldBeEqualTo Direction.BOTH
        updated.maxDepth shouldBeEqualTo 3
    }

    @Test
    fun `PathOptions 기본값은 edgeLabel null-maxDepth 10이다`() {
        val opts = PathOptions()

        opts.edgeLabel.shouldBeNull()
        opts.maxDepth shouldBeEqualTo 10
    }

    @Test
    fun `PathOptions Default 상수는 기본 생성자와 동일하다`() {
        PathOptions.Default shouldBeEqualTo PathOptions()
    }

    @Test
    fun `NeighborOptions와 PathOptions는 모두 GraphTraversalOptions의 하위 타입이다`() {
        val n: GraphTraversalOptions = NeighborOptions()
        val p: GraphTraversalOptions = PathOptions()

        n shouldBeInstanceOf NeighborOptions::class
        p shouldBeInstanceOf PathOptions::class
    }

    @Test
    fun `Direction 열거형은 세 방향을 가진다`() {
        Direction.entries shouldBeEqualTo listOf(
            Direction.OUTGOING,
            Direction.INCOMING,
            Direction.BOTH,
        )
    }

    @Test
    fun `NeighborOptions는 maxDepth 0을 허용하고 음수는 거부한다`() {
        NeighborOptions(maxDepth = 0).maxDepth shouldBeEqualTo 0

        val ex = assertFailsWith<IllegalArgumentException> {
            NeighborOptions(maxDepth = -1)
        }

        ex.message shouldContain "maxDepth"
    }

    @Test
    fun `NeighborOptions - 모든 필드를 명시해서 생성할 수 있다`() {
        val opts = NeighborOptions(
            edgeLabel = "WORKS_AT",
            direction = Direction.INCOMING,
            maxDepth = 5,
        )
        opts.edgeLabel shouldBeEqualTo "WORKS_AT"
        opts.direction shouldBeEqualTo Direction.INCOMING
        opts.maxDepth shouldBeEqualTo 5
    }

    @Test
    fun `PathOptions - edgeLabel 지정 시 필터링된다`() {
        val opts = PathOptions(edgeLabel = "KNOWS", maxDepth = 3)
        opts.edgeLabel shouldBeEqualTo "KNOWS"
        opts.maxDepth shouldBeEqualTo 3
    }

    @Test
    fun `PathOptions copy로 일부 필드만 변경한다`() {
        val base = PathOptions()
        val updated = base.copy(maxDepth = 20)
        updated.maxDepth shouldBeEqualTo 20
        updated.edgeLabel.shouldBeNull()
    }

    @Test
    fun `PathOptions는 0 이하 maxVisited를 거부한다`() {
        listOf(0, -1).forEach { maxVisited ->
            val ex = assertFailsWith<IllegalArgumentException> {
                PathOptions(maxVisited = maxVisited)
            }

            ex.message shouldContain "maxVisited"
        }
    }

    @Test
    fun `PathOptions는 maxDepth 0을 허용하고 음수는 거부한다`() {
        PathOptions(maxDepth = 0).maxDepth shouldBeEqualTo 0

        val ex = assertFailsWith<IllegalArgumentException> {
            PathOptions(maxDepth = -1)
        }

        ex.message shouldContain "maxDepth"
    }

    @Test
    fun `NeighborOptions와 PathOptions는 Serializable이다`() {
        val neighbor: java.io.Serializable = NeighborOptions()
        val path: java.io.Serializable = PathOptions()

        neighbor shouldBeInstanceOf java.io.Serializable::class
        path shouldBeInstanceOf java.io.Serializable::class
    }

    @Test
    fun `BfsDfsOptions 기본값은 OUTGOING-depth 5-maxVertices 10000이다`() {
        val opts = BfsDfsOptions()

        opts.edgeLabel.shouldBeNull()
        opts.direction shouldBeEqualTo Direction.OUTGOING
        opts.maxDepth shouldBeEqualTo 5
        opts.maxVertices shouldBeEqualTo 10_000
    }

    @Test
    fun `BfsDfsOptions Default 상수는 기본 생성자와 동일하다`() {
        BfsDfsOptions.Default shouldBeEqualTo BfsDfsOptions()
    }

    @Test
    fun `BfsDfsOptions는 모든 필드를 명시해서 생성할 수 있다`() {
        val opts = BfsDfsOptions(
            edgeLabel = "TRANSFERRED_TO",
            direction = Direction.BOTH,
            maxDepth = 7,
            maxVertices = 250,
        )

        opts.edgeLabel shouldBeEqualTo "TRANSFERRED_TO"
        opts.direction shouldBeEqualTo Direction.BOTH
        opts.maxDepth shouldBeEqualTo 7
        opts.maxVertices shouldBeEqualTo 250
    }

    @Test
    fun `BfsDfsOptions는 음수 maxDepth를 거부한다`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            BfsDfsOptions(maxDepth = -1)
        }

        ex.message shouldContain "maxDepth"
    }

    @Test
    fun `BfsDfsOptions는 maxDepth 0에서 시작 정점만 허용한다`() {
        val opts = BfsDfsOptions(maxDepth = 0)

        opts.maxDepth shouldBeEqualTo 0
    }

    @Test
    fun `BfsDfsOptions는 0 이하 maxVertices를 거부한다`() {
        listOf(0, -1).forEach { maxVertices ->
            val ex = assertFailsWith<IllegalArgumentException> {
                BfsDfsOptions(maxVertices = maxVertices)
            }

            ex.message shouldContain "maxVertices"
        }
    }

    @Test
    fun `CycleOptions 기본값은 depth 10-maxCycles 100이다`() {
        val opts = CycleOptions()

        opts.vertexLabel.shouldBeNull()
        opts.edgeLabel.shouldBeNull()
        opts.maxDepth shouldBeEqualTo 10
        opts.maxCycles shouldBeEqualTo 100
    }

    @Test
    fun `CycleOptions Default 상수는 기본 생성자와 동일하다`() {
        CycleOptions.Default shouldBeEqualTo CycleOptions()
    }

    @Test
    fun `CycleOptions는 모든 필드를 명시해서 생성할 수 있다`() {
        val opts = CycleOptions(
            vertexLabel = "Account",
            edgeLabel = "TRANSFERRED_TO",
            maxDepth = 4,
            maxCycles = 12,
        )

        opts.vertexLabel shouldBeEqualTo "Account"
        opts.edgeLabel shouldBeEqualTo "TRANSFERRED_TO"
        opts.maxDepth shouldBeEqualTo 4
        opts.maxCycles shouldBeEqualTo 12
    }

    @Test
    fun `CycleOptions는 0 이하 maxDepth를 거부한다`() {
        listOf(0, -1).forEach { maxDepth ->
            val ex = assertFailsWith<IllegalArgumentException> {
                CycleOptions(maxDepth = maxDepth)
            }

            ex.message shouldContain "maxDepth"
        }
    }

    @Test
    fun `CycleOptions는 0 이하 maxCycles를 거부한다`() {
        listOf(0, -1).forEach { maxCycles ->
            val ex = assertFailsWith<IllegalArgumentException> {
                CycleOptions(maxCycles = maxCycles)
            }

            ex.message shouldContain "maxCycles"
        }
    }

    @Test
    fun `BfsDfsOptions와 CycleOptions는 GraphTraversalOptions의 하위 타입이며 Serializable이다`() {
        val bfsDfs: GraphTraversalOptions = BfsDfsOptions()
        val cycle: GraphTraversalOptions = CycleOptions()

        bfsDfs shouldBeInstanceOf BfsDfsOptions::class
        cycle shouldBeInstanceOf CycleOptions::class
        bfsDfs shouldBeInstanceOf java.io.Serializable::class
        cycle shouldBeInstanceOf java.io.Serializable::class
    }
}
