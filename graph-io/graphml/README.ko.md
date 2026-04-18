# graph-io-graphml

StAX 스트리밍 파서를 이용한 GraphML (XML) 대량 임포터 및 익스포터.

## 개요

`graph-io-graphml` 모듈은 GraphML 형식의 그래프 데이터를 임포트하고 익스포트하기 위한 세 가지 실행 모델을 제공합니다:

1. **동기 API** - 간단한 사용 사례를 위한 블로킹 연산
2. **코루틴 Suspension API** - `suspend` 함수를 이용한 비동기/대기 처리
3. **Virtual Thread API** - Java 21+ 가상 스레드를 이용한 스레드-당-작업 실행

모든 구현은 대용량 GraphML 파일의 메모리 효율적인 파싱과 쓰기를 위해 StAX (Streaming API for XML)를 사용합니다.

## 기능

- **StAX 기반 스트리밍**: 메모리 효율적인 파싱 및 직렬화
- **GraphML 2.4 표준 지원**: GraphML 사양과의 완벽한 호환성
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

val importer = GraphMlBulkImporter()
val source = GraphImportSource.fromFile("data.graphml")
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

val importer = SuspendGraphMlBulkImporter()
val source = GraphImportSource.fromFile("data.graphml")
val ops: GraphSuspendOperations = /* 그래프 suspend 연산 인스턴스 */

val report = importer.importGraphSuspending(
    source = source,
    operations = ops,
    options = GraphImportOptions(),
    graphMlOptions = GraphMlImportOptions()
)

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

val exporter = GraphMlVirtualThreadBulkExporter()
val sink = GraphExportSink.toFile("output.graphml")
val ops: GraphOperations = /* 그래프 연산 인스턴스 */

val report = exporter.exportGraph(
    sink = sink,
    operations = ops,
    options = GraphExportOptions(
        vertexLabels = listOf("Person", "Company"),
        edgeLabels = listOf("KNOWS", "WORKS_AT")
    ),
    graphMlOptions = GraphMlExportOptions()
)

println("${report.verticesWritten}개 정점과 ${report.edgesWritten}개 간선을 익스포트했습니다")
```

### 동기식 익스포트

```kotlin
import io.bluetape4k.graph.io.graphml.GraphMlBulkExporter

val exporter = GraphMlBulkExporter()
val report = exporter.exportGraph(
    sink = GraphExportSink.toFile("graph.graphml"),
    operations = ops,
    options = GraphExportOptions(
        vertexLabels = listOf("Person"),
        edgeLabels = listOf("KNOWS")
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

### 익스포트 옵션

`GraphMlExportOptions`는 현재 비어 있지만 향후 기능 확장을 위한 확장 포인트를 제공합니다:

```kotlin
data class GraphMlExportOptions : Serializable
```

## 성능 참고 사항

### XMLFactory 캐싱 (중요)

`XMLInputFactory`와 `XMLOutputFactory` 인스턴스는 생성 비용이 많이 듭니다. 모듈은 내부적으로 싱글톤 인스턴스를 유지하여 최적의 성능을 보장합니다. **각 연산마다 새로운 인스턴스를 생성하지 마세요.**

`StaxGraphMlReader`와 `StaxGraphMlWriter` 클래스는 비용이 많이 드는 초기화 오버헤드를 피하기 위해 캐시된 팩토리를 사용합니다.

### 메모리 효율성

StAX 스트리밍 접근 방식은 XML을 증분적으로 처리하므로 DOM 기반 파서로는 메모리에 맞지 않는 대용량 GraphML 파일에 적합합니다.

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
