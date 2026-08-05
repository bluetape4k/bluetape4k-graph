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
            // 결과는 stdout 으로 스트리밍 (wrapper script 가 파싱)
            reportFormat = "json"
        }
    }
}

dependencies {
    implementation(project(":bluetape4k-graph-core"))
    implementation(project(":bluetape4k-graph-neo4j"))

    implementation(bt4k.kotlinx.benchmark.runtime)
    implementation(bt4k.bluetape4k.core)
    implementation(bt4k.bluetape4k.logging)
    implementation(bt4k.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core.lib)

    implementation(bt4k.neo4j.driver6)
    runtimeOnly(bt4k.neo4j.bolt.connection.netty)
    runtimeOnly(bt4k.neo4j.bolt.connection.pooled)

    // Testcontainers 를 main 에서 사용 (벤치마크 라이프사이클이 JMH @Setup 에서 기동)
    implementation(bt4k.bluetape4k.testcontainers)
    implementation(libs.testcontainers.core)
    implementation(libs.testcontainers.neo4j)

    testImplementation(bt4k.bluetape4k.junit5)
}
