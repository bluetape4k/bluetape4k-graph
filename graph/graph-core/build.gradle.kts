dependencies {
    api(libs.bluetape4k.core)
    implementation(libs.bluetape4k.virtualthread.api)
    implementation(libs.bluetape4k.virtualthread.jdk25)
    implementation(libs.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core.lib)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.bluetape4k.testcontainers)
    testImplementation(libs.kotlinx.coroutines.test.lib)
    testImplementation(project(":graph-tinkerpop"))
}
