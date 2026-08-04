dependencies {
    api(project(":bluetape4k-graph-core"))

    api(bt4k.tinkerpop.gremlin.core)
    api(bt4k.tinkergraph.gremlin)

    implementation(bt4k.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core.lib)
    testImplementation(libs.kotlinx.coroutines.test.lib)

    testImplementation(bt4k.bluetape4k.junit5)
}
