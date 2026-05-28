# Issue 254 - Managed AGE DataSource DSL

## Context

`graph-ktor` gained managed backend setup for driver-style backends in #232. AGE needed a separate slice because Exposed owns a process-wide JDBC transaction manager and the pool lifecycle must be explicit.

## Decision

Add `ageDataSource { ... }` as a managed, Hikari-backed convenience path while keeping `age(graphName)` as the caller-owned Exposed setup path.

## Outcome

The managed helper creates the pool, calls `Database.connect(dataSource)`, wires sync and suspend AGE operations, and closes only the plugin-created pool on Ktor shutdown.

## Verification

Run focused `graph-ktor` compile and test checks before merging.

## Future Guidance

Use `ageDataSource { ... }` for small Ktor services and examples that do not already have DI-managed Exposed infrastructure. Use `age(graphName)` when the application owns `Database.connect(...)` and the pool.
