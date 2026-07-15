# Knowledge graph

## Problem and backend

This example turns a domain question into an inspectable path, count, ranking, or diagnostic set. It uses **TinkerGraph** to isolate modeling from container and network variance. Read [core model](../architecture/core-model.md) and [TinkerPop](../backends/tinkerpop.md) first; use the [selection guide](../backends/selection-guide.md) before production.

## Model

- Nodes: Document/Entity/Concept
- Edges: MENTIONS/RELATED_TO/IS_A
- Key properties: documentId, entityId, conceptId, confidence, relationType

## Prerequisites and release boundary

Use JDK 21, commit `3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907`, and the checked-in wrapper. Examples are not published; this is an explicit release-fixture boundary. Consumers of published modules import the BOM and omit module versions.

```kotlin
dependencies {
    implementation(platform("io.bluetape4k:bluetape4k-graph-bom:0.5.1"))
    implementation("io.bluetape4k:bluetape4k-graph-core")
}
```

## Run and observe

```bash
./gradlew :knowledge-graph-examples:test --tests "io.bluetape4k.graph.examples.knowledge.TinkerGraphKnowledgeGraphTest"
```

Expect `BUILD SUCCESSFUL`; related entities and a document-to-concept explanation path are returned. A different result points to changed fixture data, edge direction, or traversal depth.

## Reading order

1. [Schema](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/knowledge-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/knowledge/schema/KnowledgeGraphSchema.kt)
2. [Service](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/knowledge-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/knowledge/service/KnowledgeGraphService.kt)
3. [Complete executable test](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/knowledge-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/knowledge/AbstractKnowledgeGraphTest.kt)
4. [Build file](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/knowledge-graph-examples/build.gradle.kts)

Continue [from code-graph](./code-graph.md), then read [recommendation](./recommendation.md). Also see [paired APIs](../architecture/paired-apis.md), [testing](../guides/testing.md), and [operations](../guides/operations.md).

## Exercises and production gaps

Add one result-changing edge and assertion; repeat through the suspend API; then run a persistent-backend concrete test serially. Add disconnected and malformed inputs as diagnostics. This fixture does not prove throughput, clustering, authorization, tenant isolation, migration, backup, remote-driver timeout, or index quality.
