# CLAUDE.md - bluetape4k-graph

This repository is a Kotlin graph database integration library for Neo4j,
Memgraph, Apache AGE, TinkerPop/TinkerGraph, and FalkorDB. It exposes paired
synchronous/coroutine APIs, virtual-thread adapters, graph-io bulk I/O,
Ktor 3 integration, Spring Boot 4 auto-configuration, domain examples,
benchmarks, and a BOM.

- Kotlin: 2.3
- Java: 21 with preview enabled
- Dependency versions: `gradle/libs.versions.toml`
- Keep agent-facing guidance in English.
- Keep user-facing root README files synchronized between `README.md` and
  `README.ko.md`.

## Project Structure

```text
bom/               # Gradle project: bluetape4k-graph-bom
graph/
  graph-core/       # Abstract model, repositories, traversal APIs, capabilities
  graph-age/        # Apache AGE over PostgreSQL/JDBC
  graph-neo4j/      # Neo4j Java Driver
  graph-memgraph/   # Memgraph through Neo4j-compatible protocol
  graph-tinkerpop/  # Apache TinkerPop / TinkerGraph
  graph-falkordb/   # FalkorDB / Redis module, jfalkordb
graph-io/
  core/             # Shared records, options, path helpers, external ID map
  csv/              # CSV import/export
  jackson2/         # Jackson 2.x NDJSON import/export
  jackson3/         # Jackson 3.x NDJSON import/export
  graphml/          # GraphML XML/StAX import/export
  okio/             # graph-okio: OkIO streams, compression, DAEAD encryption
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
  observability-graph-examples/
  recommendation-examples/
  ktor-graph-examples/
```

`examples/` modules are not published to Maven Central.

## Build Commands

```bash
./gradlew build -x test
./gradlew test
./gradlew :bluetape4k-graph-neo4j:build
./gradlew :graph-okio:test
./gradlew :code-graph-examples:test
./gradlew :fraud-detection-examples:test
./gradlew :knowledge-graph-examples:test
./gradlew :observability-graph-examples:test
./gradlew :recommendation-examples:test
./gradlew :bluetape4k-graph-neo4j:test --tests "io.bluetape4k.graph.neo4j.Neo4jGraphOperationsTest"
./gradlew publishBluetapeGraphPublicationToMavenLocalRepository
./gradlew publishAggregationToCentralPortal
```

## Architecture

`bluetape4k-graph-core` owns the common model and contracts. Backend modules implement
those contracts with each database's driver and query language.

```text
GraphOperations = GraphSession + GraphVertexRepository + GraphEdgeRepository + GraphTraversalRepository
GraphSuspendOperations = GraphSuspendSession + GraphSuspendVertexRepository + ...
```

Core concepts:

- Models: `GraphVertex`, `GraphEdge`, `GraphPath`, `GraphElementId`
- Schema DSL: `VertexLabel`, `EdgeLabel`
- Batch insert: `createVertices`, `createEdges`
- Schema/index capability: `schemaManager()`
- Merge/upsert capability: `mergeVertex`, `mergeEdge`
- Transaction DSL: `transaction { }`, `suspendTransaction { }`
- Weighted path and graph algorithms where backend semantics support them

## Backends

| Module | Driver | Query language | Local verification |
|---|---|---|---|
| `bluetape4k-graph-neo4j` | Neo4j Java Driver | Cypher | Testcontainers `neo4j:5` |
| `bluetape4k-graph-memgraph` | Neo4j Java Driver compatible | Cypher | Testcontainers `memgraph/memgraph` |
| `bluetape4k-graph-age` | PostgreSQL JDBC + Exposed | Cypher-over-SQL | Testcontainers `apache/age:PG16_latest` |
| `bluetape4k-graph-tinkerpop` | TinkerGraph | Gremlin | In-memory JVM graph |
| `bluetape4k-graph-falkordb` | jfalkordb 0.7.0, Jedis-based | openCypher subset | Testcontainers `falkordb/falkordb:v4.18.1` |

Amazon Neptune implementation is blocked by issue #113 until local or reliable
integration testability is proven. Do not build Neptune support against mocks
only.

## graph-io

The `graph-io` family provides CSV, Jackson2/Jackson3 NDJSON, GraphML, and
OkIO-based import/export paths with sync, virtual-thread, and coroutine
execution models where applicable. `graph-okio` also supports compression
chaining and DAEAD chunk encryption/decryption.

When touching graph-io:

- keep duplicate-ID and missing-endpoint import semantics intact
- run cross-format round-trip tests for affected formats
- run graph-okio negative-path tests when compression or encryption changes
- use `io.bluetape4k.assertions.assertFailsWith` for exception assertions

## Test Patterns

Use singleton Testcontainers launchers from
`io.bluetape4k.testcontainers.graphdb`:

```kotlin
import io.bluetape4k.testcontainers.graphdb.Neo4jServer

val driver = GraphDatabase.driver(
    Neo4jServer.Launcher.neo4j.boltUrl,
    AuthTokens.none(),
)
```

Example modules share behavior through abstract test classes. Concrete backend
tests should only provide `ops` and server lifecycle details.

The `testMutex` BuildService serializes container-heavy tests to avoid runtime
conflicts. `.github/workflows/examples.yml` owns daily and path-triggered
example verification; do not duplicate that coverage inside Nightly unless the
Nightly contract explicitly changes.

## Documentation Rules

- Public/contributor-facing docs, KDoc, PRs, issues, commits, and release notes
  should be English.
- Root README content is library-user documentation: keep English and Korean
  variants synchronized.
- README diagrams should use Mermaid.
- Root README hero assets live under `docs/assets/`; use image generation for
  final raster art and treat `.github/profile/assets/` only as visual direction.
- Update `WIP.md`, `CHANGELOG.md`, and `docs/lessons/` when project state
  changes.
