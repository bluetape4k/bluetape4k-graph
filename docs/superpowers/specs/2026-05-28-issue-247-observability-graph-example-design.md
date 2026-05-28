# Issue 247 Observability Graph Example Design

## Context

Issue #247 adds the first observability-oriented example module for milestone 0.5.0. The example must teach incident
graph modeling, not only expose a smoke test.

## Scope

- Add `examples/observability-graph-examples`.
- Model services, APIs, teams, alerts, and incidents.
- Provide sync and suspend services over `GraphOperations` and `GraphSuspendOperations`.
- Include bundled graph-io CSV fixtures.
- Reuse the existing backend matrix pattern from other example modules.
- Update English and Korean README files with scenario, architecture diagram, graph model, traversal goals, sample data,
  and expected output.

## Non-Goals

- No production observability backend integration.
- No OpenTelemetry ingestion.
- No new graph-core traversal primitives.

## Design

The service exposes high-level incident-response questions:

- downstream service blast radius,
- upstream impacted services,
- affected public APIs,
- alert-boundary correlation,
- service owner lookup.

The same scenarios run against TinkerGraph, Neo4j, Memgraph, Apache AGE, and FalkorDB through abstract test classes.
TinkerGraph also validates the bundled graph-io CSV loader.

## Risks

- Backend traversal depth semantics must remain portable.
- AGE tests still rely on the Exposed global transaction manager used by current examples.
- README diagrams must stay source-aligned when service APIs change.
