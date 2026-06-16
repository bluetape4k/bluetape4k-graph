# network-topology-examples

> 🇰🇷 [한국어 문서](README.ko.md)

This example models a compact network topology graph for sites, devices, routed segments, and reachable services. It
demonstrates backend-independent traversal for path finding and failure-impact analysis without building a network
simulator.

## Scenario

The sample graph connects a core router to two edge routers, redundant access switches, an IoT segment, and an isolated
lab segment. It answers which path reaches a service, which services become unreachable after a failed device or link,
which segments are isolated from the core, and whether redundant route candidates exist.

## Architecture

![network topology examples architecture](../../docs/images/readme-diagrams/examples-network-topology-examples-architecture-01.png)

## Graph Model

| Element | Label | Key properties | Purpose |
|---|---|---|---|
| Site | `Site` | `siteId`, `region`, `status` | Groups devices by facility or region. |
| Device | `Device` | `deviceId`, `role`, `status` | Routers, switches, and service attachment points. |
| Segment | `Segment` | `segmentId`, `cidr`, `status` | Routed or switched network zones. |
| Service | `Service` | `serviceId`, `tier`, `status` | Business service reachable through a host device. |

## Traversal Goals

| Question | API |
|---|---|
| What is the shortest active path between two devices? | `shortestDevicePath(sourceDeviceId, targetDeviceId)` |
| What path reaches a service from the core? | `shortestPathToService(serviceId)` |
| Which services are newly impacted by a failed device? | `impactedServicesByFailedDevice(failedDeviceId)` |
| Which services are newly impacted by a failed link? | `impactedServicesByFailedLink(failedLinkId)` |
| Which segments are isolated from the core? | `isolatedSegments()` |
| Which redundant paths connect two devices? | `redundantDevicePaths(sourceDeviceId, targetDeviceId)` |

## Sample Dataset

The module bundles graph-io CSV fixtures under `src/main/resources/sample-data/network-topology/`.

| File | Contents |
|---|---|
| `vertices.csv` | sites, routers, switches, segments, and services. |
| `edges.csv` | site membership, device links, segment membership, and service hosting edges. |

```kotlin
val ops = TinkerGraphOperations()
val service = NetworkTopologyImpactService(ops)
service.initialize()

NetworkTopologySampleDatasetLoader.importCsv(ops)
val checkoutPath = service.shortestPathToService("svc-checkout")
val impacted = service.impactedServicesByFailedLink("link-b-iot")
```

## Expected Output

| Query | Expected IDs |
|---|---|
| `shortestDevicePath("router-core", "switch-access-a")` | `router-core`, `router-edge-a`, `switch-access-a` |
| `shortestPathToService("svc-checkout")` | `router-core`, `router-edge-a`, `switch-access-a`, `svc-checkout` |
| `impactedServicesByFailedLink("link-b-iot")` | `svc-cameras` |
| `isolatedSegments()` | `seg-lab` |
| `redundantDevicePaths("router-core", "switch-access-a")` | direct edge-A path plus edge-B/access-B path |

## Running Tests

```bash
./gradlew :network-topology-examples:test
```

The first slice uses TinkerGraph smoke coverage and graph-io CSV loader tests.
