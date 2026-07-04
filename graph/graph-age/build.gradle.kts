dependencies {
    api(project(":bluetape4k-graph-core"))

api(bt4k.exposed.core)
api(libs.exposed.dao)
api(bt4k.exposed.jdbc)
api(bt4k.exposed.java.time)
    api(libs.postgresql.driver)
    api(libs.caffeine.core)

    implementation(libs.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core.lib)
    testImplementation(libs.kotlinx.coroutines.test.lib)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.bluetape4k.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.hikaricp)
    testImplementation(libs.mockk)
}
