dependencies {
    api(project(":bluetape4k-graph-io-core"))
    api(bt4k.bluetape4k.jackson3)
    api(libs.jackson3.module.kotlin)
    api(libs.jackson3.module.blackbird)

    implementation(bt4k.bluetape4k.coroutines)
    implementation(bt4k.bluetape4k.virtualthread.api)
    implementation(bt4k.bluetape4k.virtualthread.jdk21)

    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test.lib)
    testImplementation(project(":bluetape4k-graph-tinkerpop"))
    testImplementation(project(":bluetape4k-graph-io-jackson2"))
}
