plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    kotlin("plugin.serialization")
    id(Plugins.spring_boot) version Plugins.Versions.spring_boot4 apply false
    id("io.spring.dependency-management")
}

dependencies {
    implementation(platform(Libs.spring_boot4_dependencies))

    // graph-core는 api로 전이 노출 — GraphOperations 등 공개 API 타입이 전이 노출 필요
    api(project(":graph-core"))
    // 백엔드 구현 모듈(graph-neo4j 등)만 compileOnly — 사용자가 원하는 백엔드만 runtime에 추가.
    compileOnly(project(":graph-neo4j"))
    compileOnly(project(":graph-memgraph"))
    compileOnly(project(":graph-age"))
    compileOnly(project(":graph-tinkerpop"))

    // Spring Boot 4.x (위 BOM override 적용됨)
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("org.springframework.boot:spring-boot-starter")
    // Spring Boot 4에서 JDBC/DataSource autoconfigure 모듈 분리
    compileOnly("org.springframework.boot:spring-boot-jdbc")
    // Spring Boot 4에서 HealthIndicator 모듈 분리
    compileOnly("org.springframework.boot:spring-boot-health")
    compileOnly("org.springframework.boot:spring-boot-actuator-autoconfigure")

    // Annotation processor
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:${Versions.spring_boot4}")

    // Test
    testImplementation(project(":graph-tinkerpop"))
    testImplementation(project(":graph-neo4j"))
    testImplementation(project(":graph-memgraph"))
    testImplementation(project(":graph-age"))
    testImplementation(project(":graph-servers"))
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    // Spring Boot 4 분리 모듈: TestRestTemplate, WebTestClient
    testImplementation("org.springframework.boot:spring-boot-restclient")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.springframework.boot:spring-boot-webtestclient")
    testImplementation(Libs.kotlinx_coroutines_test)
    testImplementation(Libs.kotlinx_coroutines_reactor)
}
