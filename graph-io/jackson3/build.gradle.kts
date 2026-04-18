import org.jetbrains.dokka.model.doc.Li

dependencies {
    api(project(":graph-io-core"))
    api(Libs.bluetape4k_jackson3)
    api(Libs.jackson3_module_kotlin)
    api(Libs.jackson3_module_blackbird)

    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.bluetape4k_virtualthread_api)
    implementation(Libs.bluetape4k_virtualthread_jdk25)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.kotlinx_coroutines_test)
    testImplementation(project(":graph-tinkerpop"))
}
