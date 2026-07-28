# 이슈 10 Domain examples 구현 계획

## Inputs

- Spec: `docs/superpowers/specs/2026-05-13-issue-10-domain-examples-design.md`
- 이슈: #10 `[Epic] 추가 예시 모듈 — fraud-detection / recommendation / knowledge-graph`
- Worktree: `.worktrees/feat-issue-10-domain-examples`
- Branch: `feat/issue-10-domain-examples`

## 범위

Implement three new example modules:

- `examples/fraud-detection-examples`
- `examples/recommendation-examples`
- `examples/knowledge-graph-examples`

Each module includes:

- `build.gradle.kts`
- `README.md`
- `README.ko.md`
- schema DSL file
- blocking service
- suspend service
- abstract blocking test
- abstract suspend test
- five blocking backend concrete tests
- five suspend backend concrete tests

## Implementation Tasks

### 1. Scaffold modules (S)

Create:

```text
examples/fraud-detection-examples/
examples/recommendation-examples/
examples/knowledge-graph-examples/
```

For each module:

- Copy the dependency shape from `examples/code-graph-examples/build.gradle.kts`.
- Add `src/main/kotlin/io/bluetape4k/graph/examples/<domain>/schema`.
- Add `src/main/kotlin/io/bluetape4k/graph/examples/<domain>/service`.
- Add `src/test/kotlin/io/bluetape4k/graph/examples/<domain>`.

Expected Gradle project names through existing `settings.gradle.kts`:

- `:fraud-detection-examples`
- `:recommendation-examples`
- `:knowledge-graph-examples`

Rollback checkpoint:

- Run `./gradlew projects` and verify all three module names are present.

### 2. Implement fraud detection example (M)

Files:

- `FraudDetectionSchema.kt`
- `FraudDetectionService.kt`
- `FraudDetectionSuspendService.kt`

Domain operations:

- create accounts
- create transfer edges
- detect circular transfers with `CycleOptions(edgeLabel = "TRANSFERRED_TO")`
- detect suspicious clusters with `ComponentOptions(vertexLabel = "Account", edgeLabel = "TRANSFERRED_TO", minSize = minSize)`
- rank high-risk accounts with `PageRankOptions(vertexLabel = "Account", edgeLabel = "TRANSFERRED_TO", topK = limit)`

Tests:

- account and transfer creation
- circular transfer detection returns at least one cycle
- suspicious cluster detection returns a component with expected size
- PageRank topK membership includes the expected sink account by stable vertex property/id; do not assert exact rank
  position or score value.
- suspend tests mirror blocking tests with `Flow.toList()`

Diagnostics and compile gate:

- Run IDE diagnostics for touched Kotlin files when available.
- If IDE diagnostics are unavailable in the active Codex environment, record the gap and use Gradle compile/test as
  fallback evidence.
- Fix import errors and unresolved `@Deprecated` warnings before compile/test.
- Run `./gradlew :fraud-detection-examples:compileKotlin :fraud-detection-examples:compileTestKotlin --no-daemon`.
- Run TinkerGraph fraud tests before adding container backend fanout.

### 3. Implement recommendation example (M)

Files:

- `RecommendationSchema.kt`
- `RecommendationService.kt`
- `RecommendationSuspendService.kt`

Domain operations:

- create users and products
- record `PURCHASED` edges from users to products
- create social `FOLLOWS` edges between users
- recommend products with paired neighbor calls, not a mixed `maxDepth = 2` result:
  - source purchased products: `user --PURCHASED--> product`
  - similar users: `product <--PURCHASED-- user`
  - candidate products: `similar user --PURCHASED--> product`
  - exclude products already purchased by the source user and distinct by product id
- recommend follows with paired neighbor calls over `FOLLOWS`:
  - direct follows: `source --FOLLOWS--> user`
  - second-hop follows: `direct --FOLLOWS--> user`
  - exclude the source user and all direct follows, then distinct by user id
- rank popular products with `PageRankOptions(vertexLabel = "Product", edgeLabel = "PURCHASED", topK = limit)`

Tests:

- product recommendation suggests a product bought by a similar user
- follow recommendation suggests a two-hop follow target
- PageRank topK membership includes the expected product by stable vertex property/id; do not assert exact rank position
  or score value.
- suspend tests mirror blocking tests with `Flow.toList()`

Suspend Flow semantics:

- For repository APIs that already return `Flow<T>`, return the mapped/filtered Flow directly.
- For multi-step recommendations that need intermediate collection, use `flow { ... emit(...) }` or collected lists followed
  by `.asFlow()`; do not catch broad exceptions. If catch blocks become necessary, rethrow `CancellationException`.

Diagnostics and compile gate:

- Run IDE diagnostics for touched Kotlin files when available; otherwise record the gap and rely on Gradle compile/test.
- Fix import errors and unresolved `@Deprecated` warnings before compile/test.
- Run `./gradlew :recommendation-examples:compileKotlin :recommendation-examples:compileTestKotlin --no-daemon`.
- Run TinkerGraph recommendation tests before adding container backend fanout.

### 4. Implement knowledge graph example (M)

Files:

- `KnowledgeGraphSchema.kt`
- `KnowledgeGraphService.kt`
- `KnowledgeGraphSuspendService.kt`

Domain operations:

- create entities, concepts, and documents
- link documents to entities with `MENTIONS`
- relate entities and concepts with typed `RELATED_TO` or `IS_A` edges
- find related entities through `neighbors`
- infer relationship paths through `allPaths`, bounded by `maxDepth` and a service-side `maxPaths` limit so all backends
  expose the same maximum result count

Tests:

- entity/document linking returns mentioned entities
- related entity traversal returns expected entity
- relationship path inference returns a bounded non-empty path set containing the expected labels/ids; do not assert exact
  traversal order beyond source/target and maximum count.
- suspend tests mirror blocking tests with `Flow.toList()`

Diagnostics and compile gate:

- Run IDE diagnostics for touched Kotlin files when available; otherwise record the gap and rely on Gradle compile/test.
- Fix import errors and unresolved `@Deprecated` warnings before compile/test.
- Run `./gradlew :knowledge-graph-examples:compileKotlin :knowledge-graph-examples:compileTestKotlin --no-daemon`.
- Run TinkerGraph knowledge graph tests before adding container backend fanout.

### 5. Add backend concrete tests for each module (L)

For each module and service mode, add:

- `TinkerGraph<Domain>Test`
- `Neo4j<Domain>Test`
- `Memgraph<Domain>Test`
- `Age<Domain>Test`
- `FalkorDB<Domain>Test`

Reuse setup from existing examples:

- TinkerGraph: in-memory operations
- Neo4j: `Neo4jServer.Launcher.neo4j`
- Memgraph: `MemgraphServer.Launcher.memgraph`
- AGE: `PostgreSQLAgeServer.Launcher.postgresqlAge`, HikariCP, Exposed `Database.connect`
- FalkorDB: `FalkorDBServer.Launcher.falkordb`, UUID-suffixed graph name per concrete class, best-effort drop in
  `@AfterAll` with logging

Isolation rules:

- Keep each concrete class aligned with existing example patterns and inherit `Abstract*Test` / `Abstract*SuspendTest`
  behavior.
- Verify AGE tests do not depend on leaked Exposed default database state; copy the existing AGE fixture pattern
  verbatim and keep graph names/domain data isolated.
- Preserve `testMutex`-compatible execution assumptions already used by container-backed tests.

Per-module backend rollout:

- First add TinkerGraph blocking and suspend tests.
- Then add Neo4j and Memgraph.
- Then add AGE.
- Then add FalkorDB with UUID graph names and cleanup.

### 6. Add README locale sets (M)

For each module:

- `README.md` in English
- `README.ko.md` in Korean

Each README includes:

- language switch below title
- architecture section with Mermaid diagram
- core features
- usage examples
- configuration/testing notes
- dependency snippet

Rollback checkpoint:

- Verify every module has both `README.md` and `README.ko.md`.
- Verify the language switch appears directly below each title.

### 7. Update workflows (M)

7a. Edit `.github/workflows/nightly.yml`:

- Add a new job `test-domain-examples`.
- Run:
  - `:fraud-detection-examples:test`
  - `:recommendation-examples:test`
  - `:knowledge-graph-examples:test`
- Use Docker/Testcontainers env matching the existing `test-examples` job, including inherited `JAVA_VERSION: ${{ env.JAVA_VERSION }}`.
- Add `test-domain-examples` to `nightly-status.needs`.

7b. Audit `.github/workflows/ci.yml`:

- If CI enumerates example modules explicitly, update it for the new modules.
- If CI only runs repository-wide compile/build tasks that auto-include modules through `settings.gradle.kts`, document that
  no `ci.yml` edit is needed.
- Do not add these container-heavy module tests to PR CI test jobs unless existing CI policy requires it.

7c. Validate workflow syntax:

- Run `actionlint .github/workflows/nightly.yml`.
- Run `actionlint .github/workflows/ci.yml` if `ci.yml` is edited; otherwise record the no-edit audit result.

7d. Remote Nightly verification:

- After pushing the branch or opening the PR, run `gh workflow run nightly.yml --ref <branch> -f scope=full`.
- Record the Nightly run URL and result before declaring DoD.

### 8. Verification

Run in order:

1. `git status --porcelain`
2. `./gradlew projects`
3. IDE diagnostics for touched Kotlin files when available; otherwise record the fallback.
4. `actionlint .github/workflows/nightly.yml`
5. `actionlint .github/workflows/ci.yml` if edited.
6. `./gradlew :fraud-detection-examples:test --no-daemon`
7. `./gradlew :recommendation-examples:test --no-daemon`
8. `./gradlew :knowledge-graph-examples:test --no-daemon`
9. `./gradlew build -x test --parallel`
10. `rg -P "[가-힣]" examples/fraud-detection-examples/src/main examples/recommendation-examples/src/main examples/knowledge-graph-examples/src/main`
    should return no public source KDoc/text matches.
11. `git diff --check`
12. `gh workflow run nightly.yml --ref feat/issue-10-domain-examples -f scope=full`, then capture the run URL and result.

If container-backed local tests fail due local Docker availability, rerun the TinkerGraph subset if possible and record
the Docker gap. Do not claim full completion without either local full tests or CI/Nightly evidence.

### 9. Review and cleanup

- Verify public KDoc is English in new public classes.
- Verify README locale pairs are both present.
- Check module names through `./gradlew projects`.
- Ensure no generated build or docs output is staged.
- Add a concise lesson under `docs/lessons/`.

Lesson template:

```markdown
# Issue 10 Domain Examples

## 맥락

## 결정

## 결과

## Verification

## Future Guidance
```

## Commit Protocol

- Commit spec and plan before implementation.
- Use English Conventional Commit subjects with Lore trailers.
- Prefer one commit for design artifacts, one or more commits for implementation, and one commit for workflow/docs cleanup
  only if that split improves reviewability.
- Do not use `--no-verify`.
- Do not force-push unless explicitly instructed.

## Rollback Points

- After module scaffolding: `./gradlew projects`
- After each module blocking service: TinkerGraph blocking test
- After each module suspend service: TinkerGraph suspend test
- After each README locale pair: file presence and language switch check
- After each backend group: targeted backend test class subset
- After Nightly edit: `actionlint`
- Before PR: full targeted module tests, build, KDoc language grep, diff check
- After PR: full Nightly `workflow_dispatch scope=full` run URL/result

## Step 3-R 리뷰 메모

- Review artifact: `.omx/artifacts/claude-issue-10-plan-review-20260513081601.md`
- Blocking findings addressed:
  - `ci.yml` audit/update task added.
  - Full Nightly `workflow_dispatch scope=full` verification added as required DoD evidence.
  - Recommendation traversal algorithm now specifies paired neighbor calls and exclusion sets.
  - Kotlin IDE diagnostics/fallback gates added before compile/test.
  - Cross-backend assertion shapes now avoid exact PageRank scores/order and exact traversal ordering.
- P0/P1 after edits: 0 known.
