# Repository map

The repository is organized by responsibility, not by one monolithic driver.

| Area | What to learn | Evidence |
|---|---|---|
| `graph/graph-core` | models, repository contracts, schema and algorithms | [`GraphOperations.kt`](../../../../graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphOperations.kt) |
| `graph/graph-*` | backend semantics and adapters | [`Neo4jGraphOperations.kt`](../../../../graph/graph-neo4j/src/main/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphOperations.kt) |
| `graph-io/*` | records, formats and bulk transfer | [`GraphBulkImporter.kt`](../../../../graph-io/core/src/main/kotlin/io/bluetape4k/graph/io/contract/GraphBulkImporter.kt) |
| `ktor`, `spring-boot` | application lifetime integration | [`GraphPlugin.kt`](../../../../ktor/graph-ktor/src/main/kotlin/io/bluetape4k/graph/ktor/GraphPlugin.kt) |
| `examples` | domain-shaped use and cross-backend tests | [`AbstractCodeGraphTest.kt`](../../../../examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/AbstractCodeGraphTest.kt) |
| `benchmark` | workload evidence, not API promises | [`benchmark/README.md`](../../../../benchmark/README.md) |

<!-- diagram: repository learning map -->

Read core contracts before a backend implementation. Then trace one operation from interface to backend test. Example projects are deliberately unpublished; copy their design ideas, not their dependency coordinates or deployment assumptions.

When diagnosing a failure, locate its layer: model validation, repository capability, backend query/transaction, format codec, or application lifecycle. This prevents a driver-specific symptom from being documented as a portable contract.
