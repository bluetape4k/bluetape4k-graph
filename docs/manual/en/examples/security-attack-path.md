# Security attack paths

## Problem and backend

This example makes exploit, trust, privilege, and blocking transitions visible along an attack path. It uses **TinkerGraph** to isolate modeling from container and network variance. Read [core model](../architecture/core-model.md) and [TinkerPop](../backends/tinkerpop.md) first; use the [selection guide](../backends/selection-guide.md) before production.

## Model

- Nodes: EntryAsset/Host/Principal/Credential/Vulnerability/Permission
- Edges: CAN_REACH/EXPLOITS/COMPROMISES/RUNS_AS/HAS_CREDENTIAL/GRANTS_ACCESS/HAS_PERMISSION/CONTROLS_ASSET
- Key properties: assetId, hostId, principalId, vulnerabilityId, severity, privilege, status

## Prerequisites and release boundary

Use JDK 21, commit `72c0256e2e1cf61101d29852210e3c827ca93bc0`, and the checked-in wrapper. Examples are not published; run this release fixture as a Gradle project from the release source checkout. In a consumer application, select only `bluetape4k-dependencies:<ecosystem-version>` and add the required graph module without an individual version.

## Run and observe

```bash
./gradlew :security-attack-path-examples:test --tests "io.bluetape4k.graph.examples.securityattack.TinkerGraphSecurityAttackPathTest"
```

The test asserts that the escalation path includes `web-service`, `ci-admin-token`, and `domain-admin`, while `customer-db` remains blocked. A failure means an exploit or trust edge, privilege transition, or blocking rule changed.

## Reading order

1. [Schema](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/security-attack-path-examples/src/main/kotlin/io/bluetape4k/graph/examples/securityattack/schema/SecurityAttackPathGraphSchema.kt)
2. [Service](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/security-attack-path-examples/src/main/kotlin/io/bluetape4k/graph/examples/securityattack/service/SecurityAttackPathService.kt)
3. [Shared executable contract](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/security-attack-path-examples/src/test/kotlin/io/bluetape4k/graph/examples/securityattack/AbstractSecurityAttackPathTest.kt)
4. [Concrete TinkerGraph test](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/security-attack-path-examples/src/test/kotlin/io/bluetape4k/graph/examples/securityattack/SecurityAttackPathBackendTests.kt)
5. [Build file](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/security-attack-path-examples/build.gradle.kts)

Continue [from fraud-detection](./fraud-detection.md), then read [network-topology](./network-topology.md). Also see [paired APIs](../architecture/paired-apis.md), [testing](../guides/testing.md), and [operations](../guides/operations.md).

## Exercises and production gaps

Add one result-changing edge and assertion; repeat through the suspend API; then run a persistent-backend concrete test serially. Add disconnected and malformed inputs as diagnostics. This fixture does not prove throughput, clustering, authorization, tenant isolation, migration, backup, remote-driver timeout, or index quality.
