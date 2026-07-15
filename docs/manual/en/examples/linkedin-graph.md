# Professional social graph

## Problem and backend

This example turns a domain question into an inspectable path, count, ranking, or diagnostic set. It uses **TinkerGraph** to isolate modeling from container and network variance. Read [core model](../architecture/core-model.md) and [TinkerPop](../backends/tinkerpop.md) first; use the [selection guide](../backends/selection-guide.md) before production.

## Model

- Nodes: Person/Company/Skill
- Edges: KNOWS/WORKS_AT/FOLLOWS/HAS_SKILL/ENDORSES
- Key properties: name, title, company, skills, strength, role, level

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
./gradlew :linkedin-graph-examples:test --tests "io.bluetape4k.graph.examples.linkedin.TinkerGraphLinkedInGraphTest"
```

Expect `BUILD SUCCESSFUL`; connection, employer, skill, endorsement, and traversal results are non-empty. A different result points to changed fixture data, edge direction, or traversal depth.

## Reading order

1. [Schema](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/linkedin-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/linkedin/schema/LinkedInSchema.kt)
2. [Service](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/linkedin-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/linkedin/service/LinkedInGraphService.kt)
3. [Complete executable test](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/linkedin-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/linkedin/AbstractLinkedInGraphTest.kt)
4. [Build file](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/linkedin-graph-examples/build.gradle.kts)

Continue [from recommendation](./recommendation.md), then read [iam-access-graph](./iam-access-graph.md). Also see [paired APIs](../architecture/paired-apis.md), [testing](../guides/testing.md), and [operations](../guides/operations.md).

## Exercises and production gaps

Add one result-changing edge and assertion; repeat through the suspend API; then run a persistent-backend concrete test serially. Add disconnected and malformed inputs as diagnostics. This fixture does not prove throughput, clustering, authorization, tenant isolation, migration, backup, remote-driver timeout, or index quality.
