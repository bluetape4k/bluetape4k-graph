package io.bluetape4k.graph.vt

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations

/**
 * [GraphOperations] 를 Virtual Thread facade 로 감싸는 확장 함수.
 *
 * `GraphOperations` 수신자에 대해 더 구체적인 오버로드가 되므로
 * `GraphAlgorithmRepository.asVirtualThread()` 보다 우선 선택된다.
 *
 * ```kotlin
 * val vtOps = ops.asVirtualThread()      // GraphVirtualThreadOperations
 * val algoOnly = (ops as GraphAlgorithmRepository).asVirtualThread()
 * ```
 */
fun GraphOperations.asVirtualThread(): GraphVirtualThreadOperations =
    VirtualThreadOperationsAdapter(this)
