# Observability incident graph

## Problem and backend

This example correlates dependencies, owners, and alerts instead of treating telemetry as unrelated records. It uses **TinkerGraph** to isolate modeling from container and network variance. Read [core model](../architecture/core-model.md) and [TinkerPop](../backends/tinkerpop.md) first; use the [selection guide](../backends/selection-guide.md) before production.

## Model

- Nodes: Service/Api/Team/Alert/Incident
- Edges: DEPENDS_ON/OWNED_BY/ALERTS_ON/ROOT_CAUSE
- Key properties: serviceId, apiId, teamId, alertId, incidentId, severity, status

## Prerequisites and release boundary

Use JDK 21, commit `72c0256e2e1cf61101d29852210e3c827ca93bc0`, and the checked-in wrapper. Examples are not published; run this release fixture as a Gradle project from the release source checkout. In a consumer application, select only `bluetape4k-dependencies:<ecosystem-version>` and add the required graph module without an individual version.

## Run and observe

```bash
./gradlew :observability-graph-examples:test --tests "io.bluetape4k.graph.examples.observability.TinkerGraphObservabilityIncidentTest"
```

The tests assert downstream dependencies, upstream impacted services and public APIs, the shared alert boundary, and owning teams. A failure should be read as a broken telemetry relation or correlation path, not as proof that the whole observability model failed.

## Reading order

1. [Schema](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/observability-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/observability/schema/ObservabilityGraphSchema.kt)
2. [Service](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/observability-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/observability/service/ObservabilityIncidentService.kt)
3. [Shared executable contract](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/observability-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/observability/AbstractObservabilityIncidentTest.kt)
4. [Concrete TinkerGraph test](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/observability-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/observability/ObservabilityBackendTests.kt)
5. [Build file](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/observability-graph-examples/build.gradle.kts)

Continue [from network-topology](./network-topology.md), then read [data-lineage](./data-lineage.md). Also see [paired APIs](../architecture/paired-apis.md), [testing](../guides/testing.md), and [operations](../guides/operations.md).

## Exercises and production gaps

Add one result-changing edge and assertion; repeat through the suspend API; then run a persistent-backend concrete test serially. Add disconnected and malformed inputs as diagnostics. This fixture does not prove throughput, clustering, authorization, tenant isolation, migration, backup, remote-driver timeout, or index quality.

<!-- release-readme-diagrams:start -->
## Release diagrams {#release-diagrams}

These diagrams are loaded directly from README assets published with the `0.6.0` release and pinned to its immutable commit. They describe this manual's released structure and runtime flows, not later Snapshot changes. Select a preview to open the SVG at the same release commit.

### observability graph examples architecture

[![observability graph examples architecture](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-observability-graph-examples-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-observability-graph-examples-architecture-01.svg)

_Release README: [`examples/observability-graph-examples/README.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/observability-graph-examples/README.md)_

### observability graph examples data flow

[![observability graph examples data flow](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-observability-graph-examples-data-flow-03.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-observability-graph-examples-data-flow-03.svg)

_Release README: [`examples/observability-graph-examples/README.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/observability-graph-examples/README.md)_

### observability graph examples ERD

[![observability graph examples ERD](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-observability-graph-examples-erd-02.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-observability-graph-examples-erd-02.svg)

_Release README: [`examples/observability-graph-examples/README.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/observability-graph-examples/README.md)_

<!-- release-readme-diagrams:end -->
