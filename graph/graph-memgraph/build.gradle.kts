dependencies {
    api(project(":bluetape4k-graph-core"))

    api(bt4k.neo4j.driver6)
    implementation(bt4k.caffeine.core)
    runtimeOnly(bt4k.neo4j.bolt.connection.netty)
    runtimeOnly(bt4k.neo4j.bolt.connection.pooled)

    api(bt4k.bluetape4k.coroutines)
    api(libs.kotlinx.coroutines.reactive)

    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(bt4k.bluetape4k.testcontainers)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.kotlinx.coroutines.test.lib)
}
