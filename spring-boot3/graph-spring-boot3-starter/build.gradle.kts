plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.serialization")
    alias(libs.plugins.spring.boot3) apply false
    id("io.spring.dependency-management")
}

dependencies {
    implementation(platform(libs.spring.boot3.dependencies))

    // graph-core는 api로 전이 노출 — GraphOperations 등 공개 API 타입이 전이 노출 필요
    api(project(":graph-core"))
    // 백엔드 구현 모듈(graph-neo4j 등)만 compileOnly — 사용자가 원하는 백엔드만 runtime에 추가.
    compileOnly(project(":graph-neo4j"))
    compileOnly(project(":graph-memgraph"))
    compileOnly(project(":graph-age"))
    compileOnly(project(":graph-tinkerpop"))
    compileOnly(project(":graph-falkordb"))

    // Spring Boot 3.x (위 BOM override 적용됨)
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("org.springframework.boot:spring-boot-starter")
    compileOnly("org.springframework.boot:spring-boot-actuator-autoconfigure")

    // Annotation processor
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:${libs.versions.spring.boot3.get()}")

    // Test
    testImplementation(project(":graph-tinkerpop"))
    testImplementation(project(":graph-neo4j"))
    testImplementation(project(":graph-memgraph"))
    testImplementation(project(":graph-age"))
    testImplementation(project(":graph-falkordb"))
    testImplementation(testFixtures(project(":graph-falkordb")))
    testImplementation(libs.bluetape4k.testcontainers)
    testImplementation(libs.testcontainers.neo4j)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.neo4j.java.driver)
    testRuntimeOnly(libs.postgresql.driver)
    testImplementation(libs.hikaricp)
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation(libs.kotlinx.coroutines.test.lib)
    testImplementation(libs.kotlinx.coroutines.reactor)
}
