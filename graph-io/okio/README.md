# graph-okio

OkIO 기반 그래프 I/O 레이어. 기존 `graph-io-csv`, `graph-io-jackson2`, `graph-io-jackson3`, `graph-io-graphml` 모듈과 완벽하게 호환되면서 OkIO의 세그먼트 기반 스트리밍, 압축 체이닝, FileSystem 추상화를 제공한다.

## 지원 포맷

| 포맷 | `GraphIoFormat` | 설명 |
|------|----------------|------|
| CSV | `CSV` | 정점/간선 파일 분리 (`{stem}_vertices.csv` + `{stem}_edges.csv`) |
| NDJSON (Jackson 2) | `NDJSON_JACKSON2` | Newline-delimited JSON, Jackson 2.x |
| NDJSON (Jackson 3) | `NDJSON_JACKSON3` | Newline-delimited JSON, Jackson 3.x |
| GraphML | `GRAPHML` | XML/StAX 기반 그래프 교환 포맷 |

## 주요 타입

### 소스/싱크 sealed interface

```kotlin
// 임포트 소스 — 3가지 변형
sealed interface OkioGraphImportSource {
    data class PathSource(val path: Path, val fileSystem: FileSystem = FileSystem.SYSTEM) : OkioGraphImportSource
    data class SourceBased(val source: Source, val ownsSource: Boolean = false) : OkioGraphImportSource
    data class InputStreamBased(val inputStream: InputStream, val ownsStream: Boolean = false) : OkioGraphImportSource
}

// 익스포트 싱크 — 3가지 변형
sealed interface OkioGraphExportSink {
    data class PathSink(
        val path: Path,
        val fileSystem: FileSystem = FileSystem.SYSTEM,
        val mustCreate: Boolean = false,
        val mustExist: Boolean = false,
        val createParentDirectories: Boolean = true,
        val atomicWrite: Boolean = true,   // 기본값: 원자적 쓰기 활성화
    ) : OkioGraphExportSink
    data class SinkBased(val sink: Sink, val ownsSink: Boolean = false) : OkioGraphExportSink
    data class OutputStreamBased(val outputStream: OutputStream, val ownsStream: Boolean = false) : OkioGraphExportSink
}
```

### 압축 지원

```kotlin
enum class Compressor { GZIP, DEFLATE, LZ4, SNAPPY, ZSTD, BZIP2 }
```

- `GZIP`, `DEFLATE`, `BZIP2`: JDK 내장 — 별도 의존성 없음
- `LZ4`: `lz4-java` 선택적 의존성
- `SNAPPY`: `snappy-java` 선택적 의존성
- `ZSTD`: `zstd-jni` 선택적 의존성

## 사용법

### Sync API

```kotlin
// OkioGraphBulkImporter / OkioGraphBulkExporter 직접 사용
val importer = OkioGraphBulkImporter()
val exporter = OkioGraphBulkExporter()

// 파일 시스템 경로로 익스포트 (원자적 쓰기 기본)
exporter.exportGraph(
    OkioGraphExportSink.PathSink("/data/graph.ndjson".toPath()),
    GraphIoFormat.NDJSON_JACKSON3,
    graphOperations,
)

// 파일 시스템 경로로 임포트
importer.importGraph(
    OkioGraphImportSource.PathSource("/data/graph.ndjson".toPath()),
    GraphIoFormat.NDJSON_JACKSON3,
    graphOperations,
)
```

### 포맷별 Extension Functions

```kotlin
// Jackson 2/3
jackson2Exporter.exportGraph(sink, operations, options)
jackson2Exporter.exportGraphGzip(sink, operations, options)
jackson2Importer.importGraph(source, operations, options)
jackson2Importer.importGraphGzip(source, operations, options)

// GraphML
graphMlExporter.exportGraph(sink, operations, options)
graphMlExporter.exportGraphGzip(sink, operations, options)
graphMlImporter.importGraph(source, operations, options)
graphMlImporter.importGraphGzip(source, operations, options)

// CSV (PathSource/PathSink 전용)
csvExporter.exportGraph(sink, operations, options)
csvImporter.importGraph(source, operations, options)
```

### Virtual Thread (비동기)

```kotlin
val adapter = VirtualThreadGraphIoOkioBulkAdapter()

val future: CompletableFuture<GraphExportReport> = adapter.exportGraphAsync(
    sink, GraphIoFormat.NDJSON_JACKSON3, operations, options
)
val future: CompletableFuture<GraphImportReport> = adapter.importGraphAsync(
    source, GraphIoFormat.NDJSON_JACKSON3, operations, options
)

// Extension function 변형
exporter.exportGraphAsync(sink, operations, options)
importer.importGraphAsync(source, operations, options)
```

### Coroutine (suspend/Flow)

```kotlin
val adapter = SuspendGraphIoOkioBulkAdapter()

// 완료 보고서 반환
val report: GraphExportReport = adapter.exportGraphAwait(sink, GraphIoFormat.NDJSON_JACKSON3, ops, options)
val report: GraphImportReport = adapter.importGraphAwait(source, GraphIoFormat.NDJSON_JACKSON3, ops, options)

// 진행 상태 Flow
adapter.exportGraph(sink, GraphIoFormat.NDJSON_JACKSON3, ops, options).collect { progress ->
    println("exported: ${progress.exported}")
}
adapter.importGraph(source, GraphIoFormat.NDJSON_JACKSON3, ops, options).collect { progress ->
    println("processed: ${progress.processed}")
}

// Extension function 변형
exporter.exportGraphAwait(sink, operations, options)
exporter.exportGraphFlow(sink, operations, options)
importer.importGraphAwait(source, operations, options)
importer.importGraphFlow(source, operations, options)
```

### 압축 체이닝

```kotlin
// GZIP 편의 함수
GraphIoOkioPaths.openGzipSink(sink)      // BufferedSink (GZIP 압축)
GraphIoOkioPaths.openGzipSource(source)  // BufferedSource (GZIP 해제, 512 MiB 한계)

// 범용 압축
GraphIoOkioPaths.openCompressedSink(sink, Compressor.ZSTD)
GraphIoOkioPaths.openDecompressedSource(source, Compressor.ZSTD, maxDecompressedBytes = 1_073_741_824L)
```

### 원자적 쓰기

`PathSink(atomicWrite = true)` (기본값)이면:
1. `{target}.tmp.{UUID}` 임시 파일에 쓰기
2. 성공 시 → `atomicMove(tmp, target)`
3. 실패 시 → 임시 파일 삭제, 대상 파일 손상 없음

## 보안

- **XXE 방지**: GraphML StAX 파서에 `IS_SUPPORTING_EXTERNAL_ENTITIES=false`, `SUPPORT_DTD=false` 적용 (기존 구현 위임)
- **Decompression bomb 방지**: `BombGuardSource`가 해제 바이트를 추적하여 `maxDecompressedBytes` 초과 시 `IOException` 발생

## Gradle 의존성

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.github.bluetape4k.graph:graph-okio:VERSION")

    // 선택적 압축 라이브러리 (필요한 것만 추가)
    implementation("org.lz4:lz4-java:1.8.0")
    implementation("org.xerial.snappy:snappy-java:1.1.10.7")
    implementation("com.github.luben:zstd-jni:1.5.6-6")
    implementation("org.apache.commons:commons-compress:1.27.1")
}
```
