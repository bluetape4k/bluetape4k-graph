plugins {
    alias(libs.plugins.kotlinx.benchmark)
    kotlin("plugin.allopen")
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

benchmark {
    targets {
        register("main")
    }
    configurations {
        named("main") {
            warmups = 3
            iterations = 5
            iterationTime = 3
            iterationTimeUnit = "s"
        }
    }
}

dependencies {
    implementation(project(":graph-io-core"))
    implementation(project(":graph-io-csv"))
    implementation(project(":graph-io-jackson2"))
    implementation(project(":graph-io-jackson3"))
    implementation(project(":graph-io-graphml"))
    implementation(project(":graph-okio"))
    implementation(project(":graph-tinkerpop"))
    implementation(libs.bluetape4k.coroutines)
    implementation(libs.bluetape4k.virtualthread.api)
    implementation(libs.bluetape4k.virtualthread.jdk21)
    implementation(libs.kotlinx.benchmark.runtime)

    testImplementation(libs.bluetape4k.junit5)
}
