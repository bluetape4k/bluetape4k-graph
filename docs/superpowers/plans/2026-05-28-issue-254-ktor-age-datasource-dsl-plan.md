# Issue 254 - graph-ktor Managed AGE DataSource DSL Plan

## DoD

- Add `ageDataSource { ... }` with property validation.
- Create a Hikari pool, call `Database.connect(dataSource)`, and wire sync/suspend AGE operations.
- Close plugin-owned AGE operations and the plugin-created Hikari pool on Ktor stop.
- Preserve the existing caller-owned `age(graphName)` helper.
- Add validation tests for invalid properties.
- Add Ktor `testApplication` smoke coverage with PostgreSQL AGE Testcontainers.
- Update English and Korean `graph-ktor` README files.
- Update changelog and lesson note.
- Verify compile, test, and whitespace checks.

## Implementation Steps

1. Add `ManagedAgeDataSourceGraphPluginConfig` and `GraphPluginConfig.ageDataSource`.
2. Add HikariCP as a compile-only dependency for the optional DSL implementation.
3. Convert the AGE Ktor runtime smoke to use managed setup.
4. Add invalid property fail-fast tests.
5. Document lifecycle ownership and optional Hikari dependency.
6. Run focused `graph-ktor` compile/test checks.
