# graph-io-okio 구현 계획

- **Spec**: docs/superpowers/specs/2026-04-29-graph-io-okio-design.md
- **작성일**: 2026-04-29
- **개정일**: 2026-04-29 (Step 2-R 11개 HIGH 픽스 반영)
- **브랜치**: feature/12-graph-io-okio
- **모듈명**: `graph-io/okio` (`:graph-io-okio`) — 신규
- **목표**: OkIO 기반 통합 IO 어댑터(Source/Sink + 압축 체이닝 + 4개 포맷 확장 + Sync/VT/Suspend 트리플)
- **제외**: 암호화 (bluetape4k-projects #240 완료 후 v2)

---

## 작업 원칙

- **계약 우선(contract-first)**: `OkioGraphImportSource` / `OkioGraphExportSink` sealed 정의를 먼저 확정 → 이후 모든 모듈이 이 타입에만 의존.
- **close 보장**: `asClosingOutputStream`, `writeAsOutputStream { os -> ... }` 패턴으로 OkIO `BufferedSink`와 자식 OutputStream의 close 체인을 보장. StAX/Jackson과 같이 외부에서 close 시점을 통제하지 못하는 라이브러리에 한해 owning 래퍼를 적용.
- **압축은 `Compressors.Streaming.*` 사용 — `Compressable.Sinks.gzip()` (배치) 금지.** 스트리밍 변형이 OkIO `Source/Sink`를 그대로 wrap 하여 메모리 폭증을 방지함. v1에서는 모든 압축 경로가 `Compressors.Streaming.GZip / LZ4 / Snappy / Zstd / BZip2 / Deflate`를 통해 실행되어야 한다.
- **소유권 기본값 = 호출자 소유**: `ownsStream` / `ownsSource` / `ownsSink` 기본값은 **`false`** (호출자 소유). `PathSource` / `PathSink`는 라이브러리가 직접 열고 닫는 경우에만 라이브러리 소유. 외부에서 받은 stream/source/sink는 라이브러리가 임의로 닫지 않는다.
- **PathSink는 atomicWrite=true 기본** — 임시파일(`<target>.tmp`)에 기록 후 성공 시 `fileSystem.atomicMove(tmp, target)`. 실패 시 tmp 삭제. 부분 기록으로 인한 destination 손상 방지.
- **선택 의존성 가드**: LZ4/Snappy/Zstd/Bzip2 는 `compileOnly` + 런타임 `requireOnClasspath(className) { msg }` 검사. 기본 GZIP/DEFLATE 만 항상 사용 가능.
- **테스트는 FakeFileSystem 기본**: 실제 파일은 통합 테스트(round-trip) 일부에만 사용.

---

## 태스크 목록

### T0.5 GraphImportProgress / GraphExportProgress 도입 (graph-io-core)
- **complexity**: low
- **파일** (신규):
  - `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphImportProgress.kt`
  - `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphExportProgress.kt`
  - `graph-io/core/src/test/kotlin/io/bluetape4k/graph/io/contract/GraphProgressTest.kt`
- **의존**: 없음 (graph-io-core)
- **체크포인트**:
  - `data class GraphImportProgress(val processed: Long, val total: Long?, val currentLabel: String?, val throughputPerSec: Double?)` — 불변, `require(processed >= 0)` 등 검증
  - `data class GraphExportProgress(val processed: Long, val total: Long?, val currentLabel: String?, val throughputPerSec: Double?)` — 동일 구조 (export 의미)
  - 한국어 KDoc — Flow<Progress> 사용 시 emit 빈도, 마지막 emit 보장 정책 명시
  - **T8 (SuspendGraphIoOkioBulkAdapter) Flow 변형의 prerequisite** — 본 태스크가 선행되어야 T8 진행 가능

### T1. 모듈 부트스트랩 (build.gradle.kts)
- **complexity**: low
- **파일** (신규):
  - `graph-io/okio/build.gradle.kts`
  - (settings.gradle.kts 수정 **불필요** — graph-io/ 하위 자동 탐색으로 등록됨. §2.1 참조)
- **의존**: 없음
- **체크포인트**:
  - `api(project(":graph-core"))`, `api(project(":graph-io-core"))`, `api(Libs.bluetape4k_okio)`
  - `implementation(project(":graph-io-csv"))`, `:graph-io-jackson2`, `:graph-io-jackson3`, `:graph-io-graphml`
  - `implementation(Libs.bluetape4k_virtualthread_jdk25)` — 명시적 추가 (CI 실패 방지, MEMORY 가이드)
  - `compileOnly` + `testImplementation`: `lz4-java`, `snappy-java`, `zstd-jni`, `commons-compress` (BZIP2)
  - `testImplementation(Libs.okio_fakefilesystem)`, `bluetape4k-junit5`, `bluetape4k-testcontainers`, `kluent`, `mockk`
  - Java 25 + `--enable-preview` 컴파일/테스트 옵션
  - `./gradlew :graph-io-okio:compileKotlin` 통과

### T2. Compressor enum
- **complexity**: low
- **파일** (신규):
  - `graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/Compressor.kt`
  - `graph-io/okio/src/test/kotlin/io/bluetape4k/graph/io/okio/CompressorTest.kt` (신규)
- **의존**: T1
- **체크포인트**:
  - `enum class Compressor { GZIP, LZ4, SNAPPY, ZSTD, BZIP2, DEFLATE }` — **NONE 미포함** (스펙 §3.2.1 6값과 일치). 압축 미사용은 `GraphIoOkioPaths.openSource/openSink` 직접 호출로 표현 (체이닝 생략).
  - 각 enum 멤버에 필요 클래스명(`requiredClassName: String`) 메타 부여:
    - GZIP/DEFLATE → `null` (JDK 내장, 항상 사용 가능)
    - LZ4 → `"net.jpountz.lz4.LZ4Factory"`, SNAPPY → `"org.xerial.snappy.Snappy"`, ZSTD → `"com.github.luben.zstd.ZstdInputStream"`, BZIP2 → `"org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream"`
  - `requireOnClasspath(compressor)` 단일 함수: `compressor.requiredClassName?.let { requireOnClasspath(it) { "..." } }` — LZ4/Snappy/Zstd/Bzip2 4종 모두 이 함수 경유로 가드. T5에서 재호출 없이 Compressor 메타만 사용.
  - **에러 메시지에 build.gradle.kts 스니펫 포함 (스펙 §3.7.1 가이드)**: 클래스 누락 시 던지는 메시지에 다음과 같은 가이드를 함께 출력해 사용자가 즉시 의존성을 추가할 수 있도록 한다.
    ```
    LZ4 압축을 사용하려면 다음 의존성을 추가하세요:
      // build.gradle.kts
      implementation("org.lz4:lz4-java:<version>")
    ```
    - 각 Compressor에 대한 가이드 스니펫(`installHint: String`)을 enum 메타로 동봉, GZIP/DEFLATE는 비어있음.
  - 확장자 추론 헬퍼 `Compressor.fromExtension(path: String): Compressor?` (옵션)
  - KDoc: v1 미지원 항목(암호화) 및 NONE 미포함 이유 명시
  - **CompressorTest.kt**: GZIP/DEFLATE round-trip 항상 통과, LZ4/SNAPPY/ZSTD/BZIP2는 `@EnabledIfSystemProperty` 또는 try-catch 분기로 classpath 부재 시 skip. 누락 시 에러 메시지에 build.gradle.kts 스니펫이 포함되었는지도 검증.

### T3. OkIO ↔ java.io 브리지 (Bridges.kt)
- **complexity**: high
- **파일** (신규): `graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/bridge/Bridges.kt`
- **의존**: T1
- **체크포인트**:
  - 확장 함수: `BufferedSource.toInputStream()`, `BufferedSink.toOutputStream()`,
    `BufferedSink.asClosingOutputStream()` (close 시 sink도 함께 close), `BufferedSource.toReader(charset=UTF_8)`,
    `BufferedSink.toWriter(charset=UTF_8)`
  - **close 컨벤션 노트 (KDoc)**: `asClosingOutputStream`이면 underlying `BufferedSink`까지 닫는다 (즉, `OutputStream.close()` 호출 시 sink·압축 체인 전체가 닫힘). `toOutputStream`은 underlying sink를 닫지 않으므로 호출자가 직접 sink를 닫아야 한다.
  - 고수준 헬퍼:
    - `inline fun writeAsOutputStream(sink: OkioGraphExportSink, block: (OutputStream) -> Unit)` — top-level 함수, `GraphIoOkioPaths.openSink(sink).use { bs -> bs.asClosingOutputStream().use { os -> block(os) } }`
    - `inline fun readAsInputStream(source: OkioGraphImportSource, block: (InputStream) -> Unit)` — 대응 top-level 함수
  - **공통 헬퍼 `okioWriteTo` / `okioReadFrom`**: 4개 포맷 확장 함수(T9)가 재사용할 close-chain 헬퍼를 `bridge/` 패키지에 추출 → DRY (close 체인 패턴 12개 확장 함수에서 반복 방지)
  - **close 체인 정책 (모든 포맷 통일)**: 어떤 포맷이든 `BufferedSink.use { … }` 단일 close로 압축 스트림·OutputStream 전이 보장. Jackson NDJSON·GraphML 모두 `asClosingOutputStream()` 경유 — `JsonGenerator.close()` / `XMLStreamWriter.close()` 호출 후 BufferedSink까지 연쇄.
  - KDoc에 "owning vs non-owning" 차이 명시, `@param ownsSource`/`@param ownsSink` 책임 표기.

### T4. OkioGraphImportSource / OkioGraphExportSink sealed 인터페이스
- **complexity**: high
- **파일** (신규):
  - `graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/OkioGraphImportSource.kt`
  - `graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/OkioGraphExportSink.kt`
- **의존**: T1, T2
- **체크포인트**:
  - `sealed interface OkioGraphImportSource` (압축 파라미터 **미포함** — 스펙 §3.1.3: 압축은 헬퍼 분리)
    - `data class PathSource(val path: Path, val fileSystem: FileSystem = FileSystem.SYSTEM)` — **라이브러리 소유** (open/close 직접 관리)
    - `data class SourceBased(val source: Source, val ownsSource: Boolean = false)` — 기본 호출자 소유
    - `data class InputStreamBased(val inputStream: InputStream, val ownsStream: Boolean = false)` — 기본 호출자 소유
  - `sealed interface OkioGraphExportSink`(대칭 3 variants: `PathSink`, `SinkBased`, `OutputStreamBased`)
    - `data class PathSink(val path: Path, val fileSystem: FileSystem = FileSystem.SYSTEM, val mustCreate: Boolean = false, val mustExist: Boolean = false, val createParentDirectories: Boolean = true, val atomicWrite: Boolean = true)` — `mustCreate`/`mustExist` 스펙 §3.1.2 일치, **`atomicWrite=true` 기본** (T5에서 임시파일+atomicMove 처리)
    - `data class SinkBased(val sink: Sink, val ownsSink: Boolean = false)` — 기본 호출자 소유
    - `data class OutputStreamBased(val outputStream: OutputStream, val ownsStream: Boolean = false)` — 기본 호출자 소유
  - **KDoc 명시**: "PathSource/PathSink: 라이브러리 소유 (라이브러리가 open/close 모두 책임). SourceBased/SinkBased/InputStreamBased/OutputStreamBased: `ownsXxx=false` 기본으로 호출자 소유 — 라이브러리는 import/export 종료 시 underlying source/sink/stream을 닫지 않는다. 명시적으로 `ownsXxx=true`를 지정한 경우에만 라이브러리가 소유권을 인계받아 닫는다."
  - 각 변형에 `companion object` 팩토리 (`fromPath`, `fromSource`, `fromInputStream` 등) — Kotlin idiom
  - 불변(immutable) `data class`로 작성, copy/대체 시 새 인스턴스 반환
  - `requireNotBlank` / `requireNotEmpty` 인자 검증 (bluetape4k 패턴)

### T5. GraphIoOkioPaths (open Source/Sink + 압축 체이닝 + atomicWrite + bomb guard)
- **complexity**: high
- **파일** (신규): `graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/GraphIoOkioPaths.kt`
- **의존**: T2, T4
- **체크포인트**:
  - `object GraphIoOkioPaths`
  - **모든 public synchronous 메서드에 `@Throws(IOException::class)` 명시** — Java 상호운용성 + 호출자 IDE 힌트
  - **핵심 API (스펙 §3.2 기준 명명)**:
    - `@Throws(IOException::class) fun openSource(source: OkioGraphImportSource): BufferedSource`
    - `@Throws(IOException::class) fun openSink(sink: OkioGraphExportSink): BufferedSink`
    - `@Throws(IOException::class) fun openCompressedSink(sink: BufferedSink, compressor: Compressor): BufferedSink`
    - `@Throws(IOException::class) fun openDecompressedSource(source: BufferedSource, compressor: Compressor, maxDecompressedBytes: Long = DEFAULT_MAX_DECOMPRESSED_BYTES): BufferedSource`
    - `@Throws(IOException::class) fun openGzipSink(sink: OkioGraphExportSink): BufferedSink`
    - `@Throws(IOException::class) fun openGzipSource(source: OkioGraphImportSource, maxDecompressedBytes: Long = DEFAULT_MAX_DECOMPRESSED_BYTES): BufferedSource`
  - **상수**: `const val DEFAULT_MAX_DECOMPRESSED_BYTES: Long = 512L * 1024 * 1024` (512 MiB)
  - **압축은 스트리밍 사용 (배치 금지)**: 모든 압축 경로는 `Compressable.Sinks.compressableSink(sink, Compressors.Streaming.GZip)` 형태로 wrap. `Compressable.Sinks.gzip(sink)` 등 **배치 변형은 사용 금지** — 큰 그래프 export 시 메모리 폭증 방지.
    - GZip → `Compressors.Streaming.GZip`
    - LZ4 → `Compressors.Streaming.LZ4`
    - Snappy → `Compressors.Streaming.Snappy`
    - Zstd → `Compressors.Streaming.Zstd`
    - BZip2 → `Compressors.Streaming.BZip2`
    - Deflate → `Compressors.Streaming.Deflate`
  - 내부 압축 분기: `Compressor.requireOnClasspath(compressor)` 검사 후 위 스트리밍 컴프레서로 위임 — T2에서 정의한 `requiredClassName`/`installHint` 메타 활용, T5에서 재구현 없음
  - **Decompression bomb guard**: `openDecompressedSource` / `openGzipSource`는 `maxDecompressedBytes` 파라미터를 받아, 누적 decompressed 바이트가 한계를 초과하면 `throw IOException("decompression budget exceeded: limit=$maxDecompressedBytes bytes")`. 구현은 `ForwardingSource`로 래핑하여 `read()` 누적 합산.
  - PathSource: `fileSystem.source(path).buffer()` → 압축 체이닝
  - InputStreamBased(`ownsStream=false`, **기본값**): close 위임 차단 래퍼(`ForwardingSource` 서브클래스, close 무력화) 사용 — underlying InputStream이 닫히지 않도록 보장. `ownsStream=true`인 경우에만 close 전파.
  - SourceBased(`ownsSource=false`, **기본값**): 동일하게 close 차단 래퍼 적용. `ownsSource=true`이면 그대로 `.buffer()` 적용.
  - **PathSink atomicWrite 구현**: `atomicWrite=true`(기본)인 경우
    1. target path 옆에 `<target>.tmp.<random>` 임시 파일 생성 (`fileSystem.sink(tmp)`)
    2. 반환된 BufferedSink 의 close 시점에 `fileSystem.atomicMove(tmp, target)` 호출
    3. 도중 예외 발생 시 `fileSystem.delete(tmp)` (best-effort, swallow inner IOException) 후 원 예외 rethrow
    4. `atomicWrite=false`일 때는 직접 `fileSystem.sink(path)` (기존 동작)
    - close-on-success / cleanup-on-failure 의미는 `ForwardingSink` 서브클래스로 캡슐화
  - SinkBased / OutputStreamBased: `ownsXxx` 기본 false → close 차단 래퍼. `true`면 underlying까지 close.
  - **KDoc**: close 책임(호출자가 반환된 BufferedSource/Sink를 닫아야 함), `atomicWrite` 의미, decompression budget 의미, v2 암호화 예정 안내(`bluetape4k-projects #240`), 한국어 KDoc 작성
  - 파라미터 검증: `require(path.toString().isNotBlank())`, `require(maxDecompressedBytes > 0)` 등 공개 진입점 전체에 적용

### T6. OkioGraphBulkImporter / OkioGraphBulkExporter (Sync API) + GraphIoFormat enum
- **complexity**: high
- **파일** (신규):
  - `graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/GraphIoFormat.kt`
  - `graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/OkioGraphBulkImporter.kt`
  - `graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/OkioGraphBulkExporter.kt`
- **의존**: T3, T4, T5
- **체크포인트**:
  - **`enum class GraphIoFormat { CSV, NDJSON_JACKSON2, NDJSON_JACKSON3, GRAPHML }`** — 명시적 포맷 식별자. 확장자 기반 sniffing 금지 (호출자가 명시적으로 지정).
  - `interface OkioGraphBulkImporter : GraphBulkImporter<OkioGraphImportSource>` (graph-io-core 계약 준수)
  - 동일 패턴으로 Exporter
  - **`importGraph` / `exportGraph` 시그니처에 `format: GraphIoFormat` 파라미터 추가** — 호출자가 반드시 포맷을 지정.
    - `fun importGraph(source: OkioGraphImportSource, format: GraphIoFormat, options: Options): GraphImportReport`
    - `fun exportGraph(sink: OkioGraphExportSink, format: GraphIoFormat, options: Options): GraphExportReport`
  - **명시적 `when (format)` 디스패치**:
    ```kotlin
    when (format) {
        GraphIoFormat.CSV -> csvImporter.import(...)
        GraphIoFormat.NDJSON_JACKSON2 -> jackson2Importer.import(...)
        GraphIoFormat.NDJSON_JACKSON3 -> jackson3Importer.import(...)
        GraphIoFormat.GRAPHML -> graphmlImporter.import(...)
    }
    ```
    `else` branch 없음 (sealed enum exhaustive). 알 수 없는 포맷 → 컴파일 에러 (또는 런타임 `IllegalArgumentException`).
  - **확장자 기반 sniffing 명시적 금지** — KDoc에 "format은 호출자가 명시적으로 지정한다. `.csv`, `.ndjson` 등 확장자로부터 추정하지 않는다" 명시
  - 구현은 "포맷 위임자(delegate)" 형태: 생성자에서 받은 포맷별 BulkImporter/Exporter(Csv/Jackson/GraphML)에 OutputStream/InputStream 으로 위임
  - GraphML/Jackson은 `writeAsOutputStream` 통한 close 체인 사용 (T3 `asClosingOutputStream` 경유)
  - CSV/Jackson 는 단순 `toInputStream()/toOutputStream()` (외부에서 close 통제 가능)
  - 옵션 타입은 포맷별 옵션을 union 하지 않고 generic `<O>` 로 그대로 전달 (호출 시점에 포맷 매칭)
  - bluetape4k `KLogging` companion object 로깅
  - `@Throws(IOException::class)` public 메서드 표기

### T7. VirtualThreadGraphIoOkioBulkAdapter (VT 변형)
- **complexity**: medium
- **파일** (신규): `graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/virtualthread/VirtualThreadGraphIoOkioBulkAdapter.kt`
- **의존**: T6
- **체크포인트**:
  - `companion object: KLogging()` — VT 컨텍스트 로깅 (Sync와 동일 패턴)
  - 내부에 `Executors.newVirtualThreadPerTaskExecutor()` 사용 (close on adapter close)
  - 동기 API(T6) 위에 얇은 어댑터: import/export를 VT 작업으로 wrapping (`format: GraphIoFormat` 그대로 전달)
  - `AutoCloseable` 구현, 어댑터 close 시 executor shutdown
  - 파라미터 검증: 공개 진입점에 `require(...)` 적용
  - 한국어 KDoc 작성 (close 책임, VT 동작 설명)
  - 기존 Csv VT 어댑터 패턴(`CsvGraphVirtualThreadBulkImporter`) 참조

### T8. SuspendGraphIoOkioBulkAdapter (Coroutines)
- **complexity**: medium
- **파일** (신규): `graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/coroutines/SuspendGraphIoOkioBulkAdapter.kt`
- **의존**: T0.5, T6
- **체크포인트**:
  - **prereq T0.5**: `GraphImportProgress` / `GraphExportProgress` 가 graph-io-core 에 존재해야 함
  - `companion object: KLoggingChannel()` — suspend 컨텍스트 로깅 (MEMORY 가이드)
  - **API 표면 (Flow + Await 두 형태)**:
    - `fun importGraph(source: OkioGraphImportSource, format: GraphIoFormat, options: Options): Flow<GraphImportProgress>` — 진행률 스트림
    - `fun exportGraph(sink: OkioGraphExportSink, format: GraphIoFormat, options: Options): Flow<GraphExportProgress>`
    - `suspend fun importGraphAwait(source: OkioGraphImportSource, format: GraphIoFormat, options: Options): GraphImportReport` — 완료 보고서
    - `suspend fun exportGraphAwait(sink: OkioGraphExportSink, format: GraphIoFormat, options: Options): GraphExportReport`
  - **취소 안전성**: 모든 blocking I/O는 `runInterruptible(Dispatchers.IO) { … }` 사용 — OkIO `BufferedSource.read()` blocking 호출에 `Thread.interrupt()`가 전달되도록 보장. `withContext(Dispatchers.IO)`는 비차단 영역에만 사용.
  - **finally close 보장 (NonCancellable mandatory)**: 모든 import/export 경로의 finally 블록에서
    ```kotlin
    } finally {
        withContext(NonCancellable) {
            try { sink.flush() } catch (_: IOException) {}
            try { sink.close() } catch (_: IOException) {}
        }
    }
    ```
    패턴 적용. import 경로는 `source.close()`만. cancellation 도중에도 flush+close 가 반드시 실행되어야 함을 보장.
  - 파라미터 검증, 한국어 KDoc 작성
  - **취소 → 임시파일 삭제**: PathSink + atomicWrite 인 경우, cancel 시 tmp 파일이 정리되어야 함 (T5의 cleanup-on-failure 경로 활용)

### T9. CSV/Jackson/GraphML 확장 함수 (extension/*Extensions.kt)
- **complexity**: medium
- **파일** (신규):
  - `extension/CsvOkioExtensions.kt` (Sync + VT + Suspend)
  - `extension/JacksonOkioExtensions.kt` (Jackson2/Jackson3 × Sync + VT + Suspend)
  - `extension/GraphMLOkioExtensions.kt` (Sync + VT + Suspend)
- **의존**: T3, T4, T5, T6
- **체크포인트**:
  - **총 24개 확장 함수**: 4 포맷(CSV/Jackson2/Jackson3/GraphML) × 3 변형(Sync/VT/Suspend) × 2 방향(import/export)
  - **`format: GraphIoFormat` 파라미터 pass-through** — 확장 함수가 내부적으로 `OkioGraphBulkImporter`/`Exporter`를 호출할 때 해당 포맷의 enum 값을 명시적으로 전달. 포맷별 확장 함수는 자기 포맷에 맞는 enum 값을 하드코딩 (`GraphIoFormat.CSV`, `GraphIoFormat.NDJSON_JACKSON2`, ...).
  - 로깅 패턴: Sync/VT 확장 = `private val log = KotlinLogging.logger { }` (파일 단위), Suspend 확장 = 로깅 위임(호출측 어댑터로)
  - **close 체인 정책 통일**: 모든 포맷에서 T3 공통 헬퍼 `writeAsOutputStream(sink, block)` / `readAsInputStream(source, block)` 경유 — CSV/Jackson/GraphML 동일 패턴 적용, **`asClosingOutputStream()`** 사용으로 `JsonGenerator.close()` / `XMLStreamWriter.close()` 후 BufferedSink 전이 보장 (이전 `toOwningOutputStream` 명칭 폐기)
  - JacksonOkioExtensions는 jackson2/jackson3 패키지 각각에 별도 파일로 분리
  - 압축 Gzip 단축형 (`exportGraph` + `exportGraphGzip`) 노출
  - 파라미터 검증, 한국어 KDoc 작성

### T10. OkIO 단위 테스트 (FakeFileSystem 기반)
- **complexity**: medium
- **파일** (신규):
  - `OkioGraphImportSourceTest.kt`
  - `OkioGraphExportSinkTest.kt`
  - `GraphIoOkioPathsTest.kt`
  - `BridgesTest.kt`
- **의존**: T2, T3, T4, T5
- **체크포인트**:
  - 모든 `Compressor` 변형에 대한 round-trip 테스트 (FakeFileSystem `Path` 기반)
  - `@TestInstance(PER_CLASS)` + `@BeforeAll/@AfterAll` + `@AfterAll fakeFileSystem.checkNoOpenFiles()` 필수
  - Bridges: write/read 일관성, **`asClosingOutputStream` close 시 sink close 검증**, 비-owning 케이스(`toOutputStream`)에서는 sink 미-close 검증
  - GraphIoOkioPaths: 선택 압축기 미존재 시 명확한 에러 메시지 검증 (`requireOnClasspath` 위반, build.gradle.kts 스니펫 포함)
  - GZIP/DEFLATE 항상 통과, LZ4/SNAPPY/ZSTD/BZIP2 는 `@EnabledIfClassPresent` 또는 try/catch 분기
  - 단위 테스트는 컨테이너 미사용 (FakeFileSystem 만)
  - **추가: `ownsStream=false` (SourceBased) 기본값 검증** — Source/InputStream을 mock으로 wrapping 하여 import 종료 후 underlying의 close가 호출되지 않음(`verify(exactly = 0) { mockSource.close() }`)을 검증
  - **추가: `atomicWrite=true` (PathSink) 검증** — export 도중 `IOException` 주입 시 destination 파일이 존재하지 않거나 이전 내용 그대로 유지되는지(즉 부분 쓰기로 손상되지 않는지) 확인. tmp 파일은 cleanup 되어야 함.

### T11. Bulk Importer/Exporter + 확장함수 통합 테스트 (DoD 매트릭스)
- **complexity**: medium
- **파일** (신규):
  - `OkioGraphBulkImporterExporterTest.kt`
  - `extension/CsvOkioExtensionsTest.kt`
  - `extension/JacksonOkioExtensionsTest.kt`
  - `extension/GraphMLOkioExtensionsTest.kt`
- **의존**: T6, T9, T10
- **체크포인트**:
  - `@TestInstance(PER_CLASS)` + `@BeforeAll/@AfterAll` fakeFileSystem 라이프사이클 관리
  - `@AfterAll fakeFileSystem.checkNoOpenFiles()` — 파일 핸들 누수 검증 필수
  - **DoD 16 round-trip 매트릭스** — 스펙 §4.1.1 기준 (4 포맷 × {import, importGzip, export, exportGzip} = 16 셀):
    - CSV: `import` ✓, `importGzip` ✓, `export` ✓, `exportGzip` ✓
    - Jackson2 NDJSON: 동일 4 셀
    - Jackson3 NDJSON: 동일 4 셀
    - GraphML: 동일 4 셀 (StAX close 체인 특히 검증)
    - *(1 round-trip 테스트 = import + export를 함께 검증 — 총 16 셀을 8 round-trip으로 커버)*
    - 보충: DEFLATE, LZ4/SNAPPY/ZSTD/BZIP2는 classpath 존재 시 추가 케이스
  - 각 round-trip: 정점/간선/속성 동등성 (`shouldBeEqualTo`)
  - InputStreamBased / SourceBased / PathSource 세 진입점 모두 검증
  - close 누수 검증: BufferedSink mock 으로 close 호출 횟수 확인 (특히 GraphML/Jackson owning 케이스 — `asClosingOutputStream`)
  - FakeFileSystem 기반 (실제 파일은 1개 sanity 케이스만)
  - **Negative-path 테스트 (스펙 §4.2.2 필수, 보안/내구성 케이스 포함)**:
    - 빈 source(0바이트) → vertex/edge count = 0
    - truncated gzip stream → `IOException` surface
    - compileOnly 미추가 LZ4/Snappy/Zstd/Bzip2 호출 → `IllegalStateException` + 가이드 메시지(build.gradle.kts 스니펫 포함)
    - 깨진 charset → `MalformedInputException`
    - 7KB 데이터 export/import round-trip → byte 손실 없음 (`asClosingOutputStream` 검증, 명칭 변경 반영)
    - **XXE payload in GraphML** → 외부 엔티티 참조에 의한 파일/네트워크 read가 발생하지 않음. `XMLStreamException` 또는 동등 예외 surface, 그리고 외부 자원 접근이 일어나지 않았음을 mock/sandbox로 확인.
    - **Decompression bomb** → 작은 입력 → 거대 decompressed 출력. `openDecompressedSource(maxDecompressedBytes=N)` 가 한계 초과 시 `IOException("decompression budget exceeded")` 던짐.
    - **atomicWrite + mid-export exception** (FakeFileSystem) → export 도중 예외 주입 → target 파일이 존재하지 않거나 이전 내용 그대로 유지(즉 우리가 만든 tmp 파일이 atomicMove 되지 않았음). tmp 파일은 정리됨.
    - **`ownsSource=false`** → import 종료 후 underlying source `close()` 호출되지 않음을 mock으로 검증
    - **Unknown format enum** → 잘못된 enum 직렬화/null 등의 경우 `IllegalArgumentException` (런타임 분기 가드)
    - **Suspend export cancel** → coroutine cancel → `CancellationException` 재던짐(propagation), `withContext(NonCancellable)` 블록에서 close 보장됨, atomicWrite tmp 파일이 삭제되었음을 확인

### T12. VirtualThread / Suspend 어댑터 테스트
- **complexity**: medium
- **파일** (신규):
  - `virtualthread/VirtualThreadGraphIoOkioAdapterTest.kt`
  - `coroutines/SuspendGraphIoOkioAdapterTest.kt`
- **의존**: T7, T8, T11
- **체크포인트**:
  - VT: 동시 import/export 100회 round-trip, executor shutdown 검증, close 누수 없음
  - Suspend: `runTest` 기반 round-trip, 취소 시 close 보장 (Job.cancelAndJoin 후 임시 파일 close 상태 확인 + atomicWrite tmp 정리 확인)
  - JUnit `@TestInstance(PER_CLASS)` + `@AfterAll fakeFileSystem.checkNoOpenFiles()`
  - `KLoggingChannel` 동작 확인 (로그가 suspend 컨텍스트에서 정상 발행)
  - Flow 변형(`importGraph: Flow<GraphImportProgress>`) emit 동작 검증, 마지막 emit 후 await 변형(`importGraphAwait`) 결과 일치성 확인

### T13. README.md + README.ko.md
- **complexity**: low
- **파일** (신규):
  - `graph-io/okio/README.md`
  - `graph-io/okio/README.ko.md`
- **의존**: T1~T12 (실제 사용 예시 포함)
- **체크포인트**:
  - 한국어/영어 두 버전, 동일 구조
  - 섹션: Overview / Why OkIO / Quick Start (PathSource/Sink + GZIP) / Compressor 매트릭스 (필수 vs optional 의존성) / API 트리플 (Sync/VT/Suspend) / 확장 함수 사용 예 (4 포맷) / FakeFileSystem 테스트 패턴 / Roadmap (암호화 v2)
  - **소유권/atomicWrite/close 체인** 섹션 추가 — 호출자 vs 라이브러리 소유 기본값(false), atomicWrite 의미, `asClosingOutputStream` 사용처 명시
  - 코드 스니펫은 실제 컴파일 가능한 형태로 작성, KDoc 와 일치

### T14. 빌드/CI/BOM 정합성 점검
- **complexity**: low
- **파일** (수정):
  - `.github/workflows/ci.yml` — `test-core` 잡에 `:graph-io-okio:test` 추가 (§4.4.1)
  - `.github/workflows/nightly.yml` — 동일하게 `:graph-io-okio:test` 추가 (§4.4.1)
  - `graph-io/okio/build.gradle.kts` (최종 의존 정리)
  - 필요 시 `aggregation` 또는 `publishing` 설정 (예시 모듈 제외)
  - (settings.gradle.kts 수정 불필요 — 자동 탐색)
- **의존**: T1, T13
- **체크포인트**:
  - `./gradlew :graph-io-okio:build` 성공
  - `./gradlew :graph-io-okio:test` 성공
  - `./gradlew build -x test` 영향 모듈 없음 (회귀 zero)
  - BOM 자동 등록 검증: `./gradlew :bluetape4k-graph-bom:dependencies | grep graph-io-okio` 결과에 신규 모듈 표시 (§4.4)
  - Maven Central 발행 대상에 포함되었는지 확인 (graph-io 모듈 관례 일치)
  - CI workflow 수정 확인: `test-core` + `nightly` 잡에 `:graph-io-okio:test` 포함
  - **기존 5개 graph-io 모듈(`core/csv/jackson2/jackson3/graphml`)이 `test-core` 잡에 포함되어 있는지 확인, 누락 시 함께 추가** (스펙 §4.4.1)
  - CLAUDE.md 의 graph-io 디렉토리 트리에 `okio/` 줄 추가

### T15. JMH 벤치마크 추가 (신규 모듈 완료 기준)
- **complexity**: medium
- **파일** (수정):
  - `benchmark/graph-io-benchmark/src/jmh/kotlin/io/bluetape4k/graph/io/benchmark/OkioBenchmark.kt` (신규)
- **의존**: T6, T9, T14
- **체크포인트**:
  - 4개 JMH 벤치 (스펙 §4.5):
    - `csvExportJavaIo`, `csvExportOkio`, `csvExportOkioGzip` — 100K vertex
    - `csvImportJavaIo`, `csvImportOkio`, `csvImportOkioGzip`
    - `graphmlExportJavaIo`, `graphmlExportOkio`
    - `vtSyncOkioExport`, `vtVirtualThreadOkioExport` (VT vs Sync)
  - JMH `@BenchmarkMode(Mode.Throughput)` + `-prof gc` 가능 설정 (heap allocation 비교)
  - **스트리밍 컴프레서 검증용 GC allocation 측정 케이스 추가**: `csvExportOkioGzip` 의 `gc.alloc.rate.norm` 이 plain export 의 일정 배수(예: ≤ 2x) 이내인지 — 배치 압축 사용 시 폭증하는 allocation 회귀 방지 (DoD 항목과 연결)
  - **워크플로우 파일 수정**: `.github/workflows/`의 JMH 전용 `workflow_dispatch` 잡 확인 → OkIO 벤치마크 클래스 패턴(`*OkioBenchmark*`) 등록. 미존재 시 `benchmark.yml` 신설.
  - `workflow_dispatch` 트리거로만 CI 실행 (PR/nightly 자동 실행 제외 — §4.4.1)
  - 결과(BPS + GC allocation rate)를 README.ko.md "성능" 섹션에 표로 문서화 (MEMORY: 신규 모듈 완료 기준)

---

## 실행 순서

병렬 가능한 그룹별로 묶음 (각 Phase 내부는 동시 진행 가능).

**Phase 0 — graph-io-core 선행 작업**
- T0.5 (GraphImportProgress / GraphExportProgress in graph-io-core)

**Phase 1 — 부트스트랩 & 핵심 타입**
- T1 (build.gradle.kts)
- T2 (Compressor) → T1 후
- T3 (Bridges) → T1 후
- (T2, T3 는 T1 완료 후 병렬)

**Phase 2 — Sealed 계약**
- T4 (Source/Sink sealed) — T2 후

**Phase 3 — 핵심 IO 로직**
- T5 (GraphIoOkioPaths + atomicWrite + bomb guard) — T4 후
- T6 (BulkImporter/Exporter + GraphIoFormat) — T3, T4, T5 후

**Phase 4 — 어댑터 + 확장**
- T7 (VirtualThread) — T6 후
- T8 (Suspend, Flow + Await) — T0.5, T6 후
- T9 (포맷 확장 함수, format pass-through) — T6 후
- (T7, T8, T9 병렬 가능)

**Phase 5 — 테스트**
- T10 (단위 테스트 + ownsStream/atomicWrite 검증) — T2, T3, T4, T5 후 (T6 와 병렬)
- T11 (Importer/Exporter + 확장 통합 + 보안/내구성 negative-path) — T6, T9 후
- T12 (VT/Suspend 테스트, Flow 검증) — T7, T8, T11 후

**Phase 6 — 문서 & 정합성 & 벤치마크**
- T13 (README) — T1~T12 후
- T14 (CI/BOM 정합성 점검 + 빌드/테스트 그린) — T13 후
- T15 (JMH 벤치마크 추가, 스트리밍 압축 allocation 검증) — T6, T9, T14 후 (최종 게이트)

---

## DoD (Definition of Done)

- [ ] `./gradlew :graph-io-okio:build` 성공 (빌드 + 테스트 + linting)
- [ ] `./gradlew build -x test` 전체 회귀 zero
- [ ] **GraphImportProgress / GraphExportProgress 가 graph-io/core 에 추가됨** (T0.5 완료)
- [ ] BOM 자동 등록 확인 (`./gradlew :bluetape4k-graph-bom:dependencies | grep graph-io-okio`)
- [ ] 16 round-trip 매트릭스 (4 포맷 × plain+gzip) 통과, negative-path 케이스 통과
- [ ] **XXE, decompression bomb, atomicWrite mid-export 예외 negative 테스트 통과** (스펙 §4.2.2)
- [ ] **`ownsStream=false` / `ownsSource=false` 기본값 검증 통과** (호출자 소유 — 라이브러리가 underlying close 안함)
- [ ] **스트리밍 컴프레서 사용 검증 (배치 금지)** — JMH heap allocation 회귀 테스트로 확인 (`Compressors.Streaming.*` 사용; `Compressable.Sinks.gzip()` 등 배치 사용 0건)
- [ ] **PathSink atomicWrite=true 기본 검증** — 실패 시 destination 손상 없음, tmp 파일 정리됨
- [ ] **Suspend 어댑터: cancel → `withContext(NonCancellable)` close, tmp 파일 정리, `CancellationException` propagation** 검증
- [ ] **`asClosingOutputStream` 명칭으로 통일** (`toOwningOutputStream` 잔존 0건 — `rg toOwningOutputStream` empty)
- [ ] **`GraphIoFormat` enum 명시 디스패치** — 확장자 sniffing 코드 0건
- [ ] VT/Suspend 어댑터 close 누수 없음, `fakeFileSystem.checkNoOpenFiles()` green
- [ ] GZIP/DEFLATE 는 항상 통과, LZ4/SNAPPY/ZSTD/BZIP2 는 classpath 가드 동작 (에러 메시지에 build.gradle.kts 스니펫 포함)
- [ ] README.md, README.ko.md 작성 완료 (성능 표 + 소유권/atomicWrite 섹션 포함 — §4.5)
- [ ] CLAUDE.md graph-io 트리 업데이트 (`okio/`)
- [ ] CI workflow 수정 완료 (test-core + nightly에 `:graph-io-okio:test` 추가)
- [ ] JMH 벤치마크 4개 추가 + 결과 문서화 (MEMORY: 신규 모듈 완료 기준)
- [ ] code-reviewer 리뷰 통과 (CRITICAL/HIGH 이슈 zero)
- [ ] 80%+ 라인 커버리지 (graph-io-core 관례)
