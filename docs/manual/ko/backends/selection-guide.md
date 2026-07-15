# 백엔드 선택 가이드

기능 개수로 순위를 매기지 말고, 이미 운영하는 기반과 반드시 필요한 의미론으로 후보를 줄인다. 그다음 로컬에서 같은 도메인 테스트를 실행한다.

| 백엔드 | 운영 기반·질의 | 트랜잭션 | 스키마/인덱스 | 로컬 검증 | 이식성 경계 |
|---|---|---|---|---|---|
| Neo4j | Neo4j/Bolt, Cypher | 드라이버 트랜잭션 | 인덱스·제약조건 | Neo4j 5 컨테이너 | 공통 계약의 넓은 기준점 |
| Memgraph | Neo4j 드라이버 호환 Bolt, Cypher | 서버 트랜잭션 | 백엔드별 Cypher DDL | Memgraph 컨테이너 | Cypher·DDL 차이를 재검증 |
| Apache AGE | PostgreSQL, SQL 안의 Cypher | JDBC/Exposed 경계 | 이식 가능한 DDL 제한 | `apache/age:PG16_latest` | 세션과 graph context가 중요 |
| TinkerPop | JVM 안의 TinkerGraph, Gremlin | 메모리 구현 의미론 | 제한된 manager capability | 컨테이너 없음 | 원격 서버를 대신하지 않음 |
| FalkorDB | Redis 형태 서비스, openCypher 일부 | 라이브러리·서버 제약 | 전용 인덱스 | FalkorDB 컨테이너 | 미지원 트랜잭션 경로 확인 |

구현 근거: [Neo4j](../../../../graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphOperationsTest.kt), [Memgraph](../../../../graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphOperationsTest.kt), [AGE](../../../../graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeGraphOperationsTest.kt), [TinkerGraph](../../../../graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphOperationsTest.kt), [FalkorDB](../../../../graph/graph-falkordb/src/test/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphOperationsTest.kt).

Amazon Neptune은 Graph 0.5.1에서 구현되지 않았고 지원 대상도 아니다. 계획이나 백로그를 지원 근거로 삼지 않는다. 이식성이 필요하면 후보마다 트랜잭션, 스키마, ID, 속성 형식, 순회 결과를 기록한다.
