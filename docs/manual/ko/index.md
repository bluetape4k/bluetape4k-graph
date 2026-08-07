# Bluetape4k Graph 0.5 매뉴얼

이 매뉴얼은 커밋 `72c0256e2e1cf61101d29852210e3c827ca93bc0`에서 출시한 안정 버전 `0.6.0`을 설명한다. 공통 모델, 동기·코루틴 API, 지원 백엔드 다섯 개, graph-io, 프레임워크 연동이 범위다. Amazon Neptune은 0.6.0에서 **지원하지 않는다**. 백로그 이슈도 현재 기능으로 다루지 않는다.

## 핵심 기능

- **백엔드 독립 그래프 모델:** [핵심 모델](architecture/core-model.md)에서 모든 백엔드가 같은 vertex, edge, path, element ID 규칙을 사용합니다.
- **동기·코루틴 API:** [대응 API](architecture/paired-apis.md)가 저장소, 순회, 배치, merge, 트랜잭션 연산을 블로킹 실행과 일시 중단 실행에서 같은 구조로 제공합니다.
- **다섯 가지 데이터베이스 백엔드:** [백엔드 선택 가이드](backends/selection-guide.md)에서 Neo4j, Memgraph, Apache AGE, TinkerPop/TinkerGraph, FalkorDB를 쿼리 언어, 트랜잭션 동작, 운영 조건으로 비교합니다.
- **스키마·순회·트랜잭션:** [스키마와 트랜잭션](architecture/schema-and-transactions.md)에서 label, index, constraint, merge, path, 소유권 경계를 설명합니다.
- **그래프 가져오기와 내보내기:** [graph-io 형식](graph-io/formats.md), [실행 모델](graph-io/execution-model.md), [OkIO 보안](graph-io/okio-security.md)으로 CSV, NDJSON, GraphML, 압축, 인증 암호화를 다룹니다.
- **애플리케이션 연동과 예제:** [Spring Boot](frameworks/spring-boot.md), [Ktor](frameworks/ktor.md), [학습 경로](guides/learning-path.md)가 공통 API를 실행 가능한 도메인 예제와 운영 검증으로 연결합니다.

## 무엇부터 결정할까

1. [시작하기](getting-started.md)에서 생태계 BOM을 불러오고 첫 연산을 실행한다.
2. 드라이버를 고르기 전에 [백엔드 선택 가이드](backends/selection-guide.md)를 읽는다.
3. [핵심 모델](architecture/core-model.md), [동기·코루틴 API](architecture/paired-apis.md), [트랜잭션 경계](architecture/schema-and-transactions.md)를 익힌다.
4. [학습 경로](guides/learning-path.md)를 따라가고, 운영 전에 테스트와 운영 가이드를 적용한다.

![저장소 학습 지도](../assets/overview/repository-learning-map.png)

API의 중심은 [`GraphOperations`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphOperations.kt)와 [`GraphSuspendOperations`](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/repository/GraphSuspendOperations.kt)다. 두 API 모두 [`graph-core`의 공통 모델](https://github.com/bluetape4k/bluetape4k-graph/blob/72c0256e2e1cf61101d29852210e3c827ca93bc0/graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/GraphVertex.kt)을 반환한다.

## 매뉴얼 지도

- 아키텍처: 저장소 구성, 모델, API 조합, 스키마, merge·batch, 순회, 트랜잭션
- 백엔드: Neo4j, Memgraph, Apache AGE, TinkerPop/TinkerGraph, FalkorDB
- graph-io: 파일 경계, 실행 모델, OkIO 압축과 인증 암호화
- 프레임워크: Ktor 플러그인과 Spring Boot 자동 설정의 생명주기
- 가이드: 단계별 학습, 테스트, 운영, 취소, benchmark 해석

소비자가 선택할 버전은 개별 graph 라이브러리나 graph BOM 버전이 아니라 `bluetape4k-dependencies` 버전 하나다. 이 매뉴얼의 의존성 예제는 생태계 BOM을 불러오고 모듈 좌표에는 버전을 쓰지 않는다.

## 가이드와 핵심 개념

- 시작: [시작하기](getting-started.md), [학습 경로](guides/learning-path.md)
- 아키텍처: [저장소 지도](architecture/repository-map.md), [핵심 모델](architecture/core-model.md), [동기·코루틴 API](architecture/paired-apis.md), [스키마와 트랜잭션](architecture/schema-and-transactions.md)
- 백엔드: [선택 가이드](backends/selection-guide.md), [Neo4j와 Memgraph](backends/neo4j-and-memgraph.md), [Apache AGE](backends/apache-age.md), [TinkerPop](backends/tinkerpop.md), [FalkorDB](backends/falkordb.md)
- graph-io: [파일 형식](graph-io/formats.md), [실행 모델](graph-io/execution-model.md), [OkIO 보안](graph-io/okio-security.md)
- 프레임워크: [Spring Boot](frameworks/spring-boot.md), [Ktor](frameworks/ktor.md)
- 운영 가이드: [테스트](guides/testing.md), [운영](guides/operations.md), [실패와 취소](guides/failure-and-cancellation.md), [벤치마크 기반 선택](guides/benchmark-based-selection.md)

## 배포 라이브러리

- 플랫폼과 핵심: [graph BOM](modules/bluetape4k-graph-bom.md), [graph core](modules/bluetape4k-graph-core.md)
- 백엔드: [Neo4j](modules/bluetape4k-graph-neo4j.md), [Memgraph](modules/bluetape4k-graph-memgraph.md), [Apache AGE](modules/bluetape4k-graph-age.md), [TinkerPop](modules/bluetape4k-graph-tinkerpop.md), [FalkorDB](modules/bluetape4k-graph-falkordb.md)
- graph-io: [core](modules/bluetape4k-graph-io-core.md), [CSV](modules/bluetape4k-graph-io-csv.md), [Jackson 2](modules/bluetape4k-graph-io-jackson2.md), [Jackson 3](modules/bluetape4k-graph-io-jackson3.md), [GraphML](modules/bluetape4k-graph-io-graphml.md), [OkIO](modules/graph-okio.md)
- 프레임워크: [Spring Boot](modules/bluetape4k-graph-spring-boot.md), [Ktor](modules/bluetape4k-graph-ktor.md)

## 예제

- 모델링: [코드 그래프](examples/code-graph.md), [지식 그래프](examples/knowledge-graph.md), [LinkedIn 그래프](examples/linkedin-graph.md), [추천](examples/recommendation.md)
- 위험과 운영: [사기 탐지](examples/fraud-detection.md), [IAM 접근 그래프](examples/iam-access-graph.md), [관찰 가능성 그래프](examples/observability-graph.md), [보안 공격 경로](examples/security-attack-path.md)
- 시스템: [공급망](examples/supply-chain-graph.md), [데이터 계보](examples/data-lineage.md), [네트워크 토폴로지](examples/network-topology.md), [Ktor 그래프](examples/ktor-graph.md)

## 벤치마크

- [벤치마크 전체 보기](benchmarks/overview.md)
- [그래프 연산](benchmarks/graph-operations.md)
- [graph-io](benchmarks/graph-io.md)
- [AGE와 Neo4j](benchmarks/age-and-neo4j.md)
