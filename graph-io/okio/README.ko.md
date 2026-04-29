# graph-io-okio (한국어)

OkIO 기반 그래프 I/O 레이어. 기존 `graph-io-csv`, `graph-io-jackson2`, `graph-io-jackson3`, `graph-io-graphml` 모듈과 완벽하게 호환되면서 OkIO의 세그먼트 기반 스트리밍, 압축 체이닝, FileSystem 추상화를 제공한다.

## OkIO를 선택하는 이유

| java.io 방식 | OkIO 방식 |
|-------------|----------|
| 전체 파일을 Heap에 로드 | 64 KB 세그먼트 단위 스트리밍 — Heap 절약 |
| 압축 시 별도 스트림 래핑 필요 | `Compressors.Streaming.*` 체이닝으로 선언적 압축 |
| 파일 경로만 지원 | PathSource, BufferedSource, InputStream 3가지 진입점 |
| 원자적 쓰기 없음 | `PathSink(atomicWrite=true)` 기본으로 부분 쓰기 방지 |
| FakeFileSystem 없음 | `okio-fakefilesystem`으로 테스트 단순화 |

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

### 소유권 정책

| 변형 | 기본 소유권 | 의미 |
|------|-----------|------|
| `PathSource` / `PathSink` | **라이브러리** | 라이브러리가 파일을 열고 닫는다 |
| `SourceBased(ownsSource=false)` | **호출자** | import 종료 후 Source가 닫히지 않는다 |
| `SinkBased(ownsSink=false)` | **호출자** | export 종료 후 Sink가 닫히지 않는다 |
| `SourceBased(ownsSource=true)` | **라이브러리** | 라이브러리가 Source를 대신 닫는다 |

`ownsXxx=false`가 기본값이므로, 외부에서 전달한 Source/Sink/Stream은 호출자가 직접 관리한다.

### 압축 지원

```kotlin
enum class Compressor { GZIP, DEFLATE, LZ4, SNAPPY, ZSTD, BZIP2 }
```

| 압축기 | 의존성 | 항상 사용 가능 |
|-------|--------|:----------:|
| `GZIP` | JDK 내장 | ✅ |
| `DEFLATE` | JDK 내장 | ✅ |
| `LZ4` | `org.lz4:lz4-java` | 선택적 |
| `SNAPPY` | `org.xerial.snappy:snappy-java` | 선택적 |
| `ZSTD` | `com.github.luben:zstd-jni` | 선택적 |
| `BZIP2` | `org.apache.commons:commons-compress` | 선택적 |

선택적 의존성 없이 LZ4/Snappy/Zstd/Bzip2를 사용하면 `IllegalStateException`과 함께 `build.gradle.kts` 추가 방법이 안내된다.

## 사용법

### Sync API

```kotlin
val importer = OkioGraphBulkImporter()
val exporter = OkioGraphBulkExporter()

// 파일 시스템 경로로 익스포트 (원자적 쓰기 기본)
exporter.exportGraph(
    OkioGraphExportSink.PathSink("/data/graph.ndjson".toPath()),
    GraphIoFormat.NDJSON_JACKSON3,
    graphOperations,
    GraphExportOptions(vertexLabels = setOf("Person"), edgeLabels = setOf("KNOWS")),
)

// 파일 시스템 경로로 임포트
importer.importGraph(
    OkioGraphImportSource.PathSource("/data/graph.ndjson".toPath()),
    GraphIoFormat.NDJSON_JACKSON3,
    graphOperations,
    GraphImportOptions(),
)
```

### GZIP 압축

```kotlin
// export → .ndjson.gz
exporter.exportGraphGzip(
    OkioGraphExportSink.PathSink("/data/graph.ndjson.gz".toPath()),
    GraphIoFormat.NDJSON_JACKSON3,
    graphOperations,
    exportOptions,
)

// import ← .ndjson.gz
importer.importGraphGzip(
    OkioGraphImportSource.PathSource("/data/graph.ndjson.gz".toPath()),
    GraphIoFormat.NDJSON_JACKSON3,
    graphOperations,
    importOptions,
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
```

### 압축 체이닝

```kotlin
// GZIP 편의 함수
GraphIoOkioPaths.openGzipSink(sink)      // BufferedSink (GZIP 압축)
GraphIoOkioPaths.openGzipSource(source)  // BufferedSource (GZIP 해제, 512 MiB 한계)

// 범용 압축
GraphIoOkioPaths.openCompressedSink(rawSink, Compressor.ZSTD)
GraphIoOkioPaths.openDecompressedSource(rawSource, Compressor.ZSTD, maxDecompressedBytes = 1_073_741_824L)
```

### 원자적 쓰기

`PathSink(atomicWrite = true)` (기본값)이면:
1. `{target}.tmp.{UUID}` 임시 파일에 쓰기
2. 성공 시 → `atomicMove(tmp, target)`
3. 실패 시 → 임시 파일 삭제, 대상 파일 손상 없음

```kotlin
// 원자적 쓰기 비활성화 (직접 쓰기)
val sink = OkioGraphExportSink.PathSink(path, atomicWrite = false)
```

### FakeFileSystem 테스트 패턴

```kotlin
class MyGraphIoTest {
    private val fakeFs = FakeFileSystem()

    @AfterEach
    fun cleanup() {
        fakeFs.checkNoOpenFiles()  // 파일 핸들 누수 검출
    }

    @Test
    fun `round trip test`() {
        val path = "/graph.ndjson".toPath()
        val exporter = OkioGraphBulkExporter()

        exporter.exportGraph(
            OkioGraphExportSink.PathSink(path, fakeFs),
            GraphIoFormat.NDJSON_JACKSON3,
            myOperations,
            exportOptions,
        )

        // import + verify
    }
}
```

## 성능 (JMH — `small`: 1K 정점 / 2K 간선 | `medium`: 10K 정점 / 20K 간선)

> `./gradlew :graph-io-benchmark:benchmark` 으로 실행한다.
> 환경: Java 25, Apple M3 Pro, 1 warmup / 3 iterations / 2 s each (빠른 측정용).
> 프로덕션 기준 측정은 기본값(3 warmup / 5 iterations / 3 s)으로 재실행한다.

### NDJSON (Jackson3) — Export (AverageTime, ms/op, 낮을수록 좋음)

| 시나리오 | small | medium |
|---------|------:|-------:|
| `jackson3JavaIoExport` (baseline) | 1.23 | 18.06 |
| `jackson3OkioExport` | 1.53 | 19.69 |
| `jackson3OkioGzipExport` | 3.09 | 39.87 |
| `jackson3VtOkioExport` (VirtualThread) | 1.47 | 19.34 |

### NDJSON (Jackson3) — Import / RoundTrip

| 시나리오 | small | medium |
|---------|------:|-------:|
| `jackson3JavaIoImport` (baseline) | 17.26 | 183.93 |
| `jackson3OkioImport` | 17.40 | 184.61 |
| `jackson3OkioGzipImport` | 20.06 | 226.40 |
| `jackson3OkioRoundTrip` | 17.34 | 192.44 |
| `jackson3OkioGzipRoundTrip` | 20.04 | 212.96 |
| `jackson3VtOkioImport` (VirtualThread) | 16.99 | 184.68 |
| `jackson3VtOkioRoundTrip` | 17.27 | 191.34 |

### GraphML (StAX) — Export / Import / RoundTrip

| 시나리오 | small | medium |
|---------|------:|-------:|
| `graphMlJavaIoExport` (baseline) | 2.37 | 33.88 |
| `graphMlOkioExport` | 3.70 | 39.36 |
| `graphMlJavaIoImport` (baseline) | 18.74 | 215.44 |
| `graphMlOkioImport` | 19.93 | 220.84 |
| `graphMlOkioRoundTrip` | 20.75 | 215.05 |

**관찰:**
- NDJSON OkIO는 java.io 대비 Export +25% (small), +9% (medium), Import는 동일 수준
- VirtualThread 오버헤드는 사실상 없음 (sync OkIO와 동일)
- GZIP Export는 plain 대비 2× 느리지만 Import는 +15% 수준
- GraphML OkIO는 StAX→InputStream 변환 오버헤드로 +10~15% 차이 발생

## 보안

- **XXE 방지**: GraphML StAX 파서에 `IS_SUPPORTING_EXTERNAL_ENTITIES=false`, `SUPPORT_DTD=false` 적용 (기존 구현 위임)
- **Decompression bomb 방지**: `BombGuardSource`가 해제 바이트를 추적하여 `maxDecompressedBytes` 초과 시 `IOException` 발생
  - 기본 한계: 512 MiB (`DEFAULT_MAX_DECOMPRESSED_BYTES`)
  - 커스텀 한계: `openDecompressedSource(source, compressor, maxDecompressedBytes = 1L * 1024 * 1024 * 1024)` (1 GiB)

## Gradle 의존성

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.bluetape4k:graph-io-okio:VERSION")

    // 선택적 압축 라이브러리 (필요한 것만 추가)
    implementation("org.lz4:lz4-java:1.8.0")
    implementation("org.xerial.snappy:snappy-java:1.1.10.7")
    implementation("com.github.luben:zstd-jni:1.5.6-6")
    implementation("org.apache.commons:commons-compress:1.27.1")
}
```

## 로드맵

- **v2**: Tink 암호화 지원 (`bluetape4k-projects #240`) — AES-GCM 기반, 소용량 파일 전용 (`TinkEncryptSink` / `TinkDecryptSource`)
- **v2**: CSV PathSource/PathSink 없이도 스트림 기반 CSV 지원 검토
