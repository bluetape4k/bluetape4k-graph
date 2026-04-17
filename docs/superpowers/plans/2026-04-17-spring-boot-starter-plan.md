# Spring Boot Starter AutoConfiguration 구현 플랜 (v2 — 공식 문서 기반 재작성)

> **For agentic workers:** 각 Task를 순서대로 실행한다. 각 단계는 체크박스(`- [ ]`)로 진행 상태를 추적한다. 검증 명령을 반드시 실행하고 PASS를 확인 후 다음 Task로 진행한다.

**Goal:** `graph-spring-boot3-starter` / `graph-spring-boot4-starter` AutoConfiguration 모듈 구현 — `application.yml`만으로 `GraphOperations`, `GraphSuspendOperations`, `GraphVirtualThreadOperations` 빈을 자동 등록.

**Tech Stack:** Kotlin 2.3, Java 25, Spring Boot 3.5 / 4.0, AutoConfiguration, TinkerGraph (in-memory), Testcontainers (Neo4j, Memgraph, PostgreSQL+AGE).

---

## 설계 원칙 (절대 규칙)

1. **`@Primary` 금지** — 빈 충돌 회피는 `@ConditionalOnMissingBean(GraphOperations::class)` 타입 스코프로만 처리.
2. **`enabled` 플래그 없음** — 백엔드 선택은 **오직** `bluetape4k.graph.backend` 속성 하나로 한다.
3. **alias/wrapper 빈 없음** — `ApplicationContext.getBean(...)` 또는 `OPS_BEAN_NAMES` 맵 같은 간접 호출 계층을 만들지 않는다.
4. **`@AutoConfiguration(after = [...])` 명시** — DataSource / Neo4j 자동 구성 이후 로드해야 하는 백엔드는 순서를 명시한다.
5. **Actuator 통합은 nested class** — `@Configuration @ConditionalOnClass(name = ["org.springframework.boot.actuate.health.HealthIndicator"])` 중첩 클래스로 HealthIndicator 빈을 등록한다. 외부 클래스에는 Actuator import 를 두지 않고, nested class 내부에서 FQN 으로만 참조하여 `NoClassDefFoundError` 를 방지한다.
6. **Driver 재사용** — 기존 `Driver` 빈이 있으면 재사용(`@ConditionalOnMissingBean(Driver::class)`), `@Qualifier` 없이 타입 기반 주입.
7. **AGE 초기화** — `InitializingBean` 에서 `ops: AgeGraphOperations` 타입으로 직접 받아 `ops.createGraph(props.graphName)` 호출 (캐스트 불필요).
8. **TestApp** — 테스트용 애플리케이션은 `@SpringBootConfiguration + @EnableAutoConfiguration + @Import(GraphAutoConfiguration::class)` 로 구성한다.

---

## 파일 구조

```
spring-boot3/graph-spring-boot3-starter/
  build.gradle.kts
  src/main/kotlin/io/bluetape4k/graph/spring/boot3/
    properties/
      GraphProperties.kt
      Neo4jGraphProperties.kt
      MemgraphGraphProperties.kt
      AgeGraphProperties.kt
      TinkerGraphGraphProperties.kt
    autoconfigure/
      GraphAutoConfiguration.kt
      GraphNeo4jAutoConfiguration.kt
      GraphMemgraphAutoConfiguration.kt
      GraphAgeAutoConfiguration.kt
      GraphTinkerGraphAutoConfiguration.kt
  src/main/resources/
    META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
    META-INF/additional-spring-configuration-metadata.json
  src/test/kotlin/io/bluetape4k/graph/spring/boot3/
    autoconfigure/
      GraphTinkerGraphAutoConfigurationTest.kt
      GraphNeo4jAutoConfigurationTest.kt
      GraphMemgraphAutoConfigurationTest.kt
      GraphAgeAutoConfigurationTest.kt
    webmvc/
      TinkerGraphWebMvcTest.kt
      Neo4jWebMvcTest.kt
    webflux/
      TinkerGraphWebFluxTest.kt
      Neo4jWebFluxTest.kt
  src/test/resources/
    application-tinkergraph.yml
    application-neo4j.yml
    application-memgraph.yml
    application-age.yml

spring-boot4/graph-spring-boot4-starter/   # boot3 구조를 복제 (패키지: io.bluetape4k.graph.spring.boot4)
```

---

## Task 목록 (의존 순서)

| # | Task | complexity |
|---|------|-----------|
| 1 | `settings.gradle.kts` + 디렉토리 골격 | low |
| 2 | `build.gradle.kts` (boot3) | low |
| 3 | Properties 클래스 5종 | low |
| 4 | `GraphTinkerGraphAutoConfiguration` (가장 단순, 먼저 구현) | low |
| 5 | `GraphNeo4jAutoConfiguration` + `GraphMemgraphAutoConfiguration` | medium |
| 6 | `GraphAgeAutoConfiguration` | medium |
| 7 | 루트 `GraphAutoConfiguration` + `AutoConfiguration.imports` | low |
| 8 | `ApplicationContextRunner` 단위 테스트 4종 | medium |
| 9 | Spring WebMvc (Virtual Threads) 통합 테스트 | high |
| 10 | Spring WebFlux (Coroutine) 통합 테스트 | high |
| 11 | `graph-spring-boot4-starter` — boot3 코드 복사 + 패키지 변경 | low |
| 12 | README + testlog 기록 | low |

---

## Task 1 · settings.gradle.kts + 디렉토리 골격 _(complexity: low)_

- [ ] `settings.gradle.kts` 에 프로젝트 포함 (기존 `includeModules` 규칙 준수):

```kotlin
includeModules("spring-boot3", false, false)
includeModules("spring-boot4", false, false)
```

- [ ] 디렉토리 생성 (Bash `mkdir -p` 허용):

```bash
mkdir -p spring-boot3/graph-spring-boot3-starter/src/main/kotlin/io/bluetape4k/graph/spring/boot3/{properties,autoconfigure}
mkdir -p spring-boot3/graph-spring-boot3-starter/src/main/resources/META-INF/spring
mkdir -p spring-boot3/graph-spring-boot3-starter/src/test/kotlin/io/bluetape4k/graph/spring/boot3/{autoconfigure,webmvc,webflux}
mkdir -p spring-boot3/graph-spring-boot3-starter/src/test/resources
```

- [ ] **검증**: `./gradlew :graph-spring-boot3-starter:help` → 빈 프로젝트라도 명령이 성공해야 함.

---

## Task 2 · build.gradle.kts (boot3) _(complexity: low)_

- [ ] `spring-boot3/graph-spring-boot3-starter/build.gradle.kts` 작성:

```kotlin
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot") apply false
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        // 루트 프로젝트가 Boot 4 BOM을 글로벌 import 하므로, 이 모듈은 Boot 3 BOM을 명시적으로 override
        mavenBom(Libs.spring_boot3_dependencies)
    }
}

dependencies {
    // graph-core는 api로 전이 노출 — GraphOperations 등 공개 타입이 소비 모듈에서도 사용 가능.
    // 백엔드 구현 모듈(graph-neo4j 등)만 compileOnly — 사용자가 원하는 백엔드만 runtime에 추가.
    api(project(":graph-core"))           // GraphOperations 등 공개 API 타입이 전이 노출 필요
    compileOnly(project(":graph-neo4j"))
    compileOnly(project(":graph-memgraph"))
    compileOnly(project(":graph-age"))
    compileOnly(project(":graph-tinkerpop"))

    // Spring Boot 3.5 (위 BOM override 적용됨)
    api("org.springframework.boot:spring-boot-autoconfigure")
    api("org.springframework.boot:spring-boot-starter")
    compileOnly("org.springframework.boot:spring-boot-actuator-autoconfigure")

    // 공통 유틸
    api(Libs.bluetape4k_core)
    api(Libs.bluetape4k_logging)

    // @ConfigurationProperties 메타데이터 생성. Kotlin 클래스는 kapt/KSP가 필요하나 이 프로젝트는
    // additional-spring-configuration-metadata.json 으로 수동 제공하므로 annotationProcessor만 사용.
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // --- TEST ---
    testImplementation(project(":graph-core"))
    testImplementation(project(":graph-neo4j"))
    testImplementation(project(":graph-memgraph"))
    testImplementation(project(":graph-age"))
    testImplementation(project(":graph-tinkerpop"))
    testImplementation(project(":graph-servers"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux")
    testImplementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation(Libs.kluent)
    testImplementation(Libs.mockk)
    testImplementation(Libs.kotest_assertions_core)
}
```

- [ ] **검증 (BOM override)**: `./gradlew :graph-spring-boot3-starter:dependencyInsight --dependency spring-boot-autoconfigure --configuration compileClasspath` → 버전이 `3.5.x`여야 한다 (`4.x` 가 나오면 BOM override 실패).
- [ ] **검증 (api 전이 노출)**: `./gradlew :graph-spring-boot3-starter:dependencies --configuration runtimeClasspath` → `graph-core` 가 포함되어야 한다 (`api` 의존성이므로 전이 노출이 정상).

---

## Task 3 · Properties 클래스 5종 _(complexity: low)_

### `GraphProperties.kt`

```kotlin
package io.bluetape4k.graph.spring.boot3.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "bluetape4k.graph")
data class GraphProperties(
    /** 활성 백엔드: `tinkergraph` | `neo4j` | `memgraph` | `age` */
    var backend: String? = null,
)
```

### `TinkerGraphGraphProperties.kt`

```kotlin
package io.bluetape4k.graph.spring.boot3.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "bluetape4k.graph.tinkergraph")
data class TinkerGraphGraphProperties(
    var registerSuspend: Boolean = true,
    var registerVirtualThread: Boolean = true,
)
```

### `Neo4jGraphProperties.kt`

```kotlin
package io.bluetape4k.graph.spring.boot3.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "bluetape4k.graph.neo4j")
data class Neo4jGraphProperties(
    var uri: String = "bolt://localhost:7687",
    var username: String = "neo4j",
    var password: String = "",
    var database: String = "neo4j",
    var registerSuspend: Boolean = true,
    var registerVirtualThread: Boolean = true,
)
```

### `MemgraphGraphProperties.kt`

```kotlin
package io.bluetape4k.graph.spring.boot3.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "bluetape4k.graph.memgraph")
data class MemgraphGraphProperties(
    var uri: String = "bolt://localhost:7687",
    var username: String = "",
    var password: String = "",
    var database: String = "memgraph",
    var registerSuspend: Boolean = true,
    var registerVirtualThread: Boolean = true,
)
```

### `AgeGraphProperties.kt`

```kotlin
package io.bluetape4k.graph.spring.boot3.properties

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "bluetape4k.graph.age")
data class AgeGraphProperties(
    var graphName: String = "bluetape4k_graph",
    var autoCreateGraph: Boolean = true,
    var registerSuspend: Boolean = true,
    var registerVirtualThread: Boolean = true,
)
```

- [ ] **검증**: `./gradlew :graph-spring-boot3-starter:compileKotlin` → 에러 없음.

---

## Task 4 · GraphTinkerGraphAutoConfiguration _(complexity: low, 먼저 구현)_

- [ ] `autoconfigure/GraphTinkerGraphAutoConfiguration.kt`:

```kotlin
package io.bluetape4k.graph.spring.boot3.autoconfigure

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.graph.vt.asVirtualThread
import io.bluetape4k.graph.spring.boot3.properties.TinkerGraphGraphProperties
import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import io.bluetape4k.logging.KLogging
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// Actuator 클래스(Health, HealthIndicator)를 외부 클래스에 import하지 않아야
// Actuator 미사용 앱에서 NoClassDefFoundError가 발생하지 않는다.
// HealthConfig nested class 안에서만 string-based 조건으로 참조한다.
@AutoConfiguration
@ConditionalOnClass(TinkerGraphOperations::class)
@ConditionalOnProperty(
    prefix = "bluetape4k.graph",
    name = ["backend"],
    havingValue = "tinkergraph",
    matchIfMissing = true,  // backend 미설정 시 TinkerGraph가 기본 활성화
)
@EnableConfigurationProperties(TinkerGraphGraphProperties::class)
class GraphTinkerGraphAutoConfiguration {

    companion object : KLogging()

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(GraphOperations::class)
    fun graphOperations(): GraphOperations {
        log.info { "Registering TinkerGraphOperations (in-memory backend)" }
        return TinkerGraphOperations()
    }

    @Bean
    @ConditionalOnMissingBean(GraphSuspendOperations::class)
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.tinkergraph",
        name = ["register-suspend"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun graphSuspendOperations(ops: GraphOperations): GraphSuspendOperations =
        TinkerGraphSuspendOperations(ops as TinkerGraphOperations)

    @Bean
    @ConditionalOnMissingBean(GraphVirtualThreadOperations::class)
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.tinkergraph",
        name = ["register-virtual-thread"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun graphVirtualThreadOperations(ops: GraphOperations): GraphVirtualThreadOperations =
        ops.asVirtualThread()

    // Actuator 클래스를 파일 import 없이 FQN 으로만 참조 → 외부 클래스 바이트코드에 Actuator 참조 없음
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["org.springframework.boot.actuate.health.HealthIndicator"])
    class HealthConfig {
        @Bean
        @ConditionalOnMissingBean
        fun tinkerGraphHealthIndicator(): org.springframework.boot.actuate.health.HealthIndicator =
            org.springframework.boot.actuate.health.HealthIndicator {
                org.springframework.boot.actuate.health.Health.up()
                    .withDetail("backend", "tinkergraph").build()
            }
    }
}
```

- [ ] **검증**: `./gradlew :graph-spring-boot3-starter:compileKotlin` → 에러 없음.

---

## Task 5 · GraphNeo4j / GraphMemgraphAutoConfiguration _(complexity: medium)_

### `GraphNeo4jAutoConfiguration.kt`

```kotlin
package io.bluetape4k.graph.spring.boot3.autoconfigure

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.graph.vt.asVirtualThread
import io.bluetape4k.graph.neo4j.Neo4jGraphOperations
import io.bluetape4k.graph.neo4j.Neo4jGraphSuspendOperations
import io.bluetape4k.graph.spring.boot3.properties.Neo4jGraphProperties
import io.bluetape4k.logging.KLogging
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@AutoConfiguration
@ConditionalOnClass(Driver::class, Neo4jGraphOperations::class)
@ConditionalOnProperty(prefix = "bluetape4k.graph", name = ["backend"], havingValue = "neo4j")
@EnableConfigurationProperties(Neo4jGraphProperties::class)
class GraphNeo4jAutoConfiguration {

    companion object : KLogging()

    @Bean(name = ["neo4jDriver"], destroyMethod = "close")
    @ConditionalOnMissingBean(Driver::class)
    fun neo4jDriver(props: Neo4jGraphProperties): Driver {
        val auth = if (props.password.isBlank()) AuthTokens.none()
                   else AuthTokens.basic(props.username, props.password)
        log.info { "Creating Neo4j Driver: uri=${props.uri}, database=${props.database}" }
        return GraphDatabase.driver(props.uri, auth)
    }

    @Bean
    @ConditionalOnMissingBean(GraphOperations::class)
    fun graphOperations(driver: Driver, props: Neo4jGraphProperties): GraphOperations =
        Neo4jGraphOperations(driver, props.database)

    @Bean
    @ConditionalOnMissingBean(GraphSuspendOperations::class)
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.neo4j",
        name = ["register-suspend"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun graphSuspendOperations(driver: Driver, props: Neo4jGraphProperties): GraphSuspendOperations =
        Neo4jGraphSuspendOperations(driver, props.database)

    @Bean
    @ConditionalOnMissingBean(GraphVirtualThreadOperations::class)
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.neo4j",
        name = ["register-virtual-thread"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun graphVirtualThreadOperations(ops: GraphOperations): GraphVirtualThreadOperations =
        ops.asVirtualThread()

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["org.springframework.boot.actuate.health.HealthIndicator"])
    class HealthConfig {
        @Bean
        @ConditionalOnMissingBean
        fun neo4jHealthIndicator(driver: Driver): org.springframework.boot.actuate.health.HealthIndicator =
            org.springframework.boot.actuate.health.HealthIndicator {
            try {
                driver.verifyConnectivity()
                org.springframework.boot.actuate.health.Health.up().withDetail("backend", "neo4j").build()
            } catch (e: Exception) {
                org.springframework.boot.actuate.health.Health.down(e).build()
            }
        }
    }
}
```

### `GraphMemgraphAutoConfiguration.kt`

> Memgraph 는 Neo4j Bolt 프로토콜 호환. `MemgraphGraphOperations` 구현 존재 가정(모듈 `graph-memgraph`).

```kotlin
package io.bluetape4k.graph.spring.boot3.autoconfigure

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.graph.vt.asVirtualThread
import io.bluetape4k.graph.memgraph.MemgraphGraphOperations
import io.bluetape4k.graph.memgraph.MemgraphGraphSuspendOperations
import io.bluetape4k.graph.spring.boot3.properties.MemgraphGraphProperties
import io.bluetape4k.logging.KLogging
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@AutoConfiguration
@ConditionalOnClass(Driver::class, MemgraphGraphOperations::class)
@ConditionalOnProperty(prefix = "bluetape4k.graph", name = ["backend"], havingValue = "memgraph")
@EnableConfigurationProperties(MemgraphGraphProperties::class)
class GraphMemgraphAutoConfiguration {

    companion object : KLogging()

    @Bean(name = ["memgraphDriver"], destroyMethod = "close")
    @ConditionalOnMissingBean(Driver::class)
    fun memgraphDriver(props: MemgraphGraphProperties): Driver {
        val auth = if (props.password.isBlank()) AuthTokens.none()
                   else AuthTokens.basic(props.username, props.password)
        log.info { "Creating Memgraph Driver: uri=${props.uri}" }
        return GraphDatabase.driver(props.uri, auth)
    }

    @Bean
    @ConditionalOnMissingBean(GraphOperations::class)
    fun graphOperations(driver: Driver): GraphOperations = MemgraphGraphOperations(driver)

    @Bean
    @ConditionalOnMissingBean(GraphSuspendOperations::class)
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.memgraph",
        name = ["register-suspend"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun graphSuspendOperations(driver: Driver): GraphSuspendOperations =
        MemgraphGraphSuspendOperations(driver, props.database)

    @Bean
    @ConditionalOnMissingBean(GraphVirtualThreadOperations::class)
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.memgraph",
        name = ["register-virtual-thread"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun graphVirtualThreadOperations(ops: GraphOperations): GraphVirtualThreadOperations =
        ops.asVirtualThread()

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["org.springframework.boot.actuate.health.HealthIndicator"])
    class HealthConfig {
        @Bean
        @ConditionalOnMissingBean
        fun memgraphHealthIndicator(driver: Driver): org.springframework.boot.actuate.health.HealthIndicator =
            org.springframework.boot.actuate.health.HealthIndicator {
            try {
                driver.verifyConnectivity()
                org.springframework.boot.actuate.health.Health.up().withDetail("backend", "memgraph").build()
            } catch (e: Exception) {
                org.springframework.boot.actuate.health.Health.down(e).build()
            }
        }
    }
}
```

- [ ] **검증**: `./gradlew :graph-spring-boot3-starter:compileKotlin` 성공.

---

## Task 6 · GraphAgeAutoConfiguration _(complexity: medium)_

- [ ] `autoconfigure/GraphAgeAutoConfiguration.kt`:

```kotlin
package io.bluetape4k.graph.spring.boot3.autoconfigure

import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.graph.age.AgeGraphSuspendOperations
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.graph.vt.asVirtualThread
import io.bluetape4k.graph.spring.boot3.properties.AgeGraphProperties
import io.bluetape4k.logging.KLogging
import org.jetbrains.exposed.v1.jdbc.Database
import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnSingleCandidate
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import javax.sql.DataSource

@AutoConfiguration(after = [DataSourceAutoConfiguration::class])
@ConditionalOnClass(AgeGraphOperations::class, DataSource::class)
@ConditionalOnProperty(prefix = "bluetape4k.graph", name = ["backend"], havingValue = "age")
@ConditionalOnSingleCandidate(DataSource::class)
@EnableConfigurationProperties(AgeGraphProperties::class)
class GraphAgeAutoConfiguration {

    companion object : KLogging()

    @Bean(name = ["ageExposedDatabase"])
    @DependsOn("dataSource")
    @ConditionalOnMissingBean(name = ["ageExposedDatabase"])
    fun ageExposedDatabase(dataSource: DataSource): Database {
        if (dataSource is HikariDataSource) {
            // AGE extension 활성화 + search_path 설정 (커넥션 획득 시마다 실행)
            dataSource.connectionInitSql =
                "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
        }
        log.info { "Connecting Exposed Database to AGE DataSource" }
        return Database.connect(dataSource)
    }

    // graphOperations()는 AgeGraphOperations 구체 타입을 반환.
    // @ConditionalOnMissingBean(GraphOperations::class)으로 사용자 빈 우선.
    // 사용자가 GraphOperations를 override하면 이 빈이 생성되지 않고,
    // ageGraphInitializer도 @ConditionalOnBean(AgeGraphOperations::class) 조건으로 함께 생략된다.
    @Bean
    @ConditionalOnMissingBean(GraphOperations::class)
    @DependsOn("ageExposedDatabase")
    fun graphOperations(props: AgeGraphProperties): AgeGraphOperations =
        AgeGraphOperations(props.graphName)

    @Bean(name = ["ageGraphInitializer"])
    @DependsOn("graphOperations")
    @ConditionalOnBean(AgeGraphOperations::class)     // 타입 기반 조건 — name 기반보다 안전
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.age",
        name = ["auto-create-graph"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun ageGraphInitializer(ops: AgeGraphOperations, props: AgeGraphProperties): InitializingBean =
        InitializingBean {
            // AgeGraphOperations.createGraph() 는 내부적으로 LOAD 'age' + SET search_path 보장
            runCatching { ops.createGraph(props.graphName) }
                .onSuccess { log.info { "AGE graph '${props.graphName}' created / verified" } }
                .onFailure { log.debug { "AGE graph '${props.graphName}' already exists: ${it.message}" } }
        }

    @Bean
    @ConditionalOnMissingBean(GraphSuspendOperations::class)
    @DependsOn("ageExposedDatabase")
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.age",
        name = ["register-suspend"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun graphSuspendOperations(props: AgeGraphProperties): GraphSuspendOperations =
        AgeGraphSuspendOperations(props.graphName)

    @Bean
    @ConditionalOnMissingBean(GraphVirtualThreadOperations::class)
    @DependsOn("ageExposedDatabase")
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.age",
        name = ["register-virtual-thread"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun graphVirtualThreadOperations(ops: GraphOperations): GraphVirtualThreadOperations =
        ops.asVirtualThread()

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["org.springframework.boot.actuate.health.HealthIndicator"])
    class HealthConfig {
        @Bean
        @ConditionalOnMissingBean
        fun ageHealthIndicator(props: AgeGraphProperties): org.springframework.boot.actuate.health.HealthIndicator =
            org.springframework.boot.actuate.health.HealthIndicator {
                org.springframework.boot.actuate.health.Health.up()
                    .withDetail("backend", "age")
                    .withDetail("graphName", props.graphName)
                    .build()
            }
    }
}
```

- [ ] **검증**: `./gradlew :graph-spring-boot3-starter:compileKotlin` 성공.

---

## Task 7 · 루트 GraphAutoConfiguration + AutoConfiguration.imports _(complexity: low)_

- [ ] `autoconfigure/GraphAutoConfiguration.kt`:

```kotlin
package io.bluetape4k.graph.spring.boot3.autoconfigure

import io.bluetape4k.graph.spring.boot3.properties.GraphProperties
import io.bluetape4k.logging.KLogging
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties

/**
 * Root AutoConfiguration. 백엔드별 AutoConfiguration 의 import 순서를 보장하고,
 * 공통 속성(`GraphProperties`) 을 노출한다. 이 클래스 자체는 빈을 생성하지 않는다.
 */
@AutoConfiguration(
    before = [
        GraphTinkerGraphAutoConfiguration::class,
        GraphNeo4jAutoConfiguration::class,
        GraphMemgraphAutoConfiguration::class,
        GraphAgeAutoConfiguration::class,
    ],
)
@EnableConfigurationProperties(GraphProperties::class)
class GraphAutoConfiguration {
    companion object : KLogging()
}
```

- [ ] `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:

```
io.bluetape4k.graph.spring.boot3.autoconfigure.GraphAutoConfiguration
io.bluetape4k.graph.spring.boot3.autoconfigure.GraphTinkerGraphAutoConfiguration
io.bluetape4k.graph.spring.boot3.autoconfigure.GraphNeo4jAutoConfiguration
io.bluetape4k.graph.spring.boot3.autoconfigure.GraphMemgraphAutoConfiguration
io.bluetape4k.graph.spring.boot3.autoconfigure.GraphAgeAutoConfiguration
```

- [ ] `src/main/resources/META-INF/additional-spring-configuration-metadata.json`:

```json
{
  "properties": [
    {
      "name": "bluetape4k.graph.backend",
      "type": "java.lang.String",
      "description": "Active graph backend: tinkergraph | neo4j | memgraph | age.",
      "defaultValue": "tinkergraph"
    }
  ]
}
```

- [ ] **검증**: `./gradlew :graph-spring-boot3-starter:build -x test` → BUILD SUCCESSFUL.

---

## Task 8 · ApplicationContextRunner 단위 테스트 4종 _(complexity: medium)_

### `GraphTinkerGraphAutoConfigurationTest.kt`

```kotlin
package io.bluetape4k.graph.spring.boot3.autoconfigure

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldNotBeNull
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class GraphTinkerGraphAutoConfigurationTest {

    companion object : KLogging()

    private val runner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                GraphAutoConfiguration::class.java,
                GraphTinkerGraphAutoConfiguration::class.java,
            )
        )

    @Test
    fun `backend=tinkergraph 이면 GraphOperations 빈 등록`() {
        runner.withPropertyValues("bluetape4k.graph.backend=tinkergraph")
            .run { ctx ->
                ctx.getBean(GraphOperations::class.java).shouldNotBeNull()
                ctx.getBean(GraphSuspendOperations::class.java).shouldNotBeNull()
                ctx.getBean(GraphVirtualThreadOperations::class.java).shouldNotBeNull()
            }
    }

    @Test
    fun `backend=neo4j 이면 tinkergraph 빈 없음`() {
        runner.withPropertyValues("bluetape4k.graph.backend=neo4j")
            .run { ctx ->
                ctx.containsBean("graphOperations").shouldBeFalse()
            }
    }

    @Test
    fun `register-suspend=false 이면 GraphSuspendOperations 빈 없음`() {
        runner.withPropertyValues(
            "bluetape4k.graph.backend=tinkergraph",
            "bluetape4k.graph.tinkergraph.register-suspend=false",
        ).run { ctx ->
            assertThatThrownBy { ctx.getBean(GraphSuspendOperations::class.java) }
                .isInstanceOf(NoSuchBeanDefinitionException::class.java)
        }
    }

    @Test
    fun `register-virtual-thread=false 이면 VirtualThreadOperations 빈 없음`() {
        runner.withPropertyValues(
            "bluetape4k.graph.backend=tinkergraph",
            "bluetape4k.graph.tinkergraph.register-virtual-thread=false",
        ).run { ctx ->
            assertThatThrownBy { ctx.getBean(GraphVirtualThreadOperations::class.java) }
                .isInstanceOf(NoSuchBeanDefinitionException::class.java)
        }
    }
}
```

### `GraphNeo4jAutoConfigurationTest.kt` / `GraphMemgraphAutoConfigurationTest.kt`

> Testcontainers 로 `Neo4jServer.instance` / `MemgraphServer.instance` 싱글턴 사용. `backend=neo4j`/`memgraph` + `uri/username/password` 주입 후 빈 등록만 검증 (깊은 I/O 테스트는 통합 테스트에서 수행).

```kotlin
package io.bluetape4k.graph.spring.boot3.autoconfigure

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.servers.Neo4jServer
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GraphNeo4jAutoConfigurationTest {

    companion object : KLogging()

    private val runner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                GraphAutoConfiguration::class.java,
                GraphNeo4jAutoConfiguration::class.java,
            )
        )

    @Test
    fun `backend=neo4j 이면 Neo4j 빈 등록`() {
        runner.withPropertyValues(
            "bluetape4k.graph.backend=neo4j",
            "bluetape4k.graph.neo4j.uri=${Neo4jServer.boltUrl}",
            "bluetape4k.graph.neo4j.password=",
        ).run { ctx ->
            ctx.getBean(GraphOperations::class.java).shouldNotBeNull()
        }
    }
}
```

### `GraphAgeAutoConfigurationTest.kt`

> 별도 `HikariDataSource` 구성 빈을 `ApplicationContextRunner.withUserConfiguration` 으로 추가해 `DataSource` 조건 충족.

```kotlin
package io.bluetape4k.graph.spring.boot3.autoconfigure

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.servers.PostgreSQLAgeServer
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GraphAgeAutoConfigurationTest {

    companion object : KLogging()

    @Configuration(proxyBeanMethods = false)
    class DataSourceConfig {
        @Bean(destroyMethod = "close")
        fun dataSource(): DataSource {
            val cfg = HikariConfig().apply {
                jdbcUrl = PostgreSQLAgeServer.instance.jdbcUrl
                username = PostgreSQLAgeServer.instance.username
                password = PostgreSQLAgeServer.instance.password
                maximumPoolSize = 2
            }
            return HikariDataSource(cfg)
        }
    }

    private val runner = ApplicationContextRunner()
        .withUserConfiguration(DataSourceConfig::class.java)
        .withConfiguration(
            AutoConfigurations.of(
                GraphAutoConfiguration::class.java,
                GraphAgeAutoConfiguration::class.java,
            )
        )

    @Test
    fun `backend=age 이면 AGE 빈 등록`() {
        runner.withPropertyValues(
            "bluetape4k.graph.backend=age",
            "bluetape4k.graph.age.graph-name=test_graph",
        ).run { ctx ->
            ctx.getBean(GraphOperations::class.java).shouldNotBeNull()
        }
    }
}
```

- [ ] **검증**: `./gradlew :graph-spring-boot3-starter:test` → 모든 ApplicationContextRunner 테스트 PASS.

---

## Task 9 · WebMvc (Virtual Threads) 통합 테스트 _(complexity: high)_

- [ ] `src/test/resources/application-tinkergraph.yml`:

```yaml
bluetape4k:
  graph:
    backend: tinkergraph

spring:
  threads:
    virtual:
      enabled: true
```

- [ ] `webmvc/TinkerGraphWebMvcTest.kt`:

```kotlin
package io.bluetape4k.graph.spring.boot3.webmvc

import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.graph.spring.boot3.autoconfigure.GraphAutoConfiguration
import io.bluetape4k.logging.KLogging
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootTest(
    classes = [TinkerGraphWebMvcTest.TestApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@ActiveProfiles("tinkergraph")
class TinkerGraphWebMvcTest {

    companion object : KLogging()

    @Autowired lateinit var restTemplate: TestRestTemplate

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(GraphAutoConfiguration::class)
    class TestApp {

        @RestController
        @RequestMapping("/test/graph")
        class GraphController(@Autowired val ops: GraphOperations) {

            @PostMapping("/vertices/{label}")
            fun create(@PathVariable label: String): Map<String, String> {
                val v = ops.createVertex(label, mapOf("test" to "true"))
                return mapOf(
                    "id" to v.id.value,
                    "virtual" to Thread.currentThread().isVirtual.toString(),
                )
            }
        }
    }

    @Test
    fun `vertex 생성 + Virtual Thread 실행 확인`() {
        val resp = restTemplate.postForEntity(
            "/test/graph/vertices/Person", null, Map::class.java,
        )
        resp.statusCode.is2xxSuccessful.shouldBeTrue()
        @Suppress("UNCHECKED_CAST")
        val body = (resp.body.shouldNotBeNull()) as Map<String, String>
        body["virtual"].shouldBeEqualTo("true")
        body["id"].shouldNotBeNull()
    }
}
```

- [ ] **검증**: `./gradlew :graph-spring-boot3-starter:test --tests "*TinkerGraphWebMvcTest*"` → PASS, 로그에 `virtual=true` 확인.

---

## Task 10 · WebFlux (Coroutine) 통합 테스트 _(complexity: high)_

- [ ] `webflux/TinkerGraphWebFluxTest.kt`:

```kotlin
package io.bluetape4k.graph.spring.boot3.webflux

import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.graph.spring.boot3.autoconfigure.GraphAutoConfiguration
import io.bluetape4k.logging.KLogging
import kotlinx.coroutines.runBlocking
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@SpringBootTest(
    classes = [TinkerGraphWebFluxTest.TestApp::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.main.web-application-type=reactive"],
)
@ActiveProfiles("tinkergraph")
class TinkerGraphWebFluxTest {

    companion object : KLogging()

    @Autowired lateinit var webClient: WebTestClient

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(GraphAutoConfiguration::class)
    class TestApp {

        @RestController
        @RequestMapping("/test/suspend")
        class SuspendController(@Autowired val ops: GraphSuspendOperations) {

            @PostMapping("/vertices/{label}")
            suspend fun create(@PathVariable label: String): Map<String, String> {
                val v = ops.createVertex(label, mapOf("async" to "true"))
                return mapOf("id" to v.id.value, "label" to v.label)
            }
        }
    }

    @Test
    fun `suspend 컨트롤러로 vertex 생성`() = runBlocking<Unit> {
        webClient.post().uri("/test/suspend/vertices/User")
            .exchange()
            .expectStatus().is2xxSuccessful
            .expectBody(Map::class.java)
            .consumeWith { r ->
                val body = r.responseBody.shouldNotBeNull()
                body.containsKey("id").shouldBeTrue()
            }
    }
}
```

- [ ] **검증**: `./gradlew :graph-spring-boot3-starter:test --tests "*TinkerGraphWebFluxTest*"` → PASS.

---

## Task 11 · graph-spring-boot4-starter — boot3 코드 복사 + 패키지 변경 _(complexity: low)_

- [ ] `spring-boot4/graph-spring-boot4-starter/` 디렉토리 생성.
- [ ] `spring-boot3` 의 `src/**` 를 `spring-boot4` 로 복사.
- [ ] 패키지 치환: `io.bluetape4k.graph.spring.boot3` → `io.bluetape4k.graph.spring.boot4` (Edit tool 로 파일별 일괄 치환).
- [ ] `build.gradle.kts` 의 Spring Boot BOM 을 `4.0.x` 로 지정 (boot3: `3.5.x`).
- [ ] `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 도 `boot4` 패키지로 변경.
- [ ] **검증**: `./gradlew :graph-spring-boot4-starter:build` → BUILD SUCCESSFUL.

---

## Task 12 · README + testlog 기록 _(complexity: low)_

- [ ] `spring-boot3/graph-spring-boot3-starter/README.md` + `README.ko.md` (두 언어 동기화):
  - `bluetape4k.graph.backend` 선택 속성
  - 각 백엔드별 옵션 (`register-suspend`, `register-virtual-thread`)
  - TestApp 예시 (`@SpringBootConfiguration + @EnableAutoConfiguration + @Import`)
  - Actuator HealthIndicator 소개
  - **필수**: "`graph-spring-boot3-starter`는 `graph-core` API 타입(`GraphOperations` 등)을 포함하지만, 백엔드 AutoConfiguration은 백엔드 모듈(`graph-neo4j` 등) 없이 활성화되지 않는다." 경고 명시
  - **필수**: 올바른 의존성 조합 예시 (starter + 백엔드) 포함
- [ ] `wiki/testlogs/2026-04.md` 에 테스트 결과 기록 (ApplicationContextRunner 4종 + WebMvc + WebFlux).
- [ ] `docs/superpowers/index/2026-04.md` 엔트리 추가, `docs/superpowers/INDEX.md` 카운트 +1.
- [ ] **검증**: `./gradlew :graph-spring-boot3-starter:build :graph-spring-boot4-starter:build` 모두 성공.

---

## 참고 · 기존 bluetape4k-projects 패턴

`bluetape4k-projects/spring-boot3/batch-exposed` 의 `ExposedBatchAutoConfiguration` 패턴을 준수한다.

```kotlin
@AutoConfiguration(after = [BatchAutoConfiguration::class])
@ConditionalOnClass(Job::class)
@ConditionalOnBean(DataSource::class, PlatformTransactionManager::class)
class ExposedBatchAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(name = ["batchPartitionTaskExecutor"])
    fun batchPartitionTaskExecutor(): TaskExecutor = ...
}
```

---

## 최종 체크리스트 (PR 전)

- [ ] `@Primary` 검색 결과 **0 건** (모든 백엔드)
- [ ] `bluetape4k.graph.*.enabled` 패턴의 속성 **없음** — `backend` 하나만 존재 (`spring.threads.virtual.enabled` 등 무관 속성은 제외)
- [ ] `ApplicationContext.getBean` / `OPS_BEAN_NAMES` 같은 alias 계층 **없음**
- [ ] `@AutoConfiguration(after = [...])` 명시 (AGE 의 DataSourceAutoConfiguration)
- [ ] Actuator HealthIndicator 는 nested `HealthConfig` 클래스로 구현
- [ ] `@ConditionalOnMissingBean(Driver::class)` — 사용자 Driver 재사용 확인
- [ ] AGE 는 `ops: AgeGraphOperations` 타입으로 직접 받아 `ops.createGraph(props.graphName)` 호출 (캐스트 불필요)
- [ ] TestApp 은 `@SpringBootConfiguration + @EnableAutoConfiguration + @Import(GraphAutoConfiguration::class)` 패턴
- [ ] `./gradlew clean :graph-spring-boot3-starter:build :graph-spring-boot4-starter:build` 성공
- [ ] README(영/한) + testlog 업데이트
