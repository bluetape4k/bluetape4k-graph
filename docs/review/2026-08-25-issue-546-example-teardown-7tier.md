# #546 예제 suspend teardown 7-Tier review

## 범위와 기준

- Issue: [#546](https://github.com/bluetape4k/bluetape4k-graph/issues/546)
- Branch: `fix/issue-546-example-teardown`
- Base: PR #566 (`fix/issue-545-spring-boot-contract`) exact head
  `96496c612e26b8a3eafa55c401bf8073703972bd`
- Module scope: `code-graph-examples`, `fraud-detection-examples`,
  `iam-access-graph-examples`, `knowledge-graph-examples`,
  `linkedin-graph-examples`, `observability-graph-examples`,
  `recommendation-examples`
- Review 범위: suspend `@AfterAll` backend teardown의 cancellation 전파,
  graph drop 실패 로깅, owned driver 수명과 sync-only teardown 비변경

## 7-Tier 판정

| Tier | 판정 | 근거 |
|---|---|---|
| T1 컴파일·API | PASS | 7개 예제 모듈의 `test`가 각각 test compile과 실행을 완료했다. 공개 production API 변경은 없다. |
| T2 동작 계약 | PASS | 각 suspend teardown은 `runSuspendIO { ops.dropGraph(graphName) }`를 한 번 실행하고, 정상 cleanup·일반 실패 로깅·driver close 순서를 유지한다. |
| T3 실패·예외 | PASS | `CancellationException`을 먼저 잡아 즉시 재전파하고, 그 밖의 `Exception`만 `log.warn`으로 기록한다. 취소를 일반 teardown 실패로 삼키지 않는다. |
| T4 보안·노출 | PASS | 로그에는 기존 graph 이름과 예외만 남기며 credential·URI·driver 내부 상태를 추가로 노출하지 않는다. |
| T5 수명주기·동시성 | PASS | `finally`에서 owned driver를 닫아 drop 성공·일반 실패·취소의 모든 경로에서 close를 시도한다. `runSuspendIO` 외부의 sync-only `runCatching`은 근거 없이 바꾸지 않았다. |
| T6 ecosystem·패턴 | PASS/WATCH | `kotlinx.coroutines.CancellationException`, 기존 `runSuspendIO`, `io.bluetape4k.logging.warn`을 재사용하고 dependency를 추가하지 않았다. examples는 non-published 모듈이라 module-level Detekt task가 적용되지 않으며, publishable graph 모듈 root `detekt`는 통과했다. |
| T7 문서·운영 | PASS/WATCH | `CHANGELOG.md`, `WIP.md`, 이 review/lesson을 갱신한다. hosted exact-head CI와 최종 merge는 전체 stacked train의 마지막 승인 단계로 남긴다. |

## 검증 증거

- Static scope: 변경 파일 7개, suspend `runCatching { runSuspendIO … }` 잔존 0개.
- Sequential module tests: `:code-graph-examples:test` 60 passing,
  `:fraud-detection-examples:test` 44 passing,
  `:iam-access-graph-examples:test` 45 passing,
  `:knowledge-graph-examples:test` 44 passing,
  `:linkedin-graph-examples:test` 50 passing,
  `:observability-graph-examples:test` 34 passing,
  `:recommendation-examples:test` 34 passing.
- Aggregate test XML: 311 tests, failures 0, errors 0, skipped 0.
- Static: `./gradlew detekt --no-daemon --console=plain` PASS for all
  publishable graph modules. Examples Detekt is N/A by the repository's
  non-published-module convention.
- Hygiene: `git diff --check` PASS.

## DoD Status

- [x] 7개 suspend backend teardown에서 `CancellationException`을 재전파한다.
- [x] 일반 graph drop 실패 로깅을 보존하고 driver close를 `finally`에서 보장한다.
- [x] sync-only teardown은 변경하지 않는다.
- [x] 7개 예제 테스트를 graph DB lifecycle 충돌 없이 순차 실행했다.
- [x] Korean WIP/CHANGELOG와 7-Tier review/lesson을 기록한다.
- [ ] hosted exact-head CI/review와 최종 train merge — 최종 승인 단계에서 수행한다.

최종 판정: **PASS/WATCH**. 구현과 로컬 검증은 완료됐으며, PR 생성 후 hosted
검증을 대기한다. 병합은 전체 train의 마지막 사용자 승인 전까지 보류한다.
