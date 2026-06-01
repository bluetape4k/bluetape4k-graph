dependencies {
    implementation(project(":bluetape4k-graph-core"))
    implementation(project(":bluetape4k-graph-tinkerpop"))
    implementation(project(":bluetape4k-graph-io-csv"))

    implementation(libs.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core.lib)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test.lib)
}
