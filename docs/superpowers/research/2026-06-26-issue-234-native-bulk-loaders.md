# Issue #234 - Backend-Native Bulk Loader Research

- Date: 2026-06-26
- Issue: https://github.com/bluetape4k/bluetape4k-graph/issues/234
- Scope: graph-io native-loader feasibility for Neo4j, Memgraph, Apache AGE,
  FalkorDB, and TinkerPop/TinkerGraph
- Status: recommend deferring implementation from `0.6.0`

## Decision

Do not add backend-native bulk loader implementations in the `0.6.0` line.
Keep `graph-io` focused on backend-neutral import/export contracts and proceed
with issue #233's chunked export cursor API as the next implementation PR.

Backend-native loaders should be revisited only after a separate backend-owned
fast-path SPI exists. That SPI must define file staging, backend URI mapping,
transaction boundaries, failure accounting, and Testcontainers fixtures before
any production implementation is accepted.

## Existing graph-io Contract

The current `graph-io` design deliberately excludes backend-specific native
loaders from the first implementation. It provides format modules on top of
`GraphOperations` and `GraphSuspendOperations`, with reports that count inserted
vertices, inserted edges, warnings, and failures in a backend-neutral way.

Native loaders do not fit that contract directly:

- They generally require files reachable from the database server, not just JVM
  readers or Kotlin streams.
- They usually report success or failure at query, command, or batch granularity
  rather than per `GraphIoVertexRecord` / `GraphIoEdgeRecord`.
- Their transaction semantics differ by backend and sometimes by deployment
  mode.
- They need backend-specific staging, cleanup, and security checks.

## Backend Matrix

| Backend | Native path | Files / streams | Transaction and failure shape | Local testability | Recommendation |
|---|---|---|---|---|---|
| Neo4j | `LOAD CSV`; offline `neo4j-admin database import` | `LOAD CSV` reads server-local `file:///`, remote HTTP(S)/FTP, and supported cloud URIs. `neo4j-admin` uses database-admin import files. | `LOAD CSV` is Cypher query execution and can be batched with Cypher transaction constructs. `neo4j-admin` is an initial/offline database import command with progress and bad-record reporting outside the driver contract. | `LOAD CSV` is testable with the existing Neo4j Testcontainer if files are mounted or served over HTTP. `neo4j-admin` is not a good fit for the current driver-level test harness. | Defer. Consider a future Neo4j `LOAD CSV` adapter after a backend-native SPI exists. Reject `neo4j-admin` for runtime graph-io. |
| Memgraph | `LOAD CSV` | Local filesystem paths and remote HTTP(S)/FTP URLs are supported. Memgraph Cloud requires a public URL because cloud instances cannot read private/local files. | Query-level Cypher import. `IGNORE BAD` can skip malformed CSV rows, which diverges from current graph-io failure accounting. | Testable with the existing Memgraph Testcontainer if files are mounted or served. | Defer. Candidate for a future Cypher `LOAD CSV` adapter, but not before shared staging/failure contracts exist. |
| Apache AGE | `load_labels_from_file` and `load_edges_from_file` | PostgreSQL server-side CSV file paths with AGE-specific node and edge file layouts. | SQL function calls per label/edge label. Edge files depend on vertex IDs and AGE's expected CSV layout. Failures surface through PostgreSQL/AGE errors, not graph-io record reports. | Potentially testable with the existing AGE Testcontainer, but file placement and AGE CSV layout require dedicated fixtures. | Defer. Treat as an AGE-specific importer, not a generic graph-io optimization. |
| FalkorDB | `LOAD CSV`; `falkordb-bulk-loader` / `GRAPH.BULK` | `LOAD CSV` reads local data-directory files or HTTPS URLs. Bulk loader consumes CSV and sends binary batches through `GRAPH.BULK`. | `LOAD CSV` is Cypher-like query execution. `GRAPH.BULK` returns aggregate node/edge counts and uses a binary endpoint that is closer to a backend protocol than a graph-io format adapter. | `LOAD CSV` and `GRAPH.BULK` are testable with the FalkorDB Testcontainer, but `GRAPH.BULK` needs custom binary encoding or the Python CLI. | Defer. A JVM `GRAPH.BULK` adapter may be valuable later, but it is a new backend protocol implementation. |
| TinkerPop / TinkerGraph | TinkerPop `io()` step for GraphML, GraphSON, and Gryo | JVM graph IO formats, not database-server native file loaders. | Generic single-threaded OLTP-style graph loading; not designed for massive parallel backend-native bulk loading. | Already locally testable in-memory, and graph-io already owns backend-neutral format coverage. | Reject for #234. Existing graph-io/TinkerGraph coverage is the right path. |

## Source Notes

- Neo4j `LOAD CSV`: https://neo4j.com/docs/cypher-manual/current/clauses/load-csv/
- Neo4j admin import: https://neo4j.com/docs/operations-manual/current/import/
- Memgraph CSV migration: https://memgraph.com/docs/data-migration/csv
- Memgraph `LOAD CSV`: https://memgraph.com/docs/querying/clauses/load-csv
- Apache AGE file import: https://age.apache.org/age-manual/master/intro/agload.html
- FalkorDB bulk loader: https://docs.falkordb.com/integration/bulk-loader.html
- FalkorDB `GRAPH.BULK`: https://docs.falkordb.com/design/bulk-spec.html
- FalkorDB `LOAD CSV`: https://docs.falkordb.com/cypher/load-csv.html
- TinkerPop IO step: https://tinkerpop.apache.org/docs/current/reference/

## Follow-Up Shape

Create new implementation issues only if #233 or later benchmarking shows the
backend-neutral importer is insufficient for a real workload. Split follow-ups
by backend:

1. Neo4j / Memgraph Cypher `LOAD CSV` adapter using a shared staged-file SPI.
2. AGE CSV-function adapter with AGE-specific vertex and edge file layouts.
3. FalkorDB `GRAPH.BULK` JVM protocol adapter if binary batch import is worth
   maintaining directly.

Do not combine these with issue #233. The cursor export API is backend-neutral
and should remain a separate PR.

## Acceptance Criteria Status

- Backend support, required files/streams, transaction semantics, and failure
  reporting differences: covered in the backend matrix.
- Native loader paths compared against existing graph-io importer contracts:
  covered in the contract section.
- Locally testable loaders identified: covered per backend.
- Recommendation per backend: covered in the backend matrix.
