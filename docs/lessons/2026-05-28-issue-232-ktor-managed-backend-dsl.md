# 2026-05-28 - Issue #232 Ktor Managed Backend DSL

## Context

Issue #232 added a managed backend property DSL for `graph-ktor` so small Ktor services can let the plugin create
Neo4j, Memgraph, or FalkorDB drivers directly.

## Decision

Keep caller-owned helpers unchanged and add managed-driver overloads:

- `neo4j { uri; username; password; database }`
- `memgraph { uri; username; password; database }`
- `falkorDB { host; port; username; password; graphName }`

Apache AGE managed `DataSource` setup is separate issue #254 because Exposed `Database.connect(...)`, global
transaction-manager state, and pool ownership need a narrower contract.

## Outcome

Future Ktor examples should prefer the managed-driver DSL when the example owns the backend connection. Use the
caller-owned overloads only when a test fixture, DI container, or external lifecycle clearly owns the driver.

## Verification

Planned gates:

- `./gradlew :bluetape4k-graph-ktor:compileKotlin :bluetape4k-graph-ktor:compileTestKotlin --no-daemon`
- `./gradlew :bluetape4k-graph-ktor:test --no-daemon`
- `git diff --check`

## Future Guidance

When implementing example issues #247 through #252, use the latest Ktor managed-driver DSL for Ktor setup examples unless
the example deliberately demonstrates externally owned driver lifecycle.
