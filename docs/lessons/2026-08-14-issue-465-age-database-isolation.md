# #465 AGE facade의 명시적 Database 격리

## 결정

AGE sync/suspend facade가 생성 시 `org.jetbrains.exposed.v1.jdbc.Database`를
소유하고 모든 transaction·merge·schema 경로에서 같은 인스턴스를 사용하게
한다. Ktor managed helper와 Spring Boot auto-configuration도 해당 Database를
facade에 전달한다.

기존 graphName-only 생성자와 Ktor helper는 호환 기간 동안 deprecated로
유지하지만 전역 `TransactionManager.defaultDatabase`에 의존하는 새 코드는
추가하지 않는다.

## 이유

여러 DataSource 또는 ApplicationContext가 동시에 동작할 때 전역 Exposed
기본 Database를 조회하면 한 facade의 쓰기가 다른 context로 향할 수 있다.
명시적 Database 전달은 sync/suspend와 managed integration의 transaction
경계를 같은 소유자로 고정한다.

## 검증

- AGE 격리 회귀를 포함한 `AgeGraphOperationsTest`: 27 tests PASS.
- Ktor `BackendGraphPluginRuntimeTest`, `GraphPluginTest`: 14 tests PASS.
- Spring Boot `GraphAgeAutoConfigurationTest`: 5 tests PASS.
- AGE/Ktor/Spring Boot detekt: PASS.
- `git diff --check`: PASS.
