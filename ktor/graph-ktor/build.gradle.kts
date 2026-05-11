configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

dependencyManagement {
    imports {
        mavenBom(libs.ktor.bom.get().toString())
    }
}

dependencies {
    api(project(":graph-core"))

    api(libs.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core.lib)

    // Ktor 3.x — application/plugin DSL
    compileOnly(libs.ktor.server.core)

    // Backend helpers are compile-only; applications choose the concrete runtime backend.
    compileOnly(project(":graph-tinkerpop"))
    compileOnly(project(":graph-neo4j"))
    compileOnly(project(":graph-memgraph"))
    compileOnly(project(":graph-age"))
    compileOnly(project(":graph-falkordb"))

    // Logging
    implementation(libs.bluetape4k.logging)

    // Testing
    testImplementation(libs.ktor.server.core)
    testImplementation(libs.ktor.server.cio)
    testImplementation(libs.ktor.server.test.host)

    testImplementation(project(":graph-tinkerpop"))

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test.lib)
}
