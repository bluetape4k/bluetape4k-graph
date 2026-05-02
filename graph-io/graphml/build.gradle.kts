dependencies {
    api(project(":graph-io-core"))
    implementation(libs.bluetape4k.coroutines)
    implementation(libs.bluetape4k.virtualthread.api)
    implementation(libs.bluetape4k.virtualthread.jdk25)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test.lib)
    testImplementation(project(":graph-tinkerpop"))
}
