dependencies {
    api(project(":bluetape4k-graph-io-core"))
    api(bt4k.bluetape4k.csv)
    implementation(bt4k.bluetape4k.coroutines)
    implementation(bt4k.bluetape4k.virtualthread.api)
    implementation(bt4k.bluetape4k.virtualthread.jdk21)

    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test.lib)
    testImplementation(project(":bluetape4k-graph-tinkerpop"))
}
