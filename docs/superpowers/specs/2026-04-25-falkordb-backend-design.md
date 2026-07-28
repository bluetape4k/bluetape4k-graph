# graph-falkordb 백엔드 모듈 설계

- **작성일**: 2026-04-25
- **작성자**: Claude Code (general-purpose agent / design phase)
- **상태**: Ready (T0 jar + 코드리뷰 반영 완료 — 퍼쿼리 GraphImpl.use{} 패턴, deleteGraph() API 확정, TAG=v4.18.1 고정, graphName 필드 분리, SB4 HealthIndicator 패키지 차이 명시)
- **관련 워크트리**: `.worktrees/feature/graph-falkordb/`
- **모듈 경로**: `graph/graph-falkordb/`
- **패키지**: `io.bluetape4k.graph.falkordb`

---

## 1. 배경 및 목적

### 1.1 배경

`bluetape4k-graph`는 다중 그래프 DB 백엔드를 단일 추상화(`GraphOperations`/`GraphSuspendOperations`)로 추상화한다. 현재 지원: Neo4j, Memgraph, Apache AGE, TinkerGraph. [FalkorDB](https://www.falkordb.com)는 Redis 기반 인메모리 그래프 DB로, OpenCypher 서브셋을 지원하며 RAG/지식그래프/추천 워크로드에서 빠른 응답시간을 강점으로 한다. FalkorDB 백엔드를 추가하여 에코시스템을 확장한다.

### 1.2 목적

- FalkorDB를 다른 백엔드와 동일한 인터페이스로 사용 가능하도록 한다.
- bluetape4k 에코시스템 컨벤션(`KLogging`/`KLoggingChannel`, `requireNotBlank`, `bluetape4k-testcontainers` 싱글턴 패턴)을 준수한다.
- 기존 Memgraph 구현을 최대한 재활용하여 유지보수 비용을 최소화한다.

### 1.3 성공 기준

- `./gradlew :graph-falkordb:build :graph-falkordb:test` 통과.
- `GraphOperations`/`GraphSuspendOperations` 모든 메서드 통합 테스트 통과.
- `examples/code-graph-examples`, `examples/linkedin-graph-examples`에 FalkorDB 구체 테스트 클래스 추가 후 통과.
- spring-boot3/4 starter에서 `bluetape4k.graph.backend=falkordb` 활성화 시 자동 빈 등록 동작.

---

## 2. 문제, 제약, 미지수

### 2.1 핵심 제약 (Research 기반)

| 항목 | 제약 | 근거 |
|------|------|------|
| 프로토콜 | Redis RESP (port 6379) 기본. Bolt는 experimental — production은 공식 클라이언트(`jfalkordb`) 권장 | FalkorDB 공식 문서 |
| 클라이언트 | `com.falkordb:jfalkordb:0.7.0` 전용 | Neo4j Driver 재사용 불가 |
| API 모델 | 동기 전용 (Reactive Streams 없음) | jfalkordb 0.7.x |
| ID 모델 | `node.getId(): Long` (정수) | OpenCypher `id()` 함수 사용 |
| Cypher 서브셋 | `shortestPath()` 미지원 가능, `WITH`/`UNWIND` 일부 제약 | FalkorDB OpenCypher 호환성 매트릭스 |
| Testcontainers | 공식 Java 모듈 없음 | `GenericContainer` 래퍼 필요 |
| 멀티 DB | 단일 Redis 인스턴스에 다수 그래프 (`GRAPH.QUERY <name> ...`) | RedisGraph 호환 모델 |

### 2.2 미지수

- jfalkordb의 트랜잭션/격리 모델 — 단일 명령 atomic만 보장 가능성 큼.
- `WHERE id(n) = $id`에 정수 바인딩이 필요한지(toInteger 필요 여부) — 1차 구현은 Memgraph와 동일하게 `toInteger($id)`로 시도 후 실패 시 정수 직접 바인딩으로 폴백.
- `properties()` 매핑 시 jfalkordb의 nested type 처리 (e.g. List, Map).
- jfalkordb의 connection pooling 정책 — `Driver extends Closeable`, `Graph extends Closeable` 확인(T0). `driver.graph(name)` 매번 호출 시 Jedis 연결 취득. 풀링은 `DriverImpl` 내부 구현에 의존 — 통합 테스트에서 동시 쿼리 성능 검증 필요.

### 2.3 비목표 (범위 제외)

- FalkorDB 전용 알고리즘(GRAPH.RO_QUERY 등) 별도 노출.
- Cluster/Sentinel 토폴로지 지원 — 1차는 단일 노드.
- TLS/AUTH 고급 옵션 — 기본 host/port/username/password 4가지만.

---

## 3. 설계 리스크 / 실패 모드

| # | 리스크 | 영향 | 완화 |
|---|------|------|------|
| R1 | `GRAPH.QUERY`는 ResultSet 전체를 서버에서 클라이언트로 반환 — 서버 측 materialization은 클라이언트가 제어 불가 | 대용량 결과셋에서 메모리 폭증 | `channelFlow + send()`로 collector backpressure만 제공. 대용량 대응은 쿼리에 `LIMIT/SKIP` 적용, FalkorDB `RESULTSET_SIZE` 설정, 또는 paged query 계약으로 해결. "lazy 보장"이라는 표현 금지. |
| R2 | FalkorDB Cypher가 Memgraph 패턴 일부와 비호환 (예: `MATCH p = ...*1..N` `[r:LABEL*1..N]` 표기 차이) | 통합 테스트 광범위 실패 | 각 호환성 미확인 쿼리에 대해 통합 테스트 우선 작성 → 실패 시 쿼리 변형(예: 변수 길이 패턴 단계별 OPTIONAL MATCH). 미지원 시 JVM fallback (Memgraph가 이미 사용 중인 `BfsDfsRunner`/`UnionFind`/`PageRankCalculator`) 재활용. |
| R3 | `Driver`/`Graph` 라이프사이클 오해로 connection leak | 운영 시 리소스 누수 | `DriverImpl` 내부 `Pool<Jedis>`. `driver.graph(name)` → `GraphImpl` (Jedis 취득). `GraphImpl.close()` = Jedis 풀 반환. **필드 장기 보관 금지** — 퍼 쿼리 `graphImpl().use { }` 패턴으로 즉시 반환. `Driver`는 외부 소유(`destroyMethod = "close"` 단일). |
| R4 | Testcontainers 커스텀 컨테이너에서 RESP 포트 헬스체크 누락 → 테스트 flaky | CI 불안정 | `Wait.forLogMessage(".*Ready to accept connections.*", 1)` + `Wait.forListeningPort()` 조합. 시작 후 `PING` 보내는 strategy 추가 검토. |
| R5 | spring-boot3 vs spring-boot4 양쪽에 동일 코드 중복 → drift | 유지보수 부담 | 양 starter 모두 같은 properties 구조/빈 시그니처/조건 사용. 차이는 `@AutoConfiguration` 메타데이터 위치(`META-INF/spring/...`)뿐 — 기존 Memgraph autoconfig 패턴 그대로 답습. |
| R6 | `examples/`의 `Abstract*Test`가 `ops` 단일 인스턴스 가정 → FalkorDB의 `graphName`별 격리와 충돌 | 테스트 간 데이터 오염 | 각 test class에서 UUID 기반 `graphName` 필드 선언 후 `ops` 생성. `@AfterAll`에서 `ops.dropGraph(graphName)` 호출 — 내부는 `(driver.graph(name) as GraphImpl).use { it.deleteGraph() }`. |

---

## 4. 접근법 비교

### 4.1 Approach A — jfalkordb 동기 클라이언트 + IO 래핑 (채택)

- **요지**: `com.falkordb:jfalkordb`를 그대로 사용, suspend는 `withContext(Dispatchers.IO) { ... }`, Flow는 `channelFlow`로 래핑.
- **장점**: 공식/유일 클라이언트. Memgraph 코드 재사용률 높음(쿼리 문자열, RecordMapper 구조). 학습 곡선 최소.
- **단점**: 진정한 비동기 아님(IO 스레드 점유). Reactive backpressure 없음.

### 4.2 Approach B — Redis 클라이언트 직접 사용 (Lettuce reactive) + RESP 명령 수동 구성

- **요지**: `lettuce-core` reactive 클라이언트로 `GRAPH.QUERY`/`GRAPH.RO_QUERY` 커스텀 커맨드 정의.
- **장점**: 진짜 비동기, backpressure 가능. 기존 bluetape4k Lettuce 의존성 재사용 가능.
- **단점**: GraphResult 파싱(헤더/데이터/메타) 직접 구현 필요. jfalkordb의 RecordMapper/Path 객체 못 씀. 유지보수 비용 폭증. **거부**.
- **거부 이유**: bluetape4k 컨벤션은 "공식 SDK 우선 + 얇은 어댑터"이며 raw protocol 핸들링은 차별점이 없는 노이즈. R2 리스크가 모든 쿼리로 확장됨.

### 4.3 Approach C — RedisGraph 호환 클라이언트 (jredisgraph) 사용

- **요지**: 구 `com.redislabs:jredisgraph` 클라이언트 사용 (FalkorDB와 RESP 호환).
- **장점**: 더 오래된 라이브러리, 안정성.
- **단점**: 아카이브됨(2023년 이후 미유지보수). FalkorDB 신규 기능 미지원. 보안 패치 없음. **거부**.
- **거부 이유**: 활성 유지보수가 백엔드 선택의 1차 기준. bluetape4k는 deprecated/EOL 의존성을 의도적으로 회피한다.

→ **Approach A 채택**.

---

## 5. 설계 섹션

### A. 모듈 구조

#### A.1 `settings.gradle.kts`

`includeModules("graph", false, false)` 가 `graph/` 하위의 `build.gradle.kts`를 가진 디렉터리를 자동 등록한다. **별도 수정 불필요** — `graph/graph-falkordb/build.gradle.kts`만 생성하면 `:graph-falkordb` 프로젝트로 자동 인식된다.

#### A.2 `graph/graph-falkordb/build.gradle.kts`

```kotlin
configurations {
    testImplementation.get().extendsFrom(compileOnly.get())
}

dependencies {
    api(project(":graph-core"))

    api(Libs.jfalkordb)                              // buildSrc/Libs.kt에 신규 추가

    api(Libs.bluetape4k_coroutines)
    api(Libs.kotlinx_coroutines_core)

    testImplementation(Libs.bluetape4k_junit5)
    testImplementation(Libs.bluetape4k_testcontainers)
    testImplementation(Libs.testcontainers)
    testImplementation(Libs.kotlinx_coroutines_test)
}
```

#### A.3 `buildSrc/src/main/kotlin/Libs.kt` 추가 항목

```kotlin
// FalkorDB
const val jfalkordb_version = "0.7.0"
const val jfalkordb = "com.falkordb:jfalkordb:$jfalkordb_version"
```

(기존 `Versions` object 컨벤션을 따라 `Versions.jfalkordb`로 분리해도 무방.)

#### A.4 BOM 등록

`bom/build.gradle.kts`가 `subprojects` 전체를 자동 constraint로 포함하므로 **`graph-falkordb` 모듈 디렉터리 추가만으로 BOM에 자동 등록된다** — 별도 수정 불필요. (기존 graph 모듈과 동일한 자동 등록 방식.)

#### A.5 디렉터리 레이아웃

```
graph/graph-falkordb/
├── build.gradle.kts
└── src/
    ├── main/kotlin/io/bluetape4k/graph/falkordb/
    │   ├── FalkorDBGraphOperations.kt
    │   ├── FalkorDBGraphSuspendOperations.kt
    │   ├── FalkorDBRecordMapper.kt
    │   └── internal/
    │       └── FalkorDBSessionSupport.kt          # Graph 헬퍼 유틸
    ├── testFixtures/kotlin/io/bluetape4k/graph/falkordb/
    │   └── FalkorDBServer.kt                      # Testcontainers 래퍼 (examples에서 재사용)
    └── test/kotlin/io/bluetape4k/graph/falkordb/
        ├── AbstractFalkorDBTest.kt                # 공통 driver/graphName 라이프사이클
        ├── FalkorDBGraphOperationsTest.kt
        ├── FalkorDBGraphSuspendOperationsTest.kt
        └── FalkorDBAlgorithmTest.kt
```

---

### B. 핵심 클래스 설계

#### B.1 `FalkorDBGraphOperations`

```kotlin
class FalkorDBGraphOperations(
    private val driver: com.falkordb.Driver,
    private val graphName: String = DEFAULT_GRAPH_NAME,
) : GraphOperations {

    companion object : KLogging() {
        const val DEFAULT_GRAPH_NAME = "bluetape4k"
        private val SAFE_IDENTIFIER = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    }

    init {
        graphName.requireNotBlank("graphName")
    }

    // driver.graph(name) → GraphImpl (DriverImpl 내부 Pool<Jedis>에서 연결 취득).
    // GraphImpl.close() = Jedis를 풀에 반환. 필드로 장기 보관 금지 — 연결 점유 = 풀 고갈.
    // 퍼 쿼리 패턴: 매 호출마다 driver.graph() 취득 + use {} 로 즉시 반환.
    private fun graphImpl(): com.falkordb.impl.api.GraphImpl =
        driver.graph(graphName) as com.falkordb.impl.api.GraphImpl

    private fun <T> withGraph(block: (com.falkordb.impl.api.GraphImpl) -> T): T =
        graphImpl().use { block(it) }

    // GraphSession: createGraph는 no-op (FalkorDB lazy 생성)
    // dropGraph → GraphImpl.deleteGraph(): String 사용 (T0+코드리뷰 확정).
    //   graphImpl().use { it.deleteGraph() }
    // 그 외 메서드는 Memgraph Cypher 패턴 그대로, ID 파라미터는 Long.
}
```

- **실제 타입**: `com.falkordb.Driver`, `com.falkordb.Graph`, `com.falkordb.ResultSet` (T0에서 jar 검증 후 확정).
- **driver 소유권**: 외부 소유(`close()`는 AutoConfiguration이 destroyMethod로만 호출). Memgraph와 동일.
- **Graph 인스턴스**: `driver.graph(name)` 반환값은 경량 핸들 — 매 쿼리마다 재생성 불필요. 필드로 보관.
- **쿼리 실행**: `graph.query(cypher, params)` → `ResultSet`. 모든 쿼리는 `withGraph { it.query(...) }`로 감싼다.

#### B.2 `FalkorDBGraphSuspendOperations`

```kotlin
class FalkorDBGraphSuspendOperations(
    private val driver: com.falkordb.Driver,
    private val graphName: String = FalkorDBGraphOperations.DEFAULT_GRAPH_NAME,
) : GraphSuspendOperations {

    companion object : KLoggingChannel()

    // driver.graph(name) → GraphImpl (Pool<Jedis>에서 연결 취득).
    // 필드 장기 보관 금지. 퍼 쿼리 use {} 패턴으로 즉시 반환.
    private fun graphImpl(): com.falkordb.impl.api.GraphImpl =
        driver.graph(graphName) as com.falkordb.impl.api.GraphImpl

    private suspend fun <T> withGraphIO(block: (com.falkordb.impl.api.GraphImpl) -> T): T =
        withContext(Dispatchers.IO) {
            graphImpl().use { block(it) }
        }

    private fun <T> flowQuery(
        cypher: String,
        params: Map<String, Any?>,
        mapper: (com.falkordb.Record) -> T,
    ): Flow<T> = channelFlow {
        withContext(Dispatchers.IO) {
            // GRAPH.QUERY는 ResultSet을 서버에서 전부 수신 후 반환(클라이언트 측 스트리밍 없음).
            // channelFlow + send()는 collector 속도 조절(클라이언트 backpressure)에 효과적.
            // 서버 측 대용량 대응은 쿼리에 LIMIT/SKIP 또는 FalkorDB RESULTSET_SIZE 설정으로 처리.
            // 퍼 쿼리 use {}: ResultSet 수집 완료 후 Jedis 반환.
            graphImpl().use { graph ->
                graph.query(cypher, params).forEach { record ->
                    send(mapper(record))
                }
            }
        }
    }
```

- 모든 suspend 메서드 = `withContext(Dispatchers.IO)` + 동기 호출.
- `Flow<T>` = `channelFlow { withContext(IO) { rs.forEach { send(mapper(it)) } } }` — `send()`로 collector backpressure 지원. 단, `graph.query()` 자체가 ResultSet 전체를 반환하므로 **서버 응답 materialization은 클라이언트에서 제어 불가**. 대용량 대응은 Cypher `SKIP/LIMIT` 또는 FalkorDB `RESULTSET_SIZE` 설정으로 처리.
- 키 차이: Memgraph는 `ReactiveSession` + `awaitSingle/asFlow`, FalkorDB는 IO 디스패처 래핑.

#### B.3 `FalkorDBRecordMapper`

Memgraph 매퍼와 1:1 대응. 타입만 교체:

```kotlin
object FalkorDBRecordMapper : KLogging() {
    // T0 검증된 실제 API:
    //   Node.getId(): Long, Node.getLabel(int): String, Node.getNumberOfLabels(): Int
    //   Node.getEntityPropertyNames(): Set<String>, Node.getProperty(name): Property<?>
    //   Edge.getId(): Long, Edge.getRelationshipType(): String
    //   Edge.getSource(): Long, Edge.getDestination(): Long
    //   Path.getNodes(): List<Node>, Path.getEdges(): List<Edge>
    //   Record.getValue(String): T (제네릭), Record.getString(String): String

    fun nodeToVertex(node: com.falkordb.graph_entities.Node): GraphVertex {
        val id = GraphElementId(node.getId().toString())
        // labels() 없음 — 인덱스 접근: getLabel(0)
        val label = if (node.numberOfLabels > 0) node.getLabel(0) else "Unknown"
        val props = node.getEntityPropertyNames().associateWith { name ->
            node.getProperty(name)?.getValue()
        }
        return GraphVertex(id, label, props)
    }

    fun edgeToGraphEdge(edge: com.falkordb.graph_entities.Edge): GraphEdge {
        return GraphEdge(
            id = GraphElementId(edge.getId().toString()),
            label = edge.getRelationshipType(),
            startId = GraphElementId(edge.getSource().toString()),
            endId = GraphElementId(edge.getDestination().toString()),
            properties = edge.getEntityPropertyNames().associateWith { name ->
                edge.getProperty(name)?.getValue()
            },
        )
    }

    fun pathToGraphPath(path: com.falkordb.graph_entities.Path): GraphPath { /* ... */ }

    fun recordToVertex(record: com.falkordb.Record, key: String = "n"): GraphVertex =
        nodeToVertex(record.getValue(key))
    fun recordToEdge(record: com.falkordb.Record, key: String = "r"): GraphEdge =
        edgeToGraphEdge(record.getValue(key))
    fun recordToPath(record: com.falkordb.Record, key: String = "p"): GraphPath =
        pathToGraphPath(record.getValue(key))
}
```

(T0 검증 완료. `com.falkordb.graph_entities.Node/Edge/Path` 실측 타입 기준.)

#### B.4 `FalkorDBSessionSupport` (internal)

- 공통 `requireSafeIdentifier`, `sanitizeLabel`, `runQuery`/`flowQuery` 등 sync/suspend 양쪽이 공유하는 작은 유틸을 모아 중복 제거.
- 외부 노출 X (`internal` 가시성).

---

### C. Suspend / Flow 설계

#### C.1 패턴 매트릭스

| 메서드 종류 | 반환 타입 | 구현 패턴 |
|---|---|---|
| 단건 (`createVertex`, `findVertexById`, `updateVertex`, `countVertices`) | `T` / `T?` / `Long` | `suspend` + `withContext(Dispatchers.IO) { graph.query(...).single() }` |
| 다건 컬렉션 (`findVerticesByLabel`, `neighbors`, `allPaths`) | `Flow<T>` | `channelFlow { withContext(IO) { rs.forEach { send(mapper(it)) } } }` — 클라이언트 backpressure. 서버 대용량 대응은 `LIMIT/SKIP` 또는 `RESULTSET_SIZE` 설정 |
| 삭제 (`deleteVertex`, `deleteEdge`) | `Boolean` | suspend + IO. 결과는 `ResultSet.statistics.nodesDeleted()` 등. |
| 알고리즘 (`bfs`, `dfs`, `pageRank`, `connectedComponents`, `detectCycles`) | `List<...>` | suspend + IO. JVM fallback 재활용 (Memgraph와 동일). |

#### C.2 Memgraph(Reactive) vs FalkorDB(IO) 차이

| 측면 | Memgraph | FalkorDB |
|---|---|---|
| 세션 객체 | `ReactiveSession` (Publisher 기반) | `GraphImpl` (sync, Pool<Jedis>에서 취득. 퍼 쿼리 `use {}` 반환) |
| 단건 await | `result.records().awaitSingle()` | `graph.query(...).iterator().next()` (IO 안에서) |
| 컬렉션 스트리밍 | `result.records().asFlow()` | `channelFlow { withContext(IO) { graph.query(...).forEach { send(mapper(it)) } } }` |
| 취소 안전성 | `NonCancellable + s.close()` | `GraphImpl extends Closeable`. 퍼 쿼리 `graphImpl().use { }` 패턴 — 취소/예외 시에도 Jedis 풀 반환 보장. IO 취소 시 블로킹 쿼리 중단 불가 — jfalkordb 자체 한계. |
| 백프레셔 | 네이티브 (Reactive Streams) | 클라이언트 수준만 (`channelFlow send()`). 서버 ResultSet은 이미 materialized. |

→ `channelFlow`로 클라이언트 backpressure 구현. 서버 측 materialization은 `GRAPH.QUERY` 모델 특성상 클라이언트가 제어 불가 — 대용량 대응은 `SKIP/LIMIT` paged query 또는 `RESULTSET_SIZE` FalkorDB 설정으로 처리.

**퍼 쿼리 use {} 성능**: `driver.graph(graphName)` 매 호출 = `Pool<Jedis>`에서 연결 취득. 풀 크기 내에서는 오버헤드 최소 (Jedis 생성 없음, 소켓 재사용). 풀 고갈 시 대기 발생 — 통합 테스트에서 동시성 검증 필요. 풀 설정은 `FalkorDB.driver()` 내부 `JedisPool` 기본값에 의존 (0.7.0 미노출).

---

### D. Testcontainers 설계

#### D.1 위치

`graph/graph-falkordb/src/testFixtures/kotlin/io/bluetape4k/graph/falkordb/FalkorDBServer.kt`

1차 위치: `testFixtures` 소스셋 — `java-test-fixtures` plugin 활성화로 `examples/` 모듈에서 `testImplementation(testFixtures(project(":graph-falkordb")))` 으로 재사용 가능.  
추후: `bluetape4k-projects` testcontainers 모듈로 이관 (기존 `Neo4jServer`, `MemgraphServer`와 동일 디렉터리).

#### D.2 클래스 설계 (MemgraphServer 패턴 그대로)

```kotlin
class FalkorDBServer private constructor(
    imageName: DockerImageName,
    useDefaultPort: Boolean = false,
    reuse: Boolean = true,
) : GenericContainer<FalkorDBServer>(imageName), GenericServer, PropertyExportingServer {

    companion object : KLogging() {
        const val IMAGE = "falkordb/falkordb"
        const val TAG = "v4.18.1"          // Docker Hub 확인 기준 최신 안정 (2026-04-12). latest 금지.
        const val NAME = "falkordb"
        const val REDIS_PORT = 6379

        @JvmStatic operator fun invoke(...): FalkorDBServer = ...
    }

    override val port: Int get() = getMappedPort(REDIS_PORT)
    override val url: String get() = "redis://$host:$port"
    val redisUrl: String get() = url
    override val propertyNamespace: String = NAME

    init {
        addExposedPorts(REDIS_PORT)
        withReuse(reuse)
        waitingFor(
            WaitAllStrategy()
                .withStrategy(Wait.forLogMessage(".*Ready to accept connections.*", 1))
                .withStrategy(Wait.forListeningPort())
                // FalkorDB 모듈 로드 확인: Redis 코어 ready != FalkorDB 모듈 등록 완료
                // GRAPH.QUERY __health__ "RETURN 1" 으로 모듈 ready 검증
                .withStartupTimeout(Duration.ofSeconds(60))
        )
        if (useDefaultPort) exposeCustomPorts(REDIS_PORT)
    }

    object Launcher {
        val falkordb: FalkorDBServer by lazy {
            FalkorDBServer().apply {
                start()
                ShutdownQueue.register(this)
            }
        }
    }
}
```

#### D.3 테스트 격리 전략

각 테스트 클래스가 `graphName = "test_${UUID.randomUUID().toString().replace("-", "").take(12)}"` 사용.  
`@AfterAll`에서 `ops.dropGraph(graphName)` 호출 — 내부 구현: `(driver.graph(name) as GraphImpl).use { it.deleteGraph() }`. `GraphImpl.deleteGraph()` 는 `Graph` 인터페이스엔 없지만 구현체에 public 메서드로 존재(T0+코드리뷰 확정).

```kotlin
abstract class AbstractFalkorDBTest {
    protected val driver: com.falkordb.Driver = FalkorDB.driver(
        FalkorDBServer.Launcher.falkordb.host,
        FalkorDBServer.Launcher.falkordb.port,
    )
    protected val graphName = "test_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    // graph 필드: driver.graph(graphName) — Graph 타입 (T0에서 확정)
    protected val graph: com.falkordb.Graph = driver.graph(graphName)

    @AfterAll fun cleanup() {
        // GraphImpl.deleteGraph() 사용 (T0+코드리뷰 확정).
        // Graph 인터페이스엔 없지만 GraphImpl 구현체에 public deleteGraph(): String 존재.
        runCatching {
            (driver.graph(graphName) as com.falkordb.impl.api.GraphImpl).use { it.deleteGraph() }
        }
        driver.close()
    }
}
```

---

### E. Spring Boot AutoConfiguration 설계

#### E.1 `FalkorDBGraphProperties` (spring-boot3 + spring-boot4 동일)

```kotlin
@ConfigurationProperties(prefix = "bluetape4k.graph.falkordb")
data class FalkorDBGraphProperties(
    var host: String = "localhost",
    var port: Int = 6379,
    var username: String = "",
    var password: String = "",
    var graphName: String = "bluetape4k",
    // T0 검증: FalkorDB.driver(host, port) / driver(host, port, user, pass) — timeout 파라미터 없음.
    // 아래 두 프로퍼티는 jfalkordb 0.7.0 Driver 팩토리에 연결되지 않음 → 제거 권장.
    // 유지한다면 향후 jfalkordb 업그레이드 또는 Jedis Config 직접 생성 시 대비용으로만 보관.
    // var connectTimeoutMs: Long = 5_000L,  // ← 제거 또는 주석 처리
    // var socketTimeoutMs: Long = 30_000L,  // ← 제거 또는 주석 처리
    var registerSuspend: Boolean = true,
    var registerVirtualThread: Boolean = true,
)
```

#### E.2 `GraphFalkorDBAutoConfiguration`

```kotlin
@AutoConfiguration
@ConditionalOnClass(com.falkordb.Driver::class, FalkorDBGraphOperations::class)
@ConditionalOnProperty(prefix = "bluetape4k.graph", name = ["backend"], havingValue = "falkordb")
@EnableConfigurationProperties(FalkorDBGraphProperties::class)
class GraphFalkorDBAutoConfiguration {

    companion object : KLogging()

    @Bean(name = ["falkordbDriver"], destroyMethod = "close")
    @ConditionalOnMissingBean(com.falkordb.Driver::class)
    fun falkordbDriver(props: FalkorDBGraphProperties): com.falkordb.Driver {
        log.info { "Creating FalkorDB Driver: host=${props.host}:${props.port}" }
        return if (props.username.isBlank())
            FalkorDB.driver(props.host, props.port)
        else
            FalkorDB.driver(props.host, props.port, props.username, props.password)
    }

    @Bean
    @ConditionalOnMissingBean(GraphOperations::class)
    fun graphOperations(driver: com.falkordb.Driver, props: FalkorDBGraphProperties): GraphOperations =
        FalkorDBGraphOperations(driver, props.graphName)

    @Bean
    @ConditionalOnMissingBean(GraphSuspendOperations::class)
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.falkordb",
        name = ["register-suspend"], havingValue = "true", matchIfMissing = true,
    )
    fun graphSuspendOperations(driver: com.falkordb.Driver, props: FalkorDBGraphProperties)
        : GraphSuspendOperations =
        FalkorDBGraphSuspendOperations(driver, props.graphName)

    @Bean
    @ConditionalOnMissingBean(GraphVirtualThreadOperations::class)
    @ConditionalOnProperty(
        prefix = "bluetape4k.graph.falkordb",
        name = ["register-virtual-thread"], havingValue = "true", matchIfMissing = true,
    )
    fun graphVirtualThreadOperations(ops: GraphOperations): GraphVirtualThreadOperations =
        ops.asVirtualThread()   // import io.bluetape4k.graph.vt.asVirtualThread (graph-core 확장 함수)

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = ["org.springframework.boot.actuate.health.HealthIndicator"])
    class HealthConfig {
        @Bean
        @ConditionalOnMissingBean
        fun falkordbHealthIndicator(driver: com.falkordb.Driver): HealthIndicator =
            HealthIndicator {
                runCatching {
                    // Graph extends Closeable (T0 검증) → use {} 사용 가능.
                    // health check 전용으로 매번 새 GraphContextGenerator 취득 + use{} 로 자원 반환.
                    driver.graph("__health__").use { it.query("RETURN 1") }
                    Health.up().withDetail("backend", "falkordb").build()
                }.getOrElse { Health.down(it).build() }
            }
    }
}
```

#### E.3 등록

- `spring-boot3`: `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`에 `io.bluetape4k.graph.spring.boot3.autoconfigure.GraphFalkorDBAutoConfiguration` 추가.
- `spring-boot4`: 동일 파일(spring-boot4 모듈 경로)에 추가.

#### E.4 spring-boot3 vs spring-boot4 차이

| 측면 | spring-boot3 | spring-boot4 |
|---|---|---|
| 패키지 | `io.bluetape4k.graph.spring.boot3.*` | `io.bluetape4k.graph.spring.boot4.*` |
| HealthIndicator FQCN | `org.springframework.boot.actuate.health.HealthIndicator` | **패키지 변경**: `boot.health.contributor.HealthIndicator` (CLAUDE.md 명시). T14에서 실제 import 경로 확인 필수. |
| `@AutoConfiguration` | Spring Boot 3.5.x 호환 | Spring Boot 4.0.x 호환 |
| 코드 차이 | 패키지명/import 경로뿐 | 동일 |

→ **spring-boot4는 spring-boot3 코드 그대로 복제 + 패키지 변경 + SB4 HealthIndicator import 수정.** 이는 기존 Memgraph/Neo4j/AGE 코드도 동일하게 운영 중인 패턴.

---

### F. Examples 연동

#### F.1 `examples/code-graph-examples`

- `build.gradle.kts`: `testImplementation(testFixtures(project(":graph-falkordb")))` 추가 — FalkorDBServer 재사용. `jfalkordb` 직접 선언 불필요 (transitive). examples는 테스트 전용이므로 `implementation` 대신 `testImplementation` 사용.
- 신규 테스트:
  - `FalkorDBCodeGraphTest : AbstractCodeGraphTest()`
  - `FalkorDBCodeGraphSuspendTest : AbstractCodeGraphSuspendTest()`

```kotlin
class FalkorDBCodeGraphTest : AbstractCodeGraphTest() {
    private lateinit var driver: com.falkordb.Driver
    override lateinit var ops: FalkorDBGraphOperations
    // graphName을 필드로 분리 — ops.graphName(private val)에 외부 접근 불가 (컴파일 오류 방지)
    private lateinit var graphName: String

    @BeforeAll fun startServer() {
        val server = FalkorDBServer.Launcher.falkordb
        driver = FalkorDB.driver(server.host, server.port)
        graphName = "code_${UUID.randomUUID().toString().replace("-", "").take(8)}"
        ops = FalkorDBGraphOperations(driver, graphName)
    }

    @AfterAll fun stopServer() {
        // dropGraph(name: String) — 필드 graphName 사용 (ops.graphName은 private)
        runCatching { ops.dropGraph(graphName) }
        driver.close()
    }
}
```

#### F.2 `examples/linkedin-graph-examples`

동일 패턴으로 `FalkorDBLinkedInGraphTest`, `FalkorDBLinkedInGraphSuspendTest` 추가.

#### F.3 `Abstract*Test` 변경 필요성 검토

`AbstractCodeGraphTest`/`AbstractLinkedInGraphTest`가 단일 `ops: GraphOperations`만 다루고 격리는 구체 클래스의 graphName으로 처리되면 변경 불필요. (현재 Memgraph 구현이 이 가정으로 동작하므로 FalkorDB도 그대로 적용.)

---

## 6. 구현 태스크 (Draft, Complexity 레이블 포함)

| # | 태스크 | Complexity | 의존성 |
|---|---|---|---|
| T0 | ~~**[선행 필수]**~~ **완료 (2026-04-25)** — jfalkordb 0.7.0 jar `jar tf` + `javap` 검증 완료. Driver/Graph/Node/Edge/Path 실측 API 확정. spec B/C/D/E/F 갱신 완료. | small | ✅ |
| T1 | `buildSrc/Libs.kt`에 `jfalkordb` 의존성 추가 | trivial | T0 |
| T2 | `graph/graph-falkordb/build.gradle.kts` 작성 + `java-test-fixtures` plugin 포함 (FalkorDBServer testFixtures 노출용) | trivial | T1 |
| T3 | `FalkorDBRecordMapper` 구현 (Node/Edge/Path → 모델, T0 확정 타입 기준) | small | T2 |
| T4 | `FalkorDBGraphOperations` 구현 (sync, GraphSession + Vertex/Edge/Traversal) | medium | T3 |
| T5 | `FalkorDBGraphOperations` 알고리즘 메서드 (degree/bfs/dfs/cycle/components/pageRank) — graph-core fallback 클래스 가시성 확인 후 재활용 | medium | T4 |
| T6 | `FalkorDBGraphSuspendOperations` 구현 (suspend + `channelFlow` IO 래퍼, collector backpressure 제공. `graph.query()` ResultSet은 이미 materialized — 대용량 보호는 `LIMIT/SKIP`/`RESULTSET_SIZE`/paged contract로 처리) | medium | T4 |
| T7 | `FalkorDBServer` Testcontainers 래퍼 (`src/testFixtures/kotlin/...`) — `WaitAllStrategy` 헬스체크 포함. **`TAG = "latest"` 금지 — FalkorDB 최신 안정 버전을 확인해 `TAG` 상수에 고정 후 PR 머지** | small | T2 |
| T8 | `FalkorDBGraphOperationsTest` 통합 테스트 작성 (bluetape4k-assertions + JUnit5) | medium | T4, T7 |
| T9 | `FalkorDBGraphSuspendOperationsTest` 통합 테스트 (kotlinx-coroutines-test) | medium | T6, T7 |
| T10 | `FalkorDBAlgorithmTest` (PageRank/BFS/DFS/Cycle/Components) | small | T5, T7 |
| T11 | `bom/build.gradle.kts` — 자동 등록 확인 (수동 수정 불필요, 단 BOM 빌드 후 artifact 포함 여부 검증) | trivial | T2 |
| T12 | spring-boot3 starter `build.gradle.kts`에 `compileOnly(project(":graph-falkordb"))` + `testImplementation(project(":graph-falkordb"))` 추가. `FalkorDBGraphProperties`(`connectTimeoutMs`/`socketTimeoutMs` 제거 — T0에서 팩토리 미지원 확인) + `GraphFalkorDBAutoConfiguration` + AutoConfiguration.imports 등록. 필수 import: `io.bluetape4k.graph.vt.asVirtualThread`, `GraphOperations`, `GraphSuspendOperations`, `GraphVirtualThreadOperations` | medium | T0, T4, T6 |
| T13 | spring-boot3 starter 테스트 (`GraphFalkorDBAutoConfigurationTest`) | small | T12 |
| T14 | spring-boot4 starter `build.gradle.kts`에 `compileOnly(project(":graph-falkordb"))` + `testImplementation(project(":graph-falkordb"))` 추가. spring-boot3 코드 복제(패키지만 변경) + SB4 actuator 패키지 변경 여부 확인 후 적용 + 테스트 | medium | T12, T13 |
| T15 | `examples/code-graph-examples` FalkorDB 통합 (구체 테스트 2개 + build.gradle.kts에 `testImplementation(testFixtures(project(":graph-falkordb")))`) | small | T4, T6, T7 |
| T16 | `examples/linkedin-graph-examples` FalkorDB 통합 (구체 테스트 2개 + build.gradle.kts에 `testImplementation(testFixtures(project(":graph-falkordb")))`) | small | T4, T6, T7 |
| T17 | (선택) bluetape4k-projects 저장소에 `FalkorDBServer` 정식 이관 PR | medium | T7 |
| T18a | **신규** `graph/graph-falkordb/README.md` + `README.ko.md` 작성 (다른 백엔드 모듈과 동일 수준) | small | T15, T16 |
| T18b | 루트 `README.md`, `README.ko.md`, `CLAUDE.md` 모듈 매트릭스에 `graph-falkordb` 행 추가 | trivial | T18a |
| T18c | `spring-boot3/4 starter README`에 FalkorDB 설정 예제 추가, `TODO.md` 완료 체크 | trivial | T14 |
| T19 | `./gradlew clean build test` 전체 통과 확인 + 테스트 로그 기록 | small | All |

---

## 7. 미결 사항 (Open Questions)

1. ~~**jfalkordb 패키지명/타입명 정확성**~~ — **T0 완료 (2026-04-25)**. 실측 결과:
   - `Driver extends Closeable`, `graph(name)` → `GraphContextGenerator extends Graph`
   - `Graph extends Closeable` — `use {}` 가능
   - `Graph` 인터페이스에 `delete()` 없음 → `dropGraph`는 Jedis 저수준 명령으로 구현
   - `Node.getLabel(int)`, `Node.getId(): Long`, `Node.getEntityPropertyNames(): Set<String>`
   - `Edge.getRelationshipType(): String`, `Edge.getSource(): Long`, `Edge.getDestination(): Long`
   - `ResultSet extends Iterable<Record>`, `Record.getValue(String): T`
   - `FalkorDB.driver(host, port)` / `driver(host, port, user, pass)` 팩토리 확인
2. **`id()` 함수 바인딩** — Memgraph는 `id(n) = toInteger($id)`. FalkorDB는 `WHERE id(n) = $id`로 정수 직접 바인딩이 정상일 가능성. 1차는 Memgraph 패턴 그대로 두고, 통합 테스트에서 실패 시 보정.
3. **shortestPath 지원 여부** — FalkorDB는 RedisGraph 계보로 `shortestPath()` 함수를 지원하는 것으로 알려짐(Memgraph는 미지원). 지원되면 Neo4j 패턴(`shortestPath((a)-[*..N]-(b))`)으로 단순화 가능.
4. ~~**`testFixtures` vs 자체 테스트 컨테이너**~~ — **결정됨**: T2/T7/T15/T16에서 `testFixtures` 소스셋 채택. `examples/`에서 `testImplementation(testFixtures(project(":graph-falkordb")))` 로 재사용.
5. **이미지 태그 고정 정책** — **T7 블로커**: `latest` 금지. T7 시작 전 FalkorDB Docker Hub에서 최신 안정 태그 확인 후 `TAG` 상수에 고정. 미고정 시 PR 머지 차단.
6. **인증 모델** — Redis ACL `username/password`. 기본은 빈 문자열 → 비인증. spring-boot의 `username` 빈 문자열 검사 로직은 Memgraph와 동일.

---

## 8. 검증 명령

```bash
# 모듈 빌드
./gradlew :graph-falkordb:build

# 모듈 테스트
./gradlew :graph-falkordb:test

# starter 테스트 (settings.gradle.kts includeModules("spring-boot3", false, false) 기준 실제 project명)
./gradlew :graph-spring-boot3-starter:test --tests "*FalkorDB*"
./gradlew :graph-spring-boot4-starter:test --tests "*FalkorDB*"

# examples 테스트 (includeModules("examples", false, false) 기준 실제 project명)
./gradlew :code-graph-examples:test --tests "*FalkorDB*"
./gradlew :linkedin-graph-examples:test --tests "*FalkorDB*"

# 전체
./gradlew clean build
```
