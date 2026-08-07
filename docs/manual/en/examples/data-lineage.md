# Data lineage graph

## Problem and backend

This example follows upstream sources and downstream impact across datasets, jobs, and reports. It uses **TinkerGraph** to isolate modeling from container and network variance. Read [core model](../architecture/core-model.md) and [TinkerPop](../backends/tinkerpop.md) first; use the [selection guide](../backends/selection-guide.md) before production.

## Model

- Nodes: Dataset/Table/Column/PipelineJob/Dashboard/Owner/QualityCheck
- Edges: CONTAINS_TABLE/CONTAINS_COLUMN/INPUT_TO_JOB/OUTPUTS_TABLE/FEEDS_DASHBOARD/OWNS_JOB/VALIDATES_COLUMN
- Key properties: datasetId, tableId, columnId, jobId, dashboardId, ownerId, checkId

## Prerequisites and release boundary

Use JDK 21, commit `72c0256e2e1cf61101d29852210e3c827ca93bc0`, and the checked-in wrapper. Examples are not published; run this release fixture as a Gradle project from the release source checkout. In a consumer application, select only `bluetape4k-dependencies:<ecosystem-version>` and add the required graph module without an individual version.

## Run and observe

```bash
./gradlew :data-lineage-examples:test --tests "io.bluetape4k.graph.examples.datalineage.TinkerGraphDataLineageImpactTest"
```

The test asserts that downstream impact reaches `exec-revenue` and `ops-quality` and that upstream traversal finds the expected source tables. A failure points first to lineage direction, missing transformation edges, or a changed traversal bound.

## Reading order

1. [Schema](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/data-lineage-examples/src/main/kotlin/io/bluetape4k/graph/examples/datalineage/schema/DataLineageGraphSchema.kt)
2. [Service](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/data-lineage-examples/src/main/kotlin/io/bluetape4k/graph/examples/datalineage/service/DataLineageImpactService.kt)
3. [Shared executable contract](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/data-lineage-examples/src/test/kotlin/io/bluetape4k/graph/examples/datalineage/AbstractDataLineageImpactTest.kt)
4. [Concrete TinkerGraph test](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/data-lineage-examples/src/test/kotlin/io/bluetape4k/graph/examples/datalineage/DataLineageBackendTests.kt)
5. [Build file](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/examples/data-lineage-examples/build.gradle.kts)

Continue [from observability-graph](./observability-graph.md), then read [supply-chain-graph](./supply-chain-graph.md). Also see [paired APIs](../architecture/paired-apis.md), [testing](../guides/testing.md), and [operations](../guides/operations.md).

## Exercises and production gaps

Add one result-changing edge and assertion; repeat through the suspend API; then run a persistent-backend concrete test serially. Add disconnected and malformed inputs as diagnostics. This fixture does not prove throughput, clustering, authorization, tenant isolation, migration, backup, remote-driver timeout, or index quality.
