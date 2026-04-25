# graph-servers 모듈을 bluetape4k-testcontainers graphdb 서버로 대체

- **작성일**: 2026-04-18
- **작성자**: Claude Code (general-purpose agent)
- **상태**: Draft
- **관련 워크트리**: TBD (작업 시 생성)

---

## 1. 배경 및 목적

### 1.1 현재 상황

`bluetape4k-graph` 저장소에는 테스트용 Testcontainers 기반 그래프 DB 서버 팩토리가 `graph/graph-servers/` 모듈에 정의되어 있다.

- 모듈 경로: `graph/graph-servers/`
- 패키지: `io.bluetape4k.graph.servers`
- 제공 클래스: `Neo4jServer`, `MemgraphServer`, `PostgreSQLAgeServer`

한편 `bluetape4k-projects` 저장소의 `bluetape4k-testcontainers` 모듈에 동일한 목적의 서버 클래스가 이미 제공되고 있다.

- 패키지: `io.bluetape4k.testcontainers.graphdb`
- 제공 클래스: `Neo4jServer`, `MemgraphServer`, `PostgreSQLAgeServer`
- 의존성 좌표: `Libs.bluetape4k_testcontainers` (이미 `buildSrc/src/main/kotlin/Libs.kt`에 정의됨)

### 1.2 목적

- **중복 제거**: `graph-servers` 모듈을 삭제하고 `bluetape4k-testcontainers`의 서버 팩토리를 재사용한다.
- **일관성 확보**: 그래프 DB 테스트 컨테이너 설정을 bluetape4k 에코시스템 전역에서 단일 출처로 관리한다.
- **유지보수 비용 축소**: 동일한 컨테이너 설정을 두 곳에서 수정해야 하는 부담 제거.

### 1.3 성공 기준

- `graph/graph-servers/` 디렉토리가 저장소에서 완전히 제거된다.
- 모든 테스트 모듈의 `build.gradle.kts`에서 `project(":graph-servers")` 참조가 제거되고 모듈별 보조 의존성이 명시된다.
- 전체 테스트 코드에서 `io.bluetape4k.graph.servers` import가 0건이다.
- `./gradlew build && ./gradlew test` 가 모두 통과한다.
- `CLAUDE.md`, `README.md`, `README.ko.md`, `examples/*/README*.md`, `TODO.md`에서 구 API 참조가 정리된다.
- `docs/testlogs/2026-04.md` 상단에 테스트 결과가 기록된다.

---

## 2. 범위

### 2.1 변경 대상 파일

#### 2.1.1 `build.gradle.kts` (8개)

| 파일 경로 | 변경 내용 |
|----------|-----------|
| `graph/graph-neo4j/build.gradle.kts` | `project(":graph-servers")` 제거; `testImplementation(Libs.bluetape4k_testcontainers)`, `testImplementation(Libs.testcontainers_neo4j)`, `testImplementation(Libs.neo4j_java_driver)` 추가 |
| `graph/graph-memgraph/build.gradle.kts` | `project(":graph-servers")` 제거; `testImplementation(Libs.bluetape4k_testcontainers)`, `testImplementation(Libs.testcontainers)`, `testImplementation(Libs.neo4j_java_driver)` 추가 |
| `graph/graph-age/build.gradle.kts` | `project(":graph-servers")` 제거; `testImplementation(Libs.bluetape4k_testcontainers)`, `testImplementation(Libs.testcontainers_postgresql)`, `testImplementation(Libs.postgresql_driver)`, `testImplementation(Libs.hikaricp)` 추가 |
| `graph/graph-tinkerpop/build.gradle.kts` | `project(":graph-servers")` 제거만 (TinkerGraph은 in-memory — 추가 대체 불필요) |
| `examples/code-graph-examples/build.gradle.kts` | `project(":graph-servers")` 제거; `testImplementation(Libs.bluetape4k_testcontainers)`, `testImplementation(Libs.testcontainers_neo4j)`, `testImplementation(Libs.testcontainers_postgresql)`, `testImplementation(Libs.testcontainers)`, `testImplementation(Libs.neo4j_java_driver)`, `testImplementation(Libs.postgresql_driver)`, `testImplementation(Libs.hikaricp)` 추가 |
| `examples/linkedin-graph-examples/build.gradle.kts` | 동일 (code-graph-examples와 동일 조합) |
| `spring-boot3/graph-spring-boot3-starter/build.gradle.kts` | `project(":graph-servers")` 제거; `testImplementation(Libs.bluetape4k_testcontainers)`, `testImplementation(Libs.testcontainers_neo4j)`, `testImplementation(Libs.testcontainers_postgresql)`, `testImplementation(Libs.testcontainers)`, `testImplementation(Libs.neo4j_java_driver)`, `testImplementation(Libs.postgresql_driver)`, `testImplementation(Libs.hikaricp)`, `testRuntimeOnly(Libs.neo4j_bolt_connection_netty)`, `testRuntimeOnly(Libs.neo4j_bolt_connection_pooled)` 추가 |
| `spring-boot4/graph-spring-boot4-starter/build.gradle.kts` | 동일 (spring-boot3과 동일 조합) |

#### 2.1.2 Neo4jServer 사용 테스트 (9개)

- `graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jAlgorithmTest.kt`
- `graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jAlgorithmSuspendTest.kt`
- `graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphOperationsTest.kt`
- `examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/Neo4jCodeGraphTest.kt`
- `examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/Neo4jCodeGraphSuspendTest.kt`
- `examples/linkedin-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/Neo4jLinkedInGraphTest.kt`
- `examples/linkedin-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/Neo4jLinkedInGraphSuspendTest.kt`
- `spring-boot3/graph-spring-boot3-starter/src/test/.../GraphNeo4jAutoConfigurationTest.kt`
- `spring-boot4/graph-spring-boot4-starter/src/test/.../GraphNeo4jAutoConfigurationTest.kt`

#### 2.1.3 MemgraphServer 사용 테스트 (9개)

- `graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphOperationsTest.kt`
- `graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphSuspendOperationsTest.kt`
- `graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphAlgorithmTest.kt`
- `examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/MemgraphCodeGraphTest.kt`
- `examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/MemgraphCodeGraphSuspendTest.kt`
- `examples/linkedin-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/MemgraphLinkedInGraphTest.kt`
- `examples/linkedin-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/MemgraphLinkedInGraphSuspendTest.kt`
- `spring-boot3/graph-spring-boot3-starter/src/test/.../GraphMemgraphAutoConfigurationTest.kt`
- `spring-boot4/graph-spring-boot4-starter/src/test/.../GraphMemgraphAutoConfigurationTest.kt`

#### 2.1.4 PostgreSQLAgeServer 사용 테스트 (9개)

- `graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeGraphOperationsTest.kt`
- `graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeGraphSuspendOperationsTest.kt`
- `graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeAlgorithmTest.kt`
- `examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/AgeCodeGraphTest.kt`
- `examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/AgeCodeGraphSuspendTest.kt`
- `examples/linkedin-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/AgeLinkedInGraphTest.kt`
- `examples/linkedin-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/AgeLinkedInGraphSuspendTest.kt`
- `spring-boot3/graph-spring-boot3-starter/src/test/.../GraphAgeAutoConfigurationTest.kt`
- `spring-boot4/graph-spring-boot4-starter/src/test/.../GraphAgeAutoConfigurationTest.kt`

#### 2.1.5 기타 파일

- `settings.gradle.kts`: **변경 불필요** — `includeModules("graph", false, false)`가 `graph/` 하위 `build.gradle.kts` 존재 여부로 자동 스캔. `graph/graph-servers/` 디렉토리 삭제 시 자동으로 빌드 대상에서 제외됨.
- `graph/graph-servers/` 디렉토리 전체 삭제 (README.md, README.ko.md, build.gradle.kts, src/ 포함)
- `CLAUDE.md`: 모듈 구조 설명에서 `graph-servers` 섹션 제거 및 테스트 패턴 예시 갱신
- `README.md`, `README.ko.md` (루트): `graph-servers` 및 구 API 예시(`Neo4jServer.boltUrl` 등) 갱신
- `examples/*/README*.md`: 구 API 예시 갱신
- `CHANGELOG.md`: 과거 기록이므로 수정하지 않음 (과거 이력 보존)

### 2.2 범위 외

- 구현 모듈(`graph-core`, `graph-neo4j`, `graph-memgraph`, `graph-age`, `graph-tinkerpop`)의 운영 로직 변경 금지.
- `bluetape4k-testcontainers` 내부 구현 변경 금지 (외부 저장소).
- 운영 설정(docker-compose, CI 파이프라인 등)의 변경은 범위 외 — 테스트 코드가 변경된 패키지를 import하면 충분.

---

## 3. API 매핑 표

### 3.1 Neo4jServer

| 기존 API (`io.bluetape4k.graph.servers`) | 신규 API (`io.bluetape4k.testcontainers.graphdb`) |
|-----------------------------------------|--------------------------------------------------|
| `Neo4jServer.boltUrl` | `Neo4jServer.Launcher.neo4j.boltUrl` |
| `Neo4jServer.instance.boltUrl` | `Neo4jServer.Launcher.neo4j.boltUrl` |
| `Neo4jServer.instance` (as `Neo4jContainer<*>`) | `Neo4jServer.Launcher.neo4j` |

> ⚠️ 기존 `Neo4jServer`에는 `.driver` 프로퍼티가 없다. `Neo4jServer.boltUrl`로 직접 `GraphDatabase.driver(...)` 를 생성하는 패턴만 존재했다.

### 3.2 MemgraphServer

| 기존 API | 신규 API |
|---------|---------|
| `MemgraphServer.boltUrl` | `MemgraphServer.Launcher.memgraph.boltUrl` |
| `MemgraphServer.instance` | `MemgraphServer.Launcher.memgraph` |
| `MemgraphServer.driver` | 테스트 클래스가 직접 `GraphDatabase.driver(...)` 를 생성하고 `@AfterAll`에서 `close()` 호출 |

> ⚠️ **Driver 소유권 이동**: 기존 `MemgraphServer.driver`는 싱글턴으로 테스트가 닫지 않는 계약이었다. 신규 방식에서는 **driver 소유권이 테스트 클래스로 이동**한다. 단순 치환 시 `driver`가 닫히지 않고 누수될 수 있으므로, 치환 패턴을 반드시 아래 §5.1 에 따라 적용해야 한다.

### 3.3 PostgreSQLAgeServer

| 기존 API | 신규 API |
|---------|---------|
| `PostgreSQLAgeServer.instance.jdbcUrl` | `PostgreSQLAgeServer.Launcher.postgresqlAge.jdbcUrl` |
| `PostgreSQLAgeServer.instance.username` | `PostgreSQLAgeServer.Launcher.postgresqlAge.username` |
| `PostgreSQLAgeServer.instance.password` | `PostgreSQLAgeServer.Launcher.postgresqlAge.password` |
| `PostgreSQLAgeServer.instance` (as container) | `PostgreSQLAgeServer.Launcher.postgresqlAge` |

> **참고**: 신규 API는 `Launcher` 중첩 객체로 싱글턴 컨테이너를 제공한다. 기존 `.instance` 또는 직접 `.boltUrl` 접근 패턴은 모두 `.Launcher.<serverName>`의 확장 프로퍼티로 치환된다.

---

## 4. 의존성 변경

### 4.1 Before

```kotlin
// graph/graph-neo4j/build.gradle.kts (예시)
dependencies {
    api(project(":graph-core"))
    // ...
    testImplementation(project(":graph-servers"))
    // ...
}
```

### 4.2 After

> ⚠️ **중요**: `bluetape4k-testcontainers`는 graphdb 관련 의존성들(testcontainers_neo4j, testcontainers_postgresql, neo4j_java_driver, hikaricp)을 **`compileOnly`** 로 선언한다. 따라서 소비자 모듈에서 이 타입들을 직접 사용한다면 **명시적으로 추가**해야 한다.

```kotlin
// graph/graph-neo4j/build.gradle.kts
dependencies {
    api(project(":graph-core"))
    // ...
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers_neo4j)   // Neo4jServer 타입 사용 시 필요
    testImplementation(Libs.neo4j_java_driver)       // GraphDatabase.driver() 직접 호출 시
    // ...
}
```

```kotlin
// spring-boot3/graph-spring-boot3-starter/build.gradle.kts
// spring-boot4/graph-spring-boot4-starter/build.gradle.kts
dependencies {
    // ...기존 선언...
    // graph-servers 제거 후 직접 의존성 추가 필요:
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers_neo4j)
    testImplementation(Libs.testcontainers_postgresql)
    testImplementation(Libs.neo4j_java_driver)
    testImplementation(Libs.hikaricp)               // GraphAgeAutoConfigurationTest의 HikariConfig 사용
    testRuntimeOnly(Libs.neo4j_bolt_connection_netty)
    testRuntimeOnly(Libs.neo4j_bolt_connection_pooled)
    testRuntimeOnly(Libs.postgresql_driver)
}
```

### 4.3 백엔드별 testcontainers 보조 의존성

`bluetape4k-testcontainers`가 `compileOnly`로 선언하는 의존성을 소비자가 직접 추가해야 한다:

| 모듈 | 추가 필요 의존성 |
|------|----------------|
| `graph-neo4j` | `testcontainers_neo4j`, `neo4j_java_driver` |
| `graph-memgraph` | `testcontainers`, `neo4j_java_driver` |
| `graph-age` | `testcontainers_postgresql`, `postgresql_driver`, `hikaricp` |
| `examples/*` | 위 조합 중 사용하는 백엔드만 |
| `spring-boot3/4 starter` | 위 전체 + `hikaricp` (HikariConfig 직접 사용) |

### 4.4 TinkerGraph 모듈 예외

`graph-tinkerpop`은 인메모리 구현이므로 테스트 컨테이너가 불필요하다. 과거 `project(":graph-servers")` 의존성은 의미 없이 포함되어 있었을 가능성이 있다. 해당 라인만 제거하고 대체 의존성을 추가하지 않는다.

---

## 5. Import 변경

각 테스트 파일의 import 블록에서 아래 경로를 일괄 치환한다.

```kotlin
// Before
import io.bluetape4k.graph.servers.Neo4jServer
import io.bluetape4k.graph.servers.MemgraphServer
import io.bluetape4k.graph.servers.PostgreSQLAgeServer

// After
import io.bluetape4k.testcontainers.graphdb.Neo4jServer
import io.bluetape4k.testcontainers.graphdb.MemgraphServer
import io.bluetape4k.testcontainers.graphdb.PostgreSQLAgeServer
```

### 5.1 호출부 치환 패턴

```kotlin
// Before
val driver = GraphDatabase.driver(Neo4jServer.boltUrl, AuthTokens.none())
// or
val driver = GraphDatabase.driver(Neo4jServer.instance.boltUrl, AuthTokens.none())

// After
val driver = GraphDatabase.driver(Neo4jServer.Launcher.neo4j.boltUrl, AuthTokens.none())
```

**패턴 A — 기존 graph-memgraph 모듈 (ops를 @BeforeAll에서 초기화):**
```kotlin
// Before
private val driver = GraphDatabase.driver(MemgraphServer.boltUrl, AuthTokens.none())
ops = MemgraphGraphOperations(MemgraphServer.driver)

// After — driver 소유권이 테스트 클래스로 이동
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemgraphGraphOperationsTest : AbstractMemgraphTest() {
    private lateinit var driver: Driver

    @BeforeAll
    fun setup() {
        driver = GraphDatabase.driver(MemgraphServer.Launcher.memgraph.boltUrl, AuthTokens.none())
        ops = MemgraphGraphOperations(driver)
    }

    @AfterAll
    fun teardown() {
        driver.close()
    }
}
```

**패턴 B — examples 모듈 (`override val ops = ...` 한 줄 구조):**
```kotlin
// Before
class MemgraphCodeGraphTest : AbstractCodeGraphTest() {
    override val ops = MemgraphGraphOperations(MemgraphServer.driver)
}

// After — val → lateinit var로 변경, @BeforeAll/@AfterAll 추가
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemgraphCodeGraphTest : AbstractCodeGraphTest() {
    private lateinit var driver: Driver
    override lateinit var ops: GraphOperations   // 부모 추상 클래스 타입에 맞게

    @BeforeAll
    fun setup() {
        driver = GraphDatabase.driver(MemgraphServer.Launcher.memgraph.boltUrl, AuthTokens.none())
        ops = MemgraphGraphOperations(driver)
    }

    @AfterAll
    fun teardown() {
        driver.close()
    }
}
```

> ⚠️ `override val ops`를 `override lateinit var ops`로 바꾸려면 부모 추상 클래스의 선언도 `abstract var ops`여야 한다. 부모가 `val`이면 `val`을 유지하면서 `by lazy { ... }` 패턴으로 초기화하고, driver 참조를 companion object나 별도 field에 두어 `@AfterAll`에서 close한다. 구현 전 부모 클래스 선언을 먼저 확인할 것.

```kotlin
// Before
val ds = HikariDataSource(HikariConfig().apply {
    jdbcUrl = PostgreSQLAgeServer.instance.jdbcUrl
    username = PostgreSQLAgeServer.instance.username
    password = PostgreSQLAgeServer.instance.password
})

// After
val ds = HikariDataSource(HikariConfig().apply {
    jdbcUrl = PostgreSQLAgeServer.Launcher.postgresqlAge.jdbcUrl
    username = PostgreSQLAgeServer.Launcher.postgresqlAge.username
    password = PostgreSQLAgeServer.Launcher.postgresqlAge.password
})
```

### 5.2 치환 전략

- `Grep` 툴로 `io.bluetape4k.graph.servers` 문자열을 검색하여 모든 hit를 확인한다.
- `Edit` 툴로 파일 단위 치환 (동일 토큰이 반복 출현하면 `replace_all: true`).
- ⚠️ `PostgreSQLAgeServer.instance` 치환 시 반드시 `PostgreSQLAgeServer.instance`로 앵커링하여 검색/치환. 다른 클래스의 `.instance` 프로퍼티에 영향 미치지 않도록 한다.
- 치환 후 `./gradlew compileTestKotlin`으로 컴파일 검증.

---

## 6. 모듈 삭제

### 6.1 절차

1. 모든 의존처 업데이트가 완료되어 컴파일 및 테스트가 통과함을 확인한다.
2. `settings.gradle.kts` 변경 불필요 — `includeModules("graph", false, false)`가 `graph/` 하위 `build.gradle.kts` 존재 여부로 자동 스캔하므로 디렉토리 삭제로 자동 제외됨.
3. Bash `rm -rf graph/graph-servers` 로 디렉토리 전체를 삭제한다 (Edit/Write 툴은 디렉토리 삭제 불가 — `rm`은 허용된 파일 이동/삭제 명령).
4. 루트 `build.gradle.kts`의 publication 설정에서 `graph-servers` 관련 항목이 있다면 제거한다.
5. `CLAUDE.md`의 Project Structure 섹션 갱신.

### 6.2 CLAUDE.md 갱신 사항

- `graph/graph-servers/` 설명 줄 제거.
- "테스트 패턴" 섹션의 코드 예시를 `io.bluetape4k.testcontainers.graphdb` 기준으로 변경.
- "공유 컨테이너" 예시에서 `Neo4jServer.boltUrl` → `Neo4jServer.Launcher.neo4j.boltUrl` 로 갱신.

---

## 7. 제약 사항 및 작업 순서

### 7.1 제약 사항

- **Worktree 우선**: 메인 작업 트리를 보호하기 위해 별도 worktree를 생성한 후 작업한다 (`superpowers:using-git-worktrees` 참조).
- **의존성 검증 선행**: 첫 변경 모듈에서 `./gradlew :graph-neo4j:compileTestKotlin`으로 신규 API 가용성을 확인 후 다른 모듈로 확산한다.
- **테스트 컨테이너 재사용**: 신규 API도 싱글턴 컨테이너를 제공하므로, 기존 `@TestInstance(PER_CLASS)` 패턴을 유지한다. 라이프사이클 변경 불필요.
- **bluetape4k-patterns 준수**: `requireNotBlank`, `KLogging`, atomicfu 등 기존 규칙을 깨지 않는다 (본 작업은 대부분 import/빌드스크립트 변경이므로 영향 적음).
- **문서 이중화**: 모듈 README가 있는 경우 `README.md`와 `README.ko.md`를 함께 갱신한다. 본 작업에서는 `graph-servers/README.*`는 삭제되며, 의존 모듈의 README에 변경된 의존성이 언급되어 있다면 갱신한다.

### 7.2 작업 순서

1. **Worktree 생성**
   - `git worktree add ../bluetape4k-graph-wt-graph-servers-migration -b feature/graph-servers-migration`
2. **의존성 확인**
   - `Libs.bluetape4k_testcontainers`가 필요한 버전 범위를 포함하는지 확인.
3. **build.gradle.kts 모듈별 수정** (8개 파일)
   - `project(":graph-servers")` 제거 후, §2.1.1 테이블에 명시된 **모듈별 보조 의존성을 추가**한다. 단순 일괄 치환 금지 — `bluetape4k-testcontainers`의 graphdb 보조 의존성은 `compileOnly`이므로 소비자가 명시해야 한다.
4. **Import 및 호출부 치환** (27개 테스트 파일)
   - 백엔드별로 묶어 진행 (Neo4j → Memgraph → Age 순). TinkerGraph는 서버 의존성 없으므로 건너뛴다.
5. **컴파일 검증**
   - `./gradlew compileTestKotlin`으로 전체 컴파일 확인.
6. **단위/통합 테스트 실행**
   - `./gradlew :graph-neo4j:test :graph-memgraph:test :graph-age:test`
   - `./gradlew :code-graph-examples:test :linkedin-graph-examples:test`
   - `./gradlew :graph-spring-boot3-starter:test :graph-spring-boot4-starter:test`
7. **`graph/graph-servers/` 디렉토리 삭제**
   - `settings.gradle.kts` 변경 불필요 (자동 스캔 방식).
   - `rm -rf graph/graph-servers` 실행.
8. **CLAUDE.md 및 기타 문서 갱신**
   - 모듈 구조, 테스트 패턴 예시 반영.
9. **전체 빌드 재검증**
   - `./gradlew clean build -x test && ./gradlew test`
10. **커밋 & PR**
    - 커밋 타입: `refactor:` (`refactor: replace graph-servers module with bluetape4k-testcontainers graphdb`).
    - PR 설명에 본 스펙 문서 경로 첨부.
    - `docs/testlogs/2026-04.md` 상단에 테스트 결과 기록.
11. **Worktree 정리**
    - PR 머지 후 `git worktree remove` 및 브랜치 삭제.

### 7.3 롤백 전략

- 각 단계를 독립 커밋으로 분리하여 문제 발생 시 특정 커밋만 revert 가능하도록 한다.
- worktree 작업 중 compile 실패가 발생하면 해당 모듈 변경을 우선 되돌리고, 원인 분석 후 재시도.

---

## 8. 검증 체크리스트

- [ ] `graph/graph-servers/` 디렉토리가 완전히 삭제되었다.
- [ ] `settings.gradle.kts` 변경 없음 확인 (`includeModules` 자동 스캔으로 디렉토리 삭제만으로 충분).
- [ ] 모든 영향 모듈의 `build.gradle.kts`에 `project(":graph-servers")`가 존재하지 않는다.
- [ ] 전체 테스트 코드에서 `io.bluetape4k.graph.servers` import가 0건이다.
- [ ] Memgraph 테스트에서 driver lifecycle(`@BeforeAll` 생성, `@AfterAll close()`)이 올바르게 구성되었다.
- [ ] `./gradlew build` 성공.
- [ ] `./gradlew test` 성공 (Testcontainers 기반 테스트 포함).
- [ ] 최종 stale 참조 검색 — **구 API만** 잡도록 좁힌 패턴으로 0건 확인:
  ```
  rg -n 'project\(":graph-servers"\)|io\.bluetape4k\.graph\.servers|Neo4jServer\.(boltUrl|instance)|MemgraphServer\.(boltUrl|driver|instance)|PostgreSQLAgeServer\.instance' \
     README*.md CLAUDE.md TODO.md \
     examples graph spring-boot3 spring-boot4
  ```
  > 신규 API(`Neo4jServer.Launcher`, `MemgraphServer.Launcher` 등)는 마이그레이션 후에도 정상 사용되므로 패턴에서 제외. `docs/superpowers/`와 `CHANGELOG.md`는 과거 기록으로 제외.
- [ ] `CLAUDE.md`, `README.md`, `README.ko.md`, `examples/*/README*.md`, `TODO.md`에서 구 API 참조 정리 완료.
- [ ] `docs/testlogs/2026-04.md` 상단에 테스트 결과 기록.
- [ ] PR 생성 및 리뷰 요청 완료.

---

## 9. 리스크 및 대응

| 리스크 | 영향 | 구체적 변경점 | 대응 |
|-------|------|-------------|------|
| **이미지 태그 고정** — 기존은 `latest`, 신규는 고정 버전 | 동작 차이 가능 | Neo4j: `neo4j:5` → `neo4j:5.26.24`; Memgraph: `memgraph:latest` → `memgraph:3.9.0`; AGE: `apache/age:latest` → `release_PG17_1.6.0` | 첫 모듈 변경 후 통합 테스트 실행. 동작 차이 발견 시 bluetape4k-testcontainers issue 등록. |
| **AGE DB 이름 변경** — 기존 `age_test`, 신규 `test` | AGE 테스트 실패 | `withDatabaseName("age_test")` → `withDatabaseName("test")` | 마이그레이션 전 테스트 코드에서 `"age_test"` 하드코딩 여부 검색; HikariCP `connectionInitSql` 중복 `LOAD 'age'` 존재 여부도 확인. |
| **AGE 초기화 스크립트 차이** — 신규는 3단계(CREATE/LOAD/SET search_path) | 잘못 제거하면 AGE 함수 인식 실패 | 기존 1단계(`CREATE EXTENSION`) → 신규 3단계 | **`connectionInitSql` 유지 필수**: 신규 `PostgreSQLAgeServer.start()`의 LOAD/SET은 컨테이너 시작 시 사용하는 단일 연결에만 적용됨. HikariCP 풀의 각 connection에는 `connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, ..."` 가 여전히 필요하므로 제거하지 않는다. |
| `Libs.bluetape4k_testcontainers` 버전이 `graphdb` 패키지 포함 여부 | 컴파일 실패 | - | 첫 컴파일 검증 단계에서 확인. 미포함 시 `Libs.kt` 버전 업데이트. |
| Spring Boot starter 테스트의 auto-configuration 설정 | 테스트 실패 | `@DynamicPropertySource` URL 참조 변경 | `Neo4jServer.boltUrl` → `Neo4jServer.Launcher.neo4j.boltUrl` 등 URL 접근 패턴만 갱신. |

---

## 10. 참고 자료

- 기존 모듈: `graph/graph-servers/src/main/kotlin/io/bluetape4k/graph/servers/`
- 신규 모듈: `bluetape4k-projects/testing/testcontainers/src/main/kotlin/io/bluetape4k/testcontainers/graphdb/`
- 의존성 정의: `buildSrc/src/main/kotlin/Libs.kt` — `bluetape4k_testcontainers`
- 기존 테스트 패턴 문서: `/Users/debop/work/bluetape4k/bluetape4k-graph/CLAUDE.md`
- 관련 스킬: `superpowers:using-git-worktrees`, `bluetape4k-patterns`, `ecc-kotlin-testing`
