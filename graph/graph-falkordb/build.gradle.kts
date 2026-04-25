plugins {
    id("java-test-fixtures")
}

dependencies {
    api(project(":graph-core"))

    api(Libs.jfalkordb)
    api(Libs.bluetape4k_coroutines)

    testFixturesApi(Libs.bluetape4k_testcontainers)
    testFixturesApi(Libs.testcontainers)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers)
    testImplementation(Libs.kotlinx_coroutines_test)
}
