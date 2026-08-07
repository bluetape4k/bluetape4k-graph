# 실패와 취소

재시도하기 전에 실패를 입력 검증, 미지원 capability, 연결, 백엔드 질의·스키마, 트랜잭션, codec·입력, 인증·보안, 취소로 나눈다.

`transaction {}`와 `suspendTransaction {}`는 블록이 정상 종료된 뒤 commit하고, 예외가 나면 rollback한다. 지원하지 않는 구현은 자동 커밋으로 바꾸지 않고 실패한다. 계약 소스: [`GraphTransactionScope.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphTransactionScope.kt), [`GraphSuspendTransactionScope.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSuspendTransactionScope.kt).

코루틴 취소는 백엔드 경계까지 전달되고 나중에 commit이 일어나지 않아야 한다. 일부 suspend transaction 구현은 반환할 `Flow`를 commit 전에 모두 소비한다. 선택한 백엔드에서 확인해야 하며, [`Neo4jGraphSuspendOperationsTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphSuspendOperationsTest.kt)가 한 예다.

기본 batch는 중간 실패 전에 처리한 항목을 남길 수 있고 importer도 부분 진행을 보고할 수 있다. 재시도 전 실제 개수를 확인하고 검증된 idempotency/merge 키를 쓴다. OkIO의 잘못된 associated data, 잘림, 압축 해제 한도, atomic write 정리는 DB 재시도 대상이 아닌 명시적 실패다. [`GraphIoOkioPathsTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph-io/okio/src/test/kotlin/io/bluetape4k/graph/io/okio/GraphIoOkioPathsTest.kt)를 참고한다.

## 실패를 재현하고 분류한다

```kotlin
val before = ops.countVertices("Person")
val failure = runCatching {
    ops.transaction {
        createVertex("Person", mapOf("requestId" to "r-42"))
        error("injected")
    }
}.exceptionOrNull()
check(failure != null && ops.countVertices("Person") == before)
```

코루틴은 `suspendTransaction` 안에서 job을 취소한다. 취소가 호출자에게 전달되고 저장 개수가 그대로여야 한다. 일반 batch/import에는 같은 원자성을 기대하지 말고 report phase와 실제 개수를 본다. 백엔드가 일시적이라고 분류한 연결·timeout만 검증된 idempotency 키로 재시도한다. 입력 검증, 미지원 capability, 인증, 손상된 암호문, 크기 한도 실패는 입력이나 설정을 고쳐야 한다.
