# 이슈 247 Observability graph example 설계

## 맥락

Issue #247 adds the first observability-oriented example module for milestone 0.5.0. The example must teach incident
graph modeling, not only expose a smoke test.

## 범위

- Add `examples/observability-graph-examples`.
- Model services, APIs, teams, alerts, and incidents.
- Provide sync and suspend services over `GraphOperations` and `GraphSuspendOperations`.
- Include bundled graph-io CSV fixtures.
- Reuse the existing backend matrix pattern from other example modules.
- Update English and Korean README files with scenario, architecture diagram, graph model, traversal goals, sample data,
  and expected output.

## 비목표

- No production observability backend integration.
- No OpenTelemetry ingestion.
- 새 graph-core traversal primitive는 추가하지 않는다.

## 설계

The service exposes high-level incident-response questions:

- downstream service blast radius,
- upstream impacted services,
- affected public APIs,
- alert-boundary correlation,
- service owner lookup.

동일 scenario는 abstract test class를 통해 TinkerGraph, Neo4j, Memgraph, Apache AGE, FalkorDB에서 실행된다.
TinkerGraph also validates the bundled graph-io CSV loader.

## 리스크

- Backend traversal depth semantics must remain portable.
- AGE tests still rely on the Exposed global transaction manager used by current examples.
- README diagrams must stay source-aligned when service APIs change.
