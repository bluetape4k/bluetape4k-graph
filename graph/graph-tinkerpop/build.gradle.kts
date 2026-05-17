dependencies {
    api(project(":bluetape4k-graph-core"))

    api(libs.tinkerpop.gremlin.core)
    api(libs.tinkergraph.gremlin)

    implementation(libs.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core.lib)
    testImplementation(libs.kotlinx.coroutines.test.lib)

    testImplementation(libs.bluetape4k.junit5)
}
