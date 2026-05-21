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
    }
}

tasks.withType<Jar>()
    .matching { it.name == "mainBenchmarkJar" }
    .configureEach {
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
