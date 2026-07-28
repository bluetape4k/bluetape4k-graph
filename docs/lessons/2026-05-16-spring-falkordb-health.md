# 2026-05-16 — Spring Boot FalkorDB Health Indicator: Test Gap and README Omission

## 맥락

Issue #125: `GraphFalkorDBAutoConfiguration.HealthConfig` was implemented in PR #106 but had no test and was absent from README documentation.

## 핵심 결정

### `@ConditionalOnClass`는 테스트 classpath에 대상 클래스가 있어야 한다

`HealthConfig` is guarded by `@ConditionalOnClass(name = ["org.springframework.boot.health.contributor.HealthIndicator"])`. In `ApplicationContextRunner` tests, the class must actually be on the test classpath or the conditional skips the nested `@Configuration` entirely and no `HealthIndicator` bean is registered.

Fix: add `testImplementation("org.springframework.boot:spring-boot-health")` to `build.gradle.kts`. Without this the test passes vacuously (no exception from `getBean`, just no bean), so the test becomes a false negative.

### `Backend` enum 값은 모든 문서에서 동기화해야 한다

The "Common properties" table listed `backend` allowed values as `tinkergraph | neo4j | memgraph | age` in both README.md and README.ko.md. `falkordb` was missing even though the FalkorDB auto-configuration section was correctly added in five other locations.

Pattern: whenever a new backend is added, check the Common properties table explicitly — it is a catch-all that is easy to miss when adding backend-specific sections.

## 검증

```
./gradlew :graph-spring-boot:test --tests "*.GraphFalkorDBAutoConfigurationTest"
5 tests, 0 failures — BUILD SUCCESSFUL
```

Codex review: CRITICAL=0, HIGH=0 (21 total tests passing).

## 향후 지침

- When adding or verifying a `@ConditionalOnClass`-guarded Spring Boot bean in `ApplicationContextRunner` tests, verify the guarded class is in `testImplementation` scope.
- README "Common properties" tables listing enum values are the most likely place to miss a new backend — add a checklist item for it in the PR template.
