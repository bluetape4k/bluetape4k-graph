package io.bluetape4k.graph.io.support

import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.repository.GraphLabelDiscovery
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendLabelDiscovery
import io.bluetape4k.graph.repository.GraphSuspendOperations

/** Resolves the explicit-or-all label contract used by every exporter. */
fun GraphOperations.resolveVertexLabels(requested: Set<String>): Set<String> =
    requested.ifEmpty {
        (this as? GraphLabelDiscovery)?.listVertexLabels()
            ?: error("vertexLabels is empty; backend must implement GraphLabelDiscovery or receive explicit labels")
    }

/** Resolves the explicit-or-all edge label contract used by every exporter. */
fun GraphOperations.resolveEdgeLabels(requested: Set<String>): Set<String> =
    requested.ifEmpty {
        (this as? GraphLabelDiscovery)?.listEdgeLabels()
            ?: error("edgeLabels is empty; backend must implement GraphLabelDiscovery or receive explicit labels")
    }

/** Coroutine variant of [resolveVertexLabels]. */
suspend fun GraphSuspendOperations.resolveVertexLabels(requested: Set<String>): Set<String> =
    requested.ifEmpty {
        (this as? GraphSuspendLabelDiscovery)?.listVertexLabels()
            ?: error("vertexLabels is empty; backend must implement GraphSuspendLabelDiscovery or receive explicit labels")
    }

/** Coroutine variant of [resolveEdgeLabels]. */
suspend fun GraphSuspendOperations.resolveEdgeLabels(requested: Set<String>): Set<String> =
    requested.ifEmpty {
        (this as? GraphSuspendLabelDiscovery)?.listEdgeLabels()
            ?: error("edgeLabels is empty; backend must implement GraphSuspendLabelDiscovery or receive explicit labels")
    }

/** Resolves both label sets once so sync and coroutine paths share the same contract. */
fun GraphExportOptions.resolveLabels(operations: GraphOperations): Pair<Set<String>, Set<String>> =
    operations.resolveVertexLabels(vertexLabels) to operations.resolveEdgeLabels(edgeLabels)

/** Coroutine variant of [GraphExportOptions.resolveLabels]. */
suspend fun GraphExportOptions.resolveLabels(operations: GraphSuspendOperations): Pair<Set<String>, Set<String>> =
    operations.resolveVertexLabels(vertexLabels) to operations.resolveEdgeLabels(edgeLabels)
