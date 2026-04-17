# Implementation Plan — Virtual Threads Full Expansion & graph-benchmark Module

- **Date**: 2026-04-17
- **Spec**: `docs/superpowers/specs/2026-04-17-virtual-threads-expansion-and-benchmark-design.md`
- **Status**: Ready for execution
- **Scope**: `graph/graph-core` (VT 어댑터 확장) + `graph/graph-benchmark` (신규)

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Each task follows TDD: failing test → run → implementation → run → commit.

---

## Complexity Overview

- **Total tasks**: 12
- **High**: 3 (Task 5, Task 10, Task 6 dep-graph check)
- **Medium**: 6 (Task 1, 2, 3, 4, 7, 8, 9)
- **Low**: 3 (Task 11, Task 12, plus Task 6 happy path)

## Task Ordering Rationale

Phase 1 (Task 1–6) delivers VT API surface in `graph-core`. Each VT repository adapter
is built in isolation **first** (Task 1–4) so that the facade in Task 5 can compose
them via Kotlin `by` delegation with every piece already unit-tested. Task 6 verifies
the aggregate test run. Phase 2 (Task 7–11) scaffolds `graph-benchmark` and runs
measurements. Task 12 closes the loop with docs.

---

## Task 1 — `GraphVirtualThreadSession` interface + `VirtualThreadSessionAdapter` + test

**Complexity**: medium

### Why this first
Session 3개 메서드(`createGraph` / `dropGraph` / `graphExists`) 의 VT 변형은 `CompletableFuture<Void>` 반환 계약이 가장 단순해서 패턴을 정립하기 좋음. 이 패턴은 이후 Vertex/Edge/Traversal 에 그대로 반복 적용됨.

### TDD Steps

- [ ] **RED — Write failing test**

   File: `/Users/debop/work/bluetape4k/bluetape4k-graph/graph/graph-core/src/test/kotlin/io/bluetape4k/graph/vt/VirtualThreadSessionAdapterTest.kt`

   ```kotlin
   package io.bluetape4k.graph.vt

   import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
   import io.bluetape4k.junit5.concurrency.StructuredTaskScopeTester
   import io.mockk.spyk
   import io.mockk.verify
   import org.amshove.kluent.shouldBeEqualTo
   import org.amshove.kluent.shouldNotBeNull
   import org.junit.jupiter.api.AfterEach
   import org.junit.jupiter.api.BeforeEach
   import org.junit.jupiter.api.Test

   class VirtualThreadSessionAdapterTest {

       private lateinit var delegate: TinkerGraphOperations
       private lateinit var adapter: VirtualThreadSessionAdapter

       @BeforeEach
       fun setUp() {
           delegate = spyk(TinkerGraphOperations())
           adapter = VirtualThreadSessionAdapter(delegate)
       }

       @AfterEach
       fun tearDown() {
           delegate.close()
       }

       @Test
       fun `createGraphAsync delegates and completes with null`() {
           adapter.createGraphAsync("social").join() shouldBeEqualTo null
           verify(exactly = 1) { delegate.createGraph("social") }
       }

       @Test
       fun `dropGraphAsync delegates and completes with null`() {
           adapter.dropGraphAsync("social").join() shouldBeEqualTo null
           verify(exactly = 1) { delegate.dropGraph("social") }
       }

       @Test
       fun `graphExistsAsync returns delegate result`() {
           val exists = adapter.graphExistsAsync("default").join()
           exists shouldBeEqualTo delegate.graphExists("default")
       }

       @Test
       fun `graphExistsAsync is thread-safe under concurrent load`() {
           StructuredTaskScopeTester()
               .rounds(50)
               .add { adapter.graphExistsAsync("default").join().shouldNotBeNull() }
               .run()
       }
   }
   ```

- [ ] **Verify compile fail**: `./gradlew :graph-core:compileTestKotlin` → `Unresolved reference: VirtualThreadSessionAdapter`.

- [ ] **GREEN — Implement interface**

   File: `/Users/debop/work/bluetape4k/bluetape4k-graph/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphVirtualThreadSession.kt`

   ```kotlin
   package io.bluetape4k.graph.repository

   import java.util.concurrent.CompletableFuture

   /**
    * Virtual Thread 기반 그래프 세션 관리.
    *
    * 모든 메서드는 `CompletableFuture<T>` 를 반환하며,
    * 동기 [GraphSession] 을 Virtual Thread executor 위에서 실행한 결과를 담는다.
    * Java interop 목적이므로 `Unit` 대신 `Void` 를 사용한다.
    */
   interface GraphVirtualThreadSession {
       fun createGraphAsync(name: String): CompletableFuture<Void>
       fun dropGraphAsync(name: String): CompletableFuture<Void>
       fun graphExistsAsync(name: String): CompletableFuture<Boolean>
   }
   ```

- [ ] **GREEN — Implement adapter**

   File: `/Users/debop/work/bluetape4k/bluetape4k-graph/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/vt/VirtualThreadSessionAdapter.kt`

   ```kotlin
   package io.bluetape4k.graph.vt

   import io.bluetape4k.concurrent.virtualthread.StructuredTaskScopes
   import io.bluetape4k.graph.repository.GraphSession
   import io.bluetape4k.graph.repository.GraphVirtualThreadSession
   import io.bluetape4k.logging.KLogging
   import java.util.concurrent.CompletableFuture

   /**
    * [GraphSession] 의 lifecycle 조작을 Virtual Thread 위에서 실행하는 어댑터.
    *
    * `StructuredTaskScopes.withAll` 을 사용하여 각 작업을 Virtual Thread 에서 구조화 실행한다.
    */
   class VirtualThreadSessionAdapter(
       private val delegate: GraphSession,
   ) : GraphVirtualThreadSession {

       companion object : KLogging()

       override fun createGraphAsync(name: String): CompletableFuture<Void> =
           CompletableFuture.runAsync {
               StructuredTaskScopes.withAll { scope ->
                   scope.fork { delegate.createGraph(name) }
                   scope.join().throwIfFailed()
               }
           }

       override fun dropGraphAsync(name: String): CompletableFuture<Void> =
           CompletableFuture.runAsync {
               StructuredTaskScopes.withAll { scope ->
                   scope.fork { delegate.dropGraph(name) }
                   scope.join().throwIfFailed()
               }
           }

       override fun graphExistsAsync(name: String): CompletableFuture<Boolean> =
           CompletableFuture.supplyAsync {
               StructuredTaskScopes.withAll { scope ->
                   val subtask = scope.fork { delegate.graphExists(name) }
                   scope.join().throwIfFailed()
                   subtask.get()
               }
           }
   }
   ```

- [ ] **Add dependencies** (if missing):

   File: `/Users/debop/work/bluetape4k/bluetape4k-graph/graph/graph-core/build.gradle.kts`

   ```kotlin
   dependencies {
       api(Libs.bluetape4k_core)
       implementation(Libs.bluetape4k_virtualthread_api)   // <-- NEW: StructuredTaskScopes
       implementation(Libs.bluetape4k_coroutines)
       implementation(Libs.kotlinx_coroutines_core)

       testImplementation(Libs.bluetape4k_junit5)          // StructuredTaskScopeTester 포함
       testImplementation(Libs.bluetape4k_testcontainers)
       testImplementation(Libs.kotlinx_coroutines_test)
       testImplementation(project(":graph-tinkerpop"))     // <-- NEW
   }
   ```

- [ ] **Run**: `./gradlew :graph-core:test --tests "io.bluetape4k.graph.vt.VirtualThreadSessionAdapterTest"` → `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Commit**: `feat(graph-core): add GraphVirtualThreadSession + adapter`

---

## Task 2 — `GraphVirtualThreadVertexRepository` interface + `VirtualThreadVertexAdapter` + test

**Complexity**: medium

### TDD Steps

- [ ] **RED — Test**

   File: `/Users/debop/work/bluetape4k/bluetape4k-graph/graph/graph-core/src/test/kotlin/io/bluetape4k/graph/vt/VirtualThreadVertexAdapterTest.kt`

   ```kotlin
   package io.bluetape4k.graph.vt

   import io.bluetape4k.graph.model.GraphElementId
   import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
   import io.mockk.spyk
   import io.mockk.verify
   import org.amshove.kluent.shouldBeEqualTo
   import org.amshove.kluent.shouldNotBeNull
   import org.junit.jupiter.api.AfterEach
   import org.junit.jupiter.api.BeforeEach
   import io.bluetape4k.junit5.concurrency.StructuredTaskScopeTester
   import org.junit.jupiter.api.Test

   class VirtualThreadVertexAdapterTest {

       private lateinit var delegate: TinkerGraphOperations
       private lateinit var adapter: VirtualThreadVertexAdapter

       @BeforeEach
       fun setUp() {
           delegate = spyk(TinkerGraphOperations())
           adapter = VirtualThreadVertexAdapter(delegate)
       }

       @AfterEach
       fun tearDown() {
           delegate.close()
       }

       @Test
       fun `createVertexAsync creates and returns vertex`() {
           val v = adapter.createVertexAsync("Person", mapOf("name" to "Alice")).join()
           v.shouldNotBeNull()
           v.label shouldBeEqualTo "Person"
           verify(exactly = 1) { delegate.createVertex("Person", mapOf("name" to "Alice")) }
       }

       @Test
       fun `findVertexByIdAsync returns created vertex`() {
           val created = delegate.createVertex("Person", mapOf("name" to "Bob"))
           val found = adapter.findVertexByIdAsync("Person", created.id).join()
           found.shouldNotBeNull().id shouldBeEqualTo created.id
       }

       @Test
       fun `findVertexByIdAsync returns null when missing`() {
           adapter.findVertexByIdAsync("Person", GraphElementId.of("missing-id")).join() shouldBeEqualTo null
       }

       @Test
       fun `findVerticesByLabelAsync returns list`() {
           delegate.createVertex("Person", mapOf("name" to "A"))
           delegate.createVertex("Person", mapOf("name" to "B"))
           val list = adapter.findVerticesByLabelAsync("Person").join()
           list.size shouldBeEqualTo 2
       }

       @Test
       fun `updateVertexAsync updates properties`() {
           val v = delegate.createVertex("Person", mapOf("age" to 30))
           val updated = adapter.updateVertexAsync("Person", v.id, mapOf("age" to 31)).join()
           updated.shouldNotBeNull().properties["age"] shouldBeEqualTo 31
       }

       @Test
       fun `deleteVertexAsync deletes existing vertex`() {
           val v = delegate.createVertex("Person")
           adapter.deleteVertexAsync("Person", v.id).join() shouldBeEqualTo true
       }

       @Test
       fun `countVerticesAsync returns count`() {
           delegate.createVertex("Person")
           delegate.createVertex("Person")
           adapter.countVerticesAsync("Person").join() shouldBeEqualTo 2L
       }
   }
   ```

- [ ] **Verify compile fail**.

- [ ] **GREEN — Interface**

   File: `/Users/debop/work/bluetape4k/bluetape4k-graph/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphVirtualThreadVertexRepository.kt`

   ```kotlin
   package io.bluetape4k.graph.repository

   import io.bluetape4k.graph.model.GraphElementId
   import io.bluetape4k.graph.model.GraphVertex
   import java.util.concurrent.CompletableFuture

   /**
    * Virtual Thread 기반 그래프 정점(Vertex) CRUD 저장소.
    *
    * 모든 메서드는 동기 [GraphVertexRepository] 를 Virtual Thread executor 위에서 실행한
    * 결과를 `CompletableFuture<T>` 로 반환한다. Java interop 및 CompletableFuture 파이프라인용이다.
    */
   interface GraphVirtualThreadVertexRepository {

       fun createVertexAsync(
           label: String,
           properties: Map<String, Any?> = emptyMap(),
       ): CompletableFuture<GraphVertex>

       fun findVertexByIdAsync(
           label: String,
           id: GraphElementId,
       ): CompletableFuture<GraphVertex?>

       fun findVerticesByLabelAsync(
           label: String,
           filter: Map<String, Any?> = emptyMap(),
       ): CompletableFuture<List<GraphVertex>>

       fun updateVertexAsync(
           label: String,
           id: GraphElementId,
           properties: Map<String, Any?>,
       ): CompletableFuture<GraphVertex?>

       fun deleteVertexAsync(
           label: String,
           id: GraphElementId,
       ): CompletableFuture<Boolean>

       fun countVerticesAsync(label: String): CompletableFuture<Long>
   }
   ```

- [ ] **GREEN — Adapter**

   File: `/Users/debop/work/bluetape4k/bluetape4k-graph/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/vt/VirtualThreadVertexAdapter.kt`

   ```kotlin
   package io.bluetape4k.graph.vt

   import io.bluetape4k.graph.model.GraphElementId
   import io.bluetape4k.graph.model.GraphVertex
   import io.bluetape4k.graph.repository.GraphVertexRepository
   import io.bluetape4k.graph.repository.GraphVirtualThreadVertexRepository
   import io.bluetape4k.logging.KLogging
   import java.util.concurrent.CompletableFuture
   import java.util.concurrent.ExecutorService
   import java.util.concurrent.Executors

   class VirtualThreadVertexAdapter(
       private val delegate: GraphVertexRepository,
   ) : GraphVirtualThreadVertexRepository {

       companion object : KLogging()

       override fun createVertexAsync(label: String, properties: Map<String, Any?>): CompletableFuture<GraphVertex> =
           CompletableFuture.supplyAsync {
               StructuredTaskScopes.withAll { scope ->
                   val t = scope.fork { delegate.createVertex(label, properties) }
                   scope.join().throwIfFailed(); t.get()
               }
           }

       override fun findVertexByIdAsync(label: String, id: GraphElementId): CompletableFuture<GraphVertex?> =
           CompletableFuture.supplyAsync {
               StructuredTaskScopes.withAll { scope ->
                   val t = scope.fork { delegate.findVertexById(label, id) }
                   scope.join().throwIfFailed(); t.get()
               }
           }

       override fun findVerticesByLabelAsync(label: String, filter: Map<String, Any?>): CompletableFuture<List<GraphVertex>> =
           CompletableFuture.supplyAsync {
               StructuredTaskScopes.withAll { scope ->
                   val t = scope.fork { delegate.findVerticesByLabel(label, filter) }
                   scope.join().throwIfFailed(); t.get()
               }
           }

       override fun updateVertexAsync(label: String, id: GraphElementId, properties: Map<String, Any?>): CompletableFuture<GraphVertex?> =
           CompletableFuture.supplyAsync {
               StructuredTaskScopes.withAll { scope ->
                   val t = scope.fork { delegate.updateVertex(label, id, properties) }
                   scope.join().throwIfFailed(); t.get()
               }
           }

       override fun deleteVertexAsync(label: String, id: GraphElementId): CompletableFuture<Boolean> =
           CompletableFuture.supplyAsync {
               StructuredTaskScopes.withAll { scope ->
                   val t = scope.fork { delegate.deleteVertex(label, id) }
                   scope.join().throwIfFailed(); t.get()
               }
           }

       override fun countVerticesAsync(label: String): CompletableFuture<Long> =
           CompletableFuture.supplyAsync {
               StructuredTaskScopes.withAll { scope ->
                   val t = scope.fork { delegate.countVertices(label) }
                   scope.join().throwIfFailed(); t.get()
               }
           }
   }
   ```

- [ ] **Run**: `./gradlew :graph-core:test --tests "io.bluetape4k.graph.vt.VirtualThreadVertexAdapterTest"` → 7 tests passed.

- [ ] **Commit**: `feat(graph-core): add GraphVirtualThreadVertexRepository + adapter`

---

## Task 3 — `GraphVirtualThreadEdgeRepository` interface + `VirtualThreadEdgeAdapter` + test

**Complexity**: medium

### TDD Steps

- [ ] **RED — Test**

   File: `/Users/debop/work/bluetape4k/bluetape4k-graph/graph/graph-core/src/test/kotlin/io/bluetape4k/graph/vt/VirtualThreadEdgeAdapterTest.kt`

   ```kotlin
   package io.bluetape4k.graph.vt

   import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
   import io.bluetape4k.junit5.concurrency.StructuredTaskScopeTester
   import io.mockk.spyk
   import io.mockk.verify
   import org.amshove.kluent.shouldBeEqualTo
   import org.amshove.kluent.shouldNotBeNull
   import org.junit.jupiter.api.AfterEach
   import org.junit.jupiter.api.BeforeEach
   import org.junit.jupiter.api.Test

   class VirtualThreadEdgeAdapterTest {

       private lateinit var delegate: TinkerGraphOperations
       private lateinit var adapter: VirtualThreadEdgeAdapter

       @BeforeEach
       fun setUp() {
           delegate = spyk(TinkerGraphOperations())
           adapter = VirtualThreadEdgeAdapter(delegate)
       }

       @AfterEach
       fun tearDown() {
           delegate.close()
       }

       @Test
       fun `createEdgeAsync creates edge`() {
           val a = delegate.createVertex("Person")
           val b = delegate.createVertex("Person")
           val e = adapter.createEdgeAsync(a.id, b.id, "KNOWS").join()
           e.shouldNotBeNull()
           e.label shouldBeEqualTo "KNOWS"
           verify(exactly = 1) { delegate.createEdge(a.id, b.id, "KNOWS", emptyMap()) }
       }

       @Test
       fun `findEdgesByLabelAsync returns edges`() {
           val a = delegate.createVertex("P"); val b = delegate.createVertex("P")
           delegate.createEdge(a.id, b.id, "REL")
           val list = adapter.findEdgesByLabelAsync("REL").join()
           list.size shouldBeEqualTo 1
       }

       @Test
       fun `deleteEdgeAsync deletes edge`() {
           val a = delegate.createVertex("P"); val b = delegate.createVertex("P")
           val e = delegate.createEdge(a.id, b.id, "REL")
           adapter.deleteEdgeAsync("REL", e.id).join() shouldBeEqualTo true
       }

       @Test
       fun `findEdgesByLabelAsync is thread-safe under concurrent load`() {
           StructuredTaskScopeTester()
               .rounds(50)
               .add { adapter.findEdgesByLabelAsync("REL").join().shouldNotBeNull() }
               .run()
       }
   }
   ```

- [ ] **GREEN — Interface**

   File: `/Users/debop/work/bluetape4k/bluetape4k-graph/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphVirtualThreadEdgeRepository.kt`

   ```kotlin
   package io.bluetape4k.graph.repository

   import io.bluetape4k.graph.model.GraphEdge
   import io.bluetape4k.graph.model.GraphElementId
   import java.util.concurrent.CompletableFuture

   /**
    * Virtual Thread 기반 그래프 간선(Edge) CRUD 저장소.
    */
   interface GraphVirtualThreadEdgeRepository {

       fun createEdgeAsync(
           fromId: GraphElementId,
           toId: GraphElementId,
           label: String,
           properties: Map<String, Any?> = emptyMap(),
       ): CompletableFuture<GraphEdge>

       fun findEdgesByLabelAsync(
           label: String,
           filter: Map<String, Any?> = emptyMap(),
       ): CompletableFuture<List<GraphEdge>>

       fun deleteEdgeAsync(
           label: String,
           id: GraphElementId,
       ): CompletableFuture<Boolean>
   }
   ```

- [ ] **GREEN — Adapter**

   File: `/Users/debop/work/bluetape4k/bluetape4k-graph/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/vt/VirtualThreadEdgeAdapter.kt`

   ```kotlin
   package io.bluetape4k.graph.vt

   import io.bluetape4k.concurrent.virtualthread.StructuredTaskScopes
   import io.bluetape4k.graph.model.GraphEdge
   import io.bluetape4k.graph.model.GraphElementId
   import io.bluetape4k.graph.repository.GraphEdgeRepository
   import io.bluetape4k.graph.repository.GraphVirtualThreadEdgeRepository
   import io.bluetape4k.logging.KLogging
   import java.util.concurrent.CompletableFuture

   class VirtualThreadEdgeAdapter(
       private val delegate: GraphEdgeRepository,
   ) : GraphVirtualThreadEdgeRepository {

       companion object : KLogging()

       override fun createEdgeAsync(
           fromId: GraphElementId,
           toId: GraphElementId,
           label: String,
           properties: Map<String, Any?>,
       ): CompletableFuture<GraphEdge> =
           CompletableFuture.supplyAsync {
               StructuredTaskScopes.withAll { scope ->
                   val t = scope.fork { delegate.createEdge(fromId, toId, label, properties) }
                   scope.join().throwIfFailed(); t.get()
               }
           }

       override fun findEdgesByLabelAsync(label: String, filter: Map<String, Any?>): CompletableFuture<List<GraphEdge>> =
           CompletableFuture.supplyAsync {
               StructuredTaskScopes.withAll { scope ->
                   val t = scope.fork { delegate.findEdgesByLabel(label, filter) }
                   scope.join().throwIfFailed(); t.get()
               }
           }

       override fun deleteEdgeAsync(id: GraphElementId): CompletableFuture<Boolean> =
           CompletableFuture.supplyAsync {
               StructuredTaskScopes.withAll { scope ->
                   val t = scope.fork { delegate.deleteEdge(id) }
                   scope.join().throwIfFailed(); t.get()
               }
           }

       override fun countEdgesAsync(label: String): CompletableFuture<Long> =
           CompletableFuture.supplyAsync {
               StructuredTaskScopes.withAll { scope ->
                   val t = scope.fork { delegate.countEdges(label) }
                   scope.join().throwIfFailed(); t.get()
               }
           }
   }
   ```

- [ ] **Run**: `./gradlew :graph-core:test --tests "io.bluetape4k.graph.vt.VirtualThreadEdgeAdapterTest"` → 4 tests passed.

- [ ] **Commit**: `feat(graph-core): add GraphVirtualThreadEdgeRepository + adapter`

---

## Task 4 — `GraphVirtualThreadTraversalRepository` interface + `VirtualThreadTraversalAdapter` + test

**Complexity**: medium

### TDD Steps

- [ ] **RED — Test**

   File: `/Users/debop/work/bluetape4k/bluetape4k-graph/graph/graph-core/src/test/kotlin/io/bluetape4k/graph/vt/VirtualThreadTraversalAdapterTest.kt`

   ```kotlin
   package io.bluetape4k.graph.vt

   import io.bluetape4k.graph.model.NeighborOptions
   import io.bluetape4k.graph.model.PathOptions
   import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
   import io.bluetape4k.junit5.concurrency.StructuredTaskScopeTester
   import io.mockk.spyk
   import io.mockk.verify
   import org.amshove.kluent.shouldBeEqualTo
   import org.amshove.kluent.shouldBeGreaterThan
   import org.amshove.kluent.shouldNotBeNull
   import org.junit.jupiter.api.AfterEach
   import org.junit.jupiter.api.BeforeEach
   import org.junit.jupiter.api.Test

   class VirtualThreadTraversalAdapterTest {

       private lateinit var delegate: TinkerGraphOperations
       private lateinit var adapter: VirtualThreadTraversalAdapter

       @BeforeEach
       fun setUp() {
           delegate = spyk(TinkerGraphOperations())
           adapter = VirtualThreadTraversalAdapter(delegate)
       }

       @AfterEach
       fun tearDown() {
           delegate.close()
       }

       @Test
       fun `neighborsAsync returns list`() {
           val a = delegate.createVertex("P"); val b = delegate.createVertex("P")
           delegate.createEdge(a.id, b.id, "REL")
           val list = adapter.neighborsAsync(a.id, NeighborOptions(edgeLabel = "REL")).join()
           list.size shouldBeEqualTo 1
           verify(exactly = 1) { delegate.neighbors(a.id, NeighborOptions(edgeLabel = "REL")) }
       }

       @Test
       fun `shortestPathAsync returns path`() {
           val a = delegate.createVertex("P"); val b = delegate.createVertex("P")
           delegate.createEdge(a.id, b.id, "REL")
           val path = adapter.shortestPathAsync(a.id, b.id, PathOptions(edgeLabel = "REL")).join()
           path.shouldNotBeNull().length shouldBeGreaterThan 0
       }

       @Test
       fun `shortestPathAsync returns null when no path`() {
           val a = delegate.createVertex("P"); val b = delegate.createVertex("P")
           adapter.shortestPathAsync(a.id, b.id, PathOptions()).join() shouldBeEqualTo null
       }

       @Test
       fun `allPathsAsync returns list`() {
           val a = delegate.createVertex("P"); val b = delegate.createVertex("P")
           delegate.createEdge(a.id, b.id, "REL")
           val paths = adapter.allPathsAsync(a.id, b.id, PathOptions(edgeLabel = "REL")).join()
           paths.size shouldBeGreaterThan 0
       }

       @Test
       fun `neighborsAsync is thread-safe under concurrent load`() {
           val v = delegate.createVertex("Node", mapOf("name" to "A"))
           StructuredTaskScopeTester()
               .rounds(50)
               .add { adapter.neighborsAsync("Node", v.id).join().shouldNotBeNull() }
               .run()
       }
   }
   ```

- [ ] **GREEN — Interface**

   File: `/Users/debop/work/bluetape4k/bluetape4k-graph/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphVirtualThreadTraversalRepository.kt`

   ```kotlin
   package io.bluetape4k.graph.repository

   import io.bluetape4k.graph.model.GraphElementId
   import io.bluetape4k.graph.model.GraphPath
   import io.bluetape4k.graph.model.GraphVertex
   import io.bluetape4k.graph.model.NeighborOptions
   import io.bluetape4k.graph.model.PathOptions
   import java.util.concurrent.CompletableFuture

   /**
    * Virtual Thread 기반 그래프 순회(Traversal) 저장소.
    */
   interface GraphVirtualThreadTraversalRepository {

       fun neighborsAsync(
           startId: GraphElementId,
           options: NeighborOptions = NeighborOptions.Default,
       ): CompletableFuture<List<GraphVertex>>

       fun shortestPathAsync(
           fromId: GraphElementId,
           toId: GraphElementId,
           options: PathOptions = PathOptions.Default,
       ): CompletableFuture<GraphPath?>

       fun allPathsAsync(
           fromId: GraphElementId,
           toId: GraphElementId,
           options: PathOptions = PathOptions.Default,
       ): CompletableFuture<List<GraphPath>>
   }
   ```

- [ ] **GREEN — Adapter**

   File: `/Users/debop/work/bluetape4k/bluetape4k-graph/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/vt/VirtualThreadTraversalAdapter.kt`

   ```kotlin
   package io.bluetape4k.graph.vt

   import io.bluetape4k.concurrent.virtualthread.StructuredTaskScopes
   import io.bluetape4k.graph.model.GraphElementId
   import io.bluetape4k.graph.model.GraphPath
   import io.bluetape4k.graph.model.GraphVertex
   import io.bluetape4k.graph.model.NeighborOptions
   import io.bluetape4k.graph.model.PathOptions
   import io.bluetape4k.graph.repository.GraphTraversalRepository
   import io.bluetape4k.graph.repository.GraphVirtualThreadTraversalRepository
   import io.bluetape4k.logging.KLogging
   import java.util.concurrent.CompletableFuture

   class VirtualThreadTraversalAdapter(
       private val delegate: GraphTraversalRepository,
   ) : GraphVirtualThreadTraversalRepository {

       companion object : KLogging()

       override fun neighborsAsync(
           startId: GraphElementId,
           options: NeighborOptions,
       ): CompletableFuture<List<GraphVertex>> =
           CompletableFuture.supplyAsync {
               StructuredTaskScopes.withAll { scope ->
                   val t = scope.fork { delegate.neighbors(startId, options) }
                   scope.join().throwIfFailed(); t.get()
               }
           }

       override fun shortestPathAsync(
           fromId: GraphElementId,
           toId: GraphElementId,
           options: PathOptions,
       ): CompletableFuture<GraphPath?> =
           CompletableFuture.supplyAsync {
               StructuredTaskScopes.withAll { scope ->
                   val t = scope.fork { delegate.shortestPath(fromId, toId, options) }
                   scope.join().throwIfFailed(); t.get()
               }
           }

       override fun allPathsAsync(
           fromId: GraphElementId,
           toId: GraphElementId,
           options: PathOptions,
       ): CompletableFuture<List<GraphPath>> =
           CompletableFuture.supplyAsync {
               StructuredTaskScopes.withAll { scope ->
                   val t = scope.fork { delegate.allPaths(fromId, toId, options) }
                   scope.join().throwIfFailed(); t.get()
               }
           }
   }
   ```

   **Note**: `GraphTraversalRepository` must be reachable as a constructor parameter type. Inspect
   `GraphOperations` composition — if it composes `GraphGenericRepository` that transitively
   includes traversal, `GraphOperations` still satisfies the narrower type when auto-upcast.

- [ ] **Run**: `./gradlew :graph-core:test --tests "io.bluetape4k.graph.vt.VirtualThreadTraversalAdapterTest"` → 5 tests passed.

- [ ] **Commit**: `feat(graph-core): add GraphVirtualThreadTraversalRepository + adapter`

---

## Task 5 — Facade + `VirtualThreadOperationsAdapter` (Kotlin `by`) + `asVirtualThread()` extension + tests

**Complexity**: high

### Why high
- 5개 인터페이스를 Kotlin `by` 위임으로 합성하는 것이 문법적으로 가장 정밀한 부분.
- `GraphOperations.asVirtualThread()` 와 기존 `GraphAlgorithmRepository.asVirtualThread()` 의 **오버로드 해석** 을 컴파일타임에 보장해야 한다.
- `close()` 가 executor 만 shutdown 하고 delegate 는 건드리지 않는 소유권 규칙 테스트.

### TDD Steps

- [ ] **RED — Facade / Adapter test**

   File: `/Users/debop/work/bluetape4k/bluetape4k-graph/graph/graph-core/src/test/kotlin/io/bluetape4k/graph/vt/VirtualThreadOperationsAdapterTest.kt`

   ```kotlin
   package io.bluetape4k.graph.vt

   import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
   import io.bluetape4k.junit5.concurrency.StructuredTaskScopeTester
   import io.mockk.spyk
   import io.mockk.verify
   import org.amshove.kluent.shouldNotBeNull
   import org.junit.jupiter.api.AfterEach
   import org.junit.jupiter.api.BeforeEach
   import org.junit.jupiter.api.Test

   class VirtualThreadOperationsAdapterTest {

       private lateinit var delegate: TinkerGraphOperations
       private lateinit var adapter: VirtualThreadOperationsAdapter

       @BeforeEach
       fun setUp() {
           delegate = spyk(TinkerGraphOperations())
           adapter = VirtualThreadOperationsAdapter(delegate)
       }

       @AfterEach
       fun tearDown() {
           delegate.close()
       }

       @Test
       fun `vertex CRUD routes through virtual thread`() {
           val v = adapter.createVertexAsync("Person", mapOf("name" to "Alice")).join()
           v.shouldNotBeNull()
           verify(exactly = 1) { delegate.createVertex("Person", mapOf("name" to "Alice")) }
       }

       @Test
       fun `edge CRUD routes through virtual thread`() {
           val a = delegate.createVertex("P")
           val b = delegate.createVertex("P")
           adapter.createEdgeAsync(a.id, b.id, "REL").join().shouldNotBeNull()
       }

       @Test
       fun `algorithm routes through virtual thread`() {
           adapter.pageRankAsync().join().shouldNotBeNull()
       }

       @Test
       fun `session sync passthrough does not use virtual thread`() {
           // GraphSession methods are synchronous — facade forwards to delegate directly via 'by'.
           adapter.createGraph("x")
           verify(exactly = 1) { delegate.createGraph("x") }
       }

       @Test
       fun `close leaves delegate usable`() {
           adapter.close()
           // withAll 기반으로 executor 없음 — delegate 는 여전히 사용 가능
           delegate.createVertex("P").shouldNotBeNull()
       }

       @Test
       fun `asVirtualThread operations are thread-safe`() {
           val ops = TinkerGraphOperations()
           val vtOps = ops.asVirtualThread()
           StructuredTaskScopeTester()
               .rounds(50)
               .add { vtOps.createVertexAsync("Node", mapOf()).join().shouldNotBeNull() }
               .run()
           ops.close()
       }
   }
   ```

- [ ] **RED — Extension overload test**

   File: `/Users/debop/work/bluetape4k/bluetape4k-graph/graph/graph-core/src/test/kotlin/io/bluetape4k/graph/vt/VirtualThreadOperationsExtTest.kt`

   ```kotlin
   package io.bluetape4k.graph.vt

   import io.bluetape4k.graph.algo.asVirtualThread   // existing extension (unchanged)
   import io.bluetape4k.graph.repository.GraphAlgorithmRepository
   import io.bluetape4k.graph.repository.GraphOperations
   import io.bluetape4k.graph.repository.GraphVirtualThreadAlgorithmRepository
   import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
   import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
   import org.amshove.kluent.shouldBeInstanceOf
   import org.junit.jupiter.api.AfterEach
   import org.junit.jupiter.api.BeforeEach
   import org.junit.jupiter.api.Test

   class VirtualThreadOperationsExtTest {

       private lateinit var ops: TinkerGraphOperations

       @BeforeEach
       fun setUp() { ops = TinkerGraphOperations() }

       @AfterEach
       fun tearDown() { ops.close() }

       @Test
       fun `GraphOperations asVirtualThread returns facade`() {
           val vt = (ops as GraphOperations).asVirtualThread()
           vt.shouldBeInstanceOf<GraphVirtualThreadOperations>()
           vt.close()
       }

       @Test
       fun `TinkerGraphOperations asVirtualThread resolves to facade without cast`() {
           // H2 검증: TinkerGraphOperations 타입 변수에서 asVirtualThread() 가
           // GraphAlgorithmRepository.asVirtualThread() 대신 GraphOperations.asVirtualThread()
           // 로 오버로드 해석되는지 컴파일+런타임 확인.
           val ops2 = TinkerGraphOperations()
           val vt = ops2.asVirtualThread()   // 캐스트 없이 — 컴파일 에러 시 H2 확정
           vt.shouldBeInstanceOf<GraphVirtualThreadOperations>()
           vt.close()
           ops2.close()
       }

       @Test
       fun `GraphAlgorithmRepository asVirtualThread returns algorithm-only adapter`() {
           val algo = (ops as GraphAlgorithmRepository).asVirtualThread()
           algo.shouldBeInstanceOf<GraphVirtualThreadAlgorithmRepository>()
       }
   }
   ```

- [ ] **GREEN — Facade interface**

   File: `/Users/debop/work/bluetape4k/bluetape4k-graph/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphVirtualThreadOperations.kt`

   ```kotlin
   package io.bluetape4k.graph.repository

   /**
    * Virtual Thread 기반 그래프 Facade.
    *
    * 동기 lifecycle([GraphSession]) + 전체 비동기 기능(Session / Vertex / Edge / Traversal / Algorithm).
    * `AutoCloseable` 은 [GraphSession] 이 이미 상속한다.
    *
    * Kotlin 코드는 [GraphSuspendOperations] 사용을 권장한다.
    */
   interface GraphVirtualThreadOperations :
       GraphSession,
       GraphVirtualThreadSession,
       GraphVirtualThreadVertexRepository,
       GraphVirtualThreadEdgeRepository,
       GraphVirtualThreadTraversalRepository,
       GraphVirtualThreadAlgorithmRepository
   ```

- [ ] **GREEN — Operations Adapter (Kotlin `by`)**

   File: `/Users/debop/work/bluetape4k/bluetape4k-graph/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/vt/VirtualThreadOperationsAdapter.kt`

   ```kotlin
   package io.bluetape4k.graph.vt

   import io.bluetape4k.graph.algo.VirtualThreadAlgorithmAdapter
   import io.bluetape4k.graph.repository.GraphOperations
   import io.bluetape4k.graph.repository.GraphSession
   import io.bluetape4k.graph.repository.GraphVirtualThreadAlgorithmRepository
   import io.bluetape4k.graph.repository.GraphVirtualThreadEdgeRepository
   import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
   import io.bluetape4k.graph.repository.GraphVirtualThreadSession
   import io.bluetape4k.graph.repository.GraphVirtualThreadTraversalRepository
   import io.bluetape4k.graph.repository.GraphVirtualThreadVertexRepository
   import io.bluetape4k.logging.KLogging

   /**
    * [GraphOperations] 의 전체 표면(Session / Vertex / Edge / Traversal / Algorithm) 을
    * Virtual Thread 위에서 실행하는 어댑터 facade.
    *
    * Kotlin `by` 위임으로 5개 어댑터를 합성한다. 각 어댑터는 `StructuredTaskScopes.withAll` 을 사용한다.
    *
    * ### 사용 예제
    * ```kotlin
    * val ops: GraphOperations = TinkerGraphOperations()
    * val vt: GraphVirtualThreadOperations = ops.asVirtualThread()
    * vt.createVertexAsync("Person", mapOf("name" to "Alice")).thenApply { println(it) }
    * vt.close()   // withAll 기반으로 executor 없음 — delegate 도 닫지 않는다
    * ```
    */
   class VirtualThreadOperationsAdapter(
       private val delegate: GraphOperations,
   ) : GraphVirtualThreadOperations,
       GraphSession by delegate,
       GraphVirtualThreadSession by VirtualThreadSessionAdapter(delegate),
       GraphVirtualThreadVertexRepository by VirtualThreadVertexAdapter(delegate),
       GraphVirtualThreadEdgeRepository by VirtualThreadEdgeAdapter(delegate),
       GraphVirtualThreadTraversalRepository by VirtualThreadTraversalAdapter(delegate),
       GraphVirtualThreadAlgorithmRepository by VirtualThreadAlgorithmAdapter(delegate) {

       companion object : KLogging()

       /**
        * withAll 기반으로 executor 없음 — 소유권 원칙상 delegate 도 닫지 않는다.
        */
       override fun close() {
           // withAll 기반으로 executor 없음 — 소유권 원칙상 delegate 도 닫지 않는다.
       }
   }
   ```

   **설계 주의**
   - `GraphSession by delegate` 와 `GraphVirtualThreadSession by VirtualThreadSessionAdapter(...)` 가 동시 존재 → `createGraph/dropGraph/graphExists` 는 delegate 로, `createGraphAsync/...` 는 VT adapter 로 분기됨.
   - `close()` 는 `GraphSession` 의 `AutoCloseable` 에서 오는 메서드이므로 **override 명시**. executor 가 없으므로 빈 body.

- [ ] **GREEN — Extension file**

   File: `/Users/debop/work/bluetape4k/bluetape4k-graph/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/vt/VirtualThreadOperationsExt.kt`

   ```kotlin
   package io.bluetape4k.graph.vt

   import io.bluetape4k.graph.repository.GraphOperations
   import io.bluetape4k.graph.repository.GraphVirtualThreadOperations

   /**
    * [GraphOperations] 를 Virtual Thread 기반 facade 로 감싸는 확장 함수.
    *
    * Kotlin receiver resolution 규칙에 따라 더 구체적인 receiver 를 가진 이 확장이
    * 기존 `GraphAlgorithmRepository.asVirtualThread()` 보다 우선 선택된다.
    *
    * ```kotlin
    * val ops: GraphOperations = TinkerGraphOperations()
    * val vt = ops.asVirtualThread()
    * ```
    */
   fun GraphOperations.asVirtualThread(): GraphVirtualThreadOperations =
       VirtualThreadOperationsAdapter(this)
   ```

- [ ] **Run**: `./gradlew :graph-core:test --tests "io.bluetape4k.graph.vt.*"` → all 6 test classes pass.

- [ ] **Commit**: `feat(graph-core): add GraphVirtualThreadOperations facade + asVirtualThread() on GraphOperations`

---

## Task 6 — `graph-core` full test suite + test dep confirmation

**Complexity**: low (elevated to high if Gradle rejects reverse test dependency)

### Goal
1. Confirm `testImplementation(project(":graph-tinkerpop"))` is present in `graph-core/build.gradle.kts`.
2. Run entire `:graph-core:test` → all existing tests + 6 new VT tests pass.

### Steps

- [ ] **Verify dep graph**

   ```bash
   ./gradlew :graph-core:dependencies --configuration testRuntimeClasspath
   ```

   Expected: `graph-tinkerpop` appears in the tree. **No cycle error.**

   **Fallback**: if Gradle reports a cycle, relocate all 6 VT adapter test files to
   `graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/vt/` and remove the
   `testImplementation(project(":graph-tinkerpop"))` line. Commit message then becomes
   `test(graph-tinkerpop): add VT adapter tests`.

- [ ] **Run `:graph-core:test` fully**

   ```bash
   ./gradlew :graph-core:test
   ```

   Expected: `BUILD SUCCESSFUL`; all tests including 6 new VT test classes pass.

- [ ] **Commit**: `chore(graph-core): wire graph-tinkerpop test dep for VT adapter tests`

---

## Task 7 — `graph-benchmark` module scaffold

**Complexity**: medium

### Goal
- Create module directory with `build.gradle.kts`.
- `settings.gradle.kts` auto-includes it (because `includeModules("graph", ...)` iterates subdirectories with `build.gradle.kts`).
- Verify with `./gradlew projects`.
- Exclude from Maven Central aggregation.

### Steps

- [ ] **Create directory structure**

   ```bash
   mkdir -p /Users/debop/work/bluetape4k/bluetape4k-graph/graph/graph-benchmark/src/benchmark/kotlin/io/bluetape4k/graph/benchmark/setup
   ```

- [ ] **Create `build.gradle.kts`**

   File: `/Users/debop/work/bluetape4k/bluetape4k-graph/graph/graph-benchmark/build.gradle.kts`

   ```kotlin
   plugins {
       kotlin("plugin.allopen")
       id(Plugins.kotlinx_benchmark)
   }

   allOpen {
       annotation("org.openjdk.jmh.annotations.State")
   }

   sourceSets {
       create("benchmark") {
           kotlin.srcDirs("src/benchmark/kotlin")
       }
   }

   dependencies {
       "benchmarkImplementation"(project(":graph-core"))
       "benchmarkImplementation"(project(":graph-tinkerpop"))
       "benchmarkImplementation"(Libs.kotlinx_benchmark_runtime)
       "benchmarkImplementation"(Libs.kotlinx_coroutines_core)
       "benchmarkImplementation"(Libs.bluetape4k_coroutines)
   }

   benchmark {
       targets {
           register("benchmark")
       }
       configurations {
           named("main") {
               warmups = 3
               iterations = 5
               iterationTime = 1
               iterationTimeUnit = "sec"
               mode = "thrpt"
               outputTimeUnit = "sec"
               reportFormat = "json"
           }
       }
   }
   ```

   **Notes**
   - `settings.gradle.kts` auto-picks modules under `graph/` that have `build.gradle.kts`.
   - `kotlin("plugin.allopen")` inherits version from the root Kotlin plugin convention. If the
     root build doesn't apply the Kotlin plugin to subprojects automatically, add
     `kotlin("jvm")` explicitly to the plugins block.

- [ ] **Maven Central aggregation exclusion**

   Inspect root `build.gradle.kts` for the Central Portal aggregation configuration. Find the subproject filter (the same rule that excludes `examples/` modules) and add `graph-benchmark` to the exclusion list. If the rule is `examples/` path-based, no change needed — but verify.

   ```bash
   ./gradlew publishAggregationToCentralPortal --dry-run 2>&1 | grep -i benchmark
   ```

   Expected: no `graph-benchmark` entries in the publishing tasks. If present, add explicit exclusion.

- [ ] **Verify module recognized**

   ```bash
   ./gradlew projects
   ```

   Expected output includes `+--- Project ':graph-benchmark'`.

- [ ] **Verify module compiles (empty benchmark source)**

   ```bash
   ./gradlew :graph-benchmark:build -x test
   ```

   Expected: `BUILD SUCCESSFUL`.

- [ ] **Commit**: `feat(graph-benchmark): scaffold new module with kotlinx-benchmark plugin`

---

## Task 8 — `BenchmarkGraphFixture` + `VertexCrudBenchmark`

**Complexity**: medium

### Steps

- [ ] **Fixture**

   File: `/Users/debop/work/bluetape4k/bluetape4k-graph/graph/graph-benchmark/src/benchmark/kotlin/io/bluetape4k/graph/benchmark/setup/BenchmarkGraphFixture.kt`

   ```kotlin
   package io.bluetape4k.graph.benchmark.setup

   import io.bluetape4k.graph.model.GraphElementId
   import io.bluetape4k.graph.repository.GraphVirtualThreadOperations
   import io.bluetape4k.graph.tinkerpop.TinkerGraphOperations
   import io.bluetape4k.graph.tinkerpop.TinkerGraphSuspendOperations
   import io.bluetape4k.graph.vt.asVirtualThread
   import kotlinx.benchmark.Param
   import kotlinx.benchmark.Scope
   import kotlinx.benchmark.Setup
   import kotlinx.benchmark.State
   import kotlinx.benchmark.TearDown
   import kotlin.random.Random

   @State(Scope.Benchmark)
   open class BenchmarkGraphFixture {
       lateinit var ops: TinkerGraphOperations
       lateinit var vtOps: GraphVirtualThreadOperations
       lateinit var suspendOps: TinkerGraphSuspendOperations
       lateinit var vertexIds: List<GraphElementId>

       @Param("1000")
       var vertexCount: Int = 0

       @Setup
       fun setup() {
           ops = TinkerGraphOperations()
           vtOps = ops.asVirtualThread()
           suspendOps = TinkerGraphSuspendOperations(ops)   // direct instantiation — no asSuspend()
           vertexIds = (1..vertexCount).map {
               ops.createVertex("Node", mapOf("idx" to it)).id
           }
           val rnd = Random(42)
           repeat(vertexCount * 4) {
               ops.createEdge(vertexIds.random(rnd), vertexIds.random(rnd), "REL")
           }
       }

       @TearDown
       fun tearDown() {
           vtOps.close()
           ops.close()
       }
   }
   ```

- [ ] **`VertexCrudBenchmark`**

   File: `/Users/debop/work/bluetape4k/bluetape4k-graph/graph/graph-benchmark/src/benchmark/kotlin/io/bluetape4k/graph/benchmark/VertexCrudBenchmark.kt`

   ```kotlin
   package io.bluetape4k.graph.benchmark

   import io.bluetape4k.graph.benchmark.setup.BenchmarkGraphFixture
   import kotlinx.benchmark.Benchmark
   import kotlinx.benchmark.Blackhole
   import kotlinx.coroutines.runBlocking

   open class VertexCrudBenchmark: BenchmarkGraphFixture() {

       @Benchmark
       fun syncCreateFind(bh: Blackhole) {
           val v = ops.createVertex("Bench", mapOf("k" to "v"))
           bh.consume(ops.findVertexById("Bench", v.id))
       }

       @Benchmark
       fun vtCreateFind(bh: Blackhole) {
           val v = vtOps.createVertexAsync("Bench", mapOf("k" to "v")).join()
           bh.consume(vtOps.findVertexByIdAsync("Bench", v.id).join())
       }

       @Benchmark
       fun suspendCreateFind(bh: Blackhole) = runBlocking {
           val v = suspendOps.createVertex("Bench", mapOf("k" to "v"))
           bh.consume(suspendOps.findVertexById("Bench", v.id))
       }
   }
   ```

- [ ] **Compile check**

   ```bash
   ./gradlew :graph-benchmark:compileBenchmarkKotlin
   ```

   Expected: `BUILD SUCCESSFUL`.

- [ ] **Commit**: `feat(graph-benchmark): add VertexCrudBenchmark + fixture`

---

## Task 9 — `TraversalBenchmark` + `AlgorithmBenchmark`

**Complexity**: medium

### Steps

- [ ] **`TraversalBenchmark`**

   File: `/Users/debop/work/bluetape4k/bluetape4k-graph/graph/graph-benchmark/src/benchmark/kotlin/io/bluetape4k/graph/benchmark/TraversalBenchmark.kt`

   ```kotlin
   package io.bluetape4k.graph.benchmark

   import io.bluetape4k.graph.benchmark.setup.BenchmarkGraphFixture
   import io.bluetape4k.graph.model.NeighborOptions
   import io.bluetape4k.graph.model.PathOptions
   import kotlinx.benchmark.Benchmark
   import kotlinx.benchmark.Blackhole
   import kotlinx.coroutines.flow.toList
   import kotlinx.coroutines.runBlocking

   open class TraversalBenchmark: BenchmarkGraphFixture() {

       private val options = NeighborOptions(edgeLabel = "REL")
       private val pathOptions = PathOptions(edgeLabel = "REL", maxDepth = 4)

       @Benchmark
       fun syncNeighbors(bh: Blackhole) {
           bh.consume(ops.neighbors(vertexIds.first(), options))
       }

       @Benchmark
       fun vtNeighbors(bh: Blackhole) {
           bh.consume(vtOps.neighborsAsync(vertexIds.first(), options).join())
       }

       @Benchmark
       fun suspendNeighbors(bh: Blackhole) = runBlocking {
           bh.consume(suspendOps.neighbors(vertexIds.first(), options).toList())
       }

       @Benchmark
       fun syncShortestPath(bh: Blackhole) {
           bh.consume(ops.shortestPath(vertexIds.first(), vertexIds.last(), pathOptions))
       }

       @Benchmark
       fun vtShortestPath(bh: Blackhole) {
           bh.consume(vtOps.shortestPathAsync(vertexIds.first(), vertexIds.last(), pathOptions).join())
       }

       @Benchmark
       fun suspendShortestPath(bh: Blackhole) = runBlocking {
           bh.consume(suspendOps.shortestPath(vertexIds.first(), vertexIds.last(), pathOptions))
       }
   }
   ```

   **Note**: `suspendOps.neighbors(...)` returns `Flow<GraphVertex>`. `.toList()` collects it.
   `shortestPath(...)` likely returns `GraphPath?` directly (suspend). Verify at implementation time.

- [ ] **`AlgorithmBenchmark`**

   File: `/Users/debop/work/bluetape4k/bluetape4k-graph/graph/graph-benchmark/src/benchmark/kotlin/io/bluetape4k/graph/benchmark/AlgorithmBenchmark.kt`

   ```kotlin
   package io.bluetape4k.graph.benchmark

   import io.bluetape4k.graph.benchmark.setup.BenchmarkGraphFixture
   import io.bluetape4k.graph.model.BfsDfsOptions
   import io.bluetape4k.graph.model.PageRankOptions
   import kotlinx.benchmark.Benchmark
   import kotlinx.benchmark.BenchmarkMode
   import kotlinx.benchmark.Blackhole
   import kotlinx.benchmark.Mode
   import kotlinx.benchmark.OutputTimeUnit
   import kotlinx.coroutines.flow.toList
   import kotlinx.coroutines.runBlocking
   import java.util.concurrent.TimeUnit

   @BenchmarkMode(Mode.AverageTime)
   @OutputTimeUnit(TimeUnit.MICROSECONDS)
   open class AlgorithmBenchmark: BenchmarkGraphFixture() {

       private val pageRankOpts = PageRankOptions(topK = 100)
       private val bfsOpts = BfsDfsOptions(maxDepth = 3)

       @Benchmark
       fun syncPageRank(bh: Blackhole) {
           bh.consume(ops.pageRank(pageRankOpts))
       }

       @Benchmark
       fun vtPageRank(bh: Blackhole) {
           bh.consume(vtOps.pageRankAsync(pageRankOpts).join())
       }

       @Benchmark
       fun suspendPageRank(bh: Blackhole) = runBlocking {
           bh.consume(suspendOps.pageRank(pageRankOpts).toList())
       }

       @Benchmark
       fun syncBfs(bh: Blackhole) {
           bh.consume(ops.bfs(vertexIds.first(), bfsOpts))
       }

       @Benchmark
       fun vtBfs(bh: Blackhole) {
           bh.consume(vtOps.bfsAsync(vertexIds.first(), bfsOpts).join())
       }

       @Benchmark
       fun suspendBfs(bh: Blackhole) = runBlocking {
           bh.consume(suspendOps.bfs(vertexIds.first(), bfsOpts).toList())
       }
   }
   ```

   **Note**: verify `pageRank`/`bfs` suspend signatures (Flow vs suspend List) on
   `TinkerGraphSuspendOperations` when implementing and adjust `.toList()` wrapping accordingly.

- [ ] **Compile check**

   ```bash
   ./gradlew :graph-benchmark:compileBenchmarkKotlin
   ```

- [ ] **Commit**: `feat(graph-benchmark): add TraversalBenchmark + AlgorithmBenchmark`

---

## Task 10 — `ConcurrencyBenchmark` (read-only 100-way parallel)

**Complexity**: high

### Why high
Thread-safety 제약을 **코드 주석 + 스펙 참조** 로 명시해야 함. 쓰기 연산을 섞으면 TinkerGraph 가 비결정적 결과를 주므로 benchmark 가 노이즈만 됨.

### Steps

- [ ] **`ConcurrencyBenchmark`**

   File: `/Users/debop/work/bluetape4k/bluetape4k-graph/graph/graph-benchmark/src/benchmark/kotlin/io/bluetape4k/graph/benchmark/ConcurrencyBenchmark.kt`

   ```kotlin
   package io.bluetape4k.graph.benchmark

   import io.bluetape4k.graph.benchmark.setup.BenchmarkGraphFixture
   import io.bluetape4k.graph.model.GraphVertex
   import kotlinx.benchmark.Benchmark
   import kotlinx.benchmark.Blackhole
   import kotlinx.coroutines.async
   import kotlinx.coroutines.awaitAll
   import kotlinx.coroutines.runBlocking
   import java.util.concurrent.CompletableFuture
   import java.util.concurrent.Executors

   /**
    * 100-way 병렬 **읽기 전용** 조회 벤치마크.
    *
    * 스레드 안전성 제약:
    * - [io.bluetape4k.graph.tinkerpop.TinkerGraphOperations] 는 단일 `TinkerGraph` 인스턴스를
    *   공유한다. 쓰기 연산을 병렬로 섞으면 비결정적 결과가 발생하므로 이 벤치마크는
    *   `findVertexById` 같은 읽기 작업만 측정한다.
    * - 자세한 내용은 `docs/superpowers/specs/2026-04-17-virtual-threads-expansion-and-benchmark-design.md`
    *   §5.5 참조.
    */
   open class ConcurrencyBenchmark: BenchmarkGraphFixture() {

       private val first100 by lazy { vertexIds.take(100) }

       // M2: pool 은 @Setup 에서 미리 생성 — 메서드 내 생성 시 스레드 생성 비용이
       // 측정값을 오염시킴.
       private lateinit var syncPool: java.util.concurrent.ExecutorService

       @Setup
       override fun setup() {
           super.setup()
           syncPool = Executors.newFixedThreadPool(16)
       }

       @TearDown
       override fun tearDown() {
           syncPool.shutdownNow()
           super.tearDown()
       }

       @Benchmark
       fun syncParallel(bh: Blackhole) {
           val futures = first100.map { id ->
               syncPool.submit<GraphVertex?> { ops.findVertexById("Node", id) }
           }
           futures.forEach { bh.consume(it.get()) }
       }

       @Benchmark
       fun vtParallel(bh: Blackhole) {
           val futures = first100.map { id -> vtOps.findVertexByIdAsync("Node", id) }
           CompletableFuture.allOf(*futures.toTypedArray()).join()
           futures.forEach { bh.consume(it.get()) }
       }

       @Benchmark
       fun suspendParallel(bh: Blackhole) = runBlocking {
           val results = first100.map { id ->
               async { suspendOps.findVertexById("Node", id) }
           }.awaitAll()
           results.forEach { bh.consume(it) }
       }
   }
   ```

- [ ] **Compile check**

   ```bash
   ./gradlew :graph-benchmark:compileBenchmarkKotlin
   ```

- [ ] **Commit**: `feat(graph-benchmark): add read-only ConcurrencyBenchmark`

---

## Task 11 — Run benchmarks + update `docs/graphdb-tradeoffs.md`

**Complexity**: low

### Steps

- [ ] **Run full benchmark**

   ```bash
   ./gradlew :graph-benchmark:benchmark
   ```

   Expected: `BUILD SUCCESSFUL`. Report JSON appears under
   `graph/graph-benchmark/build/reports/benchmarks/main/*/main.json`.

- [ ] **Extract summary** — open the JSON file in a viewer (`jg`/`jless`); collect median throughput per benchmark method.

- [ ] **Update `docs/graphdb-tradeoffs.md`** — add new section 9 using the collected numbers. Template per spec §5.6:

   ```markdown
   ## 9. API 선택 가이드 (Benchmark 결과)

   측정 환경: JDK 25, TinkerGraph 인메모리, N=1000 vertices, ~4000 edges.

   | 시나리오 | Sync | Virtual Thread | Coroutines | 승자 |
   |----------|------|----------------|------------|------|
   | 단일 CRUD 왕복 | ... ops/s | ... ops/s | ... ops/s | ... |
   | neighbors (depth=1) | ... | ... | ... | ... |
   | shortestPath (maxDepth=4) | ... | ... | ... | ... |
   | pageRank (topK=100) | ... μs/op | ... μs/op | ... μs/op | ... |
   | bfs (maxDepth=3) | ... | ... | ... | ... |
   | 100-way 병렬 read | ... ops/s | ... ops/s | ... ops/s | ... |

   권장:
   - Kotlin 코드 → Coroutines (`GraphSuspendOperations`)
   - Java 코드 → Virtual Thread (`GraphVirtualThreadOperations`)
   - 단일 스레드 스크립트 → Sync (`GraphOperations`)
   ```

- [ ] **Commit**: `docs: add API selection guide with benchmark results`

---

## Task 12 — README / TODO / testlog sync

**Complexity**: low

### Steps

- [ ] **Update `graph/graph-core/README.md`** — Virtual Threads API 섹션 추가:
   `GraphVirtualThreadOperations` facade + `GraphOperations.asVirtualThread()` 확장 함수 사용 예제 포함.

- [ ] **Update `graph/graph-core/README.ko.md`** — 동일 내용 한국어로 작성.

- [ ] **Create `graph/graph-benchmark/README.md`** — 신규 모듈 설명:
   - 목적 (3종 API 성능 비교), 실행 방법 (`./gradlew :graph-benchmark:benchmark`), 결과 위치.

- [ ] **Create `graph/graph-benchmark/README.ko.md`** — 동일 내용 한국어로 작성.

- [ ] **Update `TODO.md`** — mark VT expansion and benchmark items as done.

- [ ] **Update `docs/superpowers/index/2026-04.md`** — 항목 추가:
   ```markdown
   | 2026-04-17 | Virtual Threads Full Expansion & graph-benchmark | `graph-core` VT 인터페이스+어댑터 5종, `graph-benchmark` 신규 모듈 |
   ```

- [ ] **Update `docs/superpowers/INDEX.md`** — 완료 건수 +1.

- [ ] **Append testlog entry** — `wiki/testlogs/2026-04.md` in the Obsidian vault:

   ```markdown
   ## 2026-04-17 — VT expansion & benchmark

   - `./gradlew :graph-core:test` ✅ (6 new VT adapter tests passed)
   - `./gradlew :graph-benchmark:benchmark` ✅ (JSON report written)
   - `docs/graphdb-tradeoffs.md` §9 updated with real measurements
   ```

- [ ] **Commit**: `docs: README/README.ko/TODO sync + testlog entry for VT expansion`

---

## Done Criteria Checklist

- [ ] `./gradlew :graph-core:test` passes with 6 new VT adapter test classes
- [ ] `./gradlew :graph-benchmark:benchmark` exits 0 and writes JSON report
- [ ] `docs/graphdb-tradeoffs.md` §9 contains real benchmark numbers
- [ ] `README.md` + `README.ko.md` mention VT facade + benchmark module
- [ ] `TODO.md` VT/benchmark items checked
- [ ] `./gradlew build -x test` succeeds with new module included
- [ ] Existing `GraphAlgorithmRepository.asVirtualThread()` callers unaffected (verified via `Grep` for any usage before commit)
- [ ] `graph-benchmark` is excluded from Maven Central aggregation

---

## Risk Log

1. **Circular dependency** — `graph-core` testImplementation → `graph-tinkerpop` → `graph-core` api. Gradle usually tolerates test-only reverse dep; if it fails, move the 6 test files to `graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/vt/`.
2. **`kotlin("plugin.allopen")` version** — if not inherited from root plugin convention, add `version Plugins.Versions.kotlin` explicitly.
3. **`kotlinx-benchmark` imports** — ensure `@State` / `@Benchmark` / etc. are imported from `kotlinx.benchmark.*`, never `org.openjdk.jmh.annotations.*`. Only the `allOpen` annotation FQN uses the JMH name (correct per spec §5.3).
4. **`suspendOps` suspend API 시그니처** — `TinkerGraphSuspendOperations` 확인 결과 (H3):
   - `pageRank(options): Flow<PageRankScore>` → 벤치마크에서 `runBlocking { suspendOps.pageRank(opts).toList() }`
   - `bfs(startId, options): Flow<TraversalVisit>` → 동일하게 `.toList()` 수집
   - `neighbors/shortestPath/allPaths` 는 `suspend` + `List`/`GraphPath?` 반환 → `runBlocking { suspendOps.neighbors(...) }` 직접 사용
   - Task 9 구현 시 위 시그니처를 그대로 적용; 탐색 없이 확정.
5. **H2 overload 검증** — `VirtualThreadOperationsExtTest.TinkerGraphOperations asVirtualThread resolves to facade without cast` 가 컴파일 실패하면 `GraphAlgorithmRepository.asVirtualThread()` 를 `@Deprecated` 처리하거나 더 구체적인 receiver 로 교체. 플랜 실행 전에 확인 필요.
