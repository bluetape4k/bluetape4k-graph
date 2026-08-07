# Testing graph applications

Build a test pyramid around semantics:

1. Use TinkerGraph for fast model and domain traversal tests.
2. Run the shared domain assertions against each candidate backend.
3. Use the release's Testcontainers fixtures for backend query, schema, transaction, merge, and batch behavior.
4. Add graph-io round trips and negative paths for every format used in production.

The examples demonstrate abstract shared tests plus concrete backend lifecycles; see [`AbstractRecommendationTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/recommendation-examples/src/test/kotlin/io/bluetape4k/graph/examples/recommendation/AbstractRecommendationTest.kt) and [`RecommendationBackendTests.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/recommendation-examples/src/test/kotlin/io/bluetape4k/graph/examples/recommendation/RecommendationBackendTests.kt).

Assert IDs only for presence/equality, not backend syntax. Test direction and depth limits, duplicate merge keys, empty and failing batches, transaction rollback/cancellation, unsupported schema operations, malformed import records, unresolved external IDs, and resource closure.

Container success is local evidence, not production equivalence. Record image/version, configuration, retries, and lifecycle timing so a pass-after-retry does not hide startup or resource conflicts.

## Run, observe, and diagnose

```bash
./gradlew :bluetape4k-graph-core:test --tests '*GraphMergeOperationsTest' --tests '*GraphBatchOperationsTest'
./gradlew :bluetape4k-graph-tinkerpop:test --tests '*TinkerGraphTransactionTest'
./gradlew :bluetape4k-graph-neo4j:test --tests '*Neo4jGraphOperationsTest'
./gradlew :bluetape4k-graph-okio:test --tests '*NegativePathTest'
```

Expected evidence is ordered batch output, one ID after duplicate merge, unchanged count after forced rollback, matching container CRUD counts, and preserved files after OkIO failures. Inject an unsafe label, transaction exception, stopped container, and truncated gzip. Classify failures by the first failing layer: core validation, repository capability, server lifecycle/query, then file codec/security. Do not rerun a container until green without recording startup timing and the first error.
