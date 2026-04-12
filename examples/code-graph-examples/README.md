# code-graph-examples

Code dependency graph example demonstrating how to model modules, classes, and functions as a property graph using bluetape4k-graph's backend-independent API.

> 🇰🇷 [한국어 문서](README.ko.md)

## Overview

This example models a software codebase as a property graph and verifies that the same code works identically across Neo4j, Memgraph, Apache AGE, and TinkerGraph backends via the abstract test class pattern.

- **Common Logic**: `CodeGraphService` / `CodeGraphSuspendService` contain the graph operations
- **Abstract Tests**: `AbstractCodeGraphTest` / `AbstractCodeGraphSuspendTest` encapsulate shared test scenarios
- **Concrete Tests**: Each backend (Neo4j, Memgraph, AGE, TinkerGraph) only provides a `GraphOperations` factory

## Domain Model

### Vertex Labels

| Label | Description | Key Properties |
|-------|-------------|----------------|
| `Module` | Build module / package | `name`, `path`, `version`, `language` |
| `Class` | Class or interface | `name`, `qualifiedName`, `module`, `isAbstract`, `isInterface` |
| `Function` | Function or method | `name`, `signature`, `className`, `module`, `lineCount` |

### Edge Labels

| Label | From → To | Description |
|-------|-----------|-------------|
| `DEPENDS_ON` | `Module → Module` | Module dependency (compile / runtime / test) |
| `IMPORTS` | `Class → Class` | Class import relationship |
| `EXTENDS` | `Class → Class` | Class inheritance |
| `IMPLEMENTS` | `Class → Class` | Interface implementation |
| `CALLS` | `Function → Function` | Function call (with count, recursive flag) |
| `BELONGS_TO` | `Class → Module` | Class-to-module containment |

## Abstract Test Class Pattern

Common test scenarios live in `AbstractCodeGraphTest`; each concrete class only wires up the backend.

```kotlin
// Abstract class (common logic)
abstract class AbstractCodeGraphTest {
    abstract val ops: GraphOperations
    // ... shared test cases ...
}

// Neo4j implementation
class Neo4jCodeGraphTest : AbstractCodeGraphTest() {
    override val ops = Neo4jGraphOperations(Neo4jServer.instance.driver)
}

// Memgraph / Apache AGE / TinkerGraph test classes follow the same pattern
```

| Abstract Class | Concrete Classes |
|----------------|------------------|
| `AbstractCodeGraphTest` | `Neo4jCodeGraphTest`, `MemgraphCodeGraphTest`, `AgeCodeGraphTest`, `TinkerGraphCodeGraphTest` |
| `AbstractCodeGraphSuspendTest` | `Neo4jCodeGraphSuspendTest`, `MemgraphCodeGraphSuspendTest`, `AgeCodeGraphSuspendTest`, `TinkerGraphCodeGraphSuspendTest` |

## Running Tests

```bash
# All tests
./gradlew :code-graph-examples:test

# Specific backend
./gradlew :code-graph-examples:test --tests "io.bluetape4k.graph.examples.code.Neo4jCodeGraphTest"
./gradlew :code-graph-examples:test --tests "io.bluetape4k.graph.examples.code.AgeCodeGraphSuspendTest"
```

Docker is required for integration tests (Neo4j, Memgraph, Apache AGE). TinkerGraph runs in-memory.

## Module Notes

- Example modules are **not** published to Maven Central
- Schema DSL and service layer are in `src/main`, while tests are in `src/test`
- Use this module as a reference when building your own graph-backed application with bluetape4k-graph
