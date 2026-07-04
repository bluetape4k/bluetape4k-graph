package io.bluetape4k.graph.vt

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations

/**
 * Wraps [GraphOperations] in a virtual-thread facade.
 *
 * This overload is more specific for `GraphOperations` receivers than
 * `GraphAlgorithmRepository.asVirtualThread()`, so overload resolution prefers it.
 *
 * ```kotlin
 * val vtOps = ops.asVirtualThread()      // GraphVirtualThreadOperations
 * val algoOnly = (ops as GraphAlgorithmRepository).asVirtualThread()
 * ```
 */
fun GraphOperations.asVirtualThread(): GraphVirtualThreadOperations =
    VirtualThreadOperationsAdapter(this)
