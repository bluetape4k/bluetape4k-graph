# bluetape4k-graph-bom

[한국어](./README.ko.md) | English

Maven BOM (Bill of Materials) for the **bluetape4k-graph** ecosystem. Manages versions of all
`io.github.bluetape4k.graph:*` modules so consumers can declare dependencies without specifying
individual versions.

## Architecture

![bom Architecture diagram](../docs/images/readme-diagrams/bom-architecture-01.png)

The BOM is a Gradle `java-platform` that publishes only `<dependencyManagement>` constraints — no runtime classes.

## Core Features

- Centralized version management for all `bluetape4k-graph` modules
- Single source of truth for graph DB drivers (Neo4j / Memgraph / AGE / TinkerPop / FalkorDB)
- Aggregated by `bluetape4k-dependencies` for cross-ecosystem version coordination

## Modules Managed

| Group | Modules |
|-------|---------|
| `graph/*` | `graph-core`, `graph-neo4j`, `graph-memgraph`, `graph-age`, `graph-tinkerpop`, `graph-falkordb` |
| `graph-io/*` | `graph-io-core`, `graph-io-csv`, `graph-io-graphml`, `graph-io-jackson2`, `graph-io-jackson3`, `graph-okio` |
| `spring-boot/*` | `graph-spring-boot` |
| `ktor/*` | `graph-ktor` |

> Note: `examples/*` and `benchmark/*` modules are excluded from the BOM constraints.

## Usage Examples

### Gradle Kotlin DSL

```kotlin
plugins {
    id("io.spring.dependency-management") version "1.1.x"
}

dependencyManagement {
    imports {
        mavenBom("io.github.bluetape4k.graph:bluetape4k-graph-bom:<version>")
    }
}

dependencies {
    implementation("io.github.bluetape4k.graph:bluetape4k-graph-neo4j")
    implementation("io.github.bluetape4k.graph:bluetape4k-graph-spring-boot")
}
```

### Plain Gradle

```kotlin
dependencies {
    implementation(platform("io.github.bluetape4k.graph:bluetape4k-graph-bom:<version>"))
    implementation("io.github.bluetape4k.graph:bluetape4k-graph-neo4j")
}
```

### Maven

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.bluetape4k.graph</groupId>
            <artifactId>bluetape4k-graph-bom</artifactId>
            <version>${bluetape4k-graph.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

## Configuration Options

The BOM itself has no configuration. For SNAPSHOT builds, add the Sonatype Central Snapshots repository:

```kotlin
repositories {
    mavenCentral()
    maven {
        name = "central-snapshots"
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
    }
}
```

## Dependency

This BOM is automatically aggregated by `bluetape4k-dependencies`. Prefer importing
`io.github.bluetape4k:bluetape4k-dependencies` when consuming multiple bluetape4k ecosystems.
