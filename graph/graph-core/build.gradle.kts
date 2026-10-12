plugins {
    id("java-test-fixtures")
}

dependencies {
    api(bt4k.bluetape4k.core)
    implementation(bt4k.bluetape4k.virtualthread.api)
    implementation(bt4k.bluetape4k.virtualthread.jdk25)
    api(bt4k.bluetape4k.coroutines)
    api(libs.kotlinx.coroutines.core.lib)

    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(bt4k.bluetape4k.testcontainers)
    testImplementation(libs.kotlinx.coroutines.test.lib)
    testImplementation(project(":bluetape4k-graph-tinkerpop"))

    // Serialization
    testImplementation(bt4k.bluetape4k.io)
    testImplementation(bt4k.fory.kotlin)
    testImplementation(bt4k.kryo)

    testFixturesApi(bt4k.bluetape4k.junit5)
}
