# #615 bounded NDJSON 줄 7-Tier review

## 범위와 기준

- Issue: [#615](https://github.com/bluetape4k/bluetape4k-graph/issues/615)
- Branch: `feat/issue-615-bounded-ndjson-lines`
- Review base: `fix/issue-614-graphml-property-types`
  `0c258afd5e5942f2f3e920538e5edc655505e8e0`
- Review head: `5873bb294b5d1a67b326ec2fd3a5124b84d6a6bf`
- Scope: graph-io core options, Jackson2·3 sync/suspend/Virtual Thread/Flow
  readers, checkpoint identity, tests, EN/KO module 문서

독립 `code-reviewer` lane 두 번이 각각 15분·5분 제한 안에 usable verdict를
반환하지 못해 중단했다. 아래 판정은 이 provenance를 독립 검토로 포장하지 않은
main lane의 exact-diff fallback review다.

## 7-Tier 판정

| Tier | 판정 | 근거 |
|---|---|---|
| T1 컴파일·API·ABI | PASS | core, Jackson2, Jackson3 module `check`가 통과했다. 새 `NdJsonReadOptions`만 공개 API로 추가했고, `javap`에서 기존 importer no-arg와 Flow `(String, String)` constructor 및 기존 method descriptor가 유지됨을 확인했다. |
| T2 기능·호환성 | PASS | `Reader.boundedLineReader`가 codec 호출 전에 `maxLineChars`를 적용한다. 기본 `Int.MAX_VALUE`는 기존 입력을 사실상 제한하지 않고, limit-1/limit/limit+1 및 LF/CRLF/CR/EOF를 Jackson2·3에서 검증한다. |
| T3 실패·취소·수명주기 | PASS | Sync, suspend, Virtual Thread, Flow가 같은 옵션을 사용한다. 연속 빈 줄 Flow도 취소를 관찰하며, owned source는 한 번 닫힌다. 줄 상한 오류는 close 실패보다 primary이고 close 오류는 suppressed로 남는다. |
| T4 보안·오류 노출 | PASS | 제한 초과 failure에는 phase, `UNIFIED`, line과 설정 상한만 포함한다. raw payload의 `secret-record`와 source path가 오류에 포함되지 않는 회귀를 검증한다. |
| T5 성능·boundedness | PASS | 개행 없는 generated 입력에서 전체 source보다 적은 byte만 읽고 실패한다. 별도 scanner나 전체 레코드 materialization 없이 bluetape4k-io의 bounded reader를 재사용한다. |
| T6 ecosystem·Kotlin 패턴 | PASS | 기존 `bt4k.bluetape4k.io`, `requirePositiveNumber`, immutable/serializable option, `bluetape4k-assertions`를 사용한다. 신규 예외 검증은 `io.bluetape4k.assertions.assertFailsWith`로 통일했다. |
| T7 테스트·문서·인계 | PASS/WATCH | Core 182, Jackson2 33, Jackson3 33, 총 248 tests가 failure/error/skip 없이 통과했다. README EN/KO, KDoc, CHANGELOG, WIP, design, plan, lesson을 갱신했다. Hosted exact-head CI와 train merge는 PR 이후 gate다. |

## Finding과 처리

| 심각도 | Finding | 처리 |
|---|---|---|
| P2 | 줄 상한 실패와 owned source close 실패가 동시에 발생할 때 primary 오류 보존을 직접 고정하지 않았다. | Jackson2·3 모두 제한 초과를 primary로, close 오류를 suppressed로 검증하는 회귀를 추가했다. |
| P3 | #614 CI가 이미 성공했지만 `WIP.md`가 확인 중이라고 기록했다. | Exact-head CI 통과 상태로 최신화했다. |

최종 unresolved finding은 P0=0, P1=0, P2=0, P3=0이다.

## 검증 증거

- TDD RED: 옵션 타입 부재, Flow option constructor 부재, 여섯 importer
  constructor 부재를 단계별 compile failure로 확인했다.
- `./gradlew :bluetape4k-graph-io-core:check
  :bluetape4k-graph-io-jackson2:check
  :bluetape4k-graph-io-jackson3:check`: `BUILD SUCCESSFUL`.
- JUnit XML: core `182`, Jackson2 `33`, Jackson3 `33`; failures/errors/skipped
  모두 `0/0/0`.
- `git diff --check`: PASS.
- 변경 테스트의 금지 assertion scan: 0건.
- `javap -public`: 기존 no-arg importer constructor와 Flow reader의
  `(String, String)` constructor 및 기존 public method를 유지한다.

## DoD Status

- [x] Codec 이전에 한 줄 길이를 제한하고 기본 호환성을 유지한다.
- [x] Jackson2·3의 모든 실행 모델과 checkpoint identity에 같은 정책을 적용한다.
- [x] 경계값, Unicode/개행, 조기 소비 중단, 취소, ownership, close suppression을 검증한다.
- [x] Public option과 Java-visible constructor, README/KDoc/lesson을 갱신한다.
- [ ] PR exact-head hosted CI와 live review/thread read-back은 PR 생성 후 수행한다.

최종 로컬 판정: **PASS/WATCH**. 독립 lane은 usable verdict 없이 중단되어
independent provenance는 `PENDING`이며, inline exact-diff 검토와 로컬 기술 증거는
PR 생성에 필요한 P0/P1 blocker가 없음을 확인했다.
