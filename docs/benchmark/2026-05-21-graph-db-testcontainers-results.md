# graph-benchmark Report

- Source: `docs/benchmark/graph-db-testcontainers-2026-05-21.json`
- Baseline: `None`
- Direction: `lower_is_better`
- Git commit: `8e16971`

| Benchmark | Params | Score | Baseline | Delta | Unit | Improved |
|---|---:|---:|---:|---:|---|---|
| `GraphDbComparisonBenchmark.batchInsertCycle` | backend=tinkergraph, sizeName=small | 5.3795 | - | - | ms/op | - |
| `GraphDbComparisonBenchmark.batchInsertCycle` | backend=neo4j, sizeName=small | 6.21737 | - | - | ms/op | - |
| `GraphDbComparisonBenchmark.batchInsertCycle` | backend=memgraph, sizeName=small | 1.96896 | - | - | ms/op | - |
| `GraphDbComparisonBenchmark.batchInsertCycle` | backend=age, sizeName=small | 21.6655 | - | - | ms/op | - |
| `GraphDbComparisonBenchmark.batchInsertCycle` | backend=falkordb, sizeName=small | 38.6596 | - | - | ms/op | - |
| `GraphDbComparisonBenchmark.countPersons` | backend=tinkergraph, sizeName=small | 0.0324826 | - | - | ms/op | - |
| `GraphDbComparisonBenchmark.countPersons` | backend=neo4j, sizeName=small | 0.809037 | - | - | ms/op | - |
| `GraphDbComparisonBenchmark.countPersons` | backend=memgraph, sizeName=small | 0.401828 | - | - | ms/op | - |
| `GraphDbComparisonBenchmark.countPersons` | backend=age, sizeName=small | 0.609781 | - | - | ms/op | - |
| `GraphDbComparisonBenchmark.countPersons` | backend=falkordb, sizeName=small | 0.193411 | - | - | ms/op | - |
| `GraphDbComparisonBenchmark.oneHopNeighbors` | backend=tinkergraph, sizeName=small | 0.00301552 | - | - | ms/op | - |
| `GraphDbComparisonBenchmark.oneHopNeighbors` | backend=neo4j, sizeName=small | 0.810796 | - | - | ms/op | - |
| `GraphDbComparisonBenchmark.oneHopNeighbors` | backend=memgraph, sizeName=small | 0.333959 | - | - | ms/op | - |
| `GraphDbComparisonBenchmark.oneHopNeighbors` | backend=age, sizeName=small | 0.932381 | - | - | ms/op | - |
| `GraphDbComparisonBenchmark.oneHopNeighbors` | backend=falkordb, sizeName=small | 0.708495 | - | - | ms/op | - |
| `GraphDbComparisonBenchmark.shortestPath` | backend=tinkergraph, sizeName=small | 0.0181862 | - | - | ms/op | - |
| `GraphDbComparisonBenchmark.shortestPath` | backend=neo4j, sizeName=small | 0.805704 | - | - | ms/op | - |
| `GraphDbComparisonBenchmark.shortestPath` | backend=memgraph, sizeName=small | 0.331374 | - | - | ms/op | - |
| `GraphDbComparisonBenchmark.shortestPath` | backend=age, sizeName=small | 1.32003 | - | - | ms/op | - |
| `GraphDbComparisonBenchmark.shortestPath` | backend=falkordb, sizeName=small | 0.28011 | - | - | ms/op | - |
