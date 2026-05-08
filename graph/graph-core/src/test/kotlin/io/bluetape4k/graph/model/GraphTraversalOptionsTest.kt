package io.bluetape4k.graph.model

import io.bluetape4k.assertions.shouldBeEqualTo
import io.bluetape4k.assertions.shouldBeInstanceOf
import io.bluetape4k.assertions.shouldBeNull
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
    fun `NeighborOptions - maxDepth 0도 설정 가능하다`() {
        val opts = NeighborOptions(maxDepth = 0)
        opts.maxDepth shouldBeEqualTo 0
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
    fun `NeighborOptions와 PathOptions는 Serializable이다`() {
        val neighbor: java.io.Serializable = NeighborOptions()
        val path: java.io.Serializable = PathOptions()

        neighbor shouldBeInstanceOf java.io.Serializable::class
        path shouldBeInstanceOf java.io.Serializable::class
    }
}
