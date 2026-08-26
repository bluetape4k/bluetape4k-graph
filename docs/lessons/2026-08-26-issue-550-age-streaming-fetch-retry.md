# #550 AGE streaming fetch size·retry 장애 주입 lesson

## 상황

#535에서 AGE suspend direct `Flow`를 JDBC cursor로 바꾸고 positive
`PreparedStatement.fetchSize`, fallback `100`, `maxAttempts=1`을 구현했지만 정상
Testcontainers 조회만으로는 driver가 실제 fetch size를 받았는지와 late JDBC 오류가
재시도를 일으키지 않는지를 관찰할 수 없었다.

## 결정

production surface를 테스트용으로 열지 않고 `DataSource`에서 실제 connection을
감싼다. `prepareStatement`가 반환하는 실제 `PreparedStatement`를 위임 proxy로
감싸 `setFetchSize` 값을 기록하고, `ResultSet.next()`의 두 번째 호출에서만
`SQLException`을 주입한다. 이 경계는 실제 AGE SQL·transaction·ResultSet close를
그대로 실행하면서 fault만 결정적으로 삽입한다.

## 검증된 계약

- `DatabaseConfig.defaultFetchSize = 8`은 prepared statement에 `8`로 전달된다.
- 비양수 설정은 positive fallback `100`으로 전달된다.
- 두 행 중 첫 행을 collector에 공개한 뒤 late `SQLException`이 발생해도
  `maxAttempts=1`로 streaming statement 실행은 한 번이고 prefix는 한 건뿐이다.
- 원인 예외 chain에 주입한 `SQLException`이 남아 있어 JDBC 장애임을 확인할 수 있다.
- 각 proxy fixture의 Exposed `Database`는 `finally`에서 `TransactionManager.closeAndUnregister`로
  제거해 global registry 오염을 남기지 않는다.

## 남은 범위

`executeQuery()` 또는 `ResultSet.next()`가 driver 내부에서 정지할 때 coroutine 취소를
`Statement.cancel()`로 연결하는 prompt cancellation은 #552의 별도 계약이다.
transaction-scoped nested `Flow` materialization은 #551에서 다룬다. 따라서 본
검증은 fetch/retry 관찰 증거를 추가하지만 driver stall까지 보장한다고 확대 해석하지
않는다.

## 재현 명령

```bash
./gradlew :bluetape4k-graph-age:test \\
  --tests 'io.bluetape4k.graph.age.AgeGraphSuspendOperationsTest' \\
  --no-build-cache --no-daemon --console=plain
./gradlew :bluetape4k-graph-age:test --no-build-cache --no-daemon --console=plain
./gradlew :bluetape4k-graph-age:detekt \\
  :bluetape4k-graph-age:compileKotlin \\
  :bluetape4k-graph-age:compileTestKotlin \\
  --no-build-cache --no-daemon --console=plain
```

현재 검증 결과는 targeted `32/32`, graph-age 전체 `194/194`, compile/Detekt/diff
check PASS다. PR 생성 후 exact hosted head와 함께 다시 대조한다.
