dependencies {
    api(project(":bluetape4k-graph-io-core"))
    api(libs.bluetape4k.csv)
    implementation(libs.bluetape4k.coroutines)
    implementation(libs.bluetape4k.virtualthread.api)
    implementation(libs.bluetape4k.virtualthread.jdk21)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test.lib)
    testImplementation(project(":bluetape4k-graph-tinkerpop"))
}
