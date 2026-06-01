# supply-chain-graph-examples

> 🇺🇸 [English](README.md)

Supplier, part, warehouse, route, carrier, customer order를 작은 supply-chain graph로 모델링하는 예제입니다.
복잡한 optimization solver 없이 deterministic impact analysis와 alternate candidate discovery를 보여줍니다.

## 예제 시나리오

차단된 ocean route와 sole-source GPS module이 customer order를 지연시킬 수 있습니다. 샘플 그래프는 supplier, part,
route 장애가 어떤 order에 영향을 주는지, 실패 route를 대체할 active route가 무엇인지, 어떤 part가 bottleneck이거나
substitution cycle에 속하는지 답합니다.

## Graph Model

| 요소 | Label | 주요 속성 | 목적 |
|---|---|---|---|
| Supplier | `Supplier` | `supplierId`, `region`, `status` | part의 upstream source입니다. |
| Part | `Part` | `partId`, `criticality`, `status` | bill-of-material component입니다. |
| Warehouse | `Warehouse` | `warehouseId`, `region`, `status` | fulfillment stock location입니다. |
| Route | `Route` | `routeId`, `region`, `status` | order로 향하는 delivery path입니다. |
| Carrier | `Carrier` | `carrierId`, `region`, `status` | route 운영자입니다. |
| Customer order | `CustomerOrder` | `orderId`, `region`, `status` | demand endpoint입니다. |

## Traversal Goals

| 질문 | API |
|---|---|
| supplier 장애가 어떤 order에 영향을 주는가? | `impactedOrdersBySupplier(supplierId)` |
| 실패한 part를 요구하는 order는 무엇인가? | `impactedOrdersByPart(partId)` |
| 실패한 route를 쓰는 order는 무엇인가? | `impactedOrdersByRoute(routeId)` |
| order 기준 실패 route를 대체할 active route는 무엇인가? | `alternateRoutesForOrder(orderId, failedRouteId)` |
| sole-source bottleneck part는 무엇인가? | `bottleneckParts()` |
| 어떤 part substitution이 cycle을 이루는가? | `partSubstitutionCycles()` |

## Sample Dataset

모듈은 `src/main/resources/sample-data/supply-chain/` 아래에 graph-io CSV fixture를 포함합니다.

| 파일 | 내용 |
|---|---|
| `vertices.csv` | supplier, part, warehouse, route, carrier, order. |
| `edges.csv` | supply, requirement, stock, route, delivery, carrier, alternate-part edge. |

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

## 테스트 실행

```bash
./gradlew :supply-chain-graph-examples:test
```

첫 slice는 TinkerGraph smoke coverage와 graph-io CSV loader test를 사용합니다.
