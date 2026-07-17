# Code graph

## Problem and backend

This example models modules, declarations, and calls so dependency and call paths can be inspected directly. It uses **TinkerGraph** to isolate modeling from container and network variance. Read [core model](../architecture/core-model.md) and [TinkerPop](../backends/tinkerpop.md) first; use the [selection guide](../backends/selection-guide.md) before production.

## Model

- Nodes: Module/Class/Function
- Edges: DEPENDS_ON/IMPORTS/EXTENDS/IMPLEMENTS/CALLS/BELONGS_TO
- Key properties: name, qualifiedName, signature, dependencyType, callCount

## Prerequisites and release boundary

Use JDK 21, commit `3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907`, and the checked-in wrapper. Examples are not published; run this release fixture as a Gradle project from the release source checkout. In a consumer application, select only `bluetape4k-dependencies:<ecosystem-version>` and add the required graph module without an individual version.

## Run and observe

```bash
./gradlew :code-graph-examples:test --tests "io.bluetape4k.graph.examples.code.TinkerGraphCodeGraphTest"
```

The test asserts that the call path contains more than one vertex and that a disconnected pair has no path. A failure usually means the fixture edges, their direction, or the traversal depth no longer matches the code-graph model.

## Reading order

1. [Schema](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/code-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/code/schema/CodeGraphSchema.kt)
2. [Service](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/code-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/code/service/CodeGraphService.kt)
3. [Shared executable contract](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/AbstractCodeGraphTest.kt)
4. [Concrete TinkerGraph test](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/TinkerGraphCodeGraphTest.kt)
5. [Build file](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/code-graph-examples/build.gradle.kts)

Continue [from ktor-graph](./ktor-graph.md), then read [knowledge-graph](./knowledge-graph.md). Also see [paired APIs](../architecture/paired-apis.md), [testing](../guides/testing.md), and [operations](../guides/operations.md).

## Exercises and production gaps

Add one result-changing edge and assertion; repeat through the suspend API; then run a persistent-backend concrete test serially. Add disconnected and malformed inputs as diagnostics. This fixture does not prove throughput, clustering, authorization, tenant isolation, migration, backup, remote-driver timeout, or index quality.

<!-- release-readme-diagrams:start -->
## Release diagrams {#release-diagrams}

These diagrams are copied byte-for-byte from README assets in the `0.5.1` release tag. They describe this manual's released structure and runtime flows, not later Snapshot changes. Select a preview to open the SVG source.

### code graph examples architecture

[![code graph examples architecture](../../assets/readme-diagrams/examples-code-graph-examples-architecture-01.png)](../../assets/readme-diagrams/examples-code-graph-examples-architecture-01.svg)

_Release README: [`examples/code-graph-examples/README.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/code-graph-examples/README.md)_

### code graph examples data flow

[![code graph examples data flow](../../assets/readme-diagrams/examples-code-graph-examples-data-flow-03.png)](../../assets/readme-diagrams/examples-code-graph-examples-data-flow-03.svg)

_Release README: [`examples/code-graph-examples/README.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/code-graph-examples/README.md)_

### code graph examples ERD

[![code graph examples ERD](../../assets/readme-diagrams/examples-code-graph-examples-erd-02.png)](../../assets/readme-diagrams/examples-code-graph-examples-erd-02.svg)

_Release README: [`examples/code-graph-examples/README.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/code-graph-examples/README.md)_

<!-- release-readme-diagrams:end -->
