package io.bluetape4k.graph.io.csv

import io.bluetape4k.graph.io.source.GraphImportSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.coroutines.CoroutineContext

/**
 * 신뢰할 수 있는 애플리케이션 설정의 classpath CSV 입력 쌍을 [block] 수명 동안 제공한다.
 *
 * 호출 시점의 context classloader에서 먼저 찾고, 리소스가 없을 때만 [fallbackClassLoader]를 사용한다.
 * 경로를 정규화하지 않으며 classloader 예외는 그대로 전파한다. 입력은 간선, 정점 순서로 닫는다.
 * 반환된 소스나 이를 사용하는 지연 연산은 callback 밖으로 유출하지 않고 [block] 안에서 소비해야 한다.
 */
fun <T> withClasspathCsvGraphImportSource(
    verticesResource: String,
    edgesResource: String,
    fallbackClassLoader: ClassLoader?,
    missingResourceMessage: (String) -> String = { "CSV classpath resource not found: $it" },
    block: (CsvGraphImportSource) -> T,
): T {
    val contextLoader = Thread.currentThread().contextClassLoader
    return openResource(verticesResource, contextLoader, fallbackClassLoader, missingResourceMessage).use { vertices ->
        openResource(edgesResource, contextLoader, fallbackClassLoader, missingResourceMessage).use { edges ->
            block(csvSource(vertices, edges))
        }
    }
}

/**
 * CSV 입력의 열기와 닫기는 IO dispatcher에서 수행하고 [block]은 호출자의 coroutine context에서 실행한다.
 *
 * 입력의 소유권은 이 함수에 있으며 취소나 예외에도 닫는다. 닫기 실패는 원래 예외의 suppressed에 보존한다.
 * 소스, Flow, Deferred 등 입력에 의존하는 작업을 callback 밖으로 유출하면 안 된다.
 * classloader 선택과 경로 정책은 [withClasspathCsvGraphImportSource]와 같다.
 */
suspend fun <T> withClasspathCsvGraphImportSourceSuspending(
    verticesResource: String,
    edgesResource: String,
    fallbackClassLoader: ClassLoader?,
    missingResourceMessage: (String) -> String = { "CSV classpath resource not found: $it" },
    block: suspend (CsvGraphImportSource) -> T,
): T {
    val contextLoader = Thread.currentThread().contextClassLoader
    val callerContext = currentCoroutineContext()
    var completedFailure: Throwable? = null
    return try {
        withContext(Dispatchers.IO) {
            captureFailure(callerContext, onFailure = { completedFailure = it }) {
                openResource(
                    verticesResource, contextLoader, fallbackClassLoader, missingResourceMessage,
                ).use { vertices ->
                    openResource(
                        edgesResource, contextLoader, fallbackClassLoader, missingResourceMessage,
                    ).use { edges ->
                        val callbackResult = try {
                            withContext(callerContext) {
                                captureFailure(callerContext) { block(csvSource(vertices, edges)) }
                            }
                        } catch (cancelled: CancellationException) {
                            // Job 취소의 원래 원인에 use의 close 실패를 보존한다.
                            callerContext.ensureActive()
                            throw cancelled
                        }
                        callbackResult.getOrThrow()
                    }
                }
            }
        }.getOrThrow()
    } catch (cancelled: CancellationException) {
        val primary = try {
            callerContext.ensureActive()
            cancelled
        } catch (callerCancellation: CancellationException) {
            callerCancellation
        }
        // IO block의 종료를 기다린 뒤 복귀하는 취소도 이미 발생한 close 실패를 버리지 않는다.
        completedFailure?.takeIf { it !== primary }?.let(primary::addSuppressed)
        throw primary
    }
}

private fun openResource(
    path: String,
    contextLoader: ClassLoader?,
    fallbackLoader: ClassLoader?,
    missingResourceMessage: (String) -> String,
): InputStream = contextLoader?.getResourceAsStream(path)
    ?: fallbackLoader?.getResourceAsStream(path)
    ?: throw IllegalArgumentException(missingResourceMessage(path))

private fun csvSource(vertices: InputStream, edges: InputStream): CsvGraphImportSource = CsvGraphImportSource(
    GraphImportSource.InputStreamSource(vertices, closeInput = false),
    GraphImportSource.InputStreamSource(edges, closeInput = false),
)

/** 활성 호출자의 예외는 dispatcher 경계를 값으로 통과시켜 예외 복사와 suppressed 손실을 막는다. */
@Suppress("TooGenericExceptionCaught") // Throwable을 처리하지 않고 원래 인스턴스로 다시 던지기 위한 전달 경계다.
private inline fun <T> captureFailure(
    callerContext: CoroutineContext,
    onFailure: (Throwable) -> Unit = {},
    block: () -> T,
): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    onFailure(cancelled)
    callerContext.ensureActive()
    Result.failure(cancelled)
} catch (failure: Throwable) {
    onFailure(failure)
    Result.failure(failure)
}
