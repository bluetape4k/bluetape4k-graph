# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- **`graph-io` 벌크 임포트/익스포트** (`graph-io/` 4개 모듈): 포맷별 대용량 I/O (Sync / VirtualThread / Coroutine)
  - `graph-io-core`: 공유 계약(`GraphBulkExporter`, `GraphBulkImporter`), 모델(`GraphIoVertexRecord`, `GraphIoEdgeRecord`), 옵션, 헬퍼(`GraphIoPaths`) — `BufferedOutputStream/InputStream` 래핑으로 StAX 성능 확보
  - `graph-io-csv`: CSV 임포터/익스포터 (univocity-parsers 기반) × Sync/VT/Suspend
  - `graph-io-jackson2`: Jackson 2.x NDJSON 임포터/익스포터 × Sync/VT/Suspend — 간선 버퍼링(`maxEdgeBufferSize`) 지원
  - `graph-io-jackson3`: Jackson 3.x NDJSON 임포터/익스포터 × Sync/VT/Suspend — Jackson2 NDJSON 호환
  - `graph-io-graphml`: GraphML (XML/StAX) 임포터/익스포터 × Sync/VT/Suspend — `XMLInputFactory`/`XMLOutputFactory` 싱글턴 캐싱
  - 크로스-포맷 round-trip 테스트: CSV ↔ Jackson2 ↔ Jackson3 ↔ GraphML
- **`graph-io-benchmark`** (`benchmark/graph-io-benchmark`): JMH 벤치마크 36개 메서드 (4 포맷 × 3 API × 3 연산)
  - 결과: CSV export 1.0ms, GraphML export 2.6ms, import 18–22ms (TinkerGraph in-memory 기준)
  - 결과 리포트: `docs/benchmark/2026-04-18-graph-io-bulk-results.md`

- **`graph-spring-boot3-starter`** (`spring-boot3/graph-spring-boot3-starter`): Spring Boot 3.5.x AutoConfiguration 스타터 신규 추가
  - `GraphAutoConfiguration`: 루트 자동 설정 (공통 프로퍼티 바인딩)
  - `GraphNeo4jAutoConfiguration`: `@ConditionalOnClass(Neo4jGraphOperations::class)` 기반 Neo4j 빈 자동 등록 + HealthIndicator
  - `GraphMemgraphAutoConfiguration`: Memgraph 빈 자동 등록 + HealthIndicator
  - `GraphAgeAutoConfiguration`: AGE DataSource 조건부 빈 등록 + HealthIndicator
  - `GraphTinkerGraphAutoConfiguration`: TinkerGraph 인메모리 빈 자동 등록 + HealthIndicator
  - `GraphProperties`, `Neo4jGraphProperties`, `MemgraphGraphProperties`, `AgeGraphProperties`, `TinkerGraphGraphProperties`: `@ConfigurationProperties` 바인딩
  - 테스트: `ApplicationContextRunner` 기반 단위 테스트 4종 + `TinkerGraphWebMvcTest` (Virtual Threads) + `TinkerGraphWebFluxTest` (코루틴) — 총 16 passing
- **`graph-spring-boot4-starter`** (`spring-boot4/graph-spring-boot4-starter`): Spring Boot 4.0.x AutoConfiguration 스타터 신규 추가
  - Spring Boot 4 모듈 분리 대응:
    - `DataSourceAutoConfiguration`: `boot.autoconfigure.jdbc` → `boot.jdbc.autoconfigure` (`spring-boot-jdbc` 모듈)
    - `HealthIndicator`/`Health`: `boot.actuate.health` → `boot.health.contributor` (`spring-boot-health` 모듈)
    - `TestRestTemplate`: `boot.test.web.client` → `boot.resttestclient` + `@AutoConfigureTestRestTemplate` 필수 (`spring-boot-resttestclient` 모듈)
    - `WebTestClient`: `@AutoConfigureWebTestClient` 필수 (`spring-boot-webtestclient` 모듈)
  - boot3와 동일한 5종 AutoConfiguration + 5종 Properties 클래스 (패키지: `boot4`)
  - 테스트: `ApplicationContextRunner` 기반 단위 테스트 4종 + `TinkerGraphWebMvcTest` + `TinkerGraphWebFluxTest` — 총 16 passing

### Changed

- **`graph-servers` 모듈 삭제**: `bluetape4k-testcontainers`의 `io.bluetape4k.testcontainers.graphdb` 패키지(`Neo4jServer.Launcher.neo4j`, `MemgraphServer.Launcher.memgraph`, `PostgreSQLAgeServer.Launcher.postgresqlAge`)로 대체. 모든 백엔드 테스트(`graph-neo4j`, `graph-memgraph`, `graph-age`, `examples`, `spring-boot3/4 starter`)가 새 API로 마이그레이션됨.

### Fixed

---

## [0.1.0] - 2026-04-16

### Added

- **BOM 모듈** (`bluetape4k-graph-bom`): 의존성 버전 통합 관리용 Bill of Materials 추가
- **`code-graph-examples`**: 코드 의존성 그래프 예시 통합 모듈
  - `AbstractCodeGraphTest` / `AbstractCodeGraphSuspendTest`: 공통 테스트 추상 클래스
  - 백엔드별 구체 클래스: `Neo4j`, `Memgraph`, `TinkerGraph`, `AGE` × sync/suspend (총 8개)
  - `CodeGraphService` / `CodeGraphSuspendService`: 모듈 의존성, 클래스 상속, 함수 호출 체인 관리
- **`linkedin-graph-examples`**: LinkedIn 소셜 그래프 예시 통합 모듈
  - `AbstractLinkedInGraphTest` / `AbstractLinkedInGraphSuspendTest`: 공통 테스트 추상 클래스
  - 백엔드별 구체 클래스: `Neo4j`, `Memgraph`, `TinkerGraph`, `AGE` × sync/suspend (총 8개)
  - `LinkedInGraphSuspendService`: suspend/Flow 기반 LinkedIn 그래프 서비스 신규 작성
- **추상 테스트 클래스 패턴**: `ops` (`GraphOperations` / `GraphSuspendOperations`) 오버라이드만으로 모든 백엔드에서 동일한 테스트 실행 가능

### Changed

- 기존 8개 분리 모듈(`code-graph-{age,neo4j,memgraph,tinkerpop}`, `linkedin-graph-{age,neo4j,memgraph,tinkerpop}`)을 2개 통합 모듈로 합침
- `settings.gradle.kts`: `examples/` 하위 디렉토리 자동 탐색으로 모듈 등록

### Fixed

- **TinkerGraph 경로 호환성**: `shortestPath` 결과에서 `path.length` (= `edges.size`) 대신 `path.vertices.size > 1` 사용 — TinkerGraph의 경로 탐색이 정점만 반환하는 특성에 대응

---

## [0.0.1] - 2026-03-25 (Initial)

### Added

- `graph-core`: 백엔드 독립 추상 모델 및 인터페이스
  - 이중 API 패턴: `GraphOperations` (동기) + `GraphSuspendOperations` (코루틴/Flow)
  - 도메인 모델: `GraphVertex`, `GraphEdge`, `GraphPath`, `GraphElementId`
  - 스키마 DSL: `VertexLabel`, `EdgeLabel` (Exposed Table 스타일)
- `graph-age`: Apache AGE (PostgreSQL 그래프 확장) 구현
- `graph-neo4j`: Neo4j Java Driver 기반 Cypher 구현
- `graph-memgraph`: Memgraph (Neo4j 프로토콜 호환) 구현
- `graph-tinkerpop`: Apache TinkerPop / TinkerGraph 인메모리 구현
- `graph-servers`: Testcontainers 기반 테스트 서버 팩토리 (Neo4j, Memgraph, PostgreSQL+AGE)
