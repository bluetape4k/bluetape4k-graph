package io.bluetape4k.graph

/**
 * Graph 모듈 최상위 예외.
 */
open class GraphException: RuntimeException {
    constructor(): super()
    constructor(message: String): super(message)
    constructor(message: String, cause: Throwable?): super(message, cause)
    constructor(cause: Throwable): super(cause)
}

/** 정점 또는 간선을 찾을 수 없을 때 */
class GraphNotFoundException: GraphException {
    constructor(): super()
    constructor(message: String): super(message)
    constructor(message: String, cause: Throwable?): super(message, cause)
    constructor(cause: Throwable): super(cause)
}

/** 그래프가 이미 존재할 때 */
class GraphAlreadyExistsException: GraphException {
    constructor(): super()
    constructor(message: String): super(message)
    constructor(message: String, cause: Throwable?): super(message, cause)
    constructor(cause: Throwable): super(cause)
}

/** Cypher/SQL 쿼리 실행 오류 */
class GraphQueryException: GraphException {
    constructor(): super()
    constructor(message: String): super(message)
    constructor(message: String, cause: Throwable?): super(message, cause)
    constructor(cause: Throwable): super(cause)
}

/** 그래프가 존재하지 않을 때 */
class GraphNotInitializedException: GraphException {
    constructor(): super()
    constructor(message: String): super(message)
    constructor(message: String, cause: Throwable?): super(message, cause)
    constructor(cause: Throwable): super(cause)
}
