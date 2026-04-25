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
            // 결과는 stdout 으로 스트리밍 (wrapper script 가 파싱)
            reportFormat = "json"
        }
    }
}

dependencies {
    implementation(project(":graph-core"))
    implementation(project(":graph-age"))

    implementation(Libs.kotlinx_benchmark_runtime)
    implementation(Libs.bluetape4k_core)
    implementation(Libs.bluetape4k_logging)
    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.kotlinx_coroutines_core)

    implementation(Libs.exposed_core)
    implementation(Libs.exposed_jdbc)
    implementation(Libs.postgresql_driver)
    implementation(Libs.hikaricp)

    // Testcontainers 를 main 에서 사용 (벤치마크 라이프사이클이 JMH @Setup 에서 기동)
    implementation(Libs.bluetape4k_testcontainers)
    implementation(Libs.testcontainers)
    implementation(Libs.testcontainers_postgresql)

    testImplementation(Libs.bluetape4k_junit5)
}
