@file:Suppress("TooGenericExceptionCaught")

package io.bluetape4k.graph.io.report

/** 여러 진행 listener를 등록 순서대로 호출하는 composite listener. */
class GraphIoCompositeProgressListener(
    listeners: Iterable<GraphIoProgressListener>,
) : GraphIoProgressListener {

    private val delegates: List<GraphIoProgressListener> = listeners.toList()

    override fun onEvent(event: GraphIoProgressEvent) {
        dispatch(event, onException = {}, rethrowExceptions = true)
    }

    /**
     * reporter가 redacted warning hook을 제공하는 내부 dispatch 경로다.
     * Exception은 delegate마다 hook을 호출하고 다음 delegate를 계속 실행한다.
     */
    internal fun dispatch(
        event: GraphIoProgressEvent,
        onException: () -> Unit,
        rethrowExceptions: Boolean,
    ) {
        var firstException: Exception? = null
        var firstError: Error? = null

        delegates.forEach { delegate ->
            try {
                delegate.onEvent(event)
            } catch (error: Exception) {
                onException()
                firstException = firstException ?: error
            } catch (error: Error) {
                firstError = firstError ?: error
            }
        }

        firstError?.let { throw it }
        if (rethrowExceptions) {
            firstException?.let { throw it }
        }
    }

    companion object {
        /** listener가 없는 경우에도 사용할 수 있는 no-op composite를 만든다. */
        fun of(vararg listeners: GraphIoProgressListener): GraphIoCompositeProgressListener =
            GraphIoCompositeProgressListener(listeners.asList())
    }
}
