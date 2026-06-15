# graph-io-csv

**bluetape4k-graph** 을 위한 CSV 포맷 벌크 임포터/익스포터. 그래프 정점과 간선을 CSV 파일로 원활하게 내보낼 수 있으며, 동기, 가상 스레드, Kotlin 코루틴 기반 suspend의 세 가지 실행 모델을 지원합니다.

## 기능

- **유연한 실행 모델**
  - **동기 (`CsvGraphBulkExporter`)**: 블로킹 I/O, 간단한 스크립트 및 배치 작업에 적합
  - **가상 스레드 (`CsvGraphVirtualThreadBulkExporter`)**: Java 가상 스레드를 통한 비동기, 가벼운 동시성
  - **Suspend (`SuspendCsvGraphBulkExporter`)**: Kotlin 코루틴 기반, `suspend` 함수를 통한 구조화된 동시성

- **속성 처리 모드**
  - `PrefixedColumns`: 속성을 접두사가 붙은 별도 컬럼으로 저장 (예: `prop.name`, `prop.age`)
  - `RawJsonColumn`: 설정한 단일 컬럼에 JSON 속성 payload 저장
  - `None`: 속성 완전 제외

- **자동 스키마 합치기**: 헤더 생성이 레코드 전체의 모든 속성 키를 자동으로 발견

- **포괄적 보고**: 익스포트 보고서는 정점/간선 개수, 실행 시간, 상세 실패 추적을 포함

## 의존성

`build.gradle.kts`에 다음을 추가하세요:

```kotlin
dependencies {
    implementation("io.bluetape4k:graph-io-csv:$version")
}
```

## 아키텍처

![graph-io-csv architecture](../../docs/images/readme-diagrams/graph-io-csv-architecture-01.png)

CSV 모듈은 정점과 간선을 한 파일에 섞지 않고, 두 CSV 파일을 한 쌍으로 다룹니다:

- `vertices.csv`는 `id`, `label`, 선택적 속성 컬럼을 저장합니다.
- `edges.csv`는 `id`, `label`, `from`, `to`, 선택적 속성 컬럼을 저장합니다.
- `CsvRecordCodec`은 import/export 양쪽에서 union header 생성과 속성 추출을 담당합니다.
- Import는 2-pass 방식입니다. 먼저 정점으로 외부 ID 맵을 만든 뒤, 간선의 `from`/`to`를 해석합니다.
- 동기, 가상 스레드, suspend API는 같은 CSV 계약을 공유하고 실행 모델만 다릅니다.

## 사용법

### 동기식 익스포트

블로킹 I/O를 사용하여 그래프를 CSV 파일로 익스포트:

```kotlin
import io.bluetape4k.graph.io.csv.CsvGraphBulkExporter
import io.bluetape4k.graph.io.csv.CsvGraphExportSink
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.repository.GraphOperations
import java.nio.file.Paths

val exporter = CsvGraphBulkExporter()

val sink = CsvGraphExportSink(
    vertices = GraphExportSink.PathSink(Paths.get("vertices.csv")),
    edges = GraphExportSink.PathSink(Paths.get("edges.csv")),
)

val options = GraphExportOptions(
    vertexLabels = setOf("Person", "Company"),
    edgeLabels = setOf("works_for", "knows"),
)

val report = exporter.exportGraph(sink, graphOps, options)
println("${report.verticesWritten}개의 정점과 ${report.edgesWritten}개의 간선을 ${report.elapsed.toMillis()}ms에 익스포트했습니다")
```

### 가상 스레드 기반 익스포트

Java 가상 스레드를 사용하여 비동기로 익스포트:

```kotlin
import io.bluetape4k.graph.io.csv.CsvGraphVirtualThreadBulkExporter
import io.bluetape4k.graph.io.csv.CsvGraphExportSink
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.source.GraphExportSink
import java.nio.file.Paths

val exporter = CsvGraphVirtualThreadBulkExporter()

val sink = CsvGraphExportSink(
    vertices = GraphExportSink.PathSink(Paths.get("vertices.csv")),
    edges = GraphExportSink.PathSink(Paths.get("edges.csv")),
)

val options = GraphExportOptions(
    vertexLabels = setOf("Person"),
    edgeLabels = setOf("knows"),
)

val future = exporter.exportGraphAsync(sink, graphOps, options)
val report = future.join()  // 완료 대기
println("${report.verticesWritten}개의 정점을 익스포트했습니다")
```

### 코루틴 기반 익스포트 (Suspend)

Kotlin 코루틴을 사용하여 구조화된 동시성으로 익스포트:

```kotlin
import io.bluetape4k.graph.io.csv.SuspendCsvGraphBulkExporter
import io.bluetape4k.graph.io.csv.CsvGraphExportSink
import io.bluetape4k.graph.io.csv.CsvGraphIoOptions
import io.bluetape4k.graph.io.csv.CsvPropertyMode
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.source.GraphExportSink
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths

val exporter = SuspendCsvGraphBulkExporter()

val sink = CsvGraphExportSink(
    vertices = GraphExportSink.PathSink(Paths.get("vertices.csv")),
    edges = GraphExportSink.PathSink(Paths.get("edges.csv")),
)

val options = GraphExportOptions(
    vertexLabels = setOf("Person", "Company"),
    edgeLabels = setOf("works_for"),
)

val csvOptions = CsvGraphIoOptions(
    propertyMode = CsvPropertyMode.PrefixedColumns(prefix = "attr."),
)

val report = runBlocking {
    exporter.exportGraphSuspending(sink, suspendGraphOps, options, csvOptions)
}
println("${report.verticesWritten}개의 정점과 ${report.edgesWritten}개의 간선을 익스포트했습니다")
```

## 임포트

CSV 파일에서 그래프를 임포트합니다. 정점과 간선 CSV 파일은 각각 별도의 파일로 제공해야 합니다.

### 동기식 임포트

```kotlin
import io.bluetape4k.graph.io.csv.CsvGraphBulkImporter
import io.bluetape4k.graph.io.csv.CsvGraphImportSource
import io.bluetape4k.graph.io.options.DuplicateVertexPolicy
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.options.MissingEndpointPolicy
import io.bluetape4k.graph.io.source.GraphImportSource
import java.nio.file.Paths

val importer = CsvGraphBulkImporter()

val source = CsvGraphImportSource(
    vertices = GraphImportSource.PathSource(Paths.get("vertices.csv")),
    edges    = GraphImportSource.PathSource(Paths.get("edges.csv")),
)

val options = GraphImportOptions(
    defaultVertexLabel    = "Node",
    onDuplicateVertexId   = DuplicateVertexPolicy.SKIP,
    onMissingEdgeEndpoint = MissingEndpointPolicy.SKIP_EDGE,
)

val report = importer.importGraph(source, graphOps, options)
println("${report.verticesCreated}/${report.verticesRead}개의 정점과 " +
        "${report.edgesCreated}/${report.edgesRead}개의 간선을 임포트했습니다: ${report.status}")
```

### 임포트 CSV 형식

정점 CSV 파일:

```csv
id,label,prop.name,prop.age
v1,Person,Alice,30
v2,Person,Bob,25
```

간선 CSV 파일:

```csv
id,label,from,to,prop.since
,KNOWS,v1,v2,2024
```

### 임포트 옵션

| 옵션 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `defaultVertexLabel` | `String` | `"Vertex"` | `label` 컬럼이 비어 있을 때 사용하는 기본 정점 레이블 |
| `defaultEdgeLabel` | `String` | `"Edge"` | `label` 컬럼이 비어 있을 때 사용하는 기본 간선 레이블 |
| `onDuplicateVertexId` | `DuplicateVertexPolicy` | `FAIL` | 중복 정점 ID 처리: 즉시 실패하거나 중복을 건너뜀 |
| `onMissingEdgeEndpoint` | `MissingEndpointPolicy` | `FAIL` | 누락된 간선 끝점 처리: 즉시 실패하거나 해당 간선을 건너뜀 |
| `preserveExternalIdProperty` | `String?` | `null` | 외부 ID를 속성으로 보존할 때 사용할 키 |

## 설정

### 속성 모드

그래프 속성이 CSV에서 직렬화되는 방식을 구성:

#### 접두사 컬럼 (기본값)

속성이 설정 가능한 접두사가 붙은 별도 컬럼으로 나타남:

```kotlin
val options = CsvGraphIoOptions(
    propertyMode = CsvPropertyMode.PrefixedColumns(prefix = "prop.")
)
// 컬럼: id, label, prop.name, prop.age, prop.email, ...
```

#### Raw JSON 컬럼

설정한 단일 컬럼에 JSON 속성 payload를 저장:

```kotlin
val options = CsvGraphIoOptions(
    propertyMode = CsvPropertyMode.RawJsonColumn(columnName = "attributes")
)
// 컬럼: id, label, attributes (JSON 값 포함)
```

#### 없음

속성 완전 제외:

```kotlin
val options = CsvGraphIoOptions(
    propertyMode = CsvPropertyMode.None
)
// 컬럼: id, label만 포함
```

## 익스포트 보고서

익스포트 후 요약 통계 및 오류 세부 정보를 확인하려면 보고서를 검사하세요:

```kotlin
val report = exporter.exportGraph(sink, graphOps, options)

println("상태: ${report.status}")  // COMPLETED, PARTIAL, FAILED
println("정점: ${report.verticesWritten}")
println("간선: ${report.edgesWritten}")
println("소요 시간: ${report.elapsed.toMillis()}ms")

if (report.failures.isNotEmpty()) {
    report.failures.forEach { failure ->
        println("오류[${failure.phase}]: ${failure.message}")
    }
}
```

## 성능 고려사항

- **동기**: 소규모 데이터셋 또는 단순성을 선호할 때 최적
- **가상 스레드**: 최소한의 스레드 오버헤드로 중간 동시성에 이상적
- **Suspend**: 논블로킹 I/O 및 구조화된 동시성으로 대규모 작업에 최적

워크로드에 따라 선택하세요:
- **소규모 데이터셋** (<100K 레코드): 동기 사용
- **중간~대규모** (100K–1M 레코드): 가상 스레드 또는 suspend 사용
- **높은 동시성** 환경: 코루틴 감시자와 함께 suspend 사용
