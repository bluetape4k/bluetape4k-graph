plugins {
    id("java-test-fixtures")
}

dependencies {
    api(project(":bluetape4k-graph-core"))
    api(bt4k.bluetape4k.core)
    api(bt4k.bluetape4k.io)
    api(libs.kotlinx.coroutines.core.lib)
    implementation(bt4k.bluetape4k.virtualthread.api)
    implementation(bt4k.bluetape4k.virtualthread.jdk25)
    implementation(bt4k.bluetape4k.coroutines)

    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test.lib)
    testImplementation(project(":bluetape4k-graph-tinkerpop"))

    testFixturesApi(bt4k.bluetape4k.junit5)
}
