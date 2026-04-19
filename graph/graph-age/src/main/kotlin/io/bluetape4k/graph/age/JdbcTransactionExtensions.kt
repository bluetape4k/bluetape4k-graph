package io.bluetape4k.graph.age

import org.jetbrains.exposed.v1.jdbc.JdbcTransaction


/**
 * [DEPRECATED - no-op] Exposed [JdbcTransaction]에서 Apache AGE 확장 로드/ search_path 설정.
 *
 * 이 함수는 이제 **아무 동작도 하지 않는 no-op**이다.
 *
 * 이전에는 매 트랜잭션마다 `LOAD 'age'` + `SET search_path = ag_catalog, "$user", public`을
 * 실행했지만, 이제는 **HikariCP의 `connectionInitSql`이 Connection 생성 시점에 1회 실행**하여
 * 동일한 역할을 수행한다. 따라서 per-transaction 호출은 중복된 JDBC round-trip만 유발했다.
 *
 * **필수 전제조건**: DataSource는 반드시 다음 `connectionInitSql`로 구성되어야 한다:
 * ```kotlin
 * HikariConfig().apply {
 *     connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
 * }
 * ```
 *
 * ABI/source 호환성을 위해 함수 시그니처는 유지한다. 호출자는 안전하게 제거할 수 있다.
 */
@Deprecated(
    message = "No-op: AGE load and search_path are handled by HikariCP connectionInitSql. Remove calls to this function.",
    replaceWith = ReplaceWith(""),
    level = DeprecationLevel.WARNING,
)
fun JdbcTransaction.loadAgeAndSetSearchPath() {
    // No-op: HikariCP connectionInitSql already runs LOAD 'age' and SET search_path
    // once per physical connection. Repeating it per-transaction wastes 2 JDBC round-trips.
}
