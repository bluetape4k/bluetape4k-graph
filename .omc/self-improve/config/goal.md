# Improvement Goal

## Objective
Neo4j(`graph-neo4j`)와 Apache AGE(`graph-age`) 모듈의 그래프 연산 평균 latency를 30% 감소시킨다.
이 두 모듈은 실제 DB 서버(Testcontainers)를 사용하며, TinkerGraph/Memgraph(메모리 기반)는 부차적 대상이다.

벤치마크는 kotlinx-benchmark + Testcontainers 기반으로 Neo4j와 AGE 각각 측정하고,
두 백엔드의 평균 latency를 합산한 `primary` 점수를 주요 메트릭으로 사용한다.

## Target Metric
- **Metric name**: avg_latency_us (평균 실행 시간, μs — 낮을수록 좋음)
- **Target value**: 베이스라인의 70% (30% 감소)
- **Direction**: lower_is_better

## Scope
- **In scope**:
  - `graph/graph-neo4j/` — Neo4j 구현 (primary)
  - `graph/graph-age/` — Apache AGE 구현 (primary)
  - `graph/graph-core/` — 공통 추상화 및 알고리즘
  - `benchmark/graph-neo4j-benchmark/` — Neo4j 벤치마크 (신규)
  - `benchmark/graph-age-benchmark/` — AGE 벤치마크 (신규)
- **Out of scope**:
  - `graph/graph-tinkerpop/` — 인메모리, 개선 효과 없음
  - `graph/graph-memgraph/` — Neo4j 호환, Neo4j 개선 효과 연동
  - `examples/` — 사용 예시 모듈
  - `spring-boot3/`, `spring-boot4/` — Spring Boot starter
  - `graph-io/` — I/O 모듈 (별도 목표)

## Milestones
| Milestone | Target | Strategy Focus |
|-----------|--------|----------------|
| M1 | 베이스라인 -15% | Neo4j/AGE 연결 풀 튜닝, 쿼리 최적화 |
| M2 | 베이스라인 -25% | 캐싱, 배치 처리, 코루틴/VT 최적화 |
| M3 | 베이스라인 -30% | 알고리즘 개선, 직렬화 최적화 |

## Experiment Ideas
- Neo4j: 세션 재사용 및 연결 풀 크기 최적화
- Neo4j: 읽기 전용 트랜잭션에 `READ` 모드 명시
- AGE: Cypher-over-SQL 변환 레이어 최적화
- AGE: JDBC 연결 풀 크기 및 타임아웃 설정 튜닝
- 공통: 반복 조회 결과 캐싱
- 공통: 배치 정점/간선 생성 시 단일 트랜잭션 병합
- 공통: `AgePropertySerializer`/`Neo4jRecordMapper` 직렬화 성능 개선
