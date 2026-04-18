dependencies {
    implementation(project(":graph-core"))
    implementation(project(":graph-age"))
    implementation(project(":graph-neo4j"))
    implementation(project(":graph-memgraph"))
    implementation(project(":graph-tinkerpop"))

    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.kotlinx_coroutines_core)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers)
    testImplementation(Libs.testcontainers_neo4j)
    testImplementation(Libs.testcontainers_postgresql)
    testImplementation(Libs.neo4j_java_driver)       // bluetape4k-testcontainers는 compileOnly로 선언
    testRuntimeOnly(Libs.postgresql_driver)           // bluetape4k-testcontainers는 compileOnly로 선언
    testImplementation(Libs.hikaricp)
    testImplementation(Libs.kotlinx_coroutines_test)
}
