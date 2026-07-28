# 이슈 10 Domain examples 설계

## 맥락

Issue #10은 graph algorithm 사용법을 보여주기 위해 domain example module 세 개를 추가한다.

- `fraud-detection-examples`
- `recommendation-examples`
- `knowledge-graph-examples`

기존 example은 이미 구현 형태를 확립하고 있다.

- `examples/code-graph-examples`
- `examples/linkedin-graph-examples`

새 module은 새 example framework를 도입하지 않고 동일한 module layout, backend matrix, service/test split, Testcontainers singleton pattern을 재사용해야 한다.

## 목표

- `examples/` 아래에 example module 세 개를 추가한다.
- 실제 domain service를 통해 algorithm 중심 graph API를 보여준다.
- blocking service variant와 coroutine service variant를 모두 cover한다.
- Neo4j, Memgraph, Apache AGE, FalkorDB, TinkerGraph backend smoke test를 cover한다.
- 기존 `examples/` convention을 따라 example module을 Maven Central publishing에서 제외한다.
- 새 module을 full Nightly example verification에 추가한다.

## 비목표

- 새로운 graph-core API는 추가하지 않는다.
- 새로운 graph backend behavior는 추가하지 않는다.
- example module 밖에는 새로운 production dependency를 추가하지 않는다.
- 모든 pull request에 대해 broad CI를 확장하지 않는다. container-heavy example test는 full Nightly에 유지한다.

## 기존 패턴 증거

### 모듈 포함

`settings.gradle.kts`는 다음 방식으로 모든 `examples/*/build.gradle.kts` directory를 include한다.

```kotlin
includeModules("examples", false, false)
```

따라서 각 새 example은 자체 `build.gradle.kts`만 있으면 된다.

### 의존성

`code-graph-examples`와 `linkedin-graph-examples`는 다음에 의존한다.

- `:graph-core`
- 다섯 backend module 전체
- `bluetape4k.coroutines`
- `kotlinx-coroutines-core`
- test-only Testcontainers, Neo4j driver, PostgreSQL driver, HikariCP, coroutine test support
- FalkorDB server support를 위한 `:graph-falkordb` test fixture

새 module 세 개는 이 dependency shape를 복사해야 한다.

각 새 module은 다음 dependency block 형태를 사용해야 한다.

```kotlin
dependencies {
    implementation(project(":graph-core"))
    implementation(project(":graph-age"))
    implementation(project(":graph-neo4j"))
    implementation(project(":graph-memgraph"))
    implementation(project(":graph-tinkerpop"))
    implementation(project(":graph-falkordb"))

    implementation(libs.bluetape4k.coroutines)
    implementation(libs.kotlinx.coroutines.core.lib)

    testImplementation(libs.bluetape4k.junit5)
    testImplementation(libs.bluetape4k.testcontainers)
    testImplementation(libs.testcontainers.core)
    testImplementation(libs.testcontainers.neo4j)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.neo4j.java.driver)
    testRuntimeOnly(libs.postgresql.driver)
    testImplementation(libs.hikaricp)
    testImplementation(libs.kotlinx.coroutines.test.lib)
    testImplementation(project(":graph-falkordb"))
    testImplementation(testFixtures(project(":graph-falkordb")))
}
```

root `build.gradle.kts`는 이미 모든 `test` task를 `testMutex`로 serialize하므로, 새 module test도 기존 example과 동일한 Testcontainers conflict protection을 상속한다.

### Schema DSL

각 module은 `CodeGraphSchema.kt`와 `LinkedInSchema.kt`를 따라 `src/main/kotlin/.../schema` 아래에 schema label을 정의해야 한다.

예시 형태:

```kotlin
object AccountLabel : VertexLabel("Account") {
    val accountNumber = string("accountNumber")
}

object TransferredToLabel : EdgeLabel("TRANSFERRED_TO", AccountLabel, AccountLabel) {
    val amount = long("amount")
}
```

### Backend test

기존 example은 다음을 사용한다:

- shared scenario test를 담은 abstract blocking test class
- 동일 scenario coverage를 가진 abstract suspend test class
- `ops`만 제공하는 backend-specific concrete class
- AGE setup with `PostgreSQLAgeServer.Launcher.postgresqlAge`, HikariCP, and Exposed `Database.connect`
- FalkorDB setup with random graph names and cleanup in `@AfterAll`
- `Neo4jServer.Launcher` / `MemgraphServer.Launcher`를 통한 Neo4j/Memgraph setup
- in-memory operation 기반 TinkerGraph setup

## 제안 모듈 설계

### fraud-detection-examples

Package root:

```text
io.bluetape4k.graph.examples.fraud
```

Public service class:

- `FraudDetectionService`
- `FraudDetectionSuspendService`

Schema label:

- vertex: `Account`, `Transaction`
- edge: `TRANSFERRED_TO`, `OWNS`

주요 scenario:

- Circular transfer detection: account-to-account transfer cycle을 찾는다.
- Suspicious cluster detection: 최소 size를 넘는 weakly connected account component를 찾는다.
- High-risk account lookup: transfer edge의 PageRank로 account 순위를 매긴다.

Blocking service API:

```kotlin
class FraudDetectionService(
    private val ops: GraphOperations,
    private val graphName: String = "fraud_detection",
) {
    fun initialize()
    fun addAccount(accountNumber: String, ownerName: String = "", riskLevel: String = "normal"): GraphVertex
    fun addTransfer(fromAccountId: GraphElementId, toAccountId: GraphElementId, amount: Long, traceId: String = "")
    fun detectCircularTransfers(maxDepth: Int = 5): List<GraphCycle>
    fun findSuspiciousClusters(minSize: Int = 2): List<GraphComponent>
    fun rankHighRiskAccounts(limit: Int = 10): List<PageRankScore>
}
```

Suspend service API:

```kotlin
class FraudDetectionSuspendService(
    private val ops: GraphSuspendOperations,
    private val graphName: String = "fraud_detection",
) {
    suspend fun initialize()
    suspend fun addAccount(accountNumber: String, ownerName: String = "", riskLevel: String = "normal"): GraphVertex
    suspend fun addTransfer(fromAccountId: GraphElementId, toAccountId: GraphElementId, amount: Long, traceId: String = "")
    fun detectCircularTransfers(maxDepth: Int = 5): Flow<GraphCycle>
    fun findSuspiciousClusters(minSize: Int = 2): Flow<GraphComponent>
    fun rankHighRiskAccounts(limit: Int = 10): Flow<PageRankScore>
}
```

### recommendation-examples

Package root:

```text
io.bluetape4k.graph.examples.recommendation
```

Public service class:

- `RecommendationService`
- `RecommendationSuspendService`

Schema label:

- vertex: `User`, `Product`, `Category`
- edge: `PURCHASED`, `VIEWED`, `FOLLOWS`, `IN_CATEGORY`

주요 scenario:

- user-product relation의 neighbor traversal로 product를 추천한다.
- 2-hop `FOLLOWS` traversal로 social follow를 추천한다.
- interaction edge의 PageRank로 popular product 순위를 매긴다.

Blocking service API:

```kotlin
class RecommendationService(
    private val ops: GraphOperations,
    private val graphName: String = "recommendation",
) {
    fun initialize()
    fun addUser(userId: String, name: String = ""): GraphVertex
    fun addProduct(sku: String, name: String, category: String = ""): GraphVertex
    fun purchase(userId: GraphElementId, productId: GraphElementId, quantity: Int = 1)
    fun follow(followerId: GraphElementId, targetId: GraphElementId)
    fun recommendProducts(userId: GraphElementId, maxDepth: Int = 2): List<GraphVertex>
    fun recommendFollows(userId: GraphElementId): List<GraphVertex>
    fun rankPopularProducts(limit: Int = 10): List<PageRankScore>
}
```

구현 제약:

- `rankPopularProducts` must use `PageRankOptions(vertexLabel = "Product", edgeLabel = "PURCHASED", topK = limit)`.
- `recommendProducts`는 stable two-step traversal을 사용해야 한다: purchased products -> 해당 product를 구매한 users -> 그 users의 purchased products. 이후 source user가 이미 구매한 product를 제거한다.
- `recommendFollows`는 `FOLLOWS` two-hop traversal을 사용하고 direct follow와 source user를 제거해야 한다.

Suspend service API:

```kotlin
class RecommendationSuspendService(
    private val ops: GraphSuspendOperations,
    private val graphName: String = "recommendation",
) {
    suspend fun initialize()
    suspend fun addUser(userId: String, name: String = ""): GraphVertex
    suspend fun addProduct(sku: String, name: String, category: String = ""): GraphVertex
    suspend fun purchase(userId: GraphElementId, productId: GraphElementId, quantity: Int = 1)
    suspend fun follow(followerId: GraphElementId, targetId: GraphElementId)
    fun recommendProducts(userId: GraphElementId, maxDepth: Int = 2): Flow<GraphVertex>
    fun recommendFollows(userId: GraphElementId): Flow<GraphVertex>
    fun rankPopularProducts(limit: Int = 10): Flow<PageRankScore>
}
```

### knowledge-graph-examples

Package root:

```text
io.bluetape4k.graph.examples.knowledge
```

Public service class:

- `KnowledgeGraphService`
- `KnowledgeGraphSuspendService`

Schema label:

- vertex: `Entity`, `Concept`, `Document`
- edge: `MENTIONS`, `RELATED_TO`, `IS_A`, `DERIVED_FROM`

주요 scenario:

- Entity linking from documents to entities.
- Concept hierarchy traversal.
- `allPaths`를 통한 relationship path 추론.

Blocking service API:

```kotlin
class KnowledgeGraphService(
    private val ops: GraphOperations,
    private val graphName: String = "knowledge_graph",
) {
    fun initialize()
    fun addEntity(name: String, entityType: String = "generic"): GraphVertex
    fun addConcept(name: String, domain: String = ""): GraphVertex
    fun addDocument(documentId: String, title: String): GraphVertex
    fun mention(documentId: GraphElementId, entityId: GraphElementId)
    fun relate(fromId: GraphElementId, toId: GraphElementId, relation: String = "RELATED_TO")
    fun findRelatedEntities(entityId: GraphElementId, maxDepth: Int = 2): List<GraphVertex>
    fun inferRelationshipPaths(fromId: GraphElementId, toId: GraphElementId, maxDepth: Int = 4, maxPaths: Int = 50): List<GraphPath>
}
```

Suspend service API:

```kotlin
class KnowledgeGraphSuspendService(
    private val ops: GraphSuspendOperations,
    private val graphName: String = "knowledge_graph",
) {
    suspend fun initialize()
    suspend fun addEntity(name: String, entityType: String = "generic"): GraphVertex
    suspend fun addConcept(name: String, domain: String = ""): GraphVertex
    suspend fun addDocument(documentId: String, title: String): GraphVertex
    suspend fun mention(documentId: GraphElementId, entityId: GraphElementId)
    suspend fun relate(fromId: GraphElementId, toId: GraphElementId, relation: String = "RELATED_TO")
    fun findRelatedEntities(entityId: GraphElementId, maxDepth: Int = 2): Flow<GraphVertex>
    fun inferRelationshipPaths(fromId: GraphElementId, toId: GraphElementId, maxDepth: Int = 4, maxPaths: Int = 50): Flow<GraphPath>
}
```

## 아키텍처 결정

### domain마다 module 하나

Each domain gets a separate Gradle module, matching issue #10 and making examples easy to run independently.

Rejected alternative: one `domain-graph-examples` module with three packages.

Reason: one module would reduce Gradle files but weaken discoverability and make Nightly failures less attributable.

### module별 small backend fixture 중복

Backend concrete test classes will mirror existing examples instead of introducing shared test-fixture modules.

Rejected alternative: extract a shared `examples-test-support` module.

Reason: the existing examples intentionally keep test setup local. A shared fixture would be a larger architecture change
than issue #10 requires and could create cross-example coupling.

### Algorithm API는 service-facing으로 유지

Service는 raw `GraphOperations` wrapper가 아니라 domain method를 노출한다. Test는 domain term으로 outcome을 검증한다.

이유: example은 business scenario를 graph operation으로 번역하는 방법을 사용자에게 가르쳐야 한다.

### Full Nightly가 backend matrix 검증을 담당

PR build는 `./gradlew build -x test --parallel`로 모든 module을 compile하지만, container-heavy example test는
기존 `test-examples` job이 아니라 새 full Nightly domain example job에 추가한다.

이유: 기존 example backend matrix도 이미 full Nightly에서만 실행되며, five-backend example suite를 PR에 추가하면
pull request 비용이 크게 증가한다. 별도 Nightly job은 failure attribution을 개선하고 기존 `test-examples` job이
30분 timeout을 넘는 상황을 피한다.

## Backend capability matrix

| Scenario | TinkerGraph | Neo4j | Memgraph | Apache AGE | FalkorDB |
|---|---|---|---|---|---|
| Fraud circular transfer detection | run, assert non-empty cycle | run, assert non-empty cycle | run, assert non-empty cycle | run, assert non-empty cycle | run, assert non-empty cycle |
| Fraud suspicious clusters | run, assert min component size | run, assert min component size | run, assert min component size | run, assert min component size | run, assert min component size |
| Fraud PageRank | run, assert expected account in topK | run, assert expected account in topK | run, assert expected account in topK | run, assert expected account in topK | run via JVM fallback, assert expected account in topK |
| Recommendation product traversal | run, assert recommended product label | run, assert recommended product label | run, assert recommended product label | run, assert recommended product label | run, assert recommended product label |
| Recommendation PageRank | run, `PURCHASED` edge filter | run, `PURCHASED` edge filter | run, `PURCHASED` edge filter | run, `PURCHASED` edge filter | run via JVM fallback, `PURCHASED` edge filter |
| Knowledge related entities | run, assert related entity label | run, assert related entity label | run, assert related entity label | run, assert related entity label | run, assert related entity label |
| Knowledge relationship paths | run, assert bounded paths | run, assert bounded paths | run, assert bounded paths | run, assert bounded paths | run, assert bounded paths |

## 검증 전략

Local verification:

- `./gradlew projects`
- `./gradlew :fraud-detection-examples:test --no-daemon`
- `./gradlew :recommendation-examples:test --no-daemon`
- `./gradlew :knowledge-graph-examples:test --no-daemon`
- `./gradlew build -x test --parallel`
- `actionlint .github/workflows/nightly.yml` if Nightly workflow is edited

Remote verification:

- PR CI compile-only build는 `settings.gradle.kts`를 통해 새 module을 포함해야 한다.
- Full Nightly는 새 `test-domain-examples` job에서 모든 새 example test를 실행해야 한다.
- Nightly status aggregation은 `test-domain-examples`를 포함해야 한다.
- If practical after merge, run `workflow_dispatch` with `scope=full` and record the run URL.

## 리스크

- Backend algorithm parity는 다를 수 있다. Test는 non-empty result 같은 stable cross-backend property를 assert해야 한다.
  expected labels, and path/component existence, not backend-specific ordering.
- AGE uses a global Exposed `Database.connect` side effect. Keep AGE tests consistent with existing examples.
- FalkorDB graph cleanup must use unique graph names and best-effort drop in `@AfterAll`.
- PageRank ranking은 score tie 때문에 달라질 수 있다. Test는 exact order보다 expected vertex가 top result set에 나타나는지 확인해야 한다.
  than asserting exact score values.
- dense knowledge graph에서 `allPaths`는 빠르게 커질 수 있다. `inferRelationshipPaths`는 `maxPaths`를 노출해야 하고 test는
  use small fixtures.

## 인수 기준

- Three new example modules exist and are auto-included by Gradle.
- Each module has blocking and suspend service classes.
- Each module has abstract blocking/suspend tests plus five backend concrete test classes for each mode.
- README.md and README.ko.md exist for every new module.
- New public KDoc is English.
- README files include the language switch, architecture/features/usage/config/dependencies sections, and a Mermaid
  architecture diagram.
- Nightly full workflow includes a separate `test-domain-examples` job for the three new modules.
- New module test tasks inherit the root `testMutex` serialization.
- Local targeted tests pass for all three modules.
- `./gradlew build -x test --parallel` passes.

## Step 2-R 리뷰 메모

### Claude Code Opus advisor

Artifact: `.omx/artifacts/claude-issue-10-spec-review-20260513081123.md`
Model: `${CLAUDE_ADVISOR_MODEL:-claude-opus-4-7}`

| 우선순위 | 발견 사항 | 결정 | Follow-up |
|---|---|---|---|
| P0 | Service APIs lacked `graphName`. | Accepted | Added `graphName` to all service API sketches. |
| P0 | Suspend service API was not specified. | Accepted | Added Flow-based suspend API sketches. |
| P0 | Backend capability/skip policy was missing. | Accepted | Added backend capability matrix. |
| P0 | New public KDoc language was not specified. | Accepted | Added English KDoc acceptance criterion. |
| P0 | Recommendation PageRank edge scope was ambiguous. | Accepted | Fixed to `PageRankOptions(edgeLabel = "PURCHASED")`. |
| P1 | Schema DSL, dependency block, testMutex, Nightly edit location이 덜 구체화되어 있었다. | 수용 | 구체 section과 Nightly job 결정을 추가했다. |
| P2 | Knowledge `allPaths` can explode. | Accepted | Added `maxPaths` to service API. |

Latest integrated finding status: P0 = 0, P1 = 0.
