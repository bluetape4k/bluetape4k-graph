# Transaction DSL 구현 계획

- **Spec**: `docs/superpowers/specs/2026-05-09-transaction-dsl-design.md`
- **Issue**: #13 — 트랜잭션 DSL `ops.transaction { }` 블록 지원
- **작성일**: 2026-05-09
- **브랜치**: `feat/issue-13-transaction-dsl`
- **목표**: source-compatible transaction DSL contract와 sync backend 1차 구현

---

## 작업 원칙

- `GraphOperations` / `GraphSuspendOperations` 직접 멤버 추가로 기존 구현체를 깨뜨리지 않는다.
- 지원하지 않는 구현체는 auto-commit fallback을 쓰지 않고 명시적으로 실패한다.
- public API는 한국어 KDoc과 짧은 Kotlin 예제를 포함한다.
- backend transaction scope 내부 쿼리는 기존 mapper와 identifier 검증 규칙을 재사용한다.
- 이번 PR은 first slice다. FalkorDB와 suspend backend 구현은 후속 작업으로 남긴다.

---

## 태스크 목록

### T1. Core transaction contract 추가

- **complexity**: high
- **파일**
  - 신규: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphTransactionScope.kt`
  - 신규: `graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSuspendTransactionScope.kt`
  - 신규: `graph/graph-core/src/test/kotlin/io/bluetape4k/graph/repository/GraphTransactionExtensionsTest.kt`
- **내용**
  - `GraphTransactionScope : GraphVertexRepository, GraphEdgeRepository`
  - `GraphTransactionalOperations`
  - `GraphOperations.transaction { }`
  - `GraphSuspendTransactionScope : GraphSuspendVertexRepository, GraphSuspendEdgeRepository`
  - `GraphSuspendTransactionalOperations`
  - `GraphSuspendOperations.suspendTransaction { }`
  - 미지원 구현체는 `UnsupportedOperationException`
- **검증**
  - `./gradlew :graph-core:test --tests "io.bluetape4k.graph.repository.GraphTransactionExtensionsTest" --no-daemon`
- **Docs 영향**
  - 신규 public API KDoc 필요
  - README는 backend 구현 후 예제 반영 여부 결정

### T2. TinkerGraph sync transaction 구현

- **complexity**: high
- **파일**
  - 수정: `graph/graph-tinkerpop/src/main/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphOperations.kt`
  - 신규: `graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphTransactionTest.kt`
- **내용**
  - `GraphTransactionalOperations` 구현
  - block 전 snapshot 생성
  - 성공 시 commit semantics
  - 예외 시 graph clear + snapshot restore
  - commit, rollback, return value 테스트
- **검증**
  - `./gradlew :graph-tinkerpop:test --tests "io.bluetape4k.graph.tinkerpop.TinkerGraphTransactionTest" --no-daemon`
- **Docs 영향**
  - README/README.ko에 짧은 DSL 예제 추가

### T3. Neo4j sync transaction 구현

- **complexity**: high
- **파일**
  - 수정: `graph/graph-neo4j/src/main/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphOperations.kt`
- **내용**
  - `GraphTransactionalOperations` 구현
  - private `Neo4jGraphTransactionScope`
  - scope CRUD는 `org.neo4j.driver.Transaction.run(...)` 사용
  - commit/rollback/final close 처리
- **검증**
  - `./gradlew :graph-neo4j:compileKotlin --no-daemon`
- **Docs 영향**
  - README/README.ko에 transaction DSL 예제 추가

### T4. Memgraph sync transaction 구현

- **complexity**: high
- **파일**
  - 수정: `graph/graph-memgraph/src/main/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphOperations.kt`
- **내용**
  - Neo4j와 같은 Driver transaction 패턴
  - Memgraph id query semantics 유지 (`id(n) = toInteger($id)`)
- **검증**
  - `./gradlew :graph-memgraph:compileKotlin --no-daemon`
- **Docs 영향**
  - README/README.ko에 transaction DSL 예제 추가 또는 Neo4j와 동일 패턴 안내

### T5. AGE sync transaction 구현

- **complexity**: medium
- **파일**
  - 수정: `graph/graph-age/src/main/kotlin/io/bluetape4k/graph/age/AgeGraphOperations.kt`
- **내용**
  - `GraphTransactionalOperations` 구현
  - Exposed `transaction { block(scope) }`
  - scope는 현재 `AgeGraphOperations`에 delegate
- **검증**
  - `./gradlew :graph-age:compileKotlin --no-daemon`
- **Docs 영향**
  - README/README.ko에 transaction DSL 예제 추가

### T6. 문서와 changelog 정리

- **complexity**: low
- **파일**
  - 수정: `graph/graph-core/README.md`
  - 수정: `graph/graph-core/README.ko.md`
  - 필요 시 backend README 한/영
  - 수정: `CHANGELOG.md`
  - 신규/수정: `docs/superpowers/index/2026-05.md`, `docs/superpowers/INDEX.md`
- **내용**
  - transaction DSL first slice 범위 명시
  - FalkorDB/suspend backend follow-up 명시
- **검증**
  - 문서 링크와 코드 snippet 수동 확인

### T7. 통합 검증과 리뷰

- **complexity**: medium
- **파일**
  - 변경 없음
- **검증**
  - `./gradlew :graph-core:test :graph-tinkerpop:test --no-daemon`
  - `./gradlew :graph-neo4j:compileKotlin :graph-memgraph:compileKotlin :graph-age:compileKotlin --no-daemon`
  - Step 6-R six-tier local review

---

## Step 3-R 로컬 계획 리뷰

`bluetape4k-design`의 plan review reference 파일은 현재 설치본에 없으므로 로컬 동등 리뷰로 대체한다.

| 관점 | 결과 |
|------|------|
| Implementer | T2를 먼저 구현해 non-Docker 테스트로 contract를 고정한 뒤 Driver backend compile을 진행한다. |
| Test engineer | Docker backend transaction integration은 이번 PR에서 제외하되, compile과 TinkerGraph rollback 테스트는 필수로 둔다. |
| Architect | capability interface 방식은 source compatibility를 지키며 backend별 semantics를 명시할 수 있다. |
| Delivery | first slice 범위를 문서/DoD에 명시해 #13 전체 완료로 오해되지 않게 한다. |
| Critic | FalkorDB/suspend 제외는 중간 위험이므로 final report에 후속 작업으로 남긴다. |

High finding은 없다. Medium finding인 "Docker backend rollback 미검증"은 T7 검증 범위와 final risk로 기록한다.

---

## Step 3-P 위험 예측

| 위험 | 대응 |
|------|------|
| TinkerGraph snapshot 복원 중 id 보존 실패 | `T.id` 사용 후 rollback 테스트에서 기존 vertex 재조회 확인 |
| Neo4j/Memgraph scope CRUD 중 query parameter 이름 실수 | 기존 구현 query를 복사하되 tx helper로만 실행 경로 변경 |
| AGE nested transaction 동작 오해 | 이번 PR은 compile만 보장하고 integration rollback은 후속 Testcontainers 테스트로 남김 |
| public extension 이름 충돌 | `io.bluetape4k.graph.repository.transaction` 패키지 유지, README import 예제 명시 |

---

## 완료 기준

| 항목 | 기준 |
|------|------|
| Contract | Core transaction/suspendTransaction 확장 함수 존재 |
| TinkerGraph | commit/rollback/return value 테스트 통과 |
| Compile | Neo4j/Memgraph/AGE compileKotlin 통과 |
| Docs | README 한/영 또는 범위 문서 갱신 |
| Review | Step 6-R critical/high 0 |
