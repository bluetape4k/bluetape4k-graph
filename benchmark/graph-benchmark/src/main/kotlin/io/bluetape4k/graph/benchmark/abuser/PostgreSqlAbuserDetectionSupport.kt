package io.bluetape4k.graph.benchmark.abuser

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

internal object PostgreSqlAbuserDetectionSupport {

    fun createDataSource(
        jdbcUrl: String,
        username: String,
        password: String,
        poolName: String,
        loadAge: Boolean,
    ): HikariDataSource =
        HikariDataSource(HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = username
            this.password = password
            driverClassName = "org.postgresql.Driver"
            maximumPoolSize = 4
            this.poolName = poolName
            if (loadAge) {
                connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
            }
        })

    fun reachableAccountIds(
        dataSource: DataSource,
        accountsTableName: String,
        edgesTableName: String,
        maxDepth: Int = 2,
    ): Set<String> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                WITH RECURSIVE reachable(account_id, depth) AS (
                    SELECT edge.to_account_id, 1
                    FROM $edgesTableName edge
                    JOIN $accountsTableName account ON account.account_id = edge.from_account_id
                    WHERE account.known_abusive = TRUE
                    UNION ALL
                    SELECT edge.to_account_id, reachable.depth + 1
                    FROM $edgesTableName edge
                    JOIN reachable ON reachable.account_id = edge.from_account_id
                    WHERE reachable.depth < ?
                )
                SELECT DISTINCT reachable.account_id
                FROM reachable
                WHERE reachable.account_id NOT IN (
                    SELECT account_id FROM $accountsTableName WHERE known_abusive = TRUE
                )
                """.trimIndent(),
            ).use { statement ->
                statement.setInt(1, maxDepth)
                statement.executeQuery().use { rs ->
                    buildSet {
                        while (rs.next()) {
                            add(rs.getString(1))
                        }
                    }
                }
            }
        }
}
