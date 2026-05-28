# Issue 254 - graph-ktor Managed AGE DataSource DSL Design

## Context

Issue #232 added managed-driver DSLs for Neo4j, Memgraph, and FalkorDB. Apache AGE was split out because it uses Exposed's global JDBC transaction manager and a JDBC `DataSource`, not a backend-local driver object.

## Decision

Add a dedicated `ageDataSource { ... }` DSL in `graph-ktor`.

- It creates a plugin-owned Hikari pool from simple properties.
- It calls Exposed `Database.connect(dataSource)` before constructing AGE operations.
- It wires `AgeGraphOperations` and `AgeGraphSuspendOperations` into `GraphPlugin`.
- It closes only the Hikari pool created by the DSL on `ApplicationStopped`.
- It leaves the existing `age(graphName)` helper as caller-owned for DI-managed Exposed setups.

## Dependency Boundary

`graph-ktor` keeps backend helpers compile-only. HikariCP follows the same optional runtime boundary: the DSL implementation compiles against Hikari, and applications using `ageDataSource { ... }` must include HikariCP with `graph-age`.

## Defaults

- `jdbcUrl`: `jdbc:postgresql://localhost:5432/postgres`
- `username`: `postgres`
- `graphName`: `default`
- `connectionInitSql`: `LOAD 'age'; SET search_path = ag_catalog, "$user", public;`
- `driverClassName`: `org.postgresql.Driver`
- `maximumPoolSize`: `4`

## Non-Goals

- Do not replace Exposed's global transaction manager.
- Do not close caller-owned `Database` or `DataSource` instances.
- Do not change existing `age(graphName)` behavior.
