dependencies {
    api(project(":graph-io-core"))
    api(libs.bluetape4k.jackson2)
    api(libs.jackson.module.kotlin)
    api(libs.jackson.module.blackbird)

    implementation(libs.bluetape4k.coroutines)
    implementation(libs.bluetape4k.virtualthread.api)
    implementation(libs.bluetape4k.virtualthread.jdk25)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test.lib)
    testImplementation(project(":graph-tinkerpop"))
}
