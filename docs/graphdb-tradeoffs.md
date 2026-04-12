# Graph Database 장단점 및 선택 가이드

> bluetape4k-graph 프로젝트에서 지원하는 Neo4j, Memgraph, Apache AGE, Apache TinkerPop 백엔드를 기준으로 정리한 문서입니다.

## 개요

Graph Database(이하 GraphDB)는 데이터를 **정점(Vertex/Node)** 과 **간선(Edge/Relationship)** 으로 모델링하는 DBMS 카테고리입니다. 관계 자체가 1급 객체이므로 연결 중심의 데이터 도메인에서 RDBMS 대비 강점을 갖습니다.

## 장점

| 항목 | 설명 |
|------|------|
| **관계 탐색 성능** | Index-free Adjacency로 다단계 JOIN 없이 O(1)에 근접한 이웃 조회 |
| **데이터 모델 직관성** | 소셜 그래프, 추천, 지식 그래프, 코드 의존성 등 "연결"이 본질인 도메인 표현이 자연스러움 |
| **스키마 유연성** | 노드/엣지에 속성을 자유롭게 추가, 도메인 진화에 유연하게 대응 |
| **경로/패턴 질의** | 최단 경로, 커뮤니티 탐지, PageRank 등 그래프 알고리즘을 내장 |
| **쿼리 표현력** | Cypher/Gremlin은 JOIN 체인보다 관계 패턴을 훨씬 간결하게 표현 |
| **동적 관계** | 관계 타입 추가/변경 시 DDL 마이그레이션 부담이 적음 |

## 단점

| 항목 | 설명 |
|------|------|
| **집계/분석 쿼리 약세** | GROUP BY, 윈도우 함수 등 OLAP성 작업은 RDBMS/컬럼나 DB 대비 불리 |
| **수평 확장 난이도** | 간선이 파티션을 넘나들면 성능 저하. RDBMS보다 샤딩이 어려움 |
| **생태계 규모** | RDBMS 대비 도구, ORM, 운영 노하우가 상대적으로 부족 |
| **학습 곡선** | Cypher/Gremlin 숙련과 그래프 기반 모델링 철학 학습 필요 |
| **저장 오버헤드** | 간선이 1급 객체이므로 관계당 저장 비용이 큼 |
| **트랜잭션 일관성** | 백엔드별 ACID 보장 수준 차이 (Neo4j=ACID, 일부 분산 엔진은 제한적) |

## 사용처 판단 기준

### 적합한 경우
- 소셜 네트워크, 팔로우/친구 관계, 콘텐츠 추천
- 사기 탐지(Fraud Detection), 금융 거래 네트워크 분석
- 지식 그래프, 온톨로지, 엔티티 링킹
- IAM/권한 그래프, 조직도, RBAC
- 코드 의존성, 아키텍처 분석, 공급망 추적
- 네트워크/인프라 토폴로지

### 부적합한 경우
- 단순 CRUD 애플리케이션
- 대량 집계 및 리포팅/BI (OLAP)
- 로그/시계열 분석
- 대규모 배치 ETL 중심 워크로드

## bluetape4k-graph 백엔드 비교

| 백엔드 | 쿼리 언어 | 드라이버 | 특징 | 적합한 상황 |
|--------|-----------|----------|------|-------------|
| **Neo4j** | Cypher | Neo4j Java Driver (Bolt) | 성숙한 생태계, ACID, 풍부한 그래프 알고리즘 | 엔터프라이즈 프로덕션, 복잡한 패턴 매칭 |
| **Memgraph** | Cypher | Neo4j Java Driver (Bolt 호환) | 인메모리, 스트리밍 친화, 저지연 | 실시간 분석, 저지연 질의, CEP |
| **Apache AGE** | Cypher over SQL | PostgreSQL JDBC + Exposed | 기존 PostgreSQL 위에 그래프 레이어 추가 | RDBMS와 그래프 혼합, 기존 PG 인프라 재활용 |
| **Apache TinkerPop** | Gremlin | TinkerGraph (인메모리) | 표준 그래프 API, 프로퍼티 그래프 | 테스트/프로토타입, 벤더 중립성 필요 |

## 선택 가이드

1. **이미 PostgreSQL을 운영 중이고 그래프는 보조 역할** → `graph-age`
2. **그래프가 주 워크로드이고 ACID 필수** → `graph-neo4j`
3. **실시간 스트리밍/저지연 분석** → `graph-memgraph`
4. **벤더 중립성, 테스트, 프로토타이핑** → `graph-tinkerpop`

## 참고

- 본 프로젝트의 추상화 레이어(`graph-core`)는 위 4개 백엔드를 공통 인터페이스(`GraphOperations` / `GraphSuspendOperations`)로 추상화하여, 비즈니스 로직을 변경하지 않고 백엔드 전환을 가능하게 합니다.
- 동기/코루틴 이중 API 패턴을 제공하므로 Spring MVC와 WebFlux 양쪽에서 모두 활용 가능합니다.
- 예시는 `examples/code-graph-examples`, `examples/linkedin-graph-examples` 모듈을 참고하세요.
