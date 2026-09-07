# #617 GraphPlugin close 재시도 7-Tier review

## 범위와 기준

- Issue: [#617](https://github.com/bluetape4k/bluetape4k-graph/issues/617)
- Branch: `fix/issue-617-graph-plugin-close-retry`
- Review base: `feat/issue-615-bounded-ndjson-lines`
  `efc0c02766d63e6999beb714ef80246692b48b63`
- 구현 review head: `f90db290c663cdbcf11cc23d4c0fba5b1d3ff3c5`
- Scope: `GraphPluginCloseAction`, `GraphPluginState`, managed resource rollback
  경계, 동시 close와 실패 후 재시도 테스트, EN/KO module 문서

독립 `code-reviewer` lane은 제한 시간 안에 usable verdict를 반환하지 못해
중단했다. 아래 종합의 일반 검토 부분은 이 provenance를 독립 검토로 포장하지 않은
main lane의 exact-diff fallback review다. 별도 독립 `architect` lane은 구현 review
head를 다시 고정하고 `WATCH`를 반환했다.

## 7-Tier 판정

| Tier | 판정 | 근거 |
|---|---|---|
| T1 컴파일·API·ABI | PASS | `:bluetape4k-graph-ktor:check --rerun-tasks`가 통과했다. `GraphPluginState`의 public `javap` 출력은 parent와 같고, 변경한 close action과 AtomicFU field는 Kotlin internal/private 경계다. |
| T2 기능·호환성 | PASS | Action은 `OPEN → CLOSING → CLOSED/OPEN`으로 전이해 성공한 action만 완료 상태로 고정한다. State-level CAS는 동시에 하나의 action pass만 실행하고 후속 호출은 기다리지 않고 반환한다. |
| T3 실패·동시성·수명주기 | PASS/WATCH | 일부 action 실패 뒤에도 다음 action을 실행하고, 후속 명시적 close는 실패 action만 재시도한다. Construction rollback 객체는 실패 뒤 폐기되어 retained retry handle이 없으며, 이 경계는 #618의 공통 registry 계약에서 결정한다. |
| T4 보안·오류 노출 | PASS/WATCH | 실패 원인은 기존 `warn(e)` 경로에 원래 Throwable로 전달되고 action 이름은 고정된 resource 종류다. Logger가 받은 동일 cause를 직접 검증하는 회귀는 없다. |
| T5 성능·boundedness | PASS | Close path는 state/action별 CAS와 기존 O(n) 순회만 사용한다. monitor, 추가 thread, blocking wait, 신규 dependency를 도입하지 않는다. |
| T6 ecosystem·Kotlin 패턴 | PASS | Class property 상태는 기존 project dependency인 `kotlinx.atomicfu.atomic`을 사용한다. 테스트는 JUnit 5, MockK, `bluetape4k-assertions`, `io.bluetape4k.assertions.assertFailsWith`를 유지한다. 정밀한 latch 순서 제어가 필요해 stress 중심 `StructuredTaskScopeTester` 대신 timeout이 있는 Virtual Thread executor를 사용했다. |
| T7 테스트·문서·인계 | PASS/WATCH | 19 tests, Detekt, Kover, module check가 통과했다. KDoc, README EN/KO, CHANGELOG, WIP, lesson을 동기화했다. Hosted exact-head CI와 train merge는 PR 이후 gate다. |

## Finding과 처리

| 심각도 | Finding | 처리 |
|---|---|---|
| P1 | Action 단위 guard만 두면 첫 action 실행 중 다른 close 호출이 다음 action을 먼저 실행해 등록 순서를 깨뜨린다. | 두 action과 latch를 사용한 RED(`expected 0, actual 1`)를 추가하고 `GraphPluginState` 전체 pass CAS로 수정했다. |
| P2 | Class property에 raw JDK atomic을 사용해 Kotlin pattern의 AtomicFU 규칙과 어긋났다. | `GraphPluginCloseAction`과 `GraphPluginState`를 `kotlinx.atomicfu.atomic`으로 변경하고 module check를 재실행했다. |
| P2 | 동시 close는 대기 직렬화가 아니라 진행 중인 pass로 합쳐진 뒤 즉시 반환한다. | KDoc, 테스트명, README EN/KO, CHANGELOG, lesson에서 coalescing과 비대기 반환을 명시했다. |
| P2 WATCH | `ManagedGraphPluginResources.rollback()`은 cleanup 전에 committed 상태로 전환하고 construction failure 뒤 폐기되므로 실패한 rollback action의 재시도 통로가 없다. | Primary construction exception과 기존 warning 정책을 유지하는 현행 경계를 이번 수정에서는 보존한다. 대기·rollback failure 계약은 이미 열린 #618 공통 registry adoption에서 결정한다. |
| P3 WATCH | Action의 원래 Throwable이 logger에 전달되는지는 구현과 직접 action 예외 검증으로 확인했지만 logger capture 테스트는 없다. | 예외를 변환하지 않는 `finally`와 `warn(e)` 경로를 exact diff에서 확인했다. 전용 logging test helper가 없는 현재 module에서 새 harness는 추가하지 않았다. |

최종 blocker는 P0=0, P1=0이다. 남은 P2=1, P3=1은 공개 API를 깨뜨리지 않는
경계 결정과 test-harness 보강 항목이며, #618 설계 입력으로 추적한다.

## 검증 증거

- TDD RED: 첫 close 실패 뒤 재호출 attempt가 `1`에 머무는 실패를 확인했다.
- TDD RED: action-level guard만 둔 중간 구현에서 두 번째 action이 첫 번째 action
  완료 전에 실행되는 `expected 0, actual 1` 실패를 확인했다.
- Target GREEN: 동시 state close 회귀 `1/1`, 전체 `GraphPluginTest` `14/14` 통과.
- `./gradlew :bluetape4k-graph-ktor:check`: 19 tests, Detekt, Kover,
  module check `BUILD SUCCESSFUL`.
- 독립 architecture lane의
  `./gradlew :bluetape4k-graph-ktor:check --rerun-tasks`: 19 tests, Detekt,
  Kover, module check 통과.
- `git diff --check`: PASS.
- 변경 테스트의 금지 assertion scan: 0건.
- `javap -public`: `GraphPluginState`의 기존 constructor, getter, `close()`
  descriptor가 parent와 동일하다.
- Korean terminology audit: 변경 한국어 문서 4개, finding 0건.

## DoD Status

- [x] 일부 close action 실패가 나머지 action 실행을 막지 않는다.
- [x] 실패 action은 `OPEN`으로 복원하고 원래 cause를 warning 경로에 전달한다.
- [x] 성공 action은 중복 실행하지 않고 후속 명시적 close에서 실패 action만 재시도한다.
- [x] 동시 close는 action 순서를 깨뜨리지 않고 하나의 pass로 합쳐진다.
- [x] Kotlin AtomicFU와 bluetape4k assertion 규칙을 적용한다.
- [x] 독립 architecture review와 inline fallback review에서 P0/P1=0으로 수렴했다.
- [ ] PR exact-head hosted CI와 live review/thread read-back은 PR 생성 후 수행한다.

최종 로컬 판정: **PASS/WATCH**. 독립 `code-reviewer` provenance는 `PENDING`이지만,
독립 architecture review와 inline exact-diff fallback, fresh local gate는 PR 생성을
막는 P0/P1이 없음을 확인했다.
