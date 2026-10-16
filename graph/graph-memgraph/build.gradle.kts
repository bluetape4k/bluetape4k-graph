dependencies {
    api(project(":bluetape4k-graph-core"))

    api(bt4k.neo4j.driver6)
    runtimeOnly(bt4k.neo4j.bolt.connection.netty)
    runtimeOnly(bt4k.neo4j.bolt.connection.pooled)

    // Caffeine Cache
    implementation(bt4k.bluetape4k.cache.core)
    implementation(bt4k.caffeine.core)


    // Coroutines
    api(bt4k.bluetape4k.coroutines)
    api(libs.kotlinx.coroutines.reactive)
    testImplementation(libs.kotlinx.coroutines.test.lib)

    // Test & Testcontainers
    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(bt4k.bluetape4k.testcontainers)
    testImplementation(libs.testcontainers.core)

    testImplementation(testFixtures(project(":bluetape4k-graph-core")))
}
