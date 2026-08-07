# bluetape4k-graph-spring-boot

## Before you run

This Spring Boot 4 module binds graph properties and imports backend-specific auto-configurations. Choose exactly one backend. It creates beans only when classpath, properties, and missing-bean conditions match; user beans make it back off. Root: [GraphAutoConfiguration.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/spring-boot/graph-spring-boot/src/main/kotlin/io/bluetape4k/graph/spring/boot/autoconfigure/GraphAutoConfiguration.kt).


Execution mode: **release-fixture linked**. `ApplicationContextRunner` supplies the Spring context and test properties; the linked test verifies conditional activation, user-bean backoff, access, and container-owned shutdown.

## Run

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

## Expected result

Expected: the context provides Driver, `GraphOperations`, `GraphSuspendOperations`, and the virtual-thread facade when all conditions match.

## Conditions, lifetime, and backoff

Backend configurations such as [GraphNeo4jAutoConfiguration.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/spring-boot/graph-spring-boot/src/main/kotlin/io/bluetape4k/graph/spring/boot/autoconfigure/GraphNeo4jAutoConfiguration.kt) use conditional classes/properties and missing-bean checks. A user-provided Driver or graph facade must prevent the corresponding duplicate bean. The container closes beans it creates; the auto-created Driver has `destroyMethod="close"`. User beans follow their own destroy contract.

## Operations checklist

- Record the condition evaluation and selected graph.
- Watch pool health after context startup.
- Verify user-bean backoff before deployment.
- Confirm container-created resources close with the context.

## Failure and recovery

Symptom: expected beans are absent or duplicated. Read the condition report, fix backend property/classpath first, preserve user-bean backoff, close the context, and rerun the exact auto-configuration test.

When beans are absent, read the condition report in this order: backend property, required classes, existing graph/driver beans, then backend properties. When multiple candidates appear, verify that only one backend is enabled. Observe condition decisions, selected backend, pool health, context startup, and shutdown order.

```bash
./gradlew :bluetape4k-graph-spring-boot:test --tests '*GraphNeo4jAutoConfigurationTest'
```

Expected: properties bind, beans appear, user beans cause backoff, and context close releases auto-created resources. Context startup alone does not prove production connectivity.

## Complete release example

The pinned [GraphNeo4jAutoConfigurationTest](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/spring-boot/graph-spring-boot/src/test/kotlin/io/bluetape4k/graph/spring/boot/autoconfigure/GraphNeo4jAutoConfigurationTest.kt) defines the fixture values and is the complete executable release example. Run:

```bash
./gradlew :bluetape4k-graph-spring-boot:test --tests '*GraphNeo4jAutoConfigurationTest'
```

Expected: the fixture starts, assertions pass, and owned resources close in the documented order.

## Non-goals and related guides

See [Spring Boot integration](../frameworks/spring-boot.md), [backend selection](../backends/selection-guide.md), and [testing](../guides/testing.md). This module does not provision servers, select multiple backends safely, override user beans, or infer ownership for externally supplied infrastructure.
