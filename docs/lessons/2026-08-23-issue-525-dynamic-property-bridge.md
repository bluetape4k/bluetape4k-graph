# Issue #525 — Spring DynamicPropertyRegistry bridge 적용 경계

## Context

공용 `PropertyExportingServer`는 컨테이너 endpoint를
`testcontainers.{namespace}.{key}` 형식으로 제공하지만, 그래프 Spring Boot
통합 테스트는 기존 `bluetape4k.graph.*` 설정 이름을 사용하고 있었다. 이 차이를
테스트마다 직접 `registry.add`로 복제하면 lazy evaluation, 누락 key 오류,
중복 등록, system property 비변경 계약이 backend별로 drift할 수 있다.

## Decision or Finding

- `bluetape4k-testcontainers-spring`을 `graph-spring-boot`의 `testImplementation`으로만
  사용한다. SDK-neutral Testcontainers core와 production graph API에는 Spring 의존성을
  추가하지 않는다.
- 공용 `registerDynamicProperties`가 generic key를 등록하고, graph test helper가
  기존 `bluetape4k.graph.*` key를 lazy alias로 추가한다. 운영 property 이름은 바꾸지
  않는다.
- 현재 live `@SpringBootTest`인 FalkorDB 테스트에만 bridge를 적용한다. Neo4j/Memgraph의
  `ApplicationContextRunner`와 AGE의 explicit `DataSource`/Hikari 테스트는
  `DynamicPropertyRegistry`를 노출하지 않으므로 기존 명시적 wiring을 유지한다.
- 새 선택 모듈 alias를 사용하기 위해 중앙 dependencies catalog ref를
  `df64293753a9491b337852a158f89d4a93a1734a`로 고정한다.

## Outcome

FalkorDB live Spring Boot 테스트가 공용 property contract와 기존 graph property
alias를 같은 lazy supplier로 사용하고, 네 backend의 mapping/applicability 경계가
Docker 없이 계약 테스트로 고정된다. graph name은 테스트 소유 값으로 별도 등록된다.

## Verification

- RED: helper와 선택 모듈을 추가하기 전 계약 테스트가 unresolved bridge/mapping으로
  컴파일 실패했다.
- GREEN: `GraphTestcontainersDynamicPropertySourceTest` 6개 통과.
- `:bluetape4k-graph-spring-boot:test`: 50 passing, 1 pending. pending은
  `BLUETAPE4K_GRAPH_SPRING_FALKORDB_INTEGRATION` 환경 변수로 보호된 기존 live test다.
- `:bluetape4k-graph-spring-boot:detekt`,
  `:bluetape4k-graph-spring-boot:dokkaGenerateModuleHtml`, 이미지 계약 validator,
  `actionlint .github/workflows/ci.yml`, `git diff --check` 통과.

## Future Guidance

새 backend에 live `@SpringBootTest`가 추가될 때만 mapping을 확장하고, 먼저 공용
`PropertyExportingServer` namespace/key를 확인한다. production property 이름을
bridge에 맞춰 바꾸거나, registry가 없는 `ApplicationContextRunner`에 억지로
DynamicPropertySource를 도입하지 않는다.
