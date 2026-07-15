# Testing graph applications

Build a test pyramid around semantics:

1. Use TinkerGraph for fast model and domain traversal tests.
2. Run the shared domain assertions against each candidate backend.
3. Use the release's Testcontainers fixtures for backend query, schema, transaction, merge, and batch behavior.
4. Add graph-io round trips and negative paths for every format used in production.

The examples demonstrate abstract shared tests plus concrete backend lifecycles; see [`AbstractRecommendationTest.kt`](../../../../examples/recommendation-examples/src/test/kotlin/io/bluetape4k/graph/examples/recommendation/AbstractRecommendationTest.kt) and [`RecommendationBackendTests.kt`](../../../../examples/recommendation-examples/src/test/kotlin/io/bluetape4k/graph/examples/recommendation/RecommendationBackendTests.kt).

Assert IDs only for presence/equality, not backend syntax. Test direction and depth limits, duplicate merge keys, empty and failing batches, transaction rollback/cancellation, unsupported schema operations, malformed import records, unresolved external IDs, and resource closure.

Container success is local evidence, not production equivalence. Record image/version, configuration, retries, and lifecycle timing so a pass-after-retry does not hide startup or resource conflicts.
