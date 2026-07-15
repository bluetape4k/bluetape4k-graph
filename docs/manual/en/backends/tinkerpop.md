# TinkerPop and TinkerGraph

The 0.5.1 module embeds TinkerGraph and maps the common repository contract onto TinkerPop/Gremlin. It is the fastest local verification path and a useful algorithm/test fixture, but it does not reproduce remote server latency, durability, clustering, or a vendor's transaction model.

Use [`TinkerGraphOperations.kt`](../../../../graph/graph-tinkerpop/src/main/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphOperations.kt) for synchronous work and [`TinkerGraphSuspendOperations.kt`](../../../../graph/graph-tinkerpop/src/main/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphSuspendOperations.kt) for coroutine integration. CRUD/traversal behavior is covered by [`TinkerGraphOperationsTest.kt`](../../../../graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphOperationsTest.kt); commit and rollback are covered by [`TinkerGraphTransactionTest.kt`](../../../../graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphTransactionTest.kt).

Prefer it for unit tests, tutorials, and a first graph model. Before moving to another backend, rerun merge, batch, schema, transaction, property-type, and traversal tests there. An in-memory pass proves domain logic, not infrastructure readiness.
