package io.bluetape4k.graph

import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeInstanceOf
import org.junit.jupiter.api.Test

class GraphExceptionsTest {

    @Test
    fun `GraphException은 RuntimeException 하위 타입이다`() {
        val ex: Throwable = GraphException("boom")
        ex shouldBeInstanceOf RuntimeException::class
        ex.message shouldBeEqualTo "boom"
    }

    @Test
    fun `GraphException은 cause를 보존한다`() {
        val cause = IllegalStateException("root")
        val ex = GraphException("wrapped", cause)
        ex.cause shouldBe cause
    }

    @Test
    fun `GraphNotFoundException은 GraphException이다`() {
        val ex: Throwable = GraphNotFoundException("missing")
        ex shouldBeInstanceOf GraphException::class
        ex.message shouldBeEqualTo "missing"
    }

    @Test
    fun `GraphAlreadyExistsException은 GraphException이다`() {
        val ex: Throwable = GraphAlreadyExistsException("dup")
        ex shouldBeInstanceOf GraphException::class
    }

    @Test
    fun `GraphQueryException은 cause와 함께 생성할 수 있다`() {
        val cause = RuntimeException("sql error")
        val ex = GraphQueryException("query failed", cause)

        ex shouldBeInstanceOf GraphException::class
        ex.cause shouldBe cause
    }

    @Test
    fun `GraphNotInitializedException은 GraphException이다`() {
        val ex: Throwable = GraphNotInitializedException("graph missing")
        ex shouldBeInstanceOf GraphException::class
    }
}
