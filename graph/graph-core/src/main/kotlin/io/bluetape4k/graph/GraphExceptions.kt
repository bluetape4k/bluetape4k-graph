package io.bluetape4k.graph

/**
 * Base exception for the graph module.
 *
 * ```kotlin
 * try {
 *     ops.createGraph("existing")
 * } catch (e: GraphAlreadyExistsException) {
 *     // Handle an existing graph.
 * } catch (e: GraphQueryException) {
 *     // Handle Cypher/SQL execution errors.
 * }
 * ```
 */
open class GraphException: RuntimeException {
    constructor(): super()
    constructor(message: String): super(message)
    constructor(message: String, cause: Throwable?): super(message, cause)
    constructor(cause: Throwable): super(cause)
}

/**
 * Thrown when a graph cannot be found.
 *
 * ```kotlin
 * throw GraphNotFoundException("social")
 * ```
 */
class GraphNotFoundException: GraphException {
    constructor(): super()
    constructor(message: String): super(message)
    constructor(message: String, cause: Throwable?): super(message, cause)
    constructor(cause: Throwable): super(cause)
}

/**
 * Thrown when a graph with the same name already exists.
 *
 * ```kotlin
 * throw GraphAlreadyExistsException("social")
 * ```
 */
class GraphAlreadyExistsException: GraphException {
    constructor(): super()
    constructor(message: String): super(message)
    constructor(message: String, cause: Throwable?): super(message, cause)
    constructor(cause: Throwable): super(cause)
}

/**
 * Thrown when graph query execution fails.
 *
 * ```kotlin
 * throw GraphQueryException("Failed to create vertex: Person")
 * ```
 */
class GraphQueryException: GraphException {
    constructor(): super()
    constructor(message: String): super(message)
    constructor(message: String, cause: Throwable?): super(message, cause)
    constructor(cause: Throwable): super(cause)
}

/**
 * Thrown when a graph is accessed before initialization.
 *
 * ```kotlin
 * throw GraphNotInitializedException("social graph is not initialized")
 * ```
 */
class GraphNotInitializedException: GraphException {
    constructor(): super()
    constructor(message: String): super(message)
    constructor(message: String, cause: Throwable?): super(message, cause)
    constructor(cause: Throwable): super(cause)
}
