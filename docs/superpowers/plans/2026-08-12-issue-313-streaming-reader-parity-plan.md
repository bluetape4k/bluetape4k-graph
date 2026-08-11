# graph-io streaming reader parity Implementation Plan

> **For agentic workers:** 승인된 설계와 이 계획을 task 단위로 실행한다. 각 단계는 checkbox로 추적하고, 테스트가 먼저 실패한 뒤 최소 구현으로 통과시킨다.

**Goal:** CSV, Jackson2 NDJSON, Jackson3 NDJSON, GraphML, OkIO가 공통 GraphRecordFlowReader의 streaming, ownership, cancellation 의미론을 제공하면서 기존 importer 정책과 bounded edge buffer를 보존한다.

**Architecture:** core에 location을 보존하는 safe GraphIoReadException을 추가하고, 각 포맷은 기존 codec/parser를 단일-pass sink로 재사용한다. Public reader는 cold Flow와 channelFlow/trySendBlocking으로 downstream backpressure를 유지하고, blocking importer는 Flow 수집 없이 같은 parser를 직접 호출한다. GraphML은 전체 정점·간선 List를 없애고 sink에 즉시 전달한다.

**Tech Stack:** Kotlin 2.4/JVM 25, Gradle 9.7, kotlinx-coroutines Flow, Dispatchers.IO, trySendBlocking, JDK StAX, bluetape4k-csv, OkIO, JUnit 5 + Bluetape assertions/Kluent/MockK, kotlinx-coroutines-test.

---

## 파일 소유권과 변경 지도

| 책임 | 생성/수정 파일 | 검증 |
| --- | --- | --- |
| 공통 safe parse failure | `graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/report/GraphIoReadException.kt` | core exception test |
| CSV reader/parser | `graph-io/csv/src/main/kotlin/io/bluetape4k/graph/io/csv/CsvGraphRecordFlowReader.kt`, `internal/CsvRecordParser.kt`, `CsvGraphBulkImporter.kt`, `SuspendCsvGraphBulkImporter.kt` | CSV contract/policy/cancel |
| Jackson2 reader/parser | `graph-io/jackson2/src/main/kotlin/io/bluetape4k/graph/io/jackson2/Jackson2NdJsonRecordFlowReader.kt`, `internal/Jackson2RecordParser.kt`, blocking/suspend importers | reader/overflow/suspend |
| Jackson3 reader/parser | `graph-io/jackson3/src/main/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3NdJsonRecordFlowReader.kt`, `internal/Jackson3RecordParser.kt`, blocking/suspend importers | reader/overflow/suspend |
| GraphML sink/reader | `graph-io/graphml/src/main/kotlin/io/bluetape4k/graph/io/graphml/GraphMlRecordFlowReader.kt`, `internal/StaxGraphMlReader.kt`, blocking/suspend importers | streaming/failure/cancel |
| OkIO adapter | `graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/OkioGraphRecordFlowReader.kt` 및 bridge | ownership/parity |
| Tests | 각 모듈 src/test/kotlin의 StreamingReaderContractTest/ReaderFailureTest | generated fixture/policy |
| Docs | 각 graph-io module의 README.md와 README.ko.md | locale parity |
| Lesson | `docs/lessons/2026-08-12-issue-313-streaming-reader-parity.md` | Step 7 commit |

새 파일을 추가하기 전 rg로 같은 책임의 기존 helper를 확인한다. !!, monitor/synchronized, suspend runCatching, raw parse payload 로그, JUnit assertThrows를 추가하지 않는다.

## Task 1: core read exception 경계

복잡도: 중간. 선행: 설계 문서. Pattern: bluetape-kotlin-patterns, ecc-kotlin-testing.

Files:

- Create: graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/report/GraphIoReadException.kt
- Test: graph-io/core/src/test/kotlin/io/bluetape4k/graph/io/report/GraphIoReadExceptionTest.kt

- [x] Step 1: 실패 테스트 작성

~~~kotlin
@Test
fun readExceptionKeepsLocationAndHidesRawPayload() {
    val failure = GraphIoFailure(
        phase = GraphIoPhase.READ_VERTEX,
        location = "line:7",
        recordId = "v-7",
        message = "secret payload",
    )
    val error = GraphIoReadException(failure)
    error.failure shouldBe failure
    error.message shouldNotContain "secret payload"
    error.message shouldContain "line:7"
    error.message shouldNotContain "v-7"
    error.cause shouldBe null
}
~~~

reader의 take(1) 후 owned path close, caller-owned stream open, cancellation 후 read count 정지는 각 포맷 모듈의 contract test가 포맷별 source 타입으로 검증한다. core 테스트는 예외의 안전한 message/cause 경계만 고정한다.

- [x] Step 2: RED 확인

실행: ./gradlew :bluetape4k-graph-io-core:test --tests '*GraphIoReadExceptionTest' --no-daemon

예상 결과: 예외 심볼이 없어 컴파일 또는 assertion 실패한다.

- [x] Step 3: 최소 구현

~~~kotlin
class GraphIoReadException(
    val failure: GraphIoFailure,
) : RuntimeException(
    buildString {
        append("Graph IO read failed")
        append(" phase=").append(failure.phase)
        append(" location=").append(failure.location ?: "unknown")
    },
)
~~~

message와 cause에는 raw line/XML value, source path, record ID, codec exception message를 넣지 않는다. 원인 예외가 필요한 내부 진단은 parser 내부에서 고정 code/location으로 기록하고 public exception에는 전달하지 않는다.

- [x] Step 4: GREEN 확인

./gradlew :bluetape4k-graph-io-core:test --no-daemon 및 git diff --check가 PASS해야 한다.

- [x] Step 5: 커밋

git add graph-io/core/src/main graph-io/core/src/test
git commit -m "graph-io read failure 경계를 안전한 예외로 고정한다"

Rollback: core exception만 revert하면 포맷 변경 전 상태로 돌아간다.

## Task 2: CSV 단일-pass parser와 Flow reader

복잡도: 높음. 선행: Task 1. Pattern: bluetape-kotlin-patterns, kotlin-coroutines-skill, ecc-kotlin-testing.

Files:

- Create: graph-io/csv/src/main/kotlin/io/bluetape4k/graph/io/csv/internal/CsvRecordParser.kt
- Create: graph-io/csv/src/main/kotlin/io/bluetape4k/graph/io/csv/CsvGraphRecordFlowReader.kt
- Modify: CsvGraphBulkImporter.kt, SuspendCsvGraphBulkImporter.kt
- Preserve: CsvGraphVirtualThreadBulkImporter.kt remains a sync-importer adapter; it does not collect the new Flow.
- Create: `graph-io/csv/src/test/kotlin/io/bluetape4k/graph/io/csv/CsvStreamingReaderContractTest.kt`, `graph-io/csv/src/test/kotlin/io/bluetape4k/graph/io/csv/CsvGraphBulkImporterPolicyTest.kt`

- [ ] Step 1: 실패 테스트 작성

close-tracking InputStream과 path source로 vertex/edge 순서, take(1) lazy read, owned close/non-owned open을 먼저 고정한다. logical EOF, truncated final row, post-terminal 재수집(재사용 가능한 PathSource), one-shot InputStream 재사용 제한, terminal callback exactly-once, close failure의 primary/suppressed 보존, malformed row location, duplicate vertex, missing endpoint FAIL/SKIP, suspend cancellation도 추가한다.

~~~kotlin
@Test
fun callerOwnedCsvStreamRemainsOpen() = runBlocking {
    val input = TrackingInputStream(csvBytes)
    val source = CsvGraphImportSource(
        vertices = GraphImportSource.InputStreamSource(input),
        edges = GraphImportSource.InputStreamSource(ByteArrayInputStream(csvBytes)),
    )
    CsvGraphRecordFlowReader().readVertices(source).toList()
    input.closed shouldBe false
}
~~~

- [ ] Step 2: RED 확인

./gradlew :bluetape4k-graph-io-csv:test --tests '*StreamingReaderContractTest' --tests '*PolicyTest' --no-daemon가 reader/close assertion 실패를 보여야 한다.

- [ ] Step 3: 최소 구현

CsvRecordParser는 CsvRecordReader().read(input, encoding, skipHeaders)를 사용하고 GraphIoPaths.openInputStream(source).use로 source ownership을 감싼다. parser는 row별 onVertex, onEdge, onFailure callback만 호출한다.

~~~kotlin
class CsvGraphRecordFlowReader(
    private val csvOptions: CsvGraphIoOptions = CsvGraphIoOptions(),
) : GraphRecordFlowReader<CsvGraphImportSource> {
    override fun readVertices(source: CsvGraphImportSource): Flow<GraphIoVertexRecord> =
        stream(source.vertices, GraphIoFileRole.VERTICES, verticesOnly = true)
    override fun readEdges(source: CsvGraphImportSource): Flow<GraphIoEdgeRecord> =
        stream(source.edges, GraphIoFileRole.EDGES, verticesOnly = false)
}
~~~

stream은 channelFlow + withContext(Dispatchers.IO) + trySendBlocking으로 parser와 downstream을 순차화한다. parser failure는 GraphIoReadException으로 감싸고, blocking/suspend importer는 같은 parser를 직접 호출하여 기존 batch writer와 policy를 유지한다.
public reader KDoc은 cold collect, record order, one-shot InputStream, ownership, cancellation, raw external ID 미해결 의미를 한국어로 명시한다. `trySendBlocking` 실패는 channel cancellation으로 전파하고 `CancellationException`을 `runCatching`으로 삼키지 않는다.

- [ ] Step 4: GREEN 확인

./gradlew :bluetape4k-graph-io-csv:test --tests '*StreamingReaderContractTest' --tests '*CsvGraphBulkImporterPolicyTest' --no-daemon가 PASS해야 한다.

- [ ] Step 5: 커밋

git add graph-io/csv/src/main graph-io/csv/src/test
git commit -m "CSV streaming reader와 source ownership을 정렬한다"

## Task 3: Jackson2/3 NDJSON reader와 bounded edge 처리

복잡도: 높음. 선행: Task 1. Pattern: bluetape-kotlin-patterns, kotlin-coroutines-skill.

Files:

- Create: `graph-io/jackson2/src/main/kotlin/io/bluetape4k/graph/io/jackson2/internal/Jackson2RecordParser.kt`, `graph-io/jackson2/src/main/kotlin/io/bluetape4k/graph/io/jackson2/Jackson2NdJsonRecordFlowReader.kt`
- Modify: `Jackson2NdJsonBulkImporter.kt`, `SuspendJackson2NdJsonBulkImporter.kt`, and their existing tests
- Create: `graph-io/jackson2/src/test/kotlin/io/bluetape4k/graph/io/jackson2/Jackson2StreamingReaderContractTest.kt`, `graph-io/jackson2/src/test/kotlin/io/bluetape4k/graph/io/jackson2/Jackson2ReaderFailureTest.kt`
- Preserve: `Jackson2NdJsonVirtualThreadBulkImporter.kt` continues to delegate to the blocking importer.
- Create: `graph-io/jackson3/src/main/kotlin/io/bluetape4k/graph/io/jackson3/internal/Jackson3RecordParser.kt`, `graph-io/jackson3/src/main/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3NdJsonRecordFlowReader.kt`
- Modify: `Jackson3NdJsonBulkImporter.kt`, `SuspendJackson3NdJsonBulkImporter.kt`, and their existing tests
- Create: `graph-io/jackson3/src/test/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3StreamingReaderContractTest.kt`, `graph-io/jackson3/src/test/kotlin/io/bluetape4k/graph/io/jackson3/Jackson3ReaderFailureTest.kt`
- Preserve: `Jackson3NdJsonVirtualThreadBulkImporter.kt` continues to delegate to the blocking importer.

- [ ] Step 1: 실패 테스트 작성

두 module에서 동일 fixture의 vertex/edge 순서, logical EOF, truncated final line, post-terminal 재수집, one-shot InputStream 재사용 제한, terminal callback exactly-once, close failure의 primary/suppressed 보존, malformed JSON의 failure.location == `line:3`, take(1), maxEdgeBufferSize = 1, duplicate/missing endpoint, Jackson2↔Jackson3 compatibility를 고정한다.

- [ ] Step 2: RED 확인

./gradlew :bluetape4k-graph-io-jackson2:test --tests '*StreamingReaderContractTest' --tests '*ReaderFailureTest' --no-daemon

./gradlew :bluetape4k-graph-io-jackson3:test --tests '*StreamingReaderContractTest' --tests '*ReaderFailureTest' --no-daemon

예상 결과: reader/parser 경계가 없어 실패한다.

- [ ] Step 3: 최소 구현

각 parser는 기존 envelope codec으로 BufferedReader.readLine() 한 줄만 처리한다. blank line은 건너뛰고, codec 예외는 raw message 대신 GraphIoFailure(phase, location, message = Malformed JSON)로 바꾼다. reader의 public shape은 다음과 같다.

~~~kotlin
class Jackson2NdJsonRecordFlowReader : GraphRecordFlowReader<GraphImportSource> {
    override fun readVertices(source: GraphImportSource): Flow<GraphIoVertexRecord> =
        stream(source, NdJsonEnvelope.TYPE_VERTEX)
    override fun readEdges(source: GraphImportSource): Flow<GraphIoEdgeRecord> =
        stream(source, NdJsonEnvelope.TYPE_EDGE)
}
~~~

GraphIoPaths.openReader(source).use 안에서 channelFlow/trySendBlocking을 사용한다. importer는 Flow를 수집하지 않고 parser callback으로 즉시 vertex를 쓰며 edge만 ArrayDeque에 둔다. size > maxEdgeBufferSize는 기존 FAILED와 line location을 유지한다. Jackson3도 동일한 구조로 구현한다. 두 reader의 한국어 KDoc은 cold/re-read와 caller-owned one-shot source 제약을 동일한 문구로 명시한다.

- [ ] Step 4: GREEN 확인

각 module의 NdJsonCompatibilityTest, EdgeBufferOverflowTest, 새 streaming/suspend tests를 순차 실행한다. generated 10,000-line input에서 전체 line collection이 없고 queue counter가 상한 이하인지 확인한다.

- [ ] Step 5: 커밋

git add graph-io/jackson2 graph-io/jackson3
git commit -m "Jackson NDJSON reader parity와 edge buffer 경계를 고정한다"

## Task 4: GraphML StAX sink와 Flow reader

복잡도: 높음. 선행: Task 1. Pattern: bluetape-kotlin-patterns, kotlin-coroutines-skill.

Files:

- Create: graph-io/graphml/src/main/kotlin/io/bluetape4k/graph/io/graphml/GraphMlRecordFlowReader.kt
- Modify: internal/StaxGraphMlReader.kt, GraphMlBulkImporter.kt, SuspendGraphMlBulkImporter.kt
- Preserve: GraphMlVirtualThreadBulkImporter.kt remains a blocking-importer adapter.
- Create: `graph-io/graphml/src/test/kotlin/io/bluetape4k/graph/io/graphml/GraphMlStreamingReaderContractTest.kt`; modify existing StAX/round-trip/suspend tests

- [ ] Step 1: 실패 테스트 작성

generated GraphML 10,000 node/edge에서 take(1) 후 parser read count 정지, sink order, logical EOF, truncated XML, post-terminal 재수집, one-shot InputStream 재사용 제한, terminal callback exactly-once, close failure의 primary/suppressed 보존, invalid typed data location, XXE rejection, duplicate/missing endpoint FAIL/SKIP, suspend cancellation을 고정한다. production path가 vertices/edges List를 만들지 않는 구조 assertion도 추가한다.

- [ ] Step 2: RED 확인

./gradlew :bluetape4k-graph-io-graphml:test --tests '*GraphMlStreamingReaderContractTest' --tests '*StaxGraphMlReaderWriterTest' --no-daemon가 기존 list 반환과 신규 sink assertion 차이로 실패해야 한다.

- [ ] Step 3: 최소 구현

StaxGraphMlReader에 다음 internal sink를 추가한다.

~~~kotlin
internal interface GraphMlRecordSink {
    fun onVertex(record: GraphIoVertexRecord)
    fun onEdge(record: GraphIoEdgeRecord)
    fun onFailure(failure: GraphIoFailure)
}
internal fun read(input: InputStream, options: GraphMlImportOptions, sink: GraphMlRecordSink)
~~~

key map과 secure XML factory는 유지하고 node/edge parse 즉시 sink에 전달한다. 기존 `GraphMlReadResult`/`read(input, options)`는 내부 회귀 테스트용 list helper로만 남기고 production importer는 sink overload를 사용하여 list를 만들지 않는다. sink helper는 parse failure를 즉시 전달하고 unsupported-element policy를 기존 severity/status로 보존한다. ERROR failure는 현재 record boundary에서 parser/import를 종료하고 writes를 시작하지 않으며, WARN failure는 기존처럼 계속 읽는다. XML/codec raw exception은 `GraphIoReadException`의 safe failure로 변환한다. reader의 한국어 KDoc에는 cold/re-read와 caller-owned one-shot source 제약을 명시한다. reader는 GraphIoPaths.openInputStream(source).use와 channelFlow/trySendBlocking을 조합하고 caller-owned wrapper를 닫지 않는다.

- [ ] Step 4: GREEN 확인

./gradlew :bluetape4k-graph-io-graphml:test --tests '*GraphMlStreamingReaderContractTest' --tests '*GraphMlRoundTripTest' --tests '*GraphMlSuspendTest' --no-daemon가 PASS해야 한다.

- [ ] Step 5: 커밋

git add graph-io/graphml/src/main graph-io/graphml/src/test
git commit -m "GraphML StAX import을 sink 기반 streaming으로 전환한다"

## Task 5: OkIO format adapter와 ownership matrix

복잡도: 중간. 선행: Tasks 2–4. Pattern: bluetape-kotlin-patterns, existing OkIO close wrappers.

Files:

- Create: graph-io/okio/src/main/kotlin/io/bluetape4k/graph/io/okio/OkioGraphRecordFlowReader.kt
- Modify: OkioGraphBulkImporter.kt 및 필요한 bridge
- Create: `graph-io/okio/src/test/kotlin/io/bluetape4k/graph/io/okio/OkioStreamingReaderContractTest.kt`; modify `OkioRoundTripTest.kt` and `GraphIoOkioPathsTest.kt`

- [ ] Step 1: 실패 테스트 작성

NDJSON2/3, GraphML, CSV의 path/source/input-stream 형태를 테스트한다. ownsSource/ownsStream=true는 collect 완료/취소 때 close, false는 open이어야 한다. close failure는 collector/parse primary를 덮지 않고 suppressed로 남아야 한다. CSV stream-backed source는 기존 stem pair 제약의 명확한 unsupported 예외를 내야 한다.

- [ ] Step 2: RED 확인

./gradlew :bluetape4k-graph-okio:test --tests '*OkioStreamingReaderContractTest' --tests '*GraphIoOkioPathsTest' --no-daemon가 reader/dispatch 부재로 실패해야 한다.

- [ ] Step 3: 최소 구현

~~~kotlin
private inline fun <T> readSingleStream(
    source: OkioGraphImportSource,
    crossinline read: (GraphImportSource) -> Flow<T>,
): Flow<T> = flow {
    GraphIoOkioPaths.openSource(source).use { bufferedSource ->
        emitAll(read(GraphImportSource.InputStreamSource(bufferedSource.toInputStream(), closeInput = false)))
    }
}
~~~

`OkioGraphRecordFlowReader(format)`는 포맷별 delegate reader를 생성하고 단일 스트림 포맷은 위 helper의 한 `GraphIoOkioPaths.openSource(source).use` 범위 안에서 delegate Flow를 수집한다. `InputStreamSource(closeInput=false)`는 delegate가 OkIO source를 닫지 않게 하며 outer `use`가 정확히 한 번 닫는다. CSV는 `PathSource`만 허용하고 source stem에서 `<stem>_vertices.csv`/`<stem>_edges.csv` OkIO path를 파생한 뒤 각 collect 시 해당 파일 하나만 열어 `CsvGraphRecordFlowReader`에 전달한다. CSV stream-backed source는 기존 importer와 동일한 명시적 `UnsupportedOperationException`을 낸다. compression/DAEAD는 기존 `GraphIoOkioPaths` helper를 재사용한다.
reader KDoc과 ownership matrix는 PathSource, ownsSource/ownsStream true, false, CSV pair/stream unsupported를 동일한 문구와 예외 타입으로 명시한다. OkIO adapter는 Jackson 버전별 reader를 공통 의존성으로 합치지 않는다. Jackson2/3 codec classpath가 분리되어 있으므로 각 module delegate를 유지하고 core exception/contract test만 공유한다.

- [ ] Step 4: GREEN 확인

./gradlew :bluetape4k-graph-okio:test --tests '*OkioStreamingReaderContractTest' --tests '*OkioRoundTripTest' --no-daemon가 ownership/format parity/cancel close를 통과해야 한다.

- [ ] Step 5: 커밋

git add graph-io/okio/src/main graph-io/okio/src/test
git commit -m "OkIO source를 포맷 reader contract에 연결한다"

## Task 6: cross-format generated fixture와 정책 회귀

복잡도: 높음. 선행: Tasks 2–5. Pattern: ecc-kotlin-testing, kotlin-coroutines-skill. Testcontainers는 사용하지 않는다.

- [ ] Step 1: 실패 시나리오 고정

10,000 records generated input, malformed CSV/JSON/XML safe failure, take(1)/cancel ownership, duplicate FAIL/SKIP, missing endpoint FAIL/SKIP_EDGE, NDJSON overflow를 각 모듈 테스트 이름과 assertion으로 고정한다. generated counter는 parser read-ahead와 edge queue가 각각 contract 상한을 넘지 않음을 기록하고, GraphML production importer source에는 vertex/edge `List` materialization이 없음을 확인한다.

- [ ] Step 2: 모듈별 순차 실행

~~~bash
./gradlew :bluetape4k-graph-io-core:test --no-daemon
./gradlew :bluetape4k-graph-io-csv:test --no-daemon
./gradlew :bluetape4k-graph-io-jackson2:test --no-daemon
./gradlew :bluetape4k-graph-io-jackson3:test --no-daemon
./gradlew :bluetape4k-graph-io-graphml:test --no-daemon
./gradlew :bluetape4k-graph-okio:test --no-daemon
~~~

앞 명령 실패 시 다음 모듈로 진행하지 않고 원인을 수정한 뒤 해당 명령부터 재실행한다.

- [ ] Step 3: batch size 분리 assertion

동일 fixture에서 GraphImportOptions.batchSize 변경은 reader record order/memory가 아니라 fake GraphOperations의 write flush 호출 수만 바꾼다는 것을 확인한다.

- [ ] Step 4: 커밋

git add graph-io/*/src/test
git commit -m "streaming reader의 생성 대용량과 정책 회귀를 검증한다"

## Task 7: README locale parity와 durable lesson

복잡도: 중간. 선행: Task 6 green. Pattern: bluetape-writer.

Files: `graph-io/core/README.md`, `graph-io/core/README.ko.md`, `graph-io/csv/README.md`, `graph-io/csv/README.ko.md`, `graph-io/jackson2/README.md`, `graph-io/jackson2/README.ko.md`, `graph-io/jackson3/README.md`, `graph-io/jackson3/README.ko.md`, `graph-io/graphml/README.md`, `graph-io/graphml/README.ko.md`, `graph-io/okio/README.md`, `graph-io/okio/README.ko.md`; create `docs/lessons/2026-08-12-issue-313-streaming-reader-parity.md`.

- [ ] Step 1: reader와 batchSize 문서화

각 locale에 다음 의미를 동일하게 기록한다.

~~~text
GraphRecordFlowReader는 record를 순차적으로 읽는 reader streaming 축이다.
GraphImportOptions.batchSize는 backend createVertices/createEdges write flush 축이며
reader 보관량과 source close ownership을 바꾸지 않는다. Path/owned source는 library가
닫고 caller-owned source는 기본적으로 닫지 않으며 NDJSON edge staging은
maxEdgeBufferSize로 제한된다.
~~~

영문과 한국어 prose만 번역하고 API명/명령/수치는 보존한다. git diff --check와 locale heading 비교를 실행한다.

- [ ] Step 2: lesson 작성

각 신규 public reader의 한국어 KDoc에 cold/re-read, record order, ownership, cancellation, raw external ID 미해결을 반영한다. 실제 source ownership, GraphML materialization 제거, NDJSON queue 경계, 검증 명령, review에서 발견한 guard를 한국어 lesson으로 기록한다. 일반론만 있는 lesson은 통과시키지 않는다.

## Task 8: 최종 검증과 PR 준비

복잡도: 높음. 선행: Tasks 1–7. Pattern: verification-before-completion, bluetape-kotlin-patterns.

- [ ] Step 1: compile/detekt/diff

~~~bash
./gradlew :bluetape4k-graph-io-core:compileKotlin :bluetape4k-graph-io-csv:compileKotlin :bluetape4k-graph-io-jackson2:compileKotlin :bluetape4k-graph-io-jackson3:compileKotlin :bluetape4k-graph-io-graphml:compileKotlin :bluetape4k-graph-okio:compileKotlin --no-daemon
./gradlew :bluetape4k-graph-io-core:detekt :bluetape4k-graph-io-csv:detekt :bluetape4k-graph-io-jackson2:detekt :bluetape4k-graph-io-jackson3:detekt :bluetape4k-graph-io-graphml:detekt :bluetape4k-graph-okio:detekt --no-daemon
git diff --check
~~~

diagnostics를 읽은 뒤 수정하고 같은 명령을 재실행한다. rg로 !!, raw exception log, ownership 누락, public KDoc 위반을 검사한다.

- [ ] Step 2: ABI와 README parity

repository의 실제 ABI task를 먼저 확인하고 실행한다. 기대 결과는 기존 importer 시그니처 유지와 additive reader/exception만 포함하는 diff다.

- [ ] Step 3: Type-A review

performance/stability scan, verifier checklist, final code-review reference를 읽고 6개 관점과 main integration을 현재 diff에 적용한다. P0/P1이 있으면 PR 전에 수정하고 affected test를 재실행한다.

- [ ] Step 4: lesson/review commit

git add docs/lessons docs/review
git commit -m "streaming reader parity의 검증 교훈을 남긴다"

- [ ] Step 5: PR 전 live 상태

~~~bash
git status --short --branch
git log --oneline --decorate -8
gh issue view 313 --repo bluetape4k/bluetape4k-graph --json state,milestone,assignees,labels,url
gh pr list --repo bluetape4k/bluetape4k-graph --head debop:feat/issue-313-streaming-reader-parity --state all --json number,state,url,headRefOid
~~~

PR 생성은 승인된 계획 범위이지만 merge는 별도 사용자 승인 없이는 실행하지 않는다.

## Step 3-R 계획 리뷰

리뷰 범위는 이 계획 전체, 승인된 설계, 이슈 #313 live acceptance, 저장소 AGENTS.md,
`bluetape-full-feature`의 step-3R/관점 체크리스트다. 여섯 관점을 main-session에서
독립적으로 재검토하고 수정 사항을 통합했다.

| Priority | Lens | Evidence | Required edit / disposition | Rerun lane |
| --- | --- | --- | --- | --- |
| P2 | performance | Task 6 generated 10,000-record fixture와 parser read-ahead/edge queue counter | heap peak 자체는 비결정적이므로 counter와 GraphML `List` 부재 소스 검사를 수용 증거로 고정 | performance |
| P2 | stability | Task 2–5에 EOF/truncation, cancellation, exactly-once terminal, close primary/suppressed 검증이 있음 | 취소 시 `CancellationException`을 보존하고 owned close를 `finally`로 수행하는 구현 규칙을 각 reader task에 고정 | stability |
| P2 | security | Task 1 safe message/cause, Task 3–4 raw codec/XML message 제거와 XXE negative test | public exception에는 raw cause/record ID/path를 전달하지 않는 API로 계획 수정 완료 | security |
| P2 | operator/Ops | parser-only 변경이며 새 backend/workflow/release side effect가 없음 | rollback, live issue/PR read-back, CI evidence를 Task 8에 유지; Nightly dispatch는 해당 범위가 아니므로 N/A | operator/Ops |
| P2 | developer/API | Task 1→2–4→5→6→7→8 순서, additive reader, existing importer/virtual-thread wrapper 보존 | Jackson2/3 classpath 분리를 이유로 module-local parser를 유지하고 core만 공유하도록 명시 | developer/API |
| P2 | user/caller | README locale pair, 한국어 KDoc, CSV unsupported와 one-shot source 제약이 계획됨 | reader streaming 축과 backend `batchSize` 축을 같은 locale 문구로 문서화하도록 고정 | user/caller |

통합 결과: P0=0, P1=0. 조건부 항목 중 backend capability/Testcontainers, 신규 module/BOM/CI
등록, Spring Boot/Exposed 변경, JDK preview migration은 이번 parser-only 범위에 해당하지 않아
N/A로 기록한다. 모든 수용 기준은 Acceptance traceability 표의 task와 증거로 연결된다.

## Acceptance traceability

| 이슈 수용 기준 | 계획 task | 증거 |
| --- | --- | --- |
| 공통 vertex/edge/parse failure/close/suspend contract | 1–5 | exception, six reader contract tests, ownership matrix |
| 다섯 포맷 parity audit | 2–5 | module implementation + sequential tests |
| generated bounded-memory input | 4, 6 | deterministic fixtures, no GraphML list, edge queue counter |
| duplicate/missing endpoint 보존 | 2, 3, 4, 6 | existing policy/report assertions across CSV/NDJSON/GraphML |
| reader streaming과 backend batchSize 문서화 | 6, 7 | core/format README locale pair |

## Risk prediction

| ID | 신호 | 완화 | 재실행 |
| --- | --- | --- | --- |
| R1 | StAX가 다시 전체 list를 축적 | sink + generated counter/heap review | Task 4→6 |
| R2 | cancellation 뒤 parser가 계속 read | channel result/read-count fake/finally close | Task 2–5 |
| R3 | caller-owned source 조기 close | ownership matrix | Task 2/5/6 |
| R4 | policy 회귀 | 기존 policy logic 유지 + report regression | Task 2/3/6 |
| R5 | safe exception과 기존 log 충돌 | raw exception message 제거 + security review | Task 1–4/8 |

## Plan DoD

- [x] 승인된 설계의 파일·책임·순서 매핑
- [x] 각 behavior의 RED/GREEN 명령과 예상 결과
- [x] 수용 기준·문서·rollback·risk traceability
- [x] 6개 관점 plan review P0=0/P1=0
- [ ] 사용자 계획 승인
- [ ] 구현·검증·PR DoD
