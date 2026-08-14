package io.bluetape4k.graph.ktor

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.graph.age.AgeGraphSuspendOperations
import io.bluetape4k.graph.age.sql.AgeSql
import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requirePositiveNumber
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager

/**
 * [GraphPlugin]이 소유하는 Apache AGE JDBC pool을 생성하는 Ktor DSL.
 *
 * ## 동작 계약
 * - [jdbcUrl], [username], [graphName], [connectionInitSql], [driverClassName]은 blank이면 안 된다.
 * - [maximumPoolSize]는 양수여야 한다.
 * - 관리 pool은 AGE operations 생성 전에 명시적인 Exposed [Database]를 생성한다.
 * - 이 DSL이 만든 Hikari pool은 plugin 소유이며 `ApplicationStopped`에서 닫힌다.
 * - 기존 `age(graphName)` helper는 호출자 소유 `Database` / `DataSource` 계약을 유지한다.
 *
 * ```kotlin
 * install(GraphPlugin) {
 *     ageDataSource {
 *         jdbcUrl = "jdbc:postgresql://localhost:5432/postgres"
 *         username = "postgres"
 *         password = "secret"
 *         graphName = "social"
 *     }
 * }
 * ```
 */
class ManagedAgeDataSourceGraphPluginConfig {
    var jdbcUrl: String = "jdbc:postgresql://localhost:5432/postgres"
    var username: String = "postgres"
    var password: String = ""
    var graphName: String = "default"
    var connectionInitSql: String = "${AgeSql.loadAge()}; ${AgeSql.setSearchPath()};"
    var driverClassName: String = "org.postgresql.Driver"
    var maximumPoolSize: Int = 4
}

/**
 * [GraphPlugin]을 plugin 소유 Apache AGE JDBC pool로 설정한다.
 */
@Suppress("TooGenericExceptionCaught")
fun GraphPluginConfig.ageDataSource(
    configure: ManagedAgeDataSourceGraphPluginConfig.() -> Unit,
): GraphPluginConfig = apply {
    val props = ManagedAgeDataSourceGraphPluginConfig().apply(configure)
    props.jdbcUrl.requireNotBlank("jdbcUrl")
    props.username.requireNotBlank("username")
    props.graphName.requireNotBlank("graphName")
    props.connectionInitSql.requireNotBlank("connectionInitSql")
    props.driverClassName.requireNotBlank("driverClassName")
    props.maximumPoolSize.requirePositiveNumber("maximumPoolSize")
    ensureBackendAvailable("managedAgeDataSource")

    val resources = ManagedGraphPluginResources()
    try {
        val dataSource = resources.own(
            "AgeDataSource",
            HikariDataSource(HikariConfig().apply {
                jdbcUrl = props.jdbcUrl
                username = props.username
                password = props.password
                driverClassName = props.driverClassName
                connectionInitSql = props.connectionInitSql
                maximumPoolSize = props.maximumPoolSize
            }),
        )
        val previousDefaultDatabase = TransactionManager.defaultDatabase
        val database = Database.connect(dataSource.value)
        TransactionManager.defaultDatabase = previousDefaultDatabase
        val databaseCloseAction = resources.register("AgeExposedDatabase") {
            if (TransactionManager.defaultDatabase === database) {
                TransactionManager.defaultDatabase = previousDefaultDatabase
            }
            TransactionManager.closeAndUnregister(database)
        }

        val graphOperations = resources.own(
            "AgeGraphOperations",
            AgeGraphOperations(database, props.graphName),
        )
        val graphSuspendOperations = resources.own(
            "AgeGraphSuspendOperations",
            AgeGraphSuspendOperations(database, props.graphName),
        )

        configure(
            backendName = "managedAgeDataSource",
            graphOperationsFactory = { graphOperations.value },
            graphSuspendOperationsFactory = { graphSuspendOperations.value },
            closeActions = listOf(
                graphOperations.closeAction,
                graphSuspendOperations.closeAction,
                databaseCloseAction,
                dataSource.closeAction,
            ),
        )
        resources.commit()
    } catch (e: Exception) {
        resources.rollback()
        throw e
    }
}
