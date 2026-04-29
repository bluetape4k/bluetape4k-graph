# Graph IO OkIO 모듈 설계 Spec

- **Issue**: #12 — graph-io에 OkIO 기반 Source/Sink 지원 추가
- **작성일**: 2026-04-29
- **작성자**: bluetape4k-graph 팀
- **상태**: Approved (Step 2-R 리뷰 반영 완료; 2026-04-29 최종 수정)
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

**결론**: CSV 경로에서 OkIO를 채택하는 1차 동기는 **압축/암호화 체이닝의 편의성 및 FileSystem 추상화**이지, segment 풀 자체의 heap 절약은 아니다. 사용자 KDoc/README에 이 사실을 명시한다.

#### 1.3.2 DAEAD 청크형 스트리밍 암호화의 heap 영향

§3.6에서 다루는 암호화 레이어는 **DAEAD(AES-SIV) 기반 청크형 스트리밍**으로 구현된다.

- **쓰기(encrypt)**: OkIO `write(source, byteCount)` 호출당 `chunkSize` 단위로 DAEAD 암호화 → 각 청크는 `[8-byte 길이][N-byte 암호문]` 포맷으로 Sink에 기록. 전체 plaintext를 메모리에 적재하지 않음.
- **읽기(decrypt)**: 청크 헤더 → 헤더 길이만큼 암호문 읽기 → DAEAD 복호화 → 반복. 청크 단위 lazy pull 유지.

결론: **압축 체인(Gzip 등)과 마찬가지로, DAEAD 청크 암호화는 segment 수준 lazy pull과 호환된다.** 기존 Tink AEAD의 "전체 파일 메모리 로드" 문제는 이 설계로 해소된다.

### 1.4 비목표 (Non-Goals)

- 기존 4개 포맷 모듈의 Java I/O 경로를 OkIO로 **대체**하지 않는다. 호환성 유지가 최우선.
- AEAD (비결정적 암호화) 지원: **DAEAD(결정적) 청크 스트리밍만** 지원. AEAD는 포함하지 않는다.
- AES-GCM / AES-CTR 자체 스트림 암호화 구현은 본 issue 범위 밖이다 (별도 이슈로 추적).
- Tink `StreamingAead` (AesGcmHkdfStreaming) 지원: `bluetape4k-okio`에 Streaming AEAD 래퍼가 추가된 후 swap 가능하도록 설계하되, 본 spec에서는 구현하지 않는다.
- Async I/O (Netty / NIO 채널) 어댑터는 본 모듈에서 제공하지 않는다.

---

## 2. 범위

### 2.1 신규 모듈

```
graph-io/
  okio/                                  # 신규 (이 spec 범위)
    src/main/kotlin/io/bluetape4k/graph/io/okio/
      OkioGraphImportSource.kt          # sealed interface
      OkioGraphExportSink.kt            # sealed interface
      GraphIoOkioPaths.kt               # 헬퍼: open Source/Sink, 압축/암호화 체이닝
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
- Tink 기반 암호화 체이닝 (단, 메모리 제약 명시)
- OkIO ↔ java.io 브리지 (기존 StAX, Jackson, CSV 파서/리더 호환)
- VirtualThread / Coroutine 변형 어댑터
- 기존 포맷 모듈에 OkIO 오버로드 확장 함수 (별도 모듈에서 제공, 기존 모듈 비침습)

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
        /** 호출자가 닫지 않도록 위임 여부 */
        val ownsSource: Boolean = true,
    ): OkioGraphImportSource

    /** java.io.InputStream을 OkIO Source로 어댑팅 */
    data class InputStreamBased(
        val inputStream: InputStream,
        val ownsStream: Boolean = true,
    ): OkioGraphImportSource
}
```

**설계 근거**:

- `okio.FileSystem`을 파라미터화 → 테스트에서 `FakeFileSystem`을 그대로 주입할 수 있다.
- `ownsSource` / `ownsStream` 플래그로 close 책임을 명시한다 — 외부에서 만든 stream을 헬퍼가 임의로 닫지 않도록 한다.
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
        val ownsSink: Boolean = true,
    ): OkioGraphExportSink

    data class OutputStreamBased(
        val outputStream: OutputStream,
        val ownsStream: Boolean = true,
    ): OkioGraphExportSink
}
```

#### 3.1.3 압축 파라미터를 sealed interface 안에 둘지, 별도 헬퍼로 분리할지

**결정: 별도 헬퍼로 분리한다 (권장안 채택)**.

| 옵션 | 장점 | 단점 |
|------|------|------|
| Sealed에 포함 (`PathSource(path, compression = Gzip)`) | 호출 1번으로 끝, 가독성↑ | enum이 sealed의 모든 variant에 분기로 박힘. 압축 옵션이 늘어날 때 (LZ4 level 등) 폭발. 암호화까지 더하면 cartesian product. |
| 별도 헬퍼 (`openGzipSource(openSource(...))`) | 직교성↑, 체이닝 자유 (Gzip+Tink, Zstd+Tink 등). 새로운 압축 추가 시 sealed 손대지 않음. | 호출이 2~3 단계로 길어짐. |

graph-io는 압축/암호화 조합이 늘어날 가능성이 높으므로(향후 Zstd + AES-GCM 등) **체이닝 모델**이 안전하다. 단축형 (`openGzipSink(sink)`)을 제공해 호출 길이는 보완한다.

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
     * @param compressor 압축 알고리즘 (Gzip, Lz4, Snappy, Zstd, Bzip2, Deflate)
     */
    fun openCompressedSink(sink: BufferedSink, compressor: Compressor): BufferedSink

    /** 기존 Source를 주어진 Decompressor로 감싼 BufferedSource를 반환. */
    fun openDecompressedSource(source: BufferedSource, decompressor: Compressor): BufferedSource

    // ------------ Gzip 단축형 (가장 흔한 케이스) ------------

    fun openGzipSink(sink: OkioGraphExportSink): BufferedSink
    fun openGzipSource(source: OkioGraphImportSource): BufferedSource

    // ------------ 조합 단축형 ------------
    // 암호화 API는 현재 버전 범위 외 (§3.6, §6 참조).
    // bluetape4k-projects #240 해결 후 다음 버전에 openDaeadEncryptedSink / openDaeadDecryptedSource 추가 예정.
}
```

#### 3.2.1 `Compressor` enum

```kotlin
enum class Compressor { GZIP, LZ4, SNAPPY, ZSTD, BZIP2, DEFLATE }
```

내부적으로 `bluetape4k-okio`의 `Compressable.Sinks/Sources` 함수에 위임.

#### 3.2.2 처리 순서

저장 시: `plaintext bytes → [optional Gzip] → [optional Tink encrypt] → file`

읽을 때 역순: `file → [Tink decrypt] → [Gzip decompress] → plaintext bytes`

이 순서는 표준 관행을 따른다 (압축 후 암호화 → 암호문은 엔트로피가 높아 더 이상 압축되지 않음을 활용).

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
        bs.toOwningOutputStream().use { os -> block(os) }
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

##### `toOwningOutputStream()` 래퍼

기본 `BufferedSink.outputStream()`이 sink를 닫지 않는 문제를 해결하기 위해, `bridge/Bridges.kt`에 `toOwningOutputStream()` 확장을 둔다.

```kotlin
/**
 * BufferedSink → OutputStream 래퍼. 일반 [outputStream]과 달리,
 * 반환된 OutputStream의 close()가 underlying BufferedSink를 명시적으로 close한다.
 * 외부 라이브러리(StAX/Jackson 등)가 OutputStream만 받는 경우, 단일 try-with-resources로
 * sink까지 안전하게 닫을 수 있도록 한다.
 */
fun BufferedSink.toOwningOutputStream(): OutputStream =
    object: OutputStream() {
        private val delegate = this@toOwningOutputStream.outputStream()
        override fun write(b: Int) = delegate.write(b)
        override fun write(b: ByteArray, off: Int, len: Int) = delegate.write(b, off, len)
        override fun flush() { delegate.flush() }
        override fun close() {
            try { delegate.close() } finally { this@toOwningOutputStream.close() }
        }
    }
```

이 래퍼와 헬퍼는 §5.2 위험 (마지막 segment 손실)에 대한 1차 방어선이다.

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
        bs.toOwningOutputStream().use { os ->
            this.exportGraph(os, options)
        }
    }
}
```

이 패턴의 장점:

- 기존 모듈의 시그니처 / API 변경 없음.
- 사용자는 `graph-io-okio` 모듈만 추가하면 OkIO 변형이 즉시 사용 가능.
- 압축/암호화 변형도 자연스럽게 노출 (`exportGraphGzipEncrypted`, `importGraphGzip` 등).

#### 3.4.1 어떤 확장 함수를 노출할지

| 모듈 | 함수 |
|------|------|
| CSV | `GraphCsvBulkImporter.importGraph(OkioGraphImportSource)`, `importGraphGzip`, `importGraphGzipEncrypted` |
| CSV | `GraphCsvBulkExporter.exportGraph(OkioGraphExportSink)`, `exportGraphGzip`, `exportGraphGzipEncrypted` |
| Jackson2/3 NDJSON | 동일 패턴 (`importGraph` / `exportGraph` 명명) |
| GraphML | 동일 패턴 (단, StAX와의 BufferedSink flush 타이밍 검증 필요 — §5 참조) |

### 3.5 VirtualThread / Suspend 변형

기존 `graph-io-bulk-import-export-design.md` 패턴을 그대로 따른다.

> **압축/암호화 옵션 비노출 정책**: 어댑터(VT/Suspend)는 **압축 알고리즘이나 Tink encryptor를 직접 파라미터로 받지 않는다**. 대신 호출자가 압축/암호화를 미리 체이닝해 만든 `Source`/`Sink`를 `OkioGraphImportSource.SourceBased` / `OkioGraphExportSink.SinkBased`로 감싸서 어댑터에 전달한다. 이렇게 하면 (a) 어댑터 시그니처가 단순해지고, (b) 사용자가 임의의 체이닝(예: Zstd → Tink → tee → multi-sink)을 자유롭게 구성할 수 있다.

```kotlin
// 사용 예 — 호출자가 체이닝을 외부에서 구성
val chained = GraphIoOkioPaths.openGzipEncryptedSink(
    OkioGraphExportSink.PathSink("/tmp/secret.graphml.gz.enc".toPath()),
    encryptor,
)
adapter.exportGraph(
    sink = OkioGraphExportSink.SinkBased(chained, ownsSink = true),
    format = GraphIoFormat.GRAPHML,
)
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

```kotlin
class SuspendGraphIoOkioBulkAdapter(
    private val ops: GraphSuspendOperations,
) {
    /** 진행 상황 Flow — 함수 자체는 cold Flow를 반환하므로 suspend 아님 */
    fun importGraph(source: OkioGraphImportSource, ...): Flow<GraphImportProgress>
    fun exportGraph(sink: OkioGraphExportSink, ...): Flow<GraphExportProgress>

    /** 또는 진행 상황을 buffer하지 않는 단순 호출 — 결과만 반환 */
    suspend fun importGraphAwait(source: OkioGraphImportSource, ...): GraphImportReport
    suspend fun exportGraphAwait(sink: OkioGraphExportSink, ...): GraphExportReport
}
```

> **suspend + Flow 문법 정정**: Kotlin에서 `Flow<T>`를 반환하는 함수는 일반적으로 `suspend` 가 **아니다** (cold Flow 자체가 collect 시점에 suspend된다). 따라서 `fun importGraph(...): Flow<...>` 형태로 선언하고, 결과만 받고 싶은 경우는 별도 `suspend fun ...Await(...): GraphImportReport`로 분리한다 (기존 graph-io-csv/jackson 모듈의 관례 준수).

- `Flow`로 진행 상황 emit.
- I/O는 `Dispatchers.IO`에서 수행.
- segment 단위 backpressure가 자연스럽게 작동 (suspend pull 모델 + OkIO lazy pull). DAEAD 청크 암호화도 청크 단위 lazy pull 유지 (§1.3.2, §3.6 참조).
- 로깅은 `KLoggingChannel` 사용 (suspend 컨텍스트에서 안전한 채널 기반 logger).

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
    return when (compressor) {
        GZIP -> Compressable.Sinks.gzip(sink).buffer()
        LZ4 -> {
            requireOnClasspath("net.jpountz.lz4.LZ4Factory") {
                "LZ4 압축을 사용하려면 build.gradle.kts에 'org.lz4:lz4-java' 의존성을 추가하세요."
            }
            Compressable.Sinks.lz4(sink).buffer()
        }
        SNAPPY -> {
            requireOnClasspath("org.xerial.snappy.Snappy") {
                "Snappy 압축을 사용하려면 'org.xerial.snappy:snappy-java' 의존성을 추가하세요."
            }
            Compressable.Sinks.snappy(sink).buffer()
        }
        ZSTD -> {
            requireOnClasspath("com.github.luben.zstd.ZstdInputStream") {
                "Zstd 압축을 사용하려면 'com.github.luben:zstd-jni' 의존성을 추가하세요."
            }
            Compressable.Sinks.zstd(sink).buffer()
        }
        BZIP2 -> {
            requireOnClasspath("org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream") {
                "Bzip2 압축을 사용하려면 'org.apache.commons:commons-compress' 의존성을 추가하세요."
            }
            Compressable.Sinks.bzip2(sink).buffer()
        }
        // ...
    }
}

/** classpath에서 클래스를 찾지 못하면 IllegalStateException을 던지는 헬퍼 */
private inline fun requireOnClasspath(className: String, lazyMessage: () -> String) {
    try { Class.forName(className) }
    catch (e: ClassNotFoundException) { throw IllegalStateException(lazyMessage(), e) }
}
```

---

## 4. 완료 기준 (Definition of Done)

### 4.1 기능 요구사항

- [ ] `OkioGraphImportSource` / `OkioGraphExportSink` sealed interface 구현 (3 variants × 2 = 6 case).
- [ ] `GraphIoOkioPaths` 헬퍼 함수 구현 (open, 압축 체이닝, 단축형). **암호화 함수는 후속 버전 대상.**
- [ ] OkIO ↔ java.io 브리지 함수 (`toInputStream`, `toOutputStream`, `toOwningOutputStream`, `toReader`, `toWriter`, `writeAsOutputStream`, `readAsInputStream`).
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
- [ ] **마지막 segment 손실 검증 (round-trip)**: 8KB 미만으로 끝나는 데이터(예: 7KB 한 줄짜리)를 export 후 import해서 byte 손실 없는지 확인 — `toOwningOutputStream()` 패턴 검증의 핵심 케이스
- **암호화 관련 Negative 테스트**: `bluetape4k-projects` #240 해결 후 추가 (key mismatch, REJECT 정책 등).

### 4.3 문서

- [ ] 모든 public API에 한국어 KDoc.
  - 압축 체이닝 시 처리 순서 명시.
  - close 책임 명시 (`@param ownsSource`, `@param ownsSink`).
  - CSV의 OkIO heap 이점 제약 명시 (§1.3.1).
- [ ] `graph-io/okio/README.md` (영문)
- [ ] `graph-io/okio/README.ko.md` (한국어)
  - 사용 예시 5개 이상 (PathSink, Gzip export/import, VT/Suspend 어댑터 등)
  - 압축 의존성 추가 가이드 (LZ4/Snappy/Zstd/Bzip2 compileOnly 전략)
  - 암호화: "다음 버전 예정 — `bluetape4k-projects` #240" 명시
  - java.io vs OkIO 경로 선택 가이드

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

---

## 6. 후속 작업 (Out of Scope)

- **암호화 지원**: DAEAD 청크형 스트리밍 (`DaeadChunkEncryptSink` / `DaeadChunkDecryptSource`) — `bluetape4k-projects` #240 해결 후 `graph-io-okio` 다음 버전에 추가. `openDaeadEncryptedSink`, `openDaeadDecryptedSource`, `openGzipDaeadEncryptedSink` 등 API 추가.
- AES-GCM streaming 자체 구현 (별도 이슈).
- HTTP/S3 원격 Source/Sink (별도 이슈).
- Spring Boot starter에 OkIO 변형 자동 등록.
- graph-io 통합 facade (기존 + OkIO를 통합한 single entry point).
- Async Channel(NIO) 기반 어댑터.

---

## 7. 결정 로그 (Decision Log)

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

---

## 8. 부록: 사용 예시 (KDoc/README 초안)

```kotlin
// (1) 가장 간단한 OkIO 경로 export — 고수준 헬퍼 사용
import io.bluetape4k.graph.io.okio.OkioGraphExportSink
import io.bluetape4k.graph.io.okio.GraphIoOkioPaths
import io.bluetape4k.graph.io.okio.bridge.writeAsOutputStream

val sink = OkioGraphExportSink.PathSink("/tmp/graph.csv".toPath())
writeAsOutputStream(sink) { os ->
    csvExporter.exportGraph(os)
}

// (2) Gzip 압축 export — close 체인 명시
GraphIoOkioPaths.openGzipSink(sink).use { bs ->
    bs.toOwningOutputStream().use { os ->
        csvExporter.exportGraph(os)
    }
}

// (3) Suspend 변형 — 진행 Flow 구독 (suspend 키워드 없음 — cold Flow 반환)
val adapter = SuspendGraphIoOkioBulkAdapter(suspendOps)
adapter.exportGraph(sink, GraphIoFormat.CSV).collect { progress ->
    log.info { "${progress.processed}/${progress.total}" }
}

// (4) 외부 체이닝 Source를 어댑터에 전달 — 어댑터는 압축 옵션을 직접 받지 않음
val gzipSink = GraphIoOkioPaths.openGzipSink(OkioGraphExportSink.PathSink("/tmp/graph.graphml.gz".toPath()))
adapter.exportGraph(
    OkioGraphExportSink.SinkBased(gzipSink, ownsSink = true),
    GraphIoFormat.GRAPHML,
).collect { /* ... */ }

// (5) 암호화 예시 — 후속 버전 예정 (bluetape4k-projects #240 해결 후)
// GraphIoOkioPaths.openGzipDaeadEncryptedSink(sink, daead) // 미구현
```

---

**Spec 끝.** 다음 단계: `Step 1-P` (planner agent로 task list 생성) → `Step 2-T` (TDD로 구현).
