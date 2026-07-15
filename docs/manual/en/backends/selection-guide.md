# Backend selection guide

Select from existing infrastructure and required semantics, then verify locally. Feature count alone is a poor decision rule.

| Backend | Existing infrastructure / language | Transactions | Schema/index | Local verification | Portability note |
|---|---|---|---|---|---|
| Neo4j | Neo4j/Bolt, Cypher | native driver transaction | indexes and constraints | Testcontainers Neo4j 5 | broad common-contract baseline |
| Memgraph | Memgraph with Neo4j-driver-compatible Bolt, Cypher | native transaction | backend-specific Cypher DDL | Memgraph container | test Cypher/schema differences |
| Apache AGE | PostgreSQL, Cypher-over-SQL | JDBC/Exposed boundary | limited portable DDL | `apache/age:PG16_latest` | SQL session and graph context matter |
| TinkerPop | in-process JVM, Gremlin/TinkerGraph | in-memory transaction behavior | manager capability is limited | no container | best for tests, not a remote-server substitute |
| FalkorDB | Redis-shaped service, openCypher subset | library/backend constraints | backend-specific indexes | FalkorDB container | validate unsupported transaction paths |

Implementation and test anchors: [Neo4j](../../../../graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphOperationsTest.kt), [Memgraph](../../../../graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphOperationsTest.kt), [AGE](../../../../graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeGraphOperationsTest.kt), [TinkerGraph](../../../../graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphOperationsTest.kt), [FalkorDB](../../../../graph/graph-falkordb/src/test/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphOperationsTest.kt).

Amazon Neptune is not implemented or supported in Graph 0.5.1. Do not infer support from roadmap or backlog issues. If portability matters, run the same domain example against candidate backends and record transaction, schema, ID, property-type, and traversal differences before selection.
