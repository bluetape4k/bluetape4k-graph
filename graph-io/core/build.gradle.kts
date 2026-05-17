dependencies {
    api(project(":bluetape4k-graph-core"))
    api(libs.bluetape4k.core)
    api(libs.bluetape4k.io)
    api(libs.kotlinx.coroutines.core.lib)
    implementation(libs.bluetape4k.virtualthread.api)
    implementation(libs.bluetape4k.virtualthread.jdk21)
    implementation(libs.bluetape4k.coroutines)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test.lib)
    testImplementation(project(":bluetape4k-graph-tinkerpop"))
}
