# #549 GraphCapability enum 호환성 7-Tier review

## 범위와 stacked 기준

- Issue: [#549](https://github.com/bluetape4k/bluetape4k-graph/issues/549)
- Branch: `fix/issue-549-graph-capability-compatibility`
- Base: PR #570의 live exact head
  `b5564c994948c3b92ab8546617ddf4c7128892a3`.
- Current head: `54dfb3cf4076ba0d700d769ba00c15c6a4998a2f`.
  (`fix/issue-548-tinkerpop-chunk-lifecycle`)
- Scope: graph-core `GraphCapability` enum, capability parsing/test 계약,
  graph-core public README EN/KO와 release-facing guidance
- Scope boundary: backend capability 집합 자체와 graph-io 실행 경로는 변경하지
  않고 대표 backend conformance로 회귀를 확인한다.

## 소비자 inventory와 정책

| 소비 지점 | 분류 | 정책 |
|---|---|---|
| `GraphCapabilities.defaultConstraints` | graph-core 내부 exhaustive `when` | enum과 같은 모듈에서 컴파일되며 새 값을 추가할 때 함께 갱신한다. |
| `AbstractGraphCapabilityConformanceTest` | test fixture의 `entries` 순회 | exhaustive `when`이 아니며 새 enum 값도 자동 검증한다. |
| backend/Spring/graph-io 소비자 | `supports(GraphCapability.X)` 조회 | capability 존재 여부를 명시적으로 확인하고 enum 전체를 `when`으로 가정하지 않는다. |
| 외부 Kotlin 소비자 | public enum/API | `when`에는 `else`를 두고 unknown을 unsupported로 처리한다. `ordinal`은 저장하지 않는다. |
| configuration·storage·remote peer 이름 입력 | forward-compatible parse | `GraphCapability.fromSerializedNameOrNull(name)`을 사용하고 `null`을 unknown으로 처리한다. |

기존 capability ordinal 0–8(`MERGE`부터 `NATIVE_ALGORITHM`)은 유지하고
`BOUNDED_CHUNKED_READ`/`BOUNDED_CHUNKED_EXPORT`를 9–10으로 마지막에 둔다.
serialization name은 enum `name`을 그대로 보존하며, 새 값이 구버전 binary에
도달하는 경우 parser는 예외 대신 `null`을 반환한다. 이 helper는 새 capability의
실행을 제공하지 않으며, unknown을 무시·관찰·fail-closed 중 소비자 정책으로
결정해야 한다.

## 7-Tier 판정

| Tier | 판정 | 근거 |
|---|---|---|
| T1 컴파일·ABI | PASS | enum 기존 순서와 이름을 유지하고 additive `fromSerializedNameOrNull`을 추가했다. graph-core test/ABI surface 확인을 통과했다. |
| T2 기능·계약 | PASS | known name은 enum으로 복원하고 future/대소문자 불일치 이름은 `null`을 반환한다. 기존 capability 집합과 backend 동작은 변경하지 않는다. |
| T3 실패·취소 | PASS | unknown capability를 `Enum.valueOf` 예외로 처리하지 않는 fail-closed parser를 제공한다. 실행 lifecycle/취소는 선행 #548 계약을 그대로 소비한다. |
| T4 보안·노출 | PASS | 외부 입력을 enum 전체 switch로 실행하지 않으며, 알 수 없는 이름을 unsupported로 격리한다. secret·URI·dependency 변경은 없다. |
| T5 수명주기·동시성 | PASS/WATCH | parser는 순수 lookup이고 shared mutable state가 없다. capability 실제 실행·remote cursor lifecycle은 변경하지 않는다. |
| T6 ecosystem·패턴 | PASS | 기존 `GraphCapabilities`, `bluetape4k-assertions`, Kotlin enum/README conventions를 재사용했고 새 dependency를 추가하지 않았다. |
| T7 문서·인계 | PASS/WATCH | graph-core EN/KO README, CHANGELOG, WIP, lesson, 본 7-Tier review에 inventory·release policy를 기록했다. hosted exact-head review와 전체 train merge는 최종 gate다. |

## 검증 증거

- `:bluetape4k-graph-core:test --tests io.bluetape4k.graph.repository.GraphCapabilitiesTest`
  — 6 tests PASS.
- `:bluetape4k-graph-core:test --rerun-tasks` — 352 tests PASS.
- `:bluetape4k-graph-core:detekt --rerun-tasks` — PASS.
- `javap`/Kotlin compile — 기존 enum 상수 순서와 additive
  `GraphCapability.Companion.fromSerializedNameOrNull(String)` public surface를
  확인했다.
- TinkerGraph → AGE → Neo4j → Memgraph → FalkorDB capability conformance를
  `--rerun-tasks`로 순차 실행했고 각각 5/4/4/4/4 tests, 0 skipped,
  0 failure로 통과했다.
- `git diff --check` — PASS.

## DoD Status

- [x] graph-core 및 주요 소비 모듈의 `GraphCapability` exhaustive 소비 inventory
  와 분류를 기록했다.
- [x] enum ordinal/name 보존, `else` unknown policy, 이름 기반 null-safe parser를
  public 문서와 release guidance에 기록했다.
- [x] `bluetape4k.assertions` 기반 ordinal/name/unknown 회귀 테스트를 추가했다.
- [x] graph-core EN/KO README, CHANGELOG, WIP, lesson과 7-Tier review를 갱신했다.
- [ ] PR hosted exact-head CI와 독립 review — PR 생성 후 진행한다.
- [ ] 전체 stacked train merge — 마지막 사용자 승인 단계에서만 진행한다.

최종 판정: **PASS/WATCH**. 현재 branch의 local implementation·문서·회귀 검증을
완료한 뒤 PR을 #570 exact head 위에 생성한다. 병합은 전체 train의 마지막 승인 전까지
보류한다.
