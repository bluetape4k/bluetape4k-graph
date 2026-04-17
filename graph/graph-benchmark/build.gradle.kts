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
        }
    }
}

dependencies {
    implementation(project(":graph-core"))
    implementation(project(":graph-tinkerpop"))

    implementation(Libs.kotlinx_benchmark_runtime)
    implementation(Libs.bluetape4k_core)
    implementation(Libs.bluetape4k_virtualthread_api)
    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.kotlinx_coroutines_core)

    testImplementation(Libs.bluetape4k_junit5)
}
