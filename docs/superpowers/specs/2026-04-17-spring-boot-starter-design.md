# graph-spring-boot-starter 설계 스펙 (공식 Spring Boot 권장 기반)

- 일자: 2026-04-17
- 대상 모듈: `spring-boot3/graph-spring-boot3-starter/`, `spring-boot4/graph-spring-boot4-starter/`
- 선행 완료: 그래프 알고리즘 확장(2026-04-16), Virtual Threads 확장(2026-04-17)
- 본 스펙은 **Spring Boot 공식 AutoConfiguration 권장 패턴**을 준수한다.

---

## 1. 배경

`bluetape4k-graph`는 4개 백엔드(Neo4j, Memgraph, Apache AGE, TinkerGraph)를 3종 Facade
(`GraphOperations`, `GraphSuspendOperations`, `GraphVirtualThreadOperations`)로 제공한다.
Spring Boot 사용자는 드라이버와 Operations 빈을 수동 등록해야 하는 보일러플레이트를 부담한다.

본 스펙은 Spring Boot 공식 AutoConfiguration 권장 사항을 따라 `application.yml`만으로
백엔드를 선택·구성하고, 타입 스코프 조건부 빈 등록으로 사용자 오버라이드를 우아하게 지원하는
Starter를 설계한다.

---

## 2. 목표 / 비-목표

### 목표

- `bluetape4k.graph.backend` 단일 프로퍼티로 백엔드 선택 (neo4j | memgraph | age | tinkergraph).
- 각 백엔드 AutoConfiguration은 `@AutoConfiguration` + `@ConditionalOnClass` + `@ConditionalOnProperty`로 활성화 여부 결정.
- Operations 빈은 **타입 스코프** `@ConditionalOnMissingBean(GraphOperations::class)`로 사용자 빈이 없을 때만 등록 (`@Primary` 금지).
- Driver 빈은 `@ConditionalOnMissingBean(Driver::class)`로 Spring Boot 기본 Driver와 공존 가능.
- Actuator `HealthIndicator`는 nested `@Configuration(proxyBeanMethods=false)` + `@ConditionalOnClass(name=["...HealthIndicator"])` (string-based)로 격리. 외부 클래스에 Actuator import 금지, FQN만 사용.
- Spring Boot 3.5.x / 4.0.x 양 버전 병행 지원 (별도 모듈).
- Spring Web(Virtual Threads) + WebFlux(Coroutine) 통합 테스트로 회귀 방지.

### 비-목표

- ORM-수준 매핑(`@Node`, `@Relationship`): 범위 외.
- Spring Data Neo4j / JPA 연동: 범위 외.
- 다중 백엔드 동시 등록: V1에서 지원하지 않음 (프로퍼티 설계가 단일 백엔드 전제).

---

## 3. 접근 방식

### 설계 원칙 (Spring Boot 공식 권장)

1. **`@Primary` 금지** — AutoConfiguration은 사용자 빈을 존중해야 한다. `@ConditionalOnMissingBean(GraphOperations::class)` 타입 스코프로 "사용자 빈이 없을 때만" 등록하면 `@Primary` 없이도 사용자 오버라이드가 자연스럽게 동작.
2. **`enabled` 플래그 폐지** — `backend` 프로퍼티 하나로만 백엔드 선택. `neo4j.enabled` 같은 중복 플래그 제거.
3. **`@AutoConfiguration(after=[...])` 순서 보장** — `@ConditionalOnBean`을 쓸 때 반드시 `after=` 명시. (V1에서는 `@ConditionalOnBean` 대신 타입 스코프 `@ConditionalOnMissingBean` 사용)
4. **Actuator nested class 격리** — 외부 클래스에 Actuator import 금지. nested `@Configuration(proxyBeanMethods=false)` + `@ConditionalOnClass(name = ["org.springframework.boot.actuate.health.HealthIndicator"])` (string-based)로 격리. nested class 내부에서 FQN으로만 참조하여 Actuator 미사용 앱에서 `NoClassDefFoundError` 방지.
5. **Driver 재사용** — Spring Boot 기본 Neo4j Driver가 이미 있으면 재사용. ops 메서드는 `@Qualifier` 없이 `Driver` 타입 주입.
6. **AGE initializer 공식 API** — `AgeGraphOperations.createGraph()` 사용. raw JDBC/SQL 금지.
7. **Root `GraphAutoConfiguration`은 컨테이너만** — `@Import` 금지, `@AutoConfiguration(before=[...])` 순서만 담당, 빈 정의 없음.

### 모듈 구조 전략

- **단일 Starter 모듈** (`graph-spring-boot3-starter`): `compileOnly`로 4개 백엔드 선언 → 사용자가 원하는 백엔드를 명시적으로 의존성에 추가.
- **패키지 분리**: Boot 3 = `io.bluetape4k.graph.spring.boot3`, Boot 4 = `io.bluetape4k.graph.spring.boot4`.

---

## 4. 모듈 구조

```
bluetape4k-graph/
  spring-boot3/
    graph-spring-boot3-starter/
      build.gradle.kts
      src/
        main/
          kotlin/io/bluetape4k/graph/spring/boot3/
            properties/
              GraphProperties.kt               # bluetape4k.graph.backend 루트
              Neo4jGraphProperties.kt
              MemgraphGraphProperties.kt
              AgeGraphProperties.kt
              TinkerGraphGraphProperties.kt
            autoconfigure/
              GraphAutoConfiguration.kt        # before=[...] 순서 담당, 빈 없음
              GraphNeo4jAutoConfiguration.kt
              GraphMemgraphAutoConfiguration.kt
              GraphAgeAutoConfiguration.kt
              GraphTinkerGraphAutoConfiguration.kt
          resources/
            META-INF/
              spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
              additional-spring-configuration-metadata.json
        test/
          kotlin/io/bluetape4k/graph/spring/boot3/
            web/         # Spring Web + Virtual Threads 통합 테스트 (4개 백엔드)
            webflux/     # WebFlux + Coroutine 통합 테스트 (4개 백엔드)
            autoconfigure/GraphAutoConfigurationTest.kt   # ApplicationContextRunner
          resources/
            application-neo4j.yml
            application-memgraph.yml
            application-age.yml
            application-tinkergraph.yml

  spring-boot4/
    graph-spring-boot4-starter/   # 구조 동일, 패키지 io.bluetape4k.graph.spring.boot4
```

---

## 5. 지원 백엔드 범위

| 백엔드 | Operations 3종 | 비고 |
|--------|---------------|------|
| Neo4j | 모두 등록 | `neo4j-java-driver` 6.x |
| Memgraph | 모두 등록 | Neo4j Driver 재사용 (Bolt 호환) |
| Apache AGE | 모두 등록 | `DataSource` + `AgeGraphOperations.createGraph()`로 초기화 |
| TinkerGraph | 모두 등록 | in-memory, 외부 서버 불필요 |

---

## 6. 프로퍼티 설계

루트 접두사: `bluetape4k.graph`. `backend` 프로퍼티 하나로만 백엔드 선택.

```yaml
bluetape4k:
  graph:
    backend: neo4j    # neo4j | memgraph | age | tinkergraph | (없으면 AutoConfig 비활성)

    neo4j:
      uri: bolt://localhost:7687
      username: neo4j
      password: ""
      database: neo4j
      register-suspend: true
      register-virtual-thread: true

    memgraph:
      uri: bolt://localhost:7687
      username: ""
      password: ""
      register-suspend: true
      register-virtual-thread: true

    age:
      graph-name: default_graph
      auto-create-graph: true
      register-suspend: true
      register-virtual-thread: true

    tinkergraph:
      register-suspend: true
      register-virtual-thread: true
```

`register-suspend` / `register-virtual-thread`는 해당 빈 등록 여부만 제어 (기본 `true`).

### 프로퍼티 클래스 예시

```kotlin
/**
 * bluetape4k Graph 모듈의 루트 프로퍼티.
 *
 * `backend` 값 하나로 활성화할 백엔드를 선택한다.
 */
@ConfigurationProperties(prefix = "bluetape4k.graph")
data class GraphProperties(
    /** 활성화할 백엔드. `neo4j | memgraph | age | tinkergraph` 또는 미설정. */
    val backend: String? = null,
)

/**
 * Neo4j 백엔드 프로퍼티.
 *
 * `bluetape4k.graph.backend=neo4j`일 때만 [GraphNeo4jAutoConfiguration]이 활성화된다.
 */
@ConfigurationProperties(prefix = "bluetape4k.graph.neo4j")
data class Neo4jGraphProperties(
    /** Neo4j Bolt URI */
    val uri: String = "bolt://localhost:7687",
    /** 인증 사용자명 */
    val username: String = "neo4j",
    /** 인증 비밀번호 (prod에서는 환경변수 주입 권장) */
    val password: String = "",
    /** 사용할 데이터베이스 */
    val database: String = "neo4j",
    /** `GraphSuspendOperations` 빈 등록 여부 */
    val registerSuspend: Boolean = true,
    /** `GraphVirtualThreadOperations` 빈 등록 여부 */
    val registerVirtualThread: Boolean = true,
) {
    init {
        requireNotBlank(uri) { "bluetape4k.graph.neo4j.uri must not be blank" }
    }
}
```

---

## 7. AutoConfiguration 설계

### 7.1 Root `GraphAutoConfiguration`

**루트는 공통 Properties 바인딩만.** `@Import` 사용 금지 — compileOnly 백엔드 클래스를 `@Import`하면
해당 클래스가 classpath에 없을 때 `NoClassDefFoundError`가 발생한다.
대신 **`AutoConfiguration.imports`에 모든 AutoConfig를 직접 등록**한다.

```kotlin
/**
 * bluetape4k Graph 모듈의 AutoConfiguration 루트.
 *
 * 공통 속성(`GraphProperties`)만 바인딩한다. 빈 정의 없음, @Import 없음.
 * 백엔드 AutoConfig는 AutoConfiguration.imports에 개별 등록된다.
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

### 7.2 Neo4j AutoConfiguration (표준 패턴)

```kotlin
/**
 * Neo4j 백엔드용 AutoConfiguration.
 *
 * 활성화 조건:
 * - 클래스패스에 `org.neo4j.driver.Driver`와 `Neo4jGraphOperations`가 있고
 * - `bluetape4k.graph.backend=neo4j`일 때
 *
 * `Driver` 빈은 Spring Boot 기본 Neo4j AutoConfig가 이미 등록했다면 재사용한다
 * (`@ConditionalOnMissingBean(Driver::class)`).
 *
 * Operations 빈은 타입 스코프 `@ConditionalOnMissingBean`으로 사용자 빈이 없을 때만 등록된다.
 * `@Primary` 없이도 사용자 오버라이드가 우선순위를 가진다.
 */
@AutoConfiguration
@ConditionalOnClass(Driver::class, Neo4jGraphOperations::class)
@ConditionalOnProperty(
    prefix = "bluetape4k.graph",
    name = ["backend"],
    havingValue = "neo4j",
)
@EnableConfigurationProperties(Neo4jGraphProperties::class)
class GraphNeo4jAutoConfiguration {

    companion object : KLogging()

    /**
     * Neo4j Bolt Driver.
     *
     * Spring Boot 기본 Neo4j AutoConfig가 `Driver` 빈을 이미 등록했다면 재사용한다.
     * 컨텍스트 종료 시 `close()`가 자동 호출된다.
     */
    @Bean(name = ["neo4jDriver"], destroyMethod = "close")
    @ConditionalOnMissingBean(Driver::class)
    fun neo4jDriver(props: Neo4jGraphProperties): Driver {
        val auth = if (props.username.isBlank()) AuthTokens.none()
                   else AuthTokens.basic(props.username, props.password)
        return GraphDatabase.driver(props.uri, auth).also {
            log.info { "Neo4j Driver 생성 완료. uri=${props.uri}, database=${props.database}" }
        }
    }

    /**
     * 기본 동기 API `GraphOperations`.
     *
     * 사용자가 직접 `GraphOperations` 빈을 등록하면 이 메서드는 호출되지 않는다
     * (`@ConditionalOnMissingBean(GraphOperations::class)`).
     */
    @Bean
    @ConditionalOnMissingBean(GraphOperations::class)
    fun graphOperations(driver: Driver, props: Neo4jGraphProperties): GraphOperations =
        Neo4jGraphOperations(driver, props.database)

    /**
     * Coroutine 기반 `GraphSuspendOperations`.
     *
     * `bluetape4k.graph.neo4j.register-suspend=false`이면 등록되지 않는다.
     */
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

    /**
     * Virtual Thread 기반 `GraphVirtualThreadOperations`.
     *
     * `bluetape4k.graph.neo4j.register-virtual-thread=false`이면 등록되지 않는다.
     */
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

    /**
     * Actuator HealthIndicator는 nested class로 격리한다.
     *
     * Actuator를 쓰지 않는 애플리케이션에서 `HealthIndicator` 클래스가 없어도
     * `ClassNotFoundException`이 발생하지 않도록 한다.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["org.springframework.boot.actuate.health.HealthIndicator"])
    class HealthConfig {

        /** Neo4j 연결 상태를 검증하는 HealthIndicator. */
        @Bean(name = ["neo4jGraphHealthIndicator"])
        @ConditionalOnMissingBean(name = ["neo4jGraphHealthIndicator"])
        fun neo4jGraphHealthIndicator(driver: Driver): org.springframework.boot.actuate.health.HealthIndicator =
            org.springframework.boot.actuate.health.HealthIndicator {
                runCatching { driver.verifyConnectivity() }
                    .map { org.springframework.boot.actuate.health.Health.up().withDetail("backend", "neo4j").build() }
                    .getOrElse { org.springframework.boot.actuate.health.Health.down(it).build() }
            }
    }
}
```

### 7.3 Memgraph AutoConfiguration

Neo4j Driver를 재사용하므로 Neo4j와 거의 동일하다. 차이점:
- `@ConditionalOnProperty(...havingValue = "memgraph")`
- Driver 빈 이름은 `memgraphDriver` (Neo4j Driver와 타입 충돌을 피하려면 프로퍼티로 URI를 구분해 하나만 활성화하는 전제)
- `MemgraphGraphProperties`에 `database: String = "memgraph"` 필드를 추가하고 `MemgraphGraphSuspendOperations(driver, props.database)` 형태로 전달.

### 7.4 AGE AutoConfiguration

```kotlin
/**
 * Apache AGE 백엔드용 AutoConfiguration.
 *
 * Spring Boot 기본 `DataSource`를 재사용한다 (`@ConditionalOnSingleCandidate(DataSource::class)`).
 * HikariCP가 있으면 `connectionInitSql`에 `LOAD 'age'; SET search_path = ag_catalog, "$user", public;`을
 * 자동 주입해 세션 초기화를 보장한다.
 *
 * 그래프 생성은 `AgeGraphOperations.createGraph()` 공식 API를 사용한다 (raw JDBC 금지).
 */
@AutoConfiguration(after = [DataSourceAutoConfiguration::class])
@ConditionalOnClass(AgeGraphOperations::class)
@ConditionalOnProperty(
    prefix = "bluetape4k.graph",
    name = ["backend"],
    havingValue = "age",
)
@EnableConfigurationProperties(AgeGraphProperties::class)
class GraphAgeAutoConfiguration {

    companion object : KLogging()

    @Bean
    @ConditionalOnMissingBean(GraphOperations::class)
    @DependsOn("ageExposedDatabase")
    fun graphOperations(props: AgeGraphProperties): AgeGraphOperations =
        AgeGraphOperations(props.graphName)

    @Bean(name = ["ageGraphInitializer"])
    @DependsOn("graphOperations")
    @ConditionalOnBean(AgeGraphOperations::class)
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.age",
        name = ["auto-create-graph"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun ageGraphInitializer(ops: AgeGraphOperations, props: AgeGraphProperties): InitializingBean =
        InitializingBean {
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
        @Bean(name = ["ageGraphHealthIndicator"])
        @ConditionalOnMissingBean(name = ["ageGraphHealthIndicator"])
        fun ageGraphHealthIndicator(dataSource: DataSource): org.springframework.boot.actuate.health.HealthIndicator =
            org.springframework.boot.actuate.health.HealthIndicator {
                runCatching { dataSource.connection.use { it.isValid(1) } }
                    .map { org.springframework.boot.actuate.health.Health.up().withDetail("backend", "age").build() }
                    .getOrElse { org.springframework.boot.actuate.health.Health.down(it).build() }
            }
    }
}
```

### 7.5 TinkerGraph AutoConfiguration

외부 의존성 없음. 단순 `TinkerGraphOperations()` 생성만 한다.

```kotlin
@AutoConfiguration
@ConditionalOnClass(TinkerGraphOperations::class)
@ConditionalOnProperty(
    prefix = "bluetape4k.graph",
    name = ["backend"],
    havingValue = "tinkergraph",
)
@EnableConfigurationProperties(TinkerGraphGraphProperties::class)
class GraphTinkerGraphAutoConfiguration {

    companion object : KLogging()

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(GraphOperations::class)
    fun graphOperations(): GraphOperations = TinkerGraphOperations()

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
}
```

### 7.6 `AutoConfiguration.imports`

루트와 백엔드 AutoConfig 5개를 **모두** 등록한다.
루트가 `@AutoConfiguration(before=[...])` 순서만 제어한다.

```
io.bluetape4k.graph.spring.boot3.autoconfigure.GraphAutoConfiguration
io.bluetape4k.graph.spring.boot3.autoconfigure.GraphTinkerGraphAutoConfiguration
io.bluetape4k.graph.spring.boot3.autoconfigure.GraphNeo4jAutoConfiguration
io.bluetape4k.graph.spring.boot3.autoconfigure.GraphMemgraphAutoConfiguration
io.bluetape4k.graph.spring.boot3.autoconfigure.GraphAgeAutoConfiguration
```

---

## 8. 빈 이름 / 오버라이드 정책

### 기본 원칙

- Operations 빈은 **타입 스코프**로만 조건부 등록. 사용자가 어떤 이름으로든 `GraphOperations` 빈을 등록하면 자동 구성이 비활성화됨.
- `@Primary` 사용 안 함 — `@ConditionalOnMissingBean(GraphOperations::class)`가 같은 역할을 한다.
- Driver 빈은 `@ConditionalOnMissingBean(Driver::class)` — Spring Boot 기본 Neo4j Driver와 공존.

### 빈 이름 컨벤션 (기본 생성 시)

| 빈 타입 | 기본 이름 (Spring 메서드명 기준) |
|---------|-------------------------------|
| Driver (Neo4j/Memgraph) | `neo4jDriver` / `memgraphDriver` |
| GraphOperations | `graphOperations` |
| GraphSuspendOperations | `graphSuspendOperations` |
| GraphVirtualThreadOperations | `graphVirtualThreadOperations` |
| HealthIndicator | `<backend>GraphHealthIndicator` |

### 사용자 오버라이드 예시

```kotlin
@Configuration
class CustomGraphConfig {
    /** 사용자 정의 GraphOperations — AutoConfig가 자동으로 비활성화됨 */
    @Bean
    fun graphOperations(driver: Driver): GraphOperations =
        Neo4jGraphOperations(driver, "customDb")
}
```

---

## 9. 테스트 전략

### 9.1 Spring Web (Virtual Threads)

- `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` (MockMvc 금지)
- 이유: MockMvc는 테스트 스레드에서 컨트롤러를 직접 실행하므로 `Thread.currentThread().isVirtual == false`.
  `RANDOM_PORT + TestRestTemplate`만 실제 HTTP로 Virtual Thread Executor를 거쳐 검증 가능.
- `application.yml`: `spring.threads.virtual.enabled=true` + `bluetape4k.graph.backend=<backend>`
- 검증 지점: 응답 body에 `"virtual": "true"` 포함 여부, 생성된 vertex id 비어 있지 않음.

| 테스트 클래스 | 백엔드 | 서버 |
|------|--------|------|
| `TinkerGraphWebMvcTest` | TinkerGraph | 불필요 |
| `Neo4jGraphWebMvcTest` | Neo4j | `Neo4jServer.instance` |
| `MemgraphGraphWebMvcTest` | Memgraph | `MemgraphServer.instance` |
| `AgeGraphWebMvcTest` | AGE | `PostgreSQLAgeServer.instance` |

컨테이너 프로퍼티 주입은 `@DynamicPropertySource`로 `bluetape4k.graph.neo4j.uri` 등 동적 주입.

### 9.2 WebFlux (Coroutine)

- `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `WebTestClient`
- `application.yml`: `spring.main.web-application-type=reactive` + `bluetape4k.graph.backend=<backend>`
- `suspend` 컨트롤러에서 `GraphSuspendOperations` 주입 동작 확인.
- `WebTestClient`로 왕복 검증, `Flow` 응답은 `.awaitFirst()` 등으로 단언.

### 9.3 AutoConfiguration 단위 테스트

```kotlin
@Test
fun `사용자가 GraphOperations 빈을 등록하면 자동 구성이 비활성화된다`() {
    ApplicationContextRunner()
        .withPropertyValues("bluetape4k.graph.backend=tinkergraph")
        .withUserConfiguration(UserGraphConfig::class.java)
        .withConfiguration(
            AutoConfigurations.of(
                GraphAutoConfiguration::class.java,
                GraphTinkerGraphAutoConfiguration::class.java,  // 테스트 대상 백엔드도 명시
            )
        )
        .run { context ->
            context.getBean(GraphOperations::class.java) shouldBeSameInstanceAs
                context.getBean("userGraphOps", GraphOperations::class.java)
        }
}
```

---

## 10. 코딩 컨벤션

모든 AutoConfiguration / Properties 클래스에 필수 적용:

1. **한국어 KDoc**: 모든 `public` 클래스·함수·프로퍼티에 한국어 KDoc.
2. **KLogging**: 로그 사용 클래스에 `companion object : KLogging()`.
3. **입력 검증**: Properties `init {}` 블록에서 `requireNotBlank(uri) { ... }` 등 적용.
4. **`@Primary` 사용 금지**: 타입 스코프 `@ConditionalOnMissingBean`이 같은 의도를 표현.
5. **`@Qualifier` 최소화**: Driver 타입 단일성을 전제로 `@Qualifier` 제거.

---

## 11. HealthIndicator / Graceful Shutdown

### HealthIndicator — nested class 격리 필수

```kotlin
// 외부 AutoConfiguration 파일에 Health/HealthIndicator import 추가하지 말 것 — FQN만 사용
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(name = ["org.springframework.boot.actuate.health.HealthIndicator"])  // string-based 필수
class HealthConfig {
    @Bean
    @ConditionalOnMissingBean
    fun neo4jGraphHealthIndicator(driver: Driver): org.springframework.boot.actuate.health.HealthIndicator =
        org.springframework.boot.actuate.health.HealthIndicator {
            runCatching { driver.verifyConnectivity() }
                .map { org.springframework.boot.actuate.health.Health.up().withDetail("backend", "neo4j").build() }
                .getOrElse { org.springframework.boot.actuate.health.Health.down(it).build() }
        }
}
```

외부 `AutoConfiguration` 본체에 `Health`/`HealthIndicator` import 없이 nested class 내에서 FQN으로만
참조하면, Actuator 없는 환경에서도 클래스 로딩 오류가 발생하지 않는다.

### Graceful Shutdown

- Neo4j/Memgraph `Driver`: `@Bean(destroyMethod = "close")`
- TinkerGraph `GraphOperations`: `@Bean(destroyMethod = "close")`
- AGE: `DataSource`는 Spring Boot가 관리. `AgeGraphOperations`는 stateless이므로 명시적 close 불필요.

---

## 12. 의존성 (spring-boot3/graph-spring-boot3-starter/build.gradle.kts)

```kotlin
plugins {
    kotlin("plugin.spring")
    kotlin("plugin.noarg")
}

dependencies {
    api(project(":graph-core"))

    // 사용자가 원하는 백엔드 모듈을 명시적으로 추가하도록 compileOnly
    compileOnly(project(":graph-neo4j"))
    compileOnly(project(":graph-memgraph"))
    compileOnly(project(":graph-age"))
    compileOnly(project(":graph-tinkerpop"))

    // Spring Boot 3.5 — 루트 build.gradle.kts의 전역 Boot 4 BOM을 명시 override
    compileOnly(platform(Libs.spring_boot3_dependencies))
    implementation(platform(Libs.spring_boot3_dependencies))
    compileOnly(Libs.springBoot("autoconfigure"))
    compileOnly(Libs.springBoot("configuration-processor"))
    compileOnly(Libs.springBootStarter("actuator"))   // HealthIndicator — nested class 격리로만 참조
    compileOnly(Libs.hikaricp)                         // AGE connectionInitSql (선택)

    compileOnly(Libs.kotlinx_coroutines_core)
    compileOnly(Libs.bluetape4k_coroutines)

    // 테스트
    testImplementation(project(":graph-neo4j"))
    testImplementation(project(":graph-memgraph"))
    testImplementation(project(":graph-age"))
    testImplementation(project(":graph-tinkerpop"))
    testImplementation(project(":graph-servers"))

    testImplementation(platform(Libs.spring_boot3_dependencies))
    testImplementation(Libs.springBootStarter("web"))
    testImplementation(Libs.springBootStarter("webflux"))
    testImplementation(Libs.springBootStarter("test"))
    testImplementation(Libs.springBootStarter("actuator"))
    testImplementation(Libs.kotlinx_coroutines_reactor)
    testImplementation(Libs.kotlinx_coroutines_test)
    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_testcontainers)
}
```

`spring-boot4/graph-spring-boot4-starter/build.gradle.kts`는 `Libs.spring_boot4_dependencies`로 치환.

---

## 13. settings.gradle.kts 변경

```kotlin
// 현재
includeModules("graph", false, false)
includeModules("examples", false, false)

// 변경 후
includeModules("graph", false, false)
includeModules("examples", false, false)
includeModules("spring-boot3", false, false)
includeModules("spring-boot4", false, false)
```

하위 디렉토리 이름을 `graph-spring-boot3-starter` / `graph-spring-boot4-starter`로 두면
`includeModules(baseDir, withProjectName=false, withBaseDir=false)`가 그대로 프로젝트명으로 사용한다.
최종 Maven 좌표: `io.bluetape4k:graph-spring-boot3-starter`, `io.bluetape4k:graph-spring-boot4-starter`.

---

## 부록 A — 초안 태스크 목록

| # | 태스크 | complexity |
|---|-------|------------|
| T1 | `settings.gradle.kts` 변경 + 두 스타터 디렉토리 생성 | S |
| T2 | `spring-boot3/graph-spring-boot3-starter/build.gradle.kts` 작성 | S |
| T3 | 5개 Properties 클래스 작성 (KDoc + requireNotBlank) | M |
| T4 | `GraphNeo4jAutoConfiguration` + `GraphMemgraphAutoConfiguration` 작성 (HealthConfig nested + destroyMethod) | M |
| T5 | `GraphAgeAutoConfiguration` 작성 (`AgeGraphOperations.createGraph()` 사용) | M |
| T6 | `GraphTinkerGraphAutoConfiguration` 작성 | S |
| T7 | `GraphAutoConfiguration` 루트 + `AutoConfiguration.imports` + 메타데이터 JSON | S |
| T8 | Spring Web(Virtual Threads) 통합 테스트 4종 (`TestRestTemplate` + RANDOM_PORT) | L |
| T9 | Spring WebFlux(Coroutine) 통합 테스트 4종 (`WebTestClient`) | L |
| T10 | `ApplicationContextRunner` 기반 AutoConfiguration 단위 테스트 | M |
| T11 | spring-boot4 스타터 복제 (패키지/BOM 치환) | M |
| T12 | `README.md` + `README.ko.md` 동시 작성 | M |
| T13 | BOM 모듈 + CLAUDE.md 업데이트 | S |
| T14 | `docs/superpowers/index/2026-04.md` + INDEX.md 갱신 | S |

총 complexity: S×6, M×6, L×2

---

## 부록 B — 확정된 기술 결정

1. **`@Primary` 전면 금지** — 타입 스코프 `@ConditionalOnMissingBean`이 같은 의도를 표현하며 사용자 오버라이드가 자연스럽다.
2. **`enabled` 플래그 폐지** — `bluetape4k.graph.backend` 하나로만 백엔드 선택.
3. **Actuator nested class 필수** — `@Configuration(proxyBeanMethods=false) @ConditionalOnClass(name=["...HealthIndicator"])` string-based nested class로만 격리. 외부 클래스에 Actuator import 금지, FQN만 사용.
4. **Driver 재사용** — `@ConditionalOnMissingBean(Driver::class)` + ops 메서드는 `@Qualifier` 없이 타입 주입.
5. **AGE 초기화** — `AgeGraphOperations.createGraph()` 공식 API 사용. raw JDBC / SQL 금지.
6. **Root AutoConfig는 빈 정의 없음** — `@Import` 금지, `@AutoConfiguration(before=[...])` 순서만 담당.
7. **`@AutoConfiguration(after=[...])`는 `@ConditionalOnBean` 필요 시에만** — AGE의 `DataSourceAutoConfiguration` 의존성 같은 경우.
