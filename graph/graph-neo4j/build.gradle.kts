dependencies {
    api(project(":graph-core"))
    testImplementation(project(":graph-servers"))
    api(Libs.neo4j_java_driver)
    runtimeOnly(Libs.neo4j_bolt_connection_netty)
    runtimeOnly(Libs.neo4j_bolt_connection_pooled)

    api(Libs.bluetape4k_coroutines)
    api(Libs.kotlinx_coroutines_reactive)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers_neo4j)
    testImplementation(Libs.kotlinx_coroutines_test)
}
