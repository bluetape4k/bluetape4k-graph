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

    @Test
    fun `GraphException - cause가 없으면 null이다`() {
        val ex = GraphException("no cause")
        ex.cause shouldBe null
    }

    @Test
    fun `GraphNotFoundException - message가 보존된다`() {
        val ex = GraphNotFoundException("graph 'myGraph' not found")
        ex.message shouldBeEqualTo "graph 'myGraph' not found"
    }

    @Test
    fun `GraphAlreadyExistsException - message와 cause를 모두 가진다`() {
        val cause = RuntimeException("constraint violation")
        val ex = GraphAlreadyExistsException("graph 'x' already exists", cause)
        ex.message shouldBeEqualTo "graph 'x' already exists"
        ex.cause shouldBe cause
    }

    @Test
    fun `GraphQueryException - message가 보존된다`() {
        val ex = GraphQueryException("invalid cypher syntax")
        ex.message shouldBeEqualTo "invalid cypher syntax"
        ex.cause shouldBe null
    }

    @Test
    fun `GraphNotInitializedException - cause와 함께 생성할 수 있다`() {
        val cause = IllegalStateException("driver not ready")
        val ex = GraphNotInitializedException("not initialized", cause)
        ex.cause shouldBe cause
    }

    @Test
    fun `모든 예외는 RuntimeException이므로 checked exception이 아니다`() {
        listOf(
            GraphException("e"),
            GraphNotFoundException("e"),
            GraphAlreadyExistsException("e"),
            GraphQueryException("e"),
            GraphNotInitializedException("e"),
        ).forEach { ex ->
            ex shouldBeInstanceOf RuntimeException::class
        }
    }
}
