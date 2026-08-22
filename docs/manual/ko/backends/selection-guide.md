# 백엔드 선택 가이드

![백엔드 선택 지도](../../assets/backends/backend-decision-map.png)

기능 개수로 순위를 매기지 말고, 이미 운영하는 기반과 반드시 필요한 의미론으로 후보를 줄인다. 그다음 로컬에서 같은 도메인 테스트를 실행한다.

| 백엔드 | 운영 기반·질의 | 트랜잭션 | 스키마/인덱스 | 로컬 검증 | 이식성 경계 |
|---|---|---|---|---|---|
| Neo4j | Neo4j/Bolt, Cypher | 드라이버 트랜잭션 | 인덱스·제약조건 | Neo4j 5 컨테이너 | 공통 계약의 넓은 기준점 |
| Memgraph | Neo4j 드라이버 호환 Bolt, Cypher | 서버 트랜잭션 | 백엔드별 Cypher DDL | Memgraph 컨테이너 | Cypher·DDL 차이를 재검증 |
| Apache AGE | PostgreSQL, SQL 안의 Cypher | JDBC/Exposed 경계 | 이식 가능한 DDL 제한 | `apache/age:release_PG18_1.7.0` | 세션과 graph context가 중요 |
| TinkerPop | JVM 안의 TinkerGraph, Gremlin | 메모리 구현 의미론 | 제한된 manager capability | 컨테이너 없음 | 원격 서버를 대신하지 않음 |
| FalkorDB | Redis 형태 서비스, openCypher 일부 | 라이브러리·서버 제약 | 전용 인덱스 | FalkorDB 컨테이너 | 미지원 트랜잭션 경로 확인 |

구현 근거: [Neo4j](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-neo4j/src/test/kotlin/io/bluetape4k/graph/neo4j/Neo4jGraphOperationsTest.kt), [Memgraph](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-memgraph/src/test/kotlin/io/bluetape4k/graph/memgraph/MemgraphGraphOperationsTest.kt), [AGE](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-age/src/test/kotlin/io/bluetape4k/graph/age/AgeGraphOperationsTest.kt), [TinkerGraph](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-tinkerpop/src/test/kotlin/io/bluetape4k/graph/tinkerpop/TinkerGraphOperationsTest.kt), [FalkorDB](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-falkordb/src/test/kotlin/io/bluetape4k/graph/falkordb/FalkorDBGraphOperationsTest.kt).

Amazon Neptune은 Graph 0.6.0에서 구현되지 않았고 지원 대상도 아니다. 계획이나 백로그를 지원 근거로 삼지 않는다. 이식성이 필요하면 후보마다 트랜잭션, 스키마, ID, 속성 형식, 순회 결과를 기록한다.

## 최종 후보 둘을 검증한다

0.6.0 테스트를 백엔드별로 따로 실행한다.

```bash
./gradlew :bluetape4k-graph-neo4j:test --tests '*Neo4jGraphMergeOperationsTest' --tests '*Neo4jGraphSchemaManagerTest'
./gradlew :bluetape4k-graph-memgraph:test --tests '*MemgraphGraphMergeOperationsTest' --tests '*MemgraphGraphSchemaManagerTest'
./gradlew :bluetape4k-graph-age:test --tests '*AgeGraphMergeOperationsTest' --tests '*AgeGraphSchemaManagerTest'
./gradlew :bluetape4k-graph-tinkerpop:test --tests '*TinkerGraphMergeOperationsTest' --tests '*TinkerGraphTransactionTest'
./gradlew :bluetape4k-graph-falkordb:test --tests '*FalkorDBGraphMergeOperationsTest' --tests '*FalkorDBGraphSchemaManagerTest'
```

컨테이너 이미지나 메모리 fixture, 생성된 정점·간선 수, 중복 merge 결과, rollback 뒤 개수, index/constraint 목록, `shortestPath` 하나를 표로 남긴다. 잘못된 identifier와 트랜잭션 예외도 일부러 넣는다. 실패 유형과 실패 뒤 개수가 복구 설계에 맞는 후보만 남긴다. 마지막으로 운영 서버 버전에서 같은 검증을 반복하고 fixture와 호출자의 자원 소유권에 맞춰 종료한다.
