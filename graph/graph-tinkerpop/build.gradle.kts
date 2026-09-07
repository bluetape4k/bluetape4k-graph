dependencies {
    api(project(":bluetape4k-graph-core"))

    api(bt4k.tinkerpop.gremlin.core)
    api(bt4k.tinkergraph.gremlin)

    // TinkerPop 3.8.2 artifact가 게시될 때까지 취약한 2.9.0 transitive version을 대체합니다.
    implementation(libs.commons.configuration2)

    implementation(bt4k.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core.lib)
    testImplementation(libs.kotlinx.coroutines.test.lib)

    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(testFixtures(project(":bluetape4k-graph-core")))
}
