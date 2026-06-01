# Issue 235 GraphML compatibility slice

## Context

The first GraphML importer/exporter is intentionally a backend-neutral property-graph subset. Full GraphML includes constructs that do not map cleanly to `GraphVertex`, directed `GraphEdge`, and backend-neutral `GraphOperations`.

## Compatibility decision matrix

| GraphML construct | Decision | Current behavior | Rationale |
|---|---|---|---|
| Directed `<graph>` with `<node>`, `<edge>`, scalar `<data>` | Implement | Imported/exported | Directly maps to vertices, directed edges, labels, and scalar properties. |
| `key` definitions with scalar `attr.type` | Implement | Imported/exported | Provides stable property names and primitive coercion without backend-specific schema assumptions. |
| Graph-level `edgedefault="undirected"` | Defer | `SKIP` records `WARN`; `FAIL` returns failed report before writes | `GraphEdge` is directed. Auto-duplicating reverse edges would change edge counts and traversal semantics. |
| Edge-level `directed="false"` | Defer | `SKIP` records `WARN` and keeps the source-to-target projection; `FAIL` returns failed report before writes | A source-to-target projection is useful for inspection, but it is not a faithful undirected edge contract. |
| Nested `<graph>` inside a node | Reject for this slice | `SKIP` records `WARN` and skips the nested subgraph; `FAIL` returns failed report before writes | `GraphVertex` has no child graph scope. Flattening needs a separate external-id and ownership mapping design. |
| `<hyperedge>` | Reject | `SKIP` records `WARN`; `FAIL` returns failed report before writes | `GraphEdge` has exactly one source and one target. Hyperedge lowering requires a reification node policy. |
| `<port>` | Defer | `SKIP` records `WARN`; `FAIL` returns failed report before writes | Ports are endpoint metadata, but current edge endpoints target vertices only. |
| XML extension payloads such as yFiles graphics | Defer | Outside the contract | Visual metadata should be preserved only after a namespaced extension-property policy exists. |

## Undirected and nested graph mapping

Undirected GraphML is not implemented as true undirected storage in this slice. Under `UnsupportedGraphMlElementPolicy.SKIP`, the reader reports a warning and can keep the explicit `source` to `target` edge projection for diagnostic imports. Under `FAIL`, the bulk importer returns `GraphIoStatus.FAILED` before vertex or edge writes.

Nested graphs remain rejected for import because the current backend-neutral operations model has no graph containment boundary. Future support should choose one of these explicit policies before implementation:

- Flatten nested vertices into the parent graph with a reserved containment property.
- Reify the nested graph as a vertex and connect contained vertices with ownership edges.
- Add a graph-scope abstraction to `graph-core`.

The first two policies can be implemented in `graph-io-graphml` only after the property names, collision rules, and round-trip export contract are defined. The third is a larger `graph-core` design change.

## Compatibility fixtures

Representative fixtures live under `graph-io/graphml/src/test/resources/fixtures/graphml/`:

- `property-graph-basic.graphml`: directed property graph fixture for the implemented subset.
- `unsupported-constructs.graphml`: undirected graph default, nested graph, port, hyperedge, and undirected edge fixture.

The reader tests verify `SKIP` warning behavior and `FAIL` strict behavior. The bulk importer test verifies strict unsupported input fails before any graph writes.
