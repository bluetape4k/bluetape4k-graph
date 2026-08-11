dependencies {
    api(project(":bluetape4k-graph-io-core"))
    api("io.micrometer:micrometer-core")

    testImplementation(bt4k.bluetape4k.junit5)
}
