package io.bluetape4k.graph.ktor

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.graph.age.AgeGraphSuspendOperations
import io.bluetape4k.graph.age.sql.AgeSql
import io.bluetape4k.support.requireNotBlank
import io.bluetape4k.support.requirePositiveNumber
import org.jetbrains.exposed.v1.jdbc.Database

/**
 * Ktor DSL for creating an Apache AGE JDBC pool owned by [GraphPlugin].
 *
 * ## Behavior / Contract
 * - [jdbcUrl], [username], [graphName], [connectionInitSql], and [driverClassName] must not be blank.
 * - [maximumPoolSize] must be positive.
 * - The managed pool is connected to Exposed through `Database.connect(dataSource)` before AGE operations are created.
 * - The Hikari pool created by this DSL is plugin-owned and is closed on `ApplicationStopped`.
 * - The existing `age(graphName)` helper keeps its caller-owned `Database` / `DataSource` contract.
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
 * Configures [GraphPlugin] with a plugin-owned Apache AGE JDBC pool.
 */
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

    val dataSource = HikariDataSource(HikariConfig().apply {
        jdbcUrl = props.jdbcUrl
        username = props.username
        password = props.password
        driverClassName = props.driverClassName
        connectionInitSql = props.connectionInitSql
        maximumPoolSize = props.maximumPoolSize
    })

    try {
        Database.connect(dataSource)

        val graphOperations = AgeGraphOperations(props.graphName)
        val graphSuspendOperations = AgeGraphSuspendOperations(props.graphName)

        configure(
            backendName = "managedAgeDataSource",
            graphOperationsFactory = { graphOperations },
            graphSuspendOperationsFactory = { graphSuspendOperations },
            closeActions = listOf(
                GraphPluginCloseAction("AgeGraphOperations") {
                    graphOperations.close()
                },
                GraphPluginCloseAction("AgeGraphSuspendOperations") {
                    graphSuspendOperations.close()
                },
                GraphPluginCloseAction("AgeDataSource") {
                    dataSource.close()
                },
            ),
        )
    } catch (e: IllegalArgumentException) {
        dataSource.close()
        throw e
    }
}
