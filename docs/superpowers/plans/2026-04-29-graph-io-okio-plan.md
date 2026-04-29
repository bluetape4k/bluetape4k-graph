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
- **파일** (신규): `graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/Compressor.kt`
- **의존**: T1
- **체크포인트**:
  - `enum class Compressor { NONE, GZIP, LZ4, SNAPPY, ZSTD, BZIP2, DEFLATE }`
  - 각 enum 멤버에 필요 클래스명(`requiredClassName: String?`) 메타 부여 → `requireOnClasspath` 호출 단일 지점화
  - 확장자 추론 헬퍼 `fromExtension(path: String): Compressor`(읽기 자동 감지용, 옵션)
  - KDoc로 v1 미지원 항목(암호화) 명시

### T3. OkIO ↔ java.io 브리지 (Bridges.kt)
- **complexity**: high
- **파일** (신규): `graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/bridge/Bridges.kt`
- **의존**: T1
- **체크포인트**:
  - 확장 함수: `BufferedSource.toInputStream()`, `BufferedSink.toOutputStream()`,
    `BufferedSink.toOwningOutputStream()` (close 시 sink도 close), `BufferedSource.toReader(charset=UTF_8)`,
    `BufferedSink.toWriter(charset=UTF_8)`
  - 고수준 헬퍼:
    - `inline fun BufferedSink.writeAsOutputStream(block: (OutputStream) -> Unit)` — block 종료 시 OutputStream `close()` → flush → sink는 호출자가 try-with-resources로 처리
    - `inline fun BufferedSource.readAsInputStream(block: (InputStream) -> Unit)`
  - close 체인 정확성: sink는 호출자 책임, OutputStream wrapper close 시 underlying buffer flush 보장.
  - 단위 테스트는 T11에서 다룸.
  - KDoc에 "owning vs non-owning" 차이 명시.

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
    - `data class PathSink(val path: Path, val fileSystem: FileSystem = FileSystem.SYSTEM, val createParentDirectories: Boolean = true)`
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
  - 핵심 API:
    - `openBufferedSource(import: OkioGraphImportSource): BufferedSource`
    - `openBufferedSink(export: OkioGraphExportSink): BufferedSink`
  - 내부에서 `Compressor` 매핑 → `Compressable.Sources.gzip / lz4 / snappy / zstd / bzip2 / deflate` 호출
  - `requireOnClasspath` 가드: 선택 압축기 사용 시 명확한 에러 메시지 (`"LZ4 compression requires lz4-java on classpath"`)
  - GZIP 단축 헬퍼: `gzipSource(path)`, `gzipSink(path)` — 일반적인 케이스 노출
  - PathSource 일 경우 `FileSystem.source(path).buffer()` 사용 후 압축 체이닝
  - InputStreamBased 일 경우 `input.source().buffer()`로 변환
  - SourceBased 는 그대로 buffer 적용
  - **close 책임 문서화**: 반환된 BufferedSource/Sink는 호출자가 close — 내부 분기 일관성 유지

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
  - 내부에 `Executors.newVirtualThreadPerTaskExecutor()` 사용 (close on adapter close)
  - 동기 API(T6) 위에 얇은 어댑터: import/export 를 VT 작업으로 wrapping
  - VT 작업 내에서 IO 블로킹 발생해도 OS 쓰레드 차단되지 않도록 stream IO 만 사용 (synchronized 블록 회피)
  - `AutoCloseable` 구현, 어댑터 close 시 executor shutdown
  - 기존 Csv VT 어댑터 패턴(`CsvGraphVirtualThreadBulkImporter`) 그대로 차용

### T8. SuspendGraphIoOkioBulkAdapter (Coroutines)
- **complexity**: medium
- **파일** (신규): `graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/coroutines/SuspendGraphIoOkioBulkAdapter.kt`
- **의존**: T6
- **체크포인트**:
  - `suspend fun import(...)` / `suspend fun export(...)`
  - 내부에서 `withContext(Dispatchers.IO)` 로 동기 어댑터 위임
  - 로깅: `KLoggingChannel` 사용 (suspend 컨텍스트, MEMORY 가이드)
  - 취소 안전성: `runInterruptible` 또는 IO 디스패처 + 외부 close 시점 보장. close 패턴은 `use { }` 블록 권장.
  - Flow 변형은 v1 범위 외 (옵션) — KDoc 만 명시.

### T9. CSV/Jackson/GraphML 확장 함수 (extension/*Extensions.kt)
- **complexity**: medium
- **파일** (신규):
  - `extension/CsvOkioExtensions.kt`
  - `extension/JacksonOkioExtensions.kt`
  - `extension/GraphMLOkioExtensions.kt`
- **의존**: T3, T4, T5, T6
- **체크포인트**:
  - 각 포맷의 기존 importer/exporter 에 OkIO 어댑터 확장:
    - `CsvGraphBulkExporter.exportGraph(sink: BufferedSink, options)` — 내부에서 `toOutputStream()`
    - `CsvGraphBulkImporter.importGraph(source: BufferedSource, options)`
    - GraphML 의 경우 `writeAsOutputStream` 으로 StAX writer close 체인 보장
    - Jackson NDJSON 은 `toOwningOutputStream` + JsonGenerator.close → underlying sink close
  - Sync, VT, Suspend 변형마다 확장(총 12개)
  - JacksonOkioExtensions 는 jackson2/jackson3 모두 커버 (각각 패키지 내 별도 객체)
  - 압축 변형 단축형 (`exportGzipNdjson(path: Path, ...)`) 옵션, 핵심은 sink 받는 형태

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
  - **DoD 16 round-trip 매트릭스** (4 포맷 × 4 압축: NONE/GZIP/DEFLATE/Selected) 검증:
    - CSV × {NONE, GZIP, DEFLATE, ZSTD-or-skip}
    - Jackson2 NDJSON × {NONE, GZIP, DEFLATE, LZ4-or-skip}
    - Jackson3 NDJSON × {NONE, GZIP, DEFLATE, SNAPPY-or-skip}
    - GraphML × {NONE, GZIP, DEFLATE, BZIP2-or-skip}
  - 각 round-trip: 정점/간선/속성 동등성 (`shouldBeEqualTo`)
  - InputStreamBased / SourceBased / PathSource 세 진입점 모두 검증
  - close 누수 검증: BufferedSink mock 으로 close 호출 횟수 확인 (특히 GraphML/Jackson owning 케이스)
  - FakeFileSystem 기반 (실제 파일은 1개 sanity 케이스만)

### T12. VirtualThread / Suspend 어댑터 테스트
- **complexity**: medium
- **파일** (신규):
  - `virtualthread/VirtualThreadGraphIoOkioAdapterTest.kt`
  - `coroutines/SuspendGraphIoOkioAdapterTest.kt`
- **의존**: T7, T8, T11
- **체크포인트**:
  - VT: 동시 import/export 100회 round-trip, executor shutdown 검증, close 누수 없음
  - Suspend: `runTest` 기반 round-trip, 취소 시 close 보장 (Job.cancelAndJoin 후 임시 파일 close 상태 확인)
  - JUnit `@TestInstance(PER_CLASS)`, 컨테이너 미사용
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

### T14. graph-io-core / graph-io 모듈 정합성 점검
- **complexity**: low
- **파일** (수정 가능):
  - `graph-io/okio/build.gradle.kts` (의존 정리)
  - 필요 시 `aggregation` 또는 `publishing` 설정 (예시 모듈 제외)
  - (settings.gradle.kts 수정 불필요 — 자동 탐색)
- **의존**: T1, T13
- **체크포인트**:
  - `./gradlew :graph-io-okio:build` 성공
  - `./gradlew :graph-io-okio:test` 성공
  - `./gradlew build -x test` 영향 모듈 없음 확인
  - Maven Central 발행 대상에 포함되었는지 확인 (graph-io 모듈 관례 일치)
  - CLAUDE.md 의 graph-io 디렉토리 트리에 `okio/` 줄 추가 (선택, 별도 PR 가능)

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

**Phase 6 — 문서 & 정합성**
- T13 (README) — T1~T12 후
- T14 (정합성 점검 + 빌드/테스트 그린) — T13 후 (최종 게이트)

---

## DoD (Definition of Done)

- [ ] `./gradlew :graph-io-okio:build` 성공 (빌드 + 테스트 + linting)
- [ ] 16개 round-trip 매트릭스 테스트 모두 통과 (또는 missing classpath 시 명시적 skip)
- [ ] VT/Suspend 어댑터 close 누수 없음
- [ ] GZIP/DEFLATE 는 항상 통과, LZ4/SNAPPY/ZSTD/BZIP2 는 classpath 가드 동작
- [ ] README.md, README.ko.md 작성 완료, 코드 스니펫 컴파일 가능
- [ ] CLAUDE.md graph-io 트리 업데이트 (`okio/`)
- [ ] code-reviewer 리뷰 통과 (CRITICAL/HIGH 이슈 zero)
- [ ] 80%+ 라인 커버리지 (graph-io-core 관례)
