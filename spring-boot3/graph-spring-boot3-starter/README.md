# graph-spring-boot3-starter

Spring Boot 3 Auto-configuration starter for [bluetape4k-graph](../../README.md).

Registers `GraphOperations`, `GraphSuspendOperations`, and `GraphVirtualThreadOperations` beans
for the selected backend via a single property.

## Supported Backends

| Backend | Property value | Required runtime dependency |
|---------|---------------|-----------------------------|
| TinkerGraph (in-memory, default) | `tinkergraph` | `graph-tinkerpop` |
| Neo4j | `neo4j` | `graph-neo4j` |
| Memgraph | `memgraph` | `graph-memgraph` |
| Apache AGE (PostgreSQL) | `age` | `graph-age` |

## Getting Started

### 1. Add dependency

```kotlin
// build.gradle.kts
dependencies {
    implementation("io.bluetape4k:graph-spring-boot3-starter:<version>")

    // Add ONE backend at runtime
    runtimeOnly("io.bluetape4k:graph-neo4j:<version>")   // or graph-memgraph / graph-age / graph-tinkerpop
}
```

### 2. Configure `application.yml`

**TinkerGraph (in-memory — no extra config needed):**
```yaml
bluetape4k:
  graph:
    backend: tinkergraph
```

**Neo4j:**
```yaml
bluetape4k:
  graph:
    backend: neo4j
    neo4j:
      uri: bolt://localhost:7687
      username: neo4j
      password: secret
      database: neo4j
      register-suspend: true
      register-virtual-thread: true
```

**Memgraph:**
```yaml
bluetape4k:
  graph:
    backend: memgraph
    memgraph:
      uri: bolt://localhost:7687
      username: ""
      password: ""
      database: memgraph
      register-suspend: true
      register-virtual-thread: true
```

**Apache AGE (PostgreSQL):**
```yaml
bluetape4k:
  graph:
    backend: age
    age:
      graph-name: my_graph
      auto-create-graph: true
      register-suspend: true
      register-virtual-thread: true
```

> AGE requires a JDBC `DataSource` bean (e.g., via `spring-boot-starter-jdbc`).

### 3. Inject and use

```kotlin
@Service
class MyGraphService(
    private val ops: GraphOperations,
    private val suspendOps: GraphSuspendOperations,   // optional
) {
    fun createPerson(name: String): GraphVertex =
        ops.createVertex("Person", mapOf("name" to name))

    suspend fun findPerson(id: GraphElementId): GraphVertex? =
        suspendOps.findVertexById("Person", id)
}
```

## Registered Beans

| Bean type | Condition |
|-----------|-----------|
| `GraphOperations` | Always when backend is active |
| `GraphSuspendOperations` | `register-suspend=true` (default) |
| `GraphVirtualThreadOperations` | `register-virtual-thread=true` (default) |
| `HealthIndicator` (Neo4j/Memgraph) | When `spring-boot-actuator` is on classpath |

All beans use `@ConditionalOnMissingBean` — provide your own bean to override.

## Configuration Properties

### Common

| Property | Default | Description |
|----------|---------|-------------|
| `bluetape4k.graph.backend` | *(none — TinkerGraph activates by default)* | Active backend: `tinkergraph` \| `neo4j` \| `memgraph` \| `age` |

### Neo4j (`bluetape4k.graph.neo4j.*`)

| Property | Default | Description |
|----------|---------|-------------|
| `uri` | `bolt://localhost:7687` | Bolt URI |
| `username` | `neo4j` | Username |
| `password` | *(empty)* | Password (empty → no-auth) |
| `database` | `neo4j` | Target database |
| `register-suspend` | `true` | Register `GraphSuspendOperations` |
| `register-virtual-thread` | `true` | Register `GraphVirtualThreadOperations` |

### Memgraph (`bluetape4k.graph.memgraph.*`)

Same properties as Neo4j with prefix `bluetape4k.graph.memgraph`. Default database: `memgraph`, default username: *(empty)*.

### Apache AGE (`bluetape4k.graph.age.*`)

| Property | Default | Description |
|----------|---------|-------------|
| `graph-name` | `bluetape4k_graph` | AGE graph name |
| `auto-create-graph` | `true` | Create graph if not exists |
| `register-suspend` | `true` | Register `GraphSuspendOperations` |
| `register-virtual-thread` | `true` | Register `GraphVirtualThreadOperations` |

## Auto-Configuration Classes

| Class | Activated when |
|-------|---------------|
| `GraphAutoConfiguration` | Always — loads `GraphProperties`, establishes ordering |
| `GraphTinkerGraphAutoConfiguration` | `backend=tinkergraph` or property absent |
| `GraphNeo4jAutoConfiguration` | `backend=neo4j` |
| `GraphMemgraphAutoConfiguration` | `backend=memgraph` |
| `GraphAgeAutoConfiguration` | `backend=age` |
