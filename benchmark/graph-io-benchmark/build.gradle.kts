plugins {
    alias(bt4k.plugins.kotlinx.benchmark)
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
        register("smoke") {
            include(".*(BulkGraphIoBenchmark|OkioGraphIoBenchmark)\\.(csvSyncRoundTrip|jackson3OkioRoundTrip|graphMlOkioRoundTrip)")
            param("sizeName", "smoke")
            warmups = 1
            iterations = 1
            iterationTime = 200
            iterationTimeUnit = "ms"
            reportFormat = "json"
        }
    }
}

dependencies {
    implementation(project(":bluetape4k-graph-io-core"))
    implementation(project(":bluetape4k-graph-io-csv"))
    implementation(project(":bluetape4k-graph-io-jackson2"))
    implementation(project(":bluetape4k-graph-io-jackson3"))
    implementation(project(":bluetape4k-graph-io-graphml"))
    implementation(project(":bluetape4k-graph-okio"))
    implementation(project(":bluetape4k-graph-tinkerpop"))
    implementation(bt4k.bluetape4k.coroutines)
    implementation(bt4k.bluetape4k.virtualthread.api)
    implementation(bt4k.bluetape4k.virtualthread.jdk21)
    implementation(bt4k.kotlinx.benchmark.runtime)

    testImplementation(bt4k.bluetape4k.junit5)
}
