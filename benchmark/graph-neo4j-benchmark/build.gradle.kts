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
    implementation(project(":graph-core"))
    implementation(project(":graph-neo4j"))

    implementation(libs.kotlinx.benchmark.runtime)
    implementation(libs.bluetape4k.core)
    implementation(libs.bluetape4k.logging)
    implementation(libs.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core.lib)

    implementation(libs.neo4j.java.driver)
    runtimeOnly(libs.neo4j.bolt.connection.netty)
    runtimeOnly(libs.neo4j.bolt.connection.pooled)

    // Testcontainers 를 main 에서 사용 (벤치마크 라이프사이클이 JMH @Setup 에서 기동)
    implementation(libs.bluetape4k.testcontainers)
    implementation(libs.testcontainers.core)
    implementation(libs.testcontainers.neo4j)

    testImplementation(libs.bluetape4k.junit5)
}
