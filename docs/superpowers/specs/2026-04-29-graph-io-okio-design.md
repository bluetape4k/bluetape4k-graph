# Graph IO OkIO 모듈 설계 Spec

- **Issue**: #12 — graph-io에 OkIO 기반 Source/Sink 지원 추가
- **작성일**: 2026-04-29
- **작성자**: bluetape4k-graph 팀
- **상태**: Step 2-R 리뷰 반영 완료 — 사용자 최종 승인 대기 (HIGH×11 + MEDIUM×8 적용)
- **연관 모듈**: `graph-io/core`, `graph-io/csv`, `graph-io/jackson2`, `graph-io/jackson3`, `graph-io/graphml`, `bluetape4k-okio`
- **관련 문서**:
  - `docs/superpowers/specs/2026-04-18-graph-io-bulk-import-export-design.md` (기존 graph-io 아키텍처)
  - `bluetape4k-projects/io/okio/` (OkIO 헬퍼 모듈)

---

## 1. 문제 정의

### 1.1 현재 상태

`graph-io/core`의 `GraphIoPaths` 헬퍼는 `java.io` / `java.nio.file` 기반의 4개 함수만 제공한다.

```kotlin
// graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/support/GraphIoPaths.kt
fun openReader(source: GraphImportSource): BufferedReader
fun openWriter(sink: GraphExportSink): BufferedWriter
fun openInputStream(source: GraphImportSource): InputStream
fun openOutputStream(sink: GraphExportSink): OutputStream
```

`GraphImportSource` / `GraphExportSink`는 sealed interface로 다음 variant만 지원한다.

- `GraphImportSource.PathSource(Path)`
- `GraphImportSource.InputStreamSource(InputStream)`
- `GraphExportSink.PathSink(Path)`
- `GraphExportSink.OutputStreamSink(OutputStream)`

기존 4개 포맷 모듈(CSV, Jackson2, Jackson3, GraphML)은 이 헬퍼를 통해 일관된 I/O 채널을 사용한다.

### 1.2 한계와 통증 지점

#### (A) Heap 메모리 비효율

`java.io.BufferedInputStream` / `BufferedReader`는 내부 `byte[]` / `char[]`를 한 덩어리로 잡고, 응용에서 `read()`가 호출될 때마다 동일 버퍼 위 위치만 옮긴다. 대용량(GB급) GraphML 임포트 시 다음 문제가 발생한다.

- StAX 파서가 `next()`를 호출할 때마다 버퍼 끝에서 디스크 I/O가 발생하고, 단편화된 파싱 토큰이 GC로 짧은 주기로 살아남는다.
- 추가 디코딩(GZIP) 또는 변환을 chain 하기 어렵다 — 단순 `GZIPInputStream(FileInputStream(...))` 조합은 가능하지만, 풀(pool)에서 재사용되지 않는 임시 byte[]가 늘어난다.
- `BufferedReader`는 한 줄을 읽을 때마다 내부에서 `StringBuilder`를 새로 만든다 — NDJSON 100만 라인 처리 시 GC 압력이 매우 크다.

#### (B) 압축 미지원

현재 graph-io는 GZIP / LZ4 / Snappy / Zstd 등 어떤 압축도 지원하지 않는다. 운영 환경에서 NDJSON / CSV / GraphML을 보관할 때 표준적으로 GZIP을 쓰는데, 사용자가 직접 `GZIPInputStream`을 합성해야 한다.

#### (C) 암호화 미지원

GraphML / CSV에 PII(개인정보)가 포함된 그래프를 저장할 때 정지(at-rest) 암호화 옵션이 없다. 현재 사용자는 외부 도구(openssl, age 등)로 별도 처리해야 한다.

#### (D) 일관된 sink/source 추상이 java.io에 묶여 있음

`graph-io-csv`의 `BulkExporter` 같은 컴포넌트는 모두 `OutputStream` 기반이다. 새로운 Sink(예: 분할 업로드, 다중 시리얼라이저 fan-out 등)를 끼워 넣기 어렵다.

### 1.3 OkIO segment 처리가 Heap에 유리한 이유

OkIO는 `Buffer`를 내부적으로 **`Segment` 연결 리스트**로 관리한다.

- 각 `Segment`는 8KB(`Segment.SIZE = 8192`) 고정 크기. `SegmentPool`이 풀로 재사용한다 → 새 byte[] 할당 거의 없음.
- `Source → Sink` 사이에서 `Buffer.write(source, byteCount)`는 byte를 복사하지 않고 **segment 포인터만 양도**한다.
  - 예: `gzipSource.readAll(fileSink)` 시 디스크 → 메모리 → 파일 사이의 중간 byte[] 복사가 0회 또는 1회로 줄어든다.
- 라인 단위 NDJSON 읽기(`readUtf8Line()`)도 `Buffer` 내부에서 segment span만 ASCII scan하므로 unnecessary string allocation이 없다.
- 압축/암호화 체이닝이 자연스럽다: `Source → GzipSource → BufferedSource`는 모두 lazy pull 모델이라 응용이 read 한 만큼만 디코딩이 진행된다.

`bluetape4k-okio` 모듈은 이 위에 `Compressable.Sources/Sinks`, `Tink*Source/Sink`, `bufferOf`, `asSource/asSink` 등의 어댑터 헬퍼를 제공한다 — graph-io에서는 이를 1차 시맨틱으로 채택해 직접 OkIO 의존성을 노출하지 않고 사용한다.

#### 1.3.1 포맷별 heap 절약 효과의 실효성 (포맷 의존성)

OkIO segment 풀의 heap 이점은 **모든 포맷에 동일하게 적용되지 않는다**. 파서가 byte stream을 어떻게 소비하느냐에 따라 다음 순서로 효과가 약해진다.

| 포맷 | 파서 입력 | OkIO 이점 | 비고 |
|------|----------|----------|------|
| **GraphML** | `InputStream` (StAX) | **큼** | StAX는 byte 단위 chunked pull → segment 양도 효과 직접 수혜 |
| **NDJSON (Jackson 2/3)** | `InputStream` (Jackson `JsonFactory`) | **중간** | Jackson 내부에 자체 byte buffer가 있지만 라인 단위 lazy pull은 유지 |
| **CSV (bluetape4k-csv 자체 구현)** | `InputStream → Reader` 기반 | **작음** | `CsvRecordReader`가 `InputStream`을 받아 내부적으로 `BufferedReader`로 래핑하는 `CsvLexer`를 사용한다. OkIO `BufferedSource → inputStream() → BufferedReader` 체인에서 char 디코딩 단계에 도달하는 순간 segment 양도 효과가 무효화된다. 단, `CsvLexer`는 lazy `Sequence` 기반 스트리밍 파서이므로 배치(batch) 할당 오버헤드는 없다. |

> **참고 — bluetape4k-csv 구조**: `CsvRecordReader` → `input.reader(encoding)` → `CsvLexer(reader: Reader)` → 내부 `BufferedReader` → char-by-char 상태 기계. `InputStream` 기반이므로 OkIO `source.inputStream()`을 브리지로 쓸 수 있지만, 파싱 코어는 Reader 레벨에서 동작한다.

**결론**: CSV 경로에서 OkIO를 채택하는 1차 동기는 **압축 체이닝의 편의성 및 FileSystem 추상화**이지, segment 풀 자체의 heap 절약은 아니다. 사용자 KDoc/README에 이 사실을 명시한다.

#### 1.3.2 암호화 레이어 heap 영향 — 현재 버전 미포함

현재 `bluetape4k-okio`의 `TinkDecryptSource`는 **전체 파일을 메모리에 적재 후 일괄 복호화**하는 구조로, 대용량 파일에서 OOM 위험이 있다. 이를 해결하는 DAEAD 청크형 스트리밍(`DaeadChunkEncryptSink` / `DaeadChunkDecryptSource`)은 **`bluetape4k-projects` #240**으로 추적한다.

**현재 버전에서는 암호화 레이어를 포함하지 않는다.** 압축 체이닝(Gzip/LZ4/Snappy/Zstd 등)만 제공한다. 암호화 지원은 #240 완료 후 다음 버전에 추가된다.

### 1.4 비목표 (비목표)

- 기존 4개 포맷 모듈의 Java I/O 경로를 OkIO로 **대체**하지 않는다. 호환성 유지가 최우선.
- **암호화 미지원** (현재 버전): AEAD/DAEAD 체이닝은 `bluetape4k-projects` #240 완료 후 다음 버전에서 추가된다.
- AES-GCM / AES-CTR 자체 스트림 암호화 구현은 본 issue 범위 밖이다.
- Tink `StreamingAead` (AesGcmHkdfStreaming) 지원은 `bluetape4k-okio`에 래퍼가 추가된 후 후속 버전에서 지원.
- Async I/O (Netty / NIO 채널) 어댑터는 본 모듈에서 제공하지 않는다.
- **I/O 메트릭 / Micrometer 계측**: bytes-transferred, record-count 메트릭은 별도 이슈로 추적.
- **Spring Boot Actuator 헬스 체크 통합**: HealthIndicator 연동은 별도 이슈.
- **감사 로깅 (Audit Logging)**: PII 포함 그래프의 감사 추적은 애플리케이션 레이어 책임.
- **파일 경로 검증 / 경로 주입 방지**: `PathSource`/`PathSink`의 `Path`가 외부 입력에서 왔을 때 경로 순회(path traversal) 검증은 **호출자 책임**이다. graph-io-okio는 경로를 sanitize하지 않는다.

---

## 2. 범위

### 2.1 신규 모듈

```
graph-io/
  okio/                                  # 신규 (이 spec 범위)
    src/main/kotlin/io/bluetape4k/graph/io/okio/
      OkioGraphImportSource.kt          # sealed interface
      OkioGraphExportSink.kt            # sealed interface
      GraphIoOkioPaths.kt               # 헬퍼: open Source/Sink, 압축 체이닝
      OkioGraphBulkImporter.kt          # Sync GraphBulkImporter<OkioGraphImportSource> 구현
      OkioGraphBulkExporter.kt          # Sync GraphBulkExporter<OkioGraphExportSink> 구현
      bridge/
        Bridges.kt                      # OkIO ↔ java.io 변환 (BufferedSource.inputStream() 등)
                                        # 포함: writeAsOutputStream(sink) { ... } 헬퍼
      virtualthread/
        VirtualThreadGraphIoOkioBulkAdapter.kt
      coroutines/
        SuspendGraphIoOkioBulkAdapter.kt
      extension/
        CsvOkioExtensions.kt            # 기존 CSV Importer/Exporter에 OkIO 변형 함수 (선택)
        GraphMLOkioExtensions.kt
        JacksonOkioExtensions.kt
    src/main/resources/
      META-INF/...
    src/test/kotlin/io/bluetape4k/graph/io/okio/
      GraphIoOkioPathsTest.kt
      ...
    README.md
    README.ko.md
    build.gradle.kts
```

> **참고 — `okio.Path` vs `java.nio.file.Path`**: `OkioGraphImportSource.PathSource` / `OkioGraphExportSink.PathSink`의 `path` 필드는 **`okio.Path`**(테스트의 `FakeFileSystem` 호환)이다. 호출자가 `java.nio.file.Path`를 가지고 있을 때는 `nioPath.toOkioPath()` 확장으로 변환한다.

> **모듈 등록 — settings.gradle.kts**: 본 프로젝트는 `settings.gradle.kts`가 `graph-io/` 하위를 자동 탐색해 모듈을 등록한다. 따라서 `:graph-io-okio`는 디렉터리만 만들면 자동 인식되며, 별도 `include(...)` 라인을 수동으로 추가할 필요가 없다.

### 2.2 포함

- OkIO 기반 Source/Sink 열기 (Path, InputStream, OutputStream, OkIO Source, OkIO Sink 입력)
- 압축 체이닝 (Gzip 우선, LZ4/Snappy/Zstd/Bzip2/Deflate은 `bluetape4k-okio` 위임)
- OkIO ↔ java.io 브리지 (기존 StAX, Jackson, CSV 파서/리더 호환)
- VirtualThread / Coroutine 변형 어댑터
- 기존 포맷 모듈에 OkIO 오버로드 확장 함수 (별도 모듈에서 제공, 기존 모듈 비침습)
- **암호화 체이닝은 제외** (후속 버전 — `bluetape4k-projects` #240 완료 후 추가)

### 2.3 제외

- 기존 `graph-io/core`, `graph-io-csv`, `graph-io-jackson2/3`, `graph-io-graphml` 내부 코드 변경 (확장 함수만 추가, 기존 API 시그니처 불변)
- AES-GCM 자체 스트림 암호화
- S3/HTTP 등 원격 Source/Sink

---

## 3. 설계

### 3.1 신규 sealed interface

#### 3.1.1 `OkioGraphImportSource`

```kotlin
package io.bluetape4k.graph.io.okio

sealed interface OkioGraphImportSource {
    /** 로컬 파일 시스템 경로 기반 (FileSystem 추상화는 SYSTEM 기본) */
    data class PathSource(
        val path: Path,
        val fileSystem: FileSystem = FileSystem.SYSTEM,
    ): OkioGraphImportSource

    /** OkIO Source 기반 (이미 구성된 Source 재사용) */
    data class SourceBased(
        val source: Source,
        /**
         * `true`이면 라이브러리가 Source를 닫는다. `false`(기본값)이면 호출자가 닫는다.
         *
         * 기존 `GraphImportSource.InputStreamSource.closeInput = false` 관례와 일치시킨다.
         * 라이브러리는 호출자 공급 스트림을 임의로 닫지 않는다.
         */
        val ownsSource: Boolean = false,
    ): OkioGraphImportSource

    /** java.io.InputStream을 OkIO Source로 어댑팅 */
    data class InputStreamBased(
        val inputStream: InputStream,
        /**
         * `true`이면 라이브러리가 InputStream을 닫는다. `false`(기본값)이면 호출자가 닫는다.
         *
         * 기존 `GraphImportSource.InputStreamSource.closeInput = false` 관례와 일치.
         */
        val ownsStream: Boolean = false,
    ): OkioGraphImportSource
}
```

**설계 근거**:

- `okio.FileSystem`을 파라미터화 → 테스트에서 `FakeFileSystem`을 그대로 주입할 수 있다.
- `ownsSource` / `ownsStream` 기본값을 **`false`**로 지정 — 기존 `GraphImportSource.InputStreamSource.closeInput = false` 관례와 일치. **라이브러리는 호출자가 공급한 스트림을 임의로 닫지 않는다.** `PathSource`는 라이브러리가 항상 소유(내부에서 열고 닫음).
- `SourceBased`는 사용자가 이미 다른 OkIO 파이프라인(예: HTTP body)을 graph-io로 흘려보낼 때 사용.

#### 3.1.2 `OkioGraphExportSink`

```kotlin
package io.bluetape4k.graph.io.okio

sealed interface OkioGraphExportSink {
    data class PathSink(
        val path: Path,
        val fileSystem: FileSystem = FileSystem.SYSTEM,
        val mustCreate: Boolean = false,
        val mustExist: Boolean = false,
        /**
         * 파일을 열기 전에 부모 디렉토리를 자동 생성할지 여부.
         * OkIO `FileSystem`은 기본적으로 부모 디렉토리를 자동 생성하지 않으므로
         * (java.nio.Files와 동일), 사용자 편의를 위해 본 헬퍼에서 명시적으로 지원한다.
         * 기본값 `true` — 운영 환경에서 디렉토리 누락으로 export가 실패하는 사례 방지.
         */
        val createParentDirectories: Boolean = true,
    ): OkioGraphExportSink

    data class SinkBased(
        val sink: Sink,
        /**
         * `true`이면 라이브러리가 Sink를 닫는다. `false`(기본값)이면 호출자가 닫는다.
         *
         * 기존 `GraphExportSink.OutputStreamSink` 관례와 일치. `PathSink`는 라이브러리 소유.
         */
        val ownsSink: Boolean = false,
    ): OkioGraphExportSink

    data class OutputStreamBased(
        val outputStream: OutputStream,
        /**
         * `true`이면 라이브러리가 OutputStream을 닫는다. `false`(기본값)이면 호출자가 닫는다.
         */
        val ownsStream: Boolean = false,
    ): OkioGraphExportSink
}
```

#### 3.1.3 압축 파라미터를 sealed interface 안에 둘지, 별도 헬퍼로 분리할지

**결정: 별도 헬퍼로 분리한다 (권장안 채택)**.

| 옵션 | 장점 | 단점 |
|------|------|------|
| Sealed에 포함 (`PathSource(path, compression = Gzip)`) | 호출 1번으로 끝, 가독성↑ | enum이 sealed의 모든 variant에 분기로 박힘. 압축 옵션이 늘어날 때 (LZ4 level 등) 폭발. 향후 암호화까지 더하면 cartesian product. |
| 별도 헬퍼 (`openGzipSource(openSource(...))`) | 직교성↑, 체이닝 자유 (Gzip+Zstd 등, 향후 암호화도 추가 용이). 새로운 압축 추가 시 sealed 손대지 않음. | 호출이 2~3 단계로 길어짐. |

압축/암호화(후속 버전) 조합이 늘어날 가능성이 높으므로 **체이닝 모델**이 안전하다. 단축형 (`openGzipSink(sink)`)을 제공해 호출 길이는 보완한다.

### 3.2 `GraphIoOkioPaths` 헬퍼

```kotlin
package io.bluetape4k.graph.io.okio

object GraphIoOkioPaths {

    // ------------ 기본 open ------------

    /** OkioGraphImportSource → BufferedSource */
    fun openSource(source: OkioGraphImportSource): BufferedSource

    /** OkioGraphExportSink → BufferedSink */
    fun openSink(sink: OkioGraphExportSink): BufferedSink

    // ------------ 압축 체이닝 ------------

    /**
     * 기존 Sink를 주어진 Compressor로 감싼 BufferedSink를 반환.
     * 내부적으로 [Compressors.Streaming] 변형(StreamingCompressSink)을 사용해 스트리밍 압축한다.
     * @param compressor 압축 알고리즘 (GZIP, LZ4, SNAPPY, ZSTD, BZIP2, DEFLATE)
     */
    fun openCompressedSink(sink: BufferedSink, compressor: Compressor): BufferedSink

    /**
     * 기존 Source를 주어진 Decompressor로 감싼 BufferedSource를 반환.
     * 내부적으로 [Compressors.Streaming] 변형(StreamingDecompressSource)을 사용해 스트리밍 압축 해제한다.
     *
     * @param maxDecompressedBytes 압축 해제 허용 최대 바이트 (기본 512MB). 이를 초과하면
     *   [java.io.IOException]("decompression budget exceeded")을 던진다.
     *   압축 폭탄(decompression bomb) 방어를 위해 반드시 설정한다.
     */
    fun openDecompressedSource(
        source: BufferedSource,
        decompressor: Compressor,
        maxDecompressedBytes: Long = 512L * 1024 * 1024,
    ): BufferedSource

    // ------------ Gzip 단축형 (가장 흔한 케이스) ------------

    fun openGzipSink(sink: OkioGraphExportSink): BufferedSink

    /**
     * @param maxDecompressedBytes 압축 폭탄 방어 한도 (기본 512MB).
     */
    fun openGzipSource(source: OkioGraphImportSource, maxDecompressedBytes: Long = 512L * 1024 * 1024): BufferedSource

    // ------------ 조합 단축형 ------------
    // 암호화 API는 현재 버전 범위 외 (§3.6, §6 참조).
    // bluetape4k-projects #240 해결 후 다음 버전에 openDaeadEncryptedSink / openDaeadDecryptedSource 추가 예정.
}
```

#### 3.2.3 원자적 쓰기 전략 (PathSink)

`PathSink` 기반 export 실패 시 대상 파일이 부분 기록된 채로 남는 문제를 방지하기 위해 **원자적 쓰기** 전략을 기본으로 적용한다.

1. 실제 파일(`path`) 대신 임시 파일(`path.parent / "${path.name}.tmp.${random}"`)을 연다.
2. 성공 시 `FileSystem.atomicMove(tmp, path)`로 원자적으로 이동 (덮어쓰기).
3. 실패 시 임시 파일을 삭제한다.

```kotlin
data class PathSink(
    val path: okio.Path,
    val fileSystem: FileSystem = FileSystem.SYSTEM,
    val mustCreate: Boolean = false,
    val mustExist: Boolean = false,
    val createParentDirectories: Boolean = true,
    /**
     * `true`(기본값)이면 임시 파일에 먼저 기록 후 atomicMove로 원자적 배치.
     * FakeFileSystem 및 SYSTEM FileSystem에서 지원.
     */
    val atomicWrite: Boolean = true,
): OkioGraphExportSink
```

#### 3.2.1 `Compressor` enum

```kotlin
enum class Compressor { GZIP, LZ4, SNAPPY, ZSTD, BZIP2, DEFLATE }
```

내부적으로 `bluetape4k-okio`의 `Compressable.Sinks/Sources`에 위임한다.

> **⚠️ 스트리밍 vs 배치 주의**: `bluetape4k-okio`의 `Compressable.Sinks.gzip()` 등 숏핸드 함수는 **배치 방식**(close 시점에 전체 데이터를 압축) `CompressableSink`를 반환한다. 대용량 파일에서 OOM이 발생한다. graph-io-okio는 반드시 **스트리밍 변형** (`Compressors.Streaming.*`)을 사용한다.
>
> ```kotlin
> // ❌ 금지 — 배치: 전체 데이터를 plainBuffer에 누적 후 close 시점에 일괄 압축
> Compressable.Sinks.gzip(sink)         // CompressableSink (batch)
>
> // ✅ 올바름 — 스트리밍: write() 호출마다 압축 청크를 즉시 delegate에 기록
> Compressable.Sinks.compressableSink(sink, Compressors.Streaming.GZip)  // StreamingCompressSink
> ```
>
> `openCompressedSink` / `openDecompressedSource`는 모두 `Compressors.Streaming.*`을 사용한다.

#### 3.2.2 처리 순서

저장 시: `plaintext bytes → [optional compression: Gzip/LZ4/Snappy/Zstd/...] → file`

읽을 때 역순: `file → [optional decompression] → plaintext bytes`

> **암호화 (후속 버전)**: 현재 버전에서는 암호화 레이어 없음. #240 완료 후 `compress → encrypt` 순서로 추가 예정 (압축 후 암호화 — 암호문은 엔트로피가 높아 더 이상 압축되지 않음을 활용).

### 3.3 OkIO ↔ java.io 브리지

#### 3.3.1 기본 방향

OkIO `BufferedSource`/`BufferedSink`를 기존 StAX, Jackson, CSV 파서/라이터에 연결하려면 `java.io.InputStream`/`OutputStream`이 필요하다. OkIO는 이미 이 변환을 제공한다.

```kotlin
// io.bluetape4k.graph.io.okio.bridge

/** BufferedSource → InputStream (OkIO 기본 제공: source.inputStream()) */
fun BufferedSource.toInputStream(): InputStream = this.inputStream()

/** BufferedSink → OutputStream */
fun BufferedSink.toOutputStream(): OutputStream = this.outputStream()
```

이 자체는 매우 얇은 래퍼이지만, **graph-io 사용자가 OkIO API를 직접 import 하지 않게** 하는 게 목적이다.

#### 3.3.2 Reader/Writer 브리지

CSV/NDJSON 텍스트 처리는 `Reader`/`Writer`가 필요하다.

```kotlin
fun BufferedSource.toReader(charset: Charset = Charsets.UTF_8): Reader =
    InputStreamReader(this.toInputStream(), charset)

fun BufferedSink.toWriter(charset: Charset = Charsets.UTF_8): Writer =
    OutputStreamWriter(this.toOutputStream(), charset)
```

#### 3.3.3 flush 시점 주의 + close 체인 보장

OkIO `BufferedSink`는 `close()` 시점에 flush를 보장하지만, 중간 단계 `OutputStreamWriter` → `BufferedSink` 사이에서 close 누락 시 일부 segment가 디스크에 안 내려갈 수 있다. 특히 **`BufferedSink.outputStream()`이 반환하는 OutputStream은 underlying `BufferedSink`를 close하지 않는다** — `OutputStream.close()`는 `flush()`만 부르고 sink 자체는 살려둔다. 따라서 사용자 코드가 다음 순서로 close하지 않으면 마지막 segment(8KB 이하)가 손실될 수 있다.

##### 권장 패턴 1 — 수동 close 체인 (안전한 nested `use`)

```kotlin
GraphIoOkioPaths.openSink(sink).use { bs ->
    bs.outputStream().use { os ->
        OutputStreamWriter(os, Charsets.UTF_8).use { w ->
            w.write("...")
        }
        // OutputStreamWriter.close() → os.flush() 호출
        // os.close() → bs.flush() 호출 (BufferedSink는 살아있음)
    }
    // bs.use {}가 마지막에 bs.close() 호출 → 디스크 flush 보장
}
```

##### 권장 패턴 2 — 고수준 헬퍼 `writeAsOutputStream(sink, block)`

graph-io-okio는 close 책임을 헬퍼가 가져가는 단축 함수를 제공한다.

```kotlin
// bridge/Bridges.kt
inline fun writeAsOutputStream(
    sink: OkioGraphExportSink,
    block: (OutputStream) -> Unit,
) {
    GraphIoOkioPaths.openSink(sink).use { bs ->
        bs.asClosingOutputStream().use { os -> block(os) }
    }
}

inline fun readAsInputStream(
    source: OkioGraphImportSource,
    block: (InputStream) -> Unit,
) {
    GraphIoOkioPaths.openSource(source).use { bs ->
        bs.inputStream().use { ins -> block(ins) }
    }
}
```

##### `asClosingOutputStream()` 래퍼

기본 `BufferedSink.outputStream()`은 OutputStream.close() 시 underlying sink를 닫지 않는다.
`asClosingOutputStream()`은 이 문제를 해결한다 — "Closing"은 "반환된 OutputStream을 close하면 sink도 함께 닫힘"을 의미한다.

```kotlin
/**
 * BufferedSink를 감싸는 OutputStream을 반환한다. 일반 [outputStream]과 달리,
 * 반환된 OutputStream의 `close()`가 underlying BufferedSink도 명시적으로 닫는다.
 *
 * 이름 이유: "Closing"은 반환된 OutputStream을 close하면 underlying sink가 함께 닫힘을 의미.
 * StAX/Jackson 등 외부 라이브러리가 OutputStream만 받는 경우 단일 try-with-resources로
 * sink까지 안전하게 닫을 수 있다.
 *
 * 호출자가 OutputStream을 닫지 않으면 sink도 닫히지 않음 — 반드시 `use { }` 사용.
 */
fun BufferedSink.asClosingOutputStream(): OutputStream =
    object: OutputStream() {
        private val delegate = this@asClosingOutputStream.outputStream()
        override fun write(b: Int) = delegate.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = delegate.write(b, off, len)
        override fun flush() { delegate.flush() }
        override fun close() {
            try { delegate.close() } finally { this@asClosingOutputStream.close() }
        }
    }
```

이 래퍼와 헬퍼는 §5.2 위험 (마지막 segment 손실)에 대한 1차 방어선이다.

#### 3.3.4 `OkioGraphBulkImporter` / `OkioGraphBulkExporter` 포맷 디스패치

`OkioGraphBulkImporter`는 `GraphBulkImporter<OkioGraphImportSource>` 계약을 구현하고, **`format: GraphIoFormat` 파라미터로 포맷을 명시적으로 선택**한다.

```kotlin
enum class GraphIoFormat { CSV, NDJSON_JACKSON2, NDJSON_JACKSON3, GRAPHML }

class OkioGraphBulkImporter(
    private val ops: GraphOperations,
) : GraphBulkImporter<OkioGraphImportSource> {

    override fun importGraph(
        source: OkioGraphImportSource,
        format: GraphIoFormat,
        options: GraphImportOptions,
    ): GraphImportReport {
        return GraphIoOkioPaths.openSource(source).use { bs ->
            when (format) {
                GraphIoFormat.CSV         -> importCsv(bs, ops, options)
                GraphIoFormat.NDJSON_JACKSON2 -> importNdjsonJackson2(bs, ops, options)
                GraphIoFormat.NDJSON_JACKSON3 -> importNdjsonJackson3(bs, ops, options)
                GraphIoFormat.GRAPHML     -> importGraphML(bs, ops, options)
            }
        }
    }
}
```

> **설계 결정**: 파일 확장자 기반 자동 포맷 스니핑을 **금지**한다. 확장자는 신뢰할 수 없으며 보안 취약점(Sec-MEDIUM-2)을 유발한다. 포맷은 항상 호출자가 명시적으로 지정한다. 알 수 없는 포맷 → `IllegalArgumentException` 즉시 던짐.

`OkioGraphBulkExporter`도 동일한 `format: GraphIoFormat` 패턴을 따른다.

### 3.4 기존 포맷 모듈의 OkIO 오버로드 확장 함수

`graph-io-okio` 모듈은 기존 4개 포맷 모듈에 **의존성을 추가**하고 (compile-time), 다음 패턴의 확장 함수를 제공한다.

> **명명 규칙**: 기존 graph-io-csv/jackson2/3/graphml의 공개 메서드명은 `exportGraph(...)` / `importGraph(...)`이다. 신규 OkIO 확장 함수도 동일한 동사를 사용한다 (`exportTo`/`importFrom` 같은 별도 동사 도입 금지 — 일관성 유지).

```kotlin
// extension/CsvOkioExtensions.kt
fun GraphCsvBulkExporter.exportGraph(
    sink: OkioGraphExportSink,
    options: CsvBulkExportOptions = CsvBulkExportOptions(),
) {
    writeAsOutputStream(sink) { os ->
        this.exportGraph(os, options)  // 기존 OutputStream 기반 API 재사용
    }
}

fun GraphCsvBulkExporter.exportGraphGzip(
    sink: OkioGraphExportSink,
    options: CsvBulkExportOptions = CsvBulkExportOptions(),
) {
    GraphIoOkioPaths.openGzipSink(sink).use { bs ->
        bs.asClosingOutputStream().use { os ->
            this.exportGraph(os, options)
        }
    }
}
```

이 패턴의 장점:

- 기존 모듈의 시그니처 / API 변경 없음.
- 사용자는 `graph-io-okio` 모듈만 추가하면 OkIO 변형이 즉시 사용 가능.
- 압축 변형도 자연스럽게 노출 (`exportGraphGzip`, `importGraphGzip` 등).

#### 3.4.1 어떤 확장 함수를 노출할지

| 모듈 | 함수 |
|------|------|
| CSV | `GraphCsvBulkImporter.importGraph(OkioGraphImportSource)`, `importGraphGzip` |
| CSV | `GraphCsvBulkExporter.exportGraph(OkioGraphExportSink)`, `exportGraphGzip` |
| Jackson2/3 NDJSON | 동일 패턴 (`importGraph` / `exportGraph` 명명) |
| GraphML | 동일 패턴 (단, StAX와의 BufferedSink flush 타이밍 검증 필요 — §5 참조) |

> **암호화 변형** (`importGraphGzipEncrypted` / `exportGraphGzipEncrypted`): `bluetape4k-projects` #240 완료 후 다음 버전에서 추가.

#### 3.4.2 GraphML XXE 강화 (보안 필수)

GraphML import 경로에서 사용하는 StAX `XMLInputFactory`는 **반드시** 다음 두 속성을 설정해야 한다.

```kotlin
// GraphML OkIO import 경로의 StAX 팩토리 설정
val factory = XMLInputFactory.newInstance().apply {
    setProperty(XMLInputFactory.SUPPORT_DTD, false)
    setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false)
    // 추가 방어 (JDK 버전에 따라 지원 여부 다름)
    runCatching {
        setProperty("javax.xml.stream.isSupportingExternalEntities", false)
    }
}
```

`SUPPORT_DTD = false`만으로는 일부 JDK 버전에서 external entity injection이 가능하므로, `IS_SUPPORTING_EXTERNAL_ENTITIES = false`를 함께 설정한다.

> **주의**: 기존 `graph-io-graphml`의 `StaxGraphMlReader`도 동일한 설정이 필요하다 — 별도 이슈로 추적.

**필수 음성 테스트** (§4.2.2): XXE payload가 포함된 GraphML 입력 시 외부 파일이 읽히지 않음을 검증한다.

```xml
<!-- XXE 페이로드 예시 — 테스트 픽스처 -->
<?xml version="1.0"?>
<!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
<graphml><graph><node id="&xxe;"/></graph></graphml>
```

### 3.5 VirtualThread / Suspend 변형

기존 `graph-io-bulk-import-export-design.md` 패턴을 그대로 따른다.

> **압축 옵션 비노출 정책**: 어댑터(VT/Suspend)는 **압축 알고리즘을 직접 파라미터로 받지 않는다**. 대신 호출자가 압축을 미리 체이닝해 만든 `Source`/`Sink`를 `OkioGraphImportSource.SourceBased` / `OkioGraphExportSink.SinkBased`로 감싸서 어댑터에 전달한다. 이렇게 하면 (a) 어댑터 시그니처가 단순해지고, (b) 사용자가 임의의 체이닝(예: Gzip → Zstd 등)을 자유롭게 구성할 수 있다.

```kotlin
// 사용 예 — 호출자가 압축 체이닝을 외부에서 구성
val compressed = GraphIoOkioPaths.openGzipSink(
    OkioGraphExportSink.PathSink("/tmp/graph.graphml.gz".toPath()),
)
adapter.exportGraph(
    sink = OkioGraphExportSink.SinkBased(compressed, ownsSink = true),
    format = GraphIoFormat.GRAPHML,
)
// 암호화 체이닝은 bluetape4k-projects #240 완료 후 추가 예정
```

#### 3.5.1 VirtualThread 변형

```kotlin
class VirtualThreadGraphIoOkioBulkAdapter(
    private val ops: GraphOperations,
    private val parallelism: Int = Runtime.getRuntime().availableProcessors(),
) {
    fun importGraph(source: OkioGraphImportSource, format: GraphIoFormat, ...) { ... }
    fun exportGraph(sink: OkioGraphExportSink, format: GraphIoFormat, ...) { ... }
}
```

- 내부적으로 `Executors.newVirtualThreadPerTaskExecutor()` 사용.
- `bluetape4k_virtualthread_jdk25` 의존성 추가 (CLAUDE.md 메모리: graph-core 외 모듈에 명시적 추가 필요).

#### 3.5.2 Suspend 변형

##### `GraphImportProgress` / `GraphExportProgress` 타입 정의 (신규 — `graph-io/core`)

`Flow` 기반 어댑터에서 방출하는 진행 상황 타입. `graph-io/core` 모듈에 추가한다.

```kotlin
// graph-io/core — io.bluetape4k.graph.io.contract
data class GraphImportProgress(
    val processed: Long,
    val total: Long?,
    val currentLabel: String?,
    val throughputPerSec: Double?,
)

data class GraphExportProgress(
    val exported: Long,
    val total: Long?,
    val currentLabel: String?,
    val throughputPerSec: Double?,
)
```

> **기존 타입 구분**: `GraphImportReport` / `GraphExportReport`는 완료 후 결과를 나타내고, `*Progress`는 진행 중 스냅샷을 나타낸다. 두 타입은 목적이 다르므로 병존한다.

##### `SuspendGraphIoOkioBulkAdapter` 선언

```kotlin
class SuspendGraphIoOkioBulkAdapter(
    private val ops: GraphSuspendOperations,
) {
    /**
     * 진행 상황을 emit하는 cold Flow. collect 시점에 I/O가 시작된다.
     * 반환 Flow는 cold이며 복수 collect 시 각각 독립적인 I/O가 발생함.
     */
    fun importGraph(source: OkioGraphImportSource, format: GraphIoFormat, ...): Flow<GraphImportProgress>
    fun exportGraph(sink: OkioGraphExportSink, format: GraphIoFormat, ...): Flow<GraphExportProgress>

    /** 진행 상황 없이 결과만 반환. */
    suspend fun importGraphAwait(source: OkioGraphImportSource, format: GraphIoFormat, ...): GraphImportReport
    suspend fun exportGraphAwait(sink: OkioGraphExportSink, format: GraphIoFormat, ...): GraphExportReport
}
```

##### 코루틴 취소 안전성 (필수)

OkIO `BufferedSink.flush()` / `close()`는 코루틴 취소에 안전하지 않다. 취소 시 마지막 segment가 flush 없이 유실될 수 있다.

**필수**: suspend 어댑터는 terminal `flush()` + `close()` 를 반드시 `withContext(NonCancellable)` 로 감싼다.

```kotlin
// SuspendGraphIoOkioBulkAdapter 내부 — 필수 패턴
try {
    // I/O 작업 (취소 가능)
    runInterruptible(Dispatchers.IO) { /* ... */ }
} finally {
    withContext(NonCancellable) {
        sink.flush()
        sink.close()
    }
}
```

> **추가 고려**: `PathSink` + `atomicWrite = true`(§3.2.3)를 사용하면 취소 시 임시 파일이 삭제되어 부분 기록 파일이 남지 않는다.

기타:
- `Flow`로 진행 상황 emit.
- I/O는 `runInterruptible(Dispatchers.IO)` 사용 — 스레드 인터럽트로 취소 신호 전달.
- segment 단위 backpressure가 자연스럽게 작동 (suspend pull 모델 + OkIO lazy pull). 압축 체이닝도 동일하게 lazy pull 유지.
- 로깅은 `KLoggingChannel` 사용 (suspend 컨텍스트에서 안전한 채널 기반 logger).

##### I/O 타임아웃 (MEDIUM)

OkIO `Source`/`Sink`는 `Timeout`을 통한 타임아웃을 지원한다. suspend 어댑터는 직접 타임아웃을 내장하지 않고, 호출자가 `withTimeout { adapter.importGraphAwait(...) }` 또는 OkIO `source.timeout().deadline(n, TimeUnit.SECONDS)` 로 설정하도록 KDoc에 안내한다.

### 3.6 암호화 — 현재 버전 범위 외 (후속 버전 예정)

#### 3.6.1 배경 및 연기 이유

`bluetape4k-okio`의 현재 `TinkDecryptSource`는 **전체 파일을 메모리에 적재 후 일괄 복호화**하는 구조로, 스트리밍 복호화를 지원하지 않는다. 또한 `TinkEncryptSink`가 다중 write 호출로 생성한 암호문을 `TinkDecryptSource`가 올바르게 복호화하지 못하는 구조적 결함이 존재한다.

이 문제의 해결(DAEAD 청크형 스트리밍 암호화/복호화 구현)은 **`bluetape4k-projects` 이슈 #240**으로 추적한다.

> **`bluetape4k-projects` 이슈 #240**: `TinkDecryptSource` 스트리밍 복호화 및 DAEAD 청크형 스트리밍 암호화(`DaeadChunkEncryptSink` / `DaeadChunkDecryptSource`) 지원 요청.

#### 3.6.2 현재 버전 (`graph-io-okio` 초기 릴리즈)의 암호화 지원

- **암호화 API 미포함**: `GraphIoOkioPaths`에 `openDaeadEncryptedSink` / `openDaeadDecryptedSource` 함수를 제공하지 않는다.
- `OkioEncryptionPolicy`: 구현하지 않는다.
- DoD 매트릭스에서 `*Encrypted` / `*GzipEncrypted` 케이스는 모두 N/A (후속 버전 대상).

#### 3.6.3 다음 버전 계획 (참고용)

`bluetape4k-projects` #240이 해결되면 다음 API를 추가한다.

```kotlin
// 향후 추가 예정 — 현재 버전 미구현
fun GraphIoOkioPaths.openDaeadEncryptedSink(
    sink: BufferedSink,
    daead: TinkDeterministicAead,
    chunkSize: Int = 64 * 1024,
): BufferedSink

fun GraphIoOkioPaths.openDaeadDecryptedSource(
    source: BufferedSource,
    daead: TinkDeterministicAead,
): BufferedSource

// 단축형
fun GraphIoOkioPaths.openGzipDaeadEncryptedSink(...): BufferedSink
fun GraphIoOkioPaths.openDaeadDecryptedGzipSource(...): BufferedSource
```

Wire format 설계 (참고):
```
[8-byte big-endian: ciphertext_len][N bytes: DAEAD(AES-SIV) ciphertext] × 반복
```

### 3.7 Compressor 의존성 처리

| 압축 | 라이브러리 | 의존성 처리 | classpath 검사용 클래스명 |
|------|----------|------------|---------------------------|
| Gzip | JDK 내장 (`java.util.zip`) | 추가 의존성 없음 | (검사 불필요) |
| Deflate | JDK 내장 | 추가 의존성 없음 | (검사 불필요) |
| LZ4 | `lz4-java` | optional, `compileOnly` + 사용자가 직접 추가 | `net.jpountz.lz4.LZ4Factory` |
| Snappy | `snappy-java` | optional, `compileOnly` | `org.xerial.snappy.Snappy` |
| Zstd | `zstd-jni` | optional, `compileOnly` | `com.github.luben.zstd.ZstdInputStream` |
| Bzip2 | `commons-compress` | optional, `compileOnly` | `org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream` |

기본 `implementation` 의존성은 `bluetape4k-okio`만 두고, LZ4/Snappy/Zstd/Bzip2는 `compileOnly`로 둔다. 사용자가 해당 압축을 사용하려면 명시적으로 의존성을 추가해야 하며, 미추가 시 **`IllegalStateException`**과 함께 명확한 가이드 메시지를 던진다.

```kotlin
fun openCompressedSink(sink: BufferedSink, compressor: Compressor): BufferedSink {
    // ⚠️ 중요: 모두 Compressors.Streaming.* 사용 — 배치(Compressors.GZip 등)는 OOM 위험.
    return when (compressor) {
        // GZIP, DEFLATE: JDK 내장 — 추가 의존성 없음. Streaming 변형 사용.
        GZIP    -> Compressable.Sinks.compressableSink(sink, Compressors.Streaming.GZip).buffer()
        DEFLATE -> Compressable.Sinks.compressableSink(sink, Compressors.Streaming.Deflate).buffer()
        LZ4 -> {
            requireOnClasspath("net.jpountz.lz4.LZ4Factory") {
                """LZ4 압축을 사용하려면 build.gradle.kts에 다음을 추가하세요:
                   |  implementation("org.lz4:lz4-java:${'$'}{Libs.lz4_java}")""".trimMargin()
            }
            Compressable.Sinks.compressableSink(sink, Compressors.Streaming.LZ4).buffer()
        }
        SNAPPY -> {
            requireOnClasspath("org.xerial.snappy.Snappy") {
                """Snappy 압축을 사용하려면 build.gradle.kts에 다음을 추가하세요:
                   |  implementation("org.xerial.snappy:snappy-java:${'$'}{Libs.snappy_java}")""".trimMargin()
            }
            Compressable.Sinks.compressableSink(sink, Compressors.Streaming.Snappy).buffer()
        }
        ZSTD -> {
            requireOnClasspath("com.github.luben.zstd.ZstdInputStream") {
                """Zstd 압축을 사용하려면 build.gradle.kts에 다음을 추가하세요:
                   |  implementation("com.github.luben:zstd-jni:${'$'}{Libs.zstd_jni}")""".trimMargin()
            }
            Compressable.Sinks.compressableSink(sink, Compressors.Streaming.Zstd).buffer()
        }
        BZIP2 -> {
            requireOnClasspath("org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream") {
                """Bzip2 압축을 사용하려면 build.gradle.kts에 다음을 추가하세요:
                   |  implementation("org.apache.commons:commons-compress:${'$'}{Libs.commons_compress}")""".trimMargin()
            }
            Compressable.Sinks.compressableSink(sink, Compressors.Streaming.BZip2).buffer()
        }
    }
}

/** classpath에서 클래스를 찾지 못하면 IllegalStateException을 던지는 헬퍼 */
private inline fun requireOnClasspath(className: String, lazyMessage: () -> String) {
    try { Class.forName(className) }
    catch (e: ClassNotFoundException) { throw IllegalStateException(lazyMessage(), e) }
}
```

#### 3.7.1 선택적 압축 라이브러리 의존성 추가 가이드 (빌드 스니펫)

graph-io-okio의 `build.gradle.kts`에는 LZ4/Snappy/Zstd/Bzip2가 `compileOnly`로 선언된다. 사용자가 해당 압축을 쓰려면 자신의 프로젝트 `build.gradle.kts`에 **명시적으로** 추가해야 한다.

```kotlin
// 선택적 압축 라이브러리 — 필요한 것만 추가
dependencies {
    // LZ4: 빠른 압축/해제, 일반 목적 권장
    implementation("org.lz4:lz4-java:${Libs.lz4_java}")

    // Snappy: 낮은 지연 시간이 중요한 경우
    implementation("org.xerial.snappy:snappy-java:${Libs.snappy_java}")

    // Zstd: 높은 압축률이 필요한 경우
    implementation("com.github.luben:zstd-jni:${Libs.zstd_jni}")

    // Bzip2: 표준 호환성이 필요한 경우 (commons-compress 기반)
    implementation("org.apache.commons:commons-compress:${Libs.commons_compress}")
}
```

> 버전값(`Libs.lz4_java` 등)은 `buildSrc/src/main/kotlin/Libs.kt`에서 관리한다.

### 3.8 예외 계약 (Exception Contract)

graph-io-okio의 공개 API는 다음 예외 계층을 따른다.

| 실패 시나리오 | 던지는 예외 | 비고 |
|---------------|------------|------|
| 압축 해제 폭탄 (maxDecompressedBytes 초과) | `java.io.IOException("decompression budget exceeded")` | §3.2 `openDecompressedSource` 참조 |
| 손상된 gzip/zstd/snappy 스트림 | `java.io.IOException` (또는 래퍼 `UncheckedIOException`) | 손실 없이 surface 보장 — 조용한 실패 금지 |
| UTF-8 인코딩 오류 (MalformedInput) | `java.nio.charset.MalformedInputException` | Reader 브리지에서 발생 |
| GraphML 파싱 오류 (mid-stream) | `javax.xml.stream.XMLStreamException` | StAX 예외 래핑 없이 전파 |
| 선택적 compressor classpath 미추가 | `java.lang.IllegalStateException` | 가이드 메시지 포함 — §3.7 참조 |
| FileSystem I/O 오류 (권한, 디스크 풀) | `java.io.IOException` | OkIO FileSystem에서 전파 |
| 원자적 쓰기 실패 (atomicMove) | `java.io.IOException` | 임시 파일 삭제 후 원본 예외 전파 |
| 코루틴 취소 | `kotlinx.coroutines.CancellationException` | **반드시 재던짐** — 내부에서 잡아서 삼키지 않음 |
| `format` 파라미터 미지원 값 | `java.lang.IllegalArgumentException` | `when` exhaustive 처리 |

> **규칙**: `CancellationException`은 `catch (e: Exception)` 블록에서 포착되더라도 반드시 `throw e`로 재던진다 (Kotlin 코루틴 불변 조건).

**Java 호출자 대비**: 공개 동기 API에는 `@Throws(IOException::class)` 어노테이션을 추가한다.

---

## 4. 완료 기준 (Definition of Done)

### 4.1 기능 요구사항

- [ ] **`GraphImportProgress` / `GraphExportProgress` data class** 를 `graph-io/core` 의 `io.bluetape4k.graph.io.contract` 패키지에 추가 (§3.5.2 참조).
- [ ] `OkioGraphImportSource` / `OkioGraphExportSink` sealed interface 구현 (3 variants × 2 = 6 case).
- [ ] `GraphIoOkioPaths` 헬퍼 함수 구현 (open, 압축 체이닝, 단축형). **암호화 함수는 후속 버전 대상.**
- [ ] OkIO ↔ java.io 브리지 함수 (`toInputStream`, `toOutputStream`, `asClosingOutputStream`, `toReader`, `toWriter`, `writeAsOutputStream`, `readAsInputStream`).
- [ ] **Sync API**: `OkioGraphBulkImporter` / `OkioGraphBulkExporter` — `GraphBulkImporter<OkioGraphImportSource>` / `GraphBulkExporter<OkioGraphExportSink>` 구현 (기존 graph-io 관례 준수).
- [ ] 기존 4개 포맷 모듈에 OkIO 확장 함수 (CSV, Jackson2/3, GraphML) — 명명: `importGraph` / `exportGraph` (기존 API와 일치). 암호화 변형(`importGraphGzipEncrypted` 등)은 후속 버전.
- [ ] VirtualThread 변형 (`VirtualThreadGraphIoOkioBulkAdapter`).
- [ ] Suspend 변형 (`SuspendGraphIoOkioBulkAdapter`) — `KLoggingChannel` 사용.

#### 4.1.1 기존 4개 포맷 모듈 OkIO 변형 DoD 매트릭스

각 셀은 round-trip(import → export → import 결과 동일) 통합 테스트로 검증한다.

| 포맷 | import | importGzip | export | exportGzip |
|------|:---:|:---:|:---:|:---:|
| CSV | [ ] | [ ] | [ ] | [ ] |
| Jackson2 NDJSON | [ ] | [ ] | [ ] | [ ] |
| Jackson3 NDJSON | [ ] | [ ] | [ ] | [ ] |
| GraphML | [ ] | [ ] | [ ] | [ ] |

총 16 round-trip 케이스 (4 × 4). 암호화 케이스(`*Encrypted`, `*GzipEncrypted`)는 §3.6에 따라 **후속 버전 대상** — `bluetape4k-projects` #240 해결 후 추가.

### 4.2 테스트 요구사항

#### 4.2.1 Happy-path 테스트

- [ ] `okio.fakefilesystem.FakeFileSystem` 기반 단위 테스트 (모든 테스트 클래스에 `@TestInstance(PER_CLASS)` + `@BeforeAll`/`@AfterAll`로 `FakeFileSystem` 라이프사이클 관리, `@AfterAll`에서 `fakeFileSystem.checkNoOpenFiles()` 호출로 누수 검증)
  - PathSource / PathSink round-trip
  - SourceBased / SinkBased round-trip
  - InputStreamBased / OutputStreamBased round-trip
- [ ] 압축 round-trip 테스트 (Gzip 필수, Deflate 필수, LZ4/Snappy/Zstd는 skip-when-absent)
- [ ] **암호화 테스트는 후속 버전 대상** (`bluetape4k-projects` #240 해결 후 추가). 현재 버전에서는 암호화 관련 테스트 포함하지 않음.
- [ ] **graph-io-csv round-trip이 OkIO 경로에서도 성공**: 기존 CSV 테스트 픽스처 재사용해서 OkIO 헬퍼로 통과 확인
- [ ] graph-io-jackson2/3, graph-io-graphml 동일하게 OkIO round-trip 통과
- [ ] VirtualThread 변형 동시성 테스트 (10K vertex 동시 import/export)
- [ ] Suspend 변형 Flow 진행 emit 검증
- [ ] 80%+ 라인 커버리지 (JaCoCo)

#### 4.2.2 Negative-path 테스트 (필수)

운영 환경에서 자주 발생하는 실패 시나리오에 대한 명확한 에러 처리를 검증한다.

- [ ] **빈 Source (0바이트)** → 빈 graph가 정상 생성됨 (예외 없이 vertex/edge count = 0 반환)
- [ ] **Truncated/corrupt gzip stream** → `java.io.IOException`이 명확하게 surface (UncheckedIOException 등으로 wrapping된 채 lost되지 않음)
- [ ] **compileOnly 미추가 상태에서 LZ4/Snappy/Zstd/Bzip2 호출** → `IllegalStateException` + 가이드 메시지 (build.gradle.kts 추가 안내 포함)
- [ ] **깨진 charset (예: UTF-8 stream을 ISO-8859-1로 디코딩 시 illegal byte)** → `java.nio.charset.MalformedInputException`
- [ ] **마지막 segment 손실 검증 (round-trip)**: 8KB 미만으로 끝나는 데이터(예: 7KB 한 줄짜리)를 export 후 import해서 byte 손실 없는지 확인 — `asClosingOutputStream()` 패턴 검증의 핵심 케이스
- [ ] **XXE 방어 테스트** (`GraphMLOkioExtensionsTest`): XXE payload 포함 GraphML 파일 import 시 로컬 파일이 읽히지 않음을 검증 (§3.4.2 픽스처 사용). `XMLStreamException`이 던져지거나 entity가 확장되지 않아야 함.
- [ ] **압축 폭탄 방어 테스트**: 1KB 내 압축 파일이 maxDecompressedBytes(512MB) 초과 시 `IOException("decompression budget exceeded")` 발생 검증.
- [ ] **원자적 쓰기 검증**: PathSink + `atomicWrite = true` 상태에서 export 도중 예외를 시뮬레이션하면 대상 파일이 오염되지 않음(부분 기록 없음) 검증. FakeFileSystem 사용.
- [ ] **ownsSource/ownsSink 기본값 검증**: `SourceBased(ownsSource = false)` 로 import 후 Source가 닫히지 않음을 확인 (`FakeFileSystem.checkNoOpenFiles()` 비보고 방식으로 검증).
- [ ] **포맷 디스패치 — 미지원 포맷**: `GraphIoFormat`에 없는 포맷 전달 시 `IllegalArgumentException` 발생 검증 (when exhaustive 처리 확인).
- [ ] **취소 안전성**: suspend 어댑터 export 도중 코루틴 취소 시 `CancellationException`이 재던져지고, `atomicWrite = true`면 임시 파일이 삭제됨을 검증.
- **암호화 관련 Negative 테스트**: `bluetape4k-projects` #240 해결 후 추가 (key mismatch, REJECT 정책 등).

### 4.3 문서

- [ ] 모든 public API에 한국어 KDoc.
  - 압축 체이닝 시 처리 순서 명시.
  - close 책임 명시 (`@param ownsSource`, `@param ownsSink`, `@param atomicWrite`).
  - CSV의 OkIO heap 이점 제약 명시 (§1.3.1).
  - 예외 계약 명시 (§3.8 표 참조).
  - `@Throws(IOException::class)` — 공개 동기 API 전체.
- [ ] `graph-io/okio/README.md` (영문), `graph-io/okio/README.ko.md` (한국어)
  - 사용 예시 6개 이상 (§8 코드 블록 기반)
  - 압축 의존성 추가 가이드 (§3.7.1 스니펫 기반)
  - 암호화: "다음 버전 예정 — `bluetape4k-projects` #240" 명시
  - java.io vs OkIO 경로 선택 가이드
  - 기존 `graph-io-csv/README.ko.md` 구조를 템플릿으로 사용
- [ ] **bluetape4k 패턴 준수** (구현 시 검증):
  - 입력 검증: `requireNotBlank`, `requireInRange` (bluetape4k-support)
  - 로깅: `KLogging` (동기), `KLoggingChannel` (suspend 컨텍스트)
  - value class 후보: 없음 (현재 버전 — 향후 `GraphElementId` 재사용)
  - companion factory에 `@JvmStatic` (Java 호환)
  - `@Throws(IOException::class)` 동기 공개 API 전체

### 4.4 빌드 / 통합

- [ ] `settings.gradle.kts` 자동 탐색으로 `:graph-io-okio` 모듈이 자동 등록됨을 확인 (별도 `include(...)` 라인 추가 불필요).
- [ ] `build.gradle.kts`:
  - `api(Libs.bluetape4k_okio)` — `BufferedSource`/`BufferedSink`를 public API 시그니처에 노출하므로 `implementation`이 아닌 `api` 선언.
  - `implementation(Libs.bluetape4k_virtualthread_jdk25)` — graph-core 외 모듈에 명시적 추가 필수 (CLAUDE.md 메모리).
  - `implementation` — 기존 graph-io 모듈 (`graph-io-core`, `graph-io-csv`, `graph-io-jackson2/3`, `graph-io-graphml`).
  - `compileOnly` — `lz4-java`, `snappy-java`, `zstd-jni`, `commons-compress`.
  - `testImplementation(Libs.okio_fakefilesystem)` — `FakeFileSystem`을 사용한 단위 테스트.
- [ ] **BOM 자동 포함**: `bluetape4k-graph-bom`은 `examples/` 미포함 모듈을 자동으로 수집하므로, `:graph-io-okio` 디렉토리가 만들어지고 `settings.gradle.kts` 자동 탐색에 포착되면 BOM에 자동 등록된다 — **수동 BOM 등록 단계 불필요**.
  - 검증: `./gradlew :bluetape4k-graph-bom:dependencies | grep graph-io-okio` 결과에서 신규 모듈이 표시됨.
- [ ] `./gradlew :graph-io-okio:build :graph-io-okio:test` 통과.
- [ ] `./gradlew build -x test` 전체 통과 (다른 모듈 회귀 없음).

#### 4.4.1 CI job 배치

`graph-io-okio`는 Docker 컨테이너 의존이 없으므로(FakeFileSystem 사용), 무거운 nightly 잡이 아닌 빠른 fast-feedback 잡에 들어간다.

- [ ] **`.github/workflows/ci.yml`의 `test-core` 잡**에 `:graph-io-okio:test` 추가. 동시에 기존 graph-io 모듈들(`:graph-io-core`, `:graph-io-csv`, `:graph-io-jackson2`, `:graph-io-jackson3`, `:graph-io-graphml`)도 `test-core` 잡에 함께 포함되어 있는지 확인하고, 누락 시 함께 추가한다.
- [ ] **`.github/workflows/nightly.yml`**에도 동일하게 `:graph-io-okio:test`를 추가 (회귀 감지).
- [ ] **JMH 벤치마크는 수동 실행**: `benchmark/graph-io-benchmark`의 OkIO 추가 벤치들은 PR/nightly에서 자동 실행하지 않고, GitHub Actions `workflow_dispatch` 또는 로컬 명령으로만 실행한다 (실행 시간이 길어 CI 부하를 키움).

### 4.5 벤치마크 (CLAUDE.md 메모리: 신규 모듈 완료 기준)

- [ ] `benchmark/graph-io-benchmark`에 OkIO 벤치 추가:
  - 100K vertex CSV export (java.io vs OkIO vs OkIO+Gzip)
  - 100K vertex CSV import (java.io vs OkIO vs OkIO+Gzip)
  - 100K vertex GraphML export (java.io vs OkIO)
  - VirtualThread vs Sync OkIO export
- [ ] 결과를 README.ko.md "성능" 섹션에 표로 기록 (heap allocation 차이 포함).

---

## 5. 위험 요소 및 트레이드오프

### 5.1 암호화 미포함 (현재 버전)

- **위험**: 암호화가 필요한 사용자가 대안 없이 대기해야 함.
- **확률**: 낮음. 초기 릴리즈에서 압축 + OkIO 추상화가 주 가치.
- **영향**: 낮음 (OkIO 도입의 핵심 가치는 압축 체이닝 + FileSystem 추상화).
- **완화**: README에 "암호화는 `bluetape4k-projects` #240 해결 후 다음 버전 추가 예정" 명시. 임시방편으로 외부 도구(`age`, `openssl`) 사용 안내.
- **장기 해결**: `bluetape4k-projects` #240 → `DaeadChunkEncryptSink/DecryptSource` → graph-io-okio 다음 버전.

### 5.2 StAX + OkIO 브리지 시 buffer flush 타이밍

- **위험**: GraphML export 시 `XMLStreamWriter` → `OutputStreamWriter` → `BufferedSink` 체인에서 outermost를 close하지 않으면 마지막 segment(8KB 이하)가 손실될 수 있음.
- **확률**: 낮음 (correctly written `use { ... }` 사용 시).
- **영향**: 높음 (조용한 데이터 손실).
- **완화**:
  - 모든 export 함수가 자체적으로 `use {}`로 감싸서 close 보장.
  - 통합 테스트에서 마지막 노드/간선이 정확히 round-trip 되는지 검증.
  - KDoc에 외부에서 `SinkBased` / `OutputStreamBased`를 넘긴 경우 close 책임 명시.

### 5.3 LZ4/Snappy 의존성 추가 시 빌드 크기 증가

- **위험**: 최종 사용자 jar 크기 증가.
- **확률**: 0% (compileOnly로 두기로 결정 → 사용자 명시적 추가 시에만 포함).
- **영향**: 없음 (compileOnly).
- **완화**: `compileOnly` + 런타임 classpath 검사 + 명확한 에러 메시지.

### 5.4 OkIO와 기존 java.io 코드 사이의 인지적 부담

- **위험**: 사용자가 두 가지 API(java.io 기반 GraphIoPaths vs OkIO 기반 GraphIoOkioPaths)를 어떻게 골라야 하는지 헷갈림.
- **확률**: 높음.
- **영향**: 낮음 (선택의 문제).
- **완화**:
  - README.ko.md "어떤 걸 써야 하나요?" 섹션 추가:
    - 기본: 기존 `GraphIoPaths` (단순함, 압축/암호화 불필요).
    - 대용량 + 압축/암호화 필요: `GraphIoOkioPaths`.
  - 두 API가 동일한 sealed 패턴을 따르도록 설계 (PathSource/PathSink 명명 일치).

### 5.5 segment 단위 처리의 측정 가능성

- **위험**: "OkIO가 heap에 유리하다"는 주장을 객관적으로 보여주지 못하면 채택 명분 약화.
- **확률**: 중간.
- **영향**: 중간 (PR 리뷰에서 push back 가능).
- **완화**: §4.5 벤치마크에 **JMH GC profiler** 추가 (`-prof gc`) → allocation rate (B/op) 비교. 보통 OkIO가 java.io BufferedReader 대비 30~70% 적은 allocation을 보인다.

### 5.6 기존 모듈 회귀

- **위험**: 확장 함수 추가가 기존 컴파일/링크에 영향.
- **확률**: 매우 낮음 (확장 함수는 기존 시그니처에 영향 없음).
- **영향**: 높음 (회귀 발생 시).
- **완화**: PR에서 기존 graph-io-csv/jackson/graphml 테스트 fully green 확인 (CI gate).

### 5.7 OkIO API 진화

- **위험**: square/okio가 3.x → 4.x 메이저 변경 시 영향.
- **확률**: 낮음 (OkIO는 안정적).
- **영향**: 낮음 (`bluetape4k-okio`가 absorb).
- **완화**: 직접 OkIO API를 노출하지 않고 `bluetape4k-okio` 함수만 호출 (이미 §3.2에서 채택).

### 5.8 XXE (XML External Entity Injection) — GraphML

- **위험**: 신뢰할 수 없는 GraphML 파일에 XXE payload가 삽입되면 로컬 파일(`/etc/passwd` 등)이 유출될 수 있음.
- **확률**: 낮음 (그래프 I/O 서버 측 처리 시 높아짐).
- **영향**: 높음 (보안 취약점).
- **완화**: §3.4.2 — `IS_SUPPORTING_EXTERNAL_ENTITIES = false` + `SUPPORT_DTD = false` 강제 설정. 음성 테스트 필수.

### 5.9 압축 해제 폭탄 (Decompression Bomb)

- **위험**: 1KB 압축 파일이 수십 GB로 팽창 → OOM / 디스크 소진.
- **확률**: 낮음 (내부 시스템 한정) ~ 높음 (외부 파일 수신 시).
- **영향**: 높음 (DoS).
- **완화**: §3.2 `openDecompressedSource`의 `maxDecompressedBytes` 파라미터 (기본 512MB). 초과 시 즉시 `IOException`.

### 5.10 코루틴 취소 시 데이터 손실

- **위험**: suspend 어댑터 export 도중 코루틴이 취소되면 `flush()`/`close()` 미호출 → 마지막 segment 손실.
- **확률**: 중간 (timeout 설정 있는 환경).
- **영향**: 높음 (조용한 데이터 손실 — 파일은 존재하지만 불완전).
- **완화**: §3.5.2 `NonCancellable` 블록으로 close 보장 + `atomicWrite = true`로 불완전 파일이 원본 경로에 도달하지 않도록 차단.

### 5.11 원자적 쓰기 없는 export 실패 시 파일 오염

- **위험**: export 도중 예외 시 대상 파일이 부분 기록된 채 남음 → 후속 import에서 손상 데이터 파싱.
- **확률**: 낮음.
- **영향**: 높음 (데이터 오염 — 오류와 구분 불가).
- **완화**: §3.2.3 `atomicWrite = true` (기본) — 임시 파일에 기록 후 `FileSystem.atomicMove`.

---

## 6. 후속 작업 (범위 제외)

- **암호화 지원**: DAEAD 청크형 스트리밍 (`DaeadChunkEncryptSink` / `DaeadChunkDecryptSource`) — `bluetape4k-projects` #240 해결 후 `graph-io-okio` 다음 버전에 추가. `openDaeadEncryptedSink`, `openDaeadDecryptedSource`, `openGzipDaeadEncryptedSink` 등 API 추가.
- AES-GCM streaming 자체 구현 (별도 이슈).
- HTTP/S3 원격 Source/Sink (별도 이슈).
- Spring Boot starter에 OkIO 변형 자동 등록.
- graph-io 통합 facade (기존 + OkIO를 통합한 single entry point).
- Async Channel(NIO) 기반 어댑터.

---

## 7. 결정 로그 (결정 로그)

| 결정 | 선택 | 대안 | 이유 |
|------|------|------|------|
| 모듈 분리 vs 기존 graph-io-core 확장 | 신규 `graph-io-okio` 모듈 | core에 추가 | core의 의존성 오염 방지, 점진적 채택 가능 |
| 압축을 sealed에 포함 vs 헬퍼 분리 | 헬퍼 분리 + 단축형 | sealed 안에 enum 박기 | 직교성, 향후 확장 용이 |
| Tink streaming 자체 구현 | 미포함, 별도 이슈 | 현재 spec에 포함 | 범위 폭발, AES-GCM stream은 독립 작업 |
| LZ4/Snappy 의존성 | `compileOnly` | `implementation` | jar 크기, 사용자 선택권 |
| OkIO API 직접 노출 | bluetape4k-okio 경유 + `api(...)` 선언 | implementation 으로 격리 | 의존성 격리, 진화 흡수. 단 BufferedSource/Sink가 public API에 등장하므로 `api` 필요 |
| 처리 순서 (압축 → 암호화) | compress-then-encrypt | encrypt-then-compress | 표준 관행, 암호문은 압축 안 됨 |
| 기존 모듈 침습 | 비침습 (확장 함수만) | 기존 API 수정 | 호환성, PR 리스크 최소화 |
| Sync API 제공 | `OkioGraphBulkImporter`/`Exporter` Sync 클래스 추가 | VT/Suspend 변형만 제공 | 기존 graph-io 4개 모듈이 모두 Sync × VT × Suspend 3종 세트로 노출 — 일관성 |
| 함수 명명 (확장) | `importGraph` / `exportGraph` | `importFrom` / `exportTo` | 기존 graph-io API와 일관 |
| `Flow` 반환 함수의 `suspend` 키워드 | `suspend` 미사용 (`fun ...: Flow<T>`) | `suspend fun ...: Flow<T>` | cold Flow는 collect 시점에 suspend되며 함수 자체는 suspend가 아님 |
| 암호화 지원 범위 | **DAEAD 청크 스트리밍만, 현재 버전 미포함** | AEAD(비결정적) 포함 | `TinkDecryptSource` 전체 메모리 로드 결함 → `bluetape4k-projects` #240으로 추적. 스트리밍 불가 암호화는 미지원 (§1.4, §3.6). |
| 암호화 구현 위치 | **`bluetape4k-projects`에서 먼저 해결** 후 graph-io-okio에 추가 | graph-io-okio 내부 구현 | 청크 포맷 표준화, 라이브러리 레벨 재사용성 확보. 현재 버전 범위에서 제외. |
| `OkioGraphImportSource` / `OkioGraphExportSink` `Serializable` 구현 | **비채택** (Serializable 미구현) | Serializable 구현 | `okio.Path`, `okio.FileSystem`이 Serializable 미구현. in-process 데이터 파이프라인 전용임을 KDoc에 명시. 직렬화가 필요한 분산 처리는 `java.nio.file.Path` 기반 기존 `GraphImportSource`/`GraphExportSink` 사용 |
| `okio.Path` vs `java.nio.file.Path` | `okio.Path` 채택 | `java.nio.file.Path` | `FakeFileSystem` 호환성, OkIO native API 일관성. 변환 헬퍼(`toOkioPath()`)는 OkIO 표준 제공 |
| BOM 등록 방식 | settings.gradle.kts 자동 탐색에 의존 | 수동 BOM 등록 | 기존 graph-io 모듈들과 동일 — 자동화로 누락 방지 |
| 압축 구현 방식 — 배치 vs 스트리밍 | `Compressors.Streaming.*` 사용 (`StreamingCompressSink`) | `Compressable.Sinks.gzip()` (배치 `CompressableSink`) | `CompressableSink`는 close 시 전체 메모리 적재 → OOM. `Compressors.Streaming.GZip`은 `GZIPOutputStream` 기반 진짜 스트리밍. |
| `ownsStream`/`ownsSource`/`ownsSink` 기본값 | `false` (호출자 소유) | `true` (라이브러리 소유) | 기존 `GraphImportSource.InputStreamSource.closeInput = false` 관례와 일치. `PathSource`/`PathSink`는 라이브러리가 항상 소유. |
| 포맷 선택 방식 | 명시적 `GraphIoFormat` 파라미터 | 파일 확장자 스니핑 | 확장자 스니핑은 보안 위험(확장자 스푸핑) + 모호성. 명시적 선택이 안전하고 예측 가능. |
| 원자적 쓰기 | `atomicWrite = true` 기본값 | 직접 덮어쓰기 | export 실패 시 부분 파일 오염 방지. `FakeFileSystem.atomicMove` 지원. opt-out 가능. |
| `maxDecompressedBytes` 기본값 | 512MB | 무제한 | 압축 폭탄 방어. 운영 환경에서 명시적 override 가능. |
| Progress 타입 위치 | `graph-io/core` 신규 타입 | `graph-io-okio` 내부 정의 | 재사용성 — 다른 포맷 모듈도 동일 타입 사용 가능. |
| XXE 방어 범위 | `SUPPORT_DTD + IS_SUPPORTING_EXTERNAL_ENTITIES = false` | DTD만 비활성화 | JDK 버전에 따라 DTD만으론 XXE 차단 불완전. 양쪽 모두 비활성화가 방어 심층화. |

---

## 8. 부록: 사용 예시 (KDoc/README 초안)

```kotlin
// (1) 가장 간단한 OkIO 경로 export — 고수준 헬퍼 사용
import io.bluetape4k.graph.io.okio.OkioGraphExportSink
import io.bluetape4k.graph.io.okio.GraphIoOkioPaths
import io.bluetape4k.graph.io.okio.bridge.writeAsOutputStream

// PathSink: 라이브러리가 소유 — 자동 close. atomicWrite=true(기본)로 실패 시 원본 보호.
val sink = OkioGraphExportSink.PathSink("/tmp/graph.csv".toPath())
writeAsOutputStream(sink) { os ->
    csvExporter.exportGraph(os)
}

// (2) Gzip 스트리밍 압축 export — Compressors.Streaming.GZip 사용 (배치 아님)
// close 체인: asClosingOutputStream()이 BufferedSink까지 함께 닫음.
GraphIoOkioPaths.openGzipSink(sink).use { bs ->
    bs.asClosingOutputStream().use { os ->
        csvExporter.exportGraph(os)
    }
}

// (3) Suspend 변형 — 진행 Flow 구독 (cold Flow — collect 시 I/O 시작)
val adapter = SuspendGraphIoOkioBulkAdapter(suspendOps)
// GraphExportProgress: exported, total, currentLabel, throughputPerSec
adapter.exportGraph(sink, GraphIoFormat.CSV).collect { progress: GraphExportProgress ->
    log.info { "${progress.exported}/${progress.total}" }
}

// (4) 호출자가 Gzip 체이닝 후 SinkBased로 전달 — 어댑터는 압축 옵션을 직접 받지 않음
val gzipSink = GraphIoOkioPaths.openGzipSink(
    OkioGraphExportSink.PathSink("/tmp/graph.graphml.gz".toPath())
)
adapter.exportGraph(
    OkioGraphExportSink.SinkBased(gzipSink, ownsSink = true),
    GraphIoFormat.GRAPHML,
).collect { /* ... */ }

// (5) SourceBased — ownsSource=false(기본): 호출자가 Source를 닫음
val mySource: BufferedSource = ...
adapter.importGraph(
    OkioGraphImportSource.SourceBased(mySource, ownsSource = false),
    GraphIoFormat.CSV,
).collect { progress: GraphImportProgress -> /* ... */ }
// mySource는 여기서 호출자가 직접 닫는다

// (6) 암호화 예시 — 후속 버전 예정 (bluetape4k-projects #240 해결 후)
// GraphIoOkioPaths.openGzipDaeadEncryptedSink(sink, daead) // 미구현
```

---

**Spec 끝.** 다음 단계: `Step 1-P` (planner agent로 task list 생성) → `Step 2-T` (TDD로 구현).
