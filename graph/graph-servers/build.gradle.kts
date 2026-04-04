dependencies {
    // Apache AGE (PostgreSQL extension)
    api(Libs.testcontainers_postgresql)
    api(Libs.postgresql_driver)
    api(Libs.hikaricp)

    // Neo4j
    api(Libs.testcontainers_neo4j)
    api(Libs.neo4j_java_driver)
    runtimeOnly(Libs.neo4j_bolt_connection_netty)
    runtimeOnly(Libs.neo4j_bolt_connection_pooled)

    // Memgraph (generic container + neo4j driver)
    api(Libs.testcontainers)
    api(Libs.bluetape4k_testcontainers)

    api(Libs.bluetape4k_core)
}
