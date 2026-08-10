# Issue #312 구현 코드 리뷰

검토 대상은 `feat/issue-312-native-loader-spi`의 현재 구현 snapshot이다.
기준 base는 `04f56d0`이며, 설계·계획 readiness review는
`docs/superpowers/reviews/2026-08-10-issue-312-plan-review.md`에 별도로
기록되어 있다. 이 리뷰는 실제 backend adapter, URI/file dereference,
staging I/O, Testcontainers를 포함하지 않는다.

## 판정

| 영역 | 판정 | 근거 |
|---|---|---|
| API/컴파일 | PASS | `GraphNativeBulkLoader<R, V>`와 `GraphNativeBulkLoadExecution<V>`가 raw source와 validated artifact를 분리하고, Kotlin/JVM compile이 성공했다. public model은 `Serializable`과 private `serialVersionUID`를 갖는다. |
| 수명/동시성 | PASS | `ReentrantLock/Condition`으로 source·loader lifecycle을 직렬화하고, validated source와 loader 모두 close owner를 한 번만 선택한다. grace 만료 뒤 load/take 종료 경로가 deferred cleanup을 인계하며 실제 cleanup completion 전 `CLOSED`를 publish하지 않는다. |
| 보안/비밀값 | PASS | operation label은 고정 `native-bulk-load`이며 request/execution 문자열은 source를 노출하지 않는다. 예외는 fixed code로 redaction되고 listener 원본만 caller primary로 보존된다. diagnostic은 bounded ID와 backend/phase/outcome/code만 노출한다. |
| 계약 검증 | PASS | capability·request를 함께 받는 report factory와 progress verifier가 count, transaction, failure-detail, cancellation, phase/token-boundary, terminal coupling을 검증한다. postcondition 위반은 `CONTRACT_VIOLATION`으로 분리된다. |
| 종료/관찰성 | PASS | cancellation hook exactly-once, bounded cancellation/close/observer call, interrupt flag 복원, diagnostic single-inflight 및 pending timeout retry를 확인했다. |

## P2 및 후속 경계

- URI origin의 default port, IDNA, IP literal canonicalization은 실제 adapter가
  dereference 직전에 처리해야 한다. 이 core SPI는 URI를 열지 않는다.
- 실제 Neo4j/Memgraph/AGE/FalkorDB adapter, file staging, DNS rebinding 방어,
  Testcontainers lifecycle은 후속 backend 이슈에서 검증한다.

## 검증 증거

- RED: nativebulk 타입이 없는 상태에서 targeted `compileTestKotlin` 실패.
- GREEN targeted: `GraphNativeBulkLoadModelsTest`와
  `GraphNativeBulkLoaderTest` **13개 통과**.
- 전체 core: `:bluetape4k-graph-io-core:test` **95개 통과**.
- 컴파일: `:bluetape4k-graph-io-core:compileKotlin` 성공.
- 정적 확인: production nativebulk 코드에 `!!`, `synchronized`,
  `wait/notifyAll` 사용 없음.
- 형식 확인: `git diff --check` 성공.

## 최종 verdict

- P0: 0
- P1: 0
- P2: 2 (adapter-owned URI canonicalization 및 실제 backend/Testcontainers)
- P3: 0
- 구현 코드 리뷰: **PASS — 로컬 이슈 범위에서 완료**
