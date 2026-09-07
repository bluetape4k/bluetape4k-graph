# #618 공통 resource registry 도입 7-Tier review

## 범위와 기준

- Issue: [#618](https://github.com/bluetape4k/bluetape4k-graph/issues/618)
- Branch: `feat/issue-618-common-resource-registry`
- Review base: `chore/issue-605-dependency-graph`
  `89007b70f108cd68183fe156bbf1e5c45d48e11f`
- 구현 review head: `47468f33dd5f6c7236c4f575f9504644555795a2`
- Scope: 공통 Ktor resource registry dependency, GraphPlugin lifecycle 연결,
  ownership/failure/late-registration 회귀, EN/KO module 문서와 publication metadata

독립 `code-reviewer` lane은 15분 제한 안에 usable verdict를 반환하지 못해 중단했다.
아래 표는 이 provenance를 독립 검토로 포장하지 않은 main lane exact-diff fallback
review다.

## 7-Tier 판정

| Tier | 판정 | 근거 |
|---|---|---|
| T1 컴파일·API·ABI | PASS | `:bluetape4k-graph-ktor:check`가 통과했다. 기존 public constructor/getter/`close()` descriptor는 유지되고 Kotlin `internal` registry 연결 method는 `@JvmSynthetic`으로 Java source surface에서 숨긴다. |
| T2 기능·호환성 | PASS | Plugin 소유 action이 있을 때만 registry group 한 건을 등록한다. `closeOnStop=false` caller-owned operations는 report `attempted=0`이며 자동으로 닫히지 않는다. |
| T3 실패·동시성·수명주기 | PASS/WATCH | Group은 모든 Graph action을 시도한 뒤 하나라도 실패하면 common report에 sanitized failure를 남긴다. JVM `Error`는 다른 action 실행 후 cause 없는 fatal marker로 전달해 report의 `fatal=true`를 보존한다. 실패 action은 Graph state에서 후속 명시적 close가 재시도한다. Concurrent direct close는 #617의 비대기 coalescing 계약을 유지한다. |
| T4 보안·오류 노출 | PASS | Common report에는 opaque registration ID, phase와 fatal flag만 남기고 원래 cause/action name은 기존 Graph warning logger에만 전달한다. 신규 credential, network, reflection 경로는 없다. |
| T5 성능·boundedness | PASS | Close action 목록 하나를 registry entry 하나로 등록한다. 추가 thread, coroutine scope, monitor polling, timeout 또는 unbounded queue가 없다. |
| T6 ecosystem·Kotlin 패턴 | PASS | 공식 `bluetape4k-ktor-core`의 `installApplicationResourceLifecycle`을 재사용하고 신규 local lifecycle abstraction을 만들지 않았다. 테스트는 JUnit 5와 `bluetape4k-assertions`를 사용한다. |
| T7 테스트·문서·publication | PASS/PENDING | 23 tests, Detekt, Kover와 POM validator가 통과했다. README EN/KO, CHANGELOG, WIP와 lesson을 동기화했다. Exact-head hosted CI는 PR 이후 gate다. |

## Finding과 처리

| 심각도 | Finding | 처리 |
|---|---|---|
| P1 | Graph action을 registry에 개별 등록하면 common LIFO가 기존 Graph 등록 순서를 뒤집고 concurrent direct close와 action 단위로 엇갈릴 수 있다. | 기존 Graph action 순회를 하나의 bounded resource group으로 등록했다. |
| P1 | Common registry가 group 실패를 claim한 뒤 재시도하지 않으므로 Graph의 실패 action 재시도 계약을 제거하면 resource가 열린 채 남을 수 있다. | `GraphPluginState`의 action 상태를 유지하고 후속 명시적 `close()`에서 실패 action만 재시도한다. |
| P1 | Group wrapper가 모든 실패를 일반 예외로 바꾸면 원래 `Error`도 common report의 `fatal=false`로 축소된다. | RED를 추가하고 fatal 여부를 별도로 수집해 cause 없는 `Error` marker로 registry에 전달한다. Direct Graph close의 기존 비전파 정책은 유지한다. |
| P2 WATCH | Concurrent direct close 진행 중 registry close가 합쳐지면 common report가 선행 pass 완료 전에 성공으로 끝날 수 있다. | #617의 비대기 coalescing 계약을 유지하고 KDoc/README/lesson에 완료 보장 범위를 기록했다. 대기 semantics는 별도 API 결정 없이는 추가하지 않는다. |
| P2 WATCH | Common report는 Graph action별 cause/count 대신 group 실패 한 건만 노출한다. | Bounded report와 redaction을 우선하고, 원래 cause/action name은 Graph logger에 보존한다. |
| P3 | Kotlin `internal` registry 연결 method가 JVM bytecode에 mangled public member로 나타난다. | 기존 repository pattern에 맞춰 `@JvmSynthetic`을 적용해 Java source surface에서 숨기고 기존 descriptor는 변경하지 않는다. |

현재 main lane blocker는 P0=0, P1=0이다. 남은 WATCH는 기존 비대기 close 계약,
관측 단위이며 exact-head 독립 검토에서 다시 확인한다.

## 검증 증거

- TDD RED: state close wrapper 도입 전 registry report `attempted`가 `expected 1,
  actual 0`으로 실패했다.
- Fatal RED: 원래 `Error`가 일반 group failure로 축소돼 common registry가 marker를
  던지지 않는 실패를 확인했다.
- Target GREEN: `GraphPluginTest` 18개 통과.
- Module gate: `:bluetape4k-graph-ktor:check` 23 tests, Detekt, Kover 성공.
- Publication: generated POM/module metadata에
  `bluetape4k-ktor-core:2.1.0-SNAPSHOT` 포함.
- POM audit: 1 POM, 125 dependency entries, 1 Maven effective model 성공.
- Stacked parent rebase 후 module gate와 publication generation을 다시 실행해
  30 actionable tasks, POM 1개/125 dependencies/1 Maven model이 통과했다.
- `javap -v`에서 registry 연결 method가 `ACC_PUBLIC, ACC_FINAL, ACC_SYNTHETIC`임을
  확인했다.
- `git diff --check`: PASS.

## DoD Status

- [x] Projects 공통 API의 merged source와 최신 snapshot binary signature를 확인했다.
- [x] Graph 소유 close action만 common registry의 bounded group으로 등록한다.
- [x] Caller-owned, late registration, 일부 실패와 기존 action 순서를 회귀로 고정한다.
- [x] JVM `Error`의 fatal 분류와 sanitized marker 경계를 보존한다.
- [x] 기존 Graph state 유지 이유와 common report 경계를 기록한다.
- [x] Module check와 generated publication metadata 검증이 통과했다.
- [ ] 독립 exact-diff `code-reviewer` verdict는 제한 시간 초과로 `PENDING`이다.
- [ ] PR exact-head hosted CI와 live review/thread read-back은 PR 생성 후 수행한다.

현재 로컬 판정: **PASS/WATCH**. Main lane exact-diff에서 P0/P1=0이며 review head를
커밋으로 고정한 뒤 PR을 생성한다.
