# graph-io-okio 구현 계획

- **Spec**: docs/superpowers/specs/2026-04-29-graph-io-okio-design.md
- **작성일**: 2026-04-29
- **브랜치**: feature/12-graph-io-okio
- **모듈명**: `graph-io/okio` (`:graph-io-okio`) — 신규
- **목표**: OkIO 기반 통합 IO 어댑터(Source/Sink + 압축 체이닝 + 4개 포맷 확장 + Sync/VT/Suspend 트리플)
- **제외**: 암호화 (bluetape4k-projects #240 완료 후 v2)

---

## 작업 원칙

- **계약 우선(contract-first)**: `OkioGraphImportSource` / `OkioGraphExportSink` sealed 정의를 먼저 확정 → 이후 모든 모듈이 이 타입에만 의존.
- **close 보장**: `toOwningOutputStream`, `writeAsOutputStream { os -> ... }` 패턴으로 OkIO `BufferedSink`와 자식 OutputStream의 close 체인을 보장. StAX/Jackson과 같이 외부에서 close 시점을 통제하지 못하는 라이브러리에 한해 owning 래퍼를 적용.
- **압축 체이닝은 `bluetape4k-okio`에 위임**: `Compressable.Sources.gzip(...)`, `Compressable.Sinks.gzip(...)` 등을 호출만 하고, 자체 구현 금지.
- **선택 의존성 가드**: LZ4/Snappy/Zstd/Bzip2 는 `compileOnly` + 런타임 `requireOnClasspath(className) { msg }` 검사. 기본 GZIP/DEFLATE 만 항상 사용 가능.
- **테스트는 FakeFileSystem 기본**: 실제 파일은 통합 테스트(round-trip) 일부에만 사용.

---

## 태스크 목록

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
  - 확장자 추론 헬퍼 `Compressor.fromExtension(path: String): Compressor?` (옵션)
  - KDoc: v1 미지원 항목(암호화) 및 NONE 미포함 이유 명시
  - **CompressorTest.kt**: GZIP/DEFLATE round-trip 항상 통과, LZ4/SNAPPY/ZSTD/BZIP2는 `@EnabledIfSystemProperty` 또는 try-catch 분기로 classpath 부재 시 skip

### T3. OkIO ↔ java.io 브리지 (Bridges.kt)
- **complexity**: high
- **파일** (신규): `graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/bridge/Bridges.kt`
- **의존**: T1
- **체크포인트**:
  - 확장 함수: `BufferedSource.toInputStream()`, `BufferedSink.toOutputStream()`,
    `BufferedSink.toOwningOutputStream()` (close 시 sink도 함께 close), `BufferedSource.toReader(charset=UTF_8)`,
    `BufferedSink.toWriter(charset=UTF_8)`
  - 고수준 헬퍼:
    - `inline fun writeAsOutputStream(sink: OkioGraphExportSink, block: (OutputStream) -> Unit)` — top-level 함수, `GraphIoOkioPaths.openSink(sink).use { bs -> bs.toOwningOutputStream().use { os -> block(os) } }`
    - `inline fun readAsInputStream(source: OkioGraphImportSource, block: (InputStream) -> Unit)` — 대응 top-level 함수
  - **공통 헬퍼 `okioWriteTo` / `okioReadFrom`**: 4개 포맷 확장 함수(T9)가 재사용할 close-chain 헬퍼를 `bridge/` 패키지에 추출 → DRY (close 체인 패턴 12개 확장 함수에서 반복 방지)
  - **close 체인 정책 (모든 포맷 통일)**: 어떤 포맷이든 `BufferedSink.use { … }` 단일 close로 압축 스트림·OutputStream 전이 보장. Jackson NDJSON·GraphML 모두 `toOwningOutputStream()` 경유 — `JsonGenerator.close()` / `XMLStreamWriter.close()` 호출 후 BufferedSink까지 연쇄.
  - KDoc에 "owning vs non-owning" 차이 명시, `@param ownsSource`/`@param ownsSink` 책임 표기.

### T4. OkioGraphImportSource / OkioGraphExportSink sealed 인터페이스
- **complexity**: high
- **파일** (신규):
  - `graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/OkioGraphImportSource.kt`
  - `graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/OkioGraphExportSink.kt`
- **의존**: T1, T2
- **체크포인트**:
  - `sealed interface OkioGraphImportSource` (압축 파라미터 **미포함** — 스펙 §3.1.3: 압축은 헬퍼 분리)
    - `data class PathSource(val path: Path, val fileSystem: FileSystem = FileSystem.SYSTEM)`
    - `data class SourceBased(val source: Source, val ownsSource: Boolean = true)`
    - `data class InputStreamBased(val inputStream: InputStream, val ownsStream: Boolean = true)`
  - `sealed interface OkioGraphExportSink`(대칭 3 variants: `PathSink`, `SinkBased`, `OutputStreamBased`)
    - `data class PathSink(val path: Path, val fileSystem: FileSystem = FileSystem.SYSTEM, val mustCreate: Boolean = false, val mustExist: Boolean = false, val createParentDirectories: Boolean = true)` — `mustCreate`/`mustExist` 스펙 §3.1.2 일치
    - `data class SinkBased(val sink: Sink, val ownsSink: Boolean = true)`
    - `data class OutputStreamBased(val outputStream: OutputStream, val ownsStream: Boolean = true)`
  - 각 변형에 `companion object` 팩토리 (`fromPath`, `fromSource`, `fromInputStream` 등) — Kotlin idiom
  - 불변(immutable) `data class`로 작성, copy/대체 시 새 인스턴스 반환
  - `requireNotBlank` / `requireNotEmpty` 인자 검증 (bluetape4k 패턴)

### T5. GraphIoOkioPaths (open Source/Sink + 압축 체이닝)
- **complexity**: high
- **파일** (신규): `graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/GraphIoOkioPaths.kt`
- **의존**: T2, T4
- **체크포인트**:
  - `object GraphIoOkioPaths`
  - **핵심 API (스펙 §3.2 기준 명명)**:
    - `openSource(source: OkioGraphImportSource): BufferedSource`
    - `openSink(sink: OkioGraphExportSink): BufferedSink`
    - `openCompressedSink(sink: BufferedSink, compressor: Compressor): BufferedSink`
    - `openDecompressedSource(source: BufferedSource, compressor: Compressor): BufferedSource`
    - `openGzipSink(sink: OkioGraphExportSink): BufferedSink` (Gzip 단축형)
    - `openGzipSource(source: OkioGraphImportSource): BufferedSource`
  - 내부 압축 분기: `Compressor.requireOnClasspath(compressor)` 검사 후 `Compressable.Sources.gzip / lz4 / snappy / zstd / bzip2 / deflate` 위임 — T2에서 정의한 `requiredClassName` 메타 활용, T5에서 재구현 없음
  - PathSource: `fileSystem.source(path).buffer()` → 압축 체이닝
  - InputStreamBased(`ownsStream=false`): close 위임 차단 래퍼(`ForwardingSource` 서브클래스, close 무력화) 사용 — underlying InputStream이 닫히지 않도록 보장
  - SourceBased: 그대로 `.buffer()` 적용
  - **KDoc**: close 책임(호출자가 반환된 BufferedSource/Sink를 닫아야 함), v2 암호화 예정 안내(`bluetape4k-projects #240`), 한국어 KDoc 작성
  - 파라미터 검증: `require(path.toString().isNotBlank())` 등 공개 진입점 전체에 적용

### T6. OkioGraphBulkImporter / OkioGraphBulkExporter (Sync API)
- **complexity**: high
- **파일** (신규):
  - `graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/OkioGraphBulkImporter.kt`
  - `graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/OkioGraphBulkExporter.kt`
- **의존**: T3, T4, T5
- **체크포인트**:
  - `interface OkioGraphBulkImporter : GraphBulkImporter<OkioGraphImportSource>` (graph-io-core 계약 준수)
  - 동일 패턴으로 Exporter
  - 구현은 "포맷 위임자(delegate)" 형태: 생성자에서 받은 포맷별 BulkImporter/Exporter(Csv/Jackson/GraphML)에 OutputStream/InputStream 으로 위임
  - `import(source: OkioGraphImportSource, options): GraphImportReport` 시그니처
  - GraphML/Jackson은 `writeAsOutputStream` 통한 close 체인 사용
  - CSV/Jackson 는 단순 `toInputStream()/toOutputStream()` (외부에서 close 통제 가능)
  - 옵션 타입은 포맷별 옵션을 union 하지 않고 generic `<O>` 로 그대로 전달 (호출 시점에 포맷 매칭)
  - bluetape4k `KLogging` companion object 로깅

### T7. VirtualThreadGraphIoOkioBulkAdapter (VT 변형)
- **complexity**: medium
- **파일** (신규): `graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/virtualthread/VirtualThreadGraphIoOkioBulkAdapter.kt`
- **의존**: T6
- **체크포인트**:
  - `companion object: KLogging()` — VT 컨텍스트 로깅 (Sync와 동일 패턴)
  - 내부에 `Executors.newVirtualThreadPerTaskExecutor()` 사용 (close on adapter close)
  - 동기 API(T6) 위에 얇은 어댑터: import/export를 VT 작업으로 wrapping
  - `AutoCloseable` 구현, 어댑터 close 시 executor shutdown
  - 파라미터 검증: 공개 진입점에 `require(...)` 적용
  - 한국어 KDoc 작성 (close 책임, VT 동작 설명)
  - 기존 Csv VT 어댑터 패턴(`CsvGraphVirtualThreadBulkImporter`) 참조

### T8. SuspendGraphIoOkioBulkAdapter (Coroutines)
- **complexity**: medium
- **파일** (신규): `graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/coroutines/SuspendGraphIoOkioBulkAdapter.kt`
- **의존**: T6
- **체크포인트**:
  - `companion object: KLoggingChannel()` — suspend 컨텍스트 로깅 (MEMORY 가이드)
  - `suspend fun import(...)` / `suspend fun export(...)`
  - **취소 안전성**: `runInterruptible(Dispatchers.IO) { … }` 사용 — OkIO `BufferedSource.read()` blocking 호출에 `Thread.interrupt()`가 전달되도록 보장. `withContext(Dispatchers.IO)`는 비차단 영역에만 사용.
  - `NonCancellable` 블록에서 sink/source `.close()` 보장 (`finally { source.close() }` 패턴)
  - Flow 변형은 v1 범위 외 — KDoc에 "v2 예정" 명시만
  - 파라미터 검증, 한국어 KDoc 작성

### T9. CSV/Jackson/GraphML 확장 함수 (extension/*Extensions.kt)
- **complexity**: medium
- **파일** (신규):
  - `extension/CsvOkioExtensions.kt` (Sync + VT + Suspend)
  - `extension/JacksonOkioExtensions.kt` (Jackson2/Jackson3 × Sync + VT + Suspend)
  - `extension/GraphMLOkioExtensions.kt` (Sync + VT + Suspend)
- **의존**: T3, T4, T5, T6
- **체크포인트**:
  - **총 24개 확장 함수**: 4 포맷(CSV/Jackson2/Jackson3/GraphML) × 3 변형(Sync/VT/Suspend) × 2 방향(import/export)
  - 로깅 패턴: Sync/VT 확장 = `private val log = KotlinLogging.logger { }` (파일 단위), Suspend 확장 = 로깅 위임(호출측 어댑터로)
  - **close 체인 정책 통일**: 모든 포맷에서 T3 공통 헬퍼 `writeAsOutputStream(sink, block)` / `readAsInputStream(source, block)` 경유 — CSV/Jackson/GraphML 동일 패턴 적용, `toOwningOutputStream()` 사용으로 `JsonGenerator.close()` / `XMLStreamWriter.close()` 후 BufferedSink 전이 보장
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
  - Bridges: write/read 일관성, owning OutputStream close 시 sink close 검증, 비-owning 케이스에서는 sink 미-close 검증
  - GraphIoOkioPaths: 선택 압축기 미존재 시 명확한 에러 메시지 검증 (`requireOnClasspath` 위반)
  - GZIP/DEFLATE 항상 통과, LZ4/SNAPPY/ZSTD/BZIP2 는 `@EnabledIfClassPresent` 또는 try/catch 분기
  - 단위 테스트는 컨테이너 미사용 (FakeFileSystem 만)

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
  - close 누수 검증: BufferedSink mock 으로 close 호출 횟수 확인 (특히 GraphML/Jackson owning 케이스)
  - FakeFileSystem 기반 (실제 파일은 1개 sanity 케이스만)
  - **Negative-path 5케이스 (스펙 §4.2.2 필수)**:
    - 빈 source(0바이트) → vertex/edge count = 0
    - truncated gzip stream → `IOException` surface
    - compileOnly 미추가 LZ4/Snappy/Zstd/Bzip2 호출 → `IllegalStateException` + 가이드 메시지
    - 깨진 charset → `MalformedInputException`
    - 7KB 데이터 export/import round-trip → byte 손실 없음 (`toOwningOutputStream` 검증)

### T12. VirtualThread / Suspend 어댑터 테스트
- **complexity**: medium
- **파일** (신규):
  - `virtualthread/VirtualThreadGraphIoOkioAdapterTest.kt`
  - `coroutines/SuspendGraphIoOkioAdapterTest.kt`
- **의존**: T7, T8, T11
- **체크포인트**:
  - VT: 동시 import/export 100회 round-trip, executor shutdown 검증, close 누수 없음
  - Suspend: `runTest` 기반 round-trip, 취소 시 close 보장 (Job.cancelAndJoin 후 임시 파일 close 상태 확인)
  - JUnit `@TestInstance(PER_CLASS)` + `@AfterAll fakeFileSystem.checkNoOpenFiles()`
  - `KLoggingChannel` 동작 확인 (로그가 suspend 컨텍스트에서 정상 발행)

### T13. README.md + README.ko.md
- **complexity**: low
- **파일** (신규):
  - `graph-io/okio/README.md`
  - `graph-io/okio/README.ko.md`
- **의존**: T1~T12 (실제 사용 예시 포함)
- **체크포인트**:
  - 한국어/영어 두 버전, 동일 구조
  - 섹션: Overview / Why OkIO / Quick Start (PathSource/Sink + GZIP) / Compressor 매트릭스 (필수 vs optional 의존성) / API 트리플 (Sync/VT/Suspend) / 확장 함수 사용 예 (4 포맷) / FakeFileSystem 테스트 패턴 / Roadmap (암호화 v2)
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
  - **워크플로우 파일 수정**: `.github/workflows/`의 JMH 전용 `workflow_dispatch` 잡 확인 → OkIO 벤치마크 클래스 패턴(`*OkioBenchmark*`) 등록. 미존재 시 `benchmark.yml` 신설.
  - `workflow_dispatch` 트리거로만 CI 실행 (PR/nightly 자동 실행 제외 — §4.4.1)
  - 결과(BPS + GC allocation rate)를 README.ko.md "성능" 섹션에 표로 문서화 (MEMORY: 신규 모듈 완료 기준)

---

## 실행 순서

병렬 가능한 그룹별로 묶음 (각 Phase 내부는 동시 진행 가능).

**Phase 1 — 부트스트랩 & 핵심 타입**
- T1 (build.gradle.kts)
- T2 (Compressor) → T1 후
- T3 (Bridges) → T1 후
- (T2, T3 는 T1 완료 후 병렬)

**Phase 2 — Sealed 계약**
- T4 (Source/Sink sealed) — T2 후

**Phase 3 — 핵심 IO 로직**
- T5 (GraphIoOkioPaths) — T4 후
- T6 (BulkImporter/Exporter) — T3, T4, T5 후

**Phase 4 — 어댑터 + 확장**
- T7 (VirtualThread) — T6 후
- T8 (Suspend) — T6 후
- T9 (포맷 확장 함수) — T6 후
- (T7, T8, T9 병렬 가능)

**Phase 5 — 테스트**
- T10 (단위 테스트) — T2, T3, T4, T5 후 (T6 와 병렬)
- T11 (Importer/Exporter + 확장 통합) — T6, T9 후
- T12 (VT/Suspend 테스트) — T7, T8, T11 후

**Phase 6 — 문서 & 정합성 & 벤치마크**
- T13 (README) — T1~T12 후
- T14 (CI/BOM 정합성 점검 + 빌드/테스트 그린) — T13 후
- T15 (JMH 벤치마크 추가) — T6, T9, T14 후 (최종 게이트)

---

## DoD (Definition of Done)

- [ ] `./gradlew :graph-io-okio:build` 성공 (빌드 + 테스트 + linting)
- [ ] `./gradlew build -x test` 전체 회귀 zero
- [ ] BOM 자동 등록 확인 (`./gradlew :bluetape4k-graph-bom:dependencies | grep graph-io-okio`)
- [ ] 16 round-trip 매트릭스 (4 포맷 × plain+gzip) 통과, negative-path 5케이스 통과
- [ ] VT/Suspend 어댑터 close 누수 없음, `fakeFileSystem.checkNoOpenFiles()` green
- [ ] GZIP/DEFLATE 는 항상 통과, LZ4/SNAPPY/ZSTD/BZIP2 는 classpath 가드 동작
- [ ] README.md, README.ko.md 작성 완료 (성능 표 포함 — §4.5)
- [ ] CLAUDE.md graph-io 트리 업데이트 (`okio/`)
- [ ] CI workflow 수정 완료 (test-core + nightly에 `:graph-io-okio:test` 추가)
- [ ] JMH 벤치마크 4개 추가 + 결과 문서화 (MEMORY: 신규 모듈 완료 기준)
- [ ] code-reviewer 리뷰 통과 (CRITICAL/HIGH 이슈 zero)
- [ ] 80%+ 라인 커버리지 (graph-io-core 관례)
