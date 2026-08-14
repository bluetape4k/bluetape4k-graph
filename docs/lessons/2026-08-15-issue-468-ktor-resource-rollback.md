# #468 Ktor managed backend resource rollback

## 결정

Managed AGE, Neo4j, Memgraph, FalkorDB helper는 resource를 생성하는 즉시
rollback close action을 등록하고, 중복 backend 구성이나 install 실패 시
역순으로 닫는다. `GraphPluginState.close()`와 개별 close action은
idempotent이며 close 실패가 다음 action 실행을 막지 않는다.

## 이유

기존 흐름은 driver/DataSource를 먼저 만들고 `configure()`에서 중복 backend를
검사했다. 검사 실패 시 새 resource가 plugin state에 등록되지 않아 누수가
발생할 수 있었다. 소유권과 rollback 순서를 생성 경계에서 기록하면 부분
생성·설치 실패와 application stop을 같은 계약으로 처리할 수 있다.

## 검증

- Ktor `GraphPluginTest`와 `BackendGraphPluginRuntimeTest`: 16 tests PASS.
- 중복 구성 rollback, 수동 close/application stop 반복 호출, close 실패 후
  후속 action 실행을 직접 검증했다.
- Ktor detekt: PASS.
- `git diff --check`: PASS.
