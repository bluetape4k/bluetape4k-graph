# Operations

Define service-level evidence around the actual boundary:

- driver/data-source pool utilization, acquisition latency, and failures;
- query latency/error rate by operation and backend error code;
- transaction commit, rollback, retry, timeout, and cancellation counts;
- batch/import throughput, partial counts, buffered edges, and rejected records;
- schema/index inventory and query-plan regressions;
- Ktor/Spring startup and shutdown ownership events.

`GraphSession` explicitly leaves injected resource ownership outside `close()`: [`GraphSession.kt`](../../../../graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSession.kt). Framework pages explain when a container registers close actions.

For incidents, preserve the backend query/error, operation parameters with secrets removed, transaction state, graph-io report, server/container version, and cancellation signal. Compare observed counts before retrying a non-idempotent batch. Use merge only when its key semantics are tested for the selected backend.

Backups and restore are backend responsibilities. Validate restored schema, counts, representative paths, and external-ID mapping with application-level checks rather than trusting file completion alone.
