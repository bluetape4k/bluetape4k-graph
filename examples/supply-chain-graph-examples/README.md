# supply-chain-graph-examples

> 🇰🇷 [한국어 문서](README.ko.md)

This example models a small supply-chain graph for suppliers, parts, warehouses, routes, carriers, and customer orders.
It demonstrates deterministic impact analysis and alternate candidate discovery without introducing an optimization
solver.

## Scenario

A blocked ocean route and a sole-source GPS module can delay customer orders. The sample graph answers which orders are
impacted by a supplier, part, or route failure, which active routes can replace a failed route, and which parts are
bottlenecks or part of substitution cycles.

## Graph Model

| Element | Label | Key properties | Purpose |
|---|---|---|---|
| Supplier | `Supplier` | `supplierId`, `region`, `status` | Upstream source for parts. |
| Part | `Part` | `partId`, `criticality`, `status` | Bill-of-material component. |
| Warehouse | `Warehouse` | `warehouseId`, `region`, `status` | Fulfillment stock location. |
| Route | `Route` | `routeId`, `region`, `status` | Delivery path to orders. |
| Carrier | `Carrier` | `carrierId`, `region`, `status` | Operator for routes. |
| Customer order | `CustomerOrder` | `orderId`, `region`, `status` | Demand endpoint. |

## Traversal Goals

| Question | API |
|---|---|
| Which orders are impacted by a supplier failure? | `impactedOrdersBySupplier(supplierId)` |
| Which orders require a failed part? | `impactedOrdersByPart(partId)` |
| Which orders use a failed route? | `impactedOrdersByRoute(routeId)` |
| Which active route can replace a failed route for an order? | `alternateRoutesForOrder(orderId, failedRouteId)` |
| Which parts are sole-source bottlenecks? | `bottleneckParts()` |
| Which part substitutions form cycles? | `partSubstitutionCycles()` |

## Sample Dataset

The module bundles graph-io CSV fixtures under `src/main/resources/sample-data/supply-chain/`.

| File | Contents |
|---|---|
| `vertices.csv` | suppliers, parts, warehouses, routes, carriers, and orders. |
| `edges.csv` | supply, requirement, stock, route, delivery, carrier, and alternate-part edges. |

```kotlin
val ops = TinkerGraphOperations()
val service = SupplyChainImpactService(ops)
service.initialize()

SupplyChainSampleDatasetLoader.importCsv(ops)
val impacted = service.impactedOrdersBySupplier("supplier-alpha")
val alternates = service.alternateRoutesForOrder("order-1001", failedRouteId = "route-pacific")
```

## Expected Output

| Query | Expected IDs |
|---|---|
| `impactedOrdersBySupplier("supplier-alpha")` | `order-1001` |
| `impactedOrdersByPart("gps-module")` | `order-1001`, `order-1002` |
| `impactedOrdersByRoute("route-pacific")` | `order-1001` |
| `alternateRoutesForOrder("order-1001", "route-pacific")` | `route-air-express` |
| `bottleneckParts()` | `gps-module` |

## Running Tests

```bash
./gradlew :supply-chain-graph-examples:test
```

The first slice uses TinkerGraph smoke coverage and graph-io CSV loader tests.
