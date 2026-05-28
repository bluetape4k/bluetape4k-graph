package io.bluetape4k.graph.benchmark.abuser

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

enum class SqlTraversalMode(
    val displayName: String,
) {
    RECURSIVE_CTE("cte"),
    ITERATIVE("iterative"),
}

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
        scenario: AbuserDetectionScenario,
        maxDepth: Int = 2,
        mode: SqlTraversalMode = SqlTraversalMode.RECURSIVE_CTE,
    ): Set<String> =
        when (mode) {
            SqlTraversalMode.RECURSIVE_CTE -> recursiveCteReachableAccountIds(
                dataSource = dataSource,
                accountsTableName = accountsTableName,
                edgesTableName = edgesTableName,
                scenario = scenario,
                maxDepth = maxDepth,
            )
            SqlTraversalMode.ITERATIVE -> iterativeReachableAccountIds(
                dataSource = dataSource,
                accountsTableName = accountsTableName,
                edgesTableName = edgesTableName,
                scenario = scenario,
                maxDepth = maxDepth,
            )
        }

    private fun recursiveCteReachableAccountIds(
        dataSource: DataSource,
        accountsTableName: String,
        edgesTableName: String,
        scenario: AbuserDetectionScenario,
        maxDepth: Int,
    ): Set<String> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                WITH RECURSIVE reachable(source_account_id, account_id, depth) AS (
                    SELECT account.account_id, edge.to_account_id, 1
                    FROM $edgesTableName edge
                    JOIN $accountsTableName account ON account.account_id = edge.from_account_id
                    WHERE account.risk_score >= ?
                      AND edge.kind = 'TRANSFER'
                      AND edge.created_at_minute >= ?
                      AND edge.weight >= ?
                    UNION ALL
                    SELECT reachable.source_account_id, edge.to_account_id, reachable.depth + 1
                    FROM $edgesTableName edge
                    JOIN reachable ON reachable.account_id = edge.from_account_id
                    WHERE reachable.depth < ?
                      AND edge.kind = 'TRANSFER'
                      AND edge.created_at_minute >= ?
                      AND edge.weight >= ?
                )
                SELECT DISTINCT reachable.account_id
                FROM reachable
                GROUP BY reachable.account_id
                HAVING COUNT(DISTINCT reachable.source_account_id) >= ?
                """.trimIndent(),
            ).use { statement ->
                statement.setDouble(1, scenario.riskThreshold)
                statement.setInt(2, scenario.windowStartMinute)
                statement.setDouble(3, scenario.riskThreshold)
                statement.setInt(4, maxDepth)
                statement.setInt(5, scenario.windowStartMinute)
                statement.setDouble(6, scenario.riskThreshold)
                statement.setInt(7, scenario.minDistinctUpstream)
                statement.executeQuery().use { rs ->
                    buildSet {
                        while (rs.next()) {
                            add(rs.getString(1))
                        }
                    }
                }
            }
        }

    private fun iterativeReachableAccountIds(
        dataSource: DataSource,
        accountsTableName: String,
        edgesTableName: String,
        scenario: AbuserDetectionScenario,
        maxDepth: Int,
    ): Set<String> =
        dataSource.connection.use { connection ->
            val riskySourceIds = connection.prepareStatement(
                """
                SELECT account_id
                FROM $accountsTableName
                WHERE risk_score >= ?
                """.trimIndent(),
            ).use { statement ->
                statement.setDouble(1, scenario.riskThreshold)
                statement.executeQuery().use { rs ->
                    buildSet {
                        while (rs.next()) {
                            add(rs.getString(1))
                        }
                    }
                }
            }

            val upstreamByDestination = linkedMapOf<String, MutableSet<String>>()
            var frontier = riskySourceIds.associateWith { setOf(it) }

            repeat(maxDepth) {
                if (frontier.isEmpty()) return@repeat

                val frontierIds = frontier.keys
                val nextFrontier = connection.prepareStatement(
                    """
                    SELECT edge.from_account_id, edge.to_account_id
                    FROM $edgesTableName edge
                    WHERE edge.from_account_id = ANY (?)
                      AND edge.kind = 'TRANSFER'
                      AND edge.created_at_minute >= ?
                      AND edge.weight >= ?
                    """.trimIndent(),
                ).use { statement ->
                    statement.setArray(1, connection.createArrayOf("varchar", frontierIds.toTypedArray()))
                    statement.setInt(2, scenario.windowStartMinute)
                    statement.setDouble(3, scenario.riskThreshold)
                    statement.executeQuery().use { rs ->
                        buildMap<String, MutableSet<String>> {
                            while (rs.next()) {
                                val from = rs.getString(1)
                                val to = rs.getString(2)
                                val upstream = frontier.getValue(from)
                                upstreamByDestination.getOrPut(to) { linkedSetOf() } += upstream
                                getOrPut(to) { linkedSetOf() } += upstream
                            }
                        }
                    }
                }
                frontier = nextFrontier
            }

            upstreamByDestination.asSequence()
                .filter { (_, upstream) -> upstream.size >= scenario.minDistinctUpstream }
                .map { (destination, _) -> destination }
                .toSet()
        }
}
