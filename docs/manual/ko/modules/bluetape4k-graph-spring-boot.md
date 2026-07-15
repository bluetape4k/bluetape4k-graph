# bluetape4k-graph-spring-boot

## 선택 기준

이 Spring Boot 4 모듈은 graph property를 bind하고 graph별 auto-configuration을 불러온다. graph 하나만 선택한다. classpath, property, missing-bean 조건이 맞을 때만 bean을 만들며, 사용자가 제공한 bean이 있으면 물러난다. 시작점은 [GraphAutoConfiguration.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/spring-boot/graph-spring-boot/src/main/kotlin/io/bluetape4k/graph/spring/boot/autoconfigure/GraphAutoConfiguration.kt)다.

## 의존성과 실행

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

예상 결과는 조건이 맞을 때 Driver, `GraphOperations`, `GraphSuspendOperations`, virtual thread facade가 등록되는 것이다.

## 조건과 종료 책임

[GraphNeo4jAutoConfiguration.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/spring-boot/graph-spring-boot/src/main/kotlin/io/bluetape4k/graph/spring/boot/autoconfigure/GraphNeo4jAutoConfiguration.kt) 같은 설정은 classpath, property, missing-bean 조건을 함께 본다. 사용자가 Driver나 graph facade를 제공하면 중복 bean을 만들지 않아야 한다. container가 만든 Driver는 `destroyMethod="close"`로 닫힌다. 사용자 bean은 그 bean의 종료 계약을 따른다.

## 확인과 문제 해결

```bash
./gradlew :bluetape4k-graph-spring-boot:test --tests '*GraphNeo4jAutoConfigurationTest'
```

예상 결과는 property bind, bean 생성, 사용자 bean을 만났을 때 물러남, context 종료가 검증되는 것이다. bean이 없으면 condition report에서 graph 선택 property, 필요한 class, 기존 graph/Driver bean, graph별 property 순서로 본다. context 시작 성공만으로 실제 서버 연결을 증명할 수는 없다.

## 관련 문서와 하지 않는 일

[Spring Boot 연동](../frameworks/spring-boot.md), [구현 선택](../backends/selection-guide.md), [테스트](../guides/testing.md)를 참고한다. 이 모듈은 graph 서버를 설치하거나 여러 graph를 자동으로 조정하지 않으며 사용자 bean을 덮어쓰지 않는다.
