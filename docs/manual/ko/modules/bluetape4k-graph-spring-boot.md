# bluetape4k-graph-spring-boot


실행 방식: **release test fixture 연계형**이다. `ApplicationContextRunner`가 Spring context와 test property를 제공한다. 아래 테스트가 조건부 활성화, 사용자 bean이 있을 때 물러남, 접근, container 소유 자원 종료를 검증한다.

## 실행 전 준비

이 Spring Boot 4 모듈은 그래프 property를 bind하고 그래프별 auto-configuration을 불러온다. 그래프 하나만 선택한다. classpath, property, missing-bean 조건이 맞을 때만 bean을 만들며, 사용자가 제공한 bean이 있으면 물러난다. 시작점은 [GraphAutoConfiguration.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/spring-boot/graph-spring-boot/src/main/kotlin/io/bluetape4k/graph/spring/boot/autoconfigure/GraphAutoConfiguration.kt)다.

## 실행

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-spring-boot")
    implementation("io.github.bluetape4k:bluetape4k-graph-neo4j")
}
```

```yaml
bluetape4k:
  graph:
    backend: neo4j
    neo4j:
      uri: bolt://localhost:7687
      username: neo4j
      password: ${NEO4J_PASSWORD:}
      database: neo4j
```

```kotlin
@Service
class PeopleService(private val graph: GraphSuspendOperations) {
    suspend fun count(): Long = graph.countVertices("Person")
}
```

## 기대 결과

예상 결과는 조건이 맞을 때 Driver, `GraphOperations`, `GraphSuspendOperations`, virtual thread 진입점가 등록되는 것이다.

## 조건과 종료 책임

[GraphNeo4jAutoConfiguration.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/spring-boot/graph-spring-boot/src/main/kotlin/io/bluetape4k/graph/spring/boot/autoconfigure/GraphNeo4jAutoConfiguration.kt) 같은 설정은 classpath, property, missing-bean 조건을 함께 본다. 사용자가 Driver나 그래프 진입점를 제공하면 중복 bean을 만들지 않아야 한다. container가 만든 Driver는 `destroyMethod="close"`로 닫힌다. 사용자 bean은 그 bean의 종료 계약을 따른다.

## 운영 점검

- condition 평가 결과와 선택한 그래프를 기록한다.
- context 시작 뒤 pool 상태를 확인한다.
- 배포 전에 사용자 bean이 있을 때 물러나는지 검증한다.
- container가 만든 자원이 context와 함께 닫히는지 확인한다.

## 실패와 복구

증상: 필요한 bean이 없거나 중복된다. condition report를 읽고 그래프 선택 property와 classpath를 먼저 고친다. 사용자 bean이 있을 때 물러나는 동작을 유지하고 context를 닫은 뒤 정확한 auto-configuration 테스트를 다시 실행한다.

```bash
./gradlew :bluetape4k-graph-spring-boot:test --tests '*GraphNeo4jAutoConfigurationTest'
```

예상 결과는 property bind, bean 생성, 사용자 bean을 만났을 때 물러남, context 종료가 검증되는 것이다. bean이 없으면 condition report에서 그래프 선택 property, 필요한 class, 기존 그래프/Driver bean, 그래프별 property 순서로 본다. context 시작 성공만으로 실제 서버 연결을 증명할 수는 없다.

## 완전한 release 예제

고정된 [GraphNeo4jAutoConfigurationTest](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/spring-boot/graph-spring-boot/src/test/kotlin/io/bluetape4k/graph/spring/boot/autoconfigure/GraphNeo4jAutoConfigurationTest.kt)가 fixture 값을 정의한 완전한 실행 예제다. 다음 명령으로 확인한다.

```bash
./gradlew :bluetape4k-graph-spring-boot:test --tests '*GraphNeo4jAutoConfigurationTest'
```

예상 결과는 fixture가 시작되고 검증이 통과하며 소유 자원이 문서에 적은 순서로 닫히는 것이다.

## 하지 않는 일과 관련 문서

[Spring Boot 연동](../frameworks/spring-boot.md), [구현 선택](../backends/selection-guide.md), [테스트](../guides/testing.md)를 참고한다. 이 모듈은 그래프 서버를 설치하거나 여러 그래프를 자동으로 조정하지 않으며 사용자 bean을 덮어쓰지 않는다.
