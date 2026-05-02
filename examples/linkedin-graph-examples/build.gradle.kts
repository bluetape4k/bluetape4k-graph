dependencies {
    implementation(project(":graph-core"))
    implementation(project(":graph-age"))
    implementation(project(":graph-neo4j"))
    implementation(project(":graph-memgraph"))
    implementation(project(":graph-tinkerpop"))
    implementation(project(":graph-falkordb"))

    implementation(libs.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core.lib)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.bluetape4k.testcontainers)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.neo4j)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.neo4j.java.driver)       // bluetape4k-testcontainers는 compileOnly로 선언
    testRuntimeOnly(libs.postgresql.driver)           // bluetape4k-testcontainers는 compileOnly로 선언
    testImplementation(libs.hikaricp)
    testImplementation(libs.kotlinx.coroutines.test.lib)
    testImplementation(project(":graph-falkordb"))
    testImplementation(testFixtures(project(":graph-falkordb")))
}
