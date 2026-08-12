# graph-io 포맷 간 streaming import reader parity 설계

## 문제와 목표

이슈 #313은 CSV, Jackson2 NDJSON, Jackson3 NDJSON, GraphML, OkIO 입력이
동일한 streaming import reader 의미론을 제공하도록 정렬하는 작업이다. 현재
`GraphRecordFlowReader` 공개 계약은 존재하지만 구현체가 없고, 포맷별 importer의
수명 관리·edge 보관·GraphML materialization 방식이 서로 다르다. 목표는 대형
입력을 전체 메모리에 올리지 않고 정점/간선 레코드를 순차적으로 읽으며, parse
failure의 위치와 source close 소유권, suspend cancellation/backpressure를
포맷 간 동일하게 관찰할 수 있게 하는 것이다.

## 현재 근거

- `graph-io/core`에 `GraphRecordFlowReader<S>`가 있으며 `readVertices`와
  `readEdges`가 cold `Flow`를 반환하도록 정의되어 있다.
- `GraphImportSource.PathSource`는 라이브러리가 열고 닫고,
  `InputStreamSource`는 `closeInput`으로 호출자 소유권을 선택한다.
- CSV는 `CsvGraphImportSource`로 정점/간선 파일을 분리한다.
- Jackson2/3 blocking importer는 `BufferedReader.forEachLine`으로 읽지만 edge를
  입력 종료까지 `ArrayDeque`에 저장한다. 상한은
  `GraphImportOptions.maxEdgeBufferSize`이다.
- GraphML의 `StaxGraphMlReader.read`는 정점/간선 `List`를 모두 만든 뒤 importer가
  이를 순회한다.
- OkIO importer는 source를 `InputStream`으로 변환해 각 포맷 importer에 위임하며,
  `PathSource`, `SourceBased`, `InputStreamBased`마다 소유권 플래그가 있다.
- 이슈 수용 기준은 기존 duplicate vertex/missing endpoint 정책 보존, generated
  large-input bounded-memory 검증, reader memory와 backend write batch size의
  문서상 분리를 요구한다.

## 설계 선택

### 권장안: 기존 Flow 계약 유지 + 포맷별 cold reader + importer 단일-pass parser

기존 `GraphRecordFlowReader`를 유일한 공통 공개 reader 계약으로 유지한다.
각 format module은 자체 `GraphRecordFlowReader` 구현을 제공하고, `flow {}` 또는
동등한 cold builder에서 collect가 시작될 때 source를 열고 `finally`에서 규칙에
따라 닫는다. `readVertices`와 `readEdges`는 각각 새 source traversal을 시작하므로
한 flow를 먼저 collect한 뒤 다른 flow를 collect해도 같은 source descriptor를
재사용할 수 있다. 단, caller-owned one-shot stream은 호출자가 새 source를
제공해야 한다는 제약을 KDoc과 테스트로 명시한다.

Importer는 Flow를 `toList`로 수집하지 않는다. blocking/suspend importer의 현재
단일-pass 처리 루프를 유지하면서 포맷별 내부 parser primitive를 공유한다. parser는
정상 record와 `GraphIoFailure`를 sink에 전달하고, public reader에서 parse failure가
발생하면 core의 `GraphIoReadException(failure)`로 종료한다. 이 예외의 public
message는 source raw line/value와 외부 record 식별자를 포함하지 않고,
허용된 `line:<n>`, `row:<n>`, `edge-buffer:<n>` location만 사용한다. importer는
이 예외의 `failure`를 기존 `GraphImportReport.failures`에
추가하여 기존 report semantics를 보존한다.

- CSV: 기존 CSV record reader의 row 단위 순회를 reader 구현과 importer가 공유한다.
  정점/간선 파일 각각을 독립적으로 열며 각 `GraphImportSource`의 close 정책을
  보존한다.
- Jackson2/3 NDJSON: line 단위 codec 순회를 reader와 importer가 공유한다. reader는
  record를 즉시 emit하고, importer는 정책상 필요한 edge만 bounded queue에 둔다.
  queue가 `maxEdgeBufferSize`를 넘으면 기존 FAILED 의미와 location을 유지한다.
  이 상한은 reader의 read memory가 아니라 import의 endpoint resolution memory임을
  문서화한다.
- GraphML: `StaxGraphMlReader`를 record sink 기반으로 바꾸어 node/edge를
  파싱하는 즉시 전달한다. sink는 정상 blocking callback과 suspend reader가
  공유할 수 있는 단일 primitive이며, `GraphMlReadResult.vertices/edges` 전체
  리스트는 production import 경로에서 제거한다. parse failure는 기존
  `GraphIoFailure` 필드(phase, location, recordId, elementName 등)로 sink에
  전달한다.
- OkIO: single-stream format은 OkIO source를 열어 underlying format reader에
  위임하는 reader adapter를 제공한다. CSV는 두 source를 묶는 기존
  `CsvGraphImportSource` 모양을 유지하고, OkIO `PathSource`/owned source는
  library-owned close, non-owned source는 caller-owned close를 동일하게 적용한다.

`Flow`의 backpressure는 downstream `emit`이 완료된 뒤 다음 record를 읽는 기본
sequential semantics로 보장한다. 내부 parser sink는 blocking callback으로 두고,
reader 구현은 `channelFlow`와 `trySendBlocking`을 사용해 parser thread를 downstream
소비까지 멈춘다. parser는 record boundary에서 collector job의 취소 상태를 확인하고,
취소된 channel 전송은 즉시 중단한다. 이 방식은 parser 자체를 suspend API로
변경하지 않으면서 blocking importer와 Flow reader가 같은 단일-pass parser를
공유하게 한다. `runCatching`으로
`CancellationException`을 삼키지 않으며, cancellation 시 parser와 owned source를
`finally`에서 닫는다.

### 제외한 대안

1. **Importer를 Flow 수집 기반으로 전면 전환**: blocking API에 coroutine bridge가
   들어가고 cancellation/예외 경계가 변하며, edge endpoint resolution을 위해
   전체 flow를 재수집할 위험이 있어 제외한다.
2. **새 callback abstraction을 공통 공개 API로 추가**: 이미 존재하는
   `GraphRecordFlowReader`와 중복되고 public API/ABI 부담을 늘리므로 제외한다.
3. **NDJSON edge buffer 제거**: vertex와 edge가 임의로 섞인 입력에서 endpoint가
   아직 생성되지 않을 수 있어 기존 missing-endpoint 정책과 순서를 바꾸게 된다.
   이번 이슈에서는 bounded 상한과 실패 위치를 보존하고, 완전한 checkpoint/resume
   또는 backend-native edge staging은 이슈 #310 및 후속 범위로 둔다.

## 컴포넌트와 수명 규칙

### 공통 계약

`GraphRecordFlowReader<S>`의 현재 메서드와 record 타입을 유지한다. 각 구현체의
KDoc은 다음을 명시한다.

- Flow는 cold이며 collect 시 source가 열린다.
- 정점/간선 record는 한 번에 하나씩 순서대로 방출된다.
- edge 외부 ID는 resolve되지 않은 값이다.
- parse failure는 `GraphIoReadException.failure`로 phase와 허용된 location만
  보존한다. source/record/column/element 식별자와 raw payload는 public failure와
  예외 message에서 제거하며, importer는 이를 기존 report failure로 변환한다.
- `PathSource`와 owned OkIO source는 library-owned, non-owned stream/source는
  caller-owned이다.
- suspend collect 취소는 다음 record 경계에서 관찰되고 owned resource를 닫는다.

### Reader 구현 위치

- `graph-io/csv`: `CsvGraphRecordFlowReader`
- `graph-io/jackson2`: `Jackson2NdJsonRecordFlowReader`
- `graph-io/jackson3`: `Jackson3NdJsonRecordFlowReader`
- `graph-io/graphml`: `GraphMlRecordFlowReader`
- `graph-io/okio`: `OkioGraphRecordFlowReader(format: GraphIoFormat)` 단일
  adapter. `CSV`를 선택하면 기존 stem 규칙으로 `{stem}_vertices.csv`와
  `{stem}_edges.csv`를 파생하고, stream-backed source는 기존 importer와 동일하게
  명시적 unsupported 결과를 낸다.

CSV/Jackson2/Jackson3/GraphML reader의 source 타입은 각각
`CsvGraphImportSource`/`GraphImportSource`를 사용하고, GraphML 옵션은 기존
`GraphMlImportOptions`를 생성자에서 받는다. 새 public data class가 필요하면
`Serializable`과 `serialVersionUID`를 포함한다. parse failure 전달을 위해 core에
`GraphIoReadException`을 추가하며, 이 예외는 `GraphIoFailure`를 보유하고 safe
message만 노출한다.

## 오류와 정책

1. 잘못된 CSV row, malformed JSON, invalid GraphML value는 포맷 기존 failure
   location을 사용한다. reader와 importer 로그는 raw line, raw XML value,
   exception message를 그대로 출력하지 않고 safe location/code만 남긴다.
2. unknown NDJSON envelope는 기존 warning semantics를 유지한다.
3. duplicate vertex와 missing endpoint 처리는 reader가 결정하지 않고 importer의
   기존 `GraphImportOptions` 정책에 맡긴다.
4. edge buffer 초과는 `maxEdgeBufferSize`를 초과하는 순간 bounded failure로
   종료하며, 이미 생성된 정점과 failure location을 기존 report에서 유지한다.
5. collector cancellation/interruption은 정상 취소로 전파하고 `CancellationException`을
   FAILED parse failure로 변환하지 않는다.
6. close 실패는 primary parse/collector failure를 가리지 않도록 기존 close
   ownership 규칙에 맞춰 suppressed로 보존한다.

## Step 2-R 설계 review

검토 범위는 이 문서, 이슈 #313 live body, `GraphRecordFlowReader`, 각 포맷
importer/reader helper, `GraphIoPaths`, `GraphIoOkioPaths` 및 기존 테스트 구조다.
Native reviewer lane은 현재 세션의 delegation 제한으로 사용하지 않고, 아래 여섯
관점을 독립적으로 수행한 뒤 main-session integration으로 재검토했다.

| Priority | Lens | Evidence | Required edit | Rerun lane |
| --- | --- | --- | --- | --- |
| P2 | performance | GraphML 기존 `List` materialization과 NDJSON queue 상한 | sink/queue 구조와 generated input 증거를 계획에 고정 | performance |
| P2 | stability | `GraphIoPaths`/`GraphIoOkioPaths`가 owned/non-owned close wrapper를 제공하지만 blocking read 중 취소는 boundary에서만 관찰됨 | boundary cancellation과 `finally` close를 contract test로 고정 | stability |
| P2 | security | Jackson importer의 기존 parse log가 exception message를 출력할 수 있음 | safe location/code 로그 규칙을 오류 섹션에 명시 | security |
| P2 | operator/Ops | 새 backend/workflow/release side effect 없음 | existing report/log와 rollback N/A를 계획에 기록 | operator/Ops |
| P2 | developer/API | 기존 public reader는 구현체가 없고 OkIO CSV는 pair/stem 제약이 있음 | reader class/source 타입과 `GraphIoReadException` 경계를 명시 | developer/API |
| P2 | user/caller | caller-owned one-shot source를 두 flow에 재사용할 수 없음 | KDoc, README, ownership matrix test로 명시 | user/caller |

Main-session integration 결과: 위 P2 항목은 구현 계획과 테스트 증거로 추적하고,
P0=0, P1=0이다. 설계 자체의 미해결 material decision은 없다.

## 테스트 전략

- 공통 reader contract test: 첫 record만 `take(1)`한 뒤 source close 상태, 순차 순서,
  caller-owned source 미종료를 확인한다.
- generated large-input test: 커밋된 대형 파일 없이 deterministic generator로
  CSV/NDJSON/GraphML 입력을 만들고, collector가 record를 즉시 소비하며 production
  GraphML 결과에 전체 `List`가 없고 NDJSON edge queue가 설정 상한을 넘지 않음을
  검증한다.
- parse failure test: malformed JSON, malformed CSV row, invalid GraphML value에서
  line/element location과 status를 검증한다.
- suspend lifecycle test: `take(1)`/cancel 중 parser가 더 읽지 않고 owned
  source가 닫히며 non-owned source는 열려 있는지 검증한다.
- policy regression: duplicate vertex와 missing endpoint의 FAIL/SKIP 결과가 기존
  report와 일치하는지 CSV/NDJSON/GraphML에서 확인한다.
- OkIO parity test: `PathSource`, `SourceBased(ownsSource=true/false)`,
  `InputStreamBased(ownsStream=true/false)`를 같은 reader contract로 검증한다.

## 호환성, 문서, 범위

- 기존 importer public method, `GraphImportOptions`, report status/count/policy를
  유지한다. `GraphRecordFlowReader`의 기존 시그니처도 유지한다.
- reader 구현체는 additive public API이며, 기존 caller는 import API를 변경하지
  않고 계속 사용할 수 있다.
- README와 README.ko.md에 “reader streaming memory”와 “backend write
  `batchSize`”가 독립적인 축임을 같은 예제로 기록한다.
- 새 dependency/module/workflow/nightly 범위는 없다. 변경 모듈은
  `graph-io/core`, `csv`, `jackson2`, `jackson3`, `graphml`, `okio`와 해당 테스트다.
- checkpoint/resume, backend-native batching, 새로운 graph backend, release/tag는
  범위 밖이다.

## 주요 failure mode와 완화

| 위험 | 결과 | 완화와 증거 |
| --- | --- | --- |
| Flow collect가 source를 조기에 닫지 않음 | file descriptor/stream leak | `use`/`finally` contract test와 owned/non-owned matrix |
| GraphML parser가 다시 List를 materialize함 | 대형 XML OOM | sink 기반 parser와 generated large-input test, `List` allocation review |
| NDJSON edge queue가 상한을 우회함 | bounded-memory 계약 위반 | queue size assertion과 overflow regression test |
| cancellation을 parse failure로 변환함 | suspend caller가 재시도/취소를 오판 | `CancellationException` 전파 test와 `NonCancellable` close 검토 |
| reader API와 importer 정책이 분리되어 duplicate/missing endpoint가 변함 | 기존 데이터 의미론 회귀 | 기존 policy test 전체 재실행과 report count 비교 |
| OkIO adapter가 caller-owned source를 닫음 | 호출자 수명 위반 | four ownership combinations test |

## 승인된 완료 기준

1. 다섯 포맷 경로가 공통 `GraphRecordFlowReader` semantics와 source close 규칙을
   문서·코드·테스트로 공유한다.
2. GraphML production import이 전체 vertex/edge list를 만들지 않는다.
3. NDJSON edge buffering은 `maxEdgeBufferSize`로 bounded이며 overflow semantics가
   보존된다.
4. generated large-input, parse-failure, cancellation/backpressure, ownership,
   duplicate/missing-endpoint regression test가 통과한다.
5. README locale parity, Korean KDoc, Kotlin pattern/static checks, compile/test/
   detekt/ABI/diff 검증이 통과한다.
6. PR 생성 전 최신 review table에서 P0=0, P1=0이며, merge는 별도 사용자 승인
   게이트로 남긴다.

## 설계 DoD

- [x] 이슈 #313 live 요구사항과 현재 구조 근거 기록
- [x] 3개 접근 비교 및 권장안 사용자 승인
- [ ] 6개 관점 설계 review와 main integration review에서 P0/P1 0
- [ ] 구현 계획과 traceability 승인
- [ ] 구현·테스트·문서·정적 검증
- [ ] PR/CI와 merge-ready DoD
