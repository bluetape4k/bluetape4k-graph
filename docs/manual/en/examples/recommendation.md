# Recommendation graph

## Problem and backend

This example turns user-item interactions into candidates and a deterministic ranking that tests can explain. It uses **TinkerGraph** to isolate modeling from container and network variance. Read [core model](../architecture/core-model.md) and [TinkerPop](../backends/tinkerpop.md) first; use the [selection guide](../backends/selection-guide.md) before production.

## Model

- Nodes: User/Product
- Edges: PURCHASED/FOLLOWS
- Key properties: userId, productId, category, quantity, purchasedAt

## Prerequisites and release boundary

Use JDK 21, commit `3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907`, and the checked-in wrapper. Examples are not published; run this release fixture as a Gradle project from the release source checkout. In a consumer application, select only `bluetape4k-dependencies:<ecosystem-version>` and add the required graph module without an individual version.

## Run and observe

```bash
./gradlew :recommendation-examples:test --tests "io.bluetape4k.graph.examples.recommendation.TinkerGraphRecommendationTest"
```

The tests assert that `p-tripod` and `u-carol` are recommended and that `p-camera` remains in the top three. A failure usually reflects changed interaction weights, candidate filtering, or ranking order.

## Reading order

1. [Schema](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/recommendation-examples/src/main/kotlin/io/bluetape4k/graph/examples/recommendation/schema/RecommendationSchema.kt)
2. [Service](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/recommendation-examples/src/main/kotlin/io/bluetape4k/graph/examples/recommendation/service/RecommendationService.kt)
3. [Dataset loader contract](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/recommendation-examples/src/test/kotlin/io/bluetape4k/graph/examples/recommendation/RecommendationSampleDatasetLoaderTest.kt)
4. [Concrete TinkerGraph test](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/recommendation-examples/src/test/kotlin/io/bluetape4k/graph/examples/recommendation/RecommendationBackendTests.kt)
5. [Build file](https://github.com/bluetape4k/bluetape4k-graph/blob/3e0fa7cb9e3bc70c2743aeebda2487f3e45e4907/examples/recommendation-examples/build.gradle.kts)

Continue [from knowledge-graph](./knowledge-graph.md), then read [linkedin-graph](./linkedin-graph.md). Also see [paired APIs](../architecture/paired-apis.md), [testing](../guides/testing.md), and [operations](../guides/operations.md).

## Exercises and production gaps

Add one result-changing edge and assertion; repeat through the suspend API; then run a persistent-backend concrete test serially. Add disconnected and malformed inputs as diagnostics. This fixture does not prove throughput, clustering, authorization, tenant isolation, migration, backup, remote-driver timeout, or index quality.
