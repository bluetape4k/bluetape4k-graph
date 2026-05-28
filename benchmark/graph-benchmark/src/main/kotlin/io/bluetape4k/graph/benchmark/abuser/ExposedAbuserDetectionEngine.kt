package io.bluetape4k.graph.benchmark.abuser

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import javax.sql.DataSource

class ExposedAbuserDetectionEngine(
    private val dataSource: DataSource,
    private val traversalMode: SqlTraversalMode = SqlTraversalMode.RECURSIVE_CTE,
): AbuserDetectionEngine {

    override val implementationName: String = "exposed-${traversalMode.displayName}"

    private val database: Database by lazy { Database.connect(dataSource) }
    private var expectedAbusiveAccountIds: Set<String> = emptySet()
    private var scenario: AbuserDetectionScenario = AbuserDetectionScenario.SHARED

    override fun reset() {
        transaction(database) {
            SchemaUtils.drop(AbuserEdgesTable, AbuserAccountsTable)
            SchemaUtils.create(AbuserAccountsTable, AbuserEdgesTable)
            exec("CREATE INDEX IF NOT EXISTS idx_abuser_edges_from ON ${AbuserEdgesTable.tableName}(from_account_id)")
            exec("CREATE INDEX IF NOT EXISTS idx_abuser_edges_to ON ${AbuserEdgesTable.tableName}(to_account_id)")
        }
    }

    override fun load(fixture: AbuserDetectionFixture) {
        expectedAbusiveAccountIds = fixture.expectedAbusiveAccountIds
        scenario = fixture.scenario

        transaction(database) {
            AbuserAccountsTable.batchInsert(fixture.accounts) { account ->
                this[AbuserAccountsTable.accountId] = account.accountId
                this[AbuserAccountsTable.segment] = account.segment
                this[AbuserAccountsTable.knownAbusive] = account.knownAbusive
                this[AbuserAccountsTable.expectedAbusive] = account.expectedAbusive
                this[AbuserAccountsTable.riskScore] = account.riskScore
                this[AbuserAccountsTable.accountAgeHours] = account.accountAgeHours
                this[AbuserAccountsTable.sharedDeviceCluster] = account.sharedDeviceCluster
            }
            AbuserEdgesTable.batchInsert(fixture.edges) { edge ->
                this[AbuserEdgesTable.fromAccountId] = edge.fromAccountId
                this[AbuserEdgesTable.toAccountId] = edge.toAccountId
                this[AbuserEdgesTable.kind] = edge.kind.name
                this[AbuserEdgesTable.weight] = edge.weight
                this[AbuserEdgesTable.amount] = edge.amount
                this[AbuserEdgesTable.createdAtMinute] = edge.createdAtMinute
            }
        }
    }

    override fun detect(): AbuserDetectionResult {
        val predicted = PostgreSqlAbuserDetectionSupport.reachableAccountIds(
            dataSource = dataSource,
            accountsTableName = AbuserAccountsTable.tableName,
            edgesTableName = AbuserEdgesTable.tableName,
            scenario = scenario,
            maxDepth = scenario.hopLimit,
            mode = traversalMode,
        )
        return AbuserDetectionResult(implementationName, predicted, expectedAbusiveAccountIds)
    }

    override fun close() = Unit
}

internal object AbuserAccountsTable: Table("abuser_accounts") {
    val accountId = varchar("account_id", 32)
    val segment = varchar("segment", 32)
    val knownAbusive = bool("known_abusive")
    val expectedAbusive = bool("expected_abusive")
    val riskScore = double("risk_score")
    val accountAgeHours = integer("account_age_hours")
    val sharedDeviceCluster = varchar("shared_device_cluster", 64)

    override val primaryKey: PrimaryKey = PrimaryKey(accountId)
}

internal object AbuserEdgesTable: Table("abuser_edges") {
    val id = long("id").autoIncrement()
    val fromAccountId = varchar("from_account_id", 32).references(AbuserAccountsTable.accountId)
    val toAccountId = varchar("to_account_id", 32).references(AbuserAccountsTable.accountId)
    val kind = varchar("kind", 32)
    val weight = double("weight")
    val amount = double("amount")
    val createdAtMinute = integer("created_at_minute")

    override val primaryKey: PrimaryKey = PrimaryKey(id)
}
