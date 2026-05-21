# Graph DB Medium Testcontainers Benchmark Results

Run date: 2026-05-21

Command:

```bash
java -jar benchmark/graph-benchmark/build/benchmarks/main/jars/graph-benchmark-main-jmh-0.3.1-SNAPSHOT-JMH.jar \
  '.*GraphDbComparisonBenchmark.*' \
  -wi 3 -i 5 -r 3s -w 2s -f 1 \
  -p backend=tinkergraph,neo4j,memgraph,age,falkordb \
  -p sizeName=medium \
  -rf json \
  -rff docs/benchmark/graph-db-medium-testcontainers-2026-05-21.json
```

FalkorDB medium rows were rerun after switching the benchmark driver to a 60 second Jedis read timeout. The default `jfalkordb` driver timed out on the medium fixture during the first full matrix run.

All values are `ms/op`; lower is better.

| Operation | TinkerGraph | Neo4j | Memgraph | AGE | FalkorDB |
|---|---:|---:|---:|---:|---:|
| `batchInsertCycle` | 44.967 | 15.690 | **11.364** | 309.090 | 1929.180 |
| `countPersons` | 0.308 | 0.528 | 1.341 | 2.176 | **0.197** |
| `oneHopNeighbors` | **0.003** | 0.665 | 0.308 | 10.175 | 1.046 |
| `shortestPath` | **0.019** | 0.700 | 0.386 | 12.420 | 0.512 |

## Detailed Rows

| Benchmark | Parameters | Score | Error | Unit |
|---|---|---:|---:|---|
| `GraphDbComparisonBenchmark.batchInsertCycle` | backend=tinkergraph, sizeName=medium | 44.967 | ±3.04098 | ms/op |
| `GraphDbComparisonBenchmark.batchInsertCycle` | backend=neo4j, sizeName=medium | 15.6899 | ±3.64651 | ms/op |
| `GraphDbComparisonBenchmark.batchInsertCycle` | backend=memgraph, sizeName=medium | 11.3643 | ±0.245352 | ms/op |
| `GraphDbComparisonBenchmark.batchInsertCycle` | backend=age, sizeName=medium | 309.09 | ±55.7416 | ms/op |
| `GraphDbComparisonBenchmark.batchInsertCycle` | backend=falkordb, sizeName=medium | 1929.18 | ±47.7961 | ms/op |
| `GraphDbComparisonBenchmark.countPersons` | backend=tinkergraph, sizeName=medium | 0.308113 | ±0.0284471 | ms/op |
| `GraphDbComparisonBenchmark.countPersons` | backend=neo4j, sizeName=medium | 0.528496 | ±0.144468 | ms/op |
| `GraphDbComparisonBenchmark.countPersons` | backend=memgraph, sizeName=medium | 1.34085 | ±0.827967 | ms/op |
| `GraphDbComparisonBenchmark.countPersons` | backend=age, sizeName=medium | 2.17556 | ±1.3092 | ms/op |
| `GraphDbComparisonBenchmark.countPersons` | backend=falkordb, sizeName=medium | 0.197483 | ±0.0211709 | ms/op |
| `GraphDbComparisonBenchmark.oneHopNeighbors` | backend=tinkergraph, sizeName=medium | 0.0033315 | ±0.000309384 | ms/op |
| `GraphDbComparisonBenchmark.oneHopNeighbors` | backend=neo4j, sizeName=medium | 0.664879 | ±1.04797 | ms/op |
| `GraphDbComparisonBenchmark.oneHopNeighbors` | backend=memgraph, sizeName=medium | 0.30814 | ±0.0841431 | ms/op |
| `GraphDbComparisonBenchmark.oneHopNeighbors` | backend=age, sizeName=medium | 10.1747 | ±11.9676 | ms/op |
| `GraphDbComparisonBenchmark.oneHopNeighbors` | backend=falkordb, sizeName=medium | 1.04602 | ±0.0139483 | ms/op |
| `GraphDbComparisonBenchmark.shortestPath` | backend=tinkergraph, sizeName=medium | 0.0187708 | ±0.000688417 | ms/op |
| `GraphDbComparisonBenchmark.shortestPath` | backend=neo4j, sizeName=medium | 0.70029 | ±0.919535 | ms/op |
| `GraphDbComparisonBenchmark.shortestPath` | backend=memgraph, sizeName=medium | 0.386095 | ±0.154776 | ms/op |
| `GraphDbComparisonBenchmark.shortestPath` | backend=age, sizeName=medium | 12.4196 | ±7.24409 | ms/op |
| `GraphDbComparisonBenchmark.shortestPath` | backend=falkordb, sizeName=medium | 0.512055 | ±0.0893247 | ms/op |

