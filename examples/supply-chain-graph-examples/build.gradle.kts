dependencies {
    implementation(project(":bluetape4k-graph-core"))
    implementation(project(":bluetape4k-graph-tinkerpop"))
    implementation(project(":bluetape4k-graph-io-csv"))

    implementation(bt4k.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core.lib)

    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(libs.kotlinx.coroutines.test.lib)
}
