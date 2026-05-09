# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased] — 0.3.0-SNAPSHOT

### Added

- **`graph-bom` README 문서**: BOM 사용법을 English/Korean README로 정리했습니다 ([PR #70](https://github.com/bluetape4k/bluetape4k-graph/pull/70)).
- **`graph-io-okio`**: OkIO 기반 스트리밍 그래프 I/O 레이어를 추가했습니다. CSV/GraphML/Jackson 계열 I/O에 Source/Sink 기반 확장 지점을 제공합니다 ([PR #48](https://github.com/bluetape4k/bluetape4k-graph/pull/48)).
- **Weighted graph support**: Dijkstra/A* shortest path 지원을 추가했습니다 ([PR #39](https://github.com/bluetape4k/bluetape4k-graph/pull/39)).
- **`graph-core` 모델 빌더 유틸리티** (`graph/graph-core/src/main/kotlin/io/bluetape4k/graph/model/`)
  - `graphElementIdOf(Any)`: 임의 타입에서 `GraphElementId` 생성 — `String`, `Long`, `Int`, `GraphElementId` 모두 안전하게 변환
  - `graphVertexOf(Any, label, properties)`: id·label·프로퍼티 맵으로 `GraphVertex` 생성 유틸
  - `graphPathOf(vertices, edges)` / `graphPathOf(vertices)`: `GraphPath` 빌더 오버로드
  - `emptyGraphPath()`: 빈 경로 생성 (기존 `emptyGraphPathOf()` 대체)
  - `GraphPath.toCycle()`: 경로를 `GraphCycle`로 변환하는 확장 메서드
- **`graph-core` 모델 테스트 클래스 신규 추가** (`graph/graph-core/src/test/`)
  - `GraphElementIdTest`: `graphElementIdOf` 4개 케이스 (이중 변환 방어 포함)
  - `GraphVertexTest`: `graphVertexOf` 6개 케이스
  - `GraphPathTest`: `graphPathOf` 8개 케이스 + `emptyGraphPath`
  - `GraphCycleTest`: `toCycle()`, `length`, 동등 비교 7개 케이스
- **`graph-core` README 모델 빌더 유틸리티 섹션** (`graph/graph-core/README.md`, `README.ko.md`)
- **Transaction DSL first slice**: `GraphOperations.transaction { }` 확장과 capability contract를 추가하고 Neo4j, Memgraph, AGE, TinkerGraph 동기 백엔드에 1차 구현을 연결했습니다. suspend transaction capability도 동일 백엔드에 연결하고, FalkorDB는 중간 결과를 즉시 반환해야 하는 repository DSL 특성상 명시적 미지원으로 고정했습니다.

### Fixed

- **`graphElementIdOf(Any)` 이중 `toString()` 변환 버그**: `GraphElementId` 값을 다시 `graphElementIdOf`에 전달하면 `"GraphElementId(value=x)"` 문자열로 오염되던 문제 수정 — `is GraphElementId` 타입 체크로 조기 반환
- `AStarRunner` 성능과 불변식 검증을 보강하고 관련 테스트/KDoc을 정리했습니다 ([PR #62](https://github.com/bluetape4k/bluetape4k-graph/pull/62)).
- `FakeFileSystem` 동시 접근 시 발생하던 `ConcurrentModificationException`을 수정했습니다 ([PR #53](https://github.com/bluetape4k/bluetape4k-graph/pull/53)).
- CI artifact 경로와 모듈별 Kover 리포트 경로를 현재 빌드 레이아웃에 맞게 수정했습니다 ([PR #55](https://github.com/bluetape4k/bluetape4k-graph/pull/55), [PR #56](https://github.com/bluetape4k/bluetape4k-graph/pull/56)).

### Changed

- Gradle dependency declarations migrated from `buildSrc/Libs.kt` to Version Catalog (`gradle/libs.versions.toml`) ([PR #63](https://github.com/bluetape4k/bluetape4k-graph/pull/63)).
- CI now uses paths-filter, Docker-specific jobs, and retry configuration for container-heavy workflows ([PR #68](https://github.com/bluetape4k/bluetape4k-graph/pull/68)).
- Removed the `tanvd.kosogor` plugin from the build ([PR #57](https://github.com/bluetape4k/bluetape4k-graph/pull/57)).
- `graph-io-core` test coverage was raised from 65% to 93% ([PR #58](https://github.com/bluetape4k/bluetape4k-graph/pull/58)).
- `emptyGraphPathOf()` → `emptyGraphPath()` 이름 변경 (Kotlin 팩토리 함수 관례 준수)
- `graphVertexOf(Any, label)` → `graphVertexOf(Any, label, properties)`: `properties` 파라미터 추가 및 `graphElementIdOf` 경유로 이중 변환 제거
- `graphPathOf` 단일-arg 중복 오버로드 제거, 명시적 파라미터 오버로드만 유지
- `AStarRunner` companion object 콜론 앞 공백 추가 (ktlint 규칙 준수)
- 테스트 코드의 Kluent 의존성을 `bluetape4k-assertions`로 마이그레이션하고 Gradle 테스트 의존성을 교체했습니다. PR #69에서 `./gradlew compileTestKotlin --no-daemon` 성공으로 검증했으며, issue #66은 완료 처리했습니다.

---

## [0.2.0] - 2026-04-28

### Added

- **그래프 알고리즘 확장** (`graph-core` + 백엔드 구현): `pageRank`, `degreeCentrality`, `connectedComponents`, `bfs`, `dfs`, `cycles` API를 추가하고 Neo4j/Memgraph/AGE/TinkerPop 계열 구현을 정리했습니다.
- **Virtual Threads API 확장**: Vertex/Edge/Traversal repository 전체에 virtual-thread bridge adapter와 `GraphVirtualThreadOperations` 합성 API를 적용했습니다.
- **FalkorDB 백엔드**: `jfalkordb` 기반 `graph-falkordb` 구현, Spring Boot 3/4 auto-configuration, examples 통합을 추가했습니다.
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
- **GitHub Actions CI 파이프라인**: CI, integration, release, benchmark 워크플로우를 추가하고 Java 25 preview, Gradle cache, Testcontainers 기반 통합 테스트 경로를 구성했습니다.

### Changed

- **`graph-servers` 모듈 삭제**: `bluetape4k-testcontainers`의 `io.bluetape4k.testcontainers.graphdb` 패키지(`Neo4jServer.Launcher.neo4j`, `MemgraphServer.Launcher.memgraph`, `PostgreSQLAgeServer.Launcher.postgresqlAge`)로 대체. 모든 백엔드 테스트(`graph-neo4j`, `graph-memgraph`, `graph-age`, `examples`, `spring-boot3/4 starter`)가 새 API로 마이그레이션됨.
- **문서 / 예제 API 정합성 정리**: `AgeGraphOperations(graphName)` 생성자 패턴, `Database.connect(dataSource)` 선행 호출, `asVirtualThread` 실제 패키지 import 기준으로 README/KDoc 예제를 정리했습니다.

### Fixed

- **TinkerGraph `graphOperations()` 반환 타입**: `GraphOperations` → `TinkerGraphOperations` — `graphSuspendOperations(ops: TinkerGraphOperations)` 주입 불가 버그 수정
- **Spring Boot AutoConfig 테스트 `withBean(Supplier)` 패턴 제거**: AGE AutoConfig 테스트에서 `HikariDataSource` 공유 인스턴스가 컨텍스트 소멸 시 자동 close되는 문제를 `withUserConfiguration(DataSourceConfig::class.java)` 패턴으로 수정

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
