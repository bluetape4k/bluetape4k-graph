# Issue #534 graph-age SQL 식별자 7-Tier 리뷰

## 범위와 기준

`AgeSql`의 공개 SQL builder와 sync/suspend AGE 호출 경계를 검토했다. 기준은
[#534](https://github.com/bluetape4k/bluetape4k-graph/issues/534)의 구조적 식별자
검증·binding 수용 기준이며, graph-age source와 SQL/통합 테스트를 exact stacked
head에서 대조했다.

## 7-Tier 결과

| Tier | 판정 | 근거 |
| --- | --- | --- |
| T1 계약·범위 | PASS | graph name, vertex/edge label, 결과 column name/type을 SQL 구조에 넣기 전에 검증하고, property 값은 기존 serializer 경계를 유지한다. |
| T2 API·ABI | PASS | `AgeSql` 공개 함수 시그니처와 String 기반 Exposed 실행 계약을 유지하고 invalid 입력만 `IllegalArgumentException`으로 조기 거부한다. |
| T3 구현·패턴 | PASS | Bluetape `requireSafeIdentifier`를 공통 사용하고, 고정 `$$` 대신 query 본문과 겹치지 않는 dollar-quote tag를 선택한다. |
| T4 테스트 | PASS | `bluetape4k.assertions.assertFailsWith`로 graph/label/column 경계를 검증하고, dollar-quote 충돌·empty columns 회귀를 추가했다. sync/suspend AGE 통합 경계도 악성 identifier를 확인한다. |
| T5 backend 영향 | PASS / WATCH | AGE SQL, sync operations, suspend operations targeted suite가 `BUILD SUCCESSFUL`이다. 실제 hosted AGE image gate는 stacked PR의 별도 receipt에서 확인한다. |
| T6 문서·사용성 | PASS | `AgeSql` KDoc에 구조적 identifier와 Cypher 본문 경계를 설명하고 기존 호출 예제를 유지한다. |
| T7 검증·운영 | PASS / WATCH | compile/test와 diff 검증을 통과했다. prepared statement parameter map 전환은 이 slice 밖의 후속 설계다. |

## 잔여 위험과 후속 범위

- 공개 `cypher` 함수는 여전히 String SQL을 반환하므로 property 값 binding과
  prepared statement API는 별도 변경으로 분리한다.
- graph-age container matrix와 release artifact 검증은 hosted workflow receipt에서
  exact head를 확인한다.
- `maxDepth` 같은 graph-core option 경계는 [#540](https://github.com/bluetape4k/bluetape4k-graph/issues/540)에서 별도 추적한다.

## 최종 판정

**PASS / WATCH** — #534의 구조적 식별자 조기 검증과 SQL delimiter 경계 수용 기준을
충족한다. merge는 전체 stacked train의 최종 승인 단계까지 보류한다.
