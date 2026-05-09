# graph-falkordb 백엔드 구현 계획

- **작성일**: 2026-04-25
- **작성자**: Claude Code (general-purpose agent / planning phase)
- **상태**: Ready for execution (critic 리뷰 반영 — testlog 경로, plugins 블록, GRAPH.LIST 방향, T7 probe, graph-core 가시성 체크, bluetape4k-patterns 항목, INDEX 명세)
- **관련 Spec**: [`docs/superpowers/specs/2026-04-25-falkordb-backend-design.md`](../specs/2026-04-25-falkordb-backend-design.md)
- **워크트리**: `.worktrees/feature/graph-falkordb/`
- **브랜치**: `feature/graph-falkordb`
- **모듈 경로**: `graph/graph-falkordb/`
- **Gradle 프로젝트**: `:graph-falkordb`
- **패키지**: `io.bluetape4k.graph.falkordb`

---

## 0. 사전 합의 사항 (Spec → Plan 매핑)

- jfalkordb **0.7.0** + Docker `falkordb/falkordb:v4.18.1` 고정.
- 퍼 쿼리 `(driver.graph(name) as GraphImpl).use { ... }` 패턴 — 장기 보관 필드 금지.
- `dropGraph(name)` 구현체는 `GraphImpl.deleteGraph(): String` 사용 (Graph 인터페이스에는 없음).
- Testcontainers 래퍼는 **`src/testFixtures/kotlin/...`** 에 위치, 모든 examples 모듈은 `testImplementation(testFixtures(project(":graph-falkordb")))`.
- 모든 통합 테스트는 UUID 기반 `graphName` 필드로 격리.
- 모든 공개 API는 한글 KDoc, companion object는 `KLogging()` 또는 `KLoggingChannel()`.
- spring-boot4 HealthIndicator 패키지: `org.springframework.boot.health.contributor.HealthIndicator` (CLAUDE.md 기준).
- T17은 **선택적** (bluetape4k-projects 외부 저장소 PR) — T19 빌드 게이트 차단 대상이 아님.

---

## 1. 태스크 분해

각 태스크는 **Task ID / 제목 / Complexity / 의존성 / 변경 파일 / 구현 노트 / 검증** 형식.

---

### T1. `buildSrc/Libs.kt`에 jfalkordb 의존성 추가

- **Complexity**: low
- **Depends on**: T0 (✅ 완료)
- **Files to create/modify**:
  - `buildSrc/src/main/kotlin/Libs.kt` (수정)
- **Implementation notes**:
  - `Versions` object (있다면) 또는 상단 `const val` 규약을 따라 추가:
    ```kotlin
    const val jfalkordb_version = "0.7.0"
    const val jfalkordb = "com.falkordb:jfalkordb:$jfalkordb_version"
    ```
  - 기존 `neo4j_*`/`memgraph` 선언과 같은 섹션에 배치, 알파벳 또는 인접 그룹 정렬.
- **Verification**:
  - `./gradlew help -q` 무오류 통과.
  - `grep -n jfalkordb buildSrc/src/main/kotlin/Libs.kt` 출력 확인.

---

### T2. `graph/graph-falkordb/build.gradle.kts` 작성 + `java-test-fixtures` plugin

- **Complexity**: low
- **Depends on**: T1
- **Files to create/modify**:
  - `graph/graph-falkordb/build.gradle.kts` (신규)
  - 디렉터리: `graph/graph-falkordb/src/{main,test,testFixtures}/kotlin/io/bluetape4k/graph/falkordb/`
  - `graph/graph-falkordb/src/test/resources/junit-platform.properties` (신규 — memgraph 동일 내용 복사)
  - `graph/graph-falkordb/src/test/resources/logback-test.xml` (신규 — memgraph 동일 내용 복사)
- **Implementation notes**:
  - `graph/graph-memgraph/build.gradle.kts`에는 plugins 블록이 없음 — root convention plugin이 `kotlin("jvm")`을 자동 적용. 본 모듈도 동일하게 plugins 블록 없이 작성하고 `id("java-test-fixtures")`만 별도로 적용:
    ```kotlin
    plugins {
        id("java-test-fixtures")
    }
    ```
  - `dependencies`:
    ```kotlin
    api(project(":graph-core"))
    api(Libs.jfalkordb)
    api(Libs.bluetape4k_coroutines)
    api(Libs.kotlinx_coroutines_core)

    testFixturesApi(Libs.bluetape4k_testcontainers)
    testFixturesApi(Libs.testcontainers)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers)
    testImplementation(Libs.kotlinx_coroutines_test)
    testImplementation(testFixtures(project(":graph-falkordb")))
    ```
  - `configurations { testImplementation.get().extendsFrom(compileOnly.get()) }` 동일 패턴 유지.
  - settings.gradle.kts는 `includeModules("graph", false, false)` 자동 인식 — 수정 불필요.
- **Verification**:
  - `./gradlew :graph-falkordb:dependencies --configuration testFixturesRuntimeClasspath -q | head` jfalkordb 미포함, testcontainers 포함 확인.
  - `./gradlew :graph-falkordb:compileKotlin` (T3 직전) 통과.

---

### T3. `FalkorDBRecordMapper` 구현

- **Complexity**: medium
- **Depends on**: T2
- **Files to create/modify**:
  - `graph/graph-falkordb/src/main/kotlin/io/bluetape4k/graph/falkordb/FalkorDBRecordMapper.kt` (신규)
- **Implementation notes**:
  - T0 검증 API 기반 (Node.getLabel(int), getEntityPropertyNames, Edge.getRelationshipType/getSource/getDestination, Path.getNodes/getEdges).
  - 함수: `nodeToVertex(Node)`, `edgeToGraphEdge(Edge)`, `pathToGraphPath(Path)`, `recordToVertex/Edge/Path(Record, key)`.
  - `node.numberOfLabels > 0`이면 `node.getLabel(0)`, 아니면 fallback `"Unknown"` (스펙 §B.3).
  - `getEntityPropertyNames().associateWith { node.getProperty(it)?.value }` — `Property<*>.getValue()` 사용 (T0 확정).
  - `companion object: KLogging()` 또는 `object FalkorDBRecordMapper: KLogging()`.
  - 모든 public 함수 한글 KDoc.
- **Verification**:
  - `./gradlew :graph-falkordb:compileKotlin` 통과.
  - 단위 테스트는 T8 통합 테스트에서 간접 검증.

---

### T4. `FalkorDBGraphOperations` (sync) 구현

- **Complexity**: high
- **Depends on**: T3
- **Files to create/modify**:
  - `graph/graph-falkordb/src/main/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphOperations.kt` (신규)
  - `graph/graph-falkordb/src/main/kotlin/io/bluetape4k/graph/falkordb/internal/FalkorDBSessionSupport.kt` (신규, 공유 유틸)
- **Implementation notes**:
  - 시그니처: `class FalkorDBGraphOperations(private val driver: com.falkordb.Driver, private val graphName: String = DEFAULT_GRAPH_NAME) : GraphOperations`.
  - `companion object : KLogging() { const val DEFAULT_GRAPH_NAME = "bluetape4k"; private val SAFE_IDENTIFIER = Regex("^[A-Za-z_][A-Za-z0-9_]*$") }`
  - `init { graphName.requireNotBlank("graphName") }`
  - **퍼 쿼리 헬퍼**:
    ```kotlin
    private fun graphImpl(): com.falkordb.impl.api.GraphImpl =
        driver.graph(graphName) as com.falkordb.impl.api.GraphImpl
    private inline fun <T> withGraph(block: (com.falkordb.impl.api.GraphImpl) -> T): T =
        graphImpl().use(block)
    ```
  - **GraphSession** (`GraphSession : AutoCloseable` — `createGraph`, `dropGraph`, `graphExists`, `close` 4개 필수):
    - `createGraph(name)` → no-op (FalkorDB lazy 생성), 로그만 남김. 식별자 검증 `SAFE_IDENTIFIER`.
    - `dropGraph(name)` → `(driver.graph(name) as GraphImpl).use { it.deleteGraph() }`. 식별자 검증.
    - `graphExists(name)` → `driver.listGraphs()` 사용 (`Driver` 인터페이스에 존재, T0 확인). 구현:
      ```kotlin
      override fun graphExists(name: String): Boolean =
          runCatching { driver.listGraphs().contains(name) }.getOrElse { false }
      ```
    - `close()` → `/* driver는 외부 소유 — 닫지 않음 */` (Memgraph 패턴 동일).
    - **`listGraphs()`는 `GraphSession` 인터페이스에 없음 — 구현하지 않는다.**
  - **Vertex CRUD**: Memgraph Cypher 패턴 그대로(`CREATE (n:Label $props) RETURN n`, `MATCH (n) WHERE id(n) = toInteger($id) ...`). 1차는 `toInteger` 적용, 통합 테스트 실패 시 직접 정수 바인딩 폴백 (Open Q #2).
  - **Edge CRUD**: `MATCH (a),(b) WHERE id(a)=toInteger($s) AND id(b)=toInteger($e) CREATE (a)-[r:LABEL $props]->(b) RETURN r`.
  - **Traversal**: `neighbors`, `paths`, `outDegree/inDegree` 등 — Memgraph 구현 비교하며 그대로.
  - 모든 쿼리 호출은 `withGraph { it.query(cypher, params) }`.
  - `internal/FalkorDBSessionSupport.kt`: `requireSafeIdentifier(name)`, `extractSingleVertex(rs)`, `extractVertices(rs)` 등 sync/suspend 공용 작은 함수.
  - 모든 public API 한글 KDoc.
- **Verification**:
  - `./gradlew :graph-falkordb:compileKotlin` 통과.
  - T8에서 통합 검증.

---

### T5. `FalkorDBGraphOperations` 알고리즘 메서드

- **Complexity**: medium
- **Depends on**: T4
- **Files to create/modify**:
  - `graph/graph-falkordb/src/main/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphOperations.kt` (T4에서 생성한 파일에 메서드 추가 또는 partial 분리)
- **Implementation notes**:
  - `degreeCentrality`, `bfs`, `dfs`, `detectCycles`, `connectedComponents`, `pageRank` 6종.
  - **[T5 선행 필수]** graph-core fallback 클래스 가시성 확인:
    ```bash
    grep -rn "^internal\|^private\|^public\|^class\|^object" \
      graph/graph-core/src/main/kotlin/ | grep -i "BfsDfsRunner\|UnionFind\|PageRank"
    ```
    `internal`이면 graph-core 측 가시성 변경 PR 먼저 생성 후 진행. `public`이면 그대로 import.
  - **재사용 전략**: 가시성 확인 후 `BfsDfsRunner`/`UnionFind`/`PageRankCalculator` import. FalkorDB Cypher 가능 영역은 직접 쿼리, 불가 영역은 JVM fallback.
  - FalkorDB Cypher 가능 영역(예: `MATCH (n) RETURN n, size((n)--()) AS deg`)은 직접 쿼리, 불가능 영역은 JVM fallback.
  - 모든 알고리즘은 `withGraph { ... }` 안에서 데이터 페치 후 JVM 처리.
- **Verification**:
  - `./gradlew :graph-falkordb:compileKotlin` 통과.
  - T10에서 통합 검증.

---

### T6. `FalkorDBGraphSuspendOperations` 구현

- **Complexity**: high
- **Depends on**: T4 (Operations 시그니처 참고), T3
- **Files to create/modify**:
  - `graph/graph-falkordb/src/main/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphSuspendOperations.kt` (신규)
- **Implementation notes**:
  - 시그니처: `class FalkorDBGraphSuspendOperations(private val driver: Driver, private val graphName: String = FalkorDBGraphOperations.DEFAULT_GRAPH_NAME) : GraphSuspendOperations`.
  - `companion object : KLoggingChannel()`.
  - 헬퍼:
    ```kotlin
    private fun graphImpl() = driver.graph(graphName) as com.falkordb.impl.api.GraphImpl
    private suspend inline fun <T> withGraphIO(crossinline block: (GraphImpl) -> T): T =
        withContext(Dispatchers.IO) { graphImpl().use { block(it) } }
    private fun <T> flowQuery(cypher: String, params: Map<String, Any?>, mapper: (Record) -> T): Flow<T> =
        channelFlow {
            withContext(Dispatchers.IO) {
                graphImpl().use { g ->
                    g.query(cypher, params).forEach { send(mapper(it)) }
                }
            }
        }
    ```
  - 단건은 `withGraphIO { it.query(...).iterator().next() }`, 다건은 `flowQuery` 사용.
  - **`GraphSuspendSession` 3개 메서드** (`createGraph`, `dropGraph`, `graphExists`) — sync 로직을 `withContext(Dispatchers.IO)`로 감싼 동일 구현. `graphExists`는 `withContext(Dispatchers.IO) { driver.listGraphs().contains(name) }`. `close()`는 no-op (driver 외부 소유).
  - 알고리즘 suspend도 `withGraphIO`로 IO 격리 후 Memgraph와 동일 fallback 재사용.
  - **중요**: spec §C 명시대로 "lazy 보장" 표현 금지 — KDoc은 "클라이언트 backpressure"로만 기술.
- **Verification**:
  - `./gradlew :graph-falkordb:compileKotlin` 통과.
  - T9에서 통합 검증.

---

### T7. `FalkorDBServer` Testcontainers 래퍼 (testFixtures)

- **Complexity**: medium
- **Depends on**: T2
- **Files to create/modify**:
  - `graph/graph-falkordb/src/testFixtures/kotlin/io/bluetape4k/graph/falkordb/FalkorDBServer.kt` (신규)
  - `graph/graph-falkordb/src/test/resources/` 디렉터리 확보 (필요 시 `logback-test.xml` 배치)
- **Implementation notes**:
  - `MemgraphServer` 패턴 그대로 복제 후 이미지/포트만 교체.
  - `IMAGE = "falkordb/falkordb"`, `TAG = "v4.18.1"` (latest 금지), `REDIS_PORT = 6379`.
  - `addExposedPorts(REDIS_PORT)`, `withReuse(reuse)`.
  - `WaitAllStrategy()` + `Wait.forLogMessage(".*Ready to accept connections.*", 1)` + `Wait.forListeningPort()` + `withStartupTimeout(Duration.ofSeconds(60))`.
  - **주의**: `"Ready to accept connections"` = Redis 코어 준비, FalkorDB 모듈 로드 완료와 다름. 추가로 `Wait.forLogMessage(".*Graph engine initialized.*", 1)` 또는 startup 후 `GRAPH.QUERY __health__ "RETURN 1"` probe를 `@BeforeAll`에서 실행해 FalkorDB 모듈 ready 검증. probe 실패 시 `Thread.sleep(500)` + retry 최대 5회.
  - `companion object : KLogging()` + `@JvmStatic operator fun invoke(...)` 팩토리.
  - **`withReuse(true)` 효과**: `~/.testcontainers.properties`에 `testcontainers.reuse.enable=true` 설정 필요. 기존 Memgraph 환경에 이미 설정되어 있으면 자동 상속. README.ko.md에 초기 설정 안내 추가.
  - `object Launcher { val falkordb by lazy { FalkorDBServer().apply { start(); ShutdownQueue.register(this) } } }`.
  - 한글 KDoc 포함.
- **Verification**:
  - `./gradlew :graph-falkordb:compileTestFixturesKotlin` 통과.
  - T8 실행 시 컨테이너 정상 기동.

---

### T8. `FalkorDBGraphOperationsTest` 통합 테스트

- **Complexity**: medium
- **Depends on**: T4, T5, T7
- **Files to create/modify**:
  - `graph/graph-falkordb/src/test/kotlin/io/bluetape4k/graph/falkordb/AbstractFalkorDBTest.kt` (신규, 공통 라이프사이클)
  - `graph/graph-falkordb/src/test/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphOperationsTest.kt` (신규)
- **Implementation notes**:
  - `AbstractFalkorDBTest`: `@TestInstance(PER_CLASS)`, `protected val driver = FalkorDB.driver(Launcher.falkordb.host, Launcher.falkordb.port)`, `protected val graphName = "test_${UUID.randomUUID().toString().replace("-","").take(12)}"`.
  - `@AfterAll`: `runCatching { (driver.graph(graphName) as GraphImpl).use { it.deleteGraph() } }; driver.close()`.
  - 테스트 시나리오: 정점 CRUD, 라벨 인덱스 조회, 간선 생성/삭제, neighbors, paths, count 쿼리, 트랜잭션 격리(가능 범위).
  - JUnit5 + Kotest assertion + bluetape4k-assertions 혼용 (CLAUDE.md 컨벤션).
- **Verification**:
  - `./gradlew :graph-falkordb:test --tests "*FalkorDBGraphOperationsTest"` 통과.

---

### T9. `FalkorDBGraphSuspendOperationsTest` 통합 테스트

- **Complexity**: medium
- **Depends on**: T6, T7, T8 (Abstract 클래스 재사용)
- **Files to create/modify**:
  - `graph/graph-falkordb/src/test/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphSuspendOperationsTest.kt` (신규)
- **Implementation notes**:
  - `AbstractFalkorDBTest` 상속 + `kotlinx-coroutines-test`의 `runTest`.
  - 각 테스트마다 `runTest { ops.createVertexSuspend(...) }` 형태.
  - `Flow<GraphVertex>` 다건 메서드는 `.toList()` 후 검증.
  - 동시성 시나리오 1건: `(1..10).map { async { ops.createVertex("Person", mapOf("name" to "u$it")) } }.awaitAll()` — 10-way 병렬, 0 예외, 10개 정점 생성 검증. 풀 고갈 회귀 방지 기준.
- **Verification**:
  - `./gradlew :graph-falkordb:test --tests "*FalkorDBGraphSuspendOperationsTest"` 통과.

---

### T10. `FalkorDBAlgorithmTest`

- **Complexity**: low
- **Depends on**: T5, T7, T8
- **Files to create/modify**:
  - `graph/graph-falkordb/src/test/kotlin/io/bluetape4k/graph/falkordb/FalkorDBAlgorithmTest.kt` (신규)
- **Implementation notes**:
  - degreeCentrality / bfs / dfs / detectCycles / connectedComponents / pageRank 6종 각 1~2 케이스.
  - Memgraph 알고리즘 테스트 데이터셋 시드 그대로 재사용.
- **Verification**:
  - `./gradlew :graph-falkordb:test --tests "*FalkorDBAlgorithmTest"` 통과.

---

### T11. BOM 자동 등록 확인

- **Complexity**: low
- **Depends on**: T2
- **Files to create/modify**:
  - 변경 없음. 검증만.
- **Implementation notes**:
  - `bom/build.gradle.kts`는 `subprojects` 자동 constraint — 수동 수정 불필요.
  - `./gradlew :bluetape4k-graph-bom:generatePomFileForBluetapeGraphPublication` 후 생성된 POM에 `graph-falkordb` artifactId가 `<dependency>`로 포함되는지 확인.
- **Verification**:
  - `./gradlew :bluetape4k-graph-bom:build` 통과.
  - `find bom/build/publications -name "*.xml" -exec grep -l graph-falkordb {} \;` 결과 1개 이상.

---

### T12. spring-boot3 starter FalkorDB AutoConfiguration

- **Complexity**: medium
- **Depends on**: T4, T6
- **Files to create/modify**:
  - `spring-boot3/graph-spring-boot3-starter/build.gradle.kts` (수정)
  - `spring-boot3/graph-spring-boot3-starter/src/main/kotlin/io/bluetape4k/graph/spring/boot3/properties/FalkorDBGraphProperties.kt` (신규 — 기존 `MemgraphGraphProperties.kt`와 동일 패키지)
  - `spring-boot3/graph-spring-boot3-starter/src/main/kotlin/io/bluetape4k/graph/spring/boot3/autoconfigure/GraphFalkorDBAutoConfiguration.kt` (신규 — 기존 `GraphMemgraphAutoConfiguration.kt`와 동일 패키지)
  - `spring-boot3/graph-spring-boot3-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (수정 — 행 추가)
- **Implementation notes**:
  - build.gradle.kts: `compileOnly(project(":graph-falkordb"))` + `testImplementation(project(":graph-falkordb"))` + `testImplementation(testFixtures(project(":graph-falkordb")))`.
  - `FalkorDBGraphProperties(prefix = "bluetape4k.graph.falkordb")` — 패키지: `io.bluetape4k.graph.spring.boot3.properties`. 필드: host/port/username/password/graphName/registerSuspend/registerVirtualThread. **`connectTimeoutMs`, `socketTimeoutMs` 제외** (T0 확인).
  - `GraphFalkorDBAutoConfiguration`: spec §E.2 그대로. `@ConditionalOnClass(Driver::class, FalkorDBGraphOperations::class)` + `@ConditionalOnProperty("bluetape4k.graph.backend"="falkordb")`.
  - 빈: `falkordbDriver`(destroyMethod="close"), `graphOperations`, `graphSuspendOperations` (조건부), `graphVirtualThreadOperations` (조건부, `ops.asVirtualThread()` 호출), `HealthConfig` 내부 `falkordbHealthIndicator`.
  - HealthIndicator는 `driver.graph("__health__").use { it.query("RETURN 1") }`로 ping.
  - SB3 HealthIndicator FQCN: `org.springframework.boot.actuate.health.HealthIndicator`.
- **Verification**:
  - `./gradlew :graph-spring-boot3-starter:compileKotlin` 통과.

---

### T13. spring-boot3 starter 테스트

- **Complexity**: low
- **Depends on**: T12
- **Files to create/modify**:
  - `spring-boot3/graph-spring-boot3-starter/src/test/kotlin/io/bluetape4k/graph/spring/boot3/autoconfigure/GraphFalkorDBAutoConfigurationTest.kt` (신규 — 기존 테스트와 동일 패키지)
- **Implementation notes**:
  - `ApplicationContextRunner` 기반 빈 등록 검증.
  - `bluetape4k.graph.backend=falkordb` 설정 시 `Driver`, `GraphOperations`, `GraphSuspendOperations`, `GraphVirtualThreadOperations` 빈 존재 검증.
  - `register-suspend=false` / `register-virtual-thread=false` 분기 테스트 1건씩.
  - 실제 컨테이너 기동까지 가는 smoke 1건은 `FalkorDBServer.Launcher` 사용 (testFixtures dep 활용).
- **Verification**:
  - `./gradlew :graph-spring-boot3-starter:test --tests "*FalkorDB*"` 통과.

---

### T14. spring-boot4 starter FalkorDB AutoConfiguration + SB4 HealthIndicator 패키지 적용

- **Complexity**: medium
- **Depends on**: T12, T13
- **Files to create/modify**:
  - `spring-boot4/graph-spring-boot4-starter/build.gradle.kts` (수정)
  - `spring-boot4/graph-spring-boot4-starter/src/main/kotlin/io/bluetape4k/graph/spring/boot4/properties/FalkorDBGraphProperties.kt` (신규 — 기존 `MemgraphGraphProperties.kt`와 동일 패키지)
  - `spring-boot4/graph-spring-boot4-starter/src/main/kotlin/io/bluetape4k/graph/spring/boot4/autoconfigure/GraphFalkorDBAutoConfiguration.kt` (신규 — 기존 `GraphMemgraphAutoConfiguration.kt`와 동일 패키지)
  - `spring-boot4/graph-spring-boot4-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (수정)
  - `spring-boot4/graph-spring-boot4-starter/src/test/kotlin/io/bluetape4k/graph/spring/boot4/autoconfigure/GraphFalkorDBAutoConfigurationTest.kt` (신규)
- **Implementation notes**:
  - SB3 코드 복제 후 패키지명만 `boot3` → `boot4`.
  - **HealthIndicator import 변경**: `org.springframework.boot.actuate.health.HealthIndicator` → `org.springframework.boot.health.contributor.HealthIndicator` (CLAUDE.md 명시). 착수 전 기존 Memgraph SB4 코드에서 실제 import 경로 확인:
    ```bash
    grep -rn "HealthIndicator\|boot.health\|boot.actuate.health" \
      spring-boot4/graph-spring-boot4-starter/src/main/kotlin/
    ```
    결과를 그대로 따름. 패키지가 다르면 파일 상단 import만 변경.
  - 그 외 빈 정의/조건/테스트 동일.
- **Verification**:
  - `./gradlew :graph-spring-boot4-starter:test --tests "*FalkorDB*"` 통과.

---

### T15. `examples/code-graph-examples` FalkorDB 통합

- **Complexity**: low
- **Depends on**: T4, T6, T7
- **Files to create/modify**:
  - `examples/code-graph-examples/build.gradle.kts` (수정 — `testImplementation(testFixtures(project(":graph-falkordb")))`. **jfalkordb 직접 의존 금지**)
  - `examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/FalkorDBCodeGraphTest.kt` (신규)
  - `examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/FalkorDBCodeGraphSuspendTest.kt` (신규)
- **Implementation notes**:
  - `AbstractCodeGraphTest`/`AbstractCodeGraphSuspendTest` 상속.
  - `@BeforeAll`에서 `FalkorDBServer.Launcher.falkordb` 기동, `driver = FalkorDB.driver(server.host, server.port)`, `graphName = "code_${UUID.randomUUID()...take(8)}"`, `ops = FalkorDBGraphOperations(driver, graphName)`.
  - `ops.graphName`이 private이므로 외부 접근 불가 → 테스트 클래스에 `private lateinit var graphName: String` 별도 필드.
  - `@AfterAll`에서 `runCatching { ops.dropGraph(graphName) }; driver.close()`.
- **Verification**:
  - `./gradlew :code-graph-examples:test --tests "*FalkorDB*"` 통과.

---

### T16. `examples/linkedin-graph-examples` FalkorDB 통합

- **Complexity**: low
- **Depends on**: T4, T6, T7
- **Files to create/modify**:
  - `examples/linkedin-graph-examples/build.gradle.kts` (수정 — testFixtures 의존성 추가)
  - `examples/linkedin-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/FalkorDBLinkedInGraphTest.kt` (신규)
  - `examples/linkedin-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/FalkorDBLinkedInGraphSuspendTest.kt` (신규)
- **Implementation notes**:
  - T15와 동일 패턴, 추상 클래스만 LinkedIn 계열로 교체.
- **Verification**:
  - `./gradlew :linkedin-graph-examples:test --tests "*FalkorDB*"` 통과.

---

### T17. (선택) `bluetape4k-projects`에 FalkorDBServer 정식 이관 PR

- **Complexity**: medium
- **Depends on**: T7
- **Status**: **OPTIONAL** — T19 빌드 게이트 차단 대상 아님. 본 저장소 PR 머지 후 별도 follow-up 가능.
- **Files to create/modify**:
  - 외부 저장소 `bluetape4k-projects/bluetape4k-testcontainers/...` (별도 PR)
- **Implementation notes**:
  - 기존 `Neo4jServer`/`MemgraphServer`와 동일 디렉터리/네이밍.
  - 본 저장소 testFixtures 버전을 그대로 이관.
  - 머지 시점에 `graph-falkordb`의 testFixtures 코드는 deprecated alias 또는 삭제 후 외부 의존성으로 교체 (별도 follow-up plan).
- **Verification**:
  - 외부 저장소 PR 링크를 본 plan 끝에 기록 (현재는 N/A로 두고 진행).

---

### T18a. `graph-falkordb` README.md / README.ko.md 작성

- **Complexity**: low
- **Depends on**: T15, T16
- **Files to create/modify**:
  - `graph/graph-falkordb/README.md` (신규)
  - `graph/graph-falkordb/README.ko.md` (신규)
- **Implementation notes**:
  - `graph/graph-memgraph/README*.md` 구조 그대로 답습 (개요, Quick Start, Configuration, Testing, References).
  - 코드 스니펫: Driver 생성 → Operations 생성 → 기본 CRUD.
  - References: FalkorDB 공식 문서, jfalkordb GitHub, Docker tag.
- **Verification**:
  - 마크다운 렌더링 확인 (lint 도구가 있다면 통과).

---

### T18b. 루트 README/CLAUDE.md 모듈 매트릭스 행 추가

- **Complexity**: low
- **Depends on**: T18a
- **Files to create/modify**:
  - `README.md` (수정)
  - `README.ko.md` (수정, 존재 시)
  - `CLAUDE.md` (수정 — Project Structure 섹션의 graph 모듈 트리에 `graph-falkordb` 행 추가)
- **Implementation notes**:
  - 모듈 표(있다면) 또는 트리 다이어그램에 `graph-falkordb # FalkorDB (Redis-based) 구현` 한 줄 추가.
- **Verification**:
  - `git diff README.md CLAUDE.md`에 정확한 행 추가만 보일 것.

---

### T18c. starter README FalkorDB 예제 추가 + TODO.md 체크

- **Complexity**: low
- **Depends on**: T14
- **Files to create/modify**:
  - `spring-boot3/graph-spring-boot3-starter/README.md` (수정, 존재 시)
  - `spring-boot4/graph-spring-boot4-starter/README.md` (수정, 존재 시)
  - `TODO.md` (수정 — FalkorDB 항목 체크)
- **Implementation notes**:
  - `bluetape4k.graph.backend=falkordb` + `bluetape4k.graph.falkordb.host/port/...` application.yml 예제.
  - HealthIndicator 활성화 안내.
- **Verification**:
  - 변경 행 수 최소, 다른 백엔드 예제와 동일 형식.

---

### T19. 전체 빌드 + 테스트 통과 + testlog 기록

- **Complexity**: low
- **Depends on**: T1–T16, T18a, T18b, T18c (T17 제외)
- **Files to create/modify**:
  - `docs/testlogs/2026-04.md` (수정 — 최상단에 신규 행 추가. `docs/testlogs/` 복수형 주의)
  - `docs/superpowers/index/2026-04.md` (수정 — 신규 행 추가)
  - `docs/superpowers/INDEX.md` (수정 — 요약 카운트 +1, 월별 보관 항목 수 +1)
- **Implementation notes**:
  - 실행 순서:
    1. `./gradlew clean build -x test`
    2. `./gradlew :graph-falkordb:test`
    3. `./gradlew :graph-spring-boot3-starter:test :graph-spring-boot4-starter:test`
    4. `./gradlew :code-graph-examples:test :linkedin-graph-examples:test`
    5. `./gradlew test` (전체)
  - testlog 항목 양식 (기존 testlog 디렉터리 컨벤션 따름):
    - 실행 명령 / 결과 / 소요 시간 / 환경 / 비고.
  - superpowers index 항목:
    - `| 2026-04-25 | FalkorDB 백엔드 모듈 | [spec](...) | [plan](...) | graph-falkordb, spring-boot3/4-starter, examples | jfalkordb 0.7.0 + 4 개 algorithm + SB autoconfig + examples 통합 | ✅ | feat |`
  - `docs/superpowers/INDEX.md`: ✅ 완료 `2 → 3`, 합계 `2 → 3` 두 곳 모두 갱신.
  - `docs/superpowers/index/2026-04.md`: 항목 수 `2 → 3` 갱신.
- **Verification**:
  - `./gradlew test` 0 failures.
  - testlog 파일 존재 + 결과 기록.
  - INDEX 카운트 일관성.

---

## 2. 의존성 그래프

```
T1 ─→ T2 ─→ T3 ─→ T4 ─→ T5 ─┐
                     │       ├─→ T8 ─→ T9 ─→ T10 ─→ T19
                     │       │
                     ├─→ T6 ─┤
                     │       │
                     │       └─→ T7 ─┤
                     │               │
                     ├─→ T11         ├─→ T15 ─→ T18a ─→ T18b ─→ T19
                     │               │
                     └─→ T12 ─→ T13 ─┴─→ T14 ─→ T18c ─→ T19
                                          │
                                          └─→ T16 ─→ T19

T17 (선택) ← T7  // 본 plan 게이트 외부
```

---

## 3. 회귀 방지 체크리스트 (Step 4-T 직전)

- [ ] `src/testFixtures/kotlin/io/bluetape4k/graph/falkordb/FalkorDBServer.kt` 위치 정확.
- [ ] `src/test/resources/` 디렉터리 존재 (testcontainers reuse 설정 가능 영역).
- [ ] examples 모듈에 `jfalkordb` 직접 의존성 없음 (오직 `testFixtures`만).
- [ ] 모든 `dropGraph` 호출이 `(driver.graph(name) as GraphImpl).use { it.deleteGraph() }` 패턴.
- [ ] `graphImpl()`가 필드가 아닌 함수로만 선언됨 (장기 보관 금지).
- [ ] SB4 HealthIndicator import = `org.springframework.boot.health.contributor.HealthIndicator`.
- [ ] 모든 통합 테스트에 `graphName = "test_${UUID...}"` 격리.
- [ ] 모든 companion object가 `KLogging()` 또는 `KLoggingChannel()`.
- [ ] 모든 public API에 한글 KDoc.
- [ ] `Libs.kt`의 `jfalkordb_version = "0.7.0"`.
- [ ] `FalkorDBServer.TAG = "v4.18.1"` (latest 금지).
- [ ] `FalkorDBGraphProperties`에 `connectTimeoutMs`/`socketTimeoutMs` 없음.
- [ ] `docs/superpowers/index/2026-04.md` 신규 행 추가.
- [ ] `docs/superpowers/INDEX.md` 요약 카운트 갱신 (✅ 완료 + 합계 둘 다).
- [ ] testlog `docs/testlogs/2026-04.md` 최상단 행 추가.
- [ ] **bluetape4k-patterns 체크리스트** 통과 — `requireNotBlank`/`requireSafeIdentifier` 전 public 파라미터 적용, KLogging/KLoggingChannel companion 전수 확인, 매직 리터럴 제거, KDoc 한글 전수 확인.
- [ ] `src/test/resources/junit-platform.properties` + `logback-test.xml` 존재 (memgraph 복사).

---

## 4. 검증 명령 모음

```bash
# 모듈 단위
./gradlew :graph-falkordb:build
./gradlew :graph-falkordb:test
./gradlew :graph-falkordb:compileTestFixturesKotlin

# Starter
./gradlew :graph-spring-boot3-starter:test --tests "*FalkorDB*"
./gradlew :graph-spring-boot4-starter:test --tests "*FalkorDB*"

# Examples
./gradlew :code-graph-examples:test --tests "*FalkorDB*"
./gradlew :linkedin-graph-examples:test --tests "*FalkorDB*"

# BOM (project name: bluetape4k-graph-bom, dir: bom/)
./gradlew :bluetape4k-graph-bom:build

# 전체
./gradlew clean build
./gradlew test
```

---

## 5. 미해결 사항 / 구현 중 결정 보류

1. **`id()` 바인딩** (Spec Open Q #2) — 1차 `toInteger($id)` 시도, 통합 테스트 실패 시 정수 직접 바인딩 폴백. T8 시점에 결정.
2. **shortestPath 지원** (Spec Open Q #3) — FalkorDB가 지원할 수 있음. T4/T5 traversal 구현 시 시도, 미지원이면 Memgraph fallback 그대로.
3. **graph-core fallback 클래스 가시성** (T5) — `BfsDfsRunner`/`UnionFind`/`PageRankCalculator`가 internal일 경우 `graph-core` 측 가시성 변경 PR이 필요할 수 있음. T5 착수 전 grep으로 확인 (T5 구현 노트 참조).
4. **T17 일정** — 본 PR 머지 후 별도 follow-up. 본 plan 게이트 차단 대상 아님.
5. **`listGraphs()`는 `GraphSession` 인터페이스에 없음** — 구현 대상 아님. `graphExists(name)` 내부에서만 `driver.listGraphs()` 활용.

---

## 6. 완료 정의 (Definition of Done)

- 모든 T1–T16, T18a/b/c, T19 ✅.
- T17은 N/A 또는 별도 PR 링크 명시.
- `./gradlew clean build test` 0 failures.
- testlog + superpowers index/INDEX 카운트 갱신 커밋 포함.
- PR 본문에 spec/plan 링크 + 검증 명령 출력 첨부.
