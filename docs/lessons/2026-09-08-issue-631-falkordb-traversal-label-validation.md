# #631 FalkorDB 순회 label 검증 lesson

## 배경

FalkorDB의 동기·코루틴 `neighbors`, `shortestPath`, `allPaths`는
`edgeLabel`을 Cypher 관계 패턴에 직접 삽입한다. 기존 구현은 blank 여부만
검사하고 label을 그대로 사용했기 때문에, 쿼리 실행 전에 거부해야 하는
안전하지 않은 식별자가 driver 호출까지 전달됐다. Neo4j와 Memgraph 순회
구현이 이미 사용하는 graph-core의 `requireSafeIdentifier`를 같은 경계에
적용할 필요가 있었다.

## 결정

각 순회 메서드는 `options.edgeLabel`을 `requireNotBlank("edgeLabel")` 뒤에
`requireSafeIdentifier("edgeLabel")`로 검증하고, 검증 결과를 Cypher 패턴
생성에 재사용한다. `null`은 기존의 전체 label 순회 의미를 유지하고, blank
label은 기존 계약대로 거부한다. 동기·코루틴 구현은 같은 검증 순서와
식별자 규칙을 공유한다.

중복 sanitizer나 backend 전용 정규식을 추가하지 않는다. Cypher 구조에
삽입되는 식별자는 graph-core 공통 helper의 현재 계약을 재사용해야 한다.

## 결과

동기·코루틴 API에 각각 세 개의 unsafe-label 회귀 테스트를 추가했다. 테스트는
driver를 호출하기 전에 `IllegalArgumentException`을 던지는지 확인한다. 기존의
정상 `KNOWS` 순회 테스트와 `null` label 분기 구현은 유지한다.

## 검증

- RED 실행에서 6개 테스트 모두 query 실행 후 `AssertionError`가 발생했고,
  기대한 `IllegalArgumentException`은 발생하지 않았다.
- RED 명령은 다음과 같다.

  ```bash
  ./gradlew :bluetape4k-graph-falkordb:test \
    --tests "io.bluetape4k.graph.falkordb.FalkorDBTraversalValidationTest" \
    --console=plain
  ```

- 구현 후 실제 FalkorDB Testcontainers를 포함한 전체 모듈 102개 테스트와 detekt를 통과했다.
- 현재 작업 tree에서는 `git diff --check`를 통과했다.

## 재발 방지

Cypher label·property key처럼 쿼리 구조에 삽입되는 값은 backend 메서드가
쿼리를 만들기 전에 graph-core `requireSafeIdentifier`를 호출하고 반환값을
사용한다. 동기·suspend 쌍을 함께 검색해 동일한 검증 범위를 유지한다. unsafe
입력은 driver guard mock으로 조기 거부를 고정하고, 정상 label과 `null`은
기존 backend 테스트로 의미 보존을 확인한다.

## 문서 검증

- **SPW-01: PASS.** 대상은 FalkorDB module 유지보수자이며, 현재 source,
  공통 helper, 기존 backend 패턴, RED 로그를 근거로 범위와 미검증 항목을
  고정했다.
- **SPW-02: PASS.** context, decision/finding, outcome, verification,
  future guidance를 기록했다.
- **SPW-03: PASS.** Korean technical register를 적용하고 `edgeLabel`,
  `requireSafeIdentifier`, `IllegalArgumentException`, 명령과 정규식은
  그대로 보존했다.
- **SPW-04: PASS.** 동기·코루틴 six-path source와 test scope를 대조했으며,
  GREEN 및 Testcontainers 102개 결과를 최신 XML과 대조했다.
- **SPW-05: PASS.** 최종 Markdown을 read-back하고 locale README와 동일한
  validation contract를 설명하는지 확인했다.
