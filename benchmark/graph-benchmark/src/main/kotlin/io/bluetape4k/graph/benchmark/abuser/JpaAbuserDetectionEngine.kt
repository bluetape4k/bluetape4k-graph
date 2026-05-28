package io.bluetape4k.graph.benchmark.abuser

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.SessionFactory
import org.hibernate.boot.MetadataSources
import org.hibernate.boot.registry.StandardServiceRegistry
import org.hibernate.boot.registry.StandardServiceRegistryBuilder
import org.hibernate.cfg.AvailableSettings
import org.hibernate.cfg.JdbcSettings
import javax.sql.DataSource

class JpaAbuserDetectionEngine(
    private val dataSource: DataSource,
    private val traversalMode: SqlTraversalMode = SqlTraversalMode.RECURSIVE_CTE,
): AbuserDetectionEngine {

    override val implementationName: String = "jpa-${traversalMode.displayName}"

    private var serviceRegistry: StandardServiceRegistry? = null
    private var sessionFactory: SessionFactory? = null
    private var scenario: AbuserDetectionScenario = AbuserDetectionScenario.SHARED

    private fun getSessionFactory(): SessionFactory {
        sessionFactory?.let { return it }

        val registry = StandardServiceRegistryBuilder()
            .applySettings(
                mapOf(
                    JdbcSettings.JAKARTA_NON_JTA_DATASOURCE to dataSource,
                    AvailableSettings.HBM2DDL_AUTO to "none",
                    AvailableSettings.SHOW_SQL to "false",
                    AvailableSettings.FORMAT_SQL to "false",
                ),
            )
            .build()
        serviceRegistry = registry

        return MetadataSources(registry)
            .addAnnotatedClass(JpaAbuserAccount::class.java)
            .addAnnotatedClass(JpaAbuserEdge::class.java)
            .buildMetadata()
            .buildSessionFactory()
            .also { sessionFactory = it }
    }

    private var expectedAbusiveAccountIds: Set<String> = emptySet()

    override fun reset() {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("DROP TABLE IF EXISTS $EDGE_TABLE")
                statement.execute("DROP TABLE IF EXISTS $ACCOUNT_TABLE")
                statement.execute(
                    """
                    CREATE TABLE $ACCOUNT_TABLE (
                        account_id VARCHAR(32) PRIMARY KEY,
                        segment VARCHAR(32) NOT NULL,
                        known_abusive BOOLEAN NOT NULL,
                        expected_abusive BOOLEAN NOT NULL,
                        risk_score DOUBLE PRECISION NOT NULL,
                        account_age_hours INTEGER NOT NULL,
                        shared_device_cluster VARCHAR(64) NOT NULL
                    )
                    """.trimIndent(),
                )
                statement.execute(
                    """
                    CREATE TABLE $EDGE_TABLE (
                        id BIGSERIAL PRIMARY KEY,
                        from_account_id VARCHAR(32) NOT NULL REFERENCES $ACCOUNT_TABLE(account_id),
                        to_account_id VARCHAR(32) NOT NULL REFERENCES $ACCOUNT_TABLE(account_id),
                        kind VARCHAR(32) NOT NULL,
                        weight DOUBLE PRECISION NOT NULL,
                        amount DOUBLE PRECISION NOT NULL,
                        created_at_minute INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                statement.execute("CREATE INDEX idx_jpa_abuser_edges_from ON $EDGE_TABLE(from_account_id)")
                statement.execute("CREATE INDEX idx_jpa_abuser_edges_to ON $EDGE_TABLE(to_account_id)")
            }
        }
    }

    override fun load(fixture: AbuserDetectionFixture) {
        expectedAbusiveAccountIds = fixture.expectedAbusiveAccountIds
        scenario = fixture.scenario

        getSessionFactory().openSession().use { session ->
            val transaction = session.beginTransaction()
            try {
                fixture.accounts.forEachIndexed { index, account ->
                    session.persist(
                        JpaAbuserAccount(
                            accountId = account.accountId,
                            segment = account.segment,
                            knownAbusive = account.knownAbusive,
                            expectedAbusive = account.expectedAbusive,
                            riskScore = account.riskScore,
                            accountAgeHours = account.accountAgeHours,
                            sharedDeviceCluster = account.sharedDeviceCluster,
                        ),
                    )
                    session.flushAndClear(index)
                }
                fixture.edges.forEachIndexed { index, edge ->
                    session.persist(
                        JpaAbuserEdge(
                            fromAccountId = edge.fromAccountId,
                            toAccountId = edge.toAccountId,
                            kind = edge.kind.name,
                            weight = edge.weight,
                            amount = edge.amount,
                            createdAtMinute = edge.createdAtMinute,
                        ),
                    )
                    session.flushAndClear(index)
                }
                transaction.commit()
            } catch (e: Exception) {
                if (transaction.status.canRollback()) {
                    transaction.rollback()
                }
                throw e
            }
        }
    }

    override fun detect(): AbuserDetectionResult {
        val predicted = PostgreSqlAbuserDetectionSupport.reachableAccountIds(
            dataSource = dataSource,
            accountsTableName = ACCOUNT_TABLE,
            edgesTableName = EDGE_TABLE,
            scenario = scenario,
            maxDepth = scenario.hopLimit,
            mode = traversalMode,
        )
        return AbuserDetectionResult(implementationName, predicted, expectedAbusiveAccountIds)
    }

    override fun close() {
        runCatching { sessionFactory?.close() }
        runCatching { serviceRegistry?.let(StandardServiceRegistryBuilder::destroy) }
    }

    private fun org.hibernate.Session.flushAndClear(index: Int) {
        if (index > 0 && index % BATCH_FLUSH_SIZE == 0) {
            flush()
            clear()
        }
    }

    private companion object {
        const val ACCOUNT_TABLE = "abuser_jpa_accounts"
        const val EDGE_TABLE = "abuser_jpa_edges"
        const val BATCH_FLUSH_SIZE = 1_000
    }
}

@Entity
@Table(name = "abuser_jpa_accounts")
open class JpaAbuserAccount(
    @Id
    @Column(name = "account_id", length = 32, nullable = false)
    open var accountId: String = "",

    @Column(name = "segment", length = 32, nullable = false)
    open var segment: String = "",

    @Column(name = "known_abusive", nullable = false)
    open var knownAbusive: Boolean = false,

    @Column(name = "expected_abusive", nullable = false)
    open var expectedAbusive: Boolean = false,

    @Column(name = "risk_score", nullable = false)
    open var riskScore: Double = 0.0,

    @Column(name = "account_age_hours", nullable = false)
    open var accountAgeHours: Int = 0,

    @Column(name = "shared_device_cluster", length = 64, nullable = false)
    open var sharedDeviceCluster: String = "",
)

@Entity
@Table(name = "abuser_jpa_edges")
open class JpaAbuserEdge(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    open var id: Long? = null,

    @Column(name = "from_account_id", length = 32, nullable = false)
    open var fromAccountId: String = "",

    @Column(name = "to_account_id", length = 32, nullable = false)
    open var toAccountId: String = "",

    @Column(name = "kind", length = 32, nullable = false)
    open var kind: String = "",

    @Column(name = "weight", nullable = false)
    open var weight: Double = 1.0,

    @Column(name = "amount", nullable = false)
    open var amount: Double = 0.0,

    @Column(name = "created_at_minute", nullable = false)
    open var createdAtMinute: Int = 0,
)
