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
 * Bridges the Neo4j Java Driver reactive API to Kotlin coroutines.
 *
 * It converts `org.reactivestreams.Publisher<T>` values returned by
 * `org.neo4j.driver.reactivestreams.ReactiveSession` through `kotlinx-coroutines-reactive`.
 *
 * Ownership: [close] does not close the externally provided [Driver].
 *
 * ```kotlin
 * val session = Neo4jCoroutineSession(driver)
 *
 * // Write query
 * val records = session.runWriteQuery(
 *     "CREATE (n:Person {name: $name}) RETURN n",
 *     mapOf("name" to "Alice")
 * )
 *
 * // Read query
 * val results = session.runReadQuery("MATCH (n:Person) RETURN n")
 * val vertices = results.map { Neo4jRecordMapper.recordToVertex(it) }
 * ```
 *
 * @param driver externally owned Neo4j Java Driver.
 * @param database Neo4j database name, defaulting to `"neo4j"`.
 */
class Neo4jCoroutineSession(
    private val driver: Driver,
    private val database: String = "neo4j",
): AutoCloseable {

    companion object: KLoggingChannel()

    /**
     * Runs a read-only reactive session block.
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
     * Runs a write reactive session block.
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
     * Runs a read query and returns the materialized records.
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
     * Runs a write query and returns the materialized records.
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
        // The driver is externally owned.
    }

    private fun sessionConfig(): SessionConfig =
        SessionConfig.builder()
            .withDatabase(database)
            .build()
}
