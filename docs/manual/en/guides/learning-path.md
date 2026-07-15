# Learning path

## Stage 1: model one relationship

Run [Getting started](../getting-started.md) with TinkerGraph. Learn opaque IDs, directed edges, and returned snapshots. Observe generated IDs and neighbor direction. If no neighbor appears, inspect `startId`/`endId`, `Direction`, and label filters. Then read [`GraphVertexTest.kt`](../../../../graph/graph-core/src/test/kotlin/io/bluetape4k/graph/model/GraphVertexTest.kt).

## Stage 2: read a domain example

Open the [code graph schema](../../../../examples/code-graph-examples/src/main/kotlin/io/bluetape4k/graph/examples/code/schema/CodeGraphSchema.kt), then follow [`AbstractCodeGraphTest.kt`](../../../../examples/code-graph-examples/src/test/kotlin/io/bluetape4k/graph/examples/code/AbstractCodeGraphTest.kt). Learn schema declarations, writes, traversal, and the assertions that define useful output. Run one concrete backend test and compare IDs/query logs.

## Stage 3: add write semantics

Exercise merge, batch, and `transaction {}`. Observe duplicate handling, output order, commit, and rollback. Diagnose partial writes by comparing pre/post counts. Use [`GraphBatchOperationsTest.kt`](../../../../graph/graph-core/src/test/kotlin/io/bluetape4k/graph/repository/GraphBatchOperationsTest.kt) as the contract map.

## Stage 4: change backend

Use the [selection guide](../backends/selection-guide.md). Run the same example against two candidates and record schema, property types, transaction and traversal differences. A compile success is not semantic proof.

## Stage 5: transfer and operate

Round-trip a small dataset with [graph-io](../graph-io/formats.md), inject malformed/truncated input, then establish metrics and recovery steps from [Operations](operations.md). Only then benchmark the representative workload.
