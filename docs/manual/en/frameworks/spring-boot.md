# Spring Boot integration

![Framework integration flow](../../assets/frameworks/framework-integration-flow.png)

`GraphAutoConfiguration` binds `GraphProperties` and orders backend-specific configurations; it does not create a graph bean by itself. Backend configurations are registered separately and activate from classpath, properties, and missing-bean conditions. Root source: [`GraphAutoConfiguration.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/spring-boot/graph-spring-boot/src/main/kotlin/io/bluetape4k/graph/spring/boot/autoconfigure/GraphAutoConfiguration.kt).

Use the ecosystem BOM and an unversioned `bluetape4k-graph-spring-boot` coordinate. Configure exactly one intended backend, then inspect the condition report if beans are absent or ambiguous. Backend examples include [`GraphNeo4jAutoConfiguration.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/spring-boot/graph-spring-boot/src/main/kotlin/io/bluetape4k/graph/spring/boot/autoconfigure/GraphNeo4jAutoConfiguration.kt) and [`GraphAgeAutoConfiguration.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/spring-boot/graph-spring-boot/src/main/kotlin/io/bluetape4k/graph/spring/boot/autoconfigure/GraphAgeAutoConfiguration.kt).

The Spring container owns beans it creates; injected caller-owned resources keep their declared ownership. Verify property binding, backoff when user beans exist, backend selection, and shutdown with focused tests such as [`GraphNeo4jAutoConfigurationTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/spring-boot/graph-spring-boot/src/test/kotlin/io/bluetape4k/graph/spring/boot/autoconfigure/GraphNeo4jAutoConfigurationTest.kt).

Observe condition evaluation, selected backend, pool health, and shutdown ordering. A green context-start test is necessary but does not prove connectivity to the production server.

## Configure and inject the facade

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

Expected beans are `Driver`, `GraphOperations`, `GraphSuspendOperations`, and, by default, `GraphVirtualThreadOperations`. Supply your own `Driver` bean to test backoff; the auto-configuration must reuse it instead of creating another.

## Verify conditions and shutdown

```bash
./gradlew :bluetape4k-graph-spring-boot:test --tests '*GraphNeo4jAutoConfigurationTest'
```

If the service bean is missing, run with `--debug` and read the condition evaluation report in this order: backend property, required Driver/backend classes, existing `GraphOperations` bean, then backend properties. The auto-created Driver bean has `destroyMethod="close"`; prove shutdown by closing the context and checking connectivity/close test evidence. A user-supplied Driver follows that bean's own destroy contract, not an inferred graph-library ownership rule.
