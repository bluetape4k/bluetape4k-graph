# bluetape4k-graph-spring-boot

## Auto-configuration boundary

This Spring Boot 4 module binds graph properties and imports backend-specific auto-configurations. Choose exactly one backend. It creates beans only when classpath, properties, and missing-bean conditions match; user beans make it back off. Root: [GraphAutoConfiguration.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/spring-boot/graph-spring-boot/src/main/kotlin/io/bluetape4k/graph/spring/boot/autoconfigure/GraphAutoConfiguration.kt).

## Dependency, configuration, and access

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

Expected: the context provides Driver, `GraphOperations`, `GraphSuspendOperations`, and the virtual-thread facade when all conditions match.

## Conditions, lifetime, and backoff

Backend configurations such as [GraphNeo4jAutoConfiguration.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/spring-boot/graph-spring-boot/src/main/kotlin/io/bluetape4k/graph/spring/boot/autoconfigure/GraphNeo4jAutoConfiguration.kt) use conditional classes/properties and missing-bean checks. A user-provided Driver or graph facade must prevent the corresponding duplicate bean. The container closes beans it creates; the auto-created Driver has `destroyMethod="close"`. User beans follow their own destroy contract.

## Failures and operations

When beans are absent, read the condition report in this order: backend property, required classes, existing graph/driver beans, then backend properties. When multiple candidates appear, verify that only one backend is enabled. Observe condition decisions, selected backend, pool health, context startup, and shutdown order.

```bash
./gradlew :bluetape4k-graph-spring-boot:test --tests '*GraphNeo4jAutoConfigurationTest'
```

Expected: properties bind, beans appear, user beans cause backoff, and context close releases auto-created resources. Context startup alone does not prove production connectivity.

## Related pages and non-goals

See [Spring Boot integration](../frameworks/spring-boot.md), [backend selection](../backends/selection-guide.md), and [testing](../guides/testing.md). This module does not provision servers, select multiple backends safely, override user beans, or infer ownership for externally supplied infrastructure.
