dependencies {
    api(project(":bluetape4k-graph-core"))
    api(libs.neo4j.java.driver)
    runtimeOnly(libs.neo4j.bolt.connection.netty)
    runtimeOnly(libs.neo4j.bolt.connection.pooled)
    api(libs.caffeine.core)

    api(libs.bluetape4k.coroutines)
    api(libs.kotlinx.coroutines.reactive)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.bluetape4k.testcontainers)
    testImplementation(libs.testcontainers.neo4j)
    testImplementation(libs.kotlinx.coroutines.test.lib)
    testImplementation(libs.mockk)
}
