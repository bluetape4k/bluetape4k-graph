plugins {
    id("java-test-fixtures")
}

dependencies {
    api(project(":bluetape4k-graph-core"))

    api(libs.jfalkordb)
    api(libs.bluetape4k.coroutines)

    testFixturesApi(libs.bluetape4k.testcontainers)
    testFixturesApi(libs.testcontainers.core)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.bluetape4k.testcontainers)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.kotlinx.coroutines.test.lib)
}
