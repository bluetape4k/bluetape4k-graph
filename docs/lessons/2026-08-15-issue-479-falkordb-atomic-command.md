# Issue #479: FalkorDB atomic Cypher command

## 결정

Redis `MULTI` 결과가 `EXEC` 전 지연되는 FalkorDB 특성 때문에 기존
`GraphTransactionScope`를 가장하지 않고, 하나의 parameterized Cypher statement를
한 번에 전송하는 `FalkorDBAtomicCypherCommand` 계약을 추가했다. `RETURN`으로 생성 ID를
명시적으로 매핑하고, sync 및 `Dispatchers.IO` 기반 suspend 실행을 제공한다.

## 안전 경계

command에는 세미콜론을 허용하지 않아 다중 statement와 script injection 경계를
분리한다. 값은 반드시 parameters map으로 전달하며, 이 API는 기존 repository
transaction DSL을 대체하지 않는다. 서버 오류는 원자 command의 실패로 그대로
전파되고, FalkorDB Testcontainers에서 실제 rollback 의미를 확인하는 것은 후속
통합 검증 범위다.
