# Transaction DSL 설계 Spec

- **Issue**: #13 — 트랜잭션 DSL `ops.transaction { }` 블록 지원
- **작성일**: 2026-05-09
- **작성자**: bluetape4k-graph 팀
- **상태**: Step 2-R 로컬 리뷰 반영 완료
- **연관 모듈**: `graph-core`, `graph-neo4j`, `graph-memgraph`, `graph-age`, `graph-tinkerpop`

---

## 1. 문제 정의

현재 `GraphOperations` / `GraphSuspendOperations`는 정점·간선 CRUD와 탐색 API를 제공하지만, 여러 쓰기 연산을 하나의 원자적 작업으로 묶는 공통 API가 없다. 호출자는 다음과 같은 작업을 직접 처리해야 한다.

- 정점 2개와 간선 1개를 만드는 작업 중간 실패 시 수동 보상 삭제
- 백엔드별 트랜잭션 API 차이 처리
- 테스트에서 커밋/롤백 동작을 백엔드마다 별도 검증

목표는 다음 DSL을 제공하는 것이다.

```kotlin
val edge = ops.transaction {
    val alice = createVertex("Person", mapOf("name" to "Alice"))
    val bob = createVertex("Person", mapOf("name" to "Bob"))
    createEdge(alice.id, bob.id, "KNOWS")
}
```

예외가 발생하면 블록 안의 쓰기는 롤백되어야 하며, 성공하면 커밋되어야 한다.

---

## 2. 조사 요약

### 2.1 현재 repository 패턴

- `GraphOperations`는 `GraphSession`, `GraphVertexRepository`, `GraphEdgeRepository`, `GraphGenericRepository` 합성 인터페이스다.
- `GraphSuspendOperations`는 suspend 대응 인터페이스 합성이다.
- 기존 구현체는 이미 외부 driver/database 소유권을 닫지 않는 패턴을 따른다.
- Neo4j/Memgraph sync 구현은 `Session.run(...)` 헬퍼를 통해 쿼리별 세션을 열고 닫는다.
- AGE sync 구현은 각 메서드가 Exposed `transaction { ... }`에 위임한다.
- TinkerGraph는 in-memory 구현이며, 현재 graph/traversal handle이 구현체 내부 private 필드다.
- FalkorDB는 jfalkordb 0.7.0 동기 API 기반이고, 명확한 graph-level rollback 트랜잭션 표면이 현재 코드에 없다.

### 2.2 공식 문서 근거

- Neo4j Java Driver는 explicit transaction으로 `session.beginTransaction()`, `tx.run()`, `tx.commit()`, `tx.rollback()` 패턴을 제공한다. 커밋하지 않고 종료되면 rollback 된다.
- Exposed는 `transaction { ... }` 블록과 `rollback()` / `commit()`를 제공하며, 기본 nested transaction은 부모와 리소스를 공유한다.
- Exposed coroutine 경로는 `newSuspendedTransaction`과 같은 suspend transaction API를 제공한다.
- TinkerPop/TinkerGraph는 provider별 transaction support가 다르므로, 지원 여부를 구현체가 명확히 결정해야 한다.

---

## 3. 범위

### 3.1 이번 PR 포함

1. `graph-core`에 트랜잭션 계약 추가
   - `GraphTransactionScope`
   - `GraphTransactionalOperations`
   - `GraphSuspendTransactionScope`
   - `GraphSuspendTransactionalOperations`
   - `GraphOperations.transaction { }` 확장 함수
   - `GraphSuspendOperations.suspendTransaction { }` 확장 함수
2. 지원하지 않는 구현체는 명시적으로 실패
   - 기존 `GraphOperations` 구현체를 강제로 깨지 않기 위해 확장 함수는 capability interface 구현 여부를 확인한다.
   - 미지원 구현체는 `UnsupportedOperationException`을 던진다.
3. Sync 백엔드 1차 구현
   - Neo4j: explicit transaction
   - Memgraph: Neo4j Java Driver explicit transaction
   - AGE: Exposed `transaction { }`
   - TinkerGraph: in-memory snapshot rollback
4. 테스트
   - `graph-core`: 미지원 구현체 실패 테스트
   - `graph-tinkerpop`: commit / rollback / return value 테스트
   - compile 대상: `graph-core`, `graph-tinkerpop`, `graph-neo4j`, `graph-memgraph`, `graph-age`

### 3.2 이번 PR 제외

- FalkorDB 트랜잭션 구현: jfalkordb/Redis graph-level rollback semantics를 별도로 확인한 뒤 후속 처리한다.
- Suspend 백엔드 전체 구현: API 표면은 추가하되, 실제 구현은 sync contract 안정화 후 별도 PR에서 백엔드별로 확장한다.
- `#32`, `#33`, `#34` 구현: transaction DSL 안정화 후 진행한다.
- distributed transaction, retry, isolation level, read/write transaction mode.

---

## 4. 설계

### 4.1 Core contract

```kotlin
interface GraphTransactionScope :
    GraphVertexRepository,
    GraphEdgeRepository

interface GraphTransactionalOperations {
    fun <T> transaction(block: GraphTransactionScope.() -> T): T
}

fun <T> GraphOperations.transaction(block: GraphTransactionScope.() -> T): T =
    when (this) {
        is GraphTransactionalOperations -> transaction(block)
        else -> throw UnsupportedOperationException(...)
    }
```

트랜잭션 scope는 처음에는 정점·간선 CRUD로 제한한다. `createGraph` / `dropGraph` 같은 session lifecycle과 graph-wide 탐색은 일반 트랜잭션 DSL에서 제외한다. 이는 graph schema/lifecycle 명령이 백엔드별 auto-commit 또는 DDL semantics를 가질 수 있기 때문이다.

### 4.2 Neo4j / Memgraph

- 구현체가 `GraphTransactionalOperations`를 구현한다.
- `session().use { session -> session.beginTransaction().use { tx -> ... } }` 패턴을 사용한다.
- scope 내부 CRUD는 `tx.run(...)`만 사용한다.
- block 성공 시 `tx.commit()`, 예외 시 `tx.rollback()` 후 원 예외 rethrow.
- label/property key는 기존 구현과 같은 검증을 적용한다.

### 4.3 AGE

- 구현체가 `GraphTransactionalOperations`를 구현한다.
- `transaction { block(delegateScope) }`로 감싼다.
- 기존 메서드의 nested `transaction { ... }` 패턴은 Exposed 기본 동작상 동일 transaction 리소스를 공유한다.
- rollback은 block에서 발생한 예외를 Exposed transaction 밖으로 전파해 처리한다.

### 4.4 TinkerGraph

TinkerGraph는 테스트와 임베디드 용도의 in-memory backend다. provider transaction API에 의존하지 않고 다음 방식으로 rollback을 보장한다.

1. block 실행 전 vertices/edges snapshot 생성
2. block 성공 시 snapshot 폐기
3. block 예외 시 현재 그래프를 비우고 snapshot을 복원

복원은 기존 vertex/edge id를 보존하도록 `T.id`를 사용한다. 실패 시 원 예외를 유지하면서 복원 실패를 suppressed exception으로 붙인다.

### 4.5 Suspend API

Suspend contract는 core에 추가하지만 이번 PR에서 백엔드 구현은 제공하지 않는다. 확장 함수는 `GraphSuspendTransactionalOperations`를 구현하지 않은 경우 명시적으로 실패한다. 이렇게 하면 public API shape를 먼저 확보하면서, 각 suspend backend의 transaction strategy를 후속 PR에서 안전하게 설계할 수 있다.

---

## 5. 설계 대안

| 대안 | 장점 | 단점 | 결정 |
|------|------|------|------|
| `GraphOperations`에 직접 `transaction` 멤버 추가 | discoverability 좋음 | 모든 구현체와 테스트 fake를 즉시 깨뜨림 | 거절 |
| 확장 함수 + capability interface | 기존 구현체 source compatibility 유지 | 미지원 구현체는 runtime failure | 채택 |
| scope를 `GraphOperations` 전체로 노출 | 기존 API 모두 사용 가능 | graph lifecycle/탐색/DDL까지 rollback 의미가 불명확 | 거절 |
| scope를 Vertex/Edge CRUD로 제한 | 원자적 write use case에 집중 | traversal을 block 안에서 직접 호출 불가 | 채택 |
| TinkerGraph tx API 의존 | provider API와 일관 | support 여부가 provider-dependent | 거절 |
| TinkerGraph snapshot rollback | 테스트 가능하고 semantics 명확 | 대형 in-memory graph에서는 비용 큼 | 채택 |

---

## 6. 리스크와 완화

| 리스크 | 영향 | 완화 |
|--------|------|------|
| scope CRUD가 기존 CRUD와 다르게 동작 | transaction 안팎 결과 불일치 | 기존 query/mapping 코드를 최대한 재사용하고 backend 테스트 추가 |
| TinkerGraph snapshot rollback 비용 | 대형 그래프에서 메모리/시간 증가 | KDoc에 테스트/임베디드 용도 명시, 운영용 backend가 아님 |
| AGE nested transaction 오해 | rollback이 의도와 다를 수 있음 | Exposed transaction 공식 동작을 근거로 테스트 추가 가능하게 plan에 포함 |
| suspend API만 있고 구현 없음 | 사용자가 런타임 실패를 만날 수 있음 | KDoc에 capability 기반 미지원 실패를 명시 |
| FalkorDB 제외로 issue 전체 완료 오해 | 후속 작업 누락 | DoD와 changelog에 first slice임을 명시 |

---

## 7. 수용 기준

- `GraphOperations.transaction { }` 확장 함수가 제공된다.
- 미지원 구현체는 명확한 `UnsupportedOperationException`을 던진다.
- TinkerGraph transaction DSL은 성공 시 commit, 예외 시 rollback 된다.
- Neo4j/Memgraph/AGE sync 구현체가 `GraphTransactionalOperations`를 구현하고 컴파일된다.
- public API에는 한국어 KDoc과 Kotlin 예제가 포함된다.
- targeted compile/test가 통과한다.

---

## 8. DoD

| 항목 | 완료 기준 |
|------|-----------|
| Core API | `graph-core` transaction contract + tests |
| Backend slice | Neo4j/Memgraph/AGE/TinkerGraph sync capability 구현 |
| Tests | `:graph-core:test`, `:graph-tinkerpop:test` targeted 통과 |
| Compile | `:graph-neo4j:compileKotlin`, `:graph-memgraph:compileKotlin`, `:graph-age:compileKotlin` 통과 |
| KDoc | 신규 public API 한국어 KDoc 포함 |
| Docs | README 변경 필요성 검토, 필요 시 한/영 동기화 |

---

## 9. Step 2-R 로컬 리뷰 결과

`bluetape4k-design`의 `references/step-2r-spec-review.md` 파일은 현재 설치된 skill 디렉터리에 없어서, 동일 관점의 로컬 리뷰로 대체했다.

| 관점 | 결과 |
|------|------|
| Developer | `GraphOperations` 직접 변경은 기존 fake/test implementation을 깨뜨리므로 capability interface 방식을 유지한다. |
| Security | label/property identifier 검증은 기존 backend와 동일하게 유지해야 한다. 사용자 입력 label을 그대로 query string에 넣는 기존 구조의 위험을 확대하지 않는다. |
| Ops/SRE | 미지원 backend가 조용히 auto-commit fallback을 쓰면 데이터 일관성을 해칠 수 있으므로 명시적 실패를 유지한다. |
| User/caller | suspend API는 추가되지만 실제 backend 구현이 없으므로 KDoc과 오류 메시지에 first-slice 범위를 명확히 적는다. |
| Critic integration | FalkorDB와 suspend 구현 제외가 issue 전체 완료로 오해되지 않도록 DoD와 final report에 후속 작업으로 기록한다. |

High finding은 없었다. Medium finding인 "지원 범위 오해 가능성"은 범위/리스크/DoD에 반영했다.
