# AGENTS.md - bluetape4k-graph

Graph database integration library for Neo4j, Memgraph, Apache AGE,
TinkerPop/TinkerGraph, and FalkorDB. The project provides paired synchronous
and coroutine APIs, virtual-thread adapters, graph-io bulk import/export,
Ktor 3 integration, Spring Boot 4 auto-configuration, examples, benchmarks,
and a BOM.

- Kotlin: 2.3
- Java: 21 with preview enabled
- Dependency versions: `gradle/libs.versions.toml`
- Public/contributor-facing docs and KDoc: English
- Library-user README files: keep `README.md` and `README.ko.md` in sync

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
  knowledge-graph-examples/
  linkedin-graph-examples/
  recommendation-examples/
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
./gradlew :knowledge-graph-examples:test
./gradlew :recommendation-examples:test
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
| `bluetape4k-graph-neo4j` | Neo4j Java Driver | Cypher | Testcontainers `neo4j:5` |
| `bluetape4k-graph-memgraph` | Neo4j Java Driver compatible | Cypher | Testcontainers `memgraph/memgraph` |
| `bluetape4k-graph-age` | PostgreSQL JDBC + Exposed | Cypher-over-SQL | Testcontainers `apache/age:PG16_latest` |
| `bluetape4k-graph-tinkerpop` | TinkerGraph | Gremlin | In-memory JVM graph |
| `bluetape4k-graph-falkordb` | jfalkordb 0.7.0, Jedis-based | openCypher subset | Testcontainers `falkordb/falkordb:v4.18.1` |

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

## Documentation

- Root README updates must keep `README.md` and `README.ko.md` structurally in
  sync.
- Public API changes require English KDoc with a one-line summary, behavior
  contract, and callable Kotlin example when useful.
- README architecture diagrams should use Mermaid.
- Root README hero assets live under `docs/assets/`. Use generated raster
  images for final README visuals; `.github/profile/assets/` may be used only
  as visual direction.
- Update `WIP.md`, `CHANGELOG.md`, and lessons after substantial work or when
  project state changes.

## Cross-Repo Lesson Guards

- Before issue, PR, workflow, release, or module-registration work, query GNO
  for this repo in both `bluetape4k-github` and `bluetape4k-docs`.
- For graph backend or example module changes, keep README locale sets,
  repo-local module lists, examples workflow, Nightly/full coverage, coverage
  artifacts, and BOM/catalog constraints synchronized.
- Run graph database Testcontainers verification sequentially across modules
  and worktrees. A pass-after-retry still needs a short lifecycle/timing note.
