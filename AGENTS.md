# AGENTS.md - bluetape4k-graph

Graph database library for Neo4j, Memgraph, Apache AGE, TinkerPop, and
FalkorDB. Provides paired synchronous and coroutine APIs plus Spring Boot 4
auto-configuration.

- Kotlin: 2.3
- Java: 21 with preview enabled
- Dependency versions: `gradle/libs.versions.toml`

## Layout

```text
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
benchmark/
spring-boot/
  graph-spring-boot/
examples/
```

`examples/` modules are excluded from Maven Central publishing.

## Commands

```bash
./gradlew build -x test
./gradlew test
./gradlew :graph-neo4j:build
./gradlew :code-graph-examples:test
./gradlew :graph-neo4j:test --tests "io.bluetape4k.graph.neo4j.Neo4jGraphOperationsTest"
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

Schema DSL uses Exposed Table-style declarations through `VertexLabel` and
`EdgeLabel`.

## Backends

| Module | Driver | Query language |
|---|---|---|
| `graph-neo4j` | Neo4j Java Driver | Cypher |
| `graph-memgraph` | Neo4j Java Driver compatible | Cypher |
| `graph-age` | PostgreSQL JDBC + Exposed | Cypher-over-SQL |
| `graph-tinkerpop` | TinkerGraph | Gremlin |
| `graph-falkordb` | jfalkordb 0.7.0, Jedis-based | openCypher subset |

## Tests

- Use `io.bluetape4k.testcontainers.graphdb` singleton launchers.
- Example modules use abstract test classes for shared logic and concrete
  backend classes for `ops` and server lifecycle.
- `testMutex` BuildService serializes container-heavy tests to avoid conflicts.
- When adding a new module, update both `.github/workflows/ci.yml` and
  `.github/workflows/nightly.yml` so the module's tests run in the appropriate
  CI/Nightly scope. Container-backed module tests should usually have a
  dedicated full Nightly job instead of being hidden inside the daily smoke job.
- When `.github/workflows/nightly.yml` is changed, explicitly run the Nightly
  workflow with `workflow_dispatch` before DoD and record the run URL/result.
  For module coverage changes, use `scope=full` unless the change is strictly
  smoke-only.
