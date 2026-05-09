# Merge / Upsert Design

## Related Issue

- Issue: [#34 MERGE (upsert) 정점/간선 연산 - mergeVertex / mergeEdge](https://github.com/bluetape4k/bluetape4k-graph/issues/34)
- Date: 2026-05-09
- Scope: `graph-core`, graph backend modules, focused merge/upsert tests, core and backend docs.

## Problem

Graph applications frequently need "find by identity properties, create when absent, update when present" behavior. The current API forces callers to implement lookup, branch, create, and update logic repeatedly. That is error-prone under concurrency and produces duplicate vertices or relationships when callers forget backend-specific merge semantics.

The API must support:

- sync and coroutine merge/upsert operations for vertices and edges,
- common validation of labels, relationship labels, property keys, and match properties,
- idempotency tests that prove repeated calls do not create duplicates,
- backend-specific native `MERGE` where available,
- explicit fallback or unsupported behavior where a backend cannot safely emulate merge.

## Research Summary

### Repository Findings

- `GraphOperations` is a facade composed of repository interfaces. Adding methods directly to `GraphVertexRepository` and `GraphEdgeRepository` would force every fake, wrapper, and backend implementation to change at once.
- Recent transaction and schema/index work intentionally used capability interfaces plus extension accessors to preserve source compatibility. Merge/upsert should follow the same pattern.
- Neo4j, Memgraph, and FalkorDB already build Cypher with validated labels/properties and map records through backend mappers.
- AGE centralizes Cypher-in-SQL generation in `AgeSql`; merge should be added there first, then consumed by `AgeGraphOperations`.
- TinkerGraph uses direct traversal APIs and can implement get-or-create/update with Gremlin traversal steps.
- Caching wrappers memoize `createVertex`/`createEdge`. Merge is a write operation and must invalidate read/write caches instead of reusing create memoization.

### External Documentation Findings

- Neo4j Cypher documents `MERGE` with `ON CREATE SET` and `ON MATCH SET`, and relationship merge requires bound nodes from a preceding `MATCH` when matching/creating a relationship between existing nodes. Source: https://neo4j.com/docs/cypher-manual/3.5/clauses/merge/
- Apache AGE documents `MERGE` as a match-or-create combination and supports merging vertices with labels and properties through the `cypher()` SQL function. Source: https://age.apache.org/age-manual/master/clauses/merge.html
- Memgraph examples show `MERGE` with `ON MATCH SET` and `ON CREATE SET`, and Memgraph query plans include a `Merge` operator with `On Match` and `On Create` branches. Source: https://memgraph.com/blog/how-to-optimize-performance-with-memgraph-query-plans
- FalkorDB Cypher documentation lists `MERGE` as a supported clause. Source: https://docs.falkordb.com/cypher/
- Apache TinkerPop documentation describes vertex upsert through `fold().coalesce(unfold(), addV(...))`, and notes `mergeV()` for newer versions. The repository currently depends on TinkerPop 3.8.1, so either can work; the first slice can prefer the explicit traversal pattern if it fits current code better.

## Constraints

- Kotlin 2.3 and Java 25 preview remain unchanged.
- No new dependencies.
- Public APIs need Korean KDoc.
- README.md and README.ko.md must both document the public API.
- `assertThrows` must not be introduced; tests use `io.bluetape4k.assertions.assertFailsWith`.
- Identifier validation must happen before query string interpolation.
- `matchProperties` must be stable identity data, not mutable update data.

## Architecture Options

### Option A - Add Methods Directly to Repository Interfaces

Add `mergeVertex` to `GraphVertexRepository` and `mergeEdge` to `GraphEdgeRepository`.

Pros:

- Matches the issue's sample call shape without requiring extension imports.
- Compile-time visibility from `GraphOperations`.

Cons:

- Breaks source compatibility for all existing implementations.
- Forces unsupported or staged backends to implement methods immediately.
- Reverses the capability pattern established by transaction and schema manager work.

Decision: reject for this slice.

### Option B - Capability Interfaces + Extension Functions

Add:

```kotlin
interface GraphMergeOperations {
    fun mergeVertex(label: String, matchProperties: Map<String, Any?>, setProperties: Map<String, Any?> = emptyMap()): GraphVertex
    fun mergeEdge(fromId: GraphElementId, toId: GraphElementId, label: String, matchProperties: Map<String, Any?> = emptyMap(), setProperties: Map<String, Any?> = emptyMap()): GraphEdge
}

interface GraphSuspendMergeOperations { ... }

fun GraphOperations.mergeVertex(...): GraphVertex
fun GraphOperations.mergeEdge(...): GraphEdge
```

Pros:

- Preserves existing source compatibility.
- Keeps backend support explicit.
- Gives callers the same `ops.mergeVertex(...)` syntax once the extension is imported.
- Mirrors the transaction/schema capability pattern already in the repository.

Cons:

- Requires extension imports for variables typed as `GraphOperations`.
- Unsupported implementations fail at runtime through the extension.

Decision: adopt.

### Option C - Generic Fallback via find/update/create

Implement extension defaults by calling `findVerticesByLabel`, `findEdgesByLabel`, `create*`, and `update*`.

Pros:

- Minimal backend work.
- Works for simple local use.

Cons:

- Not atomic.
- Race-prone under concurrent writers.
- Edge update API does not exist, so mergeEdge would be incomplete.
- Hides backend capability differences.

Decision: reject as default. A backend may use a tested fallback only when native merge is absent and semantics remain clear.

## Proposed API

The public API lives in `io.bluetape4k.graph.repository` beside transaction scope extensions.

```kotlin
fun GraphOperations.mergeVertex(
    label: String,
    matchProperties: Map<String, Any?>,
    setProperties: Map<String, Any?> = emptyMap(),
): GraphVertex

fun GraphOperations.mergeEdge(
    fromId: GraphElementId,
    toId: GraphElementId,
    label: String,
    matchProperties: Map<String, Any?> = emptyMap(),
    setProperties: Map<String, Any?> = emptyMap(),
): GraphEdge
```

Suspend versions mirror the sync API:

```kotlin
suspend fun GraphSuspendOperations.mergeVertex(...): GraphVertex
suspend fun GraphSuspendOperations.mergeEdge(...): GraphEdge
```

Validation rules:

- `label` must be non-blank and pass `requireSafeIdentifier`.
- Every property key in `matchProperties` and `setProperties` must pass `requireSafeIdentifier`.
- `mergeVertex` requires non-empty `matchProperties`; label-only merge can match multiple existing vertices while returning a single value.
- `matchProperties` values must be non-null because Cypher `MERGE` identity maps with null are backend-sensitive and unsafe.
- `setProperties` must not contain any key from `matchProperties`; identity keys should not be overwritten by the upsert update branch.
- `mergeEdge` identity is `fromId + toId + label + matchProperties`; empty edge `matchProperties` is allowed.

## Backend Semantics

### Neo4j

Use native Cypher:

```cypher
MERGE (n:Person {email: $match_email})
ON CREATE SET n.name = $set_name
ON MATCH SET n.name = $set_name
RETURN n
```

For edges:

```cypher
MATCH (a), (b)
WHERE elementId(a) = $fromId AND elementId(b) = $toId
MERGE (a)-[r:KNOWS {since: $match_since}]->(b)
ON CREATE SET r.weight = $set_weight
ON MATCH SET r.weight = $set_weight
RETURN r
```

### Memgraph

Use the same Cypher shape as Neo4j through the Neo4j Java Driver-compatible surface. Use Memgraph tests to verify `ON CREATE SET` / `ON MATCH SET` and edge merge behavior.

### FalkorDB

Use Cypher `MERGE` because FalkorDB lists `MERGE` as a supported clause. Validate in Testcontainers because FalkorDB parameter handling differs from Neo4j and does not support `$props` map expansion in current create code.

### AGE

Add `AgeSql.mergeVertex` and `AgeSql.mergeEdge`, then execute through existing AGE JDBC paths. AGE serializes property maps into Cypher literals, so validation must happen before serialization.

### TinkerGraph

Use Gremlin get-or-create/update semantics:

- Vertex: `V().has(label, key, value)...fold().coalesce(unfold(), addV(label).property(...)).property(...)`.
- Edge: traverse from the source vertex to matching outgoing edge(s), `fold().coalesce(unfold(), addE(label).from(source).to(target).property(...)).property(...)`.

If the Java traversal API makes `mergeV()` / `mergeE()` simpler and tests prove equivalent behavior, it may replace the explicit `fold().coalesce()` pattern.

## Risks and Failure Modes

| Risk | Impact | Mitigation |
|------|--------|------------|
| Mutable identity keys overwritten by `setProperties` | Repeated calls can create duplicates | Reject overlapping match/set keys |
| Empty vertex match | Can match many vertices and update arbitrary first result | Require non-empty vertex match properties |
| Null match values | Backend-specific MERGE errors or non-matches | Reject null match values in common validation |
| Cypher injection through property keys or labels | Security issue | Validate every identifier before string interpolation |
| Non-atomic fallback | Duplicate data under concurrency | Prefer native MERGE; do not provide generic default fallback |
| Edge merge without edge update API | Incomplete behavior | Implement backend-native relationship MERGE returning updated edge |
| TinkerGraph traversal creates duplicate edges | Idempotency failure | Dedicated repeated-call tests for vertex and edge merge |
| Caching wrappers return stale reads | Wrong post-merge query results | Treat merge as write and invalidate caches |

## Acceptance Criteria

- Core exposes sync and suspend merge/upsert capability interfaces plus extension functions with Korean KDoc.
- Neo4j, Memgraph, FalkorDB, AGE, and TinkerGraph implement sync and suspend merge operations or fail explicitly with tests if a backend cannot support a safe operation.
- Repeated `mergeVertex` calls with the same match data return one logical vertex and do not increase label count.
- Repeated `mergeEdge` calls with the same endpoints, label, and match data return one logical edge.
- Tests cover create branch, match/update branch, invalid identifiers, null match values, overlap rejection, and suspend variants.
- README.md and README.ko.md document the API and backend capability matrix.
- `./gradlew test --no-daemon` passes before completion.
