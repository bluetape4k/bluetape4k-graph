dependencies {
    api(project(":graph-io-core"))
    api(libs.bluetape4k.jackson3)
    api(libs.jackson3.module.kotlin)
    api(libs.jackson3.module.blackbird)

    implementation(libs.bluetape4k.coroutines)
    implementation(libs.bluetape4k.virtualthread.api)
    implementation(libs.bluetape4k.virtualthread.jdk25)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test.lib)
    testImplementation(project(":graph-tinkerpop"))
    testImplementation(project(":graph-io-jackson2"))
}
