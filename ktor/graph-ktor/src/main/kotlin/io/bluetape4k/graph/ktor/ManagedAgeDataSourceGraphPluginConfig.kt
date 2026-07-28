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
 * [GraphPlugin]이 소유하는 Apache AGE JDBC pool을 생성하는 Ktor DSL.
 *
 * ## 동작 계약
 * - [jdbcUrl], [username], [graphName], [connectionInitSql], [driverClassName]은 blank이면 안 된다.
 * - [maximumPoolSize]는 양수여야 한다.
 * - 관리 pool은 AGE operations 생성 전에 `Database.connect(dataSource)`로 Exposed에 연결된다.
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
