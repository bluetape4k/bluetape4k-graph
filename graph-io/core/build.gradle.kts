dependencies {
    api(project(":graph-core"))
    api(Libs.bluetape4k_core)
    api(Libs.bluetape4k_io)
    api(Libs.kotlinx_coroutines_core)
    implementation(Libs.bluetape4k_virtualthread_api)
    implementation(Libs.bluetape4k_virtualthread_jdk25)
    implementation(Libs.bluetape4k_coroutines)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.kotlinx_coroutines_test)
    testImplementation(project(":graph-tinkerpop"))
}
