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
            reportFormat = "json"
        }
        register("graphDbSmall") {
            include(".*GraphDbComparisonBenchmark.*")
            param("sizeName", "small")
            warmups = 1
            iterations = 3
            iterationTime = 1
            iterationTimeUnit = "s"
            reportFormat = "json"
        }
        register("graphDbMedium") {
            include(".*GraphDbComparisonBenchmark.*")
            param("sizeName", "medium")
            warmups = 3
            iterations = 5
            iterationTime = 3
            iterationTimeUnit = "s"
            reportFormat = "json"
        }
        register("graphWriteIngestion10k") {
            include(".*GraphWriteIngestionBenchmark.*")
            param("backend", "tinkergraph", "neo4j", "memgraph")
            param("batchSize", 10_000)
            warmups = 1
            iterations = 3
            iterationTime = 1
            iterationTimeUnit = "s"
            reportFormat = "json"
        }
        register("graphDomainWorkload") {
            include(".*GraphDomainWorkloadBenchmark.*")
            param("backend", "tinkergraph", "neo4j", "memgraph")
            warmups = 2
            iterations = 4
            iterationTime = 2
            iterationTimeUnit = "s"
            reportFormat = "json"
        }
        register("apiModelProduction") {
            include(".*ApiModelBenchmark\\.(bfsConcurrentVirtualThreadLatency|bfsConcurrentCoroutineLatency|virtualThreadConcurrentCreationCost|coroutineConcurrentLaunchCost)")
            param("concurrency", 10, 100, 1000)
            warmups = 5
            iterations = 10
            iterationTime = 3
            iterationTimeUnit = "s"
            reportFormat = "json"
        }
    }
}

tasks.withType<org.gradle.jvm.tasks.Jar>()
    .matching { it.name == "mainBenchmarkJar" }
    .configureEach {
        dependsOn(tasks.named("classes"))
        from(sourceSets.main.get().output)
    }

dependencies {
    implementation(project(":bluetape4k-graph-core"))
    implementation(project(":bluetape4k-graph-age"))
    implementation(project(":bluetape4k-graph-falkordb"))
    implementation(project(":bluetape4k-graph-io-core"))
    implementation(project(":bluetape4k-graph-io-csv"))
    implementation(project(":bluetape4k-graph-io-graphml"))
    implementation(project(":bluetape4k-graph-io-jackson2"))
    implementation(project(":bluetape4k-graph-io-jackson3"))
    implementation(project(":bluetape4k-graph-memgraph"))
    implementation(project(":bluetape4k-graph-neo4j"))
    implementation(project(":bluetape4k-graph-okio"))
    implementation(project(":bluetape4k-graph-tinkerpop"))

    implementation(libs.kotlinx.benchmark.runtime)
    implementation(libs.bluetape4k.core)
    implementation(libs.bluetape4k.logging)
    implementation(libs.bluetape4k.virtualthread.api)
    implementation(libs.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core.lib)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.hikaricp)
    implementation(libs.jfalkordb)
    implementation(libs.neo4j.java.driver)
    implementation(libs.postgresql.driver)

    implementation(libs.bluetape4k.testcontainers)
    implementation(libs.testcontainers.core)
    implementation(libs.testcontainers.neo4j)
    implementation(libs.testcontainers.postgresql)

    testImplementation(libs.bluetape4k.junit5)
}
