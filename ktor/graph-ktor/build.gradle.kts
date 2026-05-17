configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

dependencyManagement {
    imports {
        mavenBom(libs.ktor.bom.get().toString())
    }
}

dependencies {
    api(project(":bluetape4k-graph-core"))

    api(libs.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core.lib)

    // Ktor 3.x — application/plugin DSL
    compileOnly(libs.ktor.server.core)

    // Backend helpers are compile-only; applications choose the concrete runtime backend.
    compileOnly(project(":bluetape4k-graph-tinkerpop"))
    compileOnly(project(":bluetape4k-graph-neo4j"))
    compileOnly(project(":bluetape4k-graph-memgraph"))
    compileOnly(project(":bluetape4k-graph-age"))
    compileOnly(project(":bluetape4k-graph-falkordb"))

    // Logging
    implementation(libs.bluetape4k.logging)

    // Testing
    testImplementation(libs.ktor.server.core)
    testImplementation(libs.ktor.server.cio)
    testImplementation(libs.ktor.server.test.host)

    testImplementation(project(":bluetape4k-graph-tinkerpop"))
    testImplementation(project(":bluetape4k-graph-neo4j"))
    testImplementation(project(":bluetape4k-graph-memgraph"))
    testImplementation(project(":bluetape4k-graph-age"))
    testImplementation(project(":bluetape4k-graph-falkordb"))
    testImplementation(testFixtures(project(":bluetape4k-graph-falkordb")))

    testImplementation(libs.bluetape4k.testcontainers)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.neo4j)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.hikaricp)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test.lib)
}
