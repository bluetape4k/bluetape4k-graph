# Issue 10 Domain Examples Design

## Context

Issue #10 adds three domain example modules to demonstrate graph algorithm usage:

- `fraud-detection-examples`
- `recommendation-examples`
- `knowledge-graph-examples`

Existing examples already establish the implementation shape:

- `examples/code-graph-examples`
- `examples/linkedin-graph-examples`

The new modules should reuse the same module layout, backend matrix, service/test split, and Testcontainers singleton
patterns rather than introducing a new example framework.

## Goals

- Add three example modules under `examples/`.
- Demonstrate algorithm-oriented graph APIs through practical domain services.
- Cover blocking and coroutine service variants.
- Cover backend smoke tests for Neo4j, Memgraph, Apache AGE, FalkorDB, and TinkerGraph.
- Keep examples excluded from Maven Central publishing by following existing `examples/` conventions.
- Add the new modules to full Nightly example verification.

## Non-Goals

- No new graph-core API.
- No new graph backend behavior.
- No new production dependencies outside example modules.
- No broad CI expansion for every pull request; container-heavy example tests stay in full Nightly.

## Existing Pattern Evidence

### Module Inclusion

`settings.gradle.kts` includes every `examples/*/build.gradle.kts` directory through:

```kotlin
includeModules("examples", false, false)
```

Therefore each new example needs only its own `build.gradle.kts`.

### Dependencies

`code-graph-examples` and `linkedin-graph-examples` depend on:

- `:graph-core`
- all five backend modules
- `bluetape4k.coroutines`
- `kotlinx-coroutines-core`
- test-only Testcontainers, Neo4j driver, PostgreSQL driver, HikariCP, and coroutine test support
- `:graph-falkordb` test fixtures for FalkorDB server support

The three new modules should copy this dependency shape.

Each new module should use this dependency block shape:

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

Root `build.gradle.kts` already serializes all `test` tasks through `testMutex`, so the new module tests inherit the
same Testcontainers conflict protection as existing examples.

### Schema DSL

Each module should define schema labels under `src/main/kotlin/.../schema`, following `CodeGraphSchema.kt` and
`LinkedInSchema.kt`.

Example shape:

```kotlin
object AccountLabel : VertexLabel("Account") {
    val accountNumber = string("accountNumber")
}

object TransferredToLabel : EdgeLabel("TRANSFERRED_TO", AccountLabel, AccountLabel) {
    val amount = long("amount")
}
```

### Backend Tests

Existing examples use:

- abstract blocking test class with shared scenario tests
- abstract suspend test class with the same scenario coverage
- backend-specific concrete classes that only provide `ops`
- AGE setup with `PostgreSQLAgeServer.Launcher.postgresqlAge`, HikariCP, and Exposed `Database.connect`
- FalkorDB setup with random graph names and cleanup in `@AfterAll`
- Neo4j/Memgraph setup through `Neo4jServer.Launcher` / `MemgraphServer.Launcher`
- TinkerGraph setup with in-memory operations

## Proposed Module Designs

### fraud-detection-examples

Package root:

```text
io.bluetape4k.graph.examples.fraud
```

Public service classes:

- `FraudDetectionService`
- `FraudDetectionSuspendService`

Schema labels:

- vertices: `Account`, `Transaction`
- edges: `TRANSFERRED_TO`, `OWNS`

Main scenarios:

- Circular transfer detection: find account-to-account transfer cycles.
- Suspicious cluster detection: find weakly connected account components above a minimum size.
- High-risk account lookup: rank accounts by PageRank over transfer edges.

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

Public service classes:

- `RecommendationService`
- `RecommendationSuspendService`

Schema labels:

- vertices: `User`, `Product`, `Category`
- edges: `PURCHASED`, `VIEWED`, `FOLLOWS`, `IN_CATEGORY`

Main scenarios:

- Product recommendation from neighbor traversal over user-product relations.
- Social follow recommendation from 2-hop `FOLLOWS` traversal.
- Popular product ranking through PageRank over interaction edges.

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

Implementation constraints:

- `rankPopularProducts` must use `PageRankOptions(vertexLabel = "Product", edgeLabel = "PURCHASED", topK = limit)`.
- `recommendProducts` should use a stable two-step traversal: purchased products -> users who purchased them -> their
  purchased products, then remove products already purchased by the source user.
- `recommendFollows` should use `FOLLOWS` two-hop traversal and remove direct follows plus the source user.

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

Public service classes:

- `KnowledgeGraphService`
- `KnowledgeGraphSuspendService`

Schema labels:

- vertices: `Entity`, `Concept`, `Document`
- edges: `MENTIONS`, `RELATED_TO`, `IS_A`, `DERIVED_FROM`

Main scenarios:

- Entity linking from documents to entities.
- Concept hierarchy traversal.
- Relationship path inference through `allPaths`.

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

## Architecture Decisions

### One module per domain

Each domain gets a separate Gradle module, matching issue #10 and making examples easy to run independently.

Rejected alternative: one `domain-graph-examples` module with three packages.

Reason: one module would reduce Gradle files but weaken discoverability and make Nightly failures less attributable.

### Duplicate small backend fixtures per module

Backend concrete test classes will mirror existing examples instead of introducing shared test-fixture modules.

Rejected alternative: extract a shared `examples-test-support` module.

Reason: the existing examples intentionally keep test setup local. A shared fixture would be a larger architecture change
than issue #10 requires and could create cross-example coupling.

### Algorithm APIs stay service-facing

Services expose domain methods, not raw `GraphOperations` wrappers. Tests verify outcomes through domain terms.

Reason: the examples should teach users how to translate a business scenario into graph operations.

### Full Nightly owns backend matrix verification

The PR build will compile all modules through `./gradlew build -x test --parallel`, but container-heavy example tests
will be added to a new full Nightly domain example job instead of the existing `test-examples` job.

Reason: existing example backend matrices already run in full Nightly only, and adding five-backend example suites to PR
CI would increase pull request cost substantially. A separate Nightly job improves failure attribution and avoids pushing
the existing `test-examples` job past its 30-minute timeout.

## Backend Capability Matrix

| Scenario | TinkerGraph | Neo4j | Memgraph | Apache AGE | FalkorDB |
|---|---|---|---|---|---|
| Fraud circular transfer detection | run, assert non-empty cycle | run, assert non-empty cycle | run, assert non-empty cycle | run, assert non-empty cycle | run, assert non-empty cycle |
| Fraud suspicious clusters | run, assert min component size | run, assert min component size | run, assert min component size | run, assert min component size | run, assert min component size |
| Fraud PageRank | run, assert expected account in topK | run, assert expected account in topK | run, assert expected account in topK | run, assert expected account in topK | run via JVM fallback, assert expected account in topK |
| Recommendation product traversal | run, assert recommended product label | run, assert recommended product label | run, assert recommended product label | run, assert recommended product label | run, assert recommended product label |
| Recommendation PageRank | run, `PURCHASED` edge filter | run, `PURCHASED` edge filter | run, `PURCHASED` edge filter | run, `PURCHASED` edge filter | run via JVM fallback, `PURCHASED` edge filter |
| Knowledge related entities | run, assert related entity label | run, assert related entity label | run, assert related entity label | run, assert related entity label | run, assert related entity label |
| Knowledge relationship paths | run, assert bounded paths | run, assert bounded paths | run, assert bounded paths | run, assert bounded paths | run, assert bounded paths |

## Verification Strategy

Local verification:

- `./gradlew projects`
- `./gradlew :fraud-detection-examples:test --no-daemon`
- `./gradlew :recommendation-examples:test --no-daemon`
- `./gradlew :knowledge-graph-examples:test --no-daemon`
- `./gradlew build -x test --parallel`
- `actionlint .github/workflows/nightly.yml` if Nightly workflow is edited

Remote verification:

- PR CI compile-only build should include the new modules through `settings.gradle.kts`.
- Full Nightly should run all new example tests in a new `test-domain-examples` job.
- The Nightly status aggregation should include `test-domain-examples`.
- If practical after merge, run `workflow_dispatch` with `scope=full` and record the run URL.

## Risks

- Backend algorithm parity may differ. Tests should assert stable, cross-backend properties such as non-empty results,
  expected labels, and path/component existence, not backend-specific ordering.
- AGE uses a global Exposed `Database.connect` side effect. Keep AGE tests consistent with existing examples.
- FalkorDB graph cleanup must use unique graph names and best-effort drop in `@AfterAll`.
- PageRank ranking can vary by score tie. Tests should check that expected vertices appear in the top result set rather
  than asserting exact score values.
- `allPaths` can grow quickly on dense knowledge graphs. `inferRelationshipPaths` must expose `maxPaths` and tests should
  use small fixtures.

## Acceptance Criteria

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

## Step 2-R Review Notes

### Claude Code Opus Advisor

Artifact: `.omx/artifacts/claude-issue-10-spec-review-20260513081123.md`
Model: `${CLAUDE_ADVISOR_MODEL:-claude-opus-4-7}`

| Priority | Finding | Decision | Follow-up |
|---|---|---|---|
| P0 | Service APIs lacked `graphName`. | Accepted | Added `graphName` to all service API sketches. |
| P0 | Suspend service API was not specified. | Accepted | Added Flow-based suspend API sketches. |
| P0 | Backend capability/skip policy was missing. | Accepted | Added backend capability matrix. |
| P0 | New public KDoc language was not specified. | Accepted | Added English KDoc acceptance criterion. |
| P0 | Recommendation PageRank edge scope was ambiguous. | Accepted | Fixed to `PageRankOptions(edgeLabel = "PURCHASED")`. |
| P1 | Schema DSL, dependency block, testMutex, and Nightly edit location were under-specified. | Accepted | Added concrete sections and Nightly job decision. |
| P2 | Knowledge `allPaths` can explode. | Accepted | Added `maxPaths` to service API. |

Latest integrated finding status: P0 = 0, P1 = 0.
