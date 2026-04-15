package io.bluetape4k.graph.repository

/**
 * 그래프 데이터베이스 세션 관리 (코루틴 방식).
 *
 * 소유권: 외부에서 주입된 Database/Driver를 [close]에서 닫지 않는다.
 * 연결 풀/드라이버 생명주기는 Spring 컨테이너 또는 호출자가 관리한다.
 *
 * ```kotlin
 * runBlocking {
 *     ops.createGraph("social")
 *     ops.graphExists("social")  // true
 *     ops.dropGraph("social")
 *     ops.graphExists("social")  // false
 * }
 * ```
 *
 * @see GraphSession 동기(blocking) 방식
 */
interface GraphSuspendSession : AutoCloseable {
    /**
     * 지정한 이름의 그래프를 생성한다.
     *
     * ```kotlin
     * ops.createGraph("social")
     * ```
     *
     * @param name 생성할 그래프 이름.
     * @see GraphSession.createGraph 동기 버전
     */
    suspend fun createGraph(name: String)

    /**
     * 지정한 이름의 그래프를 삭제한다.
     *
     * ```kotlin
     * ops.dropGraph("social")
     * ```
     *
     * @param name 삭제할 그래프 이름.
     * @see GraphSession.dropGraph 동기 버전
     */
    suspend fun dropGraph(name: String)

    /**
     * 지정한 이름의 그래프가 존재하는지 확인한다.
     *
     * ```kotlin
     * val exists = ops.graphExists("social")  // true / false
     * ```
     *
     * @param name 확인할 그래프 이름.
     * @return 그래프가 존재하면 `true`, 그렇지 않으면 `false`.
     * @see GraphSession.graphExists 동기 버전
     */
    suspend fun graphExists(name: String): Boolean
}
