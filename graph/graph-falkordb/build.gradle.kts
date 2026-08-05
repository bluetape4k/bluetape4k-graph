plugins {
    id("java-test-fixtures")
}

dependencies {
    api(project(":bluetape4k-graph-core"))

    api(bt4k.jfalkordb)
    api(bt4k.bluetape4k.coroutines)

    testFixturesApi(bt4k.bluetape4k.testcontainers)
    testFixturesApi(libs.testcontainers.core)

    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(bt4k.bluetape4k.testcontainers)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.kotlinx.coroutines.test.lib)
}
