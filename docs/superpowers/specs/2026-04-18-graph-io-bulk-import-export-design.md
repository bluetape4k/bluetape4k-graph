# graph-io Bulk Import/Export Design Spec

- **Date**: 2026-04-18
- **Status**: Ready for Implementation
- **Modules**: `graph-io`, `graph-io/csv`, `graph-io/jackson2`, `graph-io/jackson3`, `graph-io/graphml`, `graph-io-benchmark`
- **Author**: bluetape4k-graph
- **Related TODO**: `TODO.md` 2순위 `graph-io` 모듈 - 벌크 임포트/익스포트

---

## 1. Goals

`graph-io` 계열 모듈은 `GraphOperations` 위에서 대량 그래프 데이터를 파일/스트림으로
import/export 하는 공통 기능을 제공한다.

지원해야 하는 포맷:

| 포맷 | 방향 | 모듈 |
|------|------|------|
| CSV (정점/간선 분리 파일) | import / export | `graph-io-csv` |
| JSON Lines / NDJSON | import / export | `graph-io-jackson2`, `graph-io-jackson3` |
| GraphML | import / export | `graph-io-graphml` |

지원해야 하는 실행 모델:

| 실행 모델 | 계약 |
|-----------|------|
| Sync | `GraphImportReport`, `GraphExportReport` 직접 반환 |
| Virtual Threads | `CompletableFuture<GraphImportReport>`, `CompletableFuture<GraphExportReport>` 반환 |
| Coroutines | `suspend` API + 필요한 read side에는 `Flow` 기반 streaming 제공 |

필수 제약:

- JSON은 Jackson 2와 Jackson 3를 모두 지원한다.
- `bluetape4k-csv`, `bluetape4k-jackson2`, `bluetape4k-jackson3`, `bluetape4k-io`를 우선 활용한다.
- 신규 외부 의존성은 추가하지 않는다.
- benchmark를 수행하고 결과를 파일 포맷별 처리 시간과 데이터량(Vertex 수, Edge 수) 기준으로 문서화한다.

---

## 2. Non-Goals

- 그래프 백엔드별 native bulk loader 최적화는 이번 범위가 아니다.
  - 예: Neo4j `LOAD CSV`, PostgreSQL `COPY`, Memgraph bulk import.
- import 중 실패한 일부 레코드를 자동 롤백하는 트랜잭션 DSL은 이번 범위가 아니다.
  - 트랜잭션 DSL은 TODO의 별도 2순위 항목이다.
- GraphML의 전체 스펙을 100% 지원하지 않는다.
  - 1차 범위는 node / edge / key / data 기반의 일반 속성 그래프다.
  - nested graph, hyperedge, port, complex extension element는 명시적으로 지원하지 않는다.
- TinkerPop `GraphMLReader`/`GraphMLWriter`에 직접 의존하지 않는다.
  - `graph-io-graphml`은 backend-neutral `GraphOperations` 위에서 동작해야 한다.
- 1차 Sync/VT export는 backend repository가 반환하는 label 단위 `List`를 스트림으로 바로 기록한다.
  - backend 내부 조회 결과의 전체 적재를 없애는 cursor/chunked export는 별도 streaming repository API 작업으로 둔다.
  - Coroutine export는 `GraphSuspendOperations`가 제공하는 `Flow` 조회 API를 사용한다.
  - CSV는 헤더를 먼저 써야 하므로 Coroutine CSV export도 v1에서는 per-label `Flow`를 `toList()`로 수집해 Union header를 계산한 뒤 기록한다.
  - 완전 streaming CSV export with late/dynamic headers는 v1 범위가 아니다.
- 압축 파일 포맷(gzip/zip/zstd)은 1차 범위가 아니다.
  - 추후 `bluetape4k-io` compressor와 결합하는 별도 확장으로 둔다.
- exact JVM property value type fidelity across formats is not guaranteed.
  - CSV values are text at the file boundary.
  - NDJSON numeric type materialization follows Jackson mapper defaults.
  - GraphML type coercion follows `attr.type`.
  - Cross-format round-trip tests compare logical values, not exact JVM numeric classes such as `Int` vs `Long`.
- Undirected GraphML import/export is not supported in v1.
  - `edgedefault="undirected"` or `edge directed="false"` is recorded as warning and skipped by default.
  - strict GraphML mode treats it as `FAILED`.

---

## 3. Research

### 3.1 Local bluetape4k Reuse Surface

조사한 로컬 소스: `/Users/debop/work/bluetape4k/bluetape4k-projects`.

`bluetape4k-io`

- `Path.bufferedReader(...)`, `Path.bufferedWriter(...)`는 UTF-8 기본값과 버퍼 크기 옵션을 제공한다.
- `Path.readAllBytesAsync(...)`, `Path.writeAsync(...)`는 `CompletableFuture` 기반 파일 API를 제공한다.
- `readAllBytesSuspending(...)`, `writeSuspending(...)`는 coroutine wrapper를 제공한다.
- `combineSafe(...)`는 상대 경로 결합 시 directory traversal을 방지하는 관례를 제공한다.
- 대용량 graph IO에서는 전체 파일 적재 API보다 buffered stream API를 기본으로 사용한다.

`bluetape4k-csv`

- Sync reader/writer:
  - `CsvRecordReader`
  - `CsvRecordWriter`
  - `File.readAsCsvRecords(...)`
  - `File.writeCsvRecords(...)`
- Coroutine reader/writer:
  - `SuspendCsvRecordReader`
  - `SuspendCsvRecordWriter`
  - `SuspendRecordReader`는 `Flow<T>`를 반환한다.
  - `SuspendRecordWriter`는 `Flow<Iterable<*>>` 입력을 순차 기록한다.

`bluetape4k-jackson2`

- `io.bluetape4k.jackson.Jackson.defaultJsonMapper`
- `JacksonSerializer`
- `jacksonTypeRef`
- `readValueOrNull(...)`
- `writeAsString(...)`, `writeAsBytes(...)`

`bluetape4k-jackson3`

- `io.bluetape4k.jackson3.Jackson.defaultJsonMapper`
- `JacksonSerializer`
- `jacksonTypeRef`
- `readValueOrNull(...)`
- `writeAsString(...)`, `writeAsBytes(...)`
- Jackson 3 패키지는 `tools.jackson.*`이므로 Jackson 2 코드와 소스 레벨에서 분리해야 한다.

Virtual Threads / CompletableFuture

- `io.bluetape4k.concurrent.virtualthread.VirtualThreadExecutor`
- `virtualFutureOf(...)`, `virtualFutureOfNullable(...)`
- `StructuredTaskScopes.withAll(...)`
- 기존 `graph-core` VT API는 `CompletableFuture<T>`를 public contract로 사용한다.

### 3.2 External Research

- Jackson core는 low-level incremental streaming parser/generator abstraction을 제공한다.
  - Source: https://github.com/FasterXML/jackson-core
- Jackson 3.0은 2025-10-03 정식 릴리스된 메이저 버전이며, 2.x와 API 호환이 아니다.
  - group id와 Java package가 `com.fasterxml.jackson`에서 `tools.jackson`으로 변경됐다.
  - Source: https://github.com/FasterXML/jackson/wiki/Jackson-Release-3.0
- TinkerPop IO 문서는 GraphML이 넓은 호환성을 가진 XML graph exchange format이지만,
  GraphML이 지원하지 않는 TinkerPop 기능(meta-properties 등)과 TinkerPop이 구현하지 않는 GraphML 기능(custom types 등)이 있다고 설명한다.
  - Source: https://tinkerpop.apache.org/docs/current/dev/io/
- GraphML 공식 문서는 XML 기반 common denominator graph format이며, hypergraph, hierarchical graph,
  application-specific attribute data 등을 지원한다고 설명한다.
  - Source: https://graphml.graphdrawing.org/
  - Primer: https://graphml.graphdrawing.org/primer/graphml-primer.html

---

## 4. Brainstorming: Module Split Options

### Option A - Single `graph-io` Module

```
graph-io/
  CSV + Jackson2 + Jackson3 + GraphML + common contracts
```

Pros:

- 모듈 수가 가장 적다.
- 사용자 입장에서 의존성 하나로 모든 포맷 사용 가능.

Cons:

- Jackson 2와 Jackson 3의 package/API 차이가 같은 모듈에 공존한다.
- 사용자가 NDJSON 하나만 써도 CSV, XML, Jackson 2/3 관련 classpath를 모두 갖게 된다.
- GraphML XML 구현과 CSV/JSON 구현이 같은 모듈에 섞인다.
- Maven artifact의 의존성 표면이 커져 장기 유지보수 비용이 높다.

Verdict: 기각.

### Option B - Common + JSON만 분리

```
graph-io/
  common + CSV + GraphML
graph-io/jackson2/
graph-io/jackson3/
```

Pros:

- Jackson 2/3 충돌은 피한다.
- 모듈 수가 Option C보다 적다.

Cons:

- `graph-io`가 여전히 `bluetape4k-csv`와 XML 구현을 직접 가진다.
- CSV/GraphML을 쓰지 않는 사용자에게도 포맷 구현이 따라온다.
- GraphML은 지원 범위와 제한이 뚜렷해 독립 README/테스트/벤치마크가 필요하다.

Verdict: 부분적으로 타당하지만 GraphML 분리 요구와 의존성 최소화 기준에는 부족하다.

### Option C - Common + Format Modules (채택)

```
graph-io/           ← 공통 계약
graph-io/csv/
graph-io/jackson2/
graph-io/jackson3/
graph-io/graphml/
graph-io-benchmark/ ← IO 전용 벤치마크
```

Pros:

- `graph-io`는 순수 공통 계약과 report/model만 가진다.
- CSV, Jackson 2, Jackson 3, GraphML 의존성을 포맷별로 격리한다.
- Jackson 2/3 package/API 차이를 명확하게 분리한다.
- GraphML의 제한 범위와 XML parser/writer 구현을 독립적으로 테스트할 수 있다.
- benchmark가 포맷 모듈별로 명확해진다.

Cons:

- 모듈 수가 늘어난다.
- BOM과 README에 artifact 설명이 더 필요하다.
- 공통 test fixture가 필요하다.

Verdict: 채택.

---

## 5. Final Module Design

### 디렉터리 구조

포맷 모듈을 `graph-io/` 하위에 중첩하여 `graph/` 디렉터리의 모듈 수를 줄인다.

```
graph/
  graph-io/                    ← 공통 계약 모듈
    csv/                       ← CSV 포맷 모듈
    jackson2/                  ← Jackson 2 NDJSON 포맷 모듈
    jackson3/                  ← Jackson 3 NDJSON 포맷 모듈
    graphml/                   ← GraphML 포맷 모듈
graph-io-benchmark/            ← IO 전용 벤치마크 모듈 (graph/ 외부)
```

Gradle 프로젝트 경로:

| 디렉터리 | Gradle project path |
|---------|---------------------|
| `graph-io/` | `:graph-io` |
| `graph-io/csv/` | `:graph-io-csv` |
| `graph-io/jackson2/` | `:graph-io-jackson2` |
| `graph-io/jackson3/` | `:graph-io-jackson3` |
| `graph-io/graphml/` | `:graph-io-graphml` |
| `graph-io-benchmark/` | `:graph-io-benchmark` |

`settings.gradle.kts`에서 중첩 모듈은 명시적 `include` + `projectDir` 로 등록한다.

### 5.1 `graph-io` (`graph-io/`)

역할:

- 공통 data model
- import/export option
- import/export report
- Sync / VT / Coroutine repository contract
- 파일/스트림 source/sink abstraction
- format-neutral helper

Dependencies:

```kotlin
dependencies {
    api(project(":graph-core"))
    api(Libs.bluetape4k_core)
    api(Libs.bluetape4k_io)
    api(Libs.kotlinx_coroutines_core)
    implementation(Libs.bluetape4k_virtualthread_api)
    implementation(Libs.bluetape4k_virtualthread_jdk25)
    implementation(Libs.bluetape4k_coroutines)
}
```

### 5.2 `graph-io-csv` (`graph-io/csv/`)

역할:

- CSV vertices file + edges file import/export
- `bluetape4k-csv` 기반 Sync/Coroutine 구현
- VT는 Sync 구현을 `CompletableFuture`로 감싸는 adapter 제공

Dependencies:

```kotlin
dependencies {
    api(project(":graph-io"))
    api(Libs.bluetape4k_csv)
    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.bluetape4k_virtualthread_api)
    implementation(Libs.bluetape4k_virtualthread_jdk25)
}
```

### 5.3 `graph-io-jackson2` (`graph-io/jackson2/`)

역할:

- Jackson 2 기반 NDJSON import/export
- `io.bluetape4k.jackson.*` 활용

Dependencies:

```kotlin
dependencies {
    api(project(":graph-io"))
    api(Libs.bluetape4k_jackson2)
    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.bluetape4k_virtualthread_api)
    implementation(Libs.bluetape4k_virtualthread_jdk25)
}
```

### 5.4 `graph-io-jackson3` (`graph-io/jackson3/`)

역할:

- Jackson 3 기반 NDJSON import/export
- `io.bluetape4k.jackson3.*` 활용

Dependencies:

```kotlin
dependencies {
    api(project(":graph-io"))
    api(Libs.bluetape4k_jackson3)
    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.bluetape4k_virtualthread_api)
    implementation(Libs.bluetape4k_virtualthread_jdk25)
}
```

### 5.5 `graph-io-graphml` (`graph-io/graphml/`)

역할:

- GraphML import/export
- JDK StAX 기반 streaming XML parser/writer
- `bluetape4k-io` 기반 파일/스트림 처리

Dependencies:

```kotlin
dependencies {
    api(project(":graph-io"))
    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.bluetape4k_virtualthread_api)
    implementation(Libs.bluetape4k_virtualthread_jdk25)
}
```

### 5.6 `graph-io-benchmark` (루트의 `graph-io-benchmark/`)

역할:

- graph-io 전용 JMH 벤치마크
- CSV / NDJSON / GraphML × Sync / VT / Coroutines 성능 측정

Dependencies:

```kotlin
dependencies {
    implementation(project(":graph-io"))
    implementation(project(":graph-io-csv"))
    implementation(project(":graph-io-jackson2"))
    implementation(project(":graph-io-jackson3"))
    implementation(project(":graph-io-graphml"))
    implementation(project(":graph-tinkerpop"))
    implementation(Libs.bluetape4k_core)
    implementation(Libs.bluetape4k_coroutines)
    implementation(Libs.bluetape4k_virtualthread_api)
    implementation(Libs.bluetape4k_virtualthread_jdk25)
    implementation(Libs.kotlinx_benchmark_runtime)
}
```

XML parser/writer는 JDK `java.xml` 모듈에 포함된 `javax.xml.stream` StAX API를 우선 사용한다.
Jakarta XML이나 외부 XML dependency는 추가하지 않는다.

---

## 6. Common Data Model

### 6.1 External Records

`GraphOperations.createVertex(...)`는 외부 ID를 직접 지정하지 않는다.
따라서 import는 외부 파일 ID와 생성된 backend ID의 매핑을 반드시 관리한다.

```kotlin
data class GraphIoVertexRecord(
    val externalId: String,
    val label: String,
    val properties: Map<String, Any?> = emptyMap(),
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

data class GraphIoEdgeRecord(
    val externalId: String? = null,
    val label: String,
    val fromExternalId: String,
    val toExternalId: String,
    val properties: Map<String, Any?> = emptyMap(),
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}
```

Validation:

- `externalId` must not be blank.
- `label` must not be blank.
- `fromExternalId`, `toExternalId` must not be blank.
- duplicate vertex `externalId` behavior follows `DuplicateVertexPolicy`.
  - `FAIL`: import returns `FAILED`.
  - `SKIP`: keep the first external ID mapping, skip the duplicate vertex, increment `skippedVertices`, record `WARN`, and continue.
- missing edge endpoint behavior follows `MissingEndpointPolicy`.
  - `FAIL`: import returns `FAILED`.
  - `SKIP_EDGE`: skip only that edge, increment `skippedEdges`, record `WARN`, and continue with `PARTIAL`.
- validation은 `requireNotBlank`, `requirePositiveNumber`, `requireZeroOrPositiveNumber` 등 bluetape4k helper를 사용한다.
- 모든 public record/options/report/failure data class는 `Serializable`과 `serialVersionUID`를 가진다.

### 6.2 Source / Sink

```kotlin
sealed interface GraphImportSource {
    data class PathSource(
        val path: Path,
        val charset: Charset = Charsets.UTF_8,
    ) : GraphImportSource, Serializable {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    data class InputStreamSource(
        val input: InputStream,
        val charset: Charset = Charsets.UTF_8,
        val closeInput: Boolean = false,
    ) : GraphImportSource
}

sealed interface GraphExportSink {
    data class PathSink(
        val path: Path,
        val charset: Charset = Charsets.UTF_8,
        val append: Boolean = false,
    ) : GraphExportSink, Serializable {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    data class OutputStreamSink(
        val output: OutputStream,
        val charset: Charset = Charsets.UTF_8,
        val closeOutput: Boolean = false,
    ) : GraphExportSink
}
```

Ownership contract:

- `PathSource` / `PathSink`: graph-io가 파일을 열고 닫는다.
- `InputStreamSource` / `OutputStreamSink`: 기본은 호출자가 닫는다.
- `closeInput` / `closeOutput`이 `true`일 때만 graph-io가 닫는다.

CSV는 정점/간선 파일이 분리되므로 wrapper source/sink를 둔다.
These wrappers belong to `graph-io-csv`, not the common `graph-io` module.

```kotlin
data class CsvGraphImportSource(
    val vertices: GraphImportSource,
    val edges: GraphImportSource,
)

data class CsvGraphExportSink(
    val vertices: GraphExportSink,
    val edges: GraphExportSink,
)
```

Serialization contract:

- `PathSource` and `PathSink` are `Serializable`.
- `InputStreamSource` and `OutputStreamSink` are not `Serializable` because stream instances are caller-owned runtime resources.
- `CsvGraphImportSource` and `CsvGraphExportSink` are not `Serializable` because they may wrap stream-backed sources/sinks.
- Public report/option/record classes that do not own runtime streams remain `Serializable`.

### 6.3 Options

```kotlin
data class GraphImportOptions(
    val batchSize: Int = 1_000,
    val maxEdgeBufferSize: Int = 100_000,
    val onDuplicateVertexId: DuplicateVertexPolicy = DuplicateVertexPolicy.FAIL,
    val onMissingEdgeEndpoint: MissingEndpointPolicy = MissingEndpointPolicy.FAIL,
    val defaultVertexLabel: String = "Vertex",
    val defaultEdgeLabel: String = "Edge",
    val preserveExternalIdProperty: String? = "_graphIoExternalId",
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

enum class DuplicateVertexPolicy {
    FAIL,
    SKIP,
}

enum class MissingEndpointPolicy {
    FAIL,
    SKIP_EDGE,
}

data class GraphExportOptions(
    val vertexLabels: Set<String> = emptySet(),
    val edgeLabels: Set<String> = emptySet(),
    val includeEmptyProperties: Boolean = true,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

data class GraphMlImportOptions(
    val unsupportedElementPolicy: UnsupportedGraphMlElementPolicy = UnsupportedGraphMlElementPolicy.SKIP,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

enum class UnsupportedGraphMlElementPolicy {
    SKIP,
    FAIL,
}
```

Export property filtering:

- `includeEmptyProperties` controls entries inside a non-empty `properties` map whose value is `null`.
- A vertex/edge with `properties = emptyMap()` is still exported regardless of `includeEmptyProperties`.
- `includeEmptyProperties = false` omits `null` property entries from JSON/GraphML output and excludes all-null-only property columns from CSV Union headers when no non-null value exists.
- Missing property values in CSV rows remain empty cells when the column exists because another record has a value.

KDoc requirement:

- `GraphExportOptions()` default instance is valid as a value object but cannot execute export in v1.
- KDoc must warn that `vertexLabels` and/or `edgeLabels` must be non-empty until label discovery is implemented.
- Export methods fail fast with `IllegalArgumentException` when required labels are not supplied.

Validation:

- `batchSize` must be positive.
- `maxEdgeBufferSize` must be positive.
- labels in filter sets must not be blank.
- `defaultVertexLabel`, `defaultEdgeLabel` must not be blank.
- `preserveExternalIdProperty`, if present, must not be blank.
- `GraphIoFailure.message` must not be blank.
- CSV property prefix and raw JSON column name must not be blank.
- Source/sink `Path` must be valid and parent directory is created for `PathSink`.

Graph target:

- `GraphOperations`/`GraphSuspendOperations` 인스턴스가 이미 대상 graph/session을 결정한다고 본다.
- import/export options는 `graphName`을 갖지 않는다.
- graph 생성/삭제/존재 확인은 호출자가 `GraphSession`/`GraphSuspendSession` API로 선행한다.

Batching contract:

- `batchSize` is the progress/reporting and writer flush cadence for records already read from a format.
- Because current `GraphOperations` exposes single-record create APIs, `batchSize` does not imply backend-native batch mutation in v1.
- `maxEdgeBufferSize` is independent of `batchSize`; it caps the NDJSON in-memory edge buffer before endpoint resolution.

### 6.4 Reports

```kotlin
data class GraphImportReport(
    val status: GraphIoStatus,
    val format: GraphIoFormat,
    val verticesRead: Long,
    val verticesCreated: Long,
    val edgesRead: Long,
    val edgesCreated: Long,
    val skippedVertices: Long,
    val skippedEdges: Long,
    val elapsed: Duration,
    val failures: List<GraphIoFailure> = emptyList(),
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

data class GraphExportReport(
    val status: GraphIoStatus,
    val format: GraphIoFormat,
    val verticesWritten: Long,
    val edgesWritten: Long,
    val skippedVertices: Long = 0,
    val skippedEdges: Long = 0,
    val elapsed: Duration,
    val failures: List<GraphIoFailure> = emptyList(),
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

enum class GraphIoStatus {
    COMPLETED,
    FAILED,
    PARTIAL,
}

enum class GraphIoFormat {
    CSV,
    NDJSON_JACKSON2,
    NDJSON_JACKSON3,
    GRAPHML,
}

data class GraphIoFailure(
    val phase: GraphIoPhase,
    val severity: GraphIoFailureSeverity = GraphIoFailureSeverity.ERROR,
    val location: String? = null,
    val sourceName: String? = null,
    val fileRole: GraphIoFileRole? = null,
    val recordId: String? = null,
    val columnName: String? = null,
    val elementName: String? = null,
    val message: String,
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

enum class GraphIoFailureSeverity {
    INFO,
    WARN,
    ERROR,
}

enum class GraphIoFileRole {
    VERTICES,
    EDGES,
    UNIFIED,
}

enum class GraphIoPhase {
    READ_VERTEX,
    READ_EDGE,
    WRITE_VERTEX,
    WRITE_EDGE,
    CREATE_VERTEX,
    CREATE_EDGE,
}
```

Rule:

- `FAIL` policy에서 첫 실패가 발생하면 `status = FAILED`.
- `SKIP` 계열 정책으로 일부 row를 건너뛰면 `status = PARTIAL`.
- WARN-only outcomes that do not skip graph data remain `COMPLETED` with failures containing `severity = WARN`.
- WARN outcomes that skip vertices/edges/elements affecting graph data return `PARTIAL`.
- 모든 row가 정상 처리되면 `status = COMPLETED`.
- CSV location은 `line:42`, `column:prop.name`처럼 기록한다.
- NDJSON location은 `line:42`처럼 기록한다.
- GraphML location은 `graph/node[@id='v1']/data[@key='label']` 또는 StAX `line:column`을 함께 기록한다.
- `fileRole` is file role, not format:
  - CSV vertices file: `VERTICES`
  - CSV edges file: `EDGES`
  - NDJSON and GraphML single-file source: `UNIFIED`
- Format is represented by `GraphImportReport.format` / `GraphExportReport.format`.

---

## 7. Common API Contracts

### 7.1 Sync

```kotlin
interface GraphBulkImporter<S : Any> {
    fun importGraph(
        source: S,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
    ): GraphImportReport
}

interface GraphBulkExporter<T : Any> {
    fun exportGraph(
        sink: T,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
    ): GraphExportReport
}
```

Generic source/sink type parameter:

- public generic contract는 `S : Any`, `T : Any` 상한을 둔다.
- concrete implementation은 marker interface나 format-specific source/sink type을 사용한다.

Format-specific options:

- Common interfaces accept only `GraphImportOptions` / `GraphExportOptions`.
- Format modules expose overloads with typed format-specific options.
- The generic interface implementation delegates to the overload with default format options.

```kotlin
class CsvGraphBulkImporter : GraphBulkImporter<CsvGraphImportSource> {
    override fun importGraph(
        source: CsvGraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
    ): GraphImportReport =
        importGraph(source, operations, options, CsvGraphIoOptions())

    fun importGraph(
        source: CsvGraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
    ): GraphImportReport
}

class GraphMlBulkImporter : GraphBulkImporter<GraphImportSource> {
    override fun importGraph(
        source: GraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions,
    ): GraphImportReport =
        importGraph(source, operations, options, GraphMlImportOptions())

    fun importGraph(
        source: GraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
        graphMlOptions: GraphMlImportOptions = GraphMlImportOptions(),
    ): GraphImportReport
}
```

VT/coroutine overload examples:

```kotlin
class CsvGraphVirtualThreadBulkImporter : GraphVirtualThreadBulkImporter<CsvGraphImportSource> {
    fun importGraphAsync(
        source: CsvGraphImportSource,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
    ): CompletableFuture<GraphImportReport>
}

class SuspendCsvGraphBulkImporter : GraphSuspendBulkImporter<CsvGraphImportSource> {
    suspend fun importGraphSuspending(
        source: CsvGraphImportSource,
        operations: GraphSuspendOperations,
        options: GraphImportOptions = GraphImportOptions(),
        csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
    ): GraphImportReport
}
```

Rationale:

- This keeps common contracts small and typed.
- It avoids untyped `Map<String, Any>` format option bags.
- It follows the repository pattern where concrete implementations can add convenience overloads without weakening the common interface.
- The same overload pattern applies to VT and Coroutine implementations.
- For example, `SuspendCsvGraphBulkImporter.importGraphSuspending(..., csvOptions: CsvGraphIoOptions)` delegates from the common `GraphSuspendBulkImporter` override with default format options.

### 7.2 Virtual Threads

```kotlin
interface GraphVirtualThreadBulkImporter<S : Any> {
    fun importGraphAsync(
        source: S,
        operations: GraphOperations,
        options: GraphImportOptions = GraphImportOptions(),
    ): CompletableFuture<GraphImportReport>
}

interface GraphVirtualThreadBulkExporter<T : Any> {
    fun exportGraphAsync(
        sink: T,
        operations: GraphOperations,
        options: GraphExportOptions = GraphExportOptions(),
    ): CompletableFuture<GraphExportReport>
}
```

Implementation pattern:

- 기본 구현은 Sync importer/exporter를 `virtualFutureOf { ... }` 또는 `CompletableFuture.supplyAsync(..., VirtualThreadExecutor)`로 감싼다.
- 별도 executor를 public API에 노출하지 않는다.
- 기존 `graph-core` VT API처럼 Java interop을 우선한다.
- `CompletableFuture.cancel(true)` does not guarantee interruption of the underlying sync import/export once started.
- Cancellation responsiveness is a non-goal for the 1차 VT adapter and must be documented in README.

### 7.3 Coroutines

```kotlin
interface GraphSuspendBulkImporter<S : Any> {
    suspend fun importGraphSuspending(
        source: S,
        operations: GraphSuspendOperations,
        options: GraphImportOptions = GraphImportOptions(),
    ): GraphImportReport
}

interface GraphSuspendBulkExporter<T : Any> {
    suspend fun exportGraphSuspending(
        sink: T,
        operations: GraphSuspendOperations,
        options: GraphExportOptions = GraphExportOptions(),
    ): GraphExportReport
}
```

Read-side streaming helpers:

```kotlin
interface GraphRecordFlowReader<S : Any> {
    fun readVertices(source: S): Flow<GraphIoVertexRecord>
    fun readEdges(source: S): Flow<GraphIoEdgeRecord>
}
```

Rule:

- import/export 최상위 API는 report를 반환한다.
- row-level streaming은 helper API로 제한한다.
- public bulk API가 resource-owning lazy `Sequence`를 그대로 반환하지 않는다.
- Coroutine bulk API는 `GraphSuspendOperations`를 받는다.
- Sync operations를 coroutine에서 감싸는 adapter는 1차 public API가 아니다.
- blocking stream 작업이 필요한 포맷 내부에서는 `withContext(Dispatchers.IO)`를 사용하고 cancellation 전파를 보존한다.
- Coroutine export는 `GraphSuspendVertexRepository.findVerticesByLabel(...)`와 `GraphSuspendEdgeRepository.findEdgesByLabel(...)`을 우선 사용한다.
- `GraphRecordFlowReader.readEdges(...)` returns raw edge records with unresolved `fromExternalId` / `toExternalId`.
- Endpoint resolution belongs to the bulk importer after vertex creation and external ID mapping.

---

## 8. Format Specifications

### 8.1 CSV

CSV는 정점 파일과 간선 파일을 분리한다.

Vertices header:

```csv
id,label,prop.name,prop.age
v1,Person,Alice,30
```

Edges header:

```csv
id,label,from,to,prop.since
e1,KNOWS,v1,v2,2024
```

Rules:

- `id`, `label` are required for vertices.
- `label`, `from`, `to` are required for edges.
- edge `id` is optional.
- `prop.` prefixed columns become `properties`.
- `properties` JSON parsing in CSV common module must avoid Jackson 2/3 dependency.
  - 1차 구현은 property map을 scalar columns로 표현하는 option을 제공한다.
  - JSON-in-CSV property parsing은 `graph-io-jackson2`/`graph-io-jackson3` bridge helper로 확장하거나,
    CSV 모듈에서 문자열로 보존하는 opt-in을 둔다.

CSV property mapping options:

```kotlin
data class CsvGraphIoOptions(
    val propertyMode: CsvPropertyMode = CsvPropertyMode.PrefixedColumns(),
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

sealed interface CsvPropertyMode {
    data object None : CsvPropertyMode, Serializable {
        private const val serialVersionUID: Long = 1L
    }

    data class PrefixedColumns(val prefix: String = "prop.") : CsvPropertyMode, Serializable {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }

    data class RawJsonColumn(val columnName: String = "properties") : CsvPropertyMode, Serializable {
        companion object {
            private const val serialVersionUID: Long = 1L
        }
    }
}
```

Default:

- `PrefixedColumns("prop.")`
- 예: `prop.name`, `prop.age`

Reason:

- `graph-io-csv`가 Jackson 2/3 중 하나에 묶이지 않도록 한다.
- `CsvPropertyMode.None` means "ignore all non-reserved columns and import/export empty property maps".
- Reserved CSV columns are `id`, `label`, `from`, `to`.
- raw JSON property column은 CSV-only 경로에서는 `properties[columnName] = rawJsonString`으로 보존한다.
- raw JSON property column을 `Map<String, Any?>`로 파싱하는 helper는 JSON 모듈에 둔다.
- `PrefixedColumns`에서 property key가 prefix 또는 `.`을 포함하면 기본은 그대로 열 이름으로 사용한다.
- 동일 column name 충돌이 발생하면 import/export는 실패한다.
- 추후 escape가 필요하면 `CsvPropertyNameCodec`을 별도 확장으로 추가한다.

CSV export header strategy:

- v1 uses Union header strategy.
- For each exported vertex label, exporter pre-scans vertices returned for that label and builds the union of property keys.
- For each exported edge label, exporter pre-scans edges returned for that label and builds the union of property keys.
- Header order is stable:
  - reserved columns first: vertices `id,label`, edges `id,label,from,to`
  - property columns sorted by property key
- Missing property values are written as empty cells.
- This adds one in-memory label result list pass for Sync/VT export because current repositories already return `List`.
- Coroutine export may collect per-label property key union before writing headers; fully streaming CSV with late headers is deferred.
- Risk is documented in the export memory risk and benchmark peak heap notes.

Property value type fidelity:

- CSV imports values as strings by default.
- CSV may heuristically parse booleans and numbers only when an explicit option is added later; v1 does not infer types by default.
- NDJSON number materialization follows Jackson 2/3 mapper defaults.
- GraphML uses `attr.type="string|int|long|float|double|boolean"` for value coercion.
- Cross-format round-trip does not guarantee exact JVM classes for scalar values.

### 8.2 NDJSON

NDJSON은 한 줄에 하나의 envelope record를 쓴다.

```json
{"type":"vertex","id":"v1","label":"Person","properties":{"name":"Alice","age":30}}
{"type":"vertex","id":"v2","label":"Person","properties":{"name":"Bob","age":31}}
{"type":"edge","id":"e1","label":"KNOWS","from":"v1","to":"v2","properties":{"since":2024}}
```

Rules:

- vertex lines can appear before edge lines.
- 1차 importer는 single-pass read + in-memory edge buffering을 사용한다.
- edge buffer size가 `GraphImportOptions.maxEdgeBufferSize`를 초과하면 import는 `FAILED`로 종료한다.
- large file에서는 edge buffering이 커질 수 있으므로 benchmark에서 memory risk를 관찰한다.
- `graph-io-jackson2`와 `graph-io-jackson3`는 동일 JSON shape를 사용해야 한다.
- Jackson 2/3 round-trip 결과는 같은 logical records를 만들어야 한다.
- JSON shape는 `graph-io/src/testFixtures` 또는 `graph-io/src/test/resources/fixtures/ndjson`에 공통 fixture로 고정한다.

### 8.3 GraphML

1차 지원 subset:

```xml
<graphml xmlns="http://graphml.graphdrawing.org/xmlns">
  <key id="label" for="node" attr.name="label" attr.type="string"/>
  <key id="name" for="node" attr.name="name" attr.type="string"/>
  <key id="since" for="edge" attr.name="since" attr.type="long"/>
  <graph id="G" edgedefault="directed">
    <node id="v1">
      <data key="label">Person</data>
      <data key="name">Alice</data>
    </node>
    <node id="v2">
      <data key="label">Person</data>
      <data key="name">Bob</data>
    </node>
    <edge id="e1" source="v1" target="v2">
      <data key="label">KNOWS</data>
      <data key="since">2024</data>
    </edge>
  </graph>
</graphml>
```

Label mapping:

- GraphML node/edge에는 표준 label attribute가 없으므로 `data key="label"`을 bluetape4k convention으로 사용한다.
- label data가 없으면 `GraphImportOptions.defaultVertexLabel` / `GraphImportOptions.defaultEdgeLabel`을 사용한다.

Unsupported in 1차:

- nested graph
- hyperedge
- port
- undirected graph / undirected edge
- yEd/yFiles custom graphics
- arbitrary complex XML extension in `<data>`

Unsupported data handling:

- unsupported element를 만나면 기본은 `GraphIoFailureSeverity.WARN` failure를 기록하고 skip한다.
- `GraphMlImportOptions.unsupportedElementPolicy = FAIL`이면 `status = FAILED`.
- `edgedefault="undirected"` or `edge directed="false"` is unsupported because `GraphOperations.createEdge` models directed edges.
- Default policy records `WARN`, skips those edges, increments `skippedEdges`, and returns `PARTIAL` if graph data was skipped.

GraphML property type mapping:

| GraphML `attr.type` | Import value type | Export value type |
|---------------------|-------------------|-------------------|
| `boolean` | `Boolean` | `Boolean` |
| `int` | `Int` | `Int`, `Short`, `Byte` when value fits |
| `long` | `Long` | `Long` |
| `float` | `Float` | `Float` |
| `double` | `Double` | `Double`, `BigDecimal` as string fallback if not safely representable |
| `string` | `String` | default for all other types |

Rules:

- Missing `key` declaration or missing `attr.type` defaults to `string`.
- Export emits `key` declarations for `label` and each discovered property key.
- `GraphMlExportOptions` controls graph metadata.

```kotlin
data class GraphMlExportOptions(
    val graphId: String = "G",
    val edgeDefault: GraphMlEdgeDefault = GraphMlEdgeDefault.DIRECTED,
    val labelKey: String = "label",
) : Serializable {
    companion object {
        private const val serialVersionUID: Long = 1L
    }
}

enum class GraphMlEdgeDefault {
    DIRECTED,
}
```

---

## 9. Import Algorithm

1. Validate options and source.
2. Read vertex records.
3. Validate duplicate external IDs.
4. Create vertices via `GraphOperations.createVertex(label, properties)`.
5. Store `externalId -> GraphElementId` mapping.
6. Read edge records.
7. Resolve `fromExternalId` / `toExternalId`.
8. Create edges via `GraphOperations.createEdge(...)`.
9. Return report with counts, elapsed time, and failures.

Data format note:

- CSV: read vertices source first, create vertices and external ID map, then read edges source and create edges.
- GraphML: process elements in document order using StAX streaming.
  - If an edge element is encountered before all referenced node IDs are in the vertex map, buffer that edge record.
  - After all nodes are parsed, flush the edge buffer.
- NDJSON: single-pass read.
  - vertex records are created as encountered.
  - edge records are buffered until all lines are read and all encountered vertices are created.
  - after EOF, flush edge buffer in step 8.
  - if edge buffer exceeds `GraphImportOptions.maxEdgeBufferSize`, stop immediately and return `FAILED`.
  - already-created vertices remain in the graph and must be reported as partial external state in the failure message.

Data integrity note:

- Without transaction DSL, failure after vertex creation can leave partial graph data.
- Report must clearly return `FAILED` or `PARTIAL`.
- README must document that full rollback requires a backend transaction wrapper when available.

Preserving external IDs:

- `preserveExternalIdProperty = "_graphIoExternalId"` by default.
- importer adds this property to vertices unless caller sets it to `null`.
- edge external IDs are preserved as the same property if present.

---

## 10. Export Algorithm

Current `GraphOperations` can list vertices by label and edges by label only when labels are known.
Therefore export requires one of the following:

1. caller provides `vertexLabels` and `edgeLabels`, or
2. implementation has a label discovery strategy.

1차 rule:

- `GraphExportOptions.vertexLabels = emptySet()` means labels are not specified.
- `GraphExportOptions.edgeLabels = emptySet()` means edge labels are not specified.
- 1차 구현에서 empty label set은 fail-fast 한다.
- vertices cannot be exported when `vertexLabels` is empty.
- edges are exported only when `edgeLabels` is non-empty; edge export may still require `vertexLabels` for formats that need endpoint metadata.
- fail-fast message must say: "label discovery is not supported by GraphOperations; provide labels explicitly".
- all-label export semantics is deferred until a backend-neutral label discovery API exists.
- `GraphSuspendOperations` export는 label이 제공된 경우 `Flow`로 streaming write한다.
- Sync/VT export는 현재 repository API가 `List`를 반환하므로 label 단위로 적재한 뒤 즉시 writer로 흘린다.

Future:

- add backend-neutral label discovery API if needed.
- add cursor/chunked sync export API if label-level list loading is too expensive.

---

## 11. File Handling Contract

Use `bluetape4k-io` conventions:

- Path API opens/closes resources internally with `use`.
- stream API does not close caller-owned streams unless `closeInput` / `closeOutput` is true.
- text files default to UTF-8.
- use buffered reader/writer for text.
- do not use full-file `readAllBytes` / `readAllLines` in production bulk path.
- create parent directories for path sinks before writing.
- use safe path helpers if directory/archive source support is added later.

---

## 12. Test Requirements

Common `graph-io`:

- option validation tests
- report status tests
- source/sink ownership tests
- VT adapter success/failure tests
- coroutine adapter success/failure tests
- `Serializable` round-trip smoke tests for public records/options/reports

CSV:

- import vertices + edges from two files
- export vertices + edges to two files
- export union headers when same-label vertices/edges have different property keys
- duplicate vertex ID failure
- missing endpoint failure
- `PrefixedColumns` property mode round-trip
- property column collision failure
- `RawJsonColumn` CSV-only string preservation
- coroutine `Flow` reader/writer path
- VT adapter path

Jackson 2:

- NDJSON import/export round-trip
- nested property map round-trip
- `maxEdgeBufferSize` overflow returns `FAILED` and documents already-created vertices
- malformed line failure includes line number
- Jackson 2 output can be read by Jackson 3 logical parser

Jackson 3:

- same as Jackson 2
- Jackson 3 output can be read by Jackson 2 logical parser

GraphML:

- basic node/edge import/export
- key/data property round-trip
- missing label default
- unsupported nested graph behavior
- strict mode failure
- malformed XML failure
- location field includes element path or StAX line/column

Integration:

- TinkerGraph round-trip for all formats.
- Same generated graph exported/imported across CSV, Jackson 2 NDJSON, Jackson 3 NDJSON, GraphML.
- `preserveExternalIdProperty = null` does not add external ID properties during import.

KDoc:

- all public APIs must have Korean KDoc.

---

## 13. Benchmark Requirements

Add benchmark class under `graph-io-benchmark`:

```text
graph-io-benchmark/src/main/kotlin/io/bluetape4k/graph/io/benchmark/
  BulkGraphIoBenchmark.kt
  BulkGraphIoBenchmarkState.kt
```

Dataset sizes:

| Size | Vertices | Edges |
|------|----------|-------|
| small | 1,000 | 2,000 |
| medium | 10,000 | 50,000 |
| large | 100,000 | 500,000 |

Formats:

- CSV
- NDJSON Jackson 2
- NDJSON Jackson 3
- GraphML

Operations:

- export
- import
- round-trip

Execution models:

- Sync
- Virtual Threads
- Coroutines

Report file:

```text
docs/benchmark/2026-04-18-graph-io-bulk-results.md
```

Report table:

| Format | API | Vertices | Edges | Export ms | Import ms | Round-trip ms | Output bytes | Notes |
|--------|-----|----------|-------|-----------|-----------|---------------|--------------|-------|

Also update:

```text
docs/graphdb-tradeoffs.md
```

Required benchmark notes:

- machine/JDK/Gradle command
- warmup/iteration settings
- backend: `TinkerGraphOperations` for Sync/VT, `TinkerGraphSuspendOperations` for Coroutines
- `graph-io-benchmark` must depend on `graph-io`, `graph-io-csv`, `graph-io-jackson2`, `graph-io-jackson3`, `graph-io-graphml`
- whether large dataset completed
- memory or timeout failures
- peak heap estimate or observed max heap when available
- edge buffer hit/miss for NDJSON

---

## 14. README Requirements

Each new module must include `README.md` and `README.ko.md`.

Required sections:

- module purpose
- dependency snippet
- Sync example
- Virtual Threads example
- Coroutines example
- format schema
- failure/partial import behavior
- benchmark result link
- known limitations

Root README updates:

- module matrix
- artifact list
- quick start for CSV, NDJSON Jackson2/Jackson3, GraphML

TODO update:

- mark `graph-io` complete only after implementation, benchmark, README, tests pass.

---

## 15. Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Backend cannot preserve imported IDs | Edge creation needs generated IDs | Maintain externalId to GraphElementId map; optionally preserve external ID as property |
| `externalId -> GraphElementId` map memory | large dataset에서 heap pressure | benchmark에서 100K/500K 기준 heap 관찰; 추후 disk-backed map 검토 |
| No transaction DSL yet | Partial mutations on failure | Report `FAILED`/`PARTIAL`; document rollback limitation |
| NDJSON edges require vertex map | Large edge buffering risk | Prefer vertices-first files; document memory risk; benchmark large data |
| CSV union header pre-scan | export needs one pass before writing headers | Use stable union headers in v1; benchmark heap; future schema-provided mode may avoid pre-scan |
| property scalar type fidelity | cross-format round-trip may change JVM scalar classes | compare logical values in tests; document format-specific coercion rules |
| CSV property JSON would pull Jackson into CSV module | dependency coupling | Default to prefixed columns; JSON property column extension belongs to JSON modules |
| Jackson 2/3 API conflict | compile/runtime confusion | Separate modules and package imports |
| GraphML full spec is broad | scope creep | Support explicit subset and strict mode |
| Export all labels requires discovery | current GraphOperations has no label discovery | emptySet means labels are unspecified in v1 and fail-fast; all-label export waits for label discovery API |
| Sync export loads label result lists | large label can pressure heap | document 1차 limit; coroutine export uses Flow; future chunked sync API |

---

## 16. Implementation Conventions

- All public APIs must have Korean KDoc.
- Public records/options/reports/failures implement `Serializable` and define `serialVersionUID`.
- Normal importer/exporter classes use `companion object : KLogging()`.
- Coroutine-heavy importer/exporter classes use `companion object : KLoggingChannel()`.
- Logs use lazy logging:
  - `debug`: start/end with counts and elapsed time
  - `info`: benchmark/example command output only, not normal library path
  - `warn`: partial import/export and skipped unsupported GraphML element
  - `error`: failed import/export with failure count
- Validation uses bluetape4k helpers such as `requireNotBlank`, `requirePositiveNumber`, and `requireZeroOrPositiveNumber`.

---

## 17. Draft Task List

1. `graph-io` common module scaffold, model, options, reports, Sync/VT/Coroutine contracts.
2. Register `graph-io`, `graph-io-csv`, `graph-io-jackson2`, `graph-io-jackson3`, `graph-io-graphml`, `graph-io-benchmark` in `settings.gradle.kts`.
3. `graph-io` file/stream source-sink helpers using `bluetape4k-io`.
4. `graph-io-csv` Sync importer/exporter using `bluetape4k-csv`.
5. `graph-io-csv` VT and Coroutine adapters/tests.
6. `graph-io-jackson2` NDJSON importer/exporter using `bluetape4k-jackson2`.
7. `graph-io-jackson3` NDJSON importer/exporter using `bluetape4k-jackson3`.
8. Jackson 2/3 compatibility tests for logical NDJSON shape.
9. `graph-io-graphml` StAX importer/exporter for supported GraphML subset.
10. Cross-format TinkerGraph round-trip tests.
11. `graph-io-benchmark` dependencies for all `graph-io-*` modules.
12. `graph-io-benchmark` bulk IO benchmark suite.
13. Run benchmark and write `docs/benchmark/2026-04-18-graph-io-bulk-results.md`.
14. Update module READMEs, root README, TODO, and `docs/graphdb-tradeoffs.md`.
15. Run compile/test/static checks.
