# linkedin-graph-examples

LinkedIn-style social graph example demonstrating how to model people, companies, skills, and their relationships as a property graph using bluetape4k-graph's backend-independent API.

> 🇰🇷 [한국어 문서](README.ko.md)

## Overview

This example models a LinkedIn-like social network as a property graph and verifies that the same code works across Neo4j, Memgraph, Apache AGE, and TinkerGraph backends via the abstract test class pattern.

- **Common Logic**: `LinkedInGraphService` / `LinkedInGraphSuspendService` contain the graph operations
- **Abstract Tests**: `AbstractLinkedInGraphTest` / `AbstractLinkedInGraphSuspendTest` encapsulate shared test scenarios
- **Concrete Tests**: Each backend (Neo4j, Memgraph, AGE, TinkerGraph) only provides a `GraphOperations` factory

## Domain Model

### Vertex Labels

| Label | Description | Key Properties |
|-------|-------------|----------------|
| `Person` | User profile | `name`, `title`, `company`, `location`, `skills`, `connectionCount` |
| `Company` | Employer | `name`, `industry`, `size`, `location` |
| `Skill` | Professional skill | `name`, `category` |

### Edge Labels

| Label | From → To | Description |
|-------|-----------|-------------|
| `KNOWS` | `Person → Person` | Mutual connection (`since`, `strength`) |
| `WORKS_AT` | `Person → Company` | Employment (`role`, `startDate`, `isCurrent`) |
| `FOLLOWS` | `Person → Person` | Follow relationship (non-mutual) |
| `HAS_SKILL` | `Person → Skill` | Skill ownership (`level`: beginner / intermediate / expert) |
| `ENDORSES` | `Person → Person` | Skill endorsement (`skillName`) |

## Abstract Test Class Pattern

Common test scenarios live in `AbstractLinkedInGraphTest`; each concrete class only wires up the backend.

```kotlin
// Abstract class (common logic)
abstract class AbstractLinkedInGraphTest {
    abstract val ops: GraphOperations
    // ... shared test cases ...
}

// Neo4j implementation
class Neo4jLinkedInGraphTest : AbstractLinkedInGraphTest() {
    private val driver = GraphDatabase.driver(Neo4jServer.Launcher.neo4j.boltUrl, AuthTokens.none())
    override val ops = Neo4jGraphOperations(driver)

    @AfterAll fun teardown() { driver.close() }
}
```

| Abstract Class | Concrete Classes |
|----------------|------------------|
| `AbstractLinkedInGraphTest` | `Neo4jLinkedInGraphTest`, `MemgraphLinkedInGraphTest`, `AgeLinkedInGraphTest`, `TinkerGraphLinkedInGraphTest` |
| `AbstractLinkedInGraphSuspendTest` | `Neo4jLinkedInGraphSuspendTest`, `MemgraphLinkedInGraphSuspendTest`, `AgeLinkedInGraphSuspendTest`, `TinkerGraphLinkedInGraphSuspendTest` |

## Sample Queries

- **Friends of friends**: 2-hop `KNOWS` traversal from a person
- **Shortest connection path**: `shortestPath` over the `KNOWS` edge
- **Skill recommendations**: Find people who share skills with the current user
- **Endorsement aggregation**: Count incoming `ENDORSES` edges per person/skill

## Running Tests

```bash
# All tests
./gradlew :linkedin-graph-examples:test

# Specific backend
./gradlew :linkedin-graph-examples:test --tests "io.bluetape4k.graph.examples.linkedin.Neo4jLinkedInGraphTest"
./gradlew :linkedin-graph-examples:test --tests "io.bluetape4k.graph.examples.linkedin.AgeLinkedInGraphSuspendTest"
```

Docker is required for integration tests (Neo4j, Memgraph, Apache AGE). TinkerGraph runs in-memory.

## Module Notes

- Example modules are **not** published to Maven Central
- Schema DSL and service layer are in `src/main`, while tests are in `src/test`
- Use this module as a reference when building social-graph applications with bluetape4k-graph
