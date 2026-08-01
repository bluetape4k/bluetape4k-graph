package io.bluetape4k.graph.repository

import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.support.requireEquals
import io.bluetape4k.support.requireNotNull
import java.util.concurrent.CompletableFuture

/**
 * ID로 정점을 조회하고 요청한 endpoint label인지 검증합니다.
 *
 * 정점이 없으면 [parameterName], label이 다르면 `"$parameterName.label"`을
 * 인자 이름으로 사용하는 [IllegalArgumentException]을 던집니다.
 *
 * ```kotlin
 * val person = repository.requireEndpoint(personId, "Person", "personId")
 * ```
 *
 * @param id 조회할 endpoint 정점 ID입니다.
 * @param expectedLabel endpoint에 필요한 정점 label입니다.
 * @param parameterName 오류 메시지에 사용할 호출자 인자 이름입니다.
 * @return 존재하며 [expectedLabel]과 label이 일치하는 정점입니다.
 * @throws IllegalArgumentException 정점이 없거나 label이 [expectedLabel]과 다를 때 발생합니다.
 */
fun GraphVertexRepository.requireEndpoint(
    id: GraphElementId,
    expectedLabel: String,
    parameterName: String,
): GraphVertex = validateEndpoint(findVertexById(id), expectedLabel, parameterName)

/**
 * ID로 정점을 비동기 조회하고 요청한 endpoint label인지 검증합니다.
 *
 * 정점이 없으면 [parameterName], label이 다르면 `"$parameterName.label"`을
 * 인자 이름으로 사용하는 [IllegalArgumentException]을 던집니다.
 *
 * ```kotlin
 * val person = repository.requireEndpoint(personId, "Person", "personId")
 * ```
 *
 * @param id 조회할 endpoint 정점 ID입니다.
 * @param expectedLabel endpoint에 필요한 정점 label입니다.
 * @param parameterName 오류 메시지에 사용할 호출자 인자 이름입니다.
 * @return 존재하며 [expectedLabel]과 label이 일치하는 정점입니다.
 * @throws IllegalArgumentException 정점이 없거나 label이 [expectedLabel]과 다를 때 발생합니다.
 */
suspend fun GraphSuspendVertexRepository.requireEndpoint(
    id: GraphElementId,
    expectedLabel: String,
    parameterName: String,
): GraphVertex = validateEndpoint(findVertexById(id), expectedLabel, parameterName)

/**
 * ID로 정점을 virtual thread에서 조회하고 요청한 endpoint label인지 검증합니다.
 *
 * 정점이 없거나 label이 다르면 반환된 [CompletableFuture]가
 * [IllegalArgumentException]을 원인으로 예외 완료됩니다. 누락 오류에는 [parameterName],
 * label 불일치 오류에는 `"$parameterName.label"`이 인자 이름으로 사용됩니다.
 *
 * ```kotlin
 * val personFuture = repository.requireEndpointAsync(personId, "Person", "personId")
 * ```
 *
 * @param id 조회할 endpoint 정점 ID입니다.
 * @param expectedLabel endpoint에 필요한 정점 label입니다.
 * @param parameterName 오류 메시지에 사용할 호출자 인자 이름입니다.
 * @return 검증된 정점을 제공하는 [CompletableFuture]입니다.
 */
fun GraphVirtualThreadVertexRepository.requireEndpointAsync(
    id: GraphElementId,
    expectedLabel: String,
    parameterName: String,
): CompletableFuture<GraphVertex> =
    findVertexByIdAsync(id).thenApply { vertex ->
        validateEndpoint(vertex, expectedLabel, parameterName)
    }

private fun validateEndpoint(
    vertex: GraphVertex?,
    expectedLabel: String,
    parameterName: String,
): GraphVertex {
    val endpoint = vertex.requireNotNull(parameterName)
    endpoint.label.requireEquals(expectedLabel, "$parameterName.label")
    return endpoint
}
