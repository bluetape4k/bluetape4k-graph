dependencies {
    api(bt4k.bluetape4k.core)
    implementation(bt4k.bluetape4k.virtualthread.api)
    implementation(bt4k.bluetape4k.virtualthread.jdk21)
    implementation(bt4k.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core.lib)

    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(bt4k.bluetape4k.testcontainers)
    testImplementation(libs.kotlinx.coroutines.test.lib)
    testImplementation(project(":bluetape4k-graph-tinkerpop"))
}
