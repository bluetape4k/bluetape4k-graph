# Batch Insert Design

## Related Issue

- Issue: [#33 Batch insert - 정점/간선 대량 생성 API](https://github.com/bluetape4k/bluetape4k-graph/issues/33)
- Date: 2026-05-10
- Branch/worktree: `feat/33-batch-insert` in `.worktrees/feat/33-batch-insert`
- Scope: `graph-core`, graph backend modules, `graph-io` importers, benchmark modules, root/module README files.

## Problem

The current core graph API creates vertices and edges one record at a time:

```kotlin
val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to 2024))
```

That shape is easy to use for small writes, but it is a poor fit for importers, examples, and bulk setup code. `graph-io`
already calls the single-record APIs in tight loops even though `GraphImportOptions.batchSize` exists. The result is many
driver round trips, more transaction overhead, and no common contract for backend-native batch insert behavior.

The new API must give callers a simple typed batch insert surface, while preserving the existing single-record API and the
sync / suspend / virtual-thread symmetry already used by the repository.

## Goals

- Add public batch vertex and edge create APIs that match the issue's intended call shape.
- Preserve existing source compatibility for current backends, fakes, wrappers, and tests.
- Provide backend-native batch implementations for production graph backends where practical.
- Make graph-io importers use the batch APIs through `GraphImportOptions.batchSize`.
- Preserve input order in returned `List<GraphVertex>` / `List<GraphEdge>`.
- Define clear failure and atomicity expectations, especially for missing edge endpoints.
- Verify at least one 10k-vertex and one 10k-edge batch scenario.

## Non-Goals

- No merge/upsert semantics. Batch insert always creates new elements; idempotent writes remain the responsibility of
  `mergeVertex` / `mergeEdge`.
- No mixed-label single call. A batch call is homogeneous by label to keep Cypher/Gremlin generation safe and fast.
- No new graph backend, dependency, or external loader.
- No public streaming `Flow` input API in this slice. Callers pass `List` or chunk upstream.

## Current Repository Findings

- `GraphOperations` is a facade over `GraphVertexRepository`, `GraphEdgeRepository`, and traversal/algorithm repositories.
- `GraphSuspendOperations` mirrors sync APIs and uses `Flow` for reads, while create/update/delete methods are suspend
  single-record calls.
- `GraphVirtualThreadVertexRepository` and `GraphVirtualThreadEdgeRepository` expose `CompletableFuture` adapters over
  sync repositories.
- Recent transaction, schema/index, and merge/upsert work avoided source-breaking facade changes by using capability
  interfaces and extension functions. Batch insert differs because it is a natural bulk variant of existing CRUD methods,
  and a default sequential implementation can preserve source compatibility.
- `GraphMergeValidation` already centralizes safe label/property-key validation for query-building code.
- Neo4j and Memgraph use the Neo4j Java Driver and can use one Cypher statement with `UNWIND`.
- FalkorDB supports `UNWIND` in Cypher, but existing code avoids `$props` map expansion in `CREATE`; the implementation
  must verify whether `SET += row.properties` works through `jfalkordb`.
- AGE builds Cypher strings inside `AgeSql.cypher(...)`; it may use AGE `UNWIND` with literal row lists or chunked
  multi-create Cypher because the current code does not pass Cypher parameters into AGE.
- TinkerGraph can implement order-preserving batch insert by looping inside one serialized write section with snapshot
  rollback.
- `graph-io` importers currently loop over `createVertex` / `createEdge`; they should buffer by label and flush via batch.

## External Documentation Findings

- Neo4j Cypher documents `UNWIND` for turning a parameter list into rows and warns that `UNWIND` does not guarantee row
  order by itself. Batch implementations must return an explicit row index and `ORDER BY` that index.
  Source: https://neo4j.com/docs/cypher-manual/5/clauses/unwind
- Neo4j Cypher `CREATE` supports creating nodes from a list parameter with `UNWIND $props AS map`.
  Source: https://neo4j.com/docs/cypher-manual/4.0/clauses/create/
- FalkorDB documents `UNWIND` and lists it as a supported Cypher clause.
  Sources: https://docs.falkordb.com/cypher/unwind.html and https://docs.falkordb.com/cypher/
- Apache AGE documents `UNWIND`, and AGE runs Cypher inside PostgreSQL through the `cypher()` SQL function.
  Sources: https://age.apache.org/age-manual/master/clauses/unwind.html and https://age.apache.org/overview/
- Apache TinkerPop documents `addV()` and `addE().to(...).property(...)` traversal steps for vertex and edge creation.
  Source: https://tinkerpop.apache.org/docs/current/reference/
- Memgraph material shows `WITH $batch AS nodes UNWIND nodes AS node CREATE ...` for large graph import patterns. Treat this
  as implementation guidance and still verify with the Memgraph Testcontainer.
  Source: https://memgraph.com/blog/handling-large-graph-datasets

## API Design Options

### Option A - Add Abstract Methods to Existing Repository Interfaces

Add `createVertices` and `createEdges` as abstract members.

Pros:

- The API appears directly on `GraphOperations`.
- Backends must make support explicit.

Cons:

- Source-breaking for every implementation, fake, adapter, and wrapper.
- Forces one large implementation step before `graph-core` compiles.

Decision: reject.

### Option B - Capability Interfaces and Extension Functions Only

Add `GraphBatchOperations` / `GraphSuspendBatchOperations` plus extension functions, following merge/upsert.

Pros:

- Keeps unsupported backends explicit.
- Matches recent capability work.

Cons:

- The issue explicitly asks for repository batch methods such as `GraphVertexRepository.createVertices`.
- `graph-io` and callers typed as `GraphOperations` would need extension imports for a basic CRUD bulk variant.
- A safe sequential default is available, so capability-only is heavier than needed.

Decision: reject as the primary API. Backend capability markers are not required for this slice.

### Option C - Default Batch Methods on Existing Repositories, Backend Overrides for Performance

Add default batch methods to the sync, suspend, and virtual-thread repository interfaces. The default implementation loops
over the existing single-record method, while production backends override with native batch behavior.

Pros:

- Preserves source compatibility.
- Keeps `ops.createVertices(...)` / `ops.createEdges(...)` discoverable.
- Gives fakes and narrow tests a correct baseline.
- Allows backend-native implementations to improve performance without making every fake implement the method immediately.

Cons:

- The default loop is not a performance implementation.
- Atomicity differs if a non-production fake relies on the default loop.

Decision: adopt, with explicit documentation that production backends override the defaults and that the default loop is a
compatibility baseline.

### Option D - Only Optimize `graph-io` Internals

Keep the public API unchanged and make importers call backend-specific helpers.

Pros:

- Smallest public API change.

Cons:

- Does not solve the issue's requested API.
- Duplicates backend-specific write behavior outside graph repositories.
- Prevents application callers from using the same performance path.

Decision: reject.

## Proposed Public API

### Core Input Model

Add one small input model in `graph-core`:

```kotlin
package io.bluetape4k.graph.model

data class BatchEdge(
    val fromId: GraphElementId,
    val toId: GraphElementId,
    val properties: Map<String, Any?> = emptyMap(),
)
```

There is no `BatchVertex` in this slice because vertex batch input is simply `List<Map<String, Any?>>` under one label.

### Sync Repository Methods

Add default methods to existing repository interfaces:

```kotlin
interface GraphVertexRepository {
    fun createVertices(
        label: String,
        propertiesList: List<Map<String, Any?>>,
    ): List<GraphVertex> =
        propertiesList.map { createVertex(label, it) }
}

interface GraphEdgeRepository {
    fun createEdges(
        label: String,
        edges: List<BatchEdge>,
    ): List<GraphEdge> =
        edges.map { createEdge(it.fromId, it.toId, label, it.properties) }
}
```

### Suspend Repository Methods

Add suspend equivalents with the same result shape:

```kotlin
suspend fun createVertices(
    label: String,
    propertiesList: List<Map<String, Any?>>,
): List<GraphVertex>

suspend fun createEdges(
    label: String,
    edges: List<BatchEdge>,
): List<GraphEdge>
```

The default implementation loops sequentially. Production suspend implementations may delegate to the sync native batch
method inside `Dispatchers.IO` when that is the existing backend pattern.

### Virtual Thread Methods

Add async wrappers:

```kotlin
fun createVerticesAsync(
    label: String,
    propertiesList: List<Map<String, Any?>>,
): CompletableFuture<List<GraphVertex>>

fun createEdgesAsync(
    label: String,
    edges: List<BatchEdge>,
): CompletableFuture<List<GraphEdge>>
```

## Common Semantics

- Empty input returns `emptyList()` and must not call the backend.
- Size `1` still uses the batch code path in production overrides unless a backend proves the single-create fast path has
  identical validation, failure, and cache behavior.
- The returned list must have the same size and logical order as the input list.
- `label` must be non-blank and pass `requireSafeIdentifier` before interpolation.
- Property keys must be non-blank and pass `requireSafeIdentifier` before interpolation.
- `BatchEdge.fromId.value` and `BatchEdge.toId.value` must be non-blank.
- Batch insert follows each backend's current single-create property value semantics. This slice does not add a new null
  policy beyond key and label validation.
- Production backend batch methods should be all-or-fail for one method call. If a backend cannot guarantee that, the
  limitation must be documented and covered by tests.
- Missing edge endpoints must fail the batch and avoid creating a partial edge batch in production backends.
- Duplicate edge creation is allowed. Batch insert is not merge/upsert.
- Batch APIs must not reuse the `createVertex` / `createEdge` write memoization result as if repeated rows were the same
  operation. Current caching wrappers memoize single create calls; every batch input row represents a new graph element.

## Default Implementation Contract

The default methods on repository interfaces are a compatibility baseline, not the production performance or atomicity
contract.

- Default sync methods loop over `createVertex` / `createEdge`.
- Default suspend methods loop over suspend single-create calls.
- Defaults preserve order and propagate the first failure.
- Defaults may leave partial state when a failure happens after earlier rows are created.
- Production backend classes in this repository must override the defaults and document/test their failure behavior.
- KDoc on the default methods must explicitly say "default implementation is sequential and may be partially applied".

This tradeoff avoids source-breaking abstract methods while making the production backends responsible for stronger batch
behavior.

## Chunking and Size Limits

Repository batch methods operate on one caller-provided list and do not automatically split application-level calls. This
keeps the method-level failure contract understandable: one call is one backend batch attempt.

- Application callers are responsible for chunking very large lists.
- `graph-io` callers chunk via `GraphImportOptions.batchSize`.
- Backends may internally chunk only when the chunks are executed inside one rollback-capable transaction. AGE is the main
  expected case because its SQL string can become large.
- If a backend cannot execute internal chunks atomically, it must not silently chunk; it should fail with a clear exception
  or document a backend limitation.
- The implementation should add a conservative backend-local constant only when a real driver/query-size limit is observed.

## Property Value Safety

Property labels and keys are validated as identifiers, but property values can be arbitrary user data and must not be
string-interpolated without a proven serializer.

| Backend | Value handling strategy |
|---------|-------------------------|
| Neo4j | Driver parameters: `row.properties` map |
| Memgraph | Neo4j Java Driver-compatible parameters: `row.properties` map |
| FalkorDB | Driver parameters when `SET += row.properties` works; otherwise per-key parameters grouped by key set |
| AGE | `AgePropertySerializer` for literal Cypher values; implementation must add tests for quotes, backslashes, nulls, lists, and nested maps before batch SQL is accepted |
| TinkerGraph | Gremlin traversal property calls with typed JVM values |

AGE must not add batch SQL by ad-hoc string concatenation of property values. If `AgePropertySerializer` is insufficient,
extend it first and lock behavior in `AgeSqlTest`.

## Backend Semantics

### Neo4j

Use one Cypher statement per homogeneous batch.

Vertex shape:

```cypher
UNWIND $rows AS row
CREATE (n:Person)
SET n += row.properties
RETURN row.index AS index, n
ORDER BY index
```

Edge shape:

```cypher
WITH $rows AS rows, size($rows) AS expected
UNWIND rows AS row
MATCH (a), (b)
WHERE elementId(a) = row.fromId AND elementId(b) = row.toId
WITH collect({index: row.index, properties: row.properties, a: a, b: b}) AS matched, expected
WHERE size(matched) = expected
UNWIND matched AS row
CREATE (row.a)-[r:KNOWS]->(row.b)
SET r += row.properties
RETURN row.index AS index, r
ORDER BY index
```

If the result count differs from input count, throw `GraphQueryException`.

### Memgraph

Use the same Cypher shape as Neo4j through the Neo4j Java Driver-compatible surface. Verify with container tests because
Memgraph compatibility and planner behavior can differ from Neo4j.

### FalkorDB

Preferred shape is `UNWIND` plus `SET += row.properties`, using `id(a) = toInteger(row.fromId)` for endpoint lookup.

Implementation must first prove this with a focused test. If FalkorDB rejects map merge/set through parameters, group each
chunk by property-key set and generate static per-key `SET n.key = row.properties.key` / `SET r.key = row.properties.key`
clauses, or fall back to a documented transactional limitation if no safe native shape exists.

### AGE

AGE currently centralizes query generation in `AgeSql` and does not pass Cypher parameter maps. Use chunked literal row
lists generated through `AgePropertySerializer`, for example:

```cypher
UNWIND [
  {index: 0, properties: {name: 'Alice'}},
  {index: 1, properties: {name: 'Bob'}}
] AS row
CREATE (v:Person)
SET v += row.properties
RETURN row.index AS index, v
```

Run all chunks inside one Exposed transaction. If AGE rejects `SET +=` for map literals in the current image, generate
static per-key `SET` clauses by property-key group. The plan must include a small AGE SQL test before broad backend edits.

### TinkerGraph

Use the repository's existing TinkerGraph write-serialization pattern, but prefer an explicit `reentrantLock` if the
implementation pass introduces new shared write coordination. For vertices, loop over inputs and call `g.addV(label)` with
validated property keys. For edges, pre-resolve every endpoint before creating any edge; then loop and create edges.

Use the existing transaction snapshot/restore pattern to roll back partial batch writes if an exception is thrown after
the batch starts.

## GraphElementId Backend Mapping

| Backend | Existing ID shape | Batch row encoding |
|---------|-------------------|--------------------|
| Neo4j | `elementId()` string | `fromId.value` / `toId.value` string parameter |
| Memgraph | Neo4j-compatible element id string in current mapper | string parameter, verified in Testcontainer |
| FalkorDB | numeric id exposed as string | string parameter converted with `toInteger(...)` |
| AGE | numeric id exposed as string | parse to `Long` before SQL generation |
| TinkerGraph | numeric id string in current mapper | parse to `Long` before traversal lookup |

Each backend test must cover edge batch creation using IDs returned by that backend's `createVertices` call.

## Graph-IO Integration

`graph-io` should use the new API rather than continue one-record loops.

- CSV: buffer vertices by label up to `GraphImportOptions.batchSize`, call `createVertices`, and map returned IDs back to
  external IDs in the same order. During the edge pass, buffer by label and call `createEdges`.
- NDJSON Jackson2/Jackson3: buffer vertices by label while reading, preserve duplicate policy behavior, and flush with
  `createVertices`; continue buffering edges until all vertices are known, then flush by label with `createEdges`.
- GraphML: apply the same buffer-by-label strategy for sync and suspend importers.
- Virtual-thread graph-io adapters should benefit through the virtual-thread repository async methods or through the sync
  importer path they already wrap.
- Preserve existing partial/failure report semantics. If a batch call fails, report the failure at the current phase and do
  not claim rows from that failed batch as created.

## Validation and Error Behavior

Add `GraphBatchValidation` or reuse an equivalent core helper:

- `validateVertexBatch(label, propertiesList)`
- `validateEdgeBatch(label, edges)`

Validation should return normalized input with validated labels/property keys, but it should not transform property values.

Errors:

- Invalid label/property key: `IllegalArgumentException`
- Blank edge endpoint ID: `IllegalArgumentException`
- Backend query failure or missing endpoint in production edge batch: `GraphQueryException`
- Empty batch: no exception

## Caching Wrapper Behavior

Caching wrappers in Neo4j, Memgraph, and AGE benchmark wrappers must treat batch create as a write:

- Do not memoize batch create results.
- Invalidate read caches after successful batch create.
- Invalidate write memoization caches if the implementation cannot reason about repeated-row semantics safely.
- If a batch call fails, do not invalidate unless the backend can leave partial state; for production all-or-fail backends,
  failed batch should leave caches unchanged.

## Test Strategy

Core tests:

- Default sync `createVertices` calls `createVertex` once per row and preserves order.
- Default sync `createEdges` calls `createEdge` once per row and preserves order.
- Suspend defaults preserve order.
- Validation rejects unsafe labels, unsafe property keys, and blank edge endpoint IDs.
- Empty input returns `emptyList()`.

Backend integration tests:

- 3-vertex batch returns 3 vertices in input order with properties.
- 3-edge batch returns 3 edges in input order with properties.
- Edge batch with one missing endpoint creates no edges for the batch.
- Empty batch is a no-op.
- Mixed property key sets are supported.
- Repeated property maps still create distinct vertices.
- Suspend batch variants pass.

Graph-IO tests:

- CSV/NDJSON/GraphML importers use `batchSize` to flush vertices and edges without changing report counts.
- Duplicate vertex policies still behave as before.
- Missing endpoint policy still returns `FAILED` or `PARTIAL` as before.

Benchmark / performance checks:

- Add backend benchmark methods for `createVertices10k` and `createEdges10k`.
- At minimum, run a targeted 10k smoke/integration test for TinkerGraph and one Cypher backend.
- Document single-create loop baseline vs batch throughput. Target at least a 5x improvement for Cypher production
  backends; if the result is lower, record the measured reason rather than hiding the benchmark.
- JMH benchmark execution can be documented as optional if local container runtime cost is too high for the PR loop.

## Acceptance Criteria

- `BatchEdge` exists in core with Korean KDoc.
- Sync, suspend, and virtual-thread repository APIs expose batch vertex and edge creation with Korean KDoc and explicit
  default-loop caveats.
- Existing implementations compile without source-breaking abstract method additions.
- Neo4j, Memgraph, FalkorDB, AGE, and TinkerGraph provide production batch implementations or explicitly document a
  backend limitation with tests.
- Return order matches input order.
- Missing edge endpoint handling is all-or-fail for production backend batch methods.
- `graph-io` importers use `GraphImportOptions.batchSize` for actual batch writes.
- README.md and README.ko.md document the batch API and graph-io batching behavior.
- Public KDoc covers `BatchEdge`, `createVertices`, `createEdges`, `createVerticesAsync`, and `createEdgesAsync`.
- Backend tests, graph-io tests, compile checks, and `git diff --check` pass before PR.

## Risks and Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| `UNWIND` returns rows out of input order | Callers receive mismatched IDs | Include `index` in rows and `ORDER BY index` |
| Edge endpoint missing after some edges are created | Partial graph state | Validate endpoints before `CREATE` in the Cypher query or transaction scope |
| FalkorDB rejects map `SET +=` | Native implementation delayed | Add focused spike/test, then group by property-key set if needed |
| AGE literal batches create very long SQL | Query size/perf failure | Chunk by `GraphImportOptions.batchSize` / backend constant inside one transaction |
| Default interface loops are mistaken for production performance | User disappointment | KDoc states defaults are compatibility baseline; backends override |
| Caching wrappers memoize repeated rows | Duplicate rows collapse incorrectly | Batch create never uses write memoization |
| Graph-IO batching changes partial report counts | Import regressions | Preserve report counters and add focused failure tests |
| Null property values differ by backend | Cross-backend mismatch | Match existing single-create semantics and cover mixed/null tests where existing behavior allows |

## Step 2-R Review Notes

### Local Perspective Reviews

| Perspective | Finding | Severity | Spec Decision |
|-------------|---------|----------|---------------|
| Developer | Direct abstract methods would break every implementation. | high | Use default interface methods plus backend overrides. |
| Security | Labels/property keys are interpolated into Cypher/Gremlin. | high | Require common validation before query construction. |
| Ops/SRE | Partial edge creation is the most harmful batch failure mode. | high | Production edge batch must validate endpoints before creating edges or run in rollback-capable transaction. |
| User/caller | Returned ID order is essential for importers. | high | Require explicit row index and ordered return. |
| Architect | `graph-io` must use the API or the core feature will not pay off. | medium | Include graph-io importer updates in acceptance criteria. |

### Claude Code Opus Advisor

Artifact: `.omx/artifacts/ask-claude-batch-insert-spec-20260510-135409.md`
Model: `${CLAUDE_ADVISOR_MODEL:-claude-opus-4-7}`

| Severity | Finding | Decision | Follow-up |
|----------|---------|----------|-----------|
| high | Default loop and all-or-fail wording could mislead callers. | accepted | Added Default Implementation Contract and production override requirement. |
| high | Chunking vs atomicity was underspecified. | accepted | Added Chunking and Size Limits contract. |
| high | AGE literal values need explicit injection-safe serializer policy. | accepted | Added Property Value Safety matrix and AgeSqlTest requirement. |
| high | Edge endpoint failure must not create partial batch. | accepted | Kept Cypher count guard and strengthened acceptance/tests. |
| medium | TinkerGraph write serialization should avoid new unsafe synchronized blocks. | accepted | Spec now says reuse existing pattern or prefer explicit lock for new coordination. |
| medium | KDoc, README, graph-io scope, ID mapping, and performance target needed more detail. | accepted | Added KDoc DoD, mapping table, migration scope, and benchmark target. |
