# Ktor integration

![Framework integration flow](../../assets/frameworks/framework-integration-flow.png)

Install `GraphPlugin` once in a Ktor application and select a backend or supply sync/suspend operations. Installation fails if configuration resolves no backend. The resulting `GraphPluginState` is stored in application attributes: [`GraphPlugin.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/ktor/graph-ktor/src/main/kotlin/io/bluetape4k/graph/ktor/GraphPlugin.kt), [`GraphPluginConfig.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/ktor/graph-ktor/src/main/kotlin/io/bluetape4k/graph/ktor/GraphPluginConfig.kt).

```kotlin
fun Application.module() {
    install(GraphPlugin) { tinkerGraph() }
    routing { /* graphOperations() / graphSuspendOperations() */ }
}
```

Lifetime is application-scoped, not request-scoped. On `ApplicationStopped`, only close actions registered by configuration run. Caller-owned drivers and data sources remain caller-owned. Verify startup, attribute lookup, and exactly-once close behavior with [`GraphPluginTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/ktor/graph-ktor/src/test/kotlin/io/bluetape4k/graph/ktor/GraphPluginTest.kt) and [`BackendGraphPluginRuntimeTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/ktor/graph-ktor/src/test/kotlin/io/bluetape4k/graph/ktor/BackendGraphPluginRuntimeTest.kt).

Diagnose install-time errors before routing errors. In production, observe application stop events, driver pool metrics, request cancellation, and whether request handlers use the API matching their coroutine/blocking model.

## Dependency, managed configuration, and route access

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-ktor")
    implementation("io.github.bluetape4k:bluetape4k-graph-neo4j")
}

fun Application.module() {
    install(GraphPlugin) {
        neo4j {
            uri = "bolt://localhost:7687"
            username = "neo4j"
            password = System.getenv("NEO4J_PASSWORD")
            database = "neo4j"
        }
    }
    routing {
        get("/people/count") {
            call.respondText(call.graphSuspendOperations().countVertices("Person").toString())
        }
    }
}
```

Expected: plugin state is available after installation and the route returns the current count. Starting with `install(GraphPlugin) {}` must fail immediately with `A graph backend must be selected...`; calling `graphSuspendOperations()` without installation must fail with `GraphPlugin is not installed...`.

## Verify shutdown and diagnose ownership

The managed `neo4j { ... }` DSL creates and closes operations plus Driver on `ApplicationStopped`. A preconstructed pair follows the separate [`GraphPluginConfig.operations`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/ktor/graph-ktor/src/main/kotlin/io/bluetape4k/graph/ktor/GraphPluginConfig.kt) contract:

```kotlin
// Default: the caller or DI container closes both operations.
install(GraphPlugin) {
    operations(sync, suspend) // closeOnStop = false
}

// Explicit handoff: the plugin closes the operations on ApplicationStopped.
install(GraphPlugin) {
    operations(sync, suspend, closeOnStop = true)
}
```

Only the `closeOnStop = true` branch registers close actions and deduplicates the two operations by object identity before closing each object once. With the default `false`, the plugin closes neither operations object. In both branches, a Driver used to construct those operations remains separately caller-owned because graph operations do not close an injected Driver. Test both alternatives:

```bash
./gradlew :bluetape4k-graph-ktor:test --tests '*GraphPluginTest' --tests '*BackendGraphPluginRuntimeTest'
```

If shutdown logs appear but the pool remains open, identify which configuration path created the Driver before adding another close hook; double-closing caller-owned infrastructure is a lifecycle bug.
