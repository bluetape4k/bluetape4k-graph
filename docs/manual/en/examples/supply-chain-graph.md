# Supply-chain impact graph

## Problem and backend

This example traces orders, routes, parts, and dependency cycles across a supply network. It uses **TinkerGraph** to isolate modeling from container and network variance. Read [core model](../architecture/core-model.md) and [TinkerPop](../backends/tinkerpop.md) first; use the [selection guide](../backends/selection-guide.md) before production.

## Model

- Nodes: Supplier/Part/Warehouse/Route/Carrier/CustomerOrder
- Edges: SUPPLIES/REQUIRED_BY/STOCKED_AT/USES_ROUTE/DELIVERS_TO/OPERATES_ROUTE/ALTERNATE_PART
- Key properties: supplierId, partId, routeId, carrierId, orderId, criticality, status

## Prerequisites and release boundary

Use JDK 21, commit `72c0256e2e1cf61101d29852210e3c827ca93bc0`, and the checked-in wrapper. Examples are not published; run this release fixture as a Gradle project from the release source checkout. In a consumer application, select only `bluetape4k-dependencies:<ecosystem-version>` and add the required graph module without an individual version.

## Run and observe

```bash
./gradlew :supply-chain-graph-examples:test --tests "io.bluetape4k.graph.examples.supplychain.TinkerGraphSupplyChainImpactTest"
```

The tests assert visibility of `order-1001`, `route-air-express`, and `gps-module`, and detect the battery dependency cycle. A failure points to a missing supply relation, reversed dependency, or cycle-detection boundary.

## Reading order

1. [Schema](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/supply-chain-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/supplychain/schema/SupplyChainGraphSchema.kt)
2. [Service](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/supply-chain-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/supplychain/service/SupplyChainImpactService.kt)
3. [Shared executable contract](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/supply-chain-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/supplychain/AbstractSupplyChainImpactTest.kt)
4. [Concrete TinkerGraph test](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/supply-chain-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/supplychain/SupplyChainBackendTests.kt)
5. [Build file](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/supply-chain-graph-examples/build.gradle.kts)

Continue [from data-lineage](./data-lineage.md), then read [ktor-graph](./ktor-graph.md). Also see [paired APIs](../architecture/paired-apis.md), [testing](../guides/testing.md), and [operations](../guides/operations.md).

## Exercises and production gaps

Add one result-changing edge and assertion; repeat through the suspend API; then run a persistent-backend concrete test serially. Add disconnected and malformed inputs as diagnostics. This fixture does not prove throughput, clustering, authorization, tenant isolation, migration, backup, remote-driver timeout, or index quality.
