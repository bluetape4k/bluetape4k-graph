# graph-io-core

[English](README.md) | 한국어

`graph-io` 계열의 벌크 임포터/익스포터가 공유하는 계약(contract), 모델, 옵션, 리포트, I/O 헬퍼.

## 개요

`graph-io-core`는 모든 `graph-io-*` 포맷 모듈(CSV, Jackson2 NDJSON, Jackson3 NDJSON, GraphML)이 의존하는 추상 인터페이스와 데이터 타입을 정의합니다. **포맷/백엔드에 종속적인 코드는 전혀 포함하지 않으며**, 동기 · Kotlin 코루틴 `suspend` · Java Virtual Thread 기반 `CompletableFuture` 세 가지 실행 모델 전반에서 동일한 계약을 구현할 수 있게 하는 것이 유일한 역할입니다.

이 모듈은 보통 직접 사용하지 않습니다. 애플리케이션은 포맷 모듈(`graph-io-csv`, `graph-io-jackson3` 등) 중 하나에 의존하며, 해당 모듈이 이 타입들을 전이적으로 노출합니다.

## 아키텍처

![graph-io-core architecture diagram](../../docs/images/readme-diagrams/graph-io-core-architecture-01.png)

## 포함 내용

### 실행 모델 계약 (`io.bluetape4k.graph.io.contract`)

총 7개 인터페이스 — 실행 모델별 익스포터 1개 + 임포터 1개 + Flow 기반 raw 리더 1개:

| 인터페이스 | 메서드 | 반환 타입 |
|-----------|--------|---------|
| `GraphBulkExporter<T>` | `exportGraph(sink, ops, options)` | `GraphExportReport` |
| `GraphBulkImporter<S>` | `importGraph(source, ops, options)` | `GraphImportReport` |
| `GraphSuspendBulkExporter<T>` | `suspend exportGraphSuspending(sink, suspendOps, options)` | `GraphExportReport` |
| `GraphSuspendBulkImporter<S>` | `suspend importGraphSuspending(source, suspendOps, options)` | `GraphImportReport` |
| `GraphVirtualThreadBulkExporter<T>` | `exportGraphAsync(sink, ops, options)` | `CompletableFuture<GraphExportReport>` |
| `GraphVirtualThreadBulkImporter<S>` | `importGraphAsync(source, ops, options)` | `CompletableFuture<GraphImportReport>` |
| `GraphRecordFlowReader<S>` | `readVertices(source)` / `readEdges(source)` | `Flow<GraphIoVertexRecord>` / `Flow<GraphIoEdgeRecord>` |

`S`는 포맷별 소스 타입(`GraphImportSource`, `CsvGraphImportSource` 등), `T`는 싱크 타입(`GraphExportSink`, `CsvGraphExportSink` 등)입니다.

### 소스 & 싱크 (`io.bluetape4k.graph.io.source`)

파일 경로와 raw 스트림을 통합 추상화하는 sealed 인터페이스:

```kotlin
sealed interface GraphImportSource {
    data class PathSource(val path: Path, val charset: Charset = Charsets.UTF_8) : GraphImportSource
    data class InputStreamSource(val input: InputStream, val charset: Charset = Charsets.UTF_8, val closeInput: Boolean = false) : GraphImportSource
}

sealed interface GraphExportSink {
    data class PathSink(val path: Path, val charset: Charset = Charsets.UTF_8, val append: Boolean = false) : GraphExportSink
    data class OutputStreamSink(val output: OutputStream, val charset: Charset = Charsets.UTF_8, val closeOutput: Boolean = false) : GraphExportSink
}
```

### 레코드 (`io.bluetape4k.graph.io.model`)

임포터가 외부 ID를 백엔드 ID로 resolve하기 전에 포맷 파서가 방출하는 중간 레코드:

- `GraphIoVertexRecord(externalId, label, properties)`
- `GraphIoEdgeRecord(externalId?, label, fromExternalId, toExternalId, properties)` — 엔드포인트는 **아직 resolve되지 않은 외부 ID**이며, 임포터가 `GraphIoExternalIdMap`을 통해 resolve합니다.

### 옵션 (`io.bluetape4k.graph.io.options`)

```kotlin
data class GraphImportOptions(
    val batchSize: Int = 1_000,
    val maxEdgeBufferSize: Int = 100_000,
    val onDuplicateVertexId: DuplicateVertexPolicy = DuplicateVertexPolicy.FAIL,
    val onMissingEdgeEndpoint: MissingEndpointPolicy = MissingEndpointPolicy.FAIL,
    val defaultVertexLabel: String = "Vertex",
    val defaultEdgeLabel: String = "Edge",
    val preserveExternalIdProperty: String? = "_graphIoExternalId",
)

data class GraphExportOptions(
    val vertexLabels: Set<String> = emptySet(),  // 비어있으면 라벨 조회 후 전체
    val edgeLabels: Set<String> = emptySet(),    // 비어있으면 라벨 조회 후 전체
    val includeEmptyProperties: Boolean = true,
    val exportChunkSize: Int = 1_000,
)

enum class DuplicateVertexPolicy { FAIL, SKIP }
enum class MissingEndpointPolicy { FAIL, SKIP_EDGE }
```

`batchSize`는 임포트 중 백엔드 쓰기 플러시 크기를 제어합니다. 임포터는 대기 중인 정점과 간선을 라벨별로 묶고, 라벨별 버퍼가 이 크기에 도달하면 `createVertices`/`createEdges`를 호출하며, 마지막 부분 버퍼는 종료 시 플러시합니다. 중복 ID나 누락 엔드포인트 정책의 의미는 바꾸지 않습니다.

`batchSize`는 양수여야 합니다. `GraphImportOptions`, `GraphIoBatchWriter`,
`SuspendGraphIoBatchWriter`는 모두 공유 Bluetape `requirePositiveNumber` 계약으로
0 또는 음수 값을 거부하며, writer를 직접 생성하는 경우에도 같은 검증이
적용됩니다.

`exportChunkSize`는 스트리밍 가능한 exporter가 `findVerticesByLabelChunked`,
`findEdgesByLabelChunked` 같은 chunk-aware repository API에서 한 번에 요청하는
레코드 수를 제어합니다. 이 메서드를 override하지 않은 백엔드는 기존 list/Flow
fallback을 사용하고, cursor-aware 백엔드는 전체 label materialization을 피할 수
있습니다. CSV처럼 전역 헤더가 필요한 포맷은 여전히 포맷별 pre-scan을 수행할 수
있습니다.

빈 라벨 집합은 `GraphLabelDiscovery`로 전체 라벨을 조회하라는 의미입니다.
해당 capability가 없는 백엔드는 명시적 라벨을 받아야 하며, exporter는 0건을
성공으로 조용히 반환하지 않고 명확한 오류를 발생시킵니다.

레이블 필드와 레이블 세트의 모든 원소에 `requireNotBlank` 검증이 적용됩니다.

### 리포트 (`io.bluetape4k.graph.io.report`)

```kotlin
data class GraphImportReport(
    val status: GraphIoStatus,                // COMPLETED | PARTIAL | FAILED
    val format: GraphIoFormat,                // CSV | NDJSON_JACKSON2 | NDJSON_JACKSON3 | GRAPHML
    val verticesRead: Long,
    val verticesCreated: Long,                // invariant: verticesCreated <= verticesRead
    val edgesRead: Long,
    val edgesCreated: Long,                   // invariant: edgesCreated <= edgesRead
    val skippedVertices: Long,
    val skippedEdges: Long,
    val elapsed: Duration,
    val failures: List<GraphIoFailure> = emptyList(),
)

data class GraphExportReport(
    val status: GraphIoStatus,
    val format: GraphIoFormat,
    val verticesWritten: Long,
    val edgesWritten: Long,
    val skippedVertices: Long,
    val skippedEdges: Long,
    val elapsed: Duration,
    val failures: List<GraphIoFailure> = emptyList(),
)

data class GraphIoFailure(
    val phase: GraphIoPhase,                  // READ_VERTEX | READ_EDGE | WRITE_VERTEX | WRITE_EDGE | ...
    val severity: GraphIoFailureSeverity = GraphIoFailureSeverity.ERROR,
    val location: String? = null,
    val sourceName: String? = null,
    val fileRole: GraphIoFileRole? = null,
    val recordId: String? = null,
    val columnName: String? = null,
    val elementName: String? = null,
    val message: String,
)
```

### 진행 listener와 metric

동기·suspend·Virtual Thread 포맷 진입점은 기존 overload를 유지하면서 마지막
인자로 required `GraphIoProgressListener`를 받는 overload를 추가로 제공합니다.
호출 하나마다 `STARTED`, 누적 `PROGRESS`/`PHASE_COMPLETED`, terminal
`COMPLETED`·`FAILED`·`CANCELLED` 중 하나가 순서대로 정확히 한 번 전달됩니다.
callback은 작업 thread에서 동기 실행되며 일반 callback 예외는 격리됩니다.
callback에서 `Error`가 발생하면 reporter가 중단되지만 원래 작업 예외는
그대로 보존됩니다.

선택 모듈 `bluetape4k-graph-io-micrometer`는 이벤트를 고정 cardinality meter로
변환합니다. operation·format·status·kind·phase enum tag만 사용하며 source
경로, record ID, run ID, exception message는 tag가 되지 않습니다. core 모듈은
Micrometer에 의존하지 않습니다.

```kotlin
val listener = GraphIoProgressListener { event ->
    println("${event.type} ${event.operation} ${event.format}")
}

CsvGraphBulkExporter().exportGraph(sink, graphOps, options, listener)
```

metric이 필요할 때만 bridge를 추가합니다.

```kotlin
dependencies {
    implementation("io.github.bluetape4k.graph:bluetape4k-graph-io-micrometer:$version")
}
```

### 지원 헬퍼 (`io.bluetape4k.graph.io.support`)

- **`GraphIoPaths`** — 모든 `GraphImportSource`/`GraphExportSink`에 대해 `BufferedReader`/`BufferedWriter`/`InputStream`/`OutputStream`을 열고, `PathSink`는 부모 디렉터리를 자동 생성하며, 호출자 소유 스트림에는 `closeInput`/`closeOutput` 플래그를 준수합니다. `closeInput/closeOutput=false` 시 스트림을 닫아도 underlying 스트림이 닫히지 않아 안전합니다. `OutputStreamSink`는 항상 `BufferedOutputStream`으로 래핑됩니다.
- **`GraphIoExternalIdMap`** — 임포트 중 외부 ID → 백엔드 `GraphElementId` 매핑을 추적하고 `DuplicateVertexPolicy`(`FAIL` 또는 `SKIP`)를 강제합니다.
- **`GraphIoBatchWriter` / `SuspendGraphIoBatchWriter`** — `GraphImportOptions.batchSize`에 따라 `createVertices`/`createEdges`로 플러시하는 라벨별 임포트 쓰기 버퍼입니다.
- **`GraphImportWorkflow` / `GraphImportJobStateStore`** — multi-source 임포트 manifest를 검증하고 순서가 있는 job state를 저장합니다. store의 `update` 경계는 한 JVM store 인스턴스에서 load/검증/save를 원자적으로 수행하며, 전이할 때 `copy(state = ...)`를 사용해 기존 `sources`·`elapsed`·`checkpoint` payload를 보존합니다. transform은 순수하고 retry-safe해야 하며 durable store는 native transaction 또는 CAS로 override해야 합니다. 재사용 가능한 state-store contract TCK는 Gradle `testFixtures` variant로 제공합니다.
- **`GraphIoStopwatch`** — 포맷 임포터/익스포터가 `report.elapsed`에 사용하는 밀리초 단위 타이머.
- **`VirtualThreadGraphBulkAdapter`** — 동기 `GraphBulkImporter`/`GraphBulkExporter`를 `CompletableFuture` 기반 Virtual Thread 비동기 변형으로 래핑합니다.

### Durable State Store TCK

`graph-io-core`는 Gradle `testFixtures` variant를 발행하므로 durable
`GraphImportJobStateStore` 구현체가 테스트 코드를 복사하지 않고 같은 계약
테스트를 실행할 수 있습니다.

```kotlin
dependencies {
    testImplementation(testFixtures(project(":bluetape4k-graph-io-core")))
}
```

외부에 발행된 모듈을 소비할 때는 external test-fixtures 표기를 사용합니다.

```kotlin
dependencies {
    testImplementation(testFixtures("io.github.bluetape4k.graph:bluetape4k-graph-io-core:$version"))
}
```

`AbstractGraphImportJobStateStoreContractTest`를 상속하고 `createStore()`를
구현하면 최신 report 갱신, 최초 report 생성, 저장하지 않는 `jobId` mismatch
거부, transform 실패 원자성을 검증합니다. CAS 또는 transaction 기반
adapter는 `AbstractGraphImportJobStateStoreRetryContractTest`를 추가로
상속하고 contention retry를 주입하는 adapter 전용
`GraphImportJobStateStoreRetryHarness`를 제공할 수 있습니다. retry 계약은
transform이 순수하고 재시도에 안전하며 최신 report로 계산한 결과만 저장하도록
요구하지만, durable 구현 자체를 제공하지는 않습니다.

## 사용법 (포맷 구현자 관점)

새 포맷을 구현하려면 `graph-io-core`에 의존하고 세 가지 실행 변형을 제공합니다:

```kotlin
class MyFormatBulkExporter : GraphBulkExporter<GraphExportSink> {
    override fun exportGraph(
        sink: GraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
    ): GraphExportReport {
        val sw = GraphIoStopwatch.start()
        val failures = mutableListOf<GraphIoFailure>()
        GraphIoPaths.openWriter(sink).use { writer ->
            // options.vertexLabels로 필터링한 정점, options.edgeLabels로 필터링한 간선을 스트리밍
        }
        return GraphExportReport(
            status = if (failures.isEmpty()) GraphIoStatus.COMPLETED else GraphIoStatus.PARTIAL,
            format = GraphIoFormat.CSV,
            verticesWritten = 0, edgesWritten = 0,
            skippedVertices = 0, skippedEdges = 0,
            elapsed = sw.elapsed(),
            failures = failures,
        )
    }
}

class MyFormatVirtualThreadBulkExporter(
    private val sync: MyFormatBulkExporter = MyFormatBulkExporter(),
) : GraphVirtualThreadBulkExporter<GraphExportSink> {
    override fun exportGraphAsync(
        sink: GraphExportSink,
        operations: GraphOperations,
        options: GraphExportOptions,
    ): CompletableFuture<GraphExportReport> =
        VirtualThreadGraphBulkAdapter.wrapExporter(sync).exportGraphAsync(sink, operations, options)
}
```

## 사용법 (소비자 관점)

애플리케이션 코드는 보통 `graph-io-core`에 직접 의존하지 않고, 포맷 모듈을 선택합니다:

```kotlin
// CSV 예시
import io.bluetape4k.graph.io.csv.CsvGraphBulkImporter
import io.bluetape4k.graph.io.csv.CsvGraphImportSource
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.source.GraphImportSource
import java.nio.file.Paths

val importer = CsvGraphBulkImporter()
val source = CsvGraphImportSource(
    vertices = GraphImportSource.PathSource(Paths.get("vertices.csv")),
    edges = GraphImportSource.PathSource(Paths.get("edges.csv")),
)
val report = importer.importGraph(source, graphOps, GraphImportOptions())
println("${report.verticesCreated} / ${report.verticesRead} 개 정점 임포트됨")
```

모든 포맷이 같은 패턴을 따릅니다 — `*BulkImporter` / `*BulkExporter` (동기), `Suspend*BulkImporter` / `Suspend*BulkExporter` (코루틴), `*VirtualThreadBulkImporter` / `*VirtualThreadBulkExporter` (VT).

## 설계 원칙

- **기본이 스트리밍.** 어떤 파서도 전체 파일을 메모리에 로드하지 않으며, 참조되는 모든 정점이 먼저 존재하도록 간선을 버퍼링합니다(`maxEdgeBufferSize`로 상한 제한).
- **호출자 소유 스트림.** `InputStreamSource` / `OutputStreamSink`는 기본값이 `closeInput = false` / `closeOutput = false`입니다. 종료 시 flush는 수행하지만 호출자 스트림은 그대로 열려 있습니다.
- **전체 실패보다 부분 성공.** 레코드별 문제는 `GraphIoFailure`로 보고되고, 전체 `status`는 중단 없이 `PARTIAL`로 설정됩니다(`onDuplicateVertexId`나 `onMissingEdgeEndpoint`가 `FAIL`일 때 제외).
- **외부 ID 보존.** `preserveExternalIdProperty`가 설정되면(기본값: `"_graphIoExternalId"`) 임포터가 원본 외부 ID를 정점 속성으로 기록하여 왕복(round-trip)이 손실 없이 이루어집니다.

### 백엔드 native bulk loading SPI

`io.bluetape4k.graph.io.nativebulk`는 백엔드가 소유한 native command 경로를
위한 additive 계약입니다. `GraphBulkImporter`와 분리되어 있으며, 백엔드
어댑터는 호출자가 소유한 raw `R` source를 검증한 뒤 typed one-shot
`GraphNativeBulkLoadValidatedSource<V>`를 반환합니다. native command에는 검증된
`V` handle과 deadline-aware cancellation token만 전달됩니다.

`GraphNativeBulkLoaderCapabilities`는 지원 source kind, transaction/failure
의미, URI 정책, bounded shutdown 보장을 선언합니다. 지원하지 않는 백엔드는
`UnsupportedGraphNativeBulkLoader`를 사용하며 고정 `UNSUPPORTED_SOURCE` 코드로
실패합니다. base loader가 progress와 report를 검증하고, raw adapter 원인,
경로, URI, source 값은 public exception이나 diagnostic event에 포함하지
않습니다.

TinkerPop/TinkerGraph는 이 SPI에서 의도적으로 제외합니다. 두 구현은 서버가
소유한 native bulk command나 staging lifecycle이 없는 인메모리/reference
graph이므로, 계속 portable `GraphBulkImporter` 경로를 사용합니다.

URI 접근은 기본적으로 거절됩니다. 허용하는 어댑터는 exact scheme/host/port
origin, redirect/private-network 정책과 실행 지점의 backend 재검증을 모두
강제해야 합니다. FILE/DIRECTORY 어댑터는 canonical artifact를 승인된 staging
root에 결합해야 합니다. 이 core 모듈은 파일을 열거나 URI를 dereference하거나
데이터를 staging하지 않으며, Neo4j/Memgraph/AGE/FalkorDB 어댑터와 Testcontainers
검증은 후속 백엔드 이슈의 범위입니다.

## 의존성

```kotlin
dependencies {
    api("io.bluetape4k:graph-io-core:$version")
}
```

전이 의존성: `bluetape4k-graph-core`, `bluetape4k-coroutines`, `bluetape4k-virtualthread`, `bluetape4k-logging`.

## 관련 모듈

- `graph-io-csv` — CSV (정점/간선 2개 파일)
- `graph-io-jackson2` — Jackson 2.x 기반 NDJSON
- `graph-io-jackson3` — Jackson 3.x (`tools.jackson`) 기반 NDJSON
- `graph-io-graphml` — StAX 기반 GraphML 2.4

## 스트리밍 reader 계약

`GraphRecordFlowReader`는 레코드 스트리밍 축입니다. `readVertices(source)`와
`readEdges(source)`가 소스 순서를 유지하며 레코드를 순차적으로 방출합니다. `GraphImportOptions.batchSize`는
`createVertices`/`createEdges`의 백엔드 쓰기 플러시 축이며 reader 버퍼링이나 source 소유권을 바꾸지 않습니다.
Path와 명시적으로 소유권을 넘긴 source는 라이브러리가 닫고, 호출자 소유 source는 열린 상태로 둡니다.
NDJSON 간선 staging은 `maxEdgeBufferSize`로 제한됩니다.
