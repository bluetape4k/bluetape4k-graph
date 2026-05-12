plugins {
    application
}

application {
    mainClass.set("io.bluetape4k.graph.examples.ktor.KtorGraphAppMain")
}

configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

dependencyManagement {
    imports {
        mavenBom(libs.ktor.bom.get().toString())
    }
}

dependencies {
    implementation(project(":graph-ktor"))
    implementation(project(":graph-tinkerpop"))

    implementation(libs.bluetape4k.coroutines)
    implementation(libs.bluetape4k.logging)
    implementation(libs.kotlinx.coroutines.core.lib)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)

    runtimeOnly(libs.logback.classic)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlinx.coroutines.test.lib)
}
