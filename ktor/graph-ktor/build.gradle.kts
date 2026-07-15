configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

dependencyManagement {
    imports {
        mavenBom(bt4k.ktor.bom.get().toString())
    }
}

dependencies {
    api(project(":bluetape4k-graph-core"))

    api(bt4k.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core.lib)

    // Ktor 3.x — application/plugin DSL
    compileOnly(libs.ktor.server.core)

    // Backend helpers are compile-only; applications choose the concrete runtime backend.
    compileOnly(project(":bluetape4k-graph-tinkerpop"))
    compileOnly(project(":bluetape4k-graph-neo4j"))
    compileOnly(project(":bluetape4k-graph-memgraph"))
    compileOnly(project(":bluetape4k-graph-age"))
    compileOnly(project(":bluetape4k-graph-falkordb"))
    compileOnly(bt4k.hikaricp)

    // Logging
    implementation(bt4k.bluetape4k.logging)

    // Testing
    testImplementation(libs.ktor.server.core)
    testImplementation(libs.ktor.server.cio)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(bt4k.bluetape4k.ktor.testing)

    testImplementation(project(":bluetape4k-graph-tinkerpop"))
    testImplementation(project(":bluetape4k-graph-neo4j"))
    testImplementation(project(":bluetape4k-graph-memgraph"))
    testImplementation(project(":bluetape4k-graph-age"))
    testImplementation(project(":bluetape4k-graph-falkordb"))
    testImplementation(testFixtures(project(":bluetape4k-graph-falkordb")))

    testImplementation(bt4k.bluetape4k.testcontainers)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.neo4j)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(bt4k.hikaricp)

    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test.lib)
}
