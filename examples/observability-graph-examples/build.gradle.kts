dependencies {
    implementation(project(":bluetape4k-graph-core"))
    implementation(project(":bluetape4k-graph-age"))
    implementation(project(":bluetape4k-graph-neo4j"))
    implementation(project(":bluetape4k-graph-memgraph"))
    implementation(project(":bluetape4k-graph-tinkerpop"))
    implementation(project(":bluetape4k-graph-falkordb"))
    implementation(project(":bluetape4k-graph-io-csv"))

    implementation(bt4k.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core.lib)

    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(bt4k.bluetape4k.testcontainers)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.neo4j)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.neo4j.java.driver)
    testRuntimeOnly(bt4k.postgresql)
    testImplementation(bt4k.hikaricp)
    testImplementation(libs.kotlinx.coroutines.test.lib)
    testImplementation(project(":bluetape4k-graph-falkordb"))
    testImplementation(testFixtures(project(":bluetape4k-graph-falkordb")))
}
