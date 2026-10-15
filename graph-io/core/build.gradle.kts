plugins {
    id("java-test-fixtures")
}

configurations {
    testImplementation.get().extendsFrom(compileOnly.get(), runtimeOnly.get())
}

dependencies {
    api(project(":bluetape4k-graph-core"))
    api(bt4k.bluetape4k.core)
    api(bt4k.bluetape4k.io)
    api(bt4k.bluetape4k.virtualthread.api)
    compileOnly(bt4k.bluetape4k.virtualthread.jdk25)

    // Serialization
    testImplementation(bt4k.fory.kotlin)

    // Coroutines
    api(bt4k.bluetape4k.coroutines)
    api(libs.kotlinx.coroutines.core.lib)
    testImplementation(libs.kotlinx.coroutines.test.lib)

    testImplementation(bt4k.bluetape4k.junit5)

    testImplementation(project(":bluetape4k-graph-tinkerpop"))

    testFixturesApi(bt4k.bluetape4k.junit5)
}
