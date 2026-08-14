# #472 backend 의존성 노출 정리

## 결정

Memgraph는 `graph-neo4j` 구현 모듈 대신 Neo4j driver API를 직접 사용하고,
Caffeine cache를 직접 `implementation` dependency로 선언한다. AGE와 Neo4j의
Caffeine도 public API가 아닌 cache 구현 세부이므로 `implementation`으로
내린다.

## 이유

구현 모듈을 `api`로 노출하면 소비자의 classpath와 BOM surface가 불필요하게
커지고 backend 구현 결합이 생긴다. Caffeine 타입은 public facade 계약에
필요하지 않으므로 transitive API가 될 이유가 없다.

## 검증 계획

- 세 backend compile/test/detekt와 BOM dependency resolution을 실행한다.
- Memgraph source가 `graph-neo4j` 패키지를 참조하지 않고 driver API만 쓰는지
  확인한다.
- published POM에서 구현 모듈과 Caffeine의 불필요한 API 노출이 사라지는지
  확인한다.
