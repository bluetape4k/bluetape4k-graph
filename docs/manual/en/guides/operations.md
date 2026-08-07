# Operations

Define service-level evidence around the actual boundary:

- driver/data-source pool utilization, acquisition latency, and failures;
- query latency/error rate by operation and backend error code;
- transaction commit, rollback, retry, timeout, and cancellation counts;
- batch/import throughput, partial counts, buffered edges, and rejected records;
- schema/index inventory and query-plan regressions;
- Ktor/Spring startup and shutdown ownership events.

`GraphSession` explicitly leaves injected resource ownership outside `close()`: [`GraphSession.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSession.kt). Framework pages explain when a container registers close actions.

For incidents, preserve the backend query/error, operation parameters with secrets removed, transaction state, graph-io report, server/container version, and cancellation signal. Compare observed counts before retrying a non-idempotent batch. Use merge only when its key semantics are tested for the selected backend.

Backups and restore are backend responsibilities. Validate restored schema, counts, representative paths, and external-ID mapping with application-level checks rather than trusting file completion alone.

## Operational smoke and failure drill

```kotlin
val before = ops.countVertices("Person")
val start = System.nanoTime()
val path = ops.shortestPath(sourceId, targetId, PathOptions(edgeLabel = "KNOWS", maxDepth = 6))
println("shortestPath.elapsedNanos=${System.nanoTime() - start}, found=${path != null}")
check(ops.countVertices("Person") == before)
```

Run this after startup and restore; expected observations are stable counts, bounded latency, and either a valid path or an explicit no-path result. During a drill, stop the backend or exhaust the pool, then confirm the application reports acquisition/query failure without committing later writes. Diagnose pool acquisition first, server health second, query plan/schema third, and domain data last. Record the exact backend version and ownership path used during shutdown.
