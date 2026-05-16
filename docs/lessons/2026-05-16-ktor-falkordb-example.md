# 2026-05-16 — FalkorDB Ktor Example: Driver Ownership and Graph Name Coupling

## Context

Added `FalkorDBKtorGraphApp.kt` and `FalkorDBKtorGraphAppTest.kt` to `ktor-graph-examples` (issue #123).

## Key Decisions

### Driver ownership follows the plugin contract

`FalkorDBGraphPluginConfig.falkorDB()` is explicitly caller-owned ("driver is a caller-owned resource; this helper does not close it"). The first implementation created a driver internally from `host: String, port: Int`, which violated this contract and caused a Jedis connection pool leak on `testApplication` teardown.

Fix: change `falkorDbModule(host, port)` → `falkorDbModule(driver: Driver)`. Caller creates and owns the driver. In tests, use a single `by lazy` driver in `companion object` reused across test methods.

### Do not expose configurable graph name when routes are hardcoded

The initial version exposed `graphName: String = DEMO_GRAPH_NAME` as a parameter. Codex review caught that `graphDemoRoutes()` always calls `DemoCityGraph.reset()` which hard-codes the graph name `"demo"`. Passing any non-default name silently misroutes reset: the plugin writes to graph X, but reset always drops/creates graph "demo", so repeated resets accumulate vertices.

Fix: remove the `graphName` parameter. When routes and graph name must be configurable together, the caller should configure `GraphPlugin` directly instead of using the convenience module.

## Verification

```
./gradlew :ktor-graph-examples:test --tests "*.FalkorDBKtorGraphAppTest"
2 tests, 0 failures — BUILD SUCCESSFUL
```

Codex review: CRITICAL=0, HIGH=0.

## Future Guidance

- When writing an `Application.someModule()` convenience function, verify that all routes, graph names, and reset operations target the same logical resource. Mismatches are silent at compile time.
- Testcontainers-backed driver for Ktor examples: always declare as `companion object { val driver by lazy { ... } }` to avoid per-`testApplication` connection pool creation.
