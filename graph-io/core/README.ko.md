# graph-io-core

`graph-io` 계열의 벌크 임포터/익스포터가 공유하는 계약(contract), 모델, 옵션, 리포트, I/O 헬퍼.

## 개요

`graph-io-core`는 모든 `graph-io-*` 포맷 모듈(CSV, Jackson2 NDJSON, Jackson3 NDJSON, GraphML)이 의존하는 추상 인터페이스와 데이터 타입을 정의합니다. **포맷/백엔드에 종속적인 코드는 전혀 포함하지 않으며**, 동기 · Kotlin 코루틴 `suspend` · Java Virtual Thread 기반 `CompletableFuture` 세 가지 실행 모델 전반에서 동일한 계약을 구현할 수 있게 하는 것이 유일한 역할입니다.

이 모듈은 보통 직접 사용하지 않습니다. 애플리케이션은 포맷 모듈(`graph-io-csv`, `graph-io-jackson3` 등) 중 하나에 의존하며, 해당 모듈이 이 타입들을 전이적으로 노출합니다.

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
    val vertexLabels: Set<String> = emptySet(),  // 비어있으면 전체 레이블
    val edgeLabels: Set<String> = emptySet(),    // 비어있으면 전체 레이블
    val includeEmptyProperties: Boolean = true,
)

enum class DuplicateVertexPolicy { FAIL, SKIP }
enum class MissingEndpointPolicy { FAIL, SKIP_EDGE }
```

레이블 필드와 레이블 세트의 모든 원소에 `requireNotBlank` 검증이 적용됩니다.

### 리포트 (`io.bluetape4k.graph.io.report`)

```kotlin
data class GraphImportReport(
    val status: GraphIoStatus,                // COMPLETED | PARTIAL | FAILED
    val format: GraphIoFormat,                // CSV | NDJSON_JACKSON2 | NDJSON_JACKSON3 | GRAPHML
    val verticesRead: Long,
    val verticesCreated: Long,
    val edgesRead: Long,
    val edgesCreated: Long,
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

### 지원 헬퍼 (`io.bluetape4k.graph.io.support`)

- **`GraphIoPaths`** — 모든 `GraphImportSource`/`GraphExportSink`에 대해 `BufferedReader`/`BufferedWriter`/`InputStream`/`OutputStream`을 열고, `PathSink`는 부모 디렉터리를 자동 생성하며, 호출자 소유 스트림에는 `closeInput`/`closeOutput` 플래그를 준수합니다.
- **`GraphIoExternalIdMap`** — 임포트 중 외부 ID → 백엔드 `GraphElementId` 매핑을 추적하고 `DuplicateVertexPolicy`(`FAIL` 또는 `SKIP`)를 강제합니다.
- **`GraphIoStopwatch`** — 포맷 임포터/익스포터가 `report.elapsed`에 사용하는 밀리초 단위 타이머.
- **`VirtualThreadGraphBulkAdapter`** — 동기 `GraphBulkImporter`/`GraphBulkExporter`를 `CompletableFuture` 기반 Virtual Thread 비동기 변형으로 래핑합니다.

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
