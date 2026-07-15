# Ktor integration

Install `GraphPlugin` once in a Ktor application and select a backend or supply sync/suspend operations. Installation fails if configuration resolves no backend. The resulting `GraphPluginState` is stored in application attributes: [`GraphPlugin.kt`](../../../../ktor/graph-ktor/src/main/kotlin/io/bluetape4k/graph/ktor/GraphPlugin.kt), [`GraphPluginConfig.kt`](../../../../ktor/graph-ktor/src/main/kotlin/io/bluetape4k/graph/ktor/GraphPluginConfig.kt).

```kotlin
fun Application.module() {
    install(GraphPlugin) { tinkerGraph() }
    routing { /* graphOperations() / graphSuspendOperations() */ }
}
```

Lifetime is application-scoped, not request-scoped. On `ApplicationStopped`, only close actions registered by configuration run. Caller-owned drivers and data sources remain caller-owned. Verify startup, attribute lookup, and exactly-once close behavior with [`GraphPluginTest.kt`](../../../../ktor/graph-ktor/src/test/kotlin/io/bluetape4k/graph/ktor/GraphPluginTest.kt) and [`BackendGraphPluginRuntimeTest.kt`](../../../../ktor/graph-ktor/src/test/kotlin/io/bluetape4k/graph/ktor/BackendGraphPluginRuntimeTest.kt).

Diagnose install-time errors before routing errors. In production, observe application stop events, driver pool metrics, request cancellation, and whether request handlers use the API matching their coroutine/blocking model.
