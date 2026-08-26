package io.bluetape4k.graph.tinkerpop

import org.apache.tinkerpop.gremlin.process.traversal.Traversal
import java.util.NoSuchElementException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * TinkerGraph의 lazy chunk cursor를 감싸는 close-aware [Sequence]다.
 *
 * Kotlin [Sequence.take]는 producer의 `finally`를 재개하지 않으므로 조기 소비를
 * 멈추는 호출자는 이 sequence를 `close()`해야 한다. 전체 소비가 필요하면
 * 일반 `Sequence`처럼 사용할 수 있고, 부분 소비는 `try/finally` 또는
 * `use`로 수명 경계를 명시한다.
 */
public class CloseableChunkSequence<T> private constructor(
    private val iteratorFactory: () -> CloseableChunkIterator<T>,
) : Sequence<T>, AutoCloseable {

    internal companion object {
        @JvmSynthetic
        fun <T> create(
            iteratorFactory: () -> CloseableChunkIterator<T>,
        ): CloseableChunkSequence<T> = CloseableChunkSequence(iteratorFactory)
    }

    private val lock = ReentrantLock()
    private val activeIterators = LinkedHashSet<TrackedCloseableChunkIterator>()
    private var closed = false

    override fun iterator(): Iterator<T> {
        val iterator = lock.withLock {
            check(!closed) { "CloseableChunkSequence is already closed" }
            TrackedCloseableChunkIterator(iteratorFactory())
                .also { activeIterators += it }
        }
        return iterator
    }

    @Suppress("TooGenericExceptionCaught")
    override fun close() {
        val iterators: List<TrackedCloseableChunkIterator> = lock.withLock {
            if (closed) {
                emptyList()
            } else {
                closed = true
                activeIterators.toList().also { activeIterators.clear() }
            }
        }

        var failure: Throwable? = null
        iterators.forEach { iterator ->
            try {
                iterator.close()
            } catch (error: Throwable) {
                val previous = failure
                if (previous == null) failure = error else previous.addSuppressed(error)
            }
        }
        failure?.let { throw it }
    }

    private inner class TrackedCloseableChunkIterator(
        private val delegate: CloseableChunkIterator<T>,
    ) : CloseableChunkIterator<T> {
        private var closed = false

        @Suppress("TooGenericExceptionCaught")
        override fun hasNext(): Boolean {
            if (closed) return false
            return try {
                val result = delegate.hasNext()
                if (!result) close()
                result
            } catch (error: Throwable) {
                closeAfterFailure(error)
                throw error
            }
        }

        @Suppress("TooGenericExceptionCaught")
        override fun next(): T {
            if (closed) throw NoSuchElementException("CloseableChunkSequence is closed")
            return try {
                delegate.next()
            } catch (error: Throwable) {
                closeAfterFailure(error)
                throw error
            }
        }

        @Suppress("TooGenericExceptionCaught")
        override fun close() {
            if (closed) return
            closed = true
            try {
                delegate.close()
            } finally {
                lock.withLock { activeIterators.remove(this) }
            }
        }

        @Suppress("TooGenericExceptionCaught")
        private fun closeAfterFailure(error: Throwable) {
            try {
                close()
            } catch (closeFailure: Throwable) {
                error.addSuppressed(closeFailure)
            }
        }
    }
}

@JvmSynthetic
internal fun <T> closeableChunkSequence(
    iteratorFactory: () -> CloseableChunkIterator<T>,
): CloseableChunkSequence<T> = CloseableChunkSequence.create(iteratorFactory)

/** [CloseableChunkSequence]가 소유하는 close 가능한 iterator다. */
internal interface CloseableChunkIterator<T> : Iterator<T>, AutoCloseable

/**
 * TinkerPop traversal 기반 bounded chunk cursor다. chunk마다 최대 [chunkSize]개의
 * record만 요청하고, 소진·실패·명시적 close 시 traversal을 닫는다.
 */
internal class TraversalChunkIterator<E, R>(
    private val traversal: Traversal<*, E>,
    private val chunkSize: Int,
    private val mapper: (E) -> R,
) : CloseableChunkIterator<List<R>> {

    private var nextChunk: List<R>? = null
    private var exhausted = false
    private var closed = false

    @Suppress("TooGenericExceptionCaught", "ReturnCount")
    override fun hasNext(): Boolean {
        nextChunk?.let { return true }
        if (exhausted || closed) return false

        return try {
            val chunk = ArrayList<R>(chunkSize)
            while (chunk.size < chunkSize && traversal.hasNext()) {
                chunk += mapper(traversal.next())
            }

            if (chunk.isEmpty()) {
                exhausted = true
                close()
                false
            } else {
                exhausted = !traversal.hasNext()
                nextChunk = chunk.toList()
                if (exhausted) close()
                true
            }
        } catch (error: Throwable) {
            closeAfterFailure(error)
            throw error
        }
    }

    override fun next(): List<R> {
        if (nextChunk == null && !hasNext()) {
            throw NoSuchElementException()
        }
        return nextChunk?.also { nextChunk = null } ?: throw NoSuchElementException()
    }

    @Suppress("TooGenericExceptionCaught")
    override fun close() {
        if (closed) return
        closed = true
        traversal.close()
    }

    @Suppress("TooGenericExceptionCaught")
    private fun closeAfterFailure(error: Throwable) {
        try {
            close()
        } catch (closeFailure: Throwable) {
            error.addSuppressed(closeFailure)
        }
    }
}
