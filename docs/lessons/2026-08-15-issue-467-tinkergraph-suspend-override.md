# Issue #467 — TinkerGraph suspend override 조건 정렬

## 결정

TinkerGraph Spring Boot auto-configuration의 suspend factory는
`TinkerGraphOperations` 빈이 실제로 존재할 때만 활성화한다. 사용자가 다른
`GraphOperations` 구현을 제공하면 sync 기본 빈과 함께 suspend factory도
back off하며, suspend API가 필요할 때는 `GraphSuspendOperations`를 직접
제공한다.

## 이유

`TinkerGraphSuspendOperations`는 TinkerGraph 동기 구현의 transaction snapshot과
공유 gate를 직접 사용한다. 공용 `GraphOperations` 타입으로 factory 인자를
넓히면 임의 구현에 TinkerGraph 전용 동작을 잘못 적용할 수 있다. 구체 타입
조건을 추가하면 custom sync-only context가 시작 실패하지 않고, custom
sync+suspend pair는 `@ConditionalOnMissingBean`으로 그대로 유지된다.

## 검증

- 기본 TinkerGraph sync/suspend/virtual-thread 빈 등록 테스트 통과.
- custom `GraphOperations`만 제공한 context가 startup failure 없이 시작하고
  suspend factory를 생성하지 않는 회귀 테스트 통과.
- custom sync+suspend pair identity와 custom sync-only + `register-suspend=false`
  시나리오 테스트 통과.
- `graph-spring-boot` compile/test/detekt 및 `git diff --check` 통과.
