# Issue 374 Graph Neo4j Coverage Review

## Scope

- Added `Neo4jCoroutineSessionTest` to cover reactive read/write/query session wrappers.
- Expanded `Neo4jGraphSuspendOperationsTest` to cover successful scoped transaction CRUD and cancellation rollback.

## Coverage

- Baseline: `6946 / 9133 = 76.05%`
- Updated: `7831 / 9133 = 85.74%`
- Main improvement: `Neo4jCoroutineSession` and `Neo4jReactiveGraphSuspendTransactionScope` execution paths.

## Verification

- `./gradlew :bluetape4k-graph-neo4j:detekt :bluetape4k-graph-neo4j:test :bluetape4k-graph-neo4j:koverXmlReport --no-daemon --no-configuration-cache`
- Result: `BUILD SUCCESSFUL`
