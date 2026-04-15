package io.bluetape4k.graph.repository

/**
 * 그래프 데이터베이스 세션 관리 (동기 방식).
 *
 * 소유권: 외부에서 주입된 Database/Driver를 [close]에서 닫지 않는다.
 * 연결 풀/드라이버 생명주기는 Spring 컨테이너 또는 호출자가 관리한다.
 *
 * ```kotlin
 * ops.createGraph("social")          // 그래프 생성
 * ops.graphExists("social")          // true
 * ops.dropGraph("social")            // 삭제
 * ops.graphExists("social")          // false
 * ```
 */
interface GraphSession : AutoCloseable {
    /**
     * 지정한 이름의 그래프를 생성한다.
     *
     * 이미 존재하는 그래프에 대해 호출하면 [io.bluetape4k.graph.GraphAlreadyExistsException]을
     * 발생시키거나 백엔드 구현에 따라 무시할 수 있다.
     *
     * ```kotlin
     * ops.createGraph("social")
     * ops.graphExists("social")  // true
     * ```
     *
     * @param name 생성할 그래프 이름.
     */
    fun createGraph(name: String)

    /**
     * 지정한 이름의 그래프를 삭제한다.
     *
     * 존재하지 않는 그래프에 대해 호출하면 [io.bluetape4k.graph.GraphNotFoundException]을
     * 발생시키거나 백엔드 구현에 따라 무시할 수 있다.
     *
     * ```kotlin
     * ops.dropGraph("social")
     * ops.graphExists("social")  // false
     * ```
     *
     * @param name 삭제할 그래프 이름.
     */
    fun dropGraph(name: String)

    /**
     * 지정한 이름의 그래프가 존재하는지 확인한다.
     *
     * ```kotlin
     * ops.createGraph("social")
     * ops.graphExists("social")  // true
     * ops.dropGraph("social")
     * ops.graphExists("social")  // false
     * ```
     *
     * @param name 확인할 그래프 이름.
     * @return 그래프가 존재하면 `true`, 그렇지 않으면 `false`.
     */
    fun graphExists(name: String): Boolean
}
