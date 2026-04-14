package io.bluetape4k.graph.age

import io.bluetape4k.graph.age.sql.AgeSql
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction


inline fun JdbcTransaction.loadAgeAndSetSearchPath() {
    exec(AgeSql.loadAge())
    exec(AgeSql.setSearchPath())
}
