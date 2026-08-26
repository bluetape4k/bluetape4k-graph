# 이슈 #544 Neo4j weighted-path assertion 7-Tier 리뷰

## 리뷰 범위와 근거

- 대상: `graph/graph-neo4j`의 `Neo4jWeightedPathTest`
- 이슈: [#544](https://github.com/bluetape4k/bluetape4k-graph/issues/544)
- stacked parent: `fix/issue-543-tinkerpop-id-contract` (`91567bb6dd67678fd19083dd29e45c68d4a63689`)
- 변경 파일:
  - `graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jWeightedPathTest.kt`
- 구현 계약: local Kotlin bare `assert` helper를 제거하고
  `io.bluetape4k.assertions.shouldBeInRange`와
  `io.bluetape4k.assertions.assertFailsWith`를 사용한다. `ClosedRange`의
  inclusive semantics와 범위 밖 값의 `AssertionFailedError` 검증을 유지한다.
- 검증 근거:
  - 변경 전 기준선: `Neo4jWeightedPathTest` 6개 테스트 통과
  - RED: pre-fix helper를 임시 복원했을 때 회귀 테스트가
    `Expected AssertionFailedError but got AssertionError`로 실패
  - GREEN: `Neo4jWeightedPathTest` 7개 테스트 통과
  - 모듈 회귀: `bluetape4k-graph-neo4j` 131개 테스트 통과
  - `:bluetape4k-graph-neo4j:compileKotlin :compileTestKotlin` 통과
  - `:bluetape4k-graph-neo4j:detekt --rerun-tasks` 통과
  - `git diff --check` 통과

## 7-Tier 결과

| Tier | 검토 항목 | 근거 | 결과 |
| --- | --- | --- | --- |
| 1 | 빌드·API·ABI | 테스트 파일만 변경했고 production API, ABI, dependency를 변경하지 않았다. `compileKotlin`과 `compileTestKotlin`이 통과했다. | PASS |
| 2 | 동작·계약 | `path.vertices.size shouldBeInRange 2..3`의 닫힌 범위를 유지하고, 범위 밖 값은 `AssertionFailedError`를 발생시킨다. | PASS |
| 3 | 테스트·assertion | 기준선 6개, RED 실패, GREEN 7개, 모듈 131개를 순차 검증했다. `Neo4jWeightedPathTest`에는 bare `assert(`가 남아 있지 않다. | PASS |
| 4 | 동시성·coroutine | production 동시성·coroutine·resource 경계를 변경하지 않았다. Neo4j Testcontainers는 `--no-parallel`로 순차 실행했다. 새 concurrency/cancellation 테스트는 범위 밖이다. | PASS (N/A 경계 포함) |
| 5 | Bluetape4k 패턴·생태계 | 기존 assertions dependency와 동일한 `shouldBeInRange`, `assertFailsWith`를 사용했다. 새 dependency나 ad hoc matcher를 추가하지 않았다. | PASS |
| 6 | 문서·호환성 | 공개 API·README·BOM·module registration을 변경하지 않는다. 이 리뷰에 결정·범위·근거·severity·잔여 위험을 기록했다. | PASS |
| 7 | 운영·CI·릴리스 | Testcontainers module test와 정적 분석은 통과했다. PR exact-head CI·live review·merge·release는 다음 stacked PR 단계에서 검증한다. | PASS (외부 게이트 PENDING) |

## 심각도별 findings

- P0: 없음
- P1: 없음
- P2: 없음
- P3: 없음

## 잔여 위험과 경계

- `shouldBeInRange`의 failure message는 assertions 모듈의 표준 메시지로
  통일된다. 범위 비교는 `ClosedRange.contains`를 사용하므로 양 끝 경계는
  기존과 같이 포함된다.
- assertion-disabled JVM에서도 검증이 유지되는 핵심 근거는 bare `assert`를
  제거하고 `Failures.fail` 기반의 bluetape4k matcher를 호출하는 것이다.
  회귀 테스트는 pre-fix의 `AssertionError`와 표준
  `AssertionFailedError`를 구분한다.
- lesson은 새 failure mode나 recovery/design/운영 지침을 추가하지 않아
  생성하지 않았다. 동일한 재사용 guard는 ancestor의
  `docs/lessons/2026-08-25-issue-543-tinkergraph-id-contract.md`에 이미
  기록되어 있으며, 이번 변경은 그 규칙을 Neo4j weighted-path 테스트에
  적용한 범위다.

## Kotlin Final Checklist

- KT-FIN-01: PASS — 현재 테스트 파일, local helper 호출부, assertions 구현과 기존 사용처를 대조했다.
- KT-FIN-02: N/A — caller validation이나 예외 계약을 제공하는 production 코드가 없다.
- KT-FIN-03: PASS — 새 production `!!`, suspend `runCatching`, cancellation swallow, blocking call, monitor가 없다.
- KT-FIN-04: PASS — `Neo4jServer.Launcher.neo4j` singleton과 `@AfterAll` driver cleanup을 그대로 유지했다.
- KT-FIN-05: N/A — Exposed 경계를 변경하지 않았다.
- KT-FIN-06: PASS — `references/testing.md`를 적용했고 launcher, sequential Testcontainers, assertions 규칙을 확인했다.
- KT-FIN-07: PASS — 범위 밖 값에 대한 `AssertionFailedError` regression test가 matcher 동작을 직접 증명한다.
- KT-FIN-08: N/A — 공개 API·KDoc·README·diagram을 변경하지 않았다.
- KT-FIN-09: PASS — import 정리, compile, detekt가 통과했다.
- KT-FIN-10: PASS — fresh targeted/module test와 `git diff --check`가 통과했다.
- KT-FIN-11: PASS — 변경 범위는 테스트 1개와 이 리뷰 문서로 제한되고 P0/P1 finding이 없다.

## Writer DoD (SPW)

- SPW-01: PASS — 독자, 이슈, 대상 파일, stacked parent, 검증 명령과 외부 게이트 경계를 고정했다.
- SPW-02: PASS — review 계약에 필요한 범위, 근거, 7개 tier, severity, disposition, gap, verdict를 포함했다.
- SPW-03: PASS — 한국어 기술 문체를 적용하고 API·명령어·식별자·URL·수치·불확실성을 보존했다. KO-01~KO-06을 확인했다.
- SPW-04: PASS — 현재 소스, assertions 구현, issue acceptance, 테스트 출력과 문서 주장을 대조했다.
- SPW-05: PASS — 최종 Markdown을 read-back하고 표·헤딩·링크·코드 토큰을 확인했다. KO-07 terminology audit 결과는 별도 검증으로 기록한다.

## Verdict

7-Tier와 Kotlin checklist에서 blocker 및 P0/P1 finding이 없어 #544의 로컬
구현·검증 단계는 PASS다. #543 PR을 base로 하는 stacked PR 생성은 다음
단계이며, exact-head CI·live review·merge 승인은 아직 PENDING이다.
