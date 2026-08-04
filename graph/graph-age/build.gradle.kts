dependencies {
    api(project(":bluetape4k-graph-core"))

api(bt4k.exposed.core)
api(libs.exposed.dao)
api(bt4k.exposed.jdbc)
api(bt4k.exposed.java.time)
    api(bt4k.postgresql)
    api(bt4k.caffeine.core)

    implementation(bt4k.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core.lib)
    testImplementation(libs.kotlinx.coroutines.test.lib)

    testImplementation(bt4k.bluetape4k.junit5)
    testImplementation(bt4k.bluetape4k.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(bt4k.hikaricp)
    testImplementation(bt4k.mockk)
}
