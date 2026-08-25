# #554 GraphImportJobStateStore durable contract TCK lesson

## 상황

#538과 #553에서 `GraphImportJobStateStore.update`의 JVM-local 원자 경계와
report payload 보존을 정렬했지만, durable CAS/transaction adapter가 같은
invariant를 재사용할 공통 TCK가 없었다. 특히 contention에서 stale result를
저장하지 않는지와 mismatch 전에 `save`가 호출되지 않는지를 단순 상태 비교만으로
놓칠 수 있었다.

## 결정

`graph-io-core`에 `java-test-fixtures` variant를 추가하고, 기본 contract와
retry contract를 분리해 제공한다. 기본 TCK는 최신 report·최초 생성·mismatch·
transform failure를 검증하고, retry TCK는 test-only harness로 intervening
report를 주입해 최신 입력 재평가와 save invocation 경계를 관찰한다. harness는
production durable API가 아니며, 실제 adapter가 각자의 transaction/CAS 충돌
주입 장치를 연결해야 한다.

## 검증

- `InMemoryGraphImportJobStateStoreContractTest`는 기본 4개와 retry 2개 테스트를
  공유 fixture에서 실행
- `io.bluetape4k.assertions.assertFailsWith`와 의도 matcher를 사용하고 금지된
  JUnit/Kotlin assertion을 추가하지 않음
- KDoc과 graph-io-core README EN/KO에 pure/retry-safe transform, `jobId`
  invariant, durable override 경계를 기록
- README에는 composite build용 `testFixtures(project(...))`와 published
  module용 `testFixtures("io.github.bluetape4k.graph:...")` 소비 표기를 모두
  기록해 TCK handoff가 build 형태에 종속되지 않게 했다.
- implementation commit `98dddf35`는 production API 변경 없이 TCK와 KDoc만
  추가하며, 문서 receipt는 후속 commit에서 분리한다.
- targeted TCK 6/6, graph-io-core full 149/149, Detekt, 금지 assertion scan,
  `git diff --check` 통과
- hosted exact-head CI·Examples와 PR lifecycle receipt는 PR 생성 후 최신 head로
  다시 기록

## 남은 가드

1. durable adapter가 추가되면 `testFixtures(project(":bluetape4k-graph-io-core"))`
   의존성과 adapter-specific retry harness를 연결한다.
2. 실제 multi-instance/multi-process contention과 transaction rollback은 이
   test-only in-memory harness의 증거로 대체하지 않는다.
3. 기본 store 전체 monitor의 head-of-line blocking 개선은 #555에서 별도 성능
   근거를 만든다.
4. PR #578 생성 후 exact head hosted CI·Examples와 review read-back을 확인하고,
   전체 train 마지막 승인 전에는 merge하지 않는다.
