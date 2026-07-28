package io.bluetape4k.graph.neo4j

import io.bluetape4k.logging.coroutines.KLoggingChannel
import io.bluetape4k.support.requireNotBlank
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import org.neo4j.driver.Driver
import org.neo4j.driver.Query
import org.neo4j.driver.Record
import org.neo4j.driver.SessionConfig
import org.neo4j.driver.reactivestreams.ReactiveSession

/**
 * Neo4j Java Driver reactive API를 Kotlin coroutine으로 연결한다.
 *
 * `org.neo4j.driver.reactivestreams.ReactiveSession`이 반환하는 `org.reactivestreams.Publisher<T>` 값을
 * `kotlinx-coroutines-reactive`를 통해 변환한다.
 *
 * 소유권: [close]는 외부에서 전달된 [Driver]를 닫지 않는다.
 *
 * ```kotlin
 * val session = Neo4jCoroutineSession(driver)
 *
 * // 쓰기 query
 * val records = session.runWriteQuery(
 *     "CREATE (n:Person {name: $name}) RETURN n",
 *     mapOf("name" to "Alice")
 * )
 *
 * // 읽기 query
 * val results = session.runReadQuery("MATCH (n:Person) RETURN n")
 * val vertices = results.map { Neo4jRecordMapper.recordToVertex(it) }
 * ```
 *
 * @param driver 외부에서 소유하고 수명주기를 관리하는 Neo4j Java Driver.
 * @param database Neo4j database 이름. 기본값은 `"neo4j"`.
 */
class Neo4jCoroutineSession(
    private val driver: Driver,
    private val database: String = "neo4j",
): AutoCloseable {

    companion object: KLoggingChannel()

    /**
     * 읽기 전용 reactive session block을 실행한다.
     *
     * ```kotlin
     * val vertices = session.read { s ->
     *     s.run(Query("MATCH (n:Person) RETURN n")).awaitSingle()
     *         .records().asFlow().map { Neo4jRecordMapper.recordToVertex(it) }
     * }
     * ```
     *
     */
    suspend fun <T> read(block: suspend (ReactiveSession) -> Flow<T>): List<T> {
        val session = driver.session(ReactiveSession::class.java, sessionConfig())
        return try {
            block(session).toList()
        } finally {
            session.close<Void>().awaitFirstOrNull()
        }
    }

    /**
     * 쓰기 reactive session block을 실행한다.
     *
     * ```kotlin
     * session.write { s ->
     *     s.run(Query("CREATE (n:Person {name: 'Alice'}) RETURN n")).awaitSingle()
     *         .records().asFlow()
     * }
     * ```
     *
     */
    suspend fun <T> write(block: suspend (ReactiveSession) -> Flow<T>): List<T> {
        val session = driver.session(ReactiveSession::class.java, sessionConfig())
        return try {
            block(session).toList()
        } finally {
            session.close<Void>().awaitFirstOrNull()
        }
    }

    /**
     * 읽기 query를 실행하고 materialized record 목록을 반환한다.
     *
     * ```kotlin
     * val records = session.runReadQuery(
     *     "MATCH (n:Person) WHERE n.name = $name RETURN n",
     *     mapOf("name" to "Alice")
     * )
     * val vertices = records.map { Neo4jRecordMapper.recordToVertex(it) }
     * ```
     *
     */
    suspend fun runReadQuery(cypher: String, params: Map<String, Any?> = emptyMap()): List<Record> {
        cypher.requireNotBlank("cypher")

        val session = driver.session(ReactiveSession::class.java, sessionConfig())
        return try {
            val result = session.run(Query(cypher, params)).awaitSingle()
            result.records().asFlow().toList()
        } finally {
            session.close<Void>().awaitFirstOrNull()
        }
    }

    /**
     * 쓰기 query를 실행하고 materialized record 목록을 반환한다.
     *
     * ```kotlin
     * val records = session.runWriteQuery(
     *     "CREATE (n:Person {name: $name}) RETURN n",
     *     mapOf("name" to "Alice")
     * )
     * ```
     *
     */
    suspend fun runWriteQuery(cypher: String, params: Map<String, Any?> = emptyMap()): List<Record> {
        cypher.requireNotBlank("cypher")
        return runReadQuery(cypher, params)
    }

    override fun close() {
        // driver는 외부 소유이다.
    }

    private fun sessionConfig(): SessionConfig =
        SessionConfig.builder()
            .withDatabase(database)
            .build()
}
