package io.bluetape4k.graph.tinkerpop

import io.bluetape4k.graph.GraphQueryException
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.PathOptions

internal fun tinkerGraphTraversalFailure(
    operation: String,
    fromId: GraphElementId,
    toId: GraphElementId,
    options: PathOptions,
    cause: Exception,
): GraphQueryException =
    cause as? GraphQueryException
        ?: GraphQueryException(
            "TinkerGraph $operation traversal failed: from=${fromId.value}, to=${toId.value}, options=$options",
            cause,
        )
