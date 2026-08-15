dependencies {
    api(project(":bluetape4k-graph-core"))
    api(bt4k.neo4j.driver6)
    runtimeOnly(bt4k.neo4j.bolt.connection.netty)
    runtimeOnly(bt4k.neo4j.bolt.connection.pooled)
    implementation(bt4k.caffeine.core)

    api(bt4k.bluetape4k.coroutines)
    api(libs.kotlinx.coroutines.reactive)

    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(bt4k.bluetape4k.testcontainers)
    testImplementation(libs.testcontainers.neo4j)
    testImplementation(libs.kotlinx.coroutines.test.lib)
    testImplementation(bt4k.mockk)
    testImplementation(testFixtures(project(":bluetape4k-graph-core")))
}
