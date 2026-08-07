# Fraud detection graph

## Problem and backend

This example combines transfer paths, cycles, and risk signals to explain why an account is suspicious. It uses **TinkerGraph** to isolate modeling from container and network variance. Read [core model](../architecture/core-model.md) and [TinkerPop](../backends/tinkerpop.md) first; use the [selection guide](../backends/selection-guide.md) before production.

## Model

- Nodes: Account
- Edges: TRANSFERRED_TO
- Key properties: accountId, ownerName, riskTier, amount, occurredAt

## Prerequisites and release boundary

Use JDK 21, commit `72c0256e2e1cf61101d29852210e3c827ca93bc0`, and the checked-in wrapper. Examples are not published; run this release fixture as a Gradle project from the release source checkout. In a consumer application, select only `bluetape4k-dependencies:<ecosystem-version>` and add the required graph module without an individual version.

## Run and observe

```bash
./gradlew :fraud-detection-examples:test --tests "io.bluetape4k.graph.examples.fraud.TinkerGraphFraudDetectionTest"
```

The test asserts that `acct-bob` is scored, the transfer cycle closes, and sink analysis reaches `acct-sink`. A failure usually indicates changed transfer direction, risk weighting, or cycle/depth limits rather than a Gradle-output problem.

## Reading order

1. [Schema](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/fraud-detection-examples/src/main/kotlin/io/bluetape4k/graph/examples/fraud/schema/FraudDetectionSchema.kt)
2. [Service](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/fraud-detection-examples/src/main/kotlin/io/bluetape4k/graph/examples/fraud/service/FraudDetectionService.kt)
3. [Shared executable contract](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/fraud-detection-examples/src/test/kotlin/io/bluetape4k/graph/examples/fraud/AbstractFraudDetectionTest.kt)
4. [Concrete TinkerGraph test](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/fraud-detection-examples/src/test/kotlin/io/bluetape4k/graph/examples/fraud/FraudDetectionBackendTests.kt)
5. [Build file](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/fraud-detection-examples/build.gradle.kts)

Continue [from iam-access-graph](./iam-access-graph.md), then read [security-attack-path](./security-attack-path.md). Also see [paired APIs](../architecture/paired-apis.md), [testing](../guides/testing.md), and [operations](../guides/operations.md).

## Exercises and production gaps

Add one result-changing edge and assertion; repeat through the suspend API; then run a persistent-backend concrete test serially. Add disconnected and malformed inputs as diagnostics. This fixture does not prove throughput, clustering, authorization, tenant isolation, migration, backup, remote-driver timeout, or index quality.

<!-- release-readme-diagrams:start -->
## Release diagrams {#release-diagrams}

These diagrams are loaded directly from README assets published with the `0.6.0` release and pinned to its immutable commit. They describe this manual's released structure and runtime flows, not later Snapshot changes. Select a preview to open the SVG at the same release commit.

### fraud detection examples Architecture diagram

[![fraud detection examples Architecture diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-fraud-detection-examples-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-fraud-detection-examples-architecture-01.svg)

_Release README: [`examples/fraud-detection-examples/README.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/fraud-detection-examples/README.md)_

### Domain UML diagram

[![Domain UML diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-fraud-detection-examples-class-02.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-fraud-detection-examples-class-02.svg)

_Release README: [`examples/fraud-detection-examples/README.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/fraud-detection-examples/README.md)_

### Analysis Flow diagram

[![Analysis Flow diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-fraud-detection-examples-sequence-03.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/examples-fraud-detection-examples-sequence-03.svg)

_Release README: [`examples/fraud-detection-examples/README.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/fraud-detection-examples/README.md)_

<!-- release-readme-diagrams:end -->
