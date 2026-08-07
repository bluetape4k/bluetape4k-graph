# bluetape4k-graph-ktor

## Before you run

`GraphPlugin` stores application-scoped sync/suspend operations in Ktor attributes. Choose one managed backend or supply existing operations. Avoid request-scoped installation and avoid selecting multiple backends. Source: [GraphPlugin.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/ktor/graph-ktor/src/main/kotlin/io/bluetape4k/graph/ktor/GraphPlugin.kt).


Execution mode: **release-fixture linked**. `testApplication` supplies the Ktor application and HTTP test client; the linked test installs the plugin, accesses state, stops the application, and verifies the exact `closeOnStop` branch.

## Run

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

## Expected result

Expected: state is available after installation and the route accesses the application-scoped facade. Empty configuration fails at startup.

## Lifetime and shutdown ownership

Managed backend DSLs create operations and infrastructure, then register close actions on `ApplicationStopped`. Existing operations use [GraphPluginConfig.kt](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/ktor/graph-ktor/src/main/kotlin/io/bluetape4k/graph/ktor/GraphPluginConfig.kt):

```kotlin
install(GraphPlugin) {
    operations(syncOps, suspendOps) // closeOnStop = false
}
```

The default is exactly `closeOnStop = false`: the caller or DI container closes both objects. Set true only to hand their ownership to the plugin. Close actions deduplicate identical instances. An injected Driver remains separately caller-owned unless a managed DSL created it.

## Operations checklist

- Record the selected graph configuration.
- Watch request latency and Driver/DataSource pool state.
- Observe `ApplicationStopped` and close-once evidence.
- Keep `closeOnStop=false` for caller-owned operations.

## Failure and recovery

Symptom: startup says no graph was selected or route access says the plugin is missing. Fix installation before routing; on shutdown leaks, identify managed versus injected ownership and rerun the close-once test.

Diagnose plugin installation and backend creation before route lookup. Missing installation, no selected backend, duplicate installation, connection creation, request cancellation, and shutdown ownership are separate failures. Observe application stop events, pool metrics, request latency, and close-once evidence.

```bash
./gradlew :bluetape4k-graph-ktor:test --tests '*GraphPluginTest' --tests '*BackendGraphPluginRuntimeTest'
```

Expected: startup/access pass, empty configuration fails, default existing operations stay open, and managed/explicit-close paths close exactly once.

## Complete release example

The pinned [GraphPluginTest](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/ktor/graph-ktor/src/test/kotlin/io/bluetape4k/graph/ktor/GraphPluginTest.kt) defines the fixture values and is the complete executable release example. Run:

```bash
./gradlew :bluetape4k-graph-ktor:test --tests '*GraphPluginTest'
```

Expected: the fixture starts, assertions pass, and owned resources close in the documented order.

## Non-goals and related guides

See [Ktor integration](../frameworks/ktor.md), [paired APIs](../architecture/paired-apis.md), and [operations](../guides/operations.md). The plugin does not create request transactions, close caller-owned resources by default, or make blocking calls nonblocking.

<!-- release-readme-diagrams:start -->
## Release diagrams {#release-diagrams}

These diagrams are loaded directly from README assets published with the `0.6.0` release and pinned to its immutable commit. They describe this manual's released structure and runtime flows, not later Snapshot changes. Select a preview to open the SVG at the same release commit.

### graph ktor Architecture diagram

[![graph ktor Architecture diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/ktor-graph-ktor-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/ktor-graph-ktor-architecture-01.svg)

_Release README: [`ktor/graph-ktor/README.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/ktor/graph-ktor/README.md)_

<!-- release-readme-diagrams:end -->
