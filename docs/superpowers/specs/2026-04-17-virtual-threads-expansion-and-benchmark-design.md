# Virtual Threads Full Expansion & graph-benchmark Module Design Spec

- **Date**: 2026-04-17
- **Status**: Draft
- **Modules**: `graph/graph-core` (interfaces + adapters), `graph/graph-benchmark` (신규)
- **Author**: bluetape4k-graph
- **Related**: `2026-04-16-graph-algorithm-extension-design.md` (VT 3번째 API 패턴의 원형)

---

## 1. Goals

본 스펙은 두 개의 독립된 기능을 하나의 일관된 작업 단위로 기술한다.

### Feature 1 — Virtual Threads Full Expansion

현재 `GraphAlgorithmRepository` 만 Virtual Thread(VT) 어댑터를 가지고 있다.
`GraphOperations` 의 **전체 표면**(Session / Vertex / Edge / Traversal / Algorithm)을
VT 기반 `CompletableFuture<T>` API로 노출하여 Java interop 및 CF 파이프라인 소비자가
끊김 없이 사용할 수 있도록 한다.

**Why**
- Java 25 Project Loom 위에서 동기 I/O 를 값싸게 펼칠 수 있어야 Kotlin 코루틴을 쓰지 않는
  Java 소비자에게도 graph 라이브러리가 유용해진다.
- 알고리즘만 VT 지원이면 Vertex CRUD → Algorithm 파이프라인이 반토막 나서 실용성 낮다.
- 기존 `asVirtualThread()` 확장 함수의 receiver 가 `GraphAlgorithmRepository` 이므로
  `GraphOperations` 수신자에는 **더 구체적인 오버로드**가 필요하다.

### Feature 2 — graph-benchmark Module

`Sync` vs `Virtual Thread` vs `Coroutines` 3종 API 의 **성능 특성**을 수치로 비교할 수
있는 `kotlinx-benchmark` 기반 모듈을 추가한다. 결과는 `docs/graphdb-tradeoffs.md` 에
표 형태로 갱신하여 사용자의 API 선택 근거로 삼는다.

**Why**
- 3종 API 를 동시 제공하는 현재 구조에서 "언제 무엇을 써야 하나" 가이드가 필요하다.
- Docker 없이 재현 가능한 벤치마크 기반(TinkerGraph 인메모리)이 있어야 CI/로컬 모두에서 돌 수 있다.

---

## 2. Non-Goals

- **Neo4j / AGE / Memgraph 백엔드의 VT 전용 최적화** — 어댑터 방식만 제공, 백엔드별
  커넥션 풀 튜닝은 별도 작업.
- **JDK Project Loom 성능 측정을 위한 마이크로벤치마크** — kotlinx-benchmark 레벨에서
  "실사용 패턴" 측정만 수행.
- **새로운 그래프 알고리즘 추가** — 본 스펙은 API 펼침과 계측만 다룬다.
- **Suspend ↔ Virtual Thread 상호 어댑터** — 양방향 변환은 불필요. 두 API 는 병렬 공존.
- **`graph-benchmark` 를 Maven Central 에 배포** — examples 처럼 배포 제외.

---

## 3. Current State

### 3.1 기존 VT 패턴 (알고리즘만)

```
graph-core/
  repository/
    GraphVirtualThreadAlgorithmRepository.kt   ← 인터페이스
  algo/
    VirtualThreadAlgorithmAdapter.kt           ← 구현 + asVirtualThread() 확장
```

- 인터페이스는 모든 메서드가 `*Async` 접미사 + `CompletableFuture<T>` 반환.
- 어댑터는 `CompletableFuture.supplyAsync({ delegate.xxx(...) }, executor)` 한 줄 위임.
- 기본 executor: `Executors.newVirtualThreadPerTaskExecutor()` (어댑터마다 주입 가능).
- 확장: `fun GraphAlgorithmRepository.asVirtualThread(executor): GraphVirtualThreadAlgorithmRepository`.

> **신규 어댑터의 구현 패턴 변경**: Task 1–5 의 신규 어댑터는 `bluetape4k-virtualthread-api` 의
> `StructuredTaskScopes.withAll` 을 사용하여 Virtual Thread 실행을 구조화한다.
> 명시적 `ExecutorService` 파라미터를 제거하고, `withAll { scope -> scope.fork { ... }; scope.join().throwIfFailed() }` 패턴으로 통일한다.
> 기존 `VirtualThreadAlgorithmAdapter` (`algo/`) 도 동일하게 업데이트한다.

### 3.2 벤치마크 인프라

- `buildSrc/src/main/kotlin/Libs.kt` (439–444): `kotlinxBenchmark(...)`, `kotlinx_benchmark_runtime`.
- `Plugins.kotlinx_benchmark` (version 0.4.15) 기 등록됨.
- `graph-tinkerpop` 은 인메모리이므로 Docker/Testcontainers 없이 벤치마크 가능.

---

## 4. Design — Feature 1 (VT Expansion)

### 4.1 패키지 배치

```
graph-core/src/main/kotlin/io/bluetape4k/graph/
  repository/
    GraphVirtualThreadSession.kt          (신규)
    GraphVirtualThreadVertexRepository.kt (신규)
    GraphVirtualThreadEdgeRepository.kt   (신규)
    GraphVirtualThreadTraversalRepository.kt (신규)
    GraphVirtualThreadAlgorithmRepository.kt (기존, 유지)
    GraphVirtualThreadOperations.kt       (신규 facade)
  vt/                                      (신규 패키지)
    VirtualThreadSessionAdapter.kt
    VirtualThreadVertexAdapter.kt
    VirtualThreadEdgeAdapter.kt
    VirtualThreadTraversalAdapter.kt
    VirtualThreadOperationsAdapter.kt     (4종 composition)
    VirtualThreadOperationsExt.kt         (확장 함수)
```

**배치 근거**
- 인터페이스는 기존 관례대로 `repository/` 아래 평평하게 둔다 (Suspend 와 동일한 위치).
- 어댑터는 새 `vt/` 패키지에 모은다 — 기존 `algo/` 는 알고리즘 전용이므로 혼합 금지.
- `VirtualThreadAlgorithmAdapter` 는 **현 위치(`algo/`) 유지** — 기존 public API 보존.
  단, `vt/` 에서 re-export 하지 않고 그대로 import 해서 composition 에 사용한다.

### 4.2 인터페이스 명명 규칙 (MUST)

기존 알고리즘 패턴과 **완전 일치**:

| 측면 | 규칙 | 예시 |
|------|------|------|
| 인터페이스 접두어 | `GraphVirtualThread` | `GraphVirtualThreadVertexRepository` |
| 메서드 접미어 | `Async` | `createVertexAsync`, `neighborsAsync` |
| 반환 타입 | `CompletableFuture<T>` | `CompletableFuture<GraphVertex>` |
| nullable 결과 | `CompletableFuture<T?>` (타입 파라미터 nullable) | `findVertexByIdAsync(...): CompletableFuture<GraphVertex?>` |
| `Unit` 결과 | `CompletableFuture<Void>` (Java interop 배려) | `createGraphAsync(...): CompletableFuture<Void>` |
| 기본값 전파 | 동기 인터페이스와 동일한 `= ... .Default` 기본값 유지 | — |
| KDoc | 한국어, 기존 `GraphVirtualThreadAlgorithmRepository` 톤에 맞춤 | — |

**예외**: `Boolean` 반환(`deleteVertex`, `deleteEdge`, `graphExists`)은 박싱된
`CompletableFuture<Boolean>` 으로 그대로 둔다 — Java 소비자가 `.join()` 으로 받아서
`boolean` 자동언박싱이 가능하다.

### 4.3 Facade 합성

```kotlin
interface GraphVirtualThreadOperations :
    GraphSession,                            // sync lifecycle (close 등) — 아래 설계 근거 참조
    GraphVirtualThreadSession,
    GraphVirtualThreadVertexRepository,
    GraphVirtualThreadEdgeRepository,
    GraphVirtualThreadTraversalRepository,
    GraphVirtualThreadAlgorithmRepository
    // AutoCloseable 은 GraphSession 이 이미 extends 하므로 중복 제거
```

**설계 근거 — `GraphSession` 동기 상속 유지**
- `close()` 는 VT 로 돌릴 필요가 없다 (호출자가 close 시점을 직접 관리).
- `createGraph/dropGraph/graphExists` 의 VT 변형(`*Async`)은 `GraphVirtualThreadSession`
  에 별도로 제공.
- 결과적으로 facade 는 **동기 lifecycle + 전체 비동기 기능** 의 합집합.

### 4.4 어댑터 개별 메서드 구현 패턴

신규 어댑터는 `bluetape4k-virtualthread-api` 의 `StructuredTaskScopes.withAll` 을 사용한다.
명시적 `ExecutorService` 파라미터 없이 `withAll` 내부가 Virtual Thread를 생성한다.

```kotlin
import io.bluetape4k.concurrent.virtualthread.StructuredTaskScopes

// 값 반환 메서드 (예: createVertex)
override fun createVertexAsync(
    label: String,
    properties: Map<String, Any?> = emptyMap(),
): CompletableFuture<GraphVertex> =
    CompletableFuture.supplyAsync {
        StructuredTaskScopes.withAll { scope ->
            val subtask = scope.fork { delegate.createVertex(label, properties) }
            scope.join().throwIfFailed()
            subtask.get()
        }
    }

// Void 메서드 (Unit 반환, Java interop — CompletableFuture<Void>)
override fun createGraphAsync(name: String): CompletableFuture<Void> =
    CompletableFuture.runAsync {
        StructuredTaskScopes.withAll { scope ->
            scope.fork { delegate.createGraph(name) }
            scope.join().throwIfFailed()
        }
    }
```

**패턴 핵심**
- `StructuredTaskScopes.withAll { scope -> ... }` 는 기본 `Thread.ofVirtual().factory()` 로 Virtual Thread 를 생성한다.
- `scope.fork { }` 로 단일 태스크를 Virtual Thread 위에서 실행, `scope.join().throwIfFailed()` 로 완료를 보장한다.
- 어댑터 생성자에 `ExecutorService` 파라미터 없음 — `withAll` 이 스레드 생성을 전담한다.
- 기존 `VirtualThreadAlgorithmAdapter` 도 동일하게 변경한다.

### 4.5 Facade composition

```kotlin
class VirtualThreadOperationsAdapter(
    private val delegate: GraphOperations,
) : GraphVirtualThreadOperations,
    GraphSession by delegate,                                           // sync passthrough
    GraphVirtualThreadSession by VirtualThreadSessionAdapter(delegate),
    GraphVirtualThreadVertexRepository by VirtualThreadVertexAdapter(delegate),
    GraphVirtualThreadEdgeRepository by VirtualThreadEdgeAdapter(delegate),
    GraphVirtualThreadTraversalRepository by VirtualThreadTraversalAdapter(delegate),
    GraphVirtualThreadAlgorithmRepository by VirtualThreadAlgorithmAdapter(delegate) {

    companion object : KLogging()

    override fun close() {
        // withAll 기반으로 executor 없음 — 소유권 원칙상 delegate 도 닫지 않는다.
    }
}
```

**핵심 결정**
- **Kotlin 위임(by)** 으로 5개 어댑터를 합성 → 보일러플레이트 최소.
- **executor 파라미터 제거** — `withAll` 이 Virtual Thread 를 내부에서 관리.
- `delegate` 가 `GraphOperations` 이므로 개별 리포지토리 타입으로 업캐스트 되어 각
  adapter 생성자에 그대로 주입 가능.
- `close()` 는 no-op — executor 없고 delegate 소유권 없음.

### 4.5 확장 함수 (receiver resolution)

```kotlin
// 1) 기존 (변경: executor 제거, withAll 패턴으로 통일)
fun GraphAlgorithmRepository.asVirtualThread(): GraphVirtualThreadAlgorithmRepository =
    VirtualThreadAlgorithmAdapter(this)

// 2) 신규 — 더 구체적인 receiver
fun GraphOperations.asVirtualThread(): GraphVirtualThreadOperations =
    VirtualThreadOperationsAdapter(this)
```

**Kotlin receiver resolution 규칙**
- `GraphOperations` 는 `GraphGenericRepository` 를 통해 `GraphAlgorithmRepository` 의
  서브타입이다 (`GraphOperations → GraphGenericRepository → GraphAlgorithmRepository`).
  따라서 두 확장 함수 모두 적용 가능한 상황이 생긴다.
- Kotlin 은 **더 구체적인 receiver 를 가진 확장** 을 선호하므로
  `GraphOperations.asVirtualThread()` 가 우선 선택된다 → `GraphVirtualThreadOperations`
  반환.
- `GraphAlgorithmRepository.asVirtualThread()` 는 알고리즘만 필요할 때 명시적으로 쓰인다
  (예: `(ops as GraphAlgorithmRepository).asVirtualThread()`).

### 4.6 테스트 전략

**위치**: `graph-core/src/test/kotlin/io/bluetape4k/graph/vt/`

**테스트 클래스**
| 클래스 | 검증 대상 |
|--------|-----------|
| `VirtualThreadVertexAdapterTest` | 각 메서드가 delegate 를 **정확히 1회 호출** + CF 결과 일치, `StructuredTaskScopeTester` 동시성 검증 |
| `VirtualThreadEdgeAdapterTest` | 동일 |
| `VirtualThreadTraversalAdapterTest` | 동일 |
| `VirtualThreadSessionAdapterTest` | `CompletableFuture<Void>` 반환 계약 검증 |
| `VirtualThreadOperationsAdapterTest` | facade 전체 wiring, `close()` no-op |
| `VirtualThreadOperationsExtTest` | 확장 함수 overload 선택 (`GraphOperations` → facade) |

**테스트 기반**
- `TinkerGraphOperations` 를 실제 delegate 로 사용 (graph-tinkerpop 가 테스트 의존성).
- `kotlin.test` + `kluent` (기존 관례) + `bluetape4k-junit5`.
- **동시성 스트레스 테스트** — `StructuredTaskScopeTester` (`io.bluetape4k.junit5.concurrency`):

```kotlin
@Test
fun `createVertexAsync is thread-safe under concurrent load`() {
    StructuredTaskScopeTester()
        .rounds(100)
        .add {
            val v = adapter.createVertexAsync("Node", mapOf("idx" to 1)).join()
            v.shouldNotBeNull()
        }
        .run()
}
```

- **코루틴 동시성 테스트** — `SuspendedJobTester` (`io.bluetape4k.junit5.coroutines`):

```kotlin
@Test
fun `suspendOps is safe under concurrent coroutines`() {
    SuspendedJobTester()
        .numJobs(100)
        .add { suspendOps.findVertexById("Node", id).shouldNotBeNull() }
        .run()
}
```

**Assertion 포인트**
1. `result == future.join()` — 반환값 동일성.
2. Mockk spy 로 delegate 메서드 호출 카운트 == 1.
3. `close()` 후 **delegate 는 여전히 사용 가능** (소유권 보존).
4. `GraphOperations.asVirtualThread()` 반환 타입이 `GraphVirtualThreadOperations` 임을 컴파일타임에 보장.
5. **`GraphSession` passthrough 검증** — `vtOps.createGraph("x")` 등 동기 메서드가 delegate 에 직접 위임되는지 spy 로 확인.
6. **`StructuredTaskScopeTester` 동시성 검증** — 100 rounds × adapter 메서드 호출, 예외 없음.

---

## 5. Design — Feature 2 (Benchmark)

### 5.1 모듈 구조

```
graph/graph-benchmark/
  build.gradle.kts
  src/
    benchmark/kotlin/io/bluetape4k/graph/benchmark/
      setup/
        BenchmarkGraphFixture.kt       (정점 N개 + 랜덤 간선 생성)
      VertexCrudBenchmark.kt
      TraversalBenchmark.kt
      AlgorithmBenchmark.kt
      ConcurrencyBenchmark.kt
```

**배치 원칙**
- Maven Central 배포 제외 (examples 와 동일하게 `settings.gradle.kts` 에만 include).
- `src/benchmark/kotlin` source set — `kotlinx-benchmark` gradle plugin 관례.
- 테스트 모듈은 아니므로 `src/test` 는 두지 않는다. 벤치마크 자체가 검증.

### 5.2 Gradle 설정 스케치

```kotlin
// graph/graph-benchmark/build.gradle.kts
plugins {
    kotlin("jvm")
    id(Plugins.kotlinx_benchmark)
    kotlin("plugin.allopen") version "..." // @State 클래스 상속 허용
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

dependencies {
    implementation(project(":graph-core"))
    implementation(project(":graph-tinkerpop"))
    implementation(Libs.kotlinx_benchmark_runtime)
    implementation(Libs.kotlinx_coroutines_core)
}

benchmark {
    targets { register("benchmark") }
    configurations {
        named("main") {
            warmups = 3
            iterations = 5
            iterationTime = 1
            iterationTimeUnit = "sec"
            mode = "thrpt"          // throughput (ops/sec)
            outputTimeUnit = "sec"
            reportFormat = "json"
        }
    }
}
```

- `graph-benchmark` 는 Maven Central 에서 **제외** — `build.gradle.kts` 루트 aggregate 설정에 추가.

### 5.3 공통 픽스처

모든 import 는 `kotlinx.benchmark.*` 에서 가져온다 (`org.openjdk.jmh.annotations.*` 혼용 금지).
`allOpen` annotation target 은 `"org.openjdk.jmh.annotations.State"` 로 지정 (kotlinx-benchmark
내부 JMH wrapper 가 이 annotation 을 상속함).

```kotlin
import kotlinx.benchmark.*
import kotlinx.benchmark.State
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.TearDown
import kotlinx.benchmark.Param
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Blackhole

@State(Scope.Benchmark)
open class BenchmarkGraphFixture {
    lateinit var ops: TinkerGraphOperations
    lateinit var vtOps: GraphVirtualThreadOperations
    lateinit var suspendOps: TinkerGraphSuspendOperations  // asSuspend() 없음 — 직접 생성
    lateinit var vertexIds: List<GraphElementId>

    @Param("1000")
    var vertexCount: Int = 0  // 10000 은 -Pbenchmark.large=true 프로파일로 활성화

    @Setup
    fun setup() {
        ops = TinkerGraphOperations()
        vtOps = ops.asVirtualThread()
        suspendOps = TinkerGraphSuspendOperations(ops)   // asSuspend() 확장 없음
        vertexIds = (1..vertexCount).map {
            ops.createVertex("Node", mapOf("idx" to it)).id
        }
        // 평균 차수 ~= 4 인 랜덤 간선
        val rnd = Random(42)
        repeat(vertexCount * 4) {
            ops.createEdge(vertexIds.random(rnd), vertexIds.random(rnd), "REL")
        }
    }

    @TearDown
    fun tearDown() { ops.close() }
}
```

### 5.4 벤치마크 클래스별 설계

| 클래스 | 측정 대상 | 메서드 (각 API 당 1개) |
|--------|----------|------------------------|
| `VertexCrudBenchmark` | 단일 vertex CRUD 왕복 지연 | `syncCreateFind`, `vtCreateFind`, `suspendCreateFind` |
| `TraversalBenchmark` | `neighbors`, `shortestPath` | `syncNeighbors`, `vtNeighbors`, `suspendNeighbors`, `syncShortestPath`, `vtShortestPath`, `suspendShortestPath` |
| `AlgorithmBenchmark` | `pageRank(topK=100)`, `bfs(maxDepth=3)` | `syncPageRank`, `vtPageRank`, `suspendPageRank`, `syncBfs`, `vtBfs`, `suspendBfs` |
| `ConcurrencyBenchmark` | 100-way 병렬 `findVertexById` | `syncParallel` (Java 21 Thread), `vtParallel`, `suspendParallel` (coroutines) |

**측정 모드**
- CRUD / Traversal: `Mode.Throughput` (ops/sec).
- Algorithm: `Mode.AverageTime` (μs/op).
- Concurrency: `Mode.Throughput` + `@Threads(1)` (자체 내부에서 100 병렬 분기).

### 5.5 동시성 벤치마크 상세 (안전성 제약)

```kotlin
@Benchmark
fun vtParallel(bh: Blackhole) {
    val futures = vertexIds.take(100).map { id ->
        vtOps.findVertexByIdAsync("Node", id)
    }
    CompletableFuture.allOf(*futures.toTypedArray()).join()
    futures.forEach { bh.consume(it.get()) }
}

@Benchmark
fun suspendParallel(bh: Blackhole) = runBlocking {
    val results = vertexIds.take(100).map { id ->
        async { suspendOps.findVertexById("Node", id) }
    }.awaitAll()
    results.forEach { bh.consume(it) }
}

@Benchmark
fun syncParallel(bh: Blackhole) {
    // Virtual thread per task executor 가 아닌 platform thread pool 로
    // 공정한 "전통적 sync multithread" 비교.
    val pool = Executors.newFixedThreadPool(16)
    try {
        val futures = vertexIds.take(100).map { id ->
            pool.submit<GraphVertex?> { ops.findVertexById("Node", id) }
        }
        futures.forEach { bh.consume(it.get()) }
    } finally { pool.shutdownNow() }
}
```

**스레드 안전성 제약 — 문서화 필수**
- `TinkerGraphOperations` 는 단일 `TinkerGraph` 인스턴스를 공유 → **읽기 전용** 병렬만 허용.
- 쓰기 동시성 테스트는 **스펙 범위 외** (TinkerGraph 가 thread-safe 하지 않으므로 노이즈만 만듦).
- `ConcurrencyBenchmark` 는 `findVertexById` 같은 읽기 작업만 측정.

### 5.6 결과 문서화

**갱신 대상**: `docs/graphdb-tradeoffs.md`

**추가 섹션 템플릿**
```
## 9. API 선택 가이드 (Benchmark 결과)

측정 환경: JDK 25, TinkerGraph 인메모리, N=10000 vertices, 40000 edges.

| 시나리오 | Sync | Virtual Thread | Coroutines | 승자 |
|----------|------|----------------|------------|------|
| 단일 CRUD 왕복 | X ops/s | Y ops/s | Z ops/s | ... |
| neighbors (depth=1) | ... |
| pageRank (topK=100) | ... |
| 100-way 병렬 read | ... |

권장:
- Kotlin 코드 → Coroutines (`GraphSuspendOperations`)
- Java 코드 → Virtual Thread (`GraphVirtualThreadOperations`)
- 단일 스레드 스크립트 → Sync (`GraphOperations`)
```

- 생성 JSON 리포트(`build/reports/benchmarks/main/*/jmh-result.json`)에서 값만 복사.
- **수치는 Draft 상태로 넣지 않고** 실제 측정 후 PR 에서 최종값 기입.

---

## 6. API Reference

### 6.1 `GraphVirtualThreadSession`

```kotlin
interface GraphVirtualThreadSession {
    fun createGraphAsync(name: String): CompletableFuture<Void>
    fun dropGraphAsync(name: String): CompletableFuture<Void>
    fun graphExistsAsync(name: String): CompletableFuture<Boolean>
}
```

### 6.2 `GraphVirtualThreadVertexRepository`

```kotlin
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

### 6.3 `GraphVirtualThreadEdgeRepository`

```kotlin
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

### 6.4 `GraphVirtualThreadTraversalRepository`

```kotlin
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

### 6.5 `GraphVirtualThreadOperations` (facade)

```kotlin
interface GraphVirtualThreadOperations :
    GraphSession,                         // AutoCloseable 은 GraphSession 이 이미 포함
    GraphVirtualThreadSession,
    GraphVirtualThreadVertexRepository,
    GraphVirtualThreadEdgeRepository,
    GraphVirtualThreadTraversalRepository,
    GraphVirtualThreadAlgorithmRepository
```

### 6.6 확장 함수

```kotlin
fun GraphOperations.asVirtualThread(
    executor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor(),
): GraphVirtualThreadOperations
```

---

## 7. Testing Strategy

### 7.1 Feature 1 (VT adapters)

| 테스트 클래스 | 위치 | 검증 |
|---------------|------|------|
| `VirtualThreadSessionAdapterTest` | `graph-core/src/test/.../vt/` | CF<Void> 정상/예외 전파, delegate 1회 호출 |
| `VirtualThreadVertexAdapterTest` | 동일 | 6개 메서드 각각 wiring, CF 결과 == sync 결과 |
| `VirtualThreadEdgeAdapterTest` | 동일 | 3개 메서드 |
| `VirtualThreadTraversalAdapterTest` | 동일 | 3개 메서드, `null` path 반환 계약 |
| `VirtualThreadOperationsAdapterTest` | 동일 | 5개 인터페이스 전체 접근 + `close()` executor shutdown |
| `VirtualThreadOperationsExtTest` | 동일 | `GraphOperations.asVirtualThread()` overload resolution |

**검증 수단**
- 실제 `TinkerGraphOperations` + `Executors.newSingleThreadExecutor()` 주입 → 결정성.
- `spyk(ops)` 로 delegate 호출 카운트 검증.
- 예외 전파: delegate 가 throw → `CompletionException` 포장되어 전달되는지 확인.

### 7.2 Feature 2 (Benchmark)

- **자체 테스트 없음** — `@Setup` 실행 성공 + `./gradlew :graph-benchmark:benchmark` 종료 코드 0 을 검증으로 삼는다.
- CI 에 벤치마크를 넣지 않는다 (시간 초과). 로컬에서만 실행 후 결과를 `docs/graphdb-tradeoffs.md` 에 반영.

---

## 8. Dependencies

**신규 외부 의존성**: 없음.

| 필요 | 이미 존재 |
|------|-----------|
| `kotlinx-benchmark-runtime` | `Libs.kotlinx_benchmark_runtime` (v0.4.15) ✅ |
| `kotlinx-benchmark` gradle plugin | `Plugins.kotlinx_benchmark` ✅ |
| VT executor | `java.util.concurrent.Executors` (JDK 25) ✅ |
| `CompletableFuture` | JDK ✅ |
| TinkerGraph delegate | `graph-tinkerpop` module ✅ |

---

## 9. Open Questions

1. **`createGraphAsync` 의 `CompletableFuture<Void>` vs `CompletableFuture<Unit>`**
   - **결정**: `CompletableFuture<Void>` — VT 어댑터 자체가 Java interop 목적이고 `null` 로 완료 가능.
     Java 소비자 친화적이며 `.thenRun {}` 체이닝이 자연스러움. 재논의 없이 구현에 반영한다.

2. **`VirtualThreadOperationsAdapter.close()` 가 delegate 에 대해 전파해야 하는가?**
   - 현 설계: **전파 안 함** (주입 소유권 원칙 — `GraphSession` 문서 명시).
   - 예외: 어댑터가 delegate 를 **자체 생성** 하는 오버로드(`asVirtualThread()` 가
     내부적으로 TinkerGraph 생성)가 있다면 그때만 전파 — 본 스펙에서는 그런 오버로드를
     만들지 않는다.

3. **벤치마크에서 JSON output 외 HTML report 도 생성할 것인가?**
   - `reportFormat` 은 plugin 이 `json`, `text`, `csv`, `scsv` 지원. HTML 은 없음.
   - **잠정 결론**: JSON + CSV — 문서화 시 사람이 읽는 건 CSV 를 엑셀로, 기계가 읽는 건 JSON.

4. **`VertexCrudBenchmark` 에 `@Param` 으로 `vertexCount` 를 2개 이상 두면 벤치마크 시간이 길어진다. 로컬 기본값은?**
   - **잠정 결론**: `@Param("1000")` 만 둠. 10000 은 별도 프로파일 플래그(`-Pbenchmark.large=true`)로 활성화.

5. **`GraphSuspendOperations.asVirtualThread()` 확장을 제공할 것인가?**
   - 현 설계: **제공 안 함**. Suspend → VT 는 불필요한 변환 (Kotlin 쪽은 이미 VT 필요 없음).

---

## 10. Success Criteria

- `./gradlew :graph-core:test` 가 신규 6개 테스트 클래스 포함해서 모두 통과.
- `./gradlew :graph-benchmark:benchmark` 가 로컬에서 정상 실행 (종료 코드 0).
- `docs/graphdb-tradeoffs.md` 에 실측 기반 API 선택 가이드 섹션이 추가됨.
- 기존 `GraphAlgorithmRepository.asVirtualThread()` 사용처(있다면)가 깨지지 않음.
- `./gradlew build -x test` 가 신규 모듈 포함 성공.
