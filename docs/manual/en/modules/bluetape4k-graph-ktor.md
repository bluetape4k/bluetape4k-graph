# bluetape4k-graph-ktor

## Install and select

`GraphPlugin` stores application-scoped sync/suspend operations in Ktor attributes. Choose one managed backend or supply existing operations. Avoid request-scoped installation and avoid selecting multiple backends. Source: [GraphPlugin.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/ktor/graph-ktor/src/main/kotlin/io/bluetape4k/graph/ktor/GraphPlugin.kt).

## Dependency and quick start

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-ktor")
    implementation("io.github.bluetape4k:bluetape4k-graph-tinkerpop")
}
```

```kotlin
fun Application.module() {
    install(GraphPlugin) { tinkerGraph() }
    routing {
        get("/vertices") {
            call.respondText(call.graphSuspendOperations().countVertices("Person").toString())
        }
    }
}
```

Expected: state is available after installation and the route accesses the application-scoped facade. Empty configuration fails at startup.

## Lifetime and shutdown ownership

Managed backend DSLs create operations and infrastructure, then register close actions on `ApplicationStopped`. Existing operations use [GraphPluginConfig.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/ktor/graph-ktor/src/main/kotlin/io/bluetape4k/graph/ktor/GraphPluginConfig.kt):

```kotlin
install(GraphPlugin) {
    operations(syncOps, suspendOps) // closeOnStop = false
}
```

The default is exactly `closeOnStop = false`: the caller or DI container closes both objects. Set true only to hand their ownership to the plugin. Close actions deduplicate identical instances. An injected Driver remains separately caller-owned unless a managed DSL created it.

## Failures and operations

Diagnose plugin installation and backend creation before route lookup. Missing installation, no selected backend, duplicate installation, connection creation, request cancellation, and shutdown ownership are separate failures. Observe application stop events, pool metrics, request latency, and close-once evidence.

```bash
./gradlew :bluetape4k-graph-ktor:test --tests '*GraphPluginTest' --tests '*BackendGraphPluginRuntimeTest'
```

Expected: startup/access pass, empty configuration fails, default existing operations stay open, and managed/explicit-close paths close exactly once.

## Related pages and non-goals

See [Ktor integration](../frameworks/ktor.md), [paired APIs](../architecture/paired-apis.md), and [operations](../guides/operations.md). The plugin does not create request transactions, close caller-owned resources by default, or make blocking calls nonblocking.
