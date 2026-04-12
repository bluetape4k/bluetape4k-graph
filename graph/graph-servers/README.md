# graph-servers

Testcontainers-based singleton server factories for integration testing of bluetape4k-graph backend modules.

> 🇰🇷 [한국어 문서](README.ko.md)

## Overview

This module provides reusable Testcontainers singletons so that integration tests for `graph-neo4j`, `graph-memgraph`, and `graph-age` modules can share a single container instance across test classes. This significantly reduces test execution time compared to creating a new container per test class.

## Provided Servers

| Object | Target | Image | Purpose |
|--------|--------|-------|---------|
| `Neo4jServer` | Neo4j 5.x | `neo4j:5` | Neo4j integration tests |
| `MemgraphServer` | Memgraph | `memgraph/memgraph:latest` | Memgraph integration tests |
| `PostgreSQLAgeServer` | PostgreSQL + Apache AGE | `apache/age:PG16_latest` | Apache AGE integration tests |

Each object is a lazy-initialized singleton — the container starts on first access and is reused for the rest of the JVM lifetime.

## Usage

### Neo4j

```kotlin
import io.bluetape4k.graph.servers.Neo4jServer
import org.neo4j.driver.GraphDatabase
import org.neo4j.driver.AuthTokens

val driver = GraphDatabase.driver(Neo4jServer.boltUrl, AuthTokens.none())
val ops = Neo4jGraphOperations(driver)
```

### Memgraph

```kotlin
import io.bluetape4k.graph.servers.MemgraphServer

val driver = GraphDatabase.driver(MemgraphServer.boltUrl, AuthTokens.none())
val ops = MemgraphGraphOperations(driver)
```

### PostgreSQL + Apache AGE

```kotlin
import io.bluetape4k.graph.servers.PostgreSQLAgeServer
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database

val hikariConfig = HikariConfig().apply {
    jdbcUrl = PostgreSQLAgeServer.jdbcUrl
    username = PostgreSQLAgeServer.username
    password = PostgreSQLAgeServer.password
    connectionInitSql = """LOAD 'age'; SET search_path = ag_catalog, "${'$'}user", public"""
}
val database = Database.connect(HikariDataSource(hikariConfig))
val ops = AgeGraphOperations(database, graphName = "test_graph")
```

## Dependencies

```kotlin
dependencies {
    // Apache AGE (PostgreSQL extension)
    api(Libs.testcontainers_postgresql)
    api(Libs.postgresql_driver)
    api(Libs.hikaricp)

    // Neo4j
    api(Libs.testcontainers_neo4j)
    api(Libs.neo4j_java_driver)

    // Memgraph (generic container + neo4j driver)
    api(Libs.testcontainers)
    api(Libs.bluetape4k_testcontainers)
}
```

## Requirements

- Docker must be running locally (Testcontainers requirement)
- This module is intended for test code — do not use it in production
