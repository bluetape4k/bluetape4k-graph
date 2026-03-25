# Examples 모듈 통합 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 8개로 분산된 examples 모듈(`code-graph-{age,neo4j,memgraph,tinkerpop}`, `linkedin-graph-{age,neo4j,memgraph,tinkerpop}`)을 `code-graph-examples`, `linkedin-graph-examples` 2개로 통합하고, 추상 테스트 클래스 패턴과 동기/suspend 양쪽 테스트를 갖춘다.

**Architecture:** 각 통합 모듈은 공통 Service/Schema를 `main`에, `AbstractXxxTest`(동기) + `AbstractXxxSuspendTest`(코루틴)를 `test`에 둔다. 백엔드별 구체 클래스는 `ops`(GraphOperations / GraphSuspendOperations)와 서버 setup만 오버라이드한다.

**Tech Stack:** Kotlin 2.3, JUnit 5 (`@TestInstance(PER_CLASS)`), Kotest Kluent assertions, kotlinx-coroutines-test (`runTest`), Testcontainers (Neo4j, Memgraph, PostgreSQL+AGE)

---

## 파일 맵

### `examples/code-graph-examples/`
```
build.gradle.kts
src/main/kotlin/io/bluetape4k/graph/examples/code/
  schema/CodeGraphSchema.kt               ← code-graph-age에서 그대로 복사 (4개 동일)
  service/CodeGraphService.kt             ← code-graph-age에서 그대로 복사 (4개 동일)
  service/CodeGraphSuspendService.kt      ← code-graph-age에서 그대로 복사
src/test/kotlin/io/bluetape4k/graph/examples/code/
  AbstractCodeGraphTest.kt                ← 신규 (공통 동기 테스트 6개)
  AbstractCodeGraphSuspendTest.kt         ← 신규 (공통 suspend 테스트 6개)
  AgeCodeGraphTest.kt                     ← 신규 (구체 클래스)
  Neo4jCodeGraphTest.kt                   ← 신규
  MemgraphCodeGraphTest.kt                ← 신규
  TinkerGraphCodeGraphTest.kt             ← 신규
  AgeCodeGraphSuspendTest.kt              ← 신규
  Neo4jCodeGraphSuspendTest.kt            ← 신규
  MemgraphCodeGraphSuspendTest.kt         ← 신규
  TinkerGraphCodeGraphSuspendTest.kt      ← 신규
```

### `examples/linkedin-graph-examples/`
```
build.gradle.kts
src/main/kotlin/io/bluetape4k/graph/examples/linkedin/
  schema/LinkedInSchema.kt                ← linkedin-graph-age에서 복사 (4개 동일)
  service/LinkedInGraphService.kt         ← linkedin-graph-age에서 복사 (4개 동일)
  service/LinkedInGraphSuspendService.kt  ← 신규 작성
src/test/kotlin/io/bluetape4k/graph/examples/linkedin/
  AbstractLinkedInGraphTest.kt            ← 신규 (공통 동기 테스트 5개)
  AbstractLinkedInGraphSuspendTest.kt     ← 신규 (공통 suspend 테스트 5개)
  AgeLinkedInGraphTest.kt                 ← 신규
  Neo4jLinkedInGraphTest.kt               ← 신규
  MemgraphLinkedInGraphTest.kt            ← 신규
  TinkerGraphLinkedInGraphTest.kt         ← 신규
  AgeLinkedInGraphSuspendTest.kt          ← 신규
  Neo4jLinkedInGraphSuspendTest.kt        ← 신규
  MemgraphLinkedInGraphSuspendTest.kt     ← 신규
  TinkerGraphLinkedInGraphSuspendTest.kt  ← 신규
```

---

## Task 1: `code-graph-examples` 모듈 뼈대 + 공통 소스 이관

**Files:**
- Create: `examples/code-graph-examples/build.gradle.kts`
- Create: `examples/code-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/code/schema/CodeGraphSchema.kt`
- Create: `examples/code-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/code/service/CodeGraphService.kt`
- Create: `examples/code-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/code/service/CodeGraphSuspendService.kt`

- [ ] **Step 1: 디렉토리 구조 생성**

```bash
mkdir -p examples/code-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/code/schema
mkdir -p examples/code-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/code/service
mkdir -p examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code
```

- [ ] **Step 2: `build.gradle.kts` 작성**

```kotlin
// examples/code-graph-examples/build.gradle.kts
dependencies {
    implementation(project(":graph-core"))
    implementation(project(":graph-age"))
    implementation(project(":graph-neo4j"))
    implementation(project(":graph-memgraph"))
    implementation(project(":graph-tinkerpop"))
    testImplementation(project(":graph-servers"))

    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.kotlinx_coroutines_core)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers_neo4j)
    testImplementation(Libs.testcontainers_postgresql)
    testImplementation(Libs.hikaricp)
    testImplementation(Libs.kotlinx_coroutines_test)
    testImplementation(Libs.testcontainers)
}
```

- [ ] **Step 3: `CodeGraphSchema.kt` 복사**

`examples/code-graph-age/src/main/kotlin/.../schema/CodeGraphSchema.kt`를 그대로 복사. (4개 백엔드 모두 동일)

- [ ] **Step 4: `CodeGraphService.kt` 복사**

`examples/code-graph-age/src/main/kotlin/.../service/CodeGraphService.kt`를 그대로 복사.

- [ ] **Step 5: `CodeGraphSuspendService.kt` 복사**

`examples/code-graph-age/src/main/kotlin/.../service/CodeGraphSuspendService.kt`를 그대로 복사.

- [ ] **Step 6: 빌드 확인**

```bash
./gradlew :code-graph-examples:build -x test
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add examples/code-graph-examples/
git commit -m "feat: add code-graph-examples module with common sources"
```

---

## Task 2: `AbstractCodeGraphTest` + 4개 동기 구체 클래스 작성

**Files:**
- Create: `examples/code-graph-examples/src/test/kotlin/.../code/AbstractCodeGraphTest.kt`
- Create: `examples/code-graph-examples/src/test/kotlin/.../code/AgeCodeGraphTest.kt`
- Create: `examples/code-graph-examples/src/test/kotlin/.../code/Neo4jCodeGraphTest.kt`
- Create: `examples/code-graph-examples/src/test/kotlin/.../code/MemgraphCodeGraphTest.kt`
- Create: `examples/code-graph-examples/src/test/kotlin/.../code/TinkerGraphCodeGraphTest.kt`

- [ ] **Step 1: `AbstractCodeGraphTest.kt` 작성**

```kotlin
package io.bluetape4k.graph.examples.code

import io.bluetape4k.graph.examples.code.service.CodeGraphService
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeEmpty
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractCodeGraphTest {

    companion object : KLogging()

    protected abstract val ops: GraphOperations
    protected open val graphName: String = "code_graph"
    protected val service: CodeGraphService by lazy { CodeGraphService(ops, graphName) }

    @BeforeEach
    open fun cleanGraph() {
        if (ops.graphExists(graphName)) ops.dropGraph(graphName)
        service.initialize()
    }

    @Test
    fun `모듈 추가 및 의존성 관계 구성`() {
        val core = service.addModule("core", "graph/graph-core", "1.0.0")
        val middle = service.addModule("middle", "graph/middle", "1.0.0")
        val app = service.addModule("app", "examples/app", "1.0.0")

        service.addDependency(middle.id, core.id, "compile")
        service.addDependency(app.id, middle.id, "compile")

        val middleDeps = service.getDependencies(middle.id)
        middleDeps.shouldNotBeEmpty()

        val appDeps = service.getTransitiveDependencies(app.id, maxDepth = 3)
        appDeps.shouldNotBeEmpty()
        appDeps.forEach { log.debug { "vertex=$it" } }
    }

    @Test
    fun `의존성 경로 탐색`() {
        val core = service.addModule("core", path = "", version = "1.0.0")
        val mid = service.addModule("middle", path = "", version = "1.0.0")
        val top = service.addModule("top", path = "", version = "1.0.0")

        service.addDependency(mid.id, core.id)
        service.addDependency(top.id, mid.id)

        val path = service.findDependencyPath(top.id, core.id)
        path.shouldNotBeNull()
        path.length shouldBeGreaterThan 0
        path.vertices.forEach { log.debug { "vertex=$it" } }
    }

    @Test
    fun `영향 범위 분석 - 역방향 탐색`() {
        val core = service.addModule("core", path = "", version = "1.0.0")
        val moduleA = service.addModule("moduleA", path = "", version = "1.0.0")
        val moduleB = service.addModule("moduleB", path = "", version = "1.0.0")

        service.addDependency(moduleA.id, core.id)
        service.addDependency(moduleB.id, core.id)

        val impacted = service.getImpactedModules(core.id, depth = 1)
        impacted.shouldNotBeEmpty()
        impacted.forEach { log.debug { "vertex=$it" } }
    }

    @Test
    fun `클래스 상속 계층 탐색`() {
        val baseClass = service.addClass("Animal", "io.example.Animal")
        val midClass = service.addClass("Mammal", "io.example.Mammal")
        val leafClass = service.addClass("Dog", "io.example.Dog")

        service.addExtends(midClass.id, baseClass.id)
        service.addExtends(leafClass.id, midClass.id)

        val chain = service.getInheritanceChain(leafClass.id, depth = 3)
        chain.shouldNotBeEmpty()
        chain.forEach { log.debug { "vertex=$it" } }
    }

    @Test
    fun `함수 호출 체인 분석`() {
        val funcA = service.addFunction("processOrder", "fun processOrder(orderId: Long)")
        val funcB = service.addFunction("validateOrder", "fun validateOrder(orderId: Long)")
        val funcC = service.addFunction("saveOrder", "fun saveOrder(order: Order)")

        service.addCall(funcA.id, funcB.id, callCount = 1)
        service.addCall(funcA.id, funcC.id, callCount = 1)
        service.addCall(funcB.id, funcC.id, callCount = 2)

        val callChain = service.getCallChain(funcA.id, maxDepth = 3)
        callChain.shouldNotBeEmpty()
        callChain.forEach { log.debug { "vertex=$it" } }
    }

    @Test
    fun `의존성 없는 경우 경로 null`() {
        val isolated = service.addModule("isolated", path = "", version = "1.0.0")
        val other = service.addModule("other", path = "", version = "1.0.0")

        val path = service.findDependencyPath(isolated.id, other.id)
        path.shouldBeNull()
    }
}
```

- [ ] **Step 2: `Neo4jCodeGraphTest.kt` 작성**

```kotlin
package io.bluetape4k.graph.examples.code

import io.bluetape4k.graph.neo4j.Neo4jGraphOperations
import io.bluetape4k.graph.servers.Neo4jServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Neo4jCodeGraphTest : AbstractCodeGraphTest() {
    private val driver: Driver = GraphDatabase.driver(Neo4jServer.instance.boltUrl, AuthTokens.none())
    override val ops = Neo4jGraphOperations(driver)

    @AfterAll
    fun teardown() { driver.close() }
}
```

- [ ] **Step 3: `MemgraphCodeGraphTest.kt` 작성**

```kotlin
package io.bluetape4k.graph.examples.code

import io.bluetape4k.graph.memgraph.MemgraphGraphOperations
import io.bluetape4k.graph.servers.MemgraphServer
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemgraphCodeGraphTest : AbstractCodeGraphTest() {
    override val ops = MemgraphGraphOperations(MemgraphServer.driver)
}
```

- [ ] **Step 4: `TinkerGraphCodeGraphTest.kt` 작성**

```kotlin
package io.bluetape4k.graph.examples.code

import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TinkerGraphCodeGraphTest : AbstractCodeGraphTest() {
    override val ops = TinkerGraphOperations()
    override val graphName = "default"
}
```

- [ ] **Step 5: `AgeCodeGraphTest.kt` 작성**

```kotlin
package io.bluetape4k.graph.examples.code

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.graph.servers.PostgreSQLAgeServer
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgeCodeGraphTest : AbstractCodeGraphTest() {
    override val graphName = "code_test"

    private lateinit var dataSource: HikariDataSource
    override lateinit var ops: AgeGraphOperations

    @BeforeAll
    fun startServer() {
        // PostgreSQLAgeServer.instance는 lazy singleton — 자동 시작, 별도 stop 불필요
        val server = PostgreSQLAgeServer.instance
        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = server.jdbcUrl
            username = server.username
            password = server.password
            driverClassName = "org.postgresql.Driver"
            connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
            maximumPoolSize = 5
        })
        ops = AgeGraphOperations(Database.connect(dataSource), graphName)
    }

    @AfterAll
    fun stopServer() { dataSource.close() }
}
```

- [ ] **Step 6: 테스트 실행 확인**

```bash
./gradlew :code-graph-examples:test --tests "io.bluetape4k.graph.examples.code.*CodeGraphTest"
```
Expected: 모든 테스트 PASS (4개 클래스 × 6개 테스트 = 24개)

- [ ] **Step 7: Commit**

```bash
git add examples/code-graph-examples/src/test/
git commit -m "feat: add AbstractCodeGraphTest and 4 backend concrete test classes"
```

---

## Task 3: `AbstractCodeGraphSuspendTest` + 4개 suspend 구체 클래스 작성

**Files:**
- Create: `examples/code-graph-examples/src/test/kotlin/.../code/AbstractCodeGraphSuspendTest.kt`
- Create: `.../{Age,Neo4j,Memgraph,TinkerGraph}CodeGraphSuspendTest.kt`

- [ ] **Step 1: `AbstractCodeGraphSuspendTest.kt` 작성**

```kotlin
package io.bluetape4k.graph.examples.code

import io.bluetape4k.graph.examples.code.service.CodeGraphSuspendService
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldNotBeEmpty
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractCodeGraphSuspendTest {

    companion object : KLogging()

    protected abstract val ops: GraphSuspendOperations
    protected open val graphName: String = "code_graph"
    protected val service: CodeGraphSuspendService by lazy { CodeGraphSuspendService(ops, graphName) }

    @BeforeEach
    fun cleanGraph() = runBlocking {
        if (ops.graphExists(graphName)) ops.dropGraph(graphName)
        service.initialize()
    }

    @Test
    fun `모듈 추가 및 의존성 관계 구성`() = runTest {
        val core = service.addModule("core", "graph/graph-core", "1.0.0")
        val middle = service.addModule("middle", "graph/middle", "1.0.0")
        val app = service.addModule("app", "examples/app", "1.0.0")

        service.addDependency(middle.id, core.id, "compile")
        service.addDependency(app.id, middle.id, "compile")

        val middleDeps = service.getDependencies(middle.id).toList()
        middleDeps.shouldNotBeEmpty()

        val appDeps = service.getTransitiveDependencies(app.id, maxDepth = 3).toList()
        appDeps.shouldNotBeEmpty()
        appDeps.forEach { log.debug { "vertex=$it" } }
    }

    @Test
    fun `의존성 경로 탐색`() = runTest {
        val core = service.addModule("core", path = "", version = "1.0.0")
        val mid = service.addModule("middle", path = "", version = "1.0.0")
        val top = service.addModule("top", path = "", version = "1.0.0")

        service.addDependency(mid.id, core.id)
        service.addDependency(top.id, mid.id)

        val path = service.findDependencyPath(top.id, core.id)
        path.shouldNotBeNull()
        path.length shouldBeGreaterThan 0
        path.vertices.forEach { log.debug { "vertex=$it" } }
    }

    @Test
    fun `영향 범위 분석 - 역방향 탐색`() = runTest {
        val core = service.addModule("core", path = "", version = "1.0.0")
        val moduleA = service.addModule("moduleA", path = "", version = "1.0.0")
        val moduleB = service.addModule("moduleB", path = "", version = "1.0.0")

        service.addDependency(moduleA.id, core.id)
        service.addDependency(moduleB.id, core.id)

        val impacted = service.getImpactedModules(core.id, depth = 1).toList()
        impacted.shouldNotBeEmpty()
        impacted.forEach { log.debug { "vertex=$it" } }
    }

    @Test
    fun `클래스 상속 계층 탐색`() = runTest {
        val baseClass = service.addClass("Animal", "io.example.Animal")
        val midClass = service.addClass("Mammal", "io.example.Mammal")
        val leafClass = service.addClass("Dog", "io.example.Dog")

        service.addExtends(midClass.id, baseClass.id)
        service.addExtends(leafClass.id, midClass.id)

        val chain = service.getInheritanceChain(leafClass.id, depth = 3).toList()
        chain.shouldNotBeEmpty()
        chain.forEach { log.debug { "vertex=$it" } }
    }

    @Test
    fun `함수 호출 체인 분석`() = runTest {
        val funcA = service.addFunction("processOrder", "fun processOrder(orderId: Long)")
        val funcB = service.addFunction("validateOrder", "fun validateOrder(orderId: Long)")
        val funcC = service.addFunction("saveOrder", "fun saveOrder(order: Order)")

        service.addCall(funcA.id, funcB.id, callCount = 1)
        service.addCall(funcA.id, funcC.id, callCount = 1)
        service.addCall(funcB.id, funcC.id, callCount = 2)

        val callChain = service.getCallChain(funcA.id, maxDepth = 3).toList()
        callChain.shouldNotBeEmpty()
        callChain.forEach { log.debug { "vertex=$it" } }
    }

    @Test
    fun `의존성 없는 경우 경로 null`() = runTest {
        val isolated = service.addModule("isolated", path = "", version = "1.0.0")
        val other = service.addModule("other", path = "", version = "1.0.0")

        val path = service.findDependencyPath(isolated.id, other.id)
        path.shouldBeNull()
    }
}
```

- [ ] **Step 2: 4개 suspend 구체 클래스 작성 — Neo4j**

```kotlin
package io.bluetape4k.graph.examples.code

import io.bluetape4k.graph.neo4j.Neo4jGraphSuspendOperations
import io.bluetape4k.graph.servers.Neo4jServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Neo4jCodeGraphSuspendTest : AbstractCodeGraphSuspendTest() {
    private val driver: Driver = GraphDatabase.driver(Neo4jServer.instance.boltUrl, AuthTokens.none())
    override val ops = Neo4jGraphSuspendOperations(driver)

    @AfterAll
    fun teardown() { driver.close() }
}
```

- [ ] **Step 3: suspend 구체 클래스 — Memgraph**

```kotlin
package io.bluetape4k.graph.examples.code

import io.bluetape4k.graph.memgraph.MemgraphGraphSuspendOperations
import io.bluetape4k.graph.servers.MemgraphServer
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemgraphCodeGraphSuspendTest : AbstractCodeGraphSuspendTest() {
    override val ops = MemgraphGraphSuspendOperations(MemgraphServer.driver)
}
```

- [ ] **Step 4: suspend 구체 클래스 — TinkerPop**

```kotlin
package io.bluetape4k.graph.examples.code

import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TinkerGraphCodeGraphSuspendTest : AbstractCodeGraphSuspendTest() {
    override val ops = TinkerGraphSuspendOperations()
    override val graphName = "default"
}
```

- [ ] **Step 5: suspend 구체 클래스 — AGE**

```kotlin
package io.bluetape4k.graph.examples.code

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.graph.age.AgeGraphSuspendOperations
import io.bluetape4k.graph.servers.PostgreSQLAgeServer
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgeCodeGraphSuspendTest : AbstractCodeGraphSuspendTest() {
    override val graphName = "code_test_suspend"

    private lateinit var dataSource: HikariDataSource
    override lateinit var ops: AgeGraphSuspendOperations

    @BeforeAll
    fun startServer() {
        // PostgreSQLAgeServer.instance는 lazy singleton — 자동 시작, 별도 stop 불필요
        val server = PostgreSQLAgeServer.instance
        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = server.jdbcUrl
            username = server.username
            password = server.password
            driverClassName = "org.postgresql.Driver"
            connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
            maximumPoolSize = 5
        })
        ops = AgeGraphSuspendOperations(Database.connect(dataSource), graphName)
    }

    @AfterAll
    fun stopServer() { dataSource.close() }
}
```

- [ ] **Step 6: 전체 테스트 실행**

```bash
./gradlew :code-graph-examples:test
```
Expected: 8개 클래스 × 6개 테스트 = 48개 PASS

- [ ] **Step 7: Commit**

```bash
git add examples/code-graph-examples/src/test/
git commit -m "feat: add AbstractCodeGraphSuspendTest and suspend concrete test classes"
```

---

## Task 4: `linkedin-graph-examples` 모듈 뼈대 + 공통 소스 이관 + `LinkedInGraphSuspendService` 신규 작성

**Files:**
- Create: `examples/linkedin-graph-examples/build.gradle.kts`
- Create: `.../linkedin/schema/LinkedInSchema.kt`
- Create: `.../linkedin/service/LinkedInGraphService.kt`
- Create: `.../linkedin/service/LinkedInGraphSuspendService.kt` ← **신규**

- [ ] **Step 1: 디렉토리 구조 생성**

```bash
mkdir -p examples/linkedin-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/linkedin/schema
mkdir -p examples/linkedin-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/linkedin/service
mkdir -p examples/linkedin-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/linkedin
```

- [ ] **Step 2: `build.gradle.kts` 작성 (`code-graph-examples`와 동일)**

```kotlin
// examples/linkedin-graph-examples/build.gradle.kts
dependencies {
    implementation(project(":graph-core"))
    implementation(project(":graph-age"))
    implementation(project(":graph-neo4j"))
    implementation(project(":graph-memgraph"))
    implementation(project(":graph-tinkerpop"))
    testImplementation(project(":graph-servers"))

    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.kotlinx_coroutines_core)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers_neo4j)
    testImplementation(Libs.testcontainers_postgresql)
    testImplementation(Libs.hikaricp)
    testImplementation(Libs.kotlinx_coroutines_test)
    testImplementation(Libs.testcontainers)
}
```

- [ ] **Step 3: `LinkedInSchema.kt`, `LinkedInGraphService.kt` 복사**

`linkedin-graph-age`에서 그대로 복사. (4개 백엔드 동일)

- [ ] **Step 4: `LinkedInGraphSuspendService.kt` 신규 작성**

`LinkedInGraphService.kt`의 모든 메서드를 suspend/Flow로 변환.

```kotlin
package io.bluetape4k.graph.examples.linkedin.service

import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.GraphElementId
import io.bluetape4k.graph.model.GraphVertex
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.model.PathOptions
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.coroutines.KLoggingChannel
import kotlinx.coroutines.flow.Flow

class LinkedInGraphSuspendService(
    private val ops: GraphSuspendOperations,
    private val graphName: String = "linkedin",
) {
    companion object : KLoggingChannel()

    suspend fun initialize() {
        if (!ops.graphExists(graphName)) {
            ops.createGraph(graphName)
        }
    }

    suspend fun addPerson(name: String, title: String = "", company: String = "", location: String = ""): GraphVertex =
        ops.createVertex("Person", mapOf("name" to name, "title" to title, "company" to company, "location" to location))

    suspend fun addCompany(name: String, industry: String = "", location: String = ""): GraphVertex =
        ops.createVertex("Company", mapOf("name" to name, "industry" to industry, "location" to location))

    suspend fun connect(personId1: GraphElementId, personId2: GraphElementId, since: String = "", strength: Int = 5) {
        ops.createEdge(personId1, personId2, "KNOWS", mapOf("since" to since, "strength" to strength))
        ops.createEdge(personId2, personId1, "KNOWS", mapOf("since" to since, "strength" to strength))
    }

    suspend fun addWorkExperience(personId: GraphElementId, companyId: GraphElementId, role: String, isCurrent: Boolean = false) {
        ops.createEdge(personId, companyId, "WORKS_AT", mapOf("role" to role, "isCurrent" to isCurrent))
    }

    suspend fun follow(followerId: GraphElementId, targetId: GraphElementId) {
        ops.createEdge(followerId, targetId, "FOLLOWS", emptyMap())
    }

    fun getDirectConnections(personId: GraphElementId): Flow<GraphVertex> =
        ops.neighbors(personId, NeighborOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING, maxDepth = 1))

    fun getConnectionsWithinDegree(personId: GraphElementId, degree: Int): Flow<GraphVertex> =
        ops.neighbors(personId, NeighborOptions(edgeLabel = "KNOWS", direction = Direction.OUTGOING, maxDepth = degree))

    suspend fun findConnectionPath(fromId: GraphElementId, toId: GraphElementId) =
        ops.shortestPath(fromId, toId, PathOptions(edgeLabel = "KNOWS", maxDepth = 6))

    fun findEmployees(companyId: GraphElementId): Flow<GraphVertex> =
        ops.neighbors(companyId, NeighborOptions(edgeLabel = "WORKS_AT", direction = Direction.INCOMING, maxDepth = 1))

    fun findAllConnectionPaths(fromId: GraphElementId, toId: GraphElementId) =
        ops.allPaths(fromId, toId, PathOptions(edgeLabel = "KNOWS", maxDepth = 3))

    fun findPersonByName(name: String): Flow<GraphVertex> =
        ops.findVerticesByLabel("Person", mapOf("name" to name))
}
```

- [ ] **Step 5: 빌드 확인**

```bash
./gradlew :linkedin-graph-examples:build -x test
```
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add examples/linkedin-graph-examples/
git commit -m "feat: add linkedin-graph-examples module with common sources and LinkedInGraphSuspendService"
```

---

## Task 5: `AbstractLinkedInGraphTest` + 4개 동기 구체 클래스, `AbstractLinkedInGraphSuspendTest` + 4개 suspend 구체 클래스

**Files:**
- Create: `.../linkedin/AbstractLinkedInGraphTest.kt`
- Create: `.../linkedin/AbstractLinkedInGraphSuspendTest.kt`
- Create: `.../{Age,Neo4j,Memgraph,TinkerGraph}LinkedInGraphTest.kt` (×8)

- [ ] **Step 1: `AbstractLinkedInGraphTest.kt` 작성**

```kotlin
package io.bluetape4k.graph.examples.linkedin

import io.bluetape4k.graph.examples.linkedin.service.LinkedInGraphService
import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.repository.GraphOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldNotBeEmpty
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractLinkedInGraphTest {

    companion object : KLogging()

    protected abstract val ops: GraphOperations
    protected open val graphName: String = "linkedin_test"
    protected val service: LinkedInGraphService by lazy { LinkedInGraphService(ops, graphName) }

    @BeforeEach
    open fun setup() {
        if (ops.graphExists(graphName)) ops.dropGraph(graphName)
        service.initialize()
    }

    @Test
    fun `사람 추가 및 인맥 연결`() {
        val alice = service.addPerson("Alice", "Software Engineer", "TechCorp", "Seoul")
        val bob = service.addPerson("Bob", "Product Manager", "StartupXYZ", "Busan")

        alice.id.shouldNotBeNull()
        bob.id.shouldNotBeNull()

        service.connect(alice.id, bob.id, since = "2023-01-01", strength = 8)

        val connections = service.getDirectConnections(alice.id)
        connections.shouldNotBeEmpty()
        connections.forEach { log.debug { "vertex=$it" } }
    }

    @Test
    fun `최단 인맥 경로 탐색 - 6단계 분리`() {
        val alice = service.addPerson("Alice", "Engineer", "A", "Seoul")
        val bob = service.addPerson("Bob", "Manager", "B", "Seoul")
        val carol = service.addPerson("Carol", "Designer", "C", "Seoul")
        val dave = service.addPerson("Dave", "CTO", "D", "Seoul")

        service.connect(alice.id, bob.id)
        service.connect(bob.id, carol.id)
        service.connect(carol.id, dave.id)

        val path = service.findConnectionPath(alice.id, dave.id)
        path.shouldNotBeNull()
        path.length shouldBeGreaterThan 0
        path.vertices.forEach { log.debug { "vertex=$it" } }
    }

    @Test
    fun `2촌 인맥 탐색`() {
        val alice = service.addPerson("Alice", "Engineer", "A", "Seoul")
        val bob = service.addPerson("Bob", "Manager", "B", "Seoul")
        val carol = service.addPerson("Carol", "Designer", "C", "Seoul")

        service.connect(alice.id, bob.id)
        service.connect(bob.id, carol.id)

        val secondDegree = service.getConnectionsWithinDegree(alice.id, degree = 2)
        secondDegree.shouldNotBeEmpty()
        secondDegree.forEach { log.debug { "vertex=$it" } }
    }

    @Test
    fun `회사 추가 및 재직자 조회`() {
        val alice = service.addPerson("Alice", "Engineer", "TechCorp", "Seoul")
        val bob = service.addPerson("Bob", "Designer", "TechCorp", "Seoul")
        val techCorp = service.addCompany("TechCorp", "Technology", "Seoul")

        service.addWorkExperience(alice.id, techCorp.id, "Software Engineer", isCurrent = true)
        service.addWorkExperience(bob.id, techCorp.id, "Designer", isCurrent = true)

        val employees = service.findEmployees(techCorp.id)
        employees.shouldNotBeEmpty()
        employees.forEach { log.debug { "vertex=$it" } }
    }

    @Test
    fun `팔로우 관계 생성`() {
        val alice = service.addPerson("Alice", "Influencer", "A", "Seoul")
        val bob = service.addPerson("Bob", "Fan", "B", "Seoul")

        service.follow(bob.id, alice.id)

        val followers = ops.neighbors(
            alice.id,
            NeighborOptions(edgeLabel = "FOLLOWS", direction = Direction.INCOMING, maxDepth = 1)
        )
        followers.shouldNotBeEmpty()
        followers.forEach { log.debug { "vertex=$it" } }
    }
}
```

- [ ] **Step 2: 4개 동기 구체 클래스 작성 (Neo4j, Memgraph, TinkerPop, AGE)**

Neo4j:
```kotlin
package io.bluetape4k.graph.examples.linkedin

import io.bluetape4k.graph.neo4j.Neo4jGraphOperations
import io.bluetape4k.graph.servers.Neo4jServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Neo4jLinkedInGraphTest : AbstractLinkedInGraphTest() {
    private val driver: Driver = GraphDatabase.driver(Neo4jServer.instance.boltUrl, AuthTokens.none())
    override val ops = Neo4jGraphOperations(driver)

    @AfterAll fun teardown() { driver.close() }
}
```

Memgraph:
```kotlin
package io.bluetape4k.graph.examples.linkedin

import io.bluetape4k.graph.memgraph.MemgraphGraphOperations
import io.bluetape4k.graph.servers.MemgraphServer
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemgraphLinkedInGraphTest : AbstractLinkedInGraphTest() {
    override val ops = MemgraphGraphOperations(MemgraphServer.driver)
}
```

TinkerPop:
```kotlin
package io.bluetape4k.graph.examples.linkedin

import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TinkerGraphLinkedInGraphTest : AbstractLinkedInGraphTest() {
    override val ops = TinkerGraphOperations()
    override val graphName = "default"
}
```

AGE:
```kotlin
package io.bluetape4k.graph.examples.linkedin

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.graph.age.AgeGraphOperations
import io.bluetape4k.graph.servers.PostgreSQLAgeServer
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgeLinkedInGraphTest : AbstractLinkedInGraphTest() {
    override val graphName = "linkedin_test"
    private lateinit var dataSource: HikariDataSource
    override lateinit var ops: AgeGraphOperations

    @BeforeAll
    fun startServer() {
        // PostgreSQLAgeServer.instance는 lazy singleton — 자동 시작, 별도 stop 불필요
        val server = PostgreSQLAgeServer.instance
        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = server.jdbcUrl
            username = server.username
            password = server.password
            driverClassName = "org.postgresql.Driver"
            connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
            maximumPoolSize = 5
        })
        ops = AgeGraphOperations(Database.connect(dataSource), graphName)
    }
    @AfterAll fun stopServer() { dataSource.close() }
}
```

- [ ] **Step 3: `AbstractLinkedInGraphSuspendTest.kt` 작성**

```kotlin
package io.bluetape4k.graph.examples.linkedin

import io.bluetape4k.graph.examples.linkedin.service.LinkedInGraphSuspendService
import io.bluetape4k.graph.model.Direction
import io.bluetape4k.graph.model.NeighborOptions
import io.bluetape4k.graph.repository.GraphSuspendOperations
import io.bluetape4k.logging.KLogging
import io.bluetape4k.logging.debug
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.amshove.kluent.shouldBeGreaterThan
import org.amshove.kluent.shouldNotBeEmpty
import org.amshove.kluent.shouldNotBeNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractLinkedInGraphSuspendTest {

    companion object : KLogging()

    protected abstract val ops: GraphSuspendOperations
    protected open val graphName: String = "linkedin_test"
    protected val service: LinkedInGraphSuspendService by lazy { LinkedInGraphSuspendService(ops, graphName) }

    @BeforeEach
    fun setup() = runBlocking {
        if (ops.graphExists(graphName)) ops.dropGraph(graphName)
        service.initialize()
    }

    @Test
    fun `사람 추가 및 인맥 연결`() = runTest {
        val alice = service.addPerson("Alice", "Software Engineer", "TechCorp", "Seoul")
        val bob = service.addPerson("Bob", "Product Manager", "StartupXYZ", "Busan")

        alice.id.shouldNotBeNull()
        bob.id.shouldNotBeNull()

        service.connect(alice.id, bob.id, since = "2023-01-01", strength = 8)

        val connections = service.getDirectConnections(alice.id).toList()
        connections.shouldNotBeEmpty()
        connections.forEach { log.debug { "vertex=$it" } }
    }

    @Test
    fun `최단 인맥 경로 탐색 - 6단계 분리`() = runTest {
        val alice = service.addPerson("Alice", "Engineer", "A", "Seoul")
        val bob = service.addPerson("Bob", "Manager", "B", "Seoul")
        val carol = service.addPerson("Carol", "Designer", "C", "Seoul")
        val dave = service.addPerson("Dave", "CTO", "D", "Seoul")

        service.connect(alice.id, bob.id)
        service.connect(bob.id, carol.id)
        service.connect(carol.id, dave.id)

        val path = service.findConnectionPath(alice.id, dave.id)
        path.shouldNotBeNull()
        path.length shouldBeGreaterThan 0
        path.vertices.forEach { log.debug { "vertex=$it" } }
    }

    @Test
    fun `2촌 인맥 탐색`() = runTest {
        val alice = service.addPerson("Alice", "Engineer", "A", "Seoul")
        val bob = service.addPerson("Bob", "Manager", "B", "Seoul")
        val carol = service.addPerson("Carol", "Designer", "C", "Seoul")

        service.connect(alice.id, bob.id)
        service.connect(bob.id, carol.id)

        val secondDegree = service.getConnectionsWithinDegree(alice.id, degree = 2).toList()
        secondDegree.shouldNotBeEmpty()
        secondDegree.forEach { log.debug { "vertex=$it" } }
    }

    @Test
    fun `회사 추가 및 재직자 조회`() = runTest {
        val alice = service.addPerson("Alice", "Engineer", "TechCorp", "Seoul")
        val bob = service.addPerson("Bob", "Designer", "TechCorp", "Seoul")
        val techCorp = service.addCompany("TechCorp", "Technology", "Seoul")

        service.addWorkExperience(alice.id, techCorp.id, "Software Engineer", isCurrent = true)
        service.addWorkExperience(bob.id, techCorp.id, "Designer", isCurrent = true)

        val employees = service.findEmployees(techCorp.id).toList()
        employees.shouldNotBeEmpty()
        employees.forEach { log.debug { "vertex=$it" } }
    }

    @Test
    fun `팔로우 관계 생성`() = runTest {
        val alice = service.addPerson("Alice", "Influencer", "A", "Seoul")
        val bob = service.addPerson("Bob", "Fan", "B", "Seoul")

        service.follow(bob.id, alice.id)

        val followers = ops.neighbors(
            alice.id,
            NeighborOptions(edgeLabel = "FOLLOWS", direction = Direction.INCOMING, maxDepth = 1)
        ).toList()
        followers.shouldNotBeEmpty()
        followers.forEach { log.debug { "vertex=$it" } }
    }
}
```

- [ ] **Step 4: 4개 suspend 구체 클래스 작성**

Neo4j:
```kotlin
package io.bluetape4k.graph.examples.linkedin

import io.bluetape4k.graph.neo4j.Neo4jGraphSuspendOperations
import io.bluetape4k.graph.servers.Neo4jServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Neo4jLinkedInGraphSuspendTest : AbstractLinkedInGraphSuspendTest() {
    private val driver: Driver = GraphDatabase.driver(Neo4jServer.instance.boltUrl, AuthTokens.none())
    override val ops = Neo4jGraphSuspendOperations(driver)

    @AfterAll fun teardown() { driver.close() }
}
```

Memgraph:
```kotlin
package io.bluetape4k.graph.examples.linkedin

import io.bluetape4k.graph.memgraph.MemgraphGraphSuspendOperations
import io.bluetape4k.graph.servers.MemgraphServer
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemgraphLinkedInGraphSuspendTest : AbstractLinkedInGraphSuspendTest() {
    override val ops = MemgraphGraphSuspendOperations(MemgraphServer.driver)
}
```

TinkerPop:
```kotlin
package io.bluetape4k.graph.examples.linkedin

import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TinkerGraphLinkedInGraphSuspendTest : AbstractLinkedInGraphSuspendTest() {
    override val ops = TinkerGraphSuspendOperations()
    override val graphName = "default"
}
```

AGE:
```kotlin
package io.bluetape4k.graph.examples.linkedin

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.bluetape4k.graph.age.AgeGraphSuspendOperations
import io.bluetape4k.graph.servers.PostgreSQLAgeServer
import org.jetbrains.exposed.v1.jdbc.Database
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AgeLinkedInGraphSuspendTest : AbstractLinkedInGraphSuspendTest() {
    override val graphName = "linkedin_test_suspend"
    private lateinit var dataSource: HikariDataSource
    override lateinit var ops: AgeGraphSuspendOperations

    @BeforeAll
    fun startServer() {
        // PostgreSQLAgeServer.instance는 lazy singleton — 자동 시작, 별도 stop 불필요
        val server = PostgreSQLAgeServer.instance
        dataSource = HikariDataSource(HikariConfig().apply {
            jdbcUrl = server.jdbcUrl
            username = server.username
            password = server.password
            driverClassName = "org.postgresql.Driver"
            connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
            maximumPoolSize = 5
        })
        ops = AgeGraphSuspendOperations(Database.connect(dataSource), graphName)
    }

    @AfterAll fun stopServer() { dataSource.close() }
}
```

- [ ] **Step 5: 전체 테스트 실행**

```bash
./gradlew :linkedin-graph-examples:test
```
Expected: 8개 클래스 × 5개 테스트 = 40개 PASS

- [ ] **Step 6: Commit**

```bash
git add examples/linkedin-graph-examples/
git commit -m "feat: add AbstractLinkedInGraphTest, suspend variant, and all backend concrete classes"
```

---

## Task 6: 기존 8개 모듈 삭제

**주의:** 새 모듈 테스트가 모두 PASS된 후 진행.

- [ ] **Step 1: 기존 모듈 디렉토리 삭제**

```bash
rm -rf examples/code-graph-age
rm -rf examples/code-graph-neo4j
rm -rf examples/code-graph-memgraph
rm -rf examples/code-graph-tinkerpop
rm -rf examples/linkedin-graph-age
rm -rf examples/linkedin-graph-neo4j
rm -rf examples/linkedin-graph-memgraph
rm -rf examples/linkedin-graph-tinkerpop
```

> `settings.gradle.kts`는 디렉토리를 자동 탐색하므로 별도 수정 불필요.

- [ ] **Step 2: 전체 빌드 확인**

```bash
./gradlew build -x test
```
Expected: BUILD SUCCESSFUL (삭제된 모듈 참조 오류 없음)

- [ ] **Step 3: Commit**

```bash
git add -A
git commit -m "chore: remove 8 redundant example modules, replaced by code/linkedin-graph-examples"
```

---

## Task 7: README.md + CLAUDE.md 갱신 및 Push

**Files:**
- Modify: `README.md`
- Modify: `CLAUDE.md`

- [ ] **Step 1: README.md의 모듈 구조 섹션 갱신**

`examples/` 항목을 다음으로 교체:
```
examples/
  code-graph-examples    # 코드 의존성 그래프 예시 (AGE·Neo4j·Memgraph·TinkerPop)
  linkedin-graph-examples # LinkedIn 소셜 그래프 예시 (AGE·Neo4j·Memgraph·TinkerPop)
```

Abstract 테스트 패턴 설명 섹션 추가:
```markdown
### Examples 테스트 구조

각 예시 모듈은 추상 테스트 클래스 패턴을 사용한다.
백엔드별 구체 클래스는 `GraphOperations`(동기) 또는 `GraphSuspendOperations`(코루틴)와 서버 setup만 오버라이드한다.

| 추상 클래스 | 구체 클래스 |
|---|---|
| `AbstractCodeGraphTest` | `Neo4j/Memgraph/Age/TinkerGraphCodeGraphTest` |
| `AbstractCodeGraphSuspendTest` | `Neo4j/Memgraph/Age/TinkerGraphCodeGraphSuspendTest` |
| `AbstractLinkedInGraphTest` | `Neo4j/Memgraph/Age/TinkerGraphLinkedInGraphTest` |
| `AbstractLinkedInGraphSuspendTest` | `Neo4j/Memgraph/Age/TinkerGraphLinkedInGraphSuspendTest` |
```

- [ ] **Step 2: 최종 전체 테스트 실행**

```bash
./gradlew test
```
Expected: 전체 PASS

- [ ] **Step 2: CLAUDE.md의 Project Structure 섹션 갱신**

`CLAUDE.md` 내 `examples/` 항목을 아래로 교체:
```
examples/
  code-graph-examples    # 코드 의존성 그래프 예시 (AGE·Neo4j·Memgraph·TinkerPop 모두 포함)
  linkedin-graph-examples # LinkedIn 소셜 그래프 예시 (AGE·Neo4j·Memgraph·TinkerPop 모두 포함)
```

- [ ] **Step 3: 최종 전체 테스트 실행**

```bash
./gradlew test
```
Expected: 전체 PASS

- [ ] **Step 4: Commit & Push**

```bash
git add README.md CLAUDE.md
git commit -m "docs: update README and CLAUDE.md for consolidated examples modules"
git push
```
