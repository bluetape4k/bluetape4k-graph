# AGENTS.md - bluetape4k-graph

## Guidance hierarchy

Before applying this repository overlay, read and follow the guidance in this
order:

1. User scope: `${CODEX_HOME:-$HOME/.codex}/AGENTS.md`.
2. Workspace scope: `/Users/debop/work/bluetape4k/.github/docs/workspace/AGENTS.md`.

Apply both broader scopes before repository-specific rules.

This repository inherits the workspace guidance from `../AGENTS.md`.
Read and follow the workspace root guide first. This file only adds
repo-specific layout, commands, domain rules, and local exceptions.


Graph database integration library for Neo4j, Memgraph, Apache AGE,
TinkerPop/TinkerGraph, and FalkorDB. The project provides paired synchronous
and coroutine APIs, virtual-thread adapters, graph-io bulk import/export,
Ktor 3 integration, Spring Boot 4 auto-configuration, examples, benchmarks,
and a BOM.

- Base version: 1.0.0
- Kotlin: 2.4.10 (language/API 2.4)
- Java: 25 with preview enabled
- Dependency versions: `gradle/libs.versions.toml`

## Layout

```text
bom/                   # Gradle project: bluetape4k-graph-bom
graph/
  graph-core/
  graph-age/
  graph-neo4j/
  graph-memgraph/
  graph-tinkerpop/
  graph-falkordb/
graph-io/
  core/
  csv/
  jackson2/
  jackson3/
  graphml/
  okio/                 # Gradle project/artifact: graph-okio
benchmark/
  graph-benchmark/
  graph-io-benchmark/
  graph-age-benchmark/
  graph-neo4j-benchmark/
ktor/
  graph-ktor/
spring-boot/
  graph-spring-boot/
examples/
  code-graph-examples/
  fraud-detection-examples/
  iam-access-graph-examples/
  knowledge-graph-examples/
  linkedin-graph-examples/
  observability-graph-examples/
  recommendation-examples/
  supply-chain-graph-examples/
  data-lineage-examples/
  network-topology-examples/
  security-attack-path-examples/
  ktor-graph-examples/
```

`examples/` modules are excluded from Maven Central publishing.

## Commands

```bash
./gradlew build -x test
./gradlew test
./gradlew :bluetape4k-graph-neo4j:build
./gradlew :graph-okio:test
./gradlew :code-graph-examples:test
./gradlew :fraud-detection-examples:test
./gradlew :iam-access-graph-examples:test
./gradlew :knowledge-graph-examples:test
./gradlew :observability-graph-examples:test
./gradlew :recommendation-examples:test
./gradlew :supply-chain-graph-examples:test
./gradlew :data-lineage-examples:test
./gradlew :network-topology-examples:test
./gradlew :security-attack-path-examples:test
./gradlew :bluetape4k-graph-neo4j:test --tests "io.bluetape4k.graph.neo4j.Neo4jGraphOperationsTest"
./gradlew publishBluetapeGraphPublicationToMavenLocalRepository
./gradlew publishAggregationToCentralPortal
```

## Architecture

The core abstraction uses paired APIs:

```text
GraphOperations = GraphSession + GraphVertexRepository + GraphEdgeRepository + GraphTraversalRepository
GraphSuspendOperations = GraphSuspendSession + GraphSuspendVertexRepository + ...
```

Core models:

- `GraphVertex(id, label, properties)`
- `GraphEdge(id, label, startId, endId, properties)`
- `GraphPath`
- `GraphElementId`

Common capability layers:

- Batch insert: `createVertices`, `createEdges`
- Schema/index management: `schemaManager()`
- Merge/upsert: `mergeVertex`, `mergeEdge`
- Transaction DSL: `transaction { }`, `suspendTransaction { }`
- Weighted paths and graph algorithms where supported by backend semantics

Schema DSL uses Exposed Table-style declarations through `VertexLabel` and
`EdgeLabel`.

## Backends

| Module | Driver | Query language | Local verification |
|---|---|---|---|
| `bluetape4k-graph-neo4j` | Neo4j Java Driver 6.2.1 | Cypher | Testcontainers `neo4j:5.26.29` |
| `bluetape4k-graph-memgraph` | Neo4j Java Driver 6.2.1 compatible | Cypher | Testcontainers `memgraph/memgraph:3.12.0` |
| `bluetape4k-graph-age` | PostgreSQL JDBC 42.7.13 + Exposed | Cypher-over-SQL | Testcontainers `apache/age:release_PG18_1.7.0` |
| `bluetape4k-graph-tinkerpop` | TinkerGraph | Gremlin | In-memory JVM graph |
| `bluetape4k-graph-falkordb` | jfalkordb 0.8.0, Jedis-based | openCypher subset | Testcontainers `falkordb/falkordb:v4.20.2` |

Amazon Neptune work is intentionally blocked by issue #113 until local or
reliable integration testability is proven. Do not implement a backend against
mocks only.

## graph-io

- `bluetape4k-graph-io-core` owns shared contracts, records, options, path helpers, and
  external ID mapping.
- CSV, Jackson2 NDJSON, Jackson3 NDJSON, and GraphML provide sync,
  virtual-thread, and coroutine import/export paths.
- `graph-okio` adds OkIO `Source`/`Sink` adapters, compression chaining,
  `FakeFileSystem` support, and DAEAD chunk encryption/decryption.
- When changing graph-io behavior, verify cross-format round trips and the
  relevant graph-okio negative-path tests.

## Tests

- Use `io.bluetape4k.testcontainers.graphdb` singleton launchers.
- Example modules use abstract test classes for shared logic and concrete
  backend classes for `ops` and server lifecycle.
- Use `testMutex` BuildService for container-heavy tests that can conflict
  when run in parallel.
- For exception tests, use `io.bluetape4k.assertions.assertFailsWith`; do not
  introduce JUnit `assertThrows`, `kotlin.test.assertFailsWith`, or
  `invoking { } shouldThrow` in new tests.
- When adding a new module, update CI and Nightly workflows so the module's
  tests run in the appropriate scope. Container-backed module tests usually
  belong in Full Nightly rather than daily smoke.
- Example module builds are covered by `.github/workflows/examples.yml`, which
  runs daily and on example/graph/graph-io/Ktor/build changes. Keep examples
  out of Nightly unless their backend coverage becomes part of Nightly's
  explicit contract.
- When `.github/workflows/nightly-tests.yml` is changed, explicitly run the Nightly
  workflow with `workflow_dispatch` before DoD and record the run URL/result.
  For module coverage changes, use `scope=full` unless the change is strictly
  smoke-only.
- `scripts/testcontainers_image_gate_manifest.json` is the source of truth for
  the four image families; CI changed-path and full Nightly gates run the
  families sequentially. Release reuses the successful exact-head full Nightly
  evidence instead of running the families again. Use
  `python3 scripts/run_testcontainers_image_gate.py --scope full --report-dir build/reports/testcontainers-image-gate`
  for the complete local gate.

## Documentation

- `docs/assets/` is the graph repo root README asset path. Use generated raster
  images for final README visuals; `.github/profile/assets/` may be used only
  as visual direction.
- Update `WIP.md`, `CHANGELOG.md`, and lessons after substantial work or when
  project state changes.

## Repo-Specific Guards

- For graph backend or example module changes, keep backend-specific examples
  workflow coverage, Nightly/full coverage, and graph BOM/catalog constraints
  synchronized with the changed backend.
- Run graph database Testcontainers verification sequentially across modules
  and worktrees. A pass-after-retry still needs a short lifecycle/timing note.
