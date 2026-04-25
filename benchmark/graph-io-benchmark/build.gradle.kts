plugins {
    id(Plugins.kotlinx_benchmark) version Plugins.Versions.kotlinx_benchmark
    kotlin("plugin.allopen") version Versions.kotlin
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
    implementation(project(":graph-tinkerpop"))
    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.bluetape4k_virtualthread_api)
    implementation(Libs.bluetape4k_virtualthread_jdk25)
    implementation(Libs.kotlinx_benchmark_runtime)

    testImplementation(Libs.bluetape4k_junit5)
}
