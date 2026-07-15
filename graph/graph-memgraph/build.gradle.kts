dependencies {
    api(project(":bluetape4k-graph-core"))
    api(project(":bluetape4k-graph-neo4j"))

    api(libs.neo4j.java.driver)
    runtimeOnly(libs.neo4j.bolt.connection.netty)
    runtimeOnly(libs.neo4j.bolt.connection.pooled)

    api(bt4k.bluetape4k.coroutines)
    api(libs.kotlinx.coroutines.reactive)

    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(bt4k.bluetape4k.testcontainers)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.kotlinx.coroutines.test.lib)
}
