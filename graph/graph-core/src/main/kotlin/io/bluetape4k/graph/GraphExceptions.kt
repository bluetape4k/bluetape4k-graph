package io.bluetape4k.graph

/**
 * Graph 모듈 최상위 예외.
 *
 * ```kotlin
 * try {
 *     ops.createGraph("existing")
 * } catch (e: GraphAlreadyExistsException) {
 *     // 이미 존재하는 그래프 처리
 * } catch (e: GraphQueryException) {
 *     // Cypher/SQL 실행 오류 처리
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
 * 지정한 그래프를 찾을 수 없을 때 발생하는 예외.
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
 * 동일한 이름의 그래프가 이미 존재할 때 발생하는 예외.
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
 * 그래프 쿼리 실행 실패 시 발생하는 예외.
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
 * 그래프가 초기화되지 않은 상태에서 접근할 때 발생하는 예외.
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
