# Getting started

## 1. Select one ecosystem version

Consumers choose a `bluetape4k-dependencies` version. Do not pin a standalone graph module or `bluetape4k-graph-bom` version.

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k:bluetape4k-dependencies:<ecosystem-version>"))
    implementation("io.github.bluetape4k:bluetape4k-graph-tinkerpop")
}
```

The unversioned coordinate is intentional: the ecosystem BOM keeps graph and its shared Bluetape libraries aligned. See the release module declaration in [`graph/graph-tinkerpop/build.gradle.kts`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-tinkerpop/build.gradle.kts).

## 2. Run a local graph

```kotlin
TinkerGraphOperations().use { ops ->
    val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
    val bob = ops.createVertex("Person", mapOf("name" to "Bob"))
    ops.createEdge(alice.id, bob.id, "KNOWS")

    val neighbors = ops.neighbors(alice.id)
    check(neighbors.single().id == bob.id)
}
```

The facade composition is visible in [`GraphOperations.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphOperations.kt), while the in-memory implementation and its behavior tests live in [`TinkerGraphOperations.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-tinkerpop/src/main/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphOperations.kt) and [`TinkerGraphOperationsTest.kt`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphOperationsTest.kt).

## 3. Observe and diagnose

Inspect returned `GraphElementId` values rather than assuming a numeric ID. If the neighbor list is empty, verify edge direction, label filters, and `maxDepth` before blaming the backend. If a transaction extension throws `UnsupportedOperationException`, the selected implementation lacks that capability; it never silently falls back to auto-commit. Continue with [paired APIs](architecture/paired-apis.md) and [backend selection](backends/selection-guide.md).
