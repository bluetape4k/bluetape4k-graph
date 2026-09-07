# Issue #615 NDJSON 줄 길이 상한 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Jackson2·Jackson3 NDJSON의 sync, suspend, Virtual Thread, Flow 입력에 선택 가능한 codec 이전 줄 길이 상한을 제공한다.

**Architecture:** `graph-io-core`의 immutable `NdJsonReadOptions`가 UTF-16 code unit 상한을 소유한다. Jackson2·3 parser는 `bluetape4k-io`의 `Reader.boundedLineReader`를 사용하고, public adapter는 기존 JVM constructor를 보존하면서 옵션을 전달한다. 제한은 checkpoint identity, safe failure, coroutine cancellation 계약에 포함한다.

**Tech Stack:** Kotlin 2.4, Java 25, Kotlin Coroutines Flow, Jackson 2/3, bluetape4k-io `BoundedLineReader`, JUnit 5, MockK, bluetape4k-assertions, Gradle

---

### Task 1: 공통 immutable 옵션 계약

**Files:**
- Create: `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/options/NdJsonReadOptions.kt`
- Create: `graph-io/core/src/test/kotlin/io/bluetape4k/graph/io/options/NdJsonReadOptionsTest.kt`

- [ ] **Step 1: 실패하는 옵션 계약 테스트 작성**

```kotlin
class NdJsonReadOptionsTest {
    @Test
    fun `기본값은 기존 입력을 사실상 제한하지 않는다`() {
        NdJsonReadOptions().maxLineChars shouldBeEqualTo Int.MAX_VALUE
    }

    @Test
    fun `copy는 줄 길이 상한을 보존하고 변경한다`() {
        NdJsonReadOptions(128).copy(maxLineChars = 256).maxLineChars shouldBeEqualTo 256
    }

    @Test
    fun `줄 길이 상한은 양수여야 한다`() {
        assertFailsWith<IllegalArgumentException> { NdJsonReadOptions(0) }
    }
}
```

- [ ] **Step 2: RED 확인**

Run: `./gradlew :bluetape4k-graph-io-core:test --tests "*.NdJsonReadOptionsTest" --no-daemon --console=plain`

Expected: `NdJsonReadOptions` unresolved로 compilation failure.

- [ ] **Step 3: 최소 옵션 구현**

```kotlin
data class NdJsonReadOptions(
    val maxLineChars: Int = UNLIMITED_MAX_LINE_CHARS,
) : Serializable {
    init {
        maxLineChars.requirePositiveNumber("maxLineChars")
    }

    companion object {
        private const val serialVersionUID: Long = 1L
        const val UNLIMITED_MAX_LINE_CHARS: Int = Int.MAX_VALUE
    }
}
```

- [ ] **Step 4: GREEN과 serialization 확인**

Run: `./gradlew :bluetape4k-graph-io-core:test --tests "*.NdJsonReadOptionsTest" --no-daemon --console=plain`

Expected: 모든 옵션 테스트 PASS.

- [ ] **Step 5: 계약 커밋**

```bash
git add graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/options/NdJsonReadOptions.kt graph-io/core/src/test/kotlin/io/bluetape4k/graph/io/options/NdJsonReadOptionsTest.kt
git commit
```

커밋은 Lore trailer와 한국어 intent를 사용한다.

### Task 2: Jackson2·3 parser의 bounded read와 safe failure

**Files:**
- Modify: `graph-io/jackson2/src/main/kotlin/io/bluetape4k/graph/io/jackson2/internal/Jackson2RecordParser.kt`
- Modify: `graph-io/jackson3/src/main/kotlin/io/bluetape4k/graph/io/jackson3/internal/Jackson3RecordParser.kt`
- Create: `graph-io/jackson2/src/test/kotlin/io/bluetape4k/graph/io/jackson2/Jackson2NdJsonLineLimitTest.kt`
- Create: `graph-io/jackson3/src/test/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3NdJsonLineLimitTest.kt`

- [ ] **Step 1: 상한 경계와 read-count 회귀 테스트 작성**

각 codec 테스트에서 동일한 계약을 고정한다.

```kotlin
val line = vertexLine("한글😀")
val exact = NdJsonReadOptions(maxLineChars = line.length)
reader(exact).readVertices(sourceOf("$line\r\n")).toList().single().externalId shouldBeEqualTo "v1"

val error = assertFailsWith<GraphIoReadException> {
    reader(NdJsonReadOptions(line.length - 1)).readVertices(sourceOf(line)).toList()
}
error.failure.phase shouldBeEqualTo GraphIoPhase.READ_VERTEX
error.failure.fileRole shouldBeEqualTo GraphIoFileRole.UNIFIED
error.failure.location shouldBeEqualTo "line:1"
```

generated no-newline 입력은 `CountingInputStream`을 사용해 실패 후 `bytesRead < payload.size`를 검증한다.
LF, CRLF, CR 입력은 세 줄의 external ID 순서가 동일함을 검증한다.

- [ ] **Step 2: RED 확인**

Run:

```bash
./gradlew :bluetape4k-graph-io-jackson2:test --tests "*.Jackson2NdJsonLineLimitTest" :bluetape4k-graph-io-jackson3:test --tests "*.Jackson3NdJsonLineLimitTest" --no-daemon --console=plain
```

Expected: 새 options constructor/parser wiring 부재로 compilation failure.

- [ ] **Step 3: 공통 helper로 parser 구현**

두 parser에 `NdJsonReadOptions`를 주입하고 다음 흐름을 동일하게 적용한다.

```kotlin
GraphIoPaths.openReader(source).use { reader ->
    val boundedReader = reader.boundedLineReader(readOptions.maxLineChars)
    while (parsing) {
        ensureActive()
        val raw = boundedReader.readLine() ?: break
        lineNumber++
        // 기존 blank/envelope/callback 처리 유지
    }
}
```

`LineLimitExceededException`은 다음 failure로 변환한다.

```kotlin
GraphIoFailure(
    phase = phase,
    fileRole = GraphIoFileRole.UNIFIED,
    location = "line:${lineNumber + 1}",
    message = "NDJSON line exceeds maxLineChars=${readOptions.maxLineChars}",
)
```

Flow의 `records`는 `ensureActive = { producer.coroutineContext.ensureActive() }`를 전달한다.

- [ ] **Step 4: GREEN과 negative path 확인**

Run: Task 2 Step 2와 동일.

Expected: Jackson2·3 boundary, newline, Unicode, read-count, redaction 테스트 PASS.

- [ ] **Step 5: parser 커밋**

```bash
git add graph-io/jackson2/src graph-io/jackson3/src
git commit
```

### Task 3: 모든 실행 모델과 checkpoint identity 연결

**Files:**
- Modify: `graph-io/jackson2/src/main/kotlin/io/bluetape4k/graph/io/jackson2/Jackson2NdJsonBulkImporter.kt`
- Modify: `graph-io/jackson2/src/main/kotlin/io/bluetape4k/graph/io/jackson2/SuspendJackson2NdJsonBulkImporter.kt`
- Modify: `graph-io/jackson2/src/main/kotlin/io/bluetape4k/graph/io/jackson2/Jackson2NdJsonVirtualThreadBulkImporter.kt`
- Modify: `graph-io/jackson2/src/main/kotlin/io/bluetape4k/graph/io/jackson2/Jackson2NdJsonRecordFlowReader.kt`
- Modify: `graph-io/jackson3/src/main/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3NdJsonBulkImporter.kt`
- Modify: `graph-io/jackson3/src/main/kotlin/io/bluetape4k/graph/io/jackson3/SuspendJackson3NdJsonBulkImporter.kt`
- Modify: `graph-io/jackson3/src/main/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3NdJsonVirtualThreadBulkImporter.kt`
- Modify: `graph-io/jackson3/src/main/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3NdJsonRecordFlowReader.kt`
- Modify: `graph-io/jackson2/src/test/kotlin/io/bluetape4k/graph/io/jackson2/Jackson2CheckpointLifecycleTest.kt`
- Modify: `graph-io/jackson3/src/test/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3CheckpointLifecycleTest.kt`
- Modify: `graph-io/jackson2/src/test/kotlin/io/bluetape4k/graph/io/jackson2/Jackson2StreamingReaderContractTest.kt`
- Modify: `graph-io/jackson3/src/test/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3StreamingReaderContractTest.kt`

- [ ] **Step 1: 실행 모델·checkpoint·빈 줄 취소 RED 작성**

sync/suspend/Virtual Thread importer에 작은 상한을 주어 같은 oversized line이 `FAILED`인지 검증한다.
checkpoint는 첫 시도와 같은 상한이면 재개되고 다른 상한이면
`GraphImportCheckpointConflictException`인지 `assertFailsWith`로 검증한다.
Flow는 무한 blank-line reader를 collect한 job을 취소하고 `CancellationException`과 close count를 검증한다.

- [ ] **Step 2: RED 확인**

Run:

```bash
./gradlew :bluetape4k-graph-io-jackson2:test :bluetape4k-graph-io-jackson3:test --no-daemon --console=plain
```

Expected: 아직 옵션을 전달하지 않는 adapter와 checkpoint identity 테스트 FAIL.

- [ ] **Step 3: public adapter wiring과 ABI 보존**

Importer는 다음 optional constructor를 사용한다.

```kotlin
class Jackson2NdJsonBulkImporter(
    private val readOptions: NdJsonReadOptions = NdJsonReadOptions(),
)
```

Virtual Thread importer는 같은 옵션으로 sync importer를 만든다. Flow reader는 기존 primary constructor를
그대로 두고 `@JvmOverloads` secondary constructor에서 parser를 교체한다.

```kotlin
@JvmOverloads
constructor(
    readOptions: NdJsonReadOptions,
    defaultVertexLabel: String = "Vertex",
    defaultEdgeLabel: String = "Edge",
) : this(defaultVertexLabel, defaultEdgeLabel) {
    parser = Jackson2RecordParser(readOptions = readOptions)
}
```

Checkpoint session은 다음 identity를 받는다.

```kotlin
importOptionsIdentity = GraphImportCheckpointIdentity.optionsIdentity(
    options,
    "maxLineChars=${readOptions.maxLineChars}",
)
```

- [ ] **Step 4: GREEN과 ABI signature 비교**

Run:

```bash
./gradlew :bluetape4k-graph-io-jackson2:test :bluetape4k-graph-io-jackson3:test --no-daemon --console=plain
javap -classpath graph-io/jackson2/build/classes/kotlin/main -p io.bluetape4k.graph.io.jackson2.Jackson2NdJsonRecordFlowReader
javap -classpath graph-io/jackson3/build/classes/kotlin/main -p io.bluetape4k.graph.io.jackson3.Jackson3NdJsonRecordFlowReader
```

Expected: 모든 테스트 PASS, 기존 no-arg와 `(String, String)`/default-mask constructor 및 기존 method가 존재.

- [ ] **Step 5: adapter 커밋**

```bash
git add graph-io/jackson2 graph-io/jackson3
git commit
```

### Task 4: 문서, 7-Tier 검토, 최종 검증

**Files:**
- Modify: `graph-io/core/README.md`
- Modify: `graph-io/core/README.ko.md`
- Modify: `graph-io/jackson2/README.md`
- Modify: `graph-io/jackson2/README.ko.md`
- Modify: `graph-io/jackson3/README.md`
- Modify: `graph-io/jackson3/README.ko.md`
- Modify: `CHANGELOG.md`
- Modify: `WIP.md`
- Create: `docs/lessons/2026-09-07-issue-615-bounded-ndjson-lines.md`
- Create: `docs/review/2026-09-07-issue-615-bounded-ndjson-lines-7tier.md`

- [ ] **Step 1: public 계약과 bilingual parity 문서화**

README에 `maxLineChars`가 UTF-16 code unit, 기본 `Int.MAX_VALUE`, codec 이전 적용,
LF/CRLF/CR 지원, safe failure, 모든 실행 모델 공통임을 source-equivalent하게 기록한다.

- [ ] **Step 2: 전체 affected module 검증**

Run:

```bash
./gradlew :bluetape4k-graph-io-core:check :bluetape4k-graph-io-jackson2:check :bluetape4k-graph-io-jackson3:check --no-daemon --console=plain
git diff --check
```

Expected: core/Jackson2/Jackson3 tests, Detekt, Kover와 diff check PASS.

- [ ] **Step 3: 7-Tier 검토와 수정 수렴**

정확한 branch head를 기준으로 API/ABI, correctness, security/redaction, concurrency/cancellation,
resource ownership, checkpoint, tests/docs를 분리해 검토한다. P0/P1을 0으로 만들고 P2/P3를 수정하거나
follow-up issue에 연결한 뒤 영향받은 검증을 다시 실행한다.

- [ ] **Step 4: 최종 Lore 커밋과 PR 준비**

```bash
git add CHANGELOG.md WIP.md docs graph-io
git commit
git status --short
```

Expected: clean status, PR head가 #614 branch 위에만 #615 commits를 가진다.
