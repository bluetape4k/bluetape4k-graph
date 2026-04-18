# bluetape4k-graph

Graph database integration library for the bluetape4k ecosystem. Provides a unified abstract API over Apache AGE, Neo4j, Memgraph, and Apache TinkerPop.

> 🇰🇷 [한국어 문서](README.ko.md)

## Module Structure

```
graph/
  graph-core       # Backend-independent models and interfaces (foundation for all modules)
  graph-age        # Apache AGE (PostgreSQL graph extension) implementation
  graph-neo4j      # Neo4j Java Driver implementation
  graph-memgraph   # Memgraph (Neo4j protocol compatible) implementation
  graph-tinkerpop  # Apache TinkerPop / TinkerGraph in-memory implementation
  graph-servers    # Testcontainers server factory for testing
examples/
  code-graph-examples     # Code dependency graph examples (AGE, Neo4j, Memgraph, TinkerGraph integration)
  linkedin-graph-examples # LinkedIn social graph examples (AGE, Neo4j, Memgraph, TinkerGraph integration)
```

## Core Abstraction (`graph-core`)

The common interface layer that every backend implementation adheres to.

### Dual API Pattern

Provides both synchronous (blocking) and coroutine (suspend/Flow) APIs.

```
GraphOperations        = GraphSession + GraphVertexRepository + GraphEdgeRepository + GraphTraversalRepository
GraphSuspendOperations = GraphSuspendSession + ... (suspend function versions)
```

### Domain Model

```kotlin
data class GraphVertex(val id: GraphElementId, val label: String, val properties: Map<String, Any?>)
data class GraphEdge(val id: GraphElementId, val label: String, val startId: GraphElementId, val endId: GraphElementId, val properties: Map<String, Any?>)
data class GraphPath(val steps: List<PathStep>)   // VertexStep | EdgeStep
```

### Schema DSL

Declarative schema definition in the Exposed Table style. Works backend-independently.

```kotlin
object PersonLabel : VertexLabel("Person") {
    val name = string("name")
    val age  = integer("age")
}

object KnowsLabel : EdgeLabel("KNOWS") {
    val since = localDate("since")
}
```

## Adding Dependencies

### BOM (Recommended)

```kotlin
// build.gradle.kts
dependencyManagement {
    imports {
        mavenBom("io.github.bluetape4k.graph:bluetape4k-graph-bom:0.0.1")
    }
}

dependencies {
    implementation("io.github.bluetape4k.graph:graph-neo4j")   // version can be omitted
    implementation("io.github.bluetape4k.graph:graph-age")
}
```

### Individual Modules

```kotlin
dependencies {
    implementation("io.github.bluetape4k.graph:graph-core:0.0.1")
    implementation("io.github.bluetape4k.graph:graph-neo4j:0.0.1")
    // graph-age | graph-memgraph | graph-tinkerpop
}
```

## Quick Start

### Neo4j

```kotlin
val driver = GraphDatabase.driver(Neo4jServer.boltUrl, AuthTokens.none())
val ops = Neo4jGraphOperations(driver)

val alice = ops.createVertex("Person", mapOf("name" to "Alice", "age" to 30))
val bob   = ops.createVertex("Person", mapOf("name" to "Bob",   "age" to 28))
ops.createEdge(alice.id, bob.id, "KNOWS", mapOf("since" to LocalDate.now()))

val path = ops.shortestPath(alice.id, bob.id, "KNOWS", maxDepth = 5)
```

### Apache AGE (PostgreSQL)

```kotlin
val hikariConfig = HikariConfig().apply {
    jdbcUrl = "jdbc:postgresql://localhost:5432/postgres"
    connectionInitSql = "LOAD 'age'; SET search_path = ag_catalog, \"${'$'}user\", public"
}
val db = Database.connect(HikariDataSource(hikariConfig))
val ops = AgeGraphOperations("my_graph")

ops.createGraph("my_graph")
val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
```

### TinkerPop (In-Memory, No External Server Required)

```kotlin
val ops = TinkerGraphOperations()
val alice = ops.createVertex("Person", mapOf("name" to "Alice"))
val bob   = ops.createVertex("Person", mapOf("name" to "Bob"))
ops.createEdge(alice.id, bob.id, "KNOWS", emptyMap())

val neighbors = ops.neighbors(alice.id, "KNOWS", Direction.OUTGOING, depth = 1)
ops.close()
```

## Backend Comparison

| Item | graph-age | graph-neo4j | graph-memgraph | graph-tinkerpop |
|------|-----------|-------------|----------------|-----------------|
| Query Language | Cypher-over-SQL | Cypher | Cypher | Gremlin |
| Infrastructure | PostgreSQL + AGE | Neo4j | Memgraph | JVM in-memory |
| Driver | JDBC + Exposed | Neo4j Java Driver | Neo4j Java Driver (compatible) | TinkerPop |
| Test Container | `apache/age:PG16_latest` | `neo4j:5` | `memgraph/memgraph:latest` | not required |

## Running Tests

Tests automatically launch Docker containers via Testcontainers. Docker is required.

```bash
# All tests
./gradlew test

# Specific module tests
./gradlew :graph-neo4j:test
./gradlew :graph-age:test
./gradlew :code-graph-examples:test
./gradlew :linkedin-graph-examples:test

# Specific class
./gradlew :graph-neo4j:test --tests "io.bluetape4k.graph.neo4j.Neo4jGraphOperationsTest"
```

## Example Module Structure (`examples/`)

Each example module uses the **abstract test class pattern**. Common test logic lives in one place, while each concrete class only overrides backend-specific setup.

| Abstract Class | Concrete Classes (Backend) |
|----------------|---------------------------|
| `AbstractCodeGraphTest` | `Neo4j/Memgraph/TinkerGraph/AgeCodeGraphTest` |
| `AbstractCodeGraphSuspendTest` | `Neo4j/Memgraph/TinkerGraph/AgeCodeGraphSuspendTest` |
| `AbstractLinkedInGraphTest` | `Neo4j/Memgraph/TinkerGraph/AgeLinkedInGraphTest` |
| `AbstractLinkedInGraphSuspendTest` | `Neo4j/Memgraph/TinkerGraph/AgeLinkedInGraphSuspendTest` |

Concrete classes only need to implement `ops` (`GraphOperations` or `GraphSuspendOperations`) and the server lifecycle (`@BeforeAll`/`@AfterAll`).

## Requirements

- Java 25 (with preview features enabled)
- Kotlin 2.3
- Docker (for integration tests)

## Tech Stack

- **Kotlin** 2.3 + Coroutines 1.10
- **Neo4j Java Driver** 5.x
- **JetBrains Exposed** (JDBC for Apache AGE)
- **Apache TinkerPop** (Gremlin)
- **Testcontainers** (integration tests)
- **bluetape4k** 1.5.x (common utilities)

## Documentation

- [Graph Database Pros & Cons and Selection Guide](docs/graphdb-tradeoffs.md) — GraphDB trade-offs and backend selection guide for bluetape4k-graph (Neo4j, Memgraph, AGE, TinkerPop)
