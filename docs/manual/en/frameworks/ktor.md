# Ktor integration

Install `GraphPlugin` once in a Ktor application and select a backend or supply sync/suspend operations. Installation fails if configuration resolves no backend. The resulting `GraphPluginState` is stored in application attributes: [`GraphPlugin.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/ktor/graph-ktor/src/main/kotlin/io/bluetape4k/graph/ktor/GraphPlugin.kt), [`GraphPluginConfig.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/ktor/graph-ktor/src/main/kotlin/io/bluetape4k/graph/ktor/GraphPluginConfig.kt).

```kotlin
fun Application.module() {
    install(GraphPlugin) { tinkerGraph() }
    routing { /* graphOperations() / graphSuspendOperations() */ }
}
```

Lifetime is application-scoped, not request-scoped. On `ApplicationStopped`, only close actions registered by configuration run. Caller-owned drivers and data sources remain caller-owned. Verify startup, attribute lookup, and exactly-once close behavior with [`GraphPluginTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/ktor/graph-ktor/src/test/kotlin/io/bluetape4k/graph/ktor/GraphPluginTest.kt) and [`BackendGraphPluginRuntimeTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/ktor/graph-ktor/src/test/kotlin/io/bluetape4k/graph/ktor/BackendGraphPluginRuntimeTest.kt).

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

The managed `neo4j { ... }` DSL creates and closes operations plus Driver on `ApplicationStopped`. For a caller-owned Driver, construct sync/suspend operations and use `operations(sync, suspend)`; plugin close is deduplicated for those operations, but Driver ownership remains with the caller. Test both alternatives:

```bash
./gradlew :bluetape4k-graph-ktor:test --tests '*GraphPluginTest' --tests '*BackendGraphPluginRuntimeTest'
```

If shutdown logs appear but the pool remains open, identify which configuration path created the Driver before adding another close hook; double-closing caller-owned infrastructure is a lifecycle bug.
