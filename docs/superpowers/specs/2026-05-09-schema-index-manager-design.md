# Schema / Index Manager Design

## Related Issue

- Issue: [#32 Schema / Index 관리 API - GraphSchemaManager 인터페이스](https://github.com/bluetape4k/bluetape4k-graph/issues/32)
- Date: 2026-05-09
- Scope: `graph-core`, graph backend modules, focused backend integration tests, core docs.

## Problem

Production graph workloads need explicit schema operations for lookup indexes and uniqueness constraints. The repository already has a declarative schema DSL through `VertexLabel`, `EdgeLabel`, and `PropertyDef`, but it has no execution API that can create, drop, or inspect backend indexes.

The new API must:

- expose sync and suspend schema manager contracts,
- keep existing `GraphOperations` and `GraphSuspendOperations` source compatible,
- support `schemaManager()` accessors,
- provide backend-specific implementations for Neo4j, Memgraph, AGE, TinkerGraph, and FalkorDB,
- reject unsafe labels/properties before DDL string construction.

## Research Summary

### Repository Findings

- `GraphOperations` and `GraphSuspendOperations` are facade interfaces in `io.bluetape4k.graph.repository`; adding members would force all fake/test implementations to change.
- Transaction DSL avoided interface churn by adding capability interfaces plus extension accessors. Schema manager should follow the same pattern.
- `VertexLabel`, `EdgeLabel`, and `PropertyDef` live in `io.bluetape4k.graph.schema`; `PropertyDef.name` is the natural bridge into schema DDL helpers.
- Backends already use `requireSafeIdentifier` for Cypher identifiers. Schema DDL must use the same guard for labels, properties, index names, and constraint names.

### External Documentation Findings

- Neo4j current Cypher supports `CREATE INDEX [name] IF NOT EXISTS FOR (n:Label) ON (n.property)`, node property uniqueness constraints through `CREATE CONSTRAINT name IF NOT EXISTS FOR (n:Label) REQUIRE n.property IS UNIQUE`, and metadata via `SHOW INDEXES` / `SHOW CONSTRAINTS`.
- Memgraph supports label and label-property indexes with `CREATE INDEX ON :Label(property)`, removal through `DROP INDEX ON :Label(property)`, and metadata through `SHOW INDEX INFO`; uniqueness uses `CREATE CONSTRAINT ON (n:Label) ASSERT n.property IS UNIQUE`.
- Apache AGE uses PostgreSQL tables and `agtype` storage under graph schemas. AGE Cypher does not provide the same schema DDL surface as Neo4j, so AGE support must be implemented as PostgreSQL-side index/constraint helpers and tested against the current Testcontainer image.
- TinkerGraph is in-memory and has no durable schema/constraint layer for this API. It should implement explicit no-op index creation/listing and explicit unsupported uniqueness constraints, not pretend persistence.
- FalkorDB supports Cypher index creation and metadata procedures such as `CALL db.indexes()`; unique constraints are backed by `GRAPH.CONSTRAINT CREATE` and require supporting indexes. The existing jfalkordb driver surface may require a staged implementation.

## Constraints

- Kotlin 2.3 and Java 25 preview remain unchanged.
- No new dependency is required.
- Public APIs need Korean KDoc.
- Public interface changes require `README.md` and `README.ko.md` sync.
- Backend DDL must never interpolate unchecked identifiers.
- Metadata model must tolerate backend differences without exposing backend-specific records directly.

## Architecture Options

### Option A - Add Methods Directly to `GraphOperations`

Add schema methods to the existing facade interfaces.

Pros:

- Simple discovery from `ops`.
- No extension lookup failure.

Cons:

- Breaks source compatibility for all existing implementers and tests.
- Forces schema support onto backends/wrappers that may not support it.
- Repeats the problem already solved by transaction capability interfaces.

Decision: reject.

### Option B - Capability Interfaces + Extension Accessors

Add `GraphSchemaManagementOperations` and `GraphSuspendSchemaManagementOperations` capability interfaces. Provide:

```kotlin
fun GraphOperations.schemaManager(): GraphSchemaManager
fun GraphSuspendOperations.schemaManager(): GraphSuspendSchemaManager
```

Backends that support schema management implement the capability. Unsupported wrappers fail explicitly.

Pros:

- Preserves source compatibility.
- Mirrors transaction DSL pattern.
- Makes unsupported behavior explicit.
- Allows backend-specific manager objects without widening core facade interfaces.

Cons:

- Callers need an extension import.
- Caching wrappers do not automatically expose schema management unless they deliberately implement the provider.

Decision: adopt.

### Option C - Backend Constructors Only

Expose only `Neo4jGraphSchemaManager(driver)` style classes and skip common accessors.

Pros:

- Simple implementation.
- No capability lookup.

Cons:

- Fragments the public API.
- Does not satisfy issue accessor requirement.
- Harder for examples and Spring integrations to consume uniformly.

Decision: reject.

## Proposed API

### Models

```kotlin
enum class GraphSchemaEntityType { VERTEX, EDGE, UNKNOWN }
enum class GraphConstraintType { UNIQUE, EXISTS, UNKNOWN }

data class GraphIndex(
    val name: String,
    val label: String,
    val property: String?,
    val entityType: GraphSchemaEntityType = GraphSchemaEntityType.VERTEX,
    val unique: Boolean = false,
)

data class GraphConstraint(
    val name: String,
    val label: String,
    val property: String,
    val type: GraphConstraintType,
    val entityType: GraphSchemaEntityType = GraphSchemaEntityType.VERTEX,
)
```

`property` is nullable for label-only index metadata, especially Memgraph and TinkerGraph.

### Managers

```kotlin
interface GraphSchemaManager {
    fun createIndex(label: String, property: String)
    fun createUniqueConstraint(label: String, property: String)
    fun dropIndex(label: String, property: String)
    fun listIndexes(): List<GraphIndex>
    fun listConstraints(): List<GraphConstraint>
}

interface GraphSuspendSchemaManager {
    suspend fun createIndex(label: String, property: String)
    suspend fun createUniqueConstraint(label: String, property: String)
    suspend fun dropIndex(label: String, property: String)
    suspend fun listIndexes(): List<GraphIndex>
    suspend fun listConstraints(): List<GraphConstraint>
}
```

Add convenience overloads:

```kotlin
fun GraphSchemaManager.createIndex(label: VertexLabel, property: PropertyDef<*>)
fun GraphSchemaManager.createUniqueConstraint(label: VertexLabel, property: PropertyDef<*>)
fun GraphSchemaManager.dropIndex(label: VertexLabel, property: PropertyDef<*>)
```

Suspend equivalents mirror these helpers.

The generic accessors throw `UnsupportedOperationException` when an operation facade does not implement schema-management capability interfaces. This mirrors transaction DSL behavior and prevents silent auto-commit or no-op schema behavior.

Schema DSL integration uses the current DSL names:

```kotlin
ops.schemaManager().createIndex(PersonLabel.label, PersonLabel.email.name)
ops.schemaManager().createIndex(PersonLabel, PersonLabel.email)
```

## Backend Semantics

### Neo4j

- Create index: named `bt4k_idx_{label}_{property}` with `IF NOT EXISTS`.
- Create unique constraint: named `bt4k_uc_{label}_{property}` with `IF NOT EXISTS`.
- Drop index: `DROP INDEX name IF EXISTS`.
- List: `SHOW INDEXES` and `SHOW CONSTRAINTS`.

### Memgraph

- Create index: `CREATE INDEX ON :Label(property)`.
- Create unique constraint: `CREATE CONSTRAINT ON (n:Label) ASSERT n.property IS UNIQUE`.
- Drop index: `DROP INDEX ON :Label(property)`.
- List indexes: `SHOW INDEX INFO`.
- List constraints: `SHOW CONSTRAINTS`.

### AGE

- Create index: PostgreSQL index against the AGE label table when the label table exists.
- Unique constraints: implement only after current Testcontainer verifies a safe PostgreSQL expression. If not reliable, throw `UnsupportedOperationException` with a precise message and test it.
- List indexes: query `pg_indexes` for generated `bt4k_idx_` names in the graph schema.
- List constraints: query PostgreSQL catalogs for generated unique constraints or return empty when unique constraints are unsupported.

### TinkerGraph

- `createIndex` and `dropIndex` are no-op but recorded in an in-memory set for listability within the manager instance.
- `createUniqueConstraint` throws `UnsupportedOperationException`.
- This is explicit because TinkerGraph has no durable uniqueness enforcement.

### FalkorDB

- Create index: Cypher `CREATE INDEX FOR (n:Label) ON (n.property)`.
- Drop index: Cypher drop when supported by the current server/driver, otherwise explicit unsupported.
- List indexes: prefer `CALL db.indexes()`.
- Unique constraint: staged. If jfalkordb exposes a reliable command path for `GRAPH.CONSTRAINT CREATE`, implement and test it. Otherwise throw explicit unsupported and document the gap.

## Risks and Failure Modes

| Risk | Impact | Mitigation |
|------|--------|------------|
| DDL injection through label/property/name | High security impact | Validate all identifiers with `requireSafeIdentifier` before building DDL |
| Backend metadata result shapes differ | False tests or broken list parsing | Use tolerant mappers and assert only common fields in integration tests |
| AGE/FalkorDB uniqueness support differs from issue ideal | Silent non-enforcement | Prefer explicit `UnsupportedOperationException` over fake success |
| Duplicate create commands fail on backends without `IF NOT EXISTS` | Non-idempotent user setup | Swallow only known duplicate schema exceptions or test backend behavior before deciding |
| TinkerGraph no-op may mislead callers | Runtime uniqueness assumptions | No-op only for indexes; constraints explicitly unsupported |

## Acceptance Criteria

- `GraphSchemaManager` and `GraphSuspendSchemaManager` are public with Korean KDoc.
- `GraphIndex` and `GraphConstraint` models are public with Korean KDoc.
- `GraphOperations.schemaManager()` and `GraphSuspendOperations.schemaManager()` extension accessors exist and fail explicitly for unsupported operations.
- Neo4j and Memgraph sync/suspend managers create/list/drop property indexes and create/list unique constraints.
- TinkerGraph sync/suspend manager has no-op index behavior and explicit unsupported unique constraints.
- AGE and FalkorDB either implement tested support or explicit unsupported behavior for unsupported operations; no silent success for uniqueness.
- Existing schema DSL can be used via overloads accepting `VertexLabel` and `PropertyDef`.
- `README.md` and `README.ko.md` document the API and backend capability matrix.
- Targeted module tests and full `./gradlew test --no-daemon` pass.

## DoD

- Spec and plan committed before implementation.
- All changed public APIs have Korean KDoc.
- All changed modules compile.
- Targeted backend tests pass.
- Full test passes or any failure is classified with evidence.
- `bluetape4k-design` Step 6-R six-tier review passes before final report.

## Step 2-R Review Notes

| Perspective | Finding | Resolution |
|-------------|---------|------------|
| Developer | `GraphSuspendOperations.schemaManager()` accessor does not need to be `suspend`; manager methods carry suspension. | Revised API sketch to non-suspend accessor. |
| Security | DDL names as well as labels/properties must be identifier-checked. | Added explicit identifier validation constraint and risk. |
| Ops/SRE | Unsupported backends must fail loudly rather than no-op constraints. | Kept explicit unsupported behavior for uniqueness where backend cannot enforce. |
| User/caller | Issue example used `PersonLabel.name`, but current DSL exposes `VertexLabel.label`. | Added current `label`-based DSL example and overload. |
