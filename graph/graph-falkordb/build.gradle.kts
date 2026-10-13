dependencies {
    api(project(":bluetape4k-graph-core"))

    api(bt4k.jfalkordb)

    // Coroutines
    api(bt4k.bluetape4k.coroutines)
    api(libs.kotlinx.coroutines.core.lib)
    testImplementation(libs.kotlinx.coroutines.test.lib)

    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(bt4k.bluetape4k.testcontainers)
    testImplementation(libs.testcontainers.core)

    testImplementation(testFixtures(project(":bluetape4k-graph-core")))
}
