# graph-io-jackson2

Jackson 2.x를 사용한 NDJSON(Newline-Delimited JSON) 그래프 데이터 벌크 임포터/익스포터.

## 개요

`graph-io-jackson2`는 NDJSON 형식을 이용한 고성능 그래프 데이터 임포트/익스포트를 제공합니다. 각 줄이 정점(vertex) 또는 간선(edge)을 나타내는 완전한 JSON 객체이므로 전체 데이터를 메모리에 로드하지 않고 스트리밍 방식으로 대용량 그래프를 처리할 수 있습니다.

## 기능

### 세 가지 실행 모델

1. **동기 API** (`Jackson2NdJsonBulkImporter`, `Jackson2NdJsonBulkExporter`)
   - 블로킹 I/O 작업
   - 간단한 순차 처리
   - 소규모~중규모 그래프 또는 단순성이 필요한 경우에 최적

2. **가상 스레드 API** (`Jackson2NdJsonVirtualThreadBulkImporter`, `Jackson2NdJsonVirtualThreadBulkExporter`)
   - Java 21+ 가상 스레드를 활용한 경량 동시성
   - 수천 개의 동시 I/O 작업으로 확장 가능
   - CPU 제약이 있는 시스템에서 높은 I/O 워크로드에 이상적

3. **코루틴 서스펜드 API** (`SuspendJackson2NdJsonBulkImporter`, `SuspendJackson2NdJsonBulkExporter`)
   - Kotlin 코루틴을 이용한 논블로킹 작업
   - 기존 async/await 코드와 완벽 통합
   - 코루틴을 이미 사용 중인 애플리케이션에 최적

### NDJSON 엔벨로프 형식

모듈은 정점과 간선 모두에 대해 구조화된 JSON 엔벨로프 형식을 사용합니다:

```json
{"type": "vertex", "id": "v1", "label": "Person", "properties": {"name": "Alice", "age": 30}}
{"type": "edge", "id": "e1", "label": "KNOWS", "from": "v1", "to": "v2", "properties": {"since": 2020}}
```

**엔벨로프 필드:**
- `type` (string) - "vertex" 또는 "edge"
- `id` (string) - 임포트/익스포트 내에서의 고유 식별자
- `label` (string) - 정점 또는 간선 라벨
- `from` (string) - 간선의 경우: 출발 정점 외부 ID
- `to` (string) - 간선의 경우: 도착 정점 외부 ID
- `properties` (object) - 키-값 속성 (선택사항, 기본값: 빈 객체)

### 간선 버퍼링

임포트 중 간선을 버퍼에 쌓아 참조되는 모든 정점이 먼저 생성되도록 보장합니다:
- 정점은 즉시 처리 및 생성
- 간선은 버퍼에 누적 (기본 최대 크기: 10,000)
- 모든 정점이 커밋된 후 간선 생성
- 일관성을 개선하고 정점 그래프 원자적 구성을 가능하게 함

## 사용 예시

### 동기 임포트

```kotlin
val importer = Jackson2NdJsonBulkImporter()
val source = GraphImportSource.fromFile(Path("graph-data.ndjson"))
val options = GraphImportOptions(
    defaultVertexLabel = "Node",
    defaultEdgeLabel = "Link",
    maxEdgeBufferSize = 10000,
    onDuplicateVertexId = DuplicatePolicy.SKIP
)

val report = importer.importGraph(source, operations, options)
println("임포트 완료: 정점 ${report.verticesCreated}개, 간선 ${report.edgesCreated}개")
println("상태: ${report.status}")
```

### 동기 익스포트

```kotlin
val exporter = Jackson2NdJsonBulkExporter()
val sink = GraphExportSink.toFile(Path("output.ndjson"))
val options = GraphExportOptions(
    vertexLabels = listOf("Person", "Company"),
    edgeLabels = listOf("KNOWS", "WORKS_AT")
)

val report = exporter.exportGraph(sink, operations, options)
println("익스포트 완료: 정점 ${report.verticesWritten}개, 간선 ${report.edgesWritten}개")
```

### 코루틴 기반 임포트

```kotlin
val importer = SuspendJackson2NdJsonBulkImporter()
val source = GraphImportSource.fromFile(Path("graph-data.ndjson"))

val report = importer.importGraphSuspend(source, operations, options)
println("임포트 완료: ${report.status}")
```

### 가상 스레드 임포트

```kotlin
val importer = Jackson2NdJsonVirtualThreadBulkImporter()
val report = importer.importGraph(source, operations, options)
// 가상 스레드를 활용한 효율적인 동시 I/O
```

## 에러 처리

모듈은 `GraphImportReport`와 `GraphExportReport`를 통해 포괄적인 에러 보고를 제공합니다:

```kotlin
val report = importer.importGraph(source, operations, options)

if (report.status == GraphIoStatus.FAILED) {
    report.failures.forEach { failure ->
        println("${failure.phase}: ${failure.message}")
        println("  위치: ${failure.location}")
        println("  심각도: ${failure.severity}")
    }
}
```

## 의존성

`build.gradle.kts`에 다음을 추가하세요:

```kotlin
dependencies {
    implementation("io.bluetape4k:graph-io-jackson2:1.0.0")
}
```

## 요구사항

- **Kotlin** 2.0+
- **Java** 21+
- Jackson 2.17+
- 코루틴 (서스펜드 API용)

## 관련 모듈

- `graph-io-core` - 핵심 I/O 추상화 및 인터페이스
- `graph-neo4j` - Neo4j 그래프 작업
- `graph-tinkerpop` - TinkerPop/Gremlin 지원
- `graph-age` - Apache AGE 지원
