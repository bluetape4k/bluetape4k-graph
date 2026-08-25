# #554 GraphImportJobStateStore durable contract TCK 설계

## 범위와 stacked 기준

- 이슈: [#554](https://github.com/bluetape4k/bluetape4k-graph/issues/554)
- 대상 모듈: `graph-io-core`
- 유형: Type B / contract test·refactoring
- 선행 PR: [#577](https://github.com/bluetape4k/bluetape4k-graph/pull/577)
- 선행 exact head: `c3ac327a23730b977c5ffc03d730b0fc8abecdcd`
- 작업 branch: `fix/issue-554-state-store-tck-stacked`
- implementation commit: `98dddf35`
- 범위: 기본 in-memory store와 향후 durable CAS/transaction adapter가 공유할
  `GraphImportJobStateStore` contract TCK와 retry 경계 문서화

## 문제

`GraphImportJobStateStore.update`는 같은 JVM store의 기본 원자 경계를 이미
제공하지만, durable 구현이 반드시 지켜야 할 CAS contention·retry·`jobId`
invariant·failure atomicity를 공통 테스트로 재사용할 방법이 없었다. adapter가
각자 유사한 테스트를 작성하면 stale transition 저장, retry 중 오래된 report
재사용, mismatch 저장 같은 회귀가 서로 다른 기준으로 남는다.

## 결정

1. `graph-io-core`에 Gradle `java-test-fixtures` variant를 추가한다.
2. `AbstractGraphImportJobStateStoreContractTest`는 다음 기본 계약을 고정한다.
   - 최신 report를 입력으로 변환하고 저장 후 재조회한다.
   - job이 없을 때 최초 report를 생성한다.
   - 요청 `jobId`와 다른 결과는 저장하지 않고 실패한다.
   - transform 실패 시 기존 report를 보존한다.
3. `AbstractGraphImportJobStateStoreRetryContractTest`와
   test-only `GraphImportJobStateStoreRetryHarness`는 CAS/transaction adapter가
   contention retry에서 최신 report를 다시 읽고, retry 결과만 저장하며,
   mismatch 전에 `save`를 호출하지 않는지 검증한다. 실제 adapter는
   충돌 주입을 위한 adapter 전용 harness만 제공한다.
4. `GraphImportJobStateStore.update` KDoc와 graph-io-core README EN/KO에
   pure/retry-safe transform, 결과 `jobId` invariant, durable override의
   native transaction/CAS 경계를 명시한다.

이 slice는 durable database 구현이나 multi-process runtime을 추가하지 않는다.
그 구현은 TCK를 소비하는 후속 lane에서 별도 검증하며, 기본 store 전체 monitor의
범위 최적화는 [#555](https://github.com/bluetape4k/bluetape4k-graph/issues/555)
범위로 남긴다.

## 수용 기준 매핑

| 기준 | 구현·검증 |
| --- | --- |
| contention/retry 재현 | test fixture retry harness가 intervening report를 주입하고 transform을 2회 평가 |
| `jobId` mismatch 저장 금지 | 결과 mismatch 예외와 save invocation 불변을 함께 검증 |
| failure atomicity | transform 실패 후 기존 report와 저장 결과 보존 검증 |
| retry-safe 규칙 문서화 | `GraphImportJobStateStore.update` KDoc, README EN/KO |
| graph-io-core 품질 | targeted/full test, Detekt, 금지 assertion scan, `git diff --check` |

## SPW gate

- SPW-01: live #554 요구사항과 #577 exact base 확인
- SPW-02: `java-test-fixtures`, immutable report, Bluetape assertions 패턴 대조
- SPW-03: production API는 `update` KDoc만 보강하고 durable implementation은 추가하지 않음
- SPW-04: TCK targeted → graph-io-core full/Detekt → static scan 순서
- SPW-05: README EN/KO, 7-Tier review, lesson, WIP, PR receipt를 exact head에 연결

## 범위 밖

- 실제 PostgreSQL/Redis/graph backend durable state store 구현
- multi-process integration test와 분산 lock/transaction 운영 검증
- store 전체 monitor의 job별 lock 최적화
- suspend workflow counterpart 추가
