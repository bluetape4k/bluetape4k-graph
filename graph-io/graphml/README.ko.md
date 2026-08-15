# graph-io-graphml

[English](README.md) | 한국어

StAX 스트리밍 파서를 이용한 GraphML (XML) 대량 임포터 및 익스포터.

## 개요

`graph-io-graphml` 모듈은 GraphML 형식의 그래프 데이터를 임포트하고 익스포트하기 위한 세 가지 실행 모델을 제공합니다:

1. **동기 API** - 간단한 사용 사례를 위한 블로킹 연산
2. **코루틴 Suspension API** - `suspend` 함수를 이용한 비동기/대기 처리
3. **Virtual Thread API** - Java 21+ 가상 스레드를 이용한 스레드-당-작업 실행

모든 구현은 대용량 GraphML 파일의 메모리 효율적인 파싱과 쓰기를 위해 StAX (Streaming API for XML)를 사용합니다.

## 아키텍처

![graph-io-graphml architecture](../../docs/images/readme-diagrams/graph-io-graphml-architecture-01.png)

`graph-io-graphml`은 directed GraphML property-graph subset을 캐시된 StAX reader/writer로 변환합니다:

- `StaxGraphMlReader`는 `<key>`, `<node>`, `<edge>`, scalar `<data>`를 graph-io record로 파싱합니다.
- Import는 vertex를 먼저 생성하고 external ID를 기록한 뒤 directed edge를 해석합니다.
- `StaxGraphMlWriter`는 key definition을 먼저 쓰고 graph, node, edge, data element를 씁니다.
- `UnsupportedGraphMlElementPolicy`는 미지원 construct를 warning으로 남길지 import 실패로 처리할지 결정합니다.
- 동기, virtual-thread, suspend API는 같은 XML contract를 공유합니다.

## 기능

- **StAX 기반 스트리밍**: 메모리 효율적인 파싱 및 직렬화
- **GraphML subset 지원**: directed graph, node, edge, scalar data를 처리하고 미지원 construct를 명시적으로 리포팅
- **세 가지 실행 모델**: 동기, 비동기, 가상 스레드 변형
- **상세한 임포트 리포트**: 단계(phase)와 심각도(severity)를 포함한 종합 실패 리포팅
- **유연한 설정**: 속성 이름, 기본 레이블, 오류 처리 정책 커스터마이징 가능
- **대량 연산 최적화**: 대규모 그래프 임포트/익스포트에 최적화됨

## 설치

`build.gradle.kts`에 의존성을 추가하세요:

```kotlin
dependencies {
    implementation("io.bluetape4k:graph-io-graphml:$version")
}
```

## 사용 예제

### 동기식 임포트

```kotlin
import io.bluetape4k.graph.io.graphml.GraphMlBulkImporter
import io.bluetape4k.graph.io.graphml.GraphMlImportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.repository.GraphOperations
import java.nio.file.Paths

val importer = GraphMlBulkImporter()
val source = GraphImportSource.PathSource(Paths.get("data.graphml"))
val ops: GraphOperations = /* 그래프 연산 인스턴스 */

val report = importer.importGraph(
    source = source,
    operations = ops,
    options = GraphImportOptions(),
    graphMlOptions = GraphMlImportOptions(
        labelAttrName = "label",
        defaultVertexLabel = "Vertex",
        defaultEdgeLabel = "EDGE"
    )
)

println("임포트 완료: ${report.verticesCreated}/${report.verticesRead} 정점, " +
        "${report.edgesCreated}/${report.edgesRead} 간선")
println("상태: ${report.status}")
```

### 코루틴 기반 임포트

```kotlin
import io.bluetape4k.graph.io.graphml.SuspendGraphMlBulkImporter
import io.bluetape4k.graph.io.graphml.GraphMlImportOptions
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.source.GraphImportSource
import io.bluetape4k.graph.repository.GraphSuspendOperations
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths

val importer = SuspendGraphMlBulkImporter()
val source = GraphImportSource.PathSource(Paths.get("data.graphml"))
val ops: GraphSuspendOperations = /* 그래프 suspend 연산 인스턴스 */

val report = runBlocking {
    importer.importGraphSuspending(
        source = source,
        operations = ops,
        options = GraphImportOptions(),
        graphMlOptions = GraphMlImportOptions()
    )
}

println("임포트 상태: ${report.status}")
if (report.failures.isNotEmpty()) {
    report.failures.forEach { failure ->
        println("${failure.phase}: ${failure.message} (심각도: ${failure.severity})")
    }
}
```

### Virtual Thread 익스포트

```kotlin
import io.bluetape4k.graph.io.graphml.GraphMlVirtualThreadBulkExporter
import io.bluetape4k.graph.io.graphml.GraphMlExportOptions
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.source.GraphExportSink
import io.bluetape4k.graph.repository.GraphOperations
import java.nio.file.Paths

val exporter = GraphMlVirtualThreadBulkExporter()
val sink = GraphExportSink.PathSink(Paths.get("output.graphml"))
val ops: GraphOperations = /* 그래프 연산 인스턴스 */

val future = exporter.exportGraphAsync(
    sink = sink,
    operations = ops,
    options = GraphExportOptions(
        vertexLabels = setOf("Person", "Company"),
        edgeLabels = setOf("KNOWS", "WORKS_AT")
    ),
    graphMlOptions = GraphMlExportOptions()
)
val report = future.join()

println("${report.verticesWritten}개 정점과 ${report.edgesWritten}개 간선을 익스포트했습니다")
```

### 동기식 익스포트

```kotlin
import io.bluetape4k.graph.io.graphml.GraphMlBulkExporter
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.source.GraphExportSink
import java.nio.file.Paths

val exporter = GraphMlBulkExporter()
val report = exporter.exportGraph(
    sink = GraphExportSink.PathSink(Paths.get("graph.graphml")),
    operations = ops,
    options = GraphExportOptions(
        vertexLabels = setOf("Person"),
        edgeLabels = setOf("KNOWS")
    )
)
```

## 설정

### 임포트 옵션

`GraphMlImportOptions`로 임포트 동작을 커스터마이징할 수 있습니다:

```kotlin
data class GraphMlImportOptions(
    val labelAttrName: String = "label",                          // 노드/간선 레이블로 사용할 속성 이름
    val unsupportedElementPolicy: UnsupportedGraphMlElementPolicy = UnsupportedGraphMlElementPolicy.SKIP,
    val defaultVertexLabel: String = "Vertex",                    // 명시적 레이블 없는 정점의 기본 레이블
    val defaultEdgeLabel: String = "EDGE"                         // 명시적 레이블 없는 간선의 기본 레이블
)
```

지원하는 import subset:

- `<node>`, `<edge>`, scalar `<data>` 자식을 가진 directed `<graph>` 문서.
- scalar GraphML attribute type을 사용하는 `key` 정의.
- undirected graph, undirected edge, nested graph, port, hyperedge 같은 미지원 GraphML construct는 import failure 목록에 기록됩니다.
- `UnsupportedGraphMlElementPolicy.SKIP`은 `WARN` failure를 기록하고 지원되는 subset 처리를 계속합니다.
- `UnsupportedGraphMlElementPolicy.FAIL`은 `ERROR` failure를 기록하며 bulk importer는 graph element를 생성하지 않고 `FAILED`를 반환합니다.

### Property-graph subset 이후의 compatibility contract

| GraphML construct | 결정 | Import 동작 | 근거 |
|---|---|---|---|
| node, edge, scalar data, scalar key를 가진 directed graph | 구현됨 | Import/export 지원 | `GraphVertex`, directed `GraphEdge`, scalar property에 직접 대응됩니다. |
| Graph-level `edgedefault="undirected"` | 보류 | `SKIP`은 `WARN` 기록, `FAIL`은 write 전 `FAILED` 반환 | `GraphEdge`는 directed입니다. reverse edge를 자동 생성하면 edge 수와 traversal 의미가 바뀝니다. |
| Edge-level `directed="false"` | 보류 | `SKIP`은 `WARN` 기록 후 source-to-target projection 유지, `FAIL`은 write 전 `FAILED` 반환 | projection은 진단용 import에는 유용하지만 충실한 undirected-edge contract는 아닙니다. |
| Nested `<graph>` | 이번 slice에서는 거부 | `SKIP`은 `WARN` 기록 후 nested content skip, `FAIL`은 write 전 `FAILED` 반환 | `GraphVertex`에는 child graph scope가 없습니다. Flattening에는 명시적 ownership mapping 설계가 필요합니다. |
| `<hyperedge>` | 거부 | `SKIP`은 `WARN` 기록, `FAIL`은 write 전 `FAILED` 반환 | `GraphEdge`는 source 하나와 target 하나만 가집니다. Hyperedge 지원에는 reification node 정책이 필요합니다. |
| `<port>` | 보류 | `SKIP`은 `WARN` 기록, `FAIL`은 write 전 `FAILED` 반환 | port는 endpoint metadata이지만 현재 backend-neutral endpoint는 vertex만 가리킵니다. |
| yFiles graphics 같은 XML extension payload | 보류 | 지원 contract 밖 | 시각 metadata를 보존하려면 namespaced extension-property 정책이 먼저 필요합니다. |

`src/test/resources/fixtures/graphml/`의 대표 fixture가 permissive/strict import policy 양쪽의 contract를 고정합니다.

### 익스포트 옵션

`GraphMlExportOptions`는 생성되는 GraphML metadata와 label data key를 제어합니다:

```kotlin
data class GraphMlExportOptions(
    val labelAttrName: String = "label",
    val edgeDefault: GraphMlEdgeDefault = GraphMlEdgeDefault.DIRECTED,
    val graphId: String = "G",
    val encoding: String = "UTF-8",
) : Serializable
```

## 성능 참고 사항

### XMLFactory 캐싱 (중요)

`XMLInputFactory`와 `XMLOutputFactory` 인스턴스는 생성 비용이 많이 듭니다. 모듈은 내부적으로 싱글톤 인스턴스를 유지하여 최적의 성능을 보장합니다. **각 연산마다 새로운 인스턴스를 생성하지 마세요.**

`StaxGraphMlReader`와 `StaxGraphMlWriter` 클래스는 비용이 많이 드는 초기화 오버헤드를 피하기 위해 캐시된 팩토리를 사용합니다.

### 메모리 효율성

StAX 스트리밍 접근 방식은 XML을 증분적으로 처리하므로 DOM 기반 파서로는 메모리에 맞지 않는 대용량 GraphML 파일에 적합합니다.

GraphML export는 정점과 간선을 조회할 때 `GraphExportOptions.exportChunkSize`를 사용합니다. 첫 번째 node 또는 edge보다 먼저 전역 `<key>` 정의를 써야 하므로 exporter는 bounded pre-scan에서 property key 이름만 보관한 뒤 같은 chunk를 다시 읽어 기록합니다. 전체 정점/간선 record list를 materialize하지 않으며, 그 대신 bounded repository pass가 한 번 더 필요합니다.

## 오류 처리

임포트 연산은 다음을 포함하는 상세한 `GraphImportReport`를 반환합니다:

- **상태 (Status)**: COMPLETED, PARTIAL, 또는 FAILED
- **실패 (Failures)**: 다음을 포함하는 `GraphIoFailure` 객체 목록:
  - Phase: READ_GRAPH, CREATE_VERTEX, CREATE_EDGE, READ_EDGE
  - Severity: INFO, WARN, ERROR
  - Message: 설명적인 오류 메시지
  - RecordId: 문제가 있는 레코드의 ID

실패는 빠르게 중단하는 대신 수집되고 리포팅되어 부분 임포트가 완료될 수 있도록 합니다.

## 구현 세부 사항

- `GraphMlBulkImporter` / `GraphMlBulkExporter`: 동기 구현
- `SuspendGraphMlBulkImporter` / `SuspendGraphMlBulkExporter`: `Dispatchers.IO`를 사용한 코루틴 기반 구현
- `GraphMlVirtualThreadBulkImporter` / `GraphMlVirtualThreadBulkExporter`: Java 21+ 가상 스레드 구현
- `StaxGraphMlReader` / `StaxGraphMlWriter`: 저수준 스트리밍 XML 처리

## 의존성

- `graph-io-core`: 핵심 그래프 I/O 인터페이스 및 모델
- `bluetape4k-coroutines`: 코루틴 유틸리티
- `bluetape4k-virtualthread`: Java 21+ Virtual Thread 지원

## 스트리밍 reader 계약

`GraphMlRecordFlowReader`는 cold `Flow`를 통해 node와 edge를 소스 순서대로 파싱합니다. StAX event는 점진적으로
전달되며 production import는 전체 vertex/edge list를 materialize하지 않고 간선 staging을 `maxEdgeBufferSize`로
제한합니다. `GraphImportOptions.batchSize`는 백엔드 쓰기 플러시만 제어합니다. Path와 소유권을 넘긴 input은
라이브러리가 닫고 호출자 소유 input은 열린 상태로 둡니다.
