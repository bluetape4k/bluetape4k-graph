# Issue #466 — Neo4j Driver bean identity와 qualifier 보존

## Context

Neo4j와 Memgraph가 같은 `org.neo4j.driver.Driver` 타입을 사용하면서도
backend별 driver bean identity를 일관되게 보존해야 했다. Neo4j auto-configuration은
`@ConditionalOnMissingBean(Driver::class)`과 무자격 `Driver` 주입을 사용해,
unrelated 또는 여러 `Driver` bean이 있을 때 잘못된 back-off와 모호한 주입을
허용하고 있었다.

## Decision or Finding

Neo4j도 Memgraph와 같은 이름 기반 계약을 사용한다. 기본 bean 이름은
`neo4jDriver`이며, `GraphOperations`, `GraphSuspendOperations`, health indicator의
모든 `Driver` 주입 지점에 `@Qualifier("neo4jDriver")`를 적용한다. 따라서 명시적인
`neo4jDriver`만 재사용하고 다른 이름의 `Driver`는 자동 선택하지 않는다.

## Outcome

unrelated 단일 driver, unrelated 복수 driver, 명시적인 `neo4jDriver`와 unrelated
driver 공존 시나리오를 `ApplicationContextRunner`로 고정했다. operations와 suspend
operations의 실제 driver identity 및 health indicator 호출 대상도 명시 bean으로
검증한다. README 양쪽 locale에 backend driver 이름 규칙을 문서화했다.

## Verification

```text
./gradlew :bluetape4k-graph-spring-boot:test --tests '*GraphNeo4jAutoConfigurationTest' --no-daemon --no-configuration-cache
8 tests, 0 failures — BUILD SUCCESSFUL
```

RED 단계에서는 기존 타입 기준 조건과 무자격 주입으로 3개 시나리오가
`NoSuchBeanDefinitionException` 또는 `NoUniqueBeanDefinitionException`으로 실패했고,
이후 이름 조건과 qualifier 적용 후 GREEN으로 전환됐다.

## Future Guidance

Neo4j-compatible backend가 새로 추가되거나 auto-configuration의 `Driver` 지점을
변경할 때는 타입 기준 `@ConditionalOnMissingBean`을 재사용하지 말고 backend 전용
bean 이름과 qualifier를 함께 검토한다. unrelated/multiple/explicit 세 context
시나리오를 유지해 bean identity drift를 조기에 탐지한다.
