# #553 workflow report payload 보존 lesson

## 상황

#538에서 workflow state transition을 store `update` 원자 경계로 옮겼지만,
전이 결과를 새 `GraphImportWorkflowReport`로 만들면서 기존 `sources`, `elapsed`,
`checkpoint`가 기본값으로 사라지는 문제가 남아 있었다.

## 결정

허용 전이를 `update` transform 안에서 검증한 뒤 기존 report는
`copy(state = state)`로 갱신하고, report가 없을 때만 새 report를 만든다. 이
방식은 #538의 동시성 경계를 재사용하면서 payload의 불변성과 저장 후 재조회를
함께 보장한다.

## 검증

- TDD RED에서 기존 구현의 빈 `sources` 반환을 재현
- 수정 후 workflow targeted 4/4, graph-io-core 전체 143/143, Detekt 통과
- `shouldBeEmpty`·`shouldBeNull`·`shouldBeEqualTo`로 기본값과 보존값의 의도를 분리
- 금지 assertion scan과 `git diff --check` 통과
- #576 exact head `112703d4752fa5dad6f25cef5a53328cd6712bfa` 위 implementation
  commit `88cc9676dfbbd48c7b700c51854f7380ee6fe07a`와 문서 receipt `09ceee92`에서 확인

## 남은 가드

1. PR 생성 후 exact-head hosted CI·Examples와 review read-back을 확인한다.
2. durable store CAS/TCK와 jobId invariant는 #554에서 검증한다.
3. store 전체 monitor의 병렬성은 #555에서 별도 근거를 만든다.
4. merge는 전체 stacked train의 마지막 일괄 승인 단계까지 수행하지 않는다.
