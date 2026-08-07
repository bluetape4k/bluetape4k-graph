# AGE and Neo4j standalone benchmarks

## Comparable boundary

The standalone projects measure the same small vertex/traversal shape through different drivers: AGE uses PostgreSQL JDBC, HikariCP, Exposed, and an AGE Testcontainer; Neo4j uses the Java driver and a Neo4j Testcontainer. Sources: [AGE state](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/benchmark/graph-age-benchmark/src/main/kotlin/io/bluetape4k/graph/age/benchmark/AgeBenchmarkState.kt), [Neo4j state](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/benchmark/graph-neo4j-benchmark/src/main/kotlin/io/bluetape4k/graph/neo4j/benchmark/Neo4jBenchmarkState.kt), and [wrapper](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/scripts/benchmark-neo4j-age.sh).

Prerequisites: JDK 21, Docker, release commit `72c0256e2e1cf61101d29852210e3c827ca93bc0`, and no concurrent container benchmark.

```bash
./gradlew :graph-age-benchmark:benchmark --rerun-tasks --console=plain
./gradlew :graph-neo4j-benchmark:benchmark --rerun-tasks --console=plain
scripts/benchmark-neo4j-age.sh
```

Each Gradle project configures 3 warmups, 5 measurements, 3 seconds per iteration, JSON format, and the harness default fork count. Reports are under `benchmark/graph-{age,neo4j}-benchmark/build/reports/benchmarks/main/`. The wrapper normalizes source units to `us/op` and emits one JSON object on the final stdout line. Lower latency is better.

No standalone 0.6.0 result file is committed for a controlled head-to-head run, so this page invents no number. Reproduce both serially on the same idle host and retain both raw reports plus the wrapper summary. The shared committed [small backend run](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/benchmark/graph-db-testcontainers-2026-05-21.json) is representative evidence only when its exact conditions are kept.

## Interpretation and cleanup

Compare only matching operation, parameters, unit, warmup, iterations, fork, fixture, and host. An arithmetic mean across unlike operations is a transport summary, not a backend ranking. Teardown must close operations, JDBC pool/driver, and container. After cancellation, remove only containers created by the run after identifying them.

Container startup failure, AGE extension initialization failure, Bolt readiness failure, or missing JSON is a failed run, not a score. These benchmarks do not prove PostgreSQL consolidation value, Neo4j operational maturity, HA, recovery, cloud network cost, or production query plans. Read [AGE backend](../backends/apache-age.md), [Neo4j/Memgraph](../backends/neo4j-and-memgraph.md), and [overview](./overview.md).

<!-- release-readme-diagrams:start -->
## Release diagrams {#release-diagrams}

These diagrams are loaded directly from README assets published with the `0.6.0` release and pinned to its immutable commit. They describe this manual's released structure and runtime flows, not later Snapshot changes. Select a preview to open the SVG at the same release commit.

### graph-age-benchmark Architecture diagram

[![graph-age-benchmark Architecture diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/benchmark/graph-age-benchmark-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/benchmark/graph-age-benchmark-architecture-01.svg)

_Release README: [`benchmark/graph-age-benchmark/README.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/benchmark/graph-age-benchmark/README.md)_

### graph-neo4j-benchmark Architecture diagram

[![graph-neo4j-benchmark Architecture diagram](https://raw.githubusercontent.com/bluetape4k/bluetape4k-graph/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/benchmark/graph-neo4j-benchmark-architecture-01.png)](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/docs/images/readme-diagrams/benchmark/graph-neo4j-benchmark-architecture-01.svg)

_Release README: [`benchmark/graph-neo4j-benchmark/README.md`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/benchmark/graph-neo4j-benchmark/README.md)_

<!-- release-readme-diagrams:end -->
