# FalkorDB

Choose FalkorDB when a Redis-shaped deployed service and its openCypher subset match the system boundary. The module uses jfalkordb; do not treat it as a drop-in Neo4j server merely because both accept Cypher-like queries.

Read [`FalkorDBGraphOperations.kt`](../../../../graph/graph-falkordb/src/main/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphOperations.kt) and [`FalkorDBGraphSchemaManager.kt`](../../../../graph/graph-falkordb/src/main/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphSchemaManager.kt). Container-backed CRUD, merge, batch, and schema evidence is in [`FalkorDBGraphOperationsTest.kt`](../../../../graph/graph-falkordb/src/test/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphOperationsTest.kt) and neighboring tests.

Transaction semantics are the main portability boundary. In 0.5.1 the suspend transaction test records an unsupported repository DSL path rather than pretending atomicity: [`FalkorDBGraphSuspendOperationsTest.kt`](../../../../graph/graph-falkordb/src/test/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphSuspendOperationsTest.kt). Design multi-write workflows around verified server/library guarantees.

Observe pool/Redis connectivity, query latency, memory, slow queries, and index creation. Reproduce against the release container, then the exact production image and configuration.
