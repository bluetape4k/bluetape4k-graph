dependencies {
    api(libs.bluetape4k.core)
    implementation(libs.bluetape4k.virtualthread.api)
    implementation(libs.bluetape4k.virtualthread.jdk21)
    implementation(libs.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core.lib)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.bluetape4k.testcontainers)
    testImplementation(libs.kotlinx.coroutines.test.lib)
    testImplementation(project(":bluetape4k-graph-tinkerpop"))
}
