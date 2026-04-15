package io.bluetape4k.graph.age

import io.bluetape4k.graph.age.sql.AgeSql
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction


/**
 * Exposed [JdbcTransaction] 에서 Apache AGE 확장을 로드하고 search_path를 설정한다.
 *
 * 매 트랜잭션 시작 시 `LOAD 'age'` 와 `SET search_path = ag_catalog, "$user", public` 을
 * 실행하여 AGE Cypher 함수가 정상 동작하도록 보장한다.
 *
 * ```kotlin
 * newSuspendedTransaction {
 *     loadAgeAndSetSearchPath()
 *     exec(AgeSql.createVertex(graphName, "Person", mapOf("name" to "Alice"))) { rs ->
 *         if (rs.next()) AgeTypeParser.parseVertex(rs.getString("v"))
 *     }
 * }
 * ```
 */
fun JdbcTransaction.loadAgeAndSetSearchPath() {
    exec(AgeSql.loadAge())
    exec(AgeSql.setSearchPath())
}
