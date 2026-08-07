# Network topology graph

## Problem and backend

This example separates reachability, service impact, and isolated components in a network topology. It uses **TinkerGraph** to isolate modeling from container and network variance. Read [core model](../architecture/core-model.md) and [TinkerPop](../backends/tinkerpop.md) first; use the [selection guide](../backends/selection-guide.md) before production.

## Model

- Nodes: Site/Device/Segment/Service
- Edges: CONTAINS_DEVICE/CONNECTED_TO/MEMBER_OF_SEGMENT/HOSTS_SERVICE
- Key properties: siteId, deviceId, segmentId, serviceId, cidr, tier, status

## Prerequisites and release boundary

Use JDK 21, commit `72c0256e2e1cf61101d29852210e3c827ca93bc0`, and the checked-in wrapper. Examples are not published; run this release fixture as a Gradle project from the release source checkout. In a consumer application, select only `bluetape4k-dependencies:<ecosystem-version>` and add the required graph module without an individual version.

## Run and observe

```bash
./gradlew :network-topology-examples:test --tests "io.bluetape4k.graph.examples.networktopology.TinkerGraphNetworkTopologyImpactTest"
```

The test asserts that reachable devices, affected services, and isolated components match the topology fixture. A failure usually means a connectivity edge changed direction, an isolation edge is missing, or the impact traversal exceeded its intended boundary.

## Reading order

1. [Schema](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/network-topology-examples/src/main/kotlin/io/bluetape4k/graph/examples/networktopology/schema/NetworkTopologyGraphSchema.kt)
2. [Service](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/network-topology-examples/src/main/kotlin/io/bluetape4k/graph/examples/networktopology/service/NetworkTopologyImpactService.kt)
3. [Shared executable contract](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/network-topology-examples/src/test/kotlin/io/bluetape4k/graph/examples/networktopology/AbstractNetworkTopologyImpactTest.kt)
4. [Concrete TinkerGraph test](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/network-topology-examples/src/test/kotlin/io/bluetape4k/graph/examples/networktopology/NetworkTopologyBackendTests.kt)
5. [Build file](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/network-topology-examples/build.gradle.kts)

Continue [from security-attack-path](./security-attack-path.md), then read [observability-graph](./observability-graph.md). Also see [paired APIs](../architecture/paired-apis.md), [testing](../guides/testing.md), and [operations](../guides/operations.md).

## Exercises and production gaps

Add one result-changing edge and assertion; repeat through the suspend API; then run a persistent-backend concrete test serially. Add disconnected and malformed inputs as diagnostics. This fixture does not prove throughput, clustering, authorization, tenant isolation, migration, backup, remote-driver timeout, or index quality.
