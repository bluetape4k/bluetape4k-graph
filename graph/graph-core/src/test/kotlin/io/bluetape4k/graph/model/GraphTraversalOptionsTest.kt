package io.bluetape4k.graph.model

import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.amshove.kluent.shouldBeNull
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
}
