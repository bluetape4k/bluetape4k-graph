# #539 graph-io CSV/GraphML export 7-Tier review

## 판정

- 기준 HEAD: `db578e6d` (`#539의 export lifecycle 실패를 원인 예외 보존으로 고정한다`)
- 대상 이슈: [#539](https://github.com/bluetape4k/bluetape4k-graph/issues/539)
- 범위: `graph-io/core`, `graph-io/csv`, `graph-io/graphml`의 sync·suspend export, 회귀 테스트, README, 설계·계획 문서
- 최종 판정: **PASS / WATCH**
- 심각도: P0 0, P1 0, P2 4, P3 1

P1은 최초 독립 review에서 확인된 replay input 누수와 cleanup 예외 masking을
수정한 뒤 재검증했다. P2/P3는 backend capability 차이와 후속 TCK·메모리
hardening 범위로 분리한다. 이 review는 PR 생성·merge·issue close를 승인하지 않는다.

## 수용 기준 추적

| 기준 | 근거 | 상태 |
| --- | --- | --- |
| CSV sync/suspend가 backend chunk를 한 번 읽고 bounded spool/replay를 사용 | `CsvGraphBulkExporter.kt`, `SuspendCsvGraphBulkExporter.kt`, CSV chunk-only 회귀 | PASS |
| GraphML sync/suspend가 live second pass 없이 동일 spool 기준 데이터를 사용 | `GraphMlBulkExporter.kt`, `SuspendGraphMlBulkExporter.kt`, GraphML chunk-only 회귀 | PASS |
| empty·multi-label·property union·caller-owned close·cancellation·failure 경계 | core spool 4개, CSV 53개, GraphML 46개 테스트 | PASS |
| replay 조기 종료에서 input close | `GraphIoRecordSpool.replayInputs`, abandoned iterator 회귀 | PASS |
| 원래 source/sink/cancellation 예외 보존 | 네 exporter의 `primaryFailure`와 `closeSuppressing` | PASS |
| #469/#471의 기존 bounded/chunk 계약과 중복 없이 정리 | 종료 이슈를 source ledger와 설계 문서에서 구분 | PASS |

## 7-Tier 결과

| Tier | 검토 내용 | 판정 및 잔여 위험 |
| --- | --- | --- |
| 1. Correctness | spool append 시 문자열 정규화, property key 발견 순서, header와 payload의 동일 replay | PASS. 정점·간선·빈 입력·다중 label 회귀가 통과했다. |
| 2. API/ABI | 기존 네 exporter signature와 `GraphExportOptions` 유지, VT adapter는 sync delegate, `GraphIoRecordSpool`은 additive support API | PASS. public `closeSuppressing`은 #539에서 새로 추가된 API이므로 사용 목적을 KDoc에 고정했다. |
| 3. Performance/Boundedness | backend chunk를 디스크로 stage하고 heap에는 chunk와 metadata만 유지 | WATCH. chunk API를 override/cursor로 제공하는 backend에서만 exporter-side bound가 성립한다. fallback의 label 전체 materialization과 128 MiB record buffer peak는 후속 이슈다. |
| 4. Reliability/Concurrency | active replay input을 추적하고 close 시 닫음; suspend cleanup은 `NonCancellable + Dispatchers.IO`; primary failure에 cleanup failure를 suppressed로 연결 | PASS. sync·suspend sink failure 및 abandoned replay 회귀가 통과했다. |
| 5. Security/Resource | temp binary file, UTF-8 normalization, record length guard, caller-owned output 미닫음 | WATCH. constructor 중간 실패 cleanup과 record serialization peak memory는 아직 hardening 대상이다. |
| 6. Tests/Observability | Bluetape assertions, chunk-only fake, caller-owned stream, cancellation, failure identity, full module test와 detekt | PASS/WATCH. 외부 backend container conformance와 output replay 중 취소 checkpoint는 이 이슈에서 실행하지 않았다. |
| 7. Documentation/Maintainability | 영문·국문 README, 설계·계획, public KDoc, lesson, source-to-claim traceability | PASS. boundedness의 backend 전제를 README에 명시했다. 내부 writer의 기존 API 설명은 범위 밖이며 후속 검토 대상으로 남긴다. |

## 독립 review disposition

최초 exact-head review는 replay input lifecycle과 cleanup exception masking을 P1로
판정했다. 다음 변경으로 두 결함을 닫았다.

1. `GraphIoRecordSpool`이 replay `DataInputStream`을 등록하고 `close()`에서 모든
   active input을 독립적으로 닫는다. sequence가 `yield`에서 abandon되어도 spool
   cleanup이 descriptor를 회수한다.
2. 네 exporter가 `primaryFailure`를 catch에서 보존하고 `closeSuppressing`을
   호출한다. 원래 write/source/cancellation 예외는 primary로 남고 cleanup 실패만
   suppressed가 된다.
3. core abandoned replay, CSV/GraphML sync·suspend failing sink 회귀를
   `io.bluetape4k.assertions.assertFailsWith`로 고정했다.

## 검증 증거

```text
./gradlew :bluetape4k-graph-io-core:test \
  :bluetape4k-graph-io-csv:test \
  :bluetape4k-graph-io-graphml:test \
  --rerun-tasks --no-daemon --console=plain
SUCCESS: Executed 143 tests in 2.8s
SUCCESS: Executed 53 tests in 3.1s
SUCCESS: Executed 46 tests in 2.1s
BUILD SUCCESSFUL

./gradlew :bluetape4k-graph-io-core:detekt \
  :bluetape4k-graph-io-csv:detekt \
  :bluetape4k-graph-io-graphml:detekt --no-daemon --console=plain
BUILD SUCCESSFUL

git diff --check
PASS

금지 assertion 검색(assertThrows, kotlin.test.assertFailsWith, shouldThrow)
0 matches
```

## 후속 범위

- backend가 실제 bounded chunk/cursor를 제공하는지 검증하는 backend matrix와
  기준 데이터 변경 TCK는 [#556](https://github.com/bluetape4k/bluetape4k-graph/issues/556)로
  추적한다.
- `ByteArrayOutputStream` 기반 per-record serialization의 peak memory와
  constructor 중간 실패 cleanup은 [#557](https://github.com/bluetape4k/bluetape4k-graph/issues/557)로
  추적한다.
- suspend output replay 중 record 단위 cancellation checkpoint와 output lifecycle
  TCK는 [#558](https://github.com/bluetape4k/bluetape4k-graph/issues/558)로 추적한다.
- GraphML 내부 writer KDoc의 bounded 표현은 이 이슈에서 public README 계약으로
  보정했으며, 별도 API 변경 없이 다음 review에서 재확인한다.

## SPW-01 Source ledger

| 출처 | 사용 목적 |
| --- | --- |
| [#539](https://github.com/bluetape4k/bluetape4k-graph/issues/539) | 현재 acceptance와 P2 범위 |
| [#469](https://github.com/bluetape4k/bluetape4k-graph/issues/469) | Jackson list/toList 선행 이슈와 중복 방지 |
| [#471](https://github.com/bluetape4k/bluetape4k-graph/issues/471) | GraphML chunk/bounded reader 선행 이슈와 차이 |
| `docs/superpowers/specs/issue-539-graph-io-export-design.md` | immutable disk spool 선택과 실패 계약 |
| `docs/superpowers/plans/issue-539-graph-io-export.md` | RED→구현→검증 순서 |
| `GraphIoRecordSpool.kt` 및 네 exporter | 구현 사실과 resource 경계 |
| core/CSV/GraphML 테스트 | 회귀와 full-count 증거 |

## SPW-02 Review contract

7-Tier는 correctness, API/ABI, performance/boundedness, reliability/lifecycle,
security/resource, tests/observability, docs/maintainability를 각각 독립
판정한다. P0/P1은 이슈를 남긴 채 PASS할 수 없으며, P2/P3는 후속 issue로
추적한다. exact HEAD와 fresh test output만 최종 증거로 사용했다.

## SPW-03 Korean naturalness checklist

- SPW-01~05 섹션과 독자 대상 문장은 한국어로 작성했다.
- `GraphIoRecordSpool`, `Dispatchers.IO`, `NonCancellable`, Gradle 명령과 URL은
  계약상 영문 토큰을 보존했다.
- `bounded`, `replay`, `suppressed exception`은 코드·API 개념을
  가리키므로 첫 사용 시 주변 한국어 문장으로 의미를 고정했다.
- README와 review의 영어·한국어 버전 경계를 섞지 않았다.

## SPW-04 Source-to-claim traceability

| 주장 | 위치 |
| --- | --- |
| active replay input이 close에서 회수됨 | `GraphIoRecordSpool.kt`의 `replayInputs`, `closeReplayInputs` |
| 원래 실패가 보존됨 | CSV/GraphML 네 exporter의 `primaryFailure`, `closeSuppressing` |
| boundedness에 backend 전제가 있음 | core/CSV/GraphML README의 export performance 문단 |
| 회귀가 통과함 | 위 검증 명령의 143+53+46 test 결과 |

## SPW-05 Render/read-back

문서는 파일로 다시 읽어 제목·표·코드 블록·URL을 확인했고 `git diff --check`를
통과했다. `audit-korean-terms.mjs`는 7개 파일에서 finding 0으로 통과했다.
