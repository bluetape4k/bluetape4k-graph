# graph-servers Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the in-repo `graph/graph-servers/` module with the `io.bluetape4k.testcontainers.graphdb` package from `bluetape4k-testcontainers`, then delete the module.

**Architecture:** Every test module currently depends on `project(":graph-servers")` and imports `io.bluetape4k.graph.servers.*` singletons. We migrate in three backend groups (Neo4j → Memgraph → AGE), fixing `build.gradle.kts` transitive deps (because `bluetape4k-testcontainers` declares graphdb deps as `compileOnly`) and re-pointing imports/call-sites to the new `*.Launcher.<server>` accessors. Memgraph drops its singleton driver, so driver lifecycle moves into test classes via `@BeforeAll`/`@AfterAll`. After compile + test green, we remove `graph/graph-servers/` and update docs.

**Tech Stack:** Kotlin 2.3, Gradle Kotlin DSL, JUnit 5 (PER_CLASS lifecycle), Neo4j Java Driver, HikariCP, Testcontainers, `bluetape4k-testcontainers` graphdb package.

**Spec:** `docs/superpowers/specs/2026-04-18-graph-servers-migration-design.md`

**Worktree:** `../bluetape4k-graph-wt-migration` (branch `feature/graph-servers-migration`)

---

## File Map

### Build scripts (8 files) — modify
- `graph/graph-neo4j/build.gradle.kts`
- `graph/graph-memgraph/build.gradle.kts`
- `graph/graph-age/build.gradle.kts`
- `graph/graph-tinkerpop/build.gradle.kts`
- `examples/code-graph-examples/build.gradle.kts`
- `examples/linkedin-graph-examples/build.gradle.kts`
- `spring-boot3/graph-spring-boot3-starter/build.gradle.kts`
- `spring-boot4/graph-spring-boot4-starter/build.gradle.kts`

### Neo4j backend tests (9 files) — modify
- `graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jAlgorithmTest.kt`
- `graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jAlgorithmSuspendTest.kt`
- `graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphOperationsTest.kt`
- `examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/Neo4jCodeGraphTest.kt`
- `examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/Neo4jCodeGraphSuspendTest.kt`
- `examples/linkedin-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/Neo4jLinkedInGraphTest.kt`
- `examples/linkedin-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/Neo4jLinkedInGraphSuspendTest.kt`
- `spring-boot3/graph-spring-boot3-starter/src/test/**/GraphNeo4jAutoConfigurationTest.kt`
- `spring-boot4/graph-spring-boot4-starter/src/test/**/GraphNeo4jAutoConfigurationTest.kt`

### Memgraph backend tests (9 files) — modify; driver lifecycle moves to test class
- `graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphOperationsTest.kt`
- `graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphSuspendOperationsTest.kt`
- `graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphAlgorithmTest.kt`
- `examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/MemgraphCodeGraphTest.kt`
- `examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/MemgraphCodeGraphSuspendTest.kt`
- `examples/linkedin-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/MemgraphLinkedInGraphTest.kt`
- `examples/linkedin-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/MemgraphLinkedInGraphSuspendTest.kt`
- `spring-boot3/graph-spring-boot3-starter/src/test/**/GraphMemgraphAutoConfigurationTest.kt`
- `spring-boot4/graph-spring-boot4-starter/src/test/**/GraphMemgraphAutoConfigurationTest.kt`

### AGE backend tests (9 files) — modify; keep HikariCP `connectionInitSql`
- `graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeGraphOperationsTest.kt`
- `graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeGraphSuspendOperationsTest.kt`
- `graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeAlgorithmTest.kt`
- `examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/AgeCodeGraphTest.kt`
- `examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/AgeCodeGraphSuspendTest.kt`
- `examples/linkedin-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/AgeLinkedInGraphTest.kt`
- `examples/linkedin-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/AgeLinkedInGraphSuspendTest.kt`
- `spring-boot3/graph-spring-boot3-starter/src/test/**/GraphAgeAutoConfigurationTest.kt`
- `spring-boot4/graph-spring-boot4-starter/src/test/**/GraphAgeAutoConfigurationTest.kt`

### Delete
- `graph/graph-servers/` (entire directory including `build.gradle.kts`, `README.md`, `README.ko.md`, `src/`)

### Documentation — modify
- `CLAUDE.md`
- `README.md`, `README.ko.md` (root)
- `examples/code-graph-examples/README.md`, `README.ko.md`
- `examples/linkedin-graph-examples/README.md`, `README.ko.md`
- `TODO.md` (if it references graph-servers)
- `docs/testlogs/2026-04.md` (append test result header)

---

## API Replacement Reference

Keep this table open while editing — use it to resolve every old reference.

| Old | New |
|-----|-----|
| `import io.bluetape4k.graph.servers.Neo4jServer` | `import io.bluetape4k.testcontainers.graphdb.Neo4jServer` |
| `import io.bluetape4k.graph.servers.MemgraphServer` | `import io.bluetape4k.testcontainers.graphdb.MemgraphServer` |
| `import io.bluetape4k.graph.servers.PostgreSQLAgeServer` | `import io.bluetape4k.testcontainers.graphdb.PostgreSQLAgeServer` |
| `Neo4jServer.boltUrl` | `Neo4jServer.Launcher.neo4j.boltUrl` |
| `Neo4jServer.instance.boltUrl` | `Neo4jServer.Launcher.neo4j.boltUrl` |
| `Neo4jServer.instance` (container) | `Neo4jServer.Launcher.neo4j` |
| `MemgraphServer.boltUrl` | `MemgraphServer.Launcher.memgraph.boltUrl` |
| `MemgraphServer.instance` | `MemgraphServer.Launcher.memgraph` |
| `MemgraphServer.driver` (singleton — no longer exists) | Test class creates `Driver` via `GraphDatabase.driver(MemgraphServer.Launcher.memgraph.boltUrl, AuthTokens.none())` in `@BeforeAll`, closes in `@AfterAll` |
| `PostgreSQLAgeServer.instance.jdbcUrl` | `PostgreSQLAgeServer.Launcher.postgresqlAge.jdbcUrl` |
| `PostgreSQLAgeServer.instance.username` | `PostgreSQLAgeServer.Launcher.postgresqlAge.username` |
| `PostgreSQLAgeServer.instance.password` | `PostgreSQLAgeServer.Launcher.postgresqlAge.password` |
| `PostgreSQLAgeServer.instance` (container) | `PostgreSQLAgeServer.Launcher.postgresqlAge` |

HikariCP AGE `connectionInitSql` **must remain unchanged** — new `PostgreSQLAgeServer.start()` only primes the container-start connection, not pool connections.

---

## Task 0: Preflight — spec/plan 커밋

**Complexity:** low

`git worktree add`는 HEAD 기준으로 새 worktree를 생성한다. 현재 untracked 상태인 spec/plan 파일이 커밋되지 않으면 새 worktree에 해당 파일이 없어 구현 에이전트가 참조할 수 없다.

- [ ] **Step 1: spec/plan 스테이징 및 커밋**

```bash
git add docs/superpowers/specs/2026-04-18-graph-servers-migration-design.md
git add docs/superpowers/plans/2026-04-18-graph-servers-migration-plan.md
git commit -m "docs: add graph-servers migration spec and plan"
```

Expected: 커밋 성공. 이 커밋이 worktree의 시작점이 된다.

---

## Task 1: Worktree Setup

**Complexity:** low

**Files:**
- Create: worktree at `/Users/debop/work/bluetape4k/bluetape4k-graph-wt-migration` (branch `feature/graph-servers-migration`)

- [ ] **Step 1: Create worktree and branch**

Run from the main repo `/Users/debop/work/bluetape4k/bluetape4k-graph`:

```bash
git worktree add ../bluetape4k-graph-wt-migration -b feature/graph-servers-migration
```

Expected output: `Preparing worktree (new branch 'feature/graph-servers-migration')` and `HEAD is now at <sha> ...`.

- [ ] **Step 2: Verify worktree**

```bash
git worktree list
```

Expected: two entries — main repo path and `../bluetape4k-graph-wt-migration  <sha> [feature/graph-servers-migration]`.

- [ ] **Step 3: Switch all subsequent work to the worktree**

All following tasks run with cwd `/Users/debop/work/bluetape4k/bluetape4k-graph-wt-migration`. Use absolute paths in this plan exactly as written but prefix the **worktree** directory, not the main repo.

---

## Task 2: Verify `Libs.bluetape4k_testcontainers` exposes the new API

**Complexity:** low

**Files:**
- Read: `/Users/debop/work/bluetape4k/bluetape4k-graph-wt-migration/buildSrc/src/main/kotlin/Libs.kt`

- [ ] **Step 1: Confirm dependency coordinate + version**

Use Grep on `buildSrc/src/main/kotlin/Libs.kt` for `bluetape4k_testcontainers` and `testcontainers_neo4j`, `testcontainers_postgresql`, `hikaricp`, `neo4j_java_driver`. Confirm all are declared. Expected: all five constants already exist (they are referenced by existing `build.gradle.kts` files).

- [ ] **Step 2: 좌표 확인 (실제 클래스 존재 여부는 Task 3의 `compileTestKotlin`에서 검증됨)**

이 단계는 `Libs.kt` 좌표 존재 확인만 수행한다. `bluetape4k-testcontainers` artifact에 `io.bluetape4k.testcontainers.graphdb.*` 클래스가 실제로 포함되어 있는지는 Task 3 Step 3의 `:graph-neo4j:compileTestKotlin`에서 import 오류 없이 통과함으로써 증명된다.

From the worktree root:

Use Grep tool to verify `bluetape4k_testcontainers` is defined in `buildSrc/src/main/kotlin/Libs.kt`:

```
Grep pattern: "bluetape4k_testcontainers"
Path: buildSrc/src/main/kotlin/Libs.kt
```

Expected: `val bluetape4k_testcontainers = bluetape4k("testcontainers")` line found. Also verify `testcontainers_neo4j`, `testcontainers_postgresql`, `hikaricp`, `neo4j_java_driver` constants exist. If `bluetape4k_testcontainers` is missing or on the wrong version, stop and update `Libs.kt` before continuing.

- [ ] **Step 3: Commit (nothing to commit — this is a verification task)**

No commit. Move on.

---

## Task 3: `graph-neo4j` build script

**Complexity:** low

**Files:**
- Modify: `graph/graph-neo4j/build.gradle.kts`

Current `testImplementation(project(":graph-servers"))` is on line 3. `bluetape4k-testcontainers`, `testcontainers_neo4j` are already present. We only need to add `neo4j_java_driver` as `testImplementation` (it's already in `api` — transitive — but we make test-use explicit) and drop the project dep.

- [ ] **Step 1: Edit `graph/graph-neo4j/build.gradle.kts`**

Replace the entire `dependencies { ... }` block with:

```kotlin
dependencies {
    api(project(":graph-core"))
    api(Libs.neo4j_java_driver)
    runtimeOnly(Libs.neo4j_bolt_connection_netty)
    runtimeOnly(Libs.neo4j_bolt_connection_pooled)

    api(Libs.bluetape4k_coroutines)
    api(Libs.kotlinx_coroutines_reactive)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers_neo4j)
    testImplementation(Libs.kotlinx_coroutines_test)
}
```

Removed: `testImplementation(project(":graph-servers"))`. The `neo4j_java_driver` stays in `api` (already transitive to tests).

- [ ] **Step 2: Sanity compile — main only (tests still reference old imports)**

```bash
./gradlew :graph-neo4j:compileKotlin
```

Expected: `BUILD SUCCESSFUL`. Test compile will fail until Task 6.

- [ ] **Step 3: Commit**

```bash
git add graph/graph-neo4j/build.gradle.kts
git commit -m "refactor(graph-neo4j): drop :graph-servers test dep"
```

---

## Task 4: `graph-memgraph` build script

**Complexity:** low

**Files:**
- Modify: `graph/graph-memgraph/build.gradle.kts`

- [ ] **Step 1: Edit `graph/graph-memgraph/build.gradle.kts`**

Replace entire `dependencies { ... }` block with:

```kotlin
dependencies {
    api(project(":graph-core"))
    api(project(":graph-neo4j"))

    api(Libs.neo4j_java_driver)
    runtimeOnly(Libs.neo4j_bolt_connection_netty)
    runtimeOnly(Libs.neo4j_bolt_connection_pooled)

    api(Libs.bluetape4k_coroutines)
    api(Libs.kotlinx_coroutines_reactive)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers)
    testImplementation(Libs.kotlinx_coroutines_test)
}
```

Removed: `testImplementation(project(":graph-servers"))`. `Libs.testcontainers` (generic) already present.

- [ ] **Step 2: Sanity compile main**

```bash
./gradlew :graph-memgraph:compileKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add graph/graph-memgraph/build.gradle.kts
git commit -m "refactor(graph-memgraph): drop :graph-servers test dep"
```

---

## Task 5: `graph-age` and `graph-tinkerpop` build scripts

**Complexity:** low

**Files:**
- Modify: `graph/graph-age/build.gradle.kts`
- Modify: `graph/graph-tinkerpop/build.gradle.kts`

- [ ] **Step 1: Edit `graph/graph-age/build.gradle.kts`**

Replace entire `dependencies { ... }` block with:

```kotlin
dependencies {
    api(project(":graph-core"))

    api(Libs.exposed_core)
    api(Libs.exposed_dao)
    api(Libs.exposed_jdbc)
    api(Libs.exposed_java_time)
    api(Libs.postgresql_driver)

    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.kotlinx_coroutines_core)
    testImplementation(Libs.kotlinx_coroutines_test)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers_postgresql)
    testImplementation(Libs.hikaricp)
}
```

Removed: `testImplementation(project(":graph-servers"))`.

- [ ] **Step 2: Edit `graph/graph-tinkerpop/build.gradle.kts`**

Replace entire `dependencies { ... }` block with:

```kotlin
dependencies {
    api(project(":graph-core"))

    api(Libs.tinkerpop_gremlin_core)
    api(Libs.tinkergraph_gremlin)

    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.kotlinx_coroutines_core)
    testImplementation(Libs.kotlinx_coroutines_test)

    testImplementation(Libs.bluetape4k_junit5)
}
```

Removed: `testImplementation(project(":graph-servers"))`. No replacement — TinkerGraph is in-memory; it never needed server deps.

- [ ] **Step 3: Sanity compile both**

```bash
./gradlew :graph-age:compileKotlin :graph-tinkerpop:compileKotlin
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add graph/graph-age/build.gradle.kts graph/graph-tinkerpop/build.gradle.kts
git commit -m "refactor(graph-age,graph-tinkerpop): drop :graph-servers test dep"
```

---

## Task 6: Neo4j test files — import/call-site migration

**Complexity:** medium

**Files:**
- Modify: `graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jAlgorithmTest.kt`
- Modify: `graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jAlgorithmSuspendTest.kt`
- Modify: `graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphOperationsTest.kt`

- [ ] **Step 1: For each of the three files, change the import**

In every file, replace:

```kotlin
import io.bluetape4k.graph.servers.Neo4jServer
```

with:

```kotlin
import io.bluetape4k.testcontainers.graphdb.Neo4jServer
```

- [ ] **Step 2: Replace call-sites in each file**

Use Grep to locate all `Neo4jServer.` references, then Edit each file to swap:

- `Neo4jServer.instance.boltUrl` → `Neo4jServer.Launcher.neo4j.boltUrl`
- `Neo4jServer.boltUrl` → `Neo4jServer.Launcher.neo4j.boltUrl`
- `Neo4jServer.instance` (as container reference) → `Neo4jServer.Launcher.neo4j`

Example (from `Neo4jGraphOperationsTest.kt` `@BeforeAll`):

```kotlin
// Before
@BeforeAll
fun setup() {
    val server = Neo4jServer.instance
    driver = GraphDatabase.driver(server.boltUrl, AuthTokens.none())
    ops = Neo4jGraphOperations(driver)
}

// After
@BeforeAll
fun setup() {
    val server = Neo4jServer.Launcher.neo4j
    driver = GraphDatabase.driver(server.boltUrl, AuthTokens.none())
    ops = Neo4jGraphOperations(driver)
}
```

Keep existing `@AfterAll { driver.close() }` — driver ownership was already in the test class.

- [ ] **Step 3: Compile tests**

```bash
./gradlew :graph-neo4j:compileTestKotlin
```

Expected: `BUILD SUCCESSFUL`. If you see `unresolved reference: servers`, a file was missed — Grep again.

- [ ] **Step 4: Run tests (Testcontainers, Docker required)**

```bash
./gradlew :graph-neo4j:test
```

Expected: all tests pass. Neo4j container image pin change (`neo4j:5.26.24`) is benign for these tests.

- [ ] **Step 5: Commit**

```bash
git add graph/graph-neo4j/src/test
git commit -m "refactor(graph-neo4j): migrate tests to bluetape4k-testcontainers graphdb"
```

---

## Task 7: Memgraph test files — driver lifecycle migration

**Complexity:** high

This is the most delicate step. The old `MemgraphServer.driver` singleton no longer exists; each test class must own its `Driver` and close it in `@AfterAll`.

**Files:**
- Modify: `graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphOperationsTest.kt`
- Modify: `graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphSuspendOperationsTest.kt`
- Modify: `graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphAlgorithmTest.kt`

- [ ] **Step 1: Update `MemgraphGraphOperationsTest.kt`**

Replace the import and the `@BeforeAll`/`@AfterAll` block:

```kotlin
// imports
import io.bluetape4k.testcontainers.graphdb.MemgraphServer
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase

// Remove: import io.bluetape4k.graph.servers.MemgraphServer
```

Class body (keep `@TestInstance(PER_CLASS)` + `@TestMethodOrder`):

```kotlin
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MemgraphGraphOperationsTest {

    private lateinit var driver: Driver
    private lateinit var ops: MemgraphGraphOperations

    @BeforeAll
    fun setup() {
        driver = GraphDatabase.driver(
            MemgraphServer.Launcher.memgraph.boltUrl,
            AuthTokens.none()
        )
        ops = MemgraphGraphOperations(driver)
    }

    @AfterAll
    fun teardown() {
        driver.close()
    }

    // ... existing @BeforeEach + @Test methods unchanged ...
}
```

- [ ] **Step 2: Apply the same pattern to `MemgraphGraphSuspendOperationsTest.kt` and `MemgraphAlgorithmTest.kt`**

Same rewrite: import change, add `private lateinit var driver: Driver`, populate in `@BeforeAll`, close in `@AfterAll`. If those files already use a locally-owned driver (unlike `MemgraphGraphOperationsTest`), only the import and the `MemgraphServer.driver` → local `driver` field replacement is needed; verify per-file.

Use Grep on each file for `MemgraphServer.` before editing to enumerate every call-site.

- [ ] **Step 3: Compile tests**

```bash
./gradlew :graph-memgraph:compileTestKotlin
```

Expected: `BUILD SUCCESSFUL`. Common failure: `unresolved reference: driver` means a `MemgraphServer.driver` call-site was missed — Grep again.

- [ ] **Step 4: Run tests**

```bash
./gradlew :graph-memgraph:test
```

Expected: all pass. Memgraph image pin change (`memgraph:3.9.0`) should not affect existing tests.

- [ ] **Step 5: Commit**

```bash
git add graph/graph-memgraph/src/test
git commit -m "refactor(graph-memgraph): migrate tests to bluetape4k-testcontainers graphdb (driver now test-owned)"
```

---

## Task 8: AGE test files — import/call-site migration (keep HikariCP init SQL)

**Complexity:** medium

**Files:**
- Modify: `graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeGraphOperationsTest.kt`
- Modify: `graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeGraphSuspendOperationsTest.kt`
- Modify: `graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeAlgorithmTest.kt`

- [ ] **Step 1: In each file, change import**

Replace:

```kotlin
import io.bluetape4k.graph.servers.PostgreSQLAgeServer
```

with:

```kotlin
import io.bluetape4k.testcontainers.graphdb.PostgreSQLAgeServer
```

- [ ] **Step 2: Replace call-sites**

- `PostgreSQLAgeServer.instance.jdbcUrl` → `PostgreSQLAgeServer.Launcher.postgresqlAge.jdbcUrl`
- `PostgreSQLAgeServer.instance.username` → `PostgreSQLAgeServer.Launcher.postgresqlAge.username`
- `PostgreSQLAgeServer.instance.password` → `PostgreSQLAgeServer.Launcher.postgresqlAge.password`
- `PostgreSQLAgeServer.instance` (container) → `PostgreSQLAgeServer.Launcher.postgresqlAge`

Example block after migration:

```kotlin
val server = PostgreSQLAgeServer.Launcher.postgresqlAge
dataSource = HikariDataSource(HikariConfig().apply {
    jdbcUrl = server.jdbcUrl
    username = server.username
    password = server.password
    driverClassName = "org.postgresql.Driver"
    connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
    maximumPoolSize = 5
})
```

**Do NOT remove `connectionInitSql`** — it primes each pooled connection for AGE. Container-start init does not substitute.

- [ ] **Step 3: Check for hardcoded DB name `age_test`**

Run Grep on `graph/graph-age/src/test` for `age_test`. If any hit, note that new `PostgreSQLAgeServer` uses DB name `test`. If code compares against a hardcoded name, replace `"age_test"` with `server.databaseName` or `"test"`. If no hits, skip.

- [ ] **Step 4: Compile and run**

```bash
./gradlew :graph-age:compileTestKotlin
./gradlew :graph-age:test
```

Expected: `BUILD SUCCESSFUL` for compile, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add graph/graph-age/src/test
git commit -m "refactor(graph-age): migrate tests to bluetape4k-testcontainers graphdb"
```

---

## Task 9: `code-graph-examples` build script

**Complexity:** low

**Files:**
- Modify: `examples/code-graph-examples/build.gradle.kts`

- [ ] **Step 1: Edit dependencies block**

Replace entire `dependencies { ... }` block with:

```kotlin
dependencies {
    implementation(project(":graph-core"))
    implementation(project(":graph-age"))
    implementation(project(":graph-neo4j"))
    implementation(project(":graph-memgraph"))
    implementation(project(":graph-tinkerpop"))

    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.kotlinx_coroutines_core)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers)
    testImplementation(Libs.testcontainers_neo4j)
    testImplementation(Libs.testcontainers_postgresql)
    testImplementation(Libs.neo4j_java_driver)       // bluetape4k-testcontainers declares this compileOnly
    testRuntimeOnly(Libs.postgresql_driver)           // bluetape4k-testcontainers declares this compileOnly
    testImplementation(Libs.hikaricp)
    testImplementation(Libs.kotlinx_coroutines_test)
}
```

Removed: `testImplementation(project(":graph-servers"))`. All transitive deps the tests touch are now explicit (including `neo4j_java_driver` and `postgresql_driver` which `bluetape4k-testcontainers` only declares as `compileOnly`).

- [ ] **Step 2: Commit**

```bash
git add examples/code-graph-examples/build.gradle.kts
git commit -m "refactor(code-graph-examples): drop :graph-servers test dep"
```

---

## Task 10: `code-graph-examples` test files

**Complexity:** medium

**Files:**
- Modify: `examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/Neo4jCodeGraphTest.kt`
- Modify: `examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/Neo4jCodeGraphSuspendTest.kt`
- Modify: `examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/MemgraphCodeGraphTest.kt`
- Modify: `examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/MemgraphCodeGraphSuspendTest.kt`
- Modify: `examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/AgeCodeGraphTest.kt`
- Modify: `examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/AgeCodeGraphSuspendTest.kt`

- [ ] **Step 1: Update Neo4j sibling tests** (`Neo4jCodeGraphTest.kt`, `Neo4jCodeGraphSuspendTest.kt`)

Current `Neo4jCodeGraphTest.kt`:

```kotlin
import io.bluetape4k.graph.servers.Neo4jServer
...
class Neo4jCodeGraphTest : AbstractCodeGraphTest() {
    private val driver: Driver = GraphDatabase.driver(Neo4jServer.instance.boltUrl, AuthTokens.none())
    override val ops = Neo4jGraphOperations(driver)

    @AfterAll
    fun teardown() { driver.close() }
}
```

Change to:

```kotlin
package io.bluetape4k.graph.examples.code

import io.bluetape4k.graph.neo4j.Neo4jGraphOperations
import io.bluetape4k.testcontainers.graphdb.Neo4jServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.TestInstance
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Neo4jCodeGraphTest : AbstractCodeGraphTest() {
    private val driver: Driver =
        GraphDatabase.driver(Neo4jServer.Launcher.neo4j.boltUrl, AuthTokens.none())
    override val ops = Neo4jGraphOperations(driver)

    @AfterAll
    fun teardown() { driver.close() }
}
```

Apply the same shape to `Neo4jCodeGraphSuspendTest.kt`. Add `@TestInstance(PER_CLASS)` only if it isn't already present on the abstract parent; if the abstract declares it, omit.

- [ ] **Step 2: Update Memgraph sibling tests** (`MemgraphCodeGraphTest.kt`, `MemgraphCodeGraphSuspendTest.kt`)

Current `MemgraphCodeGraphTest.kt`:

```kotlin
import io.bluetape4k.graph.servers.MemgraphServer

class MemgraphCodeGraphTest : AbstractCodeGraphTest() {
    override val ops = MemgraphGraphOperations(MemgraphServer.driver)
}
```

The parent declares `protected abstract val ops: GraphOperations`. Kotlin permits overriding `abstract val` with `lateinit var` (same pattern already used in `AgeCodeGraphTest`). Change to:

```kotlin
package io.bluetape4k.graph.examples.code

import io.bluetape4k.graph.memgraph.MemgraphGraphOperations
import io.bluetape4k.testcontainers.graphdb.MemgraphServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.neo4j.driver.AuthTokens
import org.neo4j.driver.Driver
import org.neo4j.driver.GraphDatabase

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemgraphCodeGraphTest : AbstractCodeGraphTest() {
    private lateinit var driver: Driver
    override lateinit var ops: MemgraphGraphOperations

    @BeforeAll
    fun setup() {
        driver = GraphDatabase.driver(
            MemgraphServer.Launcher.memgraph.boltUrl,
            AuthTokens.none()
        )
        ops = MemgraphGraphOperations(driver)
    }

    @AfterAll
    fun teardown() {
        driver.close()
    }
}
```

Apply the same pattern to `MemgraphCodeGraphSuspendTest.kt` (with `GraphSuspendOperations` parent — use `MemgraphGraphSuspendOperations` as the concrete type).

- [ ] **Step 3: Update AGE sibling tests** (`AgeCodeGraphTest.kt`, `AgeCodeGraphSuspendTest.kt`)

In each file: change import, swap `PostgreSQLAgeServer.instance` → `PostgreSQLAgeServer.Launcher.postgresqlAge`. Keep `connectionInitSql` exactly as is (`LOAD 'age'; SET search_path = ag_catalog, "$user", public;`). Example:

```kotlin
// imports
import io.bluetape4k.testcontainers.graphdb.PostgreSQLAgeServer

@BeforeAll
fun startServer() {
    val server = PostgreSQLAgeServer.Launcher.postgresqlAge
    dataSource = HikariDataSource(HikariConfig().apply {
        jdbcUrl = server.jdbcUrl
        username = server.username
        password = server.password
        driverClassName = "org.postgresql.Driver"
        connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"\$user\", public;"
        maximumPoolSize = 5
    })
    database = Database.connect(dataSource)
    ops = AgeGraphOperations(graphName)
}
```

- [ ] **Step 4: Compile and run**

```bash
./gradlew :code-graph-examples:compileTestKotlin
./gradlew :code-graph-examples:test
```

Expected: `BUILD SUCCESSFUL` for compile; all tests pass (Docker required).

- [ ] **Step 5: Commit**

```bash
git add examples/code-graph-examples
git commit -m "refactor(code-graph-examples): migrate tests to bluetape4k-testcontainers graphdb"
```

---

## Task 11: `linkedin-graph-examples` build script + test files

**Complexity:** medium

**Files:**
- Modify: `examples/linkedin-graph-examples/build.gradle.kts`
- Modify: `examples/linkedin-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/Neo4jLinkedInGraphTest.kt`
- Modify: `examples/linkedin-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/Neo4jLinkedInGraphSuspendTest.kt`
- Modify: `examples/linkedin-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/MemgraphLinkedInGraphTest.kt`
- Modify: `examples/linkedin-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/MemgraphLinkedInGraphSuspendTest.kt`
- Modify: `examples/linkedin-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/AgeLinkedInGraphTest.kt`
- Modify: `examples/linkedin-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/AgeLinkedInGraphSuspendTest.kt`

- [ ] **Step 1: Edit `examples/linkedin-graph-examples/build.gradle.kts`**

Apply the identical `dependencies { ... }` block from Task 9 (same structure — `code-graph-examples` and `linkedin-graph-examples` have identical dep footprints). This includes `testImplementation(Libs.neo4j_java_driver)` and `testRuntimeOnly(Libs.postgresql_driver)` which are required because `bluetape4k-testcontainers` declares them only as `compileOnly`.

- [ ] **Step 2: Apply Task 10 Step 1–3 patterns to the six LinkedIn test files**

Each LinkedIn file mirrors its `code-graph` counterpart structurally. Use the same rewrites (Neo4j = driver-owning, Memgraph = lateinit driver via `@BeforeAll`/`@AfterAll`, AGE = keep `connectionInitSql`).

- [ ] **Step 3: Compile and run**

```bash
./gradlew :linkedin-graph-examples:compileTestKotlin
./gradlew :linkedin-graph-examples:test
```

Expected: all green.

- [ ] **Step 4: Commit**

```bash
git add examples/linkedin-graph-examples
git commit -m "refactor(linkedin-graph-examples): migrate tests to bluetape4k-testcontainers graphdb"
```

---

## Task 12: `graph-spring-boot3-starter` build + tests

**Complexity:** medium

**Files:**
- Modify: `spring-boot3/graph-spring-boot3-starter/build.gradle.kts`
- Modify: `spring-boot3/graph-spring-boot3-starter/src/test/**/GraphNeo4jAutoConfigurationTest.kt`
- Modify: `spring-boot3/graph-spring-boot3-starter/src/test/**/GraphMemgraphAutoConfigurationTest.kt`
- Modify: `spring-boot3/graph-spring-boot3-starter/src/test/**/GraphAgeAutoConfigurationTest.kt`

- [ ] **Step 1: Edit `build.gradle.kts`**

Remove `testImplementation(project(":graph-servers"))` (line 33). Append the following test deps to the existing block:

```kotlin
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers)
    testImplementation(Libs.testcontainers_neo4j)
    testImplementation(Libs.testcontainers_postgresql)
    testImplementation(Libs.neo4j_java_driver)
    testImplementation(Libs.hikaricp)
    testRuntimeOnly(Libs.neo4j_bolt_connection_netty)
    testRuntimeOnly(Libs.neo4j_bolt_connection_pooled)
    testRuntimeOnly(Libs.postgresql_driver)
```

- [ ] **Step 2: Migrate the three AutoConfiguration tests**

Use `@DynamicPropertySource` URL-access migration per the spec. Find each file via Glob `spring-boot3/graph-spring-boot3-starter/src/test/**/Graph*AutoConfigurationTest.kt`, then:

- Replace the import (`io.bluetape4k.graph.servers.*` → `io.bluetape4k.testcontainers.graphdb.*`).
- Swap access patterns:
  - `Neo4jServer.boltUrl` / `Neo4jServer.instance.boltUrl` → `Neo4jServer.Launcher.neo4j.boltUrl`
  - `MemgraphServer.boltUrl` → `MemgraphServer.Launcher.memgraph.boltUrl` (확인됨: `GraphMemgraphAutoConfigurationTest`는 `.boltUrl`만 사용하고 `.driver`는 사용하지 않으므로 driver lifecycle 변경 불필요)
  - `PostgreSQLAgeServer.instance.jdbcUrl`/`username`/`password` → `PostgreSQLAgeServer.Launcher.postgresqlAge.jdbcUrl`/`username`/`password`
- Keep `connectionInitSql` if AGE test uses HikariCP.

- [ ] **Step 3: Compile and run**

```bash
./gradlew :graph-spring-boot3-starter:compileTestKotlin
./gradlew :graph-spring-boot3-starter:test
```

Expected: all tests pass.

- [ ] **Step 4: Commit**

```bash
git add spring-boot3/graph-spring-boot3-starter
git commit -m "refactor(graph-spring-boot3-starter): migrate tests to bluetape4k-testcontainers graphdb"
```

---

## Task 13: `graph-spring-boot4-starter` build + tests

**Complexity:** medium

**Files:**
- Modify: `spring-boot4/graph-spring-boot4-starter/build.gradle.kts`
- Modify: `spring-boot4/graph-spring-boot4-starter/src/test/**/GraphNeo4jAutoConfigurationTest.kt`
- Modify: `spring-boot4/graph-spring-boot4-starter/src/test/**/GraphMemgraphAutoConfigurationTest.kt`
- Modify: `spring-boot4/graph-spring-boot4-starter/src/test/**/GraphAgeAutoConfigurationTest.kt`

Repeat Task 12, substituting `spring-boot3` → `spring-boot4`. Build script edits identical (add the same nine test deps, drop `:graph-servers`).

- [ ] **Step 1: Edit `build.gradle.kts`** — apply Task 12 Step 1 edits.
- [ ] **Step 2: Migrate three AutoConfiguration tests** — apply Task 12 Step 2 rewrites.
- [ ] **Step 3: Compile and run**

```bash
./gradlew :graph-spring-boot4-starter:compileTestKotlin
./gradlew :graph-spring-boot4-starter:test
```

- [ ] **Step 4: Commit**

```bash
git add spring-boot4/graph-spring-boot4-starter
git commit -m "refactor(graph-spring-boot4-starter): migrate tests to bluetape4k-testcontainers graphdb"
```

---

## Task 14: Whole-project compile check

**Complexity:** low

- [ ] **Step 1: Full compile**

```bash
./gradlew compileKotlin compileTestKotlin
```

Expected: `BUILD SUCCESSFUL`. If `:graph-servers:compileKotlin` succeeds but is still listed, that's fine — the module still exists. It will be removed in Task 15.

- [ ] **Step 2: Run all tests (ex. graph-servers) sequentially to avoid container contention**

```bash
./gradlew :graph-neo4j:test :graph-memgraph:test :graph-age:test \
          :graph-tinkerpop:test \
          :code-graph-examples:test :linkedin-graph-examples:test \
          :graph-spring-boot3-starter:test :graph-spring-boot4-starter:test \
          --no-parallel
```

Expected: all tests pass. `--no-parallel` flag prevents Testcontainers singleton clashes across modules. The root `testMutex` BuildService should handle this too; `--no-parallel` is belt-and-braces.

- [ ] **Step 3: Commit (no file changes — verification only)**

No commit.

---

## Task 15: Delete `graph-servers` module

**Complexity:** low

**Files:**
- Delete: `graph/graph-servers/` (entire directory)

- [ ] **Step 1: Delete the directory**

```bash
rm -rf graph/graph-servers
```

- [ ] **Step 2: Verify `settings.gradle.kts` needs no edit**

Grep `settings.gradle.kts` for `graph-servers`. Expected: no hits — the file uses `includeModules("graph", false, false)` which auto-scans. If a direct `include(":graph-servers")` exists, remove that line.

- [ ] **Step 3: Verify root `build.gradle.kts` publication config**

Grep root `build.gradle.kts` for `graph-servers`. Expected: no hits. If a `publishing` or `centralPortal` excludes/includes references it, remove the entry.

- [ ] **Step 4: Re-verify full build succeeds**

```bash
./gradlew clean build -x test
```

Expected: `BUILD SUCCESSFUL`. The Gradle module graph should no longer list `:graph-servers`.

- [ ] **Step 5: Commit**

```bash
git add -A graph/graph-servers settings.gradle.kts build.gradle.kts
git commit -m "chore: remove graph/graph-servers module (superseded by bluetape4k-testcontainers graphdb)"
```

---

## Task 16: Documentation updates

**Complexity:** medium

**Files:**
- Modify: `CLAUDE.md`
- Modify: `README.md`, `README.ko.md`
- Modify: `examples/code-graph-examples/README.md`, `README.ko.md`
- Modify: `examples/linkedin-graph-examples/README.md`, `README.ko.md`
- Modify: `TODO.md` (if contains stale refs)

- [ ] **Step 1: `CLAUDE.md`**

- Remove the `graph-servers/` bullet from the "Project Structure" diagram (it currently reads "테스트용 Testcontainers 서버 팩토리 (Neo4j, Memgraph, PostgreSQL+AGE)").
- In the "Architecture > 테스트 패턴" section, replace the code example:

```kotlin
// Before
val driver = GraphDatabase.driver(Neo4jServer.boltUrl, AuthTokens.none())
val ops = Neo4jGraphOperations(driver)

// After
import io.bluetape4k.testcontainers.graphdb.Neo4jServer
val driver = GraphDatabase.driver(Neo4jServer.Launcher.neo4j.boltUrl, AuthTokens.none())
val ops = Neo4jGraphOperations(driver)
```

- Drop `./gradlew :graph-servers:...` command examples if any.

- [ ] **Step 2: Root `README.md` / `README.ko.md`**

Use Grep for `graph-servers`, `graph.servers`, `Neo4jServer.boltUrl`, `MemgraphServer.driver`, `PostgreSQLAgeServer.instance` under `README.md` and `README.ko.md`. Replace with the new API per the mapping table at the top of this plan.

- [ ] **Step 3: `examples/*/README.md` and `README.ko.md`**

Same Grep-and-replace pass on `examples/code-graph-examples/README*.md` and `examples/linkedin-graph-examples/README*.md`. Keep both language versions in sync (same structural edits, translated content remains).

- [ ] **Step 4: `TODO.md`**

Grep `TODO.md` for `graph-servers`. If found, replace any stale guidance or remove completed items. If not found, skip.

- [ ] **Step 5: Commit**

```bash
git add CLAUDE.md README.md README.ko.md \
        examples/code-graph-examples/README.md examples/code-graph-examples/README.ko.md \
        examples/linkedin-graph-examples/README.md examples/linkedin-graph-examples/README.ko.md \
        TODO.md
git commit -m "docs: update module references after graph-servers removal"
```

---

## Task 17: Stale reference sweep

**Complexity:** low

- [ ] **Step 1: Run the narrow stale-ref regex**

Use the Grep tool with this exact pattern (narrow, excludes new `*.Launcher` API and historical docs):

Pattern: `project\(":graph-servers"\)|io\.bluetape4k\.graph\.servers|Neo4jServer\.(boltUrl|instance)|MemgraphServer\.(boltUrl|driver|instance)|PostgreSQLAgeServer\.instance`

Scope: `README.md`, `README.ko.md`, `CLAUDE.md`, `TODO.md`, `examples/`, `graph/`, `spring-boot3/`, `spring-boot4/`.

Expected: **0 hits**. Skip `docs/superpowers/` (spec/plan) and `CHANGELOG.md` (history) — they keep the old names intentionally.

- [ ] **Step 2: If any hit, fix and re-commit**

For each hit, decide: is the file in scope (test/src/build)? If yes, rewrite per the API mapping table. If it's a historical doc (CHANGELOG-like), add it to the "excluded" list mentally and move on.

- [ ] **Step 3: Re-run search to confirm 0 hits**

Re-run the same Grep pattern. Zero hits required to proceed.

- [ ] **Step 4: Commit any late fixes**

```bash
git add <files>
git commit -m "refactor: clean up remaining graph-servers references"
```

---

## Task 17.5: bluetape4k-patterns 체크리스트 스캔

**Complexity:** low

본 마이그레이션은 import/빌드스크립트 변경이 대부분이나, Memgraph `@BeforeAll`/`@AfterAll` 추가 코드에 대해 패턴 준수 여부를 확인한다.

- [ ] **Step 1: Memgraph 신규 코드 점검**

변경된 Memgraph 테스트 파일들에서 아래 항목을 확인한다:
- `!!` 사용 없음 (null 안전)
- `driver.close()` 누락 없음
- `@TestInstance(PER_CLASS)` + `@BeforeAll` 조합 올바름

- [ ] **Step 2: 로깅 점검**

신규로 추가한 setup/teardown 코드에서 `KLogging`이 필요한 경우 companion object가 있는지 확인 (기존 테스트 클래스에 이미 있을 것임, 추가 없이 유지).

- [ ] **Step 3: 기존 `connectionInitSql` 유지 확인**

Grep으로 AGE 테스트에서 `connectionInitSql` 관련 코드가 의도치 않게 제거되지 않았는지 최종 확인:

```
Pattern: connectionInitSql
Path: graph/graph-age/src/test/, examples/, spring-boot3/, spring-boot4/
```

---

## Task 18: Final build + test verification

**Complexity:** low

- [ ] **Step 1: Clean build**

```bash
./gradlew clean build -x test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Full test run (Docker + Testcontainers — do not skip)**

```bash
./gradlew test --no-parallel
```

Expected: all tests green. If a Memgraph or AGE test flakes due to the image pin change, re-run the module's test task once before declaring failure.

- [ ] **Step 3: Record result to testlog**

Append the following at the TOP of `docs/testlogs/2026-04.md` (create file if missing; preserve any existing content below):

```markdown
## 2026-04-18 — graph-servers → bluetape4k-testcontainers.graphdb migration

- `./gradlew clean build -x test`: PASS
- `./gradlew test --no-parallel`: PASS (<N> tests, <M> modules)
- Migration commits: <sha-range>
- Spec: `docs/superpowers/specs/2026-04-18-graph-servers-migration-design.md`
- Plan: `docs/superpowers/plans/2026-04-18-graph-servers-migration-plan.md`
```

Fill in `<N>`, `<M>`, `<sha-range>` from the actual test output + `git log --oneline feature/graph-servers-migration ^main`.

- [ ] **Step 4: Commit**

```bash
git add docs/testlogs/2026-04.md
git commit -m "docs: record graph-servers migration test results"
```

---

## Task 19: PR creation

**Complexity:** low

- [ ] **Step 1: Push branch**

```bash
git push -u origin feature/graph-servers-migration
```

- [ ] **Step 2: Create PR via gh**

```bash
gh pr create \
  --base main \
  --head feature/graph-servers-migration \
  --title "refactor: replace graph-servers module with bluetape4k-testcontainers graphdb" \
  --body "$(cat <<'EOF'
## Summary

Replaces the in-repo `graph/graph-servers/` module with the `io.bluetape4k.testcontainers.graphdb` package from `bluetape4k-testcontainers`. Drops the module after all consumers are migrated.

## Spec / Plan

- Spec: `docs/superpowers/specs/2026-04-18-graph-servers-migration-design.md`
- Plan: `docs/superpowers/plans/2026-04-18-graph-servers-migration-plan.md`

## Key changes

- 8 `build.gradle.kts` updated: drop `project(":graph-servers")`, add explicit transitive deps (`bluetape4k-testcontainers` declares graphdb deps as `compileOnly`).
- 27 test files updated: import swap `io.bluetape4k.graph.servers.*` → `io.bluetape4k.testcontainers.graphdb.*`, access pattern `*.instance` / `*.boltUrl` → `*.Launcher.<serverName>`.
- Memgraph `driver` ownership moved from singleton into each test class (`@BeforeAll` create, `@AfterAll` close).
- AGE: HikariCP `connectionInitSql` retained (pool connections need `LOAD 'age'` per-connection).
- `graph/graph-servers/` directory deleted.
- `CLAUDE.md`, root + examples READMEs updated.

## Test plan

- [x] `./gradlew clean build -x test` passes
- [x] `./gradlew test --no-parallel` passes
- [x] `docs/testlogs/2026-04.md` updated with result
- [x] Stale-reference grep returns 0 hits (narrow pattern per spec §8)
EOF
)"
```

- [ ] **Step 3: Confirm PR URL returned and note for the user**

Capture the URL from `gh pr create` output. This is the handoff artifact.

---

## Task 20: Worktree cleanup (after PR merge)

**Complexity:** low

> Do NOT run this until the PR is merged.

- [ ] **Step 1: Return to the main worktree**

```bash
cd /Users/debop/work/bluetape4k/bluetape4k-graph
```

- [ ] **Step 2: Remove the feature worktree**

```bash
git worktree remove ../bluetape4k-graph-wt-migration
```

- [ ] **Step 3: Delete the branch (if not auto-deleted by GitHub)**

```bash
git branch -d feature/graph-servers-migration
git push origin --delete feature/graph-servers-migration  # if still present
```

---

## Self-Review

**Spec coverage:**

| Spec requirement | Task |
|------------------|------|
| Worktree setup | Task 1 |
| `Libs.bluetape4k_testcontainers` version check (§7.1) | Task 2 |
| 8 `build.gradle.kts` edits (§2.1.1) | Tasks 3, 4, 5, 9, 11, 12, 13 |
| Neo4j import/call-site migration (§2.1.2) | Tasks 6, 10, 11, 12, 13 |
| Memgraph driver lifecycle move (§5.1 Pattern A/B) | Tasks 7, 10, 11, 12, 13 |
| AGE import/call-site + retain `connectionInitSql` (§9 risk row) | Tasks 8, 10, 11, 12, 13 |
| Delete `graph/graph-servers/` (§6) | Task 15 |
| `settings.gradle.kts` no edit needed (§2.1.5) | Task 15 Step 2 |
| `CLAUDE.md` update (§6.2) | Task 16 Step 1 |
| Root + examples README dual-language update | Task 16 Steps 2–3 |
| Stale-ref narrow pattern (§8) | Task 17 |
| Final build + test + testlog (§8) | Task 18 |
| PR creation referencing spec | Task 19 |
| Worktree remove (§7.2 Step 11) | Task 20 |
| TinkerGraph no-replacement exception (§4.4) | Task 5 Step 2 |
| AGE `age_test` → `test` DB name check (§9 risk row) | Task 8 Step 3 |

No gaps.

**Placeholder scan:** all code blocks show actual replacement code, exact commands, expected outputs. No `TODO` / `fill in` / "handle edge cases" wording.

**Type consistency:** `Driver`, `MemgraphGraphOperations`, `Neo4jGraphOperations`, `AgeGraphOperations`, `MemgraphGraphSuspendOperations` are used consistently. `Neo4jServer.Launcher.neo4j`, `MemgraphServer.Launcher.memgraph`, `PostgreSQLAgeServer.Launcher.postgresqlAge` are the only three Launcher accessors and are used identically across tasks.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-04-18-graph-servers-migration-plan.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints.

Which approach?
