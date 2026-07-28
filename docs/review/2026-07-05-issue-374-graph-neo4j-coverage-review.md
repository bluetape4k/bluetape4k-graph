# 이슈 374 Graph Neo4j coverage review

## 범위

- reactive read/write/query session wrapper를 cover하기 위해 `Neo4jCoroutineSessionTest`를 추가했다.
- 성공 경로의 scoped transaction CRUD와 cancellation rollback을 cover하도록 `Neo4jGraphSuspendOperationsTest`를 확장했다.

## 커버리지

- Baseline: `6946 / 9133 = 76.05%`
- Updated: `7831 / 9133 = 85.74%`
- 주요 개선 지점: `Neo4jCoroutineSession`과 `Neo4jReactiveGraphSuspendTransactionScope` execution path.

## 검증

- `./gradlew :bluetape4k-graph-neo4j:detekt :bluetape4k-graph-neo4j:test :bluetape4k-graph-neo4j:koverXmlReport --no-daemon --no-configuration-cache`
- 결과: `BUILD SUCCESSFUL`
