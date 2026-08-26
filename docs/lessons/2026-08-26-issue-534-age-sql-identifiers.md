# Issue #534 graph-age SQL 식별자 lesson

## 결정

SQL 구조에 보간되는 graph name, label, column name/type은
`requireSafeIdentifier`로 한 경계에서 검증했다. 호출자가 제공하는 Cypher는
값 literal이 아니라 dollar-quote 본문으로 유지하되, 본문에 없는 tag를 선택해
고정 delimiter 충돌을 막았다. property 값은 기존 `AgePropertySerializer` 계약을
그대로 사용한다.

## 결과와 검증

- unsafe graph/label/edge/column 입력과 empty result columns를
  `io.bluetape4k.assertions.assertFailsWith`로 고정했다.
- sync/suspend AGE identifier 회귀와 `AgeSql` dollar-quote 충돌 회귀가 통과했다.
- graph-age targeted test, compile, `git diff --check`가 통과했다.

## 다음 방어선

prepared statement 기반 value binding은 공개 String builder와 API 범위가 달라 별도
변경으로 다룬다. 실제 AGE container와 hosted workflow는 PR exact-head receipt로
계속 확인한다.
