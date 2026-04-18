# graph-io-jackson3

Bluetape4k 그래프 연산을 위한 Jackson 3.x 기반 NDJSON 벌크 임포터/익스포터.

## 개요

이 모듈은 **Jackson 3.x** (`tools.jackson` 패키지 네임스페이스)를 사용하여 고성능의 유연한 NDJSON(newline-delimited JSON) 벌크 임포트/익스포트 기능을 제공합니다. 다양한 동시성 패턴에 맞춘 세 가지 실행 모델을 지원합니다: 동기, 코루틴 기반 suspend, 그리고 Java Virtual Thread 기반 방식.

## 핵심 기능

### 세 가지 실행 모델

모듈은 서로 다른 런타임 환경과 동시성 요구사항에 맞춘 세 가지 실행 모델을 제공합니다:

1. **동기** (`Jackson3NdJsonBulkImporter`, `Jackson3NdJsonBulkExporter`)
   - 블로킹 I/O 연산
   - 전통적인 블로킹 프레임워크에 적합
   - 요청당 스레드 구조

2. **코루틴 Suspend** (`SuspendJackson3NdJsonBulkImporter`, `SuspendJackson3NdJsonBulkExporter`)
   - 논블로킹, async/await 스타일
   - Kotlin 코루틴 기반
   - 높은 동시성, 저자원 시나리오에 최적

3. **Virtual Thread** (`Jackson3NdJsonVirtualThreadBulkImporter`, `Jackson3NdJsonVirtualThreadBulkExporter`)
   - Java 21+ Virtual Thread 지원
   - 경량 스레딩 모델
   - 최소 오버헤드로 블로킹 의미론 제공

### NDJSON 봉투 형식

- 각 줄은 완전하고 독립적인 JSON 객체
- 정점과 간선은 통합 스트림으로 직렬화됨 (분할 파일 아님)
- 효율적 스트리밍을 위한 봉투 구조를 통한 타입 구분

### Jackson2 파일 형식 호환성

- Jackson2 모듈에서 익스포트한 파일을 Jackson3으로 임포트 가능
- 역호환성 있는 봉투 코덱
- 구현 전환을 위한 마이그레이션 도구 불필요

### 포괄적 에러 처리

- 상세 진단 정보와 함께 레코드당 실패 추적
- 설정 가능한 미존재 엔드포인트 정책 (FAIL / SKIP_EDGE)
- 중복 정점 ID 처리 전략
- 상태 보고: COMPLETED, PARTIAL, FAILED

### 유연한 설정

- 선택적 정점/간선 라벨 필터링
- 속성에 외부 ID 보존
- 설정 가능한 간선 버퍼 크기
- 작업별 진행 상황 보고

## 사용 예시

### 동기 익스포트

```kotlin
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkExporter
import io.bluetape4k.graph.io.options.GraphExportOptions
import io.bluetape4k.graph.io.source.GraphExportSink

val exporter = Jackson3NdJsonBulkExporter()
val options = GraphExportOptions(
    vertexLabels = listOf("Person", "Organization"),
    edgeLabels = listOf("WORKS_FOR", "KNOWS")
)

val report = exporter.exportGraph(
    sink = GraphExportSink.file("export.ndjson"),
    operations = graphOps,
    options = options
)

println("${report.verticesWritten}개 정점과 ${report.edgesWritten}개 간선 익스포트됨")
println("상태: ${report.status}, 소요시간: ${report.elapsed}")
```

### 동기 임포트

```kotlin
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonBulkImporter
import io.bluetape4k.graph.io.options.GraphImportOptions
import io.bluetape4k.graph.io.options.MissingEndpointPolicy
import io.bluetape4k.graph.io.source.GraphImportSource

val importer = Jackson3NdJsonBulkImporter()
val options = GraphImportOptions(
    defaultVertexLabel = "Entity",
    defaultEdgeLabel = "RELATED_TO",
    onMissingEdgeEndpoint = MissingEndpointPolicy.SKIP_EDGE
)

val report = importer.importGraph(
    source = GraphImportSource.file("export.ndjson"),
    operations = graphOps,
    options = options
)

println("${report.verticesCreated}개 정점과 ${report.edgesCreated}개 간선 임포트됨")
if (report.status.isFailed()) {
    println("실패: ${report.failures.size}개")
    report.failures.forEach { failure ->
        println("  ${failure.phase}: ${failure.message}")
    }
}
```

### 코루틴 기반 익스포트

```kotlin
import io.bluetape4k.graph.io.jackson3.SuspendJackson3NdJsonBulkExporter
import kotlinx.coroutines.runBlocking

val exporter = SuspendJackson3NdJsonBulkExporter()
val options = GraphExportOptions(vertexLabels = listOf("Person"))

runBlocking {
    val report = exporter.exportGraph(
        sink = GraphExportSink.file("export.ndjson"),
        operations = graphOps,
        options = options
    )
}
```

### Virtual Thread 익스포트

```kotlin
import io.bluetape4k.graph.io.jackson3.Jackson3NdJsonVirtualThreadBulkExporter

val exporter = Jackson3NdJsonVirtualThreadBulkExporter()
val options = GraphExportOptions(vertexLabels = listOf("Person"))

val report = exporter.exportGraph(
    sink = GraphExportSink.file("export.ndjson"),
    operations = graphOps,
    options = options
)
```

## NDJSON 형식 사양

### 정점 줄

```json
{"v":{"id":"person_1","label":"Person","p":{"name":"Alice","age":30}}}
```

### 간선 줄

```json
{"e":{"id":"edge_1","label":"KNOWS","from":"person_1","to":"person_2","p":{"since":2020}}}
```

**필드:**
- `id`: 고유 외부 식별자
- `label`: 정점 또는 간선 라벨/타입
- `p`: 속성 객체 (평탄화된 키-값 쌍)
- `from`/`to`: 간선 엔드포인트 ID (간선만 해당)

## 설정

### GraphImportOptions

- `defaultVertexLabel: String` - 명시적 라벨이 없을 때 적용할 기본값
- `defaultEdgeLabel: String` - 명시적 라벨이 없을 때 적용할 기본값
- `onDuplicateVertexId: DuplicatePolicy` - ID 충돌 처리 (ERROR, OVERWRITE, IGNORE)
- `onMissingEdgeEndpoint: MissingEndpointPolicy` - 매달린 간선 처리 (FAIL, SKIP_EDGE)
- `preserveExternalIdProperty: String?` - 원본 외부 ID를 속성에 저장 (키 이름)
- `maxEdgeBufferSize: Int` - 플러시 전 버퍼된 간선의 메모리 제한

### GraphExportOptions

- `vertexLabels: List<String>` - 익스포트할 특정 라벨 (비어있으면 모두)
- `edgeLabels: List<String>` - 익스포트할 특정 라벨 (비어있으면 모두)

## 의존성

```gradle
dependencies {
    api("io.bluetape4k:graph-io-jackson3:latest")
}
```

### 직접 의존성

- Jackson 3.x (Kotlin 모듈 지원 포함)
- Bluetape4k graph-io-core (추상 타입)
- Kotlin 코루틴 (선택사항, suspend 변형용)
- Virtual Thread API (선택사항, VirtualThread 변형용)

## 에러 처리

모든 임포트/익스포트 연산은 상세한 `Report` 객체를 반환합니다:

```kotlin
data class GraphImportReport(
    val status: GraphIoStatus,                  // COMPLETED, PARTIAL, FAILED
    val format: GraphIoFormat,                  // NDJSON_JACKSON3
    val verticesRead: Long,
    val verticesCreated: Long,
    val edgesRead: Long,
    val edgesCreated: Long,
    val verticesSkipped: Long,
    val edgesSkipped: Long,
    val elapsed: Duration,
    val failures: List<GraphIoFailure>         // 레코드별 실패 진단
)
```

### 실패 상세정보

각 실패는 다음을 포함합니다:
- `phase`: READ_VERTEX, READ_EDGE, WRITE_VERTEX, WRITE_EDGE
- `severity`: ERROR, WARN
- `message`: 인간이 읽을 수 있는 에러 설명
- `recordId`: 실패한 특정 레코드
- `location`: 파일 및 줄 정보 (사용 가능한 경우)

## 성능 특성

- **메모리**: 간선 레코드 버퍼링 (설정 가능 제한)
- **I/O**: 줄 단위 스트리밍으로 일정한 메모리 사용
- **동시성**: 모델 의존적
  - 동기: 단일 스레드
  - Suspend: 협력적 멀티태스킹
  - Virtual Thread: 경량 병렬성

## 호환성

- Jackson 3.0+
- Java 11+ (Virtual Thread 지원은 Java 21+ 필요)
- Kotlin 1.9+
- 모든 Bluetape4k 그래프 백엔드 (Neo4j, AGE, Memgraph, TinkerPop)

## 모듈 좌표

```
Group: io.bluetape4k
Artifact: graph-io-jackson3
Module: graph-io/jackson3
```

## 관련 모듈

- `graph-io-core` - 추상 IO 계약 및 모델
- `graph-io-jackson2` - Jackson 2.x NDJSON (레거시)
- `graph-neo4j`, `graph-age`, `graph-memgraph` - 그래프 백엔드 구현
