# Backend selection guide

![Backend decision map](../../assets/backends/backend-decision-map.png)

Select from existing infrastructure and required semantics, then verify locally. Feature count alone is a poor decision rule.

| Backend | Existing infrastructure / language | Transactions | Schema/index | Local verification | Portability note |
|---|---|---|---|---|---|
| Neo4j | Neo4j/Bolt, Cypher | native driver transaction | indexes and constraints | Testcontainers Neo4j 5 | broad common-contract baseline |
| Memgraph | Memgraph with Neo4j-driver-compatible Bolt, Cypher | native transaction | backend-specific Cypher DDL | Memgraph container | test Cypher/schema differences |
| Apache AGE | PostgreSQL, Cypher-over-SQL | JDBC/Exposed boundary | limited portable DDL | `apache/age:release_PG18_1.7.0` | SQL session and graph context matter |
| TinkerPop | in-process JVM, Gremlin/TinkerGraph | in-memory transaction behavior | manager capability is limited | no container | best for tests, not a remote-server substitute |
| FalkorDB | Redis-shaped service, openCypher subset | library/backend constraints | backend-specific indexes | FalkorDB container | validate unsupported transaction paths |

Implementation and test anchors: [Neo4j](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphOperationsTest.kt), [Memgraph](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphOperationsTest.kt), [AGE](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeGraphOperationsTest.kt), [TinkerGraph](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphOperationsTest.kt), [FalkorDB](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-falkordb/src/test/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphOperationsTest.kt).

Amazon Neptune is not implemented or supported in Graph 0.6.0. Do not infer support from roadmap or backlog issues. If portability matters, run the same domain example against candidate backends and record transaction, schema, ID, property-type, and traversal differences before selection.

## Verify two finalists

Use the exact 0.6.0 test selectors, one backend at a time:

```bash
./gradlew :bluetape4k-graph-neo4j:test --tests '*Neo4jGraphMergeOperationsTest' --tests '*Neo4jGraphSchemaManagerTest'
./gradlew :bluetape4k-graph-memgraph:test --tests '*MemgraphGraphMergeOperationsTest' --tests '*MemgraphGraphSchemaManagerTest'
./gradlew :bluetape4k-graph-age:test --tests '*AgeGraphMergeOperationsTest' --tests '*AgeGraphSchemaManagerTest'
./gradlew :bluetape4k-graph-tinkerpop:test --tests '*TinkerGraphMergeOperationsTest' --tests '*TinkerGraphTransactionTest'
./gradlew :bluetape4k-graph-falkordb:test --tests '*FalkorDBGraphMergeOperationsTest' --tests '*FalkorDBGraphSchemaManagerTest'
```

Record container/image or in-memory fixture, created vertex/edge counts, duplicate merge outcome, rollback outcome, index/constraint inventory, and one `shortestPath` result. Inject an invalid identifier and a transaction exception. A candidate advances only if expected failure types and post-failure counts match the application's recovery design. Finally repeat the same checks against the deployment server version and close resources according to fixture/caller ownership.
