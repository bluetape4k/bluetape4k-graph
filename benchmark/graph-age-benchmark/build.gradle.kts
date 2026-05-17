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
            // 결과는 stdout 으로 스트리밍 (wrapper script 가 파싱)
            reportFormat = "json"
        }
    }
}

dependencies {
    implementation(project(":bluetape4k-graph-core"))
    implementation(project(":bluetape4k-graph-age"))

    implementation(libs.kotlinx.benchmark.runtime)
    implementation(libs.bluetape4k.core)
    implementation(libs.bluetape4k.logging)
    implementation(libs.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core.lib)

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.postgresql.driver)
    implementation(libs.hikaricp)

    // Testcontainers 를 main 에서 사용 (벤치마크 라이프사이클이 JMH @Setup 에서 기동)
    implementation(libs.bluetape4k.testcontainers)
    implementation(libs.testcontainers.core)
    implementation(libs.testcontainers.postgresql)

    testImplementation(libs.bluetape4k.junit5)
}
